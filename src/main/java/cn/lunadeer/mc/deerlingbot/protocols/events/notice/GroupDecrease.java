package cn.lunadeer.mc.deerlingbot.protocols.events.notice;

import com.alibaba.fastjson2.JSONObject;

public class GroupDecrease extends Group {

    private final SubType sub_type;
    private final long operator_id;

    public enum SubType {
        leave,
        kick,
        kick_me,
    }

    public GroupDecrease(long time,
                         long self_id,
                         long group_id,
                         long user_id,
                         SubType sub_type,
                         long operator_id
    ) {
        super(time, self_id, group_id, user_id);
        this.sub_type = sub_type;
        this.operator_id = operator_id;
    }


    public SubType getSubType() {
        return sub_type;
    }

    public long getOperatorId() {
        return operator_id;
    }

    public static GroupDecrease parse(JSONObject jsonObject) {
        return new GroupDecrease(
                jsonObject.getLong("time"),
                jsonObject.getLong("self_id"),
                jsonObject.getLong("group_id"),
                jsonObject.getLong("user_id"),
                SubType.valueOf(jsonObject.getString("sub_type")),
                jsonObject.getLong("operator_id")
        );
    }
}
