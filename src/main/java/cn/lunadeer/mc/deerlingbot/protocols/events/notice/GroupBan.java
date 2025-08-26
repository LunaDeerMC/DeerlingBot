package cn.lunadeer.mc.deerlingbot.protocols.events.notice;

import com.alibaba.fastjson2.JSONObject;

public class GroupBan extends Group {

    public enum SubType {
        ban,
        lift_ban,
    }

    private final SubType sub_type;
    private final long duration;

    public GroupBan(long time,
                    long self_id,
                    long group_id,
                    long user_id,
                    SubType sub_type,
                    long duration
    ) {
        super(time, self_id, group_id, user_id);
        this.sub_type = sub_type;
        this.duration = duration;
    }

    public SubType getSubType() {
        return sub_type;
    }

    public long getDuration() {
        return duration;
    }

    public static GroupBan parse(JSONObject jsonObject) {
        return new GroupBan(
                jsonObject.getLong("time"),
                jsonObject.getLong("self_id"),
                jsonObject.getLong("group_id"),
                jsonObject.getLong("user_id"),
                SubType.valueOf(jsonObject.getString("sub_type")),
                jsonObject.getLong("duration")
        );
    }
}
