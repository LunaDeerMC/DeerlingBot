package cn.lunadeer.mc.deerlingbot.managers;

import cn.lunadeer.mc.deerlingbot.configuration.Configuration;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.PrivateMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.notice.GroupAdmin;
import cn.lunadeer.mc.deerlingbot.protocols.events.notice.GroupBan;
import cn.lunadeer.mc.deerlingbot.protocols.events.notice.GroupDecrease;
import cn.lunadeer.mc.deerlingbot.protocols.events.notice.GroupIncrease;
import cn.lunadeer.mc.deerlingbot.protocols.events.request.GroupRequest;
import cn.lunadeer.mc.deerlingbot.utils.AutoReconnectWebSocket;
import cn.lunadeer.mc.deerlingbot.utils.XLogger;
import com.alibaba.fastjson2.JSONObject;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

import static cn.lunadeer.mc.deerlingbot.protocols.events.AbstractPost.PostType.*;
import static cn.lunadeer.mc.deerlingbot.protocols.events.notice.NoticeType.*;

public class CoreConnector {

    private static CoreConnector instance;
    private AutoReconnectWebSocket webSocketInstance;

    private String botName = "";

    public CoreConnector() {
        instance = this;
    }

    public static CoreConnector getInstance() {
        if (instance == null) {
            instance = new CoreConnector();
        }
        return instance;
    }

    public void open() {
        URI uri = URI.create("ws://" + Configuration.oneBotWebSocket.host + ":" + Configuration.oneBotWebSocket.port);
        webSocketInstance = new AutoReconnectWebSocket(uri, Configuration.oneBotWebSocket.token, listener, 5000);
    }

    public void close() {
        if (webSocketInstance != null) {
            webSocketInstance.close();
            webSocketInstance = null;
        }
    }

    private final WebSocket.Listener listener = new WebSocket.Listener() {

        // 用于累积接收到的文本帧
        private final StringBuilder messageBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            messageBuffer.setLength(0); // 新连接不能继承旧连接未收完的分片。
            webSocket.request(1); // Request one message
            cn.lunadeer.mc.deerlingbot.utils.scheduler.Scheduler.runTask(
                    cn.lunadeer.mc.deerlingbot.DeerlingBot::notifyBotConnected);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence charSeq, boolean last) {
            messageBuffer.append(charSeq);
            if (!last) {
                webSocket.request(1);
                return null;
            }
            String rawMessage = messageBuffer.toString();
            messageBuffer.setLength(0); // 清空缓冲区
            webSocket.request(1);
            try {
                handleMessage(rawMessage);
            } catch (RuntimeException e) {
                // 隔离整条上报的解析/调度异常，让后续消息继续使用当前连接。
                XLogger.warn("忽略无法处理的 OneBot 消息: {0}", e.getMessage());
                XLogger.debug("无效消息内容: {0}", rawMessage);
            }
            return null;
        }
    };

    private void handleMessage(String rawMessage) {
        JSONObject jsonObject = JSONObject.parseObject(rawMessage);
        if (jsonObject == null) throw new IllegalArgumentException("OneBot 消息必须是 JSON 对象");
        String postType = jsonObject.getString("post_type");
        if (message.name().equals(postType)) {
            String messageType = jsonObject.getString("message_type");
            if ("group".equals(messageType)) {
                GroupMessage.parse(jsonObject).call();
            } else if ("private".equals(messageType)) {
                PrivateMessage.parse(jsonObject).call();
            } else {
                return;
            }
        } else if (notice.name().equals(postType)) {
            String noticeType = jsonObject.getString("notice_type");
            if (group_admin.name().equals(noticeType)) {
                GroupAdmin.parse(jsonObject).call();
            } else if (group_increase.name().equals(noticeType)) {
                GroupIncrease.parse(jsonObject).call();
            } else if (group_decrease.name().equals(noticeType)) {
                GroupDecrease.parse(jsonObject).call();
            } else if (group_ban.name().equals(noticeType)) {
                GroupBan.parse(jsonObject).call();
            } else {
                return;
            }
        } else if (request.name().equals(postType)) {
            String requestType = jsonObject.getString("request_type");
            if ("group".equals(requestType)) {
                GroupRequest.parse(jsonObject).call();
            } else {
                return;
            }
        } else if (meta_event.name().equals(postType)) {
            XLogger.debug("收到 meta_event: {0}", rawMessage);
            String metaEventType = jsonObject.getString("meta_event_type");
            if ("lifecycle".equals(metaEventType)) {
                String subType = jsonObject.getString("sub_type");
                if ("connect".equals(subType)) {
                    XLogger.debug("连接成功，原始数据：{0}", rawMessage);
                } else if ("heartbeat".equals(subType)) {
                    XLogger.debug("收到心跳事件，原始数据：{0}", rawMessage);
                }
            }
        } else {
            XLogger.debug("未知的 post_type: {0}，原始数据：{1}", postType, rawMessage);
        }
    }

    public void send(JSONObject message) {
        if (!EventIoService.canSendFromCurrentTask()) return;
        if (webSocketInstance != null) {
            XLogger.debug("发送消息: {0}", message.toJSONString());
            webSocketInstance.sendText(message.toJSONString());
        } else {
            XLogger.warn("WebSocket 还未连接，无法发送消息：" + message.toJSONString());
        }
    }

    public String getBotName() {
        return botName;
    }

}
