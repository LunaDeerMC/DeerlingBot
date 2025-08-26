package cn.lunadeer.mc.deerlingbot.protocols.events.notice;

import cn.lunadeer.mc.deerlingbot.protocols.events.AbstractPost;

public abstract class Group extends AbstractPost {

    private final long group_id;
    private final long user_id;

    public Group(long time,
                 long self_id,
                 long group_id,
                 long user_id
    ) {
        super(time, self_id);
        this.group_id = group_id;
        this.user_id = user_id;
    }

    public long getGroupId() {
        return group_id;
    }

    public long getUserId() {
        return user_id;
    }
}
