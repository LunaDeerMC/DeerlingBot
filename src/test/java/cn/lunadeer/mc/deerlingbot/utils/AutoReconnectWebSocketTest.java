package cn.lunadeer.mc.deerlingbot.utils;

import cn.lunadeer.mc.deerlingbot.utils.scheduler.CancellableTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutoReconnectWebSocketTest {

    private final List<Connection> connections = new ArrayList<>();
    private final List<Retry> retries = new ArrayList<>();
    private final WebSocket.Listener delegate = mock(WebSocket.Listener.class);
    private MockedStatic<XLogger> logger;
    private AutoReconnectWebSocket client;

    @BeforeEach
    void mockLogging() {
        logger = mockStatic(XLogger.class);
    }

    @AfterEach
    void close() {
        if (client != null) client.close();
        logger.close();
    }

    private void createClient(int capacity) {
        client = new AutoReconnectWebSocket(delegate, 5000, listener -> {
            Connection connection = new Connection(listener);
            connections.add(connection);
            return connection.future;
        }, (task, ticks) -> {
            assertEquals(100L, ticks);
            Retry retry = new Retry(task);
            retries.add(retry);
            return retry;
        }, capacity);
    }

    @Test
    void waitsForEachSendToCompleteBeforeStartingTheNext() {
        createClient(4);
        WebSocket socket = connections.get(0).open();
        CompletableFuture<WebSocket> first = new CompletableFuture<>();
        CompletableFuture<WebSocket> second = new CompletableFuture<>();
        when(socket.sendText("first", true)).thenReturn(first);
        when(socket.sendText("second", true)).thenReturn(second);

        client.sendText("first");
        client.sendText("second");
        client.sendText("third");
        verify(socket, never()).sendText("second", true);
        first.complete(socket);
        verify(socket).sendText("second", true);
        verify(socket, never()).sendText("third", true);
        second.complete(socket);
        verify(socket).sendText("third", true);
    }

    @Test
    void dropsAndLogsMessagesWhenTheWaitingQueueIsFull() {
        createClient(2);
        WebSocket socket = connections.get(0).open();
        CompletableFuture<WebSocket> first = new CompletableFuture<>();
        when(socket.sendText("in flight", true)).thenReturn(first);

        client.sendText("in flight");
        client.sendText("queued 1");
        client.sendText("queued 2");
        client.sendText("overflow");
        logger.verify(() -> XLogger.warn(contains("发送队列已满")));
        first.complete(socket);
        verify(socket).sendText("queued 1", true);
        verify(socket).sendText("queued 2", true);
        verify(socket, never()).sendText("overflow", true);
    }

    @Test
    void immediateCompletionDrainsABacklogWithoutRecursiveCallbacks() {
        createClient(10000);
        WebSocket socket = connections.get(0).open();
        CompletableFuture<WebSocket> first = new CompletableFuture<>();
        when(socket.sendText("first", true)).thenReturn(first);
        client.sendText("first");
        for (int i = 0; i < 10000; i++) client.sendText("queued");

        first.complete(socket);

        verify(socket, times(10000)).sendText("queued", true);
        assertTrue(retries.isEmpty());
    }

    @Test
    void failedSendDropsQueuedActionsAndReconnectsOnlyOnce() {
        createClient(4);
        Connection initial = connections.get(0);
        WebSocket socket = initial.open();
        CompletableFuture<WebSocket> first = new CompletableFuture<>();
        when(socket.sendText("first", true)).thenReturn(first);
        client.sendText("first");
        client.sendText("old kick action");

        first.completeExceptionally(new IOException("broken pipe"));
        initial.listener.onError(socket, new IOException("same disconnect"));
        initial.listener.onClose(socket, 1006, "same disconnect");
        client.sendText("offline ban action");

        assertEquals(1, retries.size());
        verify(socket).abort();
        logger.verify(() -> XLogger.error(contains("消息发送失败")));
        retries.get(0).task.run();
        WebSocket replacement = connections.get(1).open();
        client.sendText("new action");
        verify(socket, never()).sendText("old kick action", true);
        verify(replacement).sendText("new action", true);
        verify(replacement, times(1)).sendText(anyString(), eq(true));
    }

    @Test
    void lateCallbacksCannotClearOrDisconnectTheReplacementConnection() {
        createClient(4);
        Connection initial = connections.get(0);
        WebSocket oldSocket = initial.open();
        CompletableFuture<WebSocket> oldSend = new CompletableFuture<>();
        when(oldSocket.sendText("old", true)).thenReturn(oldSend);
        client.sendText("old");
        initial.listener.onClose(oldSocket, 1001, "going away");
        retries.get(0).task.run();
        WebSocket replacement = connections.get(1).open();
        CompletableFuture<WebSocket> newSend = new CompletableFuture<>();
        when(replacement.sendText("new first", true)).thenReturn(newSend);
        client.sendText("new first");
        client.sendText("new second");

        oldSend.completeExceptionally(new IOException("late send error"));
        initial.listener.onError(oldSocket, new IOException("late socket error"));
        initial.listener.onText(oldSocket, "stale event", true);
        verify(replacement, never()).abort();
        verify(replacement, never()).sendText("new second", true);
        verify(delegate, never()).onText(oldSocket, "stale event", true);
        assertEquals(1, retries.size());
        newSend.complete(replacement);
        verify(replacement).sendText("new second", true);
    }

    @Test
    void closeDiscardsPendingSendsAndPreventsReconnect() {
        createClient(4);
        Connection initial = connections.get(0);
        WebSocket socket = initial.open();
        CompletableFuture<WebSocket> first = new CompletableFuture<>();
        when(socket.sendText("first", true)).thenReturn(first);
        client.sendText("first");
        client.sendText("pending");

        client.close();
        client.close();
        first.complete(socket);
        initial.listener.onClose(socket, 1000, "closed");
        client.sendText("after close");

        verify(socket, times(1)).abort();
        verify(socket, times(1)).sendText(anyString(), eq(true));
        assertTrue(retries.isEmpty());
    }

    @Test
    void closeCancelsHandshakeAndAbortsASocketThatStillOpens() {
        createClient(4);
        Connection initial = connections.get(0);
        client.close();
        assertTrue(initial.future.isCancelled());

        WebSocket lateSocket = mock(WebSocket.class);
        initial.listener.onOpen(lateSocket);

        verify(lateSocket).abort();
        verify(delegate, never()).onOpen(any());
        assertTrue(retries.isEmpty());
    }

    @Test
    void closeAbortsLateHandshakeCompletionEvenIfCancellationLosesTheRace() {
        CompletableFuture<WebSocket> handshake = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }
        };
        client = new AutoReconnectWebSocket(delegate, 5000, ignored -> handshake,
                (task, ticks) -> fail("must not reconnect after close"), 4);
        client.close();
        WebSocket lateSocket = mock(WebSocket.class);

        handshake.complete(lateSocket);

        verify(lateSocket).abort();
    }

    @Test
    void closeCancelsScheduledRetryAndIgnoresAlreadyDispatchedRetry() {
        createClient(4);
        Connection initial = connections.get(0);
        WebSocket socket = initial.open();
        initial.listener.onError(socket, new IOException("disconnect"));

        client.close();
        assertTrue(retries.get(0).cancelled);
        retries.get(0).task.run();

        assertEquals(1, connections.size());
    }

    @Test
    void connectionFailureSchedulesAnotherAttempt() {
        createClient(4);
        connections.get(0).future.completeExceptionally(new IOException("refused"));

        assertEquals(1, retries.size());
        retries.get(0).task.run();
        assertEquals(2, connections.size());
        connections.get(1).open();
        logger.verify(() -> XLogger.error(contains("连接失败")));
    }

    @Test
    void synchronousSendFailureIsLoggedAndReconnects() {
        createClient(4);
        WebSocket socket = connections.get(0).open();
        when(socket.sendText("broken", true)).thenThrow(new IllegalStateException("closed output"));

        assertDoesNotThrow(() -> client.sendText("broken"));

        verify(socket).abort();
        assertEquals(1, retries.size());
        logger.verify(() -> XLogger.error(contains("消息发送失败")));
    }

    @Test
    void schedulerFailureIsLoggedAndCloseRemainsSafe() {
        CompletableFuture<WebSocket> handshake = new CompletableFuture<>();
        client = new AutoReconnectWebSocket(delegate, 5000, ignored -> handshake,
                (task, ticks) -> { throw new IllegalStateException("plugin disabled"); }, 4);

        handshake.completeExceptionally(new IOException("refused"));

        logger.verify(() -> XLogger.error(contains("无法安排重连")));
        assertDoesNotThrow(client::close);
    }

    @Test
    void nullableCancellationHandleDoesNotBreakClose() {
        CompletableFuture<WebSocket> handshake = new CompletableFuture<>();
        client = new AutoReconnectWebSocket(delegate, 5000, ignored -> handshake,
                (task, ticks) -> null, 4);
        handshake.completeExceptionally(new IOException("refused"));

        assertDoesNotThrow(client::close);
    }

    private static class Connection {
        final WebSocket.Listener listener;
        final CompletableFuture<WebSocket> future = new CompletableFuture<>();

        Connection(WebSocket.Listener listener) {
            this.listener = listener;
        }

        WebSocket open() {
            WebSocket socket = mock(WebSocket.class);
            when(socket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(socket));
            listener.onOpen(socket);
            future.complete(socket);
            return socket;
        }
    }

    private static class Retry implements CancellableTask {
        final Runnable task;
        boolean cancelled;

        Retry(Runnable task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
