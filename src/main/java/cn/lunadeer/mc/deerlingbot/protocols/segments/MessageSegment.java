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
            case "at" -> "all".equals(data.getString("qq"))
                    ? MentionSegment.all() : new MentionSegment(data.getLong("qq"));
            case "text" -> {
                // 文本会交给异步业务处理，必须在接收边界拒绝无效字段。
                if (data == null || !(data.get("text") instanceof String text)) {
                    throw new IllegalArgumentException("文本消息段缺少有效的 text 字符串");
                }
                yield new TextSegment(text);
            }
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
