package cn.lunadeer.mc.deerlingbot.protocols.events.message;

import cn.lunadeer.mc.deerlingbot.protocols.events.AbstractPost;
import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;

import java.util.List;

public abstract class Message extends AbstractPost {

    private final int messageId;
    private final long userId;
    private final List<MessageSegment> message;
    private final String rawMessage;

    public Message(long time,
                   long self_id,
                   int messageId,
                   long userId,
                   List<MessageSegment> message,
                   String rawMessage
    ) {
        super(time, self_id);
        this.messageId = messageId;
        this.userId = userId;
        this.message = message;
        this.rawMessage = rawMessage;
    }

    public int getMessageId() {
        return messageId;
    }

    public long getUserId() {
        return userId;
    }

    public List<MessageSegment> getMessage() {
        return message;
    }

    public String getRawMessage() {
        return rawMessage;
    }
}
