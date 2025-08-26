package cn.lunadeer.mc.deerlingbot.protocols.events.message;

import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

public class PrivateMessage extends Message {

    public enum SubType {
        friend,
        group,
        other
    }

    public static class Sender {
        private final long userId;
        private final String nickname;
        private final String sex;
        private final int age;

        public Sender(long userId,
                      String nickname,
                      String sex,
                      int age) {
            this.userId = userId;
            this.nickname = nickname;
            this.sex = sex;
            this.age = age;
        }

        public long getUserId() {
            return userId;
        }

        public String getNickname() {
            return nickname;
        }

        public String getSex() {
            return sex;
        }

        public int getAge() {
            return age;
        }
    }

    private final Sender sender;
    private final SubType subType;

    public PrivateMessage(long time,
                          long self_id,
                          SubType subType,
                          int messageId,
                          long userId,
                          List<MessageSegment> message,
                          String rawMessage,
                          Sender sender
    ) {
        super(time, self_id, messageId, userId, message, rawMessage);
        this.sender = sender;
        this.subType = subType;
    }

    public Sender getSender() {
        return sender;
    }

    public SubType getSubType() {
        return subType;
    }

    public static PrivateMessage parse(JSONObject jsonObject) {
        JSONObject senderObj = jsonObject.getJSONObject("sender");
        Sender sender = new Sender(
                senderObj.getLongValue("user_id"),
                senderObj.getString("nickname"),
                senderObj.getString("sex"),
                senderObj.getIntValue("age")
        );
        return new PrivateMessage(
                jsonObject.getLongValue("time"),
                jsonObject.getLongValue("self_id"),
                SubType.valueOf(jsonObject.getString("sub_type")),
                jsonObject.getIntValue("message_id"),
                jsonObject.getLongValue("user_id"),
                MessageSegment.parse(jsonObject.getJSONArray("message")),
                jsonObject.getString("raw_message"),
                sender
        );
    }
}
