package cn.lunadeer.mc.deerlingbot.protocols.events.notice;

import com.alibaba.fastjson2.JSONObject;

public class GroupAdmin extends Group {

    public enum SubType {
        set,
        unset,
    }

    private final SubType sub_type;

    public GroupAdmin(long time,
                      long self_id,
                      long group_id,
                      long user_id,
                      SubType sub_type
    ) {
        super(time, self_id, group_id, user_id);
        this.sub_type = sub_type;
    }

    public SubType getSubType() {
        return sub_type;
    }

    public static GroupAdmin parse(JSONObject jsonObject) {
        return new GroupAdmin(
                jsonObject.getLongValue("time"),
                jsonObject.getLongValue("self_id"),
                jsonObject.getLongValue("group_id"),
                jsonObject.getLongValue("user_id"),
                SubType.valueOf(jsonObject.getString("sub_type"))
        );
    }
}
