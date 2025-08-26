package cn.lunadeer.mc.deerlingbot.protocols.events.message;

import cn.lunadeer.mc.deerlingbot.protocols.segments.MessageSegment;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

public class GroupMessage extends Message {

    public enum SubType {
        normal,
        anonymous,
        notice,
    }

    private final long groupID;
    private final Sender sender;
    private final SubType subType;

    public static class Sender extends PrivateMessage.Sender {

        private final String card;
        private final String area;
        private final String level;
        private final String role;
        private final String title;

        public Sender(long userId,
                      String nickname,
                      String sex,
                      int age,
                      String card,
                      String area,
                      String level,
                      String role,
                      String title
        ) {
            super(userId, nickname, sex, age);
            this.card = card;
            this.area = area;
            this.level = level;
            this.role = role;
            this.title = title;
        }

        public String getCard() {
            return card;
        }

        public String getArea() {
            return area;
        }

        public String getLevel() {
            return level;
        }

        public String getRole() {
            return role;
        }

        public String getTitle() {
            return title;
        }
    }


    public GroupMessage(long time,
                        long self_id,
                        SubType subType,
                        int messageId,
                        long groupID,
                        long userId,
                        List<MessageSegment> message,
                        String rawMessage,
                        Sender sender) {
        super(time, self_id, messageId, userId, message, rawMessage);
        this.sender = sender;
        this.groupID = groupID;
        this.subType = subType;
    }

    public Sender getSender() {
        return sender;
    }

    public long getGroupID() {
        return groupID;
    }

    public SubType getSubType() {
        return subType;
    }

    public static GroupMessage parse(JSONObject jsonObject) {
        JSONObject senderObj = jsonObject.getJSONObject("sender");
        Sender sender = new Sender(
                senderObj.getLongValue("user_id"),
                senderObj.getString("nickname"),
                senderObj.getString("sex"),
                senderObj.getIntValue("age"),
                senderObj.getString("card"),
                senderObj.getString("area"),
                senderObj.getString("level"),
                senderObj.getString("role"),
                senderObj.getString("title")
        );
        return new GroupMessage(
                jsonObject.getLongValue("time"),
                jsonObject.getLongValue("self_id"),
                SubType.valueOf(jsonObject.getString("sub_type")),
                jsonObject.getIntValue("message_id"),
                jsonObject.getLongValue("group_id"),
                jsonObject.getLongValue("user_id"),
                MessageSegment.parse(jsonObject.getJSONArray("message")),
                jsonObject.getString("raw_message"),
                sender
        );
    }
}
