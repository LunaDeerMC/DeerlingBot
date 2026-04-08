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

    public static void SetGroupKick(long groupID, long userID, boolean rejectAddRequest) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", "set_group_kick");
        JSONObject params = new JSONObject();
        params.put("group_id", groupID);
        params.put("user_id", userID);
        params.put("reject_add_request", rejectAddRequest);
        jsonObject.put("params", params);
        CoreConnector.getInstance().send(jsonObject);
    }

    public static void SetGroupAddRequest(String flag, String subType, boolean approve, String reason) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", "set_group_add_request");
        JSONObject params = new JSONObject();
        params.put("flag", flag);
        params.put("sub_type", subType);
        params.put("approve", approve);
        params.put("reason", reason == null ? "" : reason);
        jsonObject.put("params", params);
        CoreConnector.getInstance().send(jsonObject);
    }
}
