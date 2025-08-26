package cn.lunadeer.mc.deerlingbot.protocols.segments;

import com.alibaba.fastjson2.JSONObject;

public class ReplySegment extends MessageSegment {

    private final long messageID;

    public ReplySegment(long messageID) {
        this.messageID = messageID;
    }

    public long getMessageID() {
        return messageID;
    }

    @Override
    public JSONObject parse() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "reply");
        jsonObject.put("data", new JSONObject().fluentPut("id", String.valueOf(messageID)));
        return jsonObject;
    }

    @Override
    public SegmentType getType() {
        return SegmentType.reply;
    }
}
