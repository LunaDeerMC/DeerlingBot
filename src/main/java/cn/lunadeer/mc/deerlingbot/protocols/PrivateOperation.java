package cn.lunadeer.mc.deerlingbot.protocols;

import cn.lunadeer.mc.deerlingbot.managers.CoreConnector;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

public class PrivateOperation {

    public static void SendPrivateMessage(long userId, MessageSegment... messages) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", "send_private_msg");
        JSONObject params = new JSONObject();
        JSONArray messagesArray = new JSONArray();
        for (MessageSegment msg : messages) {
            messagesArray.add(msg.parse());
        }
        params.put("message", messagesArray);
        params.put("auto_escape", false);
        params.put("user_id", userId);
        jsonObject.put("params", params);
        CoreConnector.getInstance().send(jsonObject);
    }

    public static void SendPrivateMessage(long userId, String message) {
        SendPrivateMessage(userId, new TextSegment(message));
    }

}
