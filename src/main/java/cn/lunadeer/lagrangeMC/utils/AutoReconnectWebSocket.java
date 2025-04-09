package cn.lunadeer.lagrangeMC.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

public class AutoReconnectWebSocket {

    private final URI uri;
    private final WebSocket.Listener listener;
    private final int reconnectInterval; // 重连间隔（毫秒）
    private WebSocket webSocket;
    private boolean isClosed = false;
    private String token = "";

    public AutoReconnectWebSocket(URI uri, WebSocket.Listener listener, int reconnectInterval) {
        this.uri = uri;
        this.listener = listener;
        this.reconnectInterval = reconnectInterval;
        connect();
    }

    public AutoReconnectWebSocket(URI uri, String token, WebSocket.Listener listener, int reconnectInterval) {
        this.uri = uri;
        this.listener = listener;
        this.reconnectInterval = reconnectInterval;
        this.token = token;
        connect();
    }

    private synchronized void connect() {
        if (isClosed) return;

        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder()
                .header("Authorization", "Bearer " + token)
                .buildAsync(uri, new WebSocketListenerWrapper(listener))
                .thenAccept(ws -> {
                    synchronized (this) {
                        this.webSocket = ws;
                    }
                })
                .exceptionally(e -> {
                    XLogger.error("WebSocket 连接失败: " + e.getMessage());
                    scheduleReconnect();
                    return null;
                });
    }

    private void scheduleReconnect() {
        if (isClosed) return;

        new Thread(() -> {
            try {
                Thread.sleep(reconnectInterval);
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public synchronized void sendText(String message) {
        if (webSocket != null) {
            webSocket.sendText(message, true);
        } else {
            XLogger.warn("WebSocket 还没有连接，无法发送消息: " + message);
        }
    }

    public synchronized void close() {
        isClosed = true;
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Closing").thenRun(() -> webSocket = null);
        }
    }

    private class WebSocketListenerWrapper implements WebSocket.Listener {
        private final WebSocket.Listener delegate;

        public WebSocketListenerWrapper(WebSocket.Listener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            XLogger.info("WebSocket 连接成功");
            delegate.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            return delegate.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            XLogger.warn("WebSocket 连接关闭: " + reason);
            scheduleReconnect();
            return delegate.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            XLogger.error("WebSocket 发生错误: " + error.getMessage());
            scheduleReconnect();
            delegate.onError(webSocket, error);
        }
    }
}