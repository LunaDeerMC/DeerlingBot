package cn.lunadeer.lagrangeMC.managers;

import cn.lunadeer.lagrangeMC.configuration.Configuration;
import cn.lunadeer.lagrangeMC.utils.AutoReconnectWebSocket;
import cn.lunadeer.lagrangeMC.utils.XLogger;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

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

    private final static WebSocket.Listener listener = new WebSocket.Listener() {
        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1); // Request one message
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence charSeq, boolean last) {
            String sqlStr = charSeq.toString();
            webSocket.request(1);
            JSONObject jsonObject = JSONObject.parseObject(sqlStr);

            if (!jsonObject.containsKey("self_id")) return null;
            if (!jsonObject.getLong("self_id").equals(Long.parseLong(Configuration.botId))) return null;

            XLogger.debug("收到消息: {0}", sqlStr);

            JSONArray message = jsonObject.getJSONArray("message");
            if (message == null) return null;
            if (message.isEmpty()) return null;
            JSONObject firstMessage = message.getJSONObject(0);
            if (firstMessage == null) return null;
            if (!firstMessage.containsKey("type")) return null;
            if (!firstMessage.getString("type").equals("text")) return null;
            if (!firstMessage.containsKey("data")) return null;
            JSONObject data = firstMessage.getJSONObject("data");
            if (data == null) return null;
            if (!data.containsKey("text")) return null;
            String text = data.getString("text");
            if (text == null) return null;

            if (!jsonObject.containsKey("group_id") &&  // only handle private answer
                    QuestionManager.getInstance().handleAnswer(text, jsonObject)) {
                return null;
            }

            if (text.startsWith(Configuration.commandPrefix)) {
                CommandManager.getInstance().handleBotCommand(text, jsonObject);
            } else {
                if (!jsonObject.containsKey("group_id")) return null;
                if (!jsonObject.getLong("group_id").equals(Long.parseLong(Configuration.messageTransfer.groupId)))
                    return null;
                long groupID = jsonObject.getLong("group_id");

                JSONObject sender = jsonObject.getJSONObject("sender");
                if (sender == null) return null;

                if (!sender.containsKey("user_id")) return null;
                long userID = sender.getLong("user_id");

                if (!sender.containsKey("nickname")) return null;
                String nickname = sender.getString("nickname");

                if (!jsonObject.containsKey("message_id")) return null;
                long messageID = jsonObject.getLong("message_id");

                MessageManager.getInstance().handleGroupMessageToServer(groupID, userID, messageID, nickname, text);
            }
            return null;
        }
    };

    public void send(JSONObject message) {
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
