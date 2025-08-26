package cn.lunadeer.mc.deerlingbot.protocols.segments;

import com.alibaba.fastjson2.JSONObject;

public class MentionSegment extends MessageSegment {

    private final long id;

    public MentionSegment(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    @Override
    public JSONObject parse() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "at");
        jsonObject.put("data", new JSONObject().fluentPut("qq", String.valueOf(id)));
        return jsonObject;
    }

    @Override
    public SegmentType getType() {
        return SegmentType.mention;
    }
}
