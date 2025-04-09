package cn.lunadeer.lagrangeMC.protocols;

import cn.lunadeer.lagrangeMC.managers.CoreConnector;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Collections;

import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.TextSegment;

public class PrivateOperation {

    public static void SendPrivateMessage(long userId, JSONObject... messages) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", "send_private_msg");
        JSONObject params = new JSONObject();
        JSONArray messagesArray = new JSONArray();
        Collections.addAll(messagesArray, messages);
        params.put("message", messagesArray);
        params.put("auto_escape", false);
        params.put("user_id", userId);
        jsonObject.put("params", params);
        CoreConnector.getInstance().send(jsonObject);
    }

    public static void SendPrivateMessage(long userId, String message) {
        SendPrivateMessage(userId, TextSegment(message));
    }

}
