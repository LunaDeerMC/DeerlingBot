package cn.lunadeer.mc.deerlingbot.protocols.segments;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

public abstract class MessageSegment {
    public abstract JSONObject parse();

    public static MessageSegment parse(JSONObject jsonObject) {
        String type = jsonObject.getString("type");
        JSONObject data = jsonObject.getJSONObject("data");
        return switch (type) {
            case "at" -> new MentionSegment(data.getLong("qq"));
            case "text" -> new TextSegment(data.getString("text"));
            case "image" -> null; // no support for image segment parsing yet
            case "reply" -> new ReplySegment(data.getLong("id"));
            default -> null;
        };
    }

    public static List<MessageSegment> parse(JSONArray jsonArray) {
        return jsonArray.stream().map(obj -> parse((JSONObject) obj)).toList();
    }

    public abstract SegmentType getType();
}
