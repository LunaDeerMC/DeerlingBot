package cn.lunadeer.mc.deerlingbot.utils;

import cn.lunadeer.mc.deerlingbot.utils.scheduler.CancellableTask;
import cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

public class AutoReconnectWebSocket {

    private static final int MAX_PENDING_MESSAGES = 256;

    private final WebSocket.Listener listener;
    private final long reconnectDelayTicks;
    private final Function<WebSocket.Listener, CompletableFuture<WebSocket>> connector;
    private final BiFunction<Runnable, Long, CancellableTask> scheduler;
    private final int queueCapacity;
    private final Deque<String> pendingMessages = new ArrayDeque<>();

    // 所有连接和队列状态均由当前对象的同步锁保护。
    private WebSocket webSocket;
    private CompletableFuture<WebSocket> connectingFuture;
    private CancellableTask reconnectTask;
    private long generation;
    private boolean closed;
    private boolean connecting;
    private boolean reconnectScheduled;
    private boolean sending;
    private boolean draining;

    public AutoReconnectWebSocket(URI uri, WebSocket.Listener listener, int reconnectInterval) {
        this(uri, "", listener, reconnectInterval);
    }

    public AutoReconnectWebSocket(URI uri, String token, WebSocket.Listener listener, int reconnectInterval) {
        this(listener, reconnectInterval, createConnector(uri, token), Scheduler::runTaskLaterAsync,
                MAX_PENDING_MESSAGES);
    }

    // 注入连接器和调度器，便于在无服务器、无网络的环境中测试连接竞态。
    AutoReconnectWebSocket(WebSocket.Listener listener, int reconnectInterval,
                           Function<WebSocket.Listener, CompletableFuture<WebSocket>> connector,
                           BiFunction<Runnable, Long, CancellableTask> scheduler, int queueCapacity) {
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be positive");
        this.listener = Objects.requireNonNull(listener);
        this.reconnectDelayTicks = Math.max(1L, (reconnectInterval + 49L) / 50L);
        this.connector = Objects.requireNonNull(connector);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.queueCapacity = queueCapacity;
        connect();
    }

    private static Function<WebSocket.Listener, CompletableFuture<WebSocket>> createConnector(URI uri, String token) {
        HttpClient client = HttpClient.newHttpClient();
        return listener -> client.newWebSocketBuilder()
                .header("Authorization", "Bearer " + token)
                .buildAsync(uri, listener);
    }

    private synchronized void connect() {
        if (closed || connecting || webSocket != null) return;
        connecting = true;
        long attempt = ++generation;
        try {
            CompletableFuture<WebSocket> future = connector.apply(new WebSocketListenerWrapper(attempt));
            if (!closed && generation == attempt && connecting) connectingFuture = future;
            future.whenComplete((socket, error) -> {
                synchronized (this) {
                    if (closed || generation != attempt) {
                        if (socket != null) socket.abort();
                        return;
                    }
                    connectingFuture = null;
                    if (error != null) {
                        XLogger.error("WebSocket 连接失败: " + error.getMessage());
                        disconnect(attempt, webSocket);
                    } else {
                        webSocket = socket;
                        connecting = false;
                    }
                }
            });
        } catch (RuntimeException error) {
            XLogger.error("WebSocket 连接失败: " + error.getMessage());
            disconnect(attempt, webSocket);
        }
    }

    private synchronized void scheduleReconnect() {
        if (closed || connecting || webSocket != null || reconnectScheduled) return;
        reconnectScheduled = true;
        try {
            reconnectTask = scheduler.apply(() -> {
                synchronized (this) {
                    reconnectTask = null;
                    reconnectScheduled = false;
                    connect();
                }
            }, reconnectDelayTicks);
        } catch (RuntimeException error) {
            reconnectScheduled = false;
            XLogger.error("WebSocket 无法安排重连: " + error.getMessage());
        }
    }

    public synchronized void sendText(String message) {
        Objects.requireNonNull(message);
        if (closed || webSocket == null) {
            XLogger.warn("WebSocket 还没有连接，消息已丢弃");
            return;
        }
        if (pendingMessages.size() >= queueCapacity) {
            XLogger.warn("WebSocket 发送队列已满，消息已丢弃");
            return;
        }
        pendingMessages.addLast(message);
        drain();
    }

    private void drain() {
        if (draining || sending) return;
        draining = true;
        try {
            // 已完成的 future 可能立即执行回调；使用循环避免递归清空队列，
            // 异步发送时则等待当前发送完成后再继续。
            while (!closed && webSocket != null && !sending && !pendingMessages.isEmpty()) {
                WebSocket socket = webSocket;
                long attempt = generation;
                String message = pendingMessages.removeFirst();
                sending = true;
                try {
                    socket.sendText(message, true).whenComplete((ignored, error) -> {
                        synchronized (this) {
                            if (closed || generation != attempt || webSocket != socket) return;
                            sending = false;
                            if (error != null) {
                                XLogger.error("WebSocket 消息发送失败: " + error.getMessage());
                                disconnect(attempt, socket);
                            } else {
                                drain();
                            }
                        }
                    });
                } catch (RuntimeException error) {
                    XLogger.error("WebSocket 消息发送失败: " + error.getMessage());
                    disconnect(attempt, socket);
                }
            }
        } finally {
            draining = false;
        }
    }

    private void disconnect(long attempt, WebSocket socket) {
        if (closed || generation != attempt) return;
        ++generation; // 使旧连接的迟到回调失效，防止影响新连接。
        webSocket = null;
        connecting = false;
        sending = false;
        discardPendingMessages();
        if (connectingFuture != null) {
            connectingFuture.cancel(true);
            connectingFuture = null;
        }
        if (socket != null) socket.abort();
        scheduleReconnect();
    }

    private void discardPendingMessages() {
        if (!pendingMessages.isEmpty()) {
            XLogger.warn("WebSocket 连接结束，丢弃 " + pendingMessages.size() + " 条待发送消息");
            pendingMessages.clear();
        }
        // 不重放旧 OneBot 操作，避免重复执行踢人、禁言等管理动作。
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        ++generation;
        discardPendingMessages();
        sending = false;
        connecting = false;
        reconnectScheduled = false;
        if (reconnectTask != null) {
            reconnectTask.cancel();
            reconnectTask = null;
        }
        if (connectingFuture != null) {
            connectingFuture.cancel(true);
            connectingFuture = null;
        }
        if (webSocket != null) {
            WebSocket socket = webSocket;
            webSocket = null;
            // 停用插件时立即释放连接，避免遗留未完成的发送或关闭握手。
            socket.abort();
        }
    }

    private class WebSocketListenerWrapper implements WebSocket.Listener {
        private final long attempt;

        private WebSocketListenerWrapper(long attempt) {
            this.attempt = attempt;
        }

        @Override
        public void onOpen(WebSocket socket) {
            synchronized (AutoReconnectWebSocket.this) {
                if (closed || generation != attempt) {
                    socket.abort();
                    return;
                }
                webSocket = socket;
                connecting = false;
                XLogger.info("WebSocket 连接成功");
                listener.onOpen(socket);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            synchronized (AutoReconnectWebSocket.this) {
                if (closed || generation != attempt || webSocket != socket) return null;
                return listener.onText(socket, data, last);
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            synchronized (AutoReconnectWebSocket.this) {
                if (closed || generation != attempt) return null;
                XLogger.warn("WebSocket 连接关闭: " + reason);
                disconnect(attempt, socket);
                return listener.onClose(socket, statusCode, reason);
            }
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            synchronized (AutoReconnectWebSocket.this) {
                if (closed || generation != attempt) return;
                XLogger.error("WebSocket 发生错误: " + error.getMessage());
                disconnect(attempt, socket);
                listener.onError(socket, error);
            }
        }
    }
}
