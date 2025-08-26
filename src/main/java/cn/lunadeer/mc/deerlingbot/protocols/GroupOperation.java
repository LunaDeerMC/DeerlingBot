package cn.lunadeer.mc.deerlingbot.protocols;

import cn.lunadeer.mc.deerlingbot.managers.CoreConnector;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import cn.lunadeer.mc.deerlingbot.protocols.segments.TextSegment;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;


public class GroupOperation {

    public static void SendGroupMessage(long groupID, MessageSegment... messages) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", "send_group_msg");
        JSONObject params = new JSONObject();
        JSONArray messagesArray = new JSONArray();
        for (MessageSegment message : messages) {
            messagesArray.add(message.parse());
        }
        params.put("message", messagesArray);
        params.put("auto_escape", false);
        params.put("group_id", groupID);
        jsonObject.put("params", params);
        CoreConnector.getInstance().send(jsonObject);
    }

    public static void SendGroupMessage(long groupID, String message) {
        SendGroupMessage(groupID, new TextSegment(message));
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
