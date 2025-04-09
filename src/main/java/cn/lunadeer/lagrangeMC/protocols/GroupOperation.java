package cn.lunadeer.lagrangeMC.protocols;

import cn.lunadeer.lagrangeMC.managers.CoreConnector;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Collections;

import static cn.lunadeer.lagrangeMC.protocols.MessageSegment.TextSegment;

public class GroupOperation {

    public static void SendGroupMessage(long groupID, JSONObject... messages) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", "send_group_msg");
        JSONObject params = new JSONObject();
        JSONArray messagesArray = new JSONArray();
        Collections.addAll(messagesArray, messages);
        params.put("message", messagesArray);
        params.put("auto_escape", false);
        params.put("group_id", groupID);
        jsonObject.put("params", params);
        CoreConnector.getInstance().send(jsonObject);
    }

    public static void SendGroupMessage(long groupID, String message) {
        SendGroupMessage(groupID, TextSegment(message));
    }

    public static void SetGroupCard(long groupID, long userID, String card) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", "set_group_card");
        JSONObject params = new JSONObject();
        params.put("user_id", userID);
        params.put("card", card);
        params.put("group_id", groupID);
        jsonObject.put("params", params);
        CoreConnector.getInstance().send(jsonObject);
    }

}
