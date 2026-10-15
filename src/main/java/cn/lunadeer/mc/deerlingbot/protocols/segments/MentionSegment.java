package cn.lunadeer.mc.deerlingbot.protocols.segments;

import com.alibaba.fastjson2.JSONObject;

public class MentionSegment extends MessageSegment {

    private final Long id;

    public MentionSegment(long id) {
        this.id = id;
    }

    private MentionSegment() {
        this.id = null;
    }

    /** OneBot 使用字符串 all 表示全体成员，不对应任何 QQ 号。 */
    public static MentionSegment all() {
        return new MentionSegment();
    }

    public boolean isAll() {
        return id == null;
    }

    public long getId() {
        if (isAll()) throw new IllegalStateException("全体成员提及没有单独的 QQ 号");
        return id;
    }

    @Override
    public JSONObject parse() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "at");
        jsonObject.put("data", new JSONObject().fluentPut("qq", isAll() ? "all" : String.valueOf(id)));
        return jsonObject;
    }

    @Override
    public SegmentType getType() {
        return SegmentType.mention;
    }
}
