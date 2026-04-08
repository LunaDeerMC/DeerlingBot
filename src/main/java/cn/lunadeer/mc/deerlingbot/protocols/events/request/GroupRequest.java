package cn.lunadeer.mc.deerlingbot.protocols.events.request;

import com.alibaba.fastjson2.JSONObject;

public class GroupRequest extends Request {

    public enum SubType {
        add,
        invite,
    }

    private final long group_id;
    private final SubType sub_type;

    public GroupRequest(long time,
                        long self_id,
                        long group_id,
                        long user_id,
                        String comment,
                        String flag,
                        SubType sub_type
    ) {
        super(time, self_id, user_id, comment, flag);
        this.group_id = group_id;
        this.sub_type = sub_type;
    }

    public long getGroupId() {
        return group_id;
    }

    public SubType getSubType() {
        return sub_type;
    }

    public static GroupRequest parse(JSONObject jsonObject) {
        return new GroupRequest(
                jsonObject.getLongValue("time"),
                jsonObject.getLongValue("self_id"),
                jsonObject.getLongValue("group_id"),
                jsonObject.getLongValue("user_id"),
                jsonObject.getString("comment"),
                jsonObject.getString("flag"),
                SubType.valueOf(jsonObject.getString("sub_type"))
        );
    }
}