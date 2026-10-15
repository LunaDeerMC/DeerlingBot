package cn.lunadeer.mc.deerlingbot;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.managers.PlaceHolderApiManager;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.PrivateOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.PrivateMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.notice.GroupDecrease;
import cn.lunadeer.mc.deerlingbot.protocols.events.notice.GroupIncrease;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MentionSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.ReplySegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.protocols.events.request.GroupRequest;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.CancellableTask;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class JoinLeaveEvents implements Listener {

    private static final long REVIEW_TIMEOUT_MILLIS = 300_000L;
    private final Map<ReviewKey, PendingReview> pendingReviews = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final Function<List<ReviewStore.Entry>, CompletableFuture<Void>> save;
    private final Consumer<Runnable> dispatch;
    private ReviewStore store;
    private volatile boolean closed;
    private boolean connected;
    private boolean loaded = true;
    private long revision;

    public JoinLeaveEvents() {
        this(System::currentTimeMillis, ignored -> CompletableFuture.completedFuture(null), Runnable::run);
    }

    JoinLeaveEvents(LongSupplier clock,
                    Function<List<ReviewStore.Entry>, CompletableFuture<Void>> save,
                    Consumer<Runnable> dispatch) {
        this.clock = clock;
        this.save = save;
        this.dispatch = dispatch;
    }

    public JoinLeaveEvents(Path stateFile) {
        this(new ReviewStore(stateFile));
    }

    private JoinLeaveEvents(ReviewStore store) {
        this(store, System::currentTimeMillis, task -> Scheduler.runTask(task));
    }

    JoinLeaveEvents(ReviewStore store, LongSupplier clock, Consumer<Runnable> dispatch) {
        this(clock, store::save, dispatch);
        this.store = store;
        loaded = false;
    }

    /** 加载完成前不打开连接，从而不会与新入群/退群事件互相覆盖。 */
    public void start(Runnable ready, Consumer<Throwable> failure) {
        store.load().whenComplete((entries, error) -> dispatchIfOpen(() -> {
            if (closed) return;
            if (error != null) { failure.accept(error); return; }
            restore(entries);
            ready.run();
        }));
    }

    void restore(List<ReviewStore.Entry> entries) {
        if (closed) return;
        for (ReviewStore.Entry entry : entries) {
            ReviewKey key = new ReviewKey(entry.groupId(), entry.userId());
            if (enabled(key)) pendingReviews.putIfAbsent(key, new PendingReview(entry, Phase.RECOVERED));
        }
        loaded = true;
        persist(); // 清除关闭审核或已移除群的磁盘记录。
        if (connected) activateRecovered();
    }

    public boolean isLoaded() { return loaded; }

    public void onBotConnected() {
        if (closed) return;
        connected = true;
        if (loaded) activateRecovered();
    }

    private void activateRecovered() {
        for (var entry : pendingReviews.entrySet()) {
            PendingReview pending = entry.getValue();
            if (pending.phase != Phase.RECOVERED) continue;
            ReviewStore.Entry old = pending.data;
            // 停机期间已过期：重新给完整答题时间，不能在恢复时立即踢人。
            pending.freshWindow = old.deadline() <= clock.getAsLong();
            long deadline = pending.freshWindow ? clock.getAsLong() + REVIEW_TIMEOUT_MILLIS : old.deadline();
            pending.data = new ReviewStore.Entry(old.groupId(), old.userId(), UUID.randomUUID().toString(),
                    old.expression(), old.answer(), old.privateMessage(), deadline);
            pending.phase = Phase.STARTING;
            commitStart(entry.getKey(), pending);
        }
    }

    public void reloadConfiguration() {
        boolean changed = false;
        for (var entry : pendingReviews.entrySet()) {
            if (!enabled(entry.getKey()) && pendingReviews.remove(entry.getKey(), entry.getValue())) {
                cancel(entry.getValue());
                changed = true;
            }
        }
        if (changed) persist();
    }

    public void close() {
        if (closed) return;
        closed = true;
        pendingReviews.values().forEach(this::cancel);
        // 最终快照也覆盖队列满时尚未成功提交的取消/退群状态。
        // 只等待文件线程，不等待回到游戏线程的完成回调。
        if (store != null) {
            if (loaded) store.flushAndClose(snapshot());
            else store.close(); // 恢复尚未应用时，不能用空内存覆盖已有审核文件。
        }
    }

    private record ReviewKey(long groupId, long userId) { }
    private record MathQuestion(String expression, int answer) { }
    private record PrivateReviewAttempt(Long groupId, String answerText) { }
    private enum Phase { RECOVERED, STARTING, ACTIVE, PASSED, EXPIRED }
    private static final class PendingReview {
        private ReviewStore.Entry data;
        private Phase phase;
        private boolean freshWindow;
        private CancellableTask timeoutTask;
        private PendingReview(ReviewStore.Entry data, Phase phase) { this.data = data; this.phase = phase; }
    }

    private void dispatchIfOpen(Runnable task) {
        if (!closed) dispatch.accept(() -> { if (!closed) task.run(); });
    }

    private boolean enabled(ReviewKey key) {
        return Configuration.postJoinReview.enable && Configuration.groupList.contains(String.valueOf(key.groupId()));
    }

    private boolean current(ReviewKey key, PendingReview pending) {
        return !closed && pendingReviews.get(key) == pending && enabled(key);
    }

    private void cancel(PendingReview pending) {
        if (pending.timeoutTask != null) pending.timeoutTask.cancel();
        pending.timeoutTask = null;
    }

    private CompletableFuture<Void> persist() {
        long submitted = ++revision;
        CompletableFuture<Void> result = save.apply(snapshot());
        result.whenComplete((ignored, error) -> {
            if (error != null && !(closed && error instanceof CancellationException)) {
                XLogger.error("审核状态持久化失败，暂停相关处罚并重试: " + error.getMessage());
                if (!closed) dispatchIfOpen(() -> {
                    if (!closed && revision == submitted)
                        Scheduler.runTaskLater(() -> { if (!closed && revision == submitted) persist(); }, 20L);
                });
            }
        });
        return result;
    }

    private List<ReviewStore.Entry> snapshot() {
        return pendingReviews.values().stream().filter(pending -> pending.phase != Phase.PASSED)
                .map(pending -> pending.data).toList();
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (!Configuration.joinQuitMessage.enable)
            return;
        String message = Configuration.joinQuitMessage.joinMessage;
        message = message.replace("%player_name%", event.getPlayer().getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            message = PlaceHolderApiManager.setPlaceholders(event.getPlayer(), message);

        GroupOperation.SendGroupMessage(Long.parseLong(Configuration.joinQuitMessage.groupId),
                new TextSegment(message));
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (!Configuration.joinQuitMessage.enable)
            return;
        String message = Configuration.joinQuitMessage.quitMessage;
        message = message.replace("%player_name%", event.getPlayer().getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            message = PlaceHolderApiManager.setPlaceholders(event.getPlayer(), message);

        GroupOperation.SendGroupMessage(Long.parseLong(Configuration.joinQuitMessage.groupId),
                new TextSegment(message));
    }

    @EventHandler
    public void onGroupIncrease(GroupIncrease event) {
        if (!Configuration.groupList.contains(String.valueOf(event.getGroupId())))
            return;
        if (event.getUserId() == event.getSelfId())
            return;

        // 申请通过和邀请入群均验证加入者，邀请者的身份不影响审核。
        if (Configuration.postJoinReview.enable) {
            startPostJoinReview(event.getGroupId(), event.getUserId());
            return;
        }

        sendWelcomeMessage(event.getUserId(), event.getGroupId());
    }

    @EventHandler
    public void onGroupDecrease(GroupDecrease event) {
        if (event.getUserId() == event.getSelfId()) {
            for (ReviewKey key : List.copyOf(pendingReviews.keySet())) {
                if (key.groupId() == event.getGroupId()) clearPendingReview(key.groupId(), key.userId());
            }
        } else {
            clearPendingReview(event.getGroupId(), event.getUserId());
        }
    }

    @EventHandler
    public void onGroupRequest(GroupRequest event) {
        if (!Configuration.postJoinReview.enable)
            return;
        if (!Configuration.groupList.contains(String.valueOf(event.getGroupId())))
            return;
        if (event.getSubType() != GroupRequest.SubType.add)
            return;

        GroupOperation.SetGroupAddRequest(event.getFlag(), event.getSubType().name(), true, "");
        XLogger.info("已自动通过群 {0} 的加群申请，用户 {1}", event.getGroupId(), event.getUserId());
    }

    @EventHandler
    public void onGroupMessage(GroupMessage event) {
        ReviewKey key = new ReviewKey(event.getGroupID(), event.getUserId());
        PendingReview pending = pendingReviews.get(key);
        if (pending == null || pending.data.privateMessage()) return;
        String normalizedMessage = normalizeReviewMessage(event.getRawMessage());
        if (normalizedMessage == null)
            return;

        handleReviewAnswer(
                key,
                normalizedMessage,
                () -> GroupOperation.SendGroupMessage(
                        event.getGroupID(),
                        new ReplySegment(event.getMessageId()),
                        new TextSegment("答案不正确，请直接发送算式结果。")));
    }

    @EventHandler
    public void onPrivateMessage(PrivateMessage event) {
        String normalizedMessage = normalizeReviewMessage(event.getRawMessage());
        if (normalizedMessage == null)
            return;

        PrivateReviewAttempt attempt = parsePrivateReviewAttempt(normalizedMessage);
        if (attempt == null)
            return;

        List<ReviewKey> userReviews = findPendingReviewsByUserId(event.getUserId());
        if (userReviews.isEmpty())
            return;

        ReviewKey key = resolvePrivateReviewKey(event.getUserId(), attempt, userReviews);
        if (key == null)
            return;

        handleReviewAnswer(
                key,
                attempt.answerText(),
                () -> PrivateOperation.SendPrivateMessage(event.getUserId(), "答案不正确，请直接发送算式结果。"));
    }

    private void startPostJoinReview(long groupId, long userId) {
        if (closed) return;
        ReviewKey key = new ReviewKey(groupId, userId);
        MathQuestion question = createMathQuestion();
        PendingReview pending = new PendingReview(new ReviewStore.Entry(groupId, userId, UUID.randomUUID().toString(),
                question.expression(), question.answer(), Configuration.postJoinReview.usePrivateMessage,
                clock.getAsLong() + REVIEW_TIMEOUT_MILLIS), Phase.STARTING);
        pending.freshWindow = true;
        PendingReview previous = pendingReviews.put(key, pending);
        if (previous != null) cancel(previous);
        commitStart(key, pending);
    }

    private void commitStart(ReviewKey key, PendingReview pending) {
        persist().whenComplete((ignored, error) -> dispatchIfOpen(() -> {
            if (!current(key, pending) || pending.phase != Phase.STARTING) return;
            if (error != null) {
                pending.timeoutTask = Scheduler.runTaskLater(() -> {
                    if (current(key, pending)) commitStart(key, pending);
                }, 20L);
                return;
            }
            long remaining = pending.data.deadline() - clock.getAsLong();
            if (remaining <= 0 || (pending.freshWindow && remaining < REVIEW_TIMEOUT_MILLIS - 10_000L)) {
                ReviewStore.Entry data = pending.data;
                pending.data = new ReviewStore.Entry(data.groupId(), data.userId(), data.session(), data.expression(),
                        data.answer(), data.privateMessage(), clock.getAsLong() + REVIEW_TIMEOUT_MILLIS);
                pending.freshWindow = true;
                commitStart(key, pending); // 磁盘延迟不能吃掉新题目的作答窗口。
                return;
            }
            pending.phase = Phase.ACTIVE;
            scheduleTimeout(key, pending);
            sendReviewQuestion(pending.data);
        }));
    }

    private void scheduleTimeout(ReviewKey key, PendingReview pending) {
        long remaining = Math.max(1L, pending.data.deadline() - clock.getAsLong());
        pending.timeoutTask = Scheduler.runTaskLater(() -> timeoutPostJoinReview(key, pending),
                Math.max(1L, (remaining + 49L) / 50L));
    }

    private void finish(ReviewKey key, PendingReview pending, Phase outcome) {
        if (!current(key, pending)) return;
        pending.phase = outcome;
        cancel(pending);
        persist().whenComplete((ignored, error) -> dispatchIfOpen(() -> {
            if (!current(key, pending) || pending.phase != outcome) return;
            if (error != null) {
                pending.timeoutTask = Scheduler.runTaskLater(() -> {
                    if (current(key, pending) && pending.phase == outcome) finish(key, pending, outcome);
                }, 20L);
                return;
            }
            pendingReviews.remove(key, pending);
            if (outcome == Phase.EXPIRED) {
                GroupOperation.SetGroupKick(key.groupId(), key.userId(), false);
                GroupOperation.SendGroupMessage(key.groupId(), new TextSegment("用户 " + key.userId()
                        + " 未在规定时间内通过后置审核，已请求移出群聊。"));
                XLogger.warn("群 {0} 的成员 {1} 审核超时，已请求移出群聊", key.groupId(), key.userId());
                persist(); // 请求发出前保留过期记录；关服回调未执行时可重新恢复审核。
            } else if (Configuration.groupWelcomeMessage.enable) {
                sendWelcomeMessage(key.userId(), key.groupId());
            } else {
                GroupOperation.SendGroupMessage(key.groupId(), new MentionSegment(key.userId()),
                        new TextSegment("\n审核通过，欢迎加入本群。"));
            }
        }));
    }

    private void timeoutPostJoinReview(ReviewKey key, PendingReview pending) {
        if (!current(key, pending) || pending.phase != Phase.ACTIVE) return;
        if (clock.getAsLong() < pending.data.deadline()) {
            scheduleTimeout(key, pending); // tick 时间不能替代持久化的绝对截止时间。
            return;
        }
        finish(key, pending, Phase.EXPIRED);
    }

    private void clearPendingReview(long groupId, long userId) {
        PendingReview pending = pendingReviews.remove(new ReviewKey(groupId, userId));
        if (pending == null) return;
        cancel(pending);
        persist();
    }

    private void handleReviewAnswer(ReviewKey key, String normalizedMessage, Runnable wrongAnswerAction) {
        PendingReview pending = pendingReviews.get(key);
        if (pending == null || !current(key, pending) || pending.phase != Phase.ACTIVE) return;
        if (clock.getAsLong() >= pending.data.deadline()) {
            timeoutPostJoinReview(key, pending);
            return;
        }
        if (normalizedMessage.equals(String.valueOf(pending.data.answer()))) {
            finish(key, pending, Phase.PASSED);
            return;
        }
        if (normalizedMessage.matches("-?\\d+")) wrongAnswerAction.run();
    }

    private void sendReviewQuestion(ReviewStore.Entry review) {
        long seconds = Math.max(1L, (review.deadline() - clock.getAsLong() + 999L) / 1000L);
        String question = "入群后置审核：请在 " + seconds + " 秒内回复算式结果，超时将被移出群聊。\n"
                + review.expression() + " = ?";
        if (review.privateMessage()) {
            PrivateOperation.SendPrivateMessage(review.userId(), "你正在为群 " + review.groupId() + " 完成验证。\n"
                    + question + "\n若同时在多个群等待审核，请按“群号 答案”的格式回复。");
        } else {
            GroupOperation.SendGroupMessage(review.groupId(), new MentionSegment(review.userId()),
                    new TextSegment("\n" + question));
        }
    }

    private List<ReviewKey> findPendingReviewsByUserId(long userId) {
        List<ReviewKey> userReviews = new ArrayList<>();
        for (ReviewKey key : pendingReviews.keySet()) {
            PendingReview pending = pendingReviews.get(key);
            if (key.userId() == userId && pending != null && pending.data.privateMessage()
                    && pending.phase == Phase.ACTIVE && current(key, pending)) {
                userReviews.add(key);
            }
        }
        return userReviews;
    }

    private ReviewKey resolvePrivateReviewKey(long userId, PrivateReviewAttempt attempt, List<ReviewKey> userReviews) {
        if (attempt.groupId() != null) {
            for (ReviewKey key : userReviews) {
                if (key.groupId() == attempt.groupId()) {
                    return key;
                }
            }
            PrivateOperation.SendPrivateMessage(userId, "未找到对应群的待审核任务，请确认群号后重试。");
            return null;
        }

        if (userReviews.size() == 1) {
            return userReviews.get(0);
        }

        PrivateOperation.SendPrivateMessage(userId, "检测到你当前在多个群存在待审核任务，请按“群号 答案”的格式回复。");
        return null;
    }

    private PrivateReviewAttempt parsePrivateReviewAttempt(String normalizedMessage) {
        if (normalizedMessage.matches("-?\\d+")) {
            return new PrivateReviewAttempt(null, normalizedMessage);
        }

        String[] parts = normalizedMessage.split("\\s+", 2);
        if (parts.length != 2)
            return null;
        if (!parts[0].matches("\\d+"))
            return null;

        String answerText = parts[1].trim();
        if (!answerText.matches("-?\\d+"))
            return null;

        try {
            return new PrivateReviewAttempt(Long.parseLong(parts[0]), answerText);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeReviewMessage(String rawMessage) {
        if (rawMessage == null)
            return null;

        String normalizedMessage = rawMessage.trim();
        if (normalizedMessage.isEmpty())
            return null;

        return normalizedMessage;
    }

    private void sendWelcomeMessage(long userId, long groupId) {
        if (!Configuration.groupWelcomeMessage.enable)
            return;

        String message = Configuration.groupWelcomeMessage.getMessage(userId, groupId).trim();
        if (message.isEmpty())
            return;

        GroupOperation.SendGroupMessage(
                groupId,
                new MentionSegment(userId),
                new TextSegment("\n" + message));
    }

    private MathQuestion createMathQuestion() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int operation = random.nextInt(3);
        return switch (operation) {
            case 0 -> {
                int left = random.nextInt(10, 100);
                int right = random.nextInt(10, 100);
                yield new MathQuestion(left + " + " + right, left + right);
            }
            case 1 -> {
                int left = random.nextInt(20, 100);
                int right = random.nextInt(1, left + 1);
                yield new MathQuestion(left + " - " + right, left - right);
            }
            default -> {
                int left = random.nextInt(2, 10);
                int right = random.nextInt(2, 10);
                yield new MathQuestion(left + " * " + right, left * right);
            }
        };
    }
}
