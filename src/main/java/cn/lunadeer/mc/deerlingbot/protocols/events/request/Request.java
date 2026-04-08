package cn.lunadeer.mc.deerlingbot.protocols.events.request;

import cn.lunadeer.mc.deerlingbot.protocols.events.AbstractPost;

public abstract class Request extends AbstractPost {

    private final long user_id;
    private final String comment;
    private final String flag;

    public Request(long time,
                   long self_id,
                   long user_id,
                   String comment,
                   String flag
    ) {
        super(time, self_id);
        this.user_id = user_id;
        this.comment = comment;
        this.flag = flag;
    }

    public long getUserId() {
        return user_id;
    }

    public String getComment() {
        return comment;
    }

    public String getFlag() {
        return flag;
    }
}