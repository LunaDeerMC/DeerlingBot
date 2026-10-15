package cn.lunadeer.mc.deerlingbot.managers;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventIoServiceTest {
    @Test
    void sameUserStaysOrderedWhileAnotherStripeCanProgress() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch other = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        try (EventIoService service = service(2, 8, 1)) {
            assertTrue(service.submitCommand(42, () -> {
                started.countDown();
                await(release);
                calls.add("bind");
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTrue(service.submitMessage(42, () -> calls.add("message")));
            assertTrue(service.submitDeletion(42, () -> { calls.add("leave"); finished.countDown(); }));
            assertTrue(service.submitMessage(43, other::countDown));
            assertTrue(other.await(2, TimeUnit.SECONDS));
            assertTrue(calls.isEmpty());
            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("bind", "message", "leave"), calls);
        } finally {
            release.countDown();
        }
    }

    @Test
    void boundedQueueReservesCapacityAndEvictsChatForDeletion() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch deleted = new CountDownLatch(1);
        Runnable chat = mock(Runnable.class);
        Runnable command = mock(Runnable.class);
        try (EventIoService service = service(1, 2, 1)) {
            service.submitCommand(1, () -> { started.countDown(); await(release); });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTrue(service.submitMessage(2, chat));
            assertFalse(service.submitMessage(3, () -> fail("容量外的聊天不能执行")));
            assertTrue(service.submitCommand(4, command));
            assertTrue(service.submitDeletion(5, deleted::countDown));
            release.countDown();
            assertTrue(deleted.await(2, TimeUnit.SECONDS));
            verifyNoInteractions(chat);
            verify(command).run();
        } finally {
            release.countDown();
        }
    }

    @Test
    void onlyConsecutivePendingDeletionsAreCoalesced() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        try (EventIoService service = service(1, 8, 1)) {
            service.submitCommand(42, () -> { started.countDown(); await(release); });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            service.submitDeletion(42, () -> calls.add("leave"));
            service.submitDeletion(42, () -> fail("连续重复退群应合并"));
            service.submitCommand(42, () -> calls.add("bind"));
            service.submitDeletion(42, () -> { calls.add("leave"); finished.countDown(); });
            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("leave", "bind", "leave"), calls);
        } finally {
            release.countDown();
        }
    }

    @Test
    void shutdownDropsOrdinaryTasksButDrainsDeletionAndSuppressesLateSends() throws Exception {
        Logger logger = mock(Logger.class);
        EventIoService service = new EventIoService(logger, 1, 8, 1, Duration.ofSeconds(2));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Runnable dropped = mock(Runnable.class);
        CountDownLatch deleted = new CountDownLatch(1);
        try {
            service.submitCommand(42, () -> {
                started.countDown(); await(release);
                assertFalse(EventIoService.canSendFromCurrentTask());
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            service.submitMessage(42, dropped);
            service.submitDeletion(42, deleted::countDown);
            service.submitCommand(42, dropped);
            CompletableFuture<Void> closing = CompletableFuture.runAsync(service::close);
            waitUntilClosed(service);
            assertFalse(service.submitDeletion(43, () -> fail("关闭后不能接收新任务")));
            release.countDown();
            closing.get(2, TimeUnit.SECONDS);
            assertEquals(0, deleted.getCount());
            verifyNoInteractions(dropped);
            verify(logger, never()).severe(anyString());
            assertTrue(EventIoService.canSendFromCurrentTask());
        } finally {
            release.countDown();
            service.close();
        }
    }

    @Test
    void shutdownDeadlineReportsAnUnfinishedDeletion() throws Exception {
        Logger logger = mock(Logger.class);
        EventIoService service = new EventIoService(logger, 1, 4, 1, Duration.ofMillis(50));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            service.submitDeletion(42, () -> {
                started.countDown();
                while (release.getCount() > 0) {
                    try { release.await(); } catch (InterruptedException ignored) { }
                }
                finished.countDown();
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTimeout(Duration.ofSeconds(1), service::close);
            verify(logger).severe(contains("QQ 42"));
        } finally {
            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            service.close();
        }
    }

    private EventIoService service(int stripes, int capacity, int reserved) {
        return new EventIoService(mock(Logger.class), stripes, capacity, reserved, Duration.ofSeconds(2));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(4, TimeUnit.SECONDS)) throw new IllegalStateException("测试等待超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void waitUntilClosed(EventIoService service) {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            while (service.isOpen()) Thread.sleep(1);
        });
    }
}
