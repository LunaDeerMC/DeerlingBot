package cn.lunadeer.mc.deerlingbot;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.managers.PlaceHolderApiManager;
import cn.lunadeer.mc.deerlingbot.protocols.GroupOperation;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
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


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;


public class JoinLeaveEvents implements Listener {

    private static final long REVIEW_TIMEOUT_TICKS = 300L * 20L;
    private final Map<ReviewKey, PendingReview> pendingReviews = new ConcurrentHashMap<>();

    private record ReviewKey(long groupId, long userId) {
    }

    private record MathQuestion(String expression, int answer) {
    }

    private static final class PendingReview {
        private final MathQuestion question;
        private final CancellableTask timeoutTask;

        private PendingReview(MathQuestion question, CancellableTask timeoutTask) {
            this.question = question;
            this.timeoutTask = timeoutTask;
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (!Configuration.joinQuitMessage.enable) return;
        String message = Configuration.joinQuitMessage.joinMessage;
        message = message.replace("%player_name%", event.getPlayer().getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            message = PlaceHolderApiManager.setPlaceholders(event.getPlayer(), message);

        GroupOperation.SendGroupMessage(Long.parseLong(Configuration.joinQuitMessage.groupId), new TextSegment(message));
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (!Configuration.joinQuitMessage.enable) return;
        String message = Configuration.joinQuitMessage.quitMessage;
        message = message.replace("%player_name%", event.getPlayer().getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            message = PlaceHolderApiManager.setPlaceholders(event.getPlayer(), message);

        GroupOperation.SendGroupMessage(Long.parseLong(Configuration.joinQuitMessage.groupId), new TextSegment(message));
    }

    @EventHandler
    public void onGroupIncrease(GroupIncrease event) {
        if (!Configuration.groupList.contains(String.valueOf(event.getGroupId()))) return;

        if (Configuration.postJoinReview.enable && event.getSubType() == GroupIncrease.SubType.approve) {
            startPostJoinReview(event.getGroupId(), event.getUserId());
            return;
        }

        sendWelcomeMessage(event.getUserId(), event.getGroupId());
    }

    @EventHandler
    public void onGroupDecrease(GroupDecrease event) {
        clearPendingReview(event.getGroupId(), event.getUserId());
    }

    @EventHandler
    public void onGroupRequest(GroupRequest event) {
        if (!Configuration.postJoinReview.enable) return;
        if (!Configuration.groupList.contains(String.valueOf(event.getGroupId()))) return;
        if (event.getSubType() != GroupRequest.SubType.add) return;

        GroupOperation.SetGroupAddRequest(event.getFlag(), event.getSubType().name(), true, "");
        XLogger.info("已自动通过群 {0} 的加群申请，用户 {1}", event.getGroupId(), event.getUserId());
    }

    @EventHandler
    public void onGroupMessage(GroupMessage event) {
        ReviewKey key = new ReviewKey(event.getGroupID(), event.getUserId());
        PendingReview pendingReview = pendingReviews.get(key);
        if (pendingReview == null) return;

        String rawMessage = event.getRawMessage();
        if (rawMessage == null) return;

        String normalizedMessage = rawMessage.trim();
        if (normalizedMessage.isEmpty()) return;

        if (normalizedMessage.equals(String.valueOf(pendingReview.question.answer()))) {
            completePostJoinReview(key);
            return;
        }

        if (!normalizedMessage.matches("-?\\d+")) return;

        GroupOperation.SendGroupMessage(
                event.getGroupID(),
                new ReplySegment(event.getMessageId()),
                new TextSegment("答案不正确，请直接发送算式结果。")
        );
    }

    private void startPostJoinReview(long groupId, long userId) {
        clearPendingReview(groupId, userId);

        MathQuestion question = createMathQuestion();
        ReviewKey key = new ReviewKey(groupId, userId);
        CancellableTask timeoutTask = Scheduler.runTaskLater(() -> timeoutPostJoinReview(key), REVIEW_TIMEOUT_TICKS);
        pendingReviews.put(key, new PendingReview(question, timeoutTask));

        GroupOperation.SendGroupMessage(
                groupId,
                new MentionSegment(userId),
                new TextSegment("\n入群后置审核：请在 300 秒内直接发送下面算式的结果，超时将被自动移出群聊。\n" + question.expression() + " = ?")
        );
        XLogger.info("已向群 {0} 的新成员 {1} 发起后置审核", groupId, userId);
    }

    private void completePostJoinReview(ReviewKey key) {
        PendingReview pendingReview = pendingReviews.remove(key);
        if (pendingReview == null) return;
        if (pendingReview.timeoutTask != null) {
            pendingReview.timeoutTask.cancel();
        }

        if (Configuration.groupWelcomeMessage.enable) {
            sendWelcomeMessage(key.userId(), key.groupId());
        } else {
            GroupOperation.SendGroupMessage(
                    key.groupId(),
                    new MentionSegment(key.userId()),
                    new TextSegment("\n审核通过，欢迎加入本群。")
            );
        }
        XLogger.info("群 {0} 的新成员 {1} 已通过后置审核", key.groupId(), key.userId());
    }

    private void timeoutPostJoinReview(ReviewKey key) {
        PendingReview pendingReview = pendingReviews.remove(key);
        if (pendingReview == null) return;

        GroupOperation.SetGroupKick(key.groupId(), key.userId(), false);
        GroupOperation.SendGroupMessage(
                key.groupId(),
                new TextSegment("用户 " + key.userId() + " 未在 300 秒内通过后置审核，已自动移出群聊。")
        );
        XLogger.warn("群 {0} 的新成员 {1} 未通过后置审核，已自动移出群聊", key.groupId(), key.userId());
    }

    private void clearPendingReview(long groupId, long userId) {
        PendingReview pendingReview = pendingReviews.remove(new ReviewKey(groupId, userId));
        if (pendingReview == null || pendingReview.timeoutTask == null) return;
        pendingReview.timeoutTask.cancel();
    }

    private void sendWelcomeMessage(long userId, long groupId) {
        if (!Configuration.groupWelcomeMessage.enable) return;

        String message = Configuration.groupWelcomeMessage.getMessage(userId, groupId).trim();
        if (message.isEmpty()) return;

        GroupOperation.SendGroupMessage(
                groupId,
                new MentionSegment(userId),
                new TextSegment("\n" + message)
        );
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
