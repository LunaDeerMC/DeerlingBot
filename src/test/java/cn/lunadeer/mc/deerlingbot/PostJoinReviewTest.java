package cn.lunadeer.mc.deerlingbot;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.managers.CoreConnector;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.PrivateMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.notice.GroupIncrease;
import cn.lunadeer.mc.deerlingbot.protocols.events.request.GroupRequest;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.CancellableTask;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import cn.lunadeer.mc.deerlingbot.protocols.events.notice.GroupDecrease;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostJoinReviewTest {
    private final List<JSONObject> sent = new ArrayList<>();
    private final List<Runnable> timeouts = new ArrayList<>();
    private final CancellableTask timeoutTask = mock(CancellableTask.class);
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final JoinLeaveEvents listener = new JoinLeaveEvents(now::get,
            ignored -> CompletableFuture.completedFuture(null), Runnable::run);
    private MockedStatic<CoreConnector> connectors;
    private MockedStatic<Scheduler> scheduler;
    private MockedStatic<XLogger> logger;
    private List<String> previousGroups;
    private Configuration.PostJoinReview previousReview;
    private Configuration.GroupWelcomeMessage previousWelcome;

    @BeforeEach
    void setUp() {
        previousGroups = Configuration.groupList;
        previousReview = Configuration.postJoinReview;
        previousWelcome = Configuration.groupWelcomeMessage;
        Configuration.groupList = List.of("100");
        Configuration.postJoinReview = new Configuration.PostJoinReview();
        Configuration.postJoinReview.enable = true;
        Configuration.groupWelcomeMessage = new Configuration.GroupWelcomeMessage();
        Configuration.groupWelcomeMessage.message = List.of("欢迎新成员");

        CoreConnector connector = mock(CoreConnector.class);
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(connector).send(any(JSONObject.class));
        connectors = mockStatic(CoreConnector.class);
        connectors.when(CoreConnector::getInstance).thenReturn(connector);
        scheduler = mockStatic(Scheduler.class);
        scheduler.when(() -> Scheduler.runTaskLater(any(Runnable.class), anyLong()))
                .thenAnswer(invocation -> {
                    timeouts.add(invocation.getArgument(0));
                    return timeoutTask;
                });
        logger = mockStatic(XLogger.class);
    }

    @AfterEach
    void tearDown() {
        logger.close();
        scheduler.close();
        connectors.close();
        Configuration.groupList = previousGroups;
        Configuration.postJoinReview = previousReview;
        Configuration.groupWelcomeMessage = previousWelcome;
    }

    @ParameterizedTest
    @CsvSource({"approve,false", "invite,false", "approve,true", "invite,true"})
    void bothEntryMethodsRequireCorrectAnswerBeforeWelcome(GroupIncrease.SubType type, boolean privateReview) {
        Configuration.postJoinReview.usePrivateMessage = privateReview;
        listener.onGroupIncrease(increase(type, 100, 42));

        assertEquals(1, timeouts.size());
        assertEquals(1, sent.size());
        JSONObject question = sent.get(0);
        assertEquals(privateReview ? "send_private_msg" : "send_group_msg", question.getString("action"));
        assertEquals(privateReview ? 42L : 100L,
                question.getJSONObject("params").getLongValue(privateReview ? "user_id" : "group_id"));
        String text = messageText(question);
        assertTrue(text.contains("300 秒"));
        assertFalse(text.contains("欢迎新成员"));

        String answer = answerTo(text);
        if (privateReview) {
            listener.onPrivateMessage(new PrivateMessage(0, 1, PrivateMessage.SubType.group, 7, 42,
                    List.of(new TextSegment(answer)), answer, null));
        } else {
            listener.onGroupMessage(new GroupMessage(0, 1, GroupMessage.SubType.normal, 7, 100, 42,
                    List.of(new TextSegment(answer)), answer, null));
        }
        verify(timeoutTask).cancel();
        assertEquals(2, sent.size());
        assertTrue(messageText(sent.get(1)).contains("欢迎新成员"));
        timeouts.get(0).run();
        assertEquals(2, sent.size()); // 已通过的用户即使遇到迟到的超时回调也不能被踢出。
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invitedMemberIsKickedWhenVerificationTimesOut(boolean privateReview) {
        Configuration.postJoinReview.usePrivateMessage = privateReview;
        listener.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        assertEquals(1, timeouts.size());

        now.addAndGet(300_000L);
        timeouts.get(0).run();

        List<JSONObject> kicks = sent.stream().filter(action -> "set_group_kick".equals(action.getString("action"))).toList();
        assertEquals(1, kicks.size());
        JSONObject params = kicks.get(0).getJSONObject("params");
        assertEquals(100L, params.getLongValue("group_id"));
        assertEquals(42L, params.getLongValue("user_id"));
    }

    @ParameterizedTest
    @EnumSource(GroupIncrease.SubType.class)
    void disabledReviewWelcomesBothEntryMethods(GroupIncrease.SubType type) {
        Configuration.postJoinReview.enable = false;
        listener.onGroupIncrease(increase(type, 100, 42));

        scheduler.verifyNoInteractions();
        assertEquals(1, sent.size());
        assertTrue(messageText(sent.get(0)).contains("欢迎新成员"));
    }

    @ParameterizedTest
    @CsvSource({"200,42", "100,1"})
    void unrelatedGroupsAndBotItselfAreIgnored(long groupId, long userId) {
        listener.onGroupIncrease(increase(GroupIncrease.SubType.invite, groupId, userId));

        scheduler.verifyNoInteractions();
        assertTrue(sent.isEmpty());
    }

    @Test
    void botInvitationRequestsAreNotAutomaticallyAccepted() {
        listener.onGroupRequest(new GroupRequest(0, 1, 100, 42, "", "invitation", GroupRequest.SubType.invite));

        scheduler.verifyNoInteractions();
        assertTrue(sent.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reloadKeepsOriginalAnswerChannel(boolean privateReview) {
        Configuration.postJoinReview.usePrivateMessage = privateReview;
        listener.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        String answer = answerTo(messageText(sent.get(0)));
        Configuration.postJoinReview.usePrivateMessage = !privateReview;
        listener.reloadConfiguration();
        reply(listener, privateReview, 100, 42, answer);
        assertTrue(messageText(sent.get(1)).contains("欢迎新成员"));
        now.addAndGet(300_000L);
        timeouts.get(0).run();
        assertEquals(0, kicks());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disablingReviewOrRemovingGroupCancelsAndPersists(boolean disable) {
        List<List<ReviewStore.Entry>> snapshots = new ArrayList<>();
        JoinLeaveEvents instance = new JoinLeaveEvents(now::get, entries -> {
            snapshots.add(entries); return CompletableFuture.completedFuture(null);
        }, Runnable::run);
        instance.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        if (disable) Configuration.postJoinReview.enable = false;
        else Configuration.groupList = List.of();
        instance.reloadConfiguration();
        now.addAndGet(300_000L);
        timeouts.get(0).run();
        assertTrue(snapshots.get(snapshots.size() - 1).isEmpty());
        assertEquals(0, kicks());
    }

    @Test
    void oldTimeoutCannotKickMemberAfterLeavingAndRejoining() {
        listener.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        Runnable old = timeouts.get(0);
        listener.onGroupDecrease(new GroupDecrease(0, 1, 100, 42, GroupDecrease.SubType.leave, 42));
        now.addAndGet(100_000L);
        listener.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        now.addAndGet(200_000L);
        old.run();
        assertEquals(0, kicks());
        reply(listener, false, 100, 42, answerTo(messageText(sent.get(1))));
        assertTrue(messageText(sent.get(2)).contains("欢迎新成员"));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void restoredReviewWaitsForConnectionAndUsesDeadline(boolean expired, boolean connectionFirst) {
        List<List<ReviewStore.Entry>> snapshots = new ArrayList<>();
        JoinLeaveEvents instance = new JoinLeaveEvents(now::get, entries -> {
            snapshots.add(entries); return CompletableFuture.completedFuture(null);
        }, Runnable::run);
        if (connectionFirst) instance.onBotConnected();
        instance.restore(List.of(new ReviewStore.Entry(100, 42, "old-session", "20 + 3", 23,
                true, now.get() + (expired ? -1000L : 100_000L))));
        if (!connectionFirst) {
            assertTrue(sent.isEmpty());
            assertTrue(timeouts.isEmpty());
            instance.onBotConnected();
        }
        assertEquals(1, sent.size());
        assertTrue(messageText(sent.get(0)).contains(expired ? "300 秒" : "100 秒"));
        ReviewStore.Entry restored = snapshots.get(snapshots.size() - 1).get(0);
        assertEquals(now.get() + (expired ? 300_000L : 100_000L), restored.deadline());
        assertTrue(restored.privateMessage());
        instance.onBotConnected();
        assertEquals(1, sent.size()); // 普通重连不能延长或重发已激活审核。
        now.set(restored.deadline());
        timeouts.get(0).run();
        timeouts.get(0).run();
        assertEquals(1, kicks());
    }

    @Test
    void failedTimeoutPersistenceDoesNotKickAndLateSuccessCannotKickNewSession() {
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        JoinLeaveEvents instance = new JoinLeaveEvents(now::get, entries -> {
            CompletableFuture<Void> write = new CompletableFuture<>(); writes.add(write); return write;
        }, Runnable::run);
        instance.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        assertTrue(sent.isEmpty());
        writes.get(0).complete(null);
        now.addAndGet(300_000L);
        timeouts.get(0).run();
        assertEquals(0, kicks());
        writes.get(1).completeExceptionally(new IllegalStateException("disk full"));
        assertEquals(0, kicks());
        instance.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        writes.get(2).complete(null);
        assertEquals(0, kicks());
        // 所有旧重试和旧超时均不能干扰新会话。
        List.copyOf(timeouts).subList(0, timeouts.size() - 1).forEach(Runnable::run);
        assertEquals(0, kicks());
    }

    @Test
    void leaveDuringTimeoutWritePreventsKickAfterCommit() {
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        JoinLeaveEvents instance = new JoinLeaveEvents(now::get, entries -> {
            CompletableFuture<Void> write = new CompletableFuture<>(); writes.add(write); return write;
        }, Runnable::run);
        instance.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        writes.get(0).complete(null);
        now.addAndGet(300_000L);
        timeouts.get(0).run();
        instance.onGroupDecrease(new GroupDecrease(0, 1, 100, 42, GroupDecrease.SubType.leave, 42));
        writes.get(1).complete(null);
        assertEquals(0, kicks());
    }

    @Test
    void shutdownIgnoresLateWriteAndTimeoutCallbacks() {
        CompletableFuture<Void> write = new CompletableFuture<>();
        JoinLeaveEvents instance = new JoinLeaveEvents(now::get, ignored -> write, Runnable::run);
        instance.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        instance.close();
        write.complete(null);
        instance.onBotConnected();
        assertTrue(sent.isEmpty());
        assertTrue(timeouts.isEmpty());
    }

    @Test
    void slowInitialPersistenceRestartsAnswerWindowBeforeSending() {
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        List<List<ReviewStore.Entry>> snapshots = new ArrayList<>();
        JoinLeaveEvents instance = new JoinLeaveEvents(now::get, entries -> {
            snapshots.add(entries);
            CompletableFuture<Void> write = new CompletableFuture<>(); writes.add(write); return write;
        }, Runnable::run);
        instance.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        now.addAndGet(300_001L);
        writes.get(0).complete(null);
        assertTrue(sent.isEmpty());
        assertEquals(now.get() + 300_000L, snapshots.get(1).get(0).deadline());
        writes.get(1).complete(null);
        assertTrue(messageText(sent.get(0)).contains("300 秒"));
        assertEquals(0, kicks());
    }

    @Test
    void shutdownBeforeTimeoutActionKeepsRecordForRecovery() {
        List<List<ReviewStore.Entry>> snapshots = new ArrayList<>();
        List<Runnable> callbacks = new ArrayList<>();
        JoinLeaveEvents instance = new JoinLeaveEvents(now::get, entries -> {
            snapshots.add(entries); return CompletableFuture.completedFuture(null);
        }, callbacks::add);
        instance.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        callbacks.remove(0).run();
        now.addAndGet(300_000L);
        timeouts.get(0).run();
        assertEquals(1, snapshots.get(snapshots.size() - 1).size());
        instance.close();
        callbacks.remove(0).run();
        assertEquals(0, kicks());
        JoinLeaveEvents restored = new JoinLeaveEvents(now::get,
                ignored -> CompletableFuture.completedFuture(null), Runnable::run);
        restored.restore(snapshots.get(snapshots.size() - 1));
        restored.onBotConnected();
        assertTrue(messageText(sent.get(1)).contains("300 秒"));
    }

    @Test
    void restoreDropsDisallowedGroupsAndDoesNotOverwriteNewSession() {
        List<List<ReviewStore.Entry>> snapshots = new ArrayList<>();
        JoinLeaveEvents instance = new JoinLeaveEvents(now::get, entries -> {
            snapshots.add(entries); return CompletableFuture.completedFuture(null);
        }, Runnable::run);
        instance.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        String currentAnswer = answerTo(messageText(sent.get(0)));
        instance.restore(List.of(new ReviewStore.Entry(100, 42, "old", "10 + 1", 11, true, now.get() + 1000L),
                new ReviewStore.Entry(999, 88, "removed", "10 + 1", 11, true, now.get() + 1000L)));
        instance.onBotConnected();
        assertEquals(1, sent.size());
        assertEquals(1, snapshots.get(snapshots.size() - 1).size());
        reply(instance, false, 100, 42, currentAnswer);
        assertTrue(messageText(sent.get(1)).contains("欢迎新成员"));
    }

    private long kicks() {
        return sent.stream().filter(action -> "set_group_kick".equals(action.getString("action"))).count();
    }

    @Test
    void absoluteDeadlineAppliesEvenBeforeDelayedTimerRuns() {
        listener.onGroupIncrease(increase(GroupIncrease.SubType.invite, 100, 42));
        String answer = answerTo(messageText(sent.get(0)));
        now.addAndGet(300_001L);
        reply(listener, false, 100, 42, answer);
        timeouts.get(0).run();
        assertEquals(1, kicks());
    }

    private void reply(JoinLeaveEvents instance, boolean privateMessage, long group, long user, String answer) {
        if (privateMessage) instance.onPrivateMessage(new PrivateMessage(0, 1, PrivateMessage.SubType.group, 7,
                user, List.of(new TextSegment(answer)), answer, null));
        else instance.onGroupMessage(new GroupMessage(0, 1, GroupMessage.SubType.normal, 7, group, user,
                List.of(new TextSegment(answer)), answer, null));
    }

    private GroupIncrease increase(GroupIncrease.SubType type, long groupId, long userId) {
        JSONObject notice = new JSONObject();
        notice.put("time", 0);
        notice.put("self_id", 1);
        notice.put("group_id", groupId);
        notice.put("user_id", userId);
        notice.put("sub_type", type.name());
        // 机器人作为邀请者时，受邀的其他成员仍须验证。
        notice.put("operator_id", 1);
        return GroupIncrease.parse(notice);
    }

    private String messageText(JSONObject action) {
        StringBuilder text = new StringBuilder();
        for (Object segment : action.getJSONObject("params").getJSONArray("message")) {
            JSONObject json = (JSONObject) segment;
            if ("text".equals(json.getString("type"))) text.append(json.getJSONObject("data").getString("text"));
        }
        return text.toString();
    }

    private String answerTo(String question) {
        var match = Pattern.compile("(\\d+) ([+*-]) (\\d+) = \\?").matcher(question);
        assertTrue(match.find(), "验证消息应包含可作答的算式");
        int left = Integer.parseInt(match.group(1));
        int right = Integer.parseInt(match.group(3));
        return String.valueOf(switch (match.group(2)) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            default -> throw new AssertionError("不支持的算式");
        });
    }
}
