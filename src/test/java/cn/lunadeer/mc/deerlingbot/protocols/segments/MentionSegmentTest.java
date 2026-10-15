package cn.lunadeer.mc.deerlingbot.protocols.segments;

import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MentionSegmentTest {
    @Test
    void allMembersMentionParsesAndRoundTripsWithoutInventingAUserId() {
        JSONObject input = JSONObject.parseObject("{\"type\":\"at\",\"data\":{\"qq\":\"all\"}}");
        MentionSegment mention = assertInstanceOf(MentionSegment.class, MessageSegment.parse(input));
        assertTrue(mention.isAll());
        assertEquals(SegmentType.mention, mention.getType());
        assertEquals(input, mention.parse());
        assertThrows(IllegalStateException.class, mention::getId);
    }

    @Test
    void numericAndStringUserIdsKeepTheirExistingMeaning() {
        for (String value : new String[]{"1234567890", "\"1234567890\"", "\"9223372036854775807\""}) {
            JSONObject input = JSONObject.parseObject("{\"type\":\"at\",\"data\":{\"qq\":" + value + "}}");
            MentionSegment mention = assertInstanceOf(MentionSegment.class, MessageSegment.parse(input));
            assertFalse(mention.isAll());
            assertEquals(input.getJSONObject("data").getLongValue("qq"), mention.getId());
            assertEquals(String.valueOf(mention.getId()), mention.parse().getJSONObject("data").getString("qq"));
        }
    }

    @Test
    void outboundUserAndAllMentionsSerializeDistinctTargets() {
        assertEquals("42", new MentionSegment(42).parse().getJSONObject("data").getString("qq"));
        assertEquals("all", MentionSegment.all().parse().getJSONObject("data").getString("qq"));
    }

    @Test
    void realGroupEventKeepsAllMentionAndFollowingTextInOrder() {
        GroupMessage event = GroupMessage.parse(JSONObject.parseObject("""
                {"time":1,"self_id":999001,"post_type":"message","message_type":"group",
                 "sub_type":"normal","message_id":7,"group_id":10001,"user_id":42,
                 "message":[{"type":"at","data":{"qq":"all"}},
                            {"type":"text","data":{"text":" 公告"}}],
                 "raw_message":"[CQ:at,qq=all] 公告",
                 "sender":{"user_id":42,"nickname":"玩家","sex":"unknown","age":0}}
                """));
        assertEquals(2, event.getMessage().size());
        assertTrue(assertInstanceOf(MentionSegment.class, event.getMessage().get(0)).isAll());
        assertEquals(" 公告", assertInstanceOf(TextSegment.class, event.getMessage().get(1)).getText());
        assertEquals("[CQ:at,qq=all] 公告", event.getRawMessage());
    }
}
