package cn.lunadeer.mc.deerlingbot.protocols.segments;

import com.alibaba.fastjson2.JSONObject;

public class TextSegment extends MessageSegment {

    private final String text;

    public TextSegment(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public JSONObject parse() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "text");
        jsonObject.put("data", new JSONObject().fluentPut("text", text));
        return jsonObject;
    }

    @Override
    public SegmentType getType() {
        return SegmentType.text;
    }
}
