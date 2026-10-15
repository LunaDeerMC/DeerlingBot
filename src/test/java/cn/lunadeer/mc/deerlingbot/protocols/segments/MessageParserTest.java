package cn.lunadeer.mc.deerlingbot.protocols.segments;

import cn.lunadeer.mc.deerlingbot.protocols.events.message.GroupMessage;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.Message;
import cn.lunadeer.mc.deerlingbot.protocols.events.message.PrivateMessage;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageParserTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void groupAndPrivateEventsAcceptPlainStringAndKeepRawMessage(boolean group) {
        Message event = parseEvent(group, "/list");
        assertEquals("/list", text(event.getMessage().get(0)));
        assertEquals("原始消息字段保持不变", event.getRawMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void groupAndPrivateEventsAcceptMixedCqString(boolean group) {
        Message event = parseEvent(group, "[CQ:reply,id=-42][CQ:at,qq=123456] 后续文本&amp;&#91;完成&#93;");
        assertEquals(3, event.getMessage().size());
        assertEquals(-42, assertInstanceOf(ReplySegment.class, event.getMessage().get(0)).getMessageID());
        assertEquals(123456, assertInstanceOf(MentionSegment.class, event.getMessage().get(1)).getId());
        assertEquals(" 后续文本&[完成]", text(event.getMessage().get(2)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void groupAndPrivateEventsAcceptAllMemberCqMentions(boolean group) {
        Message event = parseEvent(group, "[CQ:at,qq=all] 全体成员");
        assertTrue(assertInstanceOf(MentionSegment.class, event.getMessage().get(0)).isAll());
        assertEquals(" 全体成员", text(event.getMessage().get(1)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void arrayTextRemainsLiteralAndAdjacentSegmentsKeepTheirBoundaries(boolean group) {
        JSONArray message = new JSONArray(
                new TextSegment("&#91;CQ:at,qq=123456&#93;&amp;").parse(),
                new TextSegment("/list").parse());
        Message event = parseEvent(group, message);
        assertEquals(2, event.getMessage().size());
        assertEquals("&#91;CQ:at,qq=123456&#93;&amp;", text(event.getMessage().get(0)));
        assertEquals("/list", text(event.getMessage().get(1)));
    }

    @Test
    void escapedCqRemainsTextAndIsDecodedOnlyOnce() {
        List<MessageSegment> segments = MessageParser.parse(
                "&#91;CQ:at,qq=all&#93; &amp;#91; &amp;amp; &#44;");
        assertEquals(1, segments.size());
        assertEquals("[CQ:at,qq=all] &#91; &amp; &#44;", text(segments.get(0)));
    }

    @Test
    void parametersSplitBeforeDecodingAndAllowEqualsSpacesAndNewlines() {
        JSONArray segments = MessageParser.parseString(
                "[CQ:share,title=标题&#44;含=等号与 空格\n换行,url=https://example.test/?a=1&amp;b=&#91;x&#93;&amp;#44;]");
        JSONObject data = segments.getJSONObject(0).getJSONObject("data");
        assertEquals("标题,含=等号与 空格\n换行", data.getString("title"));
        assertEquals("https://example.test/?a=1&b=[x]&#44;", data.getString("url"));
        assertEquals(2, data.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[CQ:image,file=test.png]/list", "[CQ:shake]/list", "[CQ:future,key=value]/list"})
    void unsupportedSegmentsKeepTheirPositionBeforeCommandLikeText(String message) {
        List<MessageSegment> segments = MessageParser.parse(message);
        assertEquals(2, segments.size());
        assertNull(segments.get(0));
        assertEquals("/list", text(segments.get(1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[CQ:at,qq=123456", "[CQ:]/list", "[CQ:at,qq]/list", "[CQ:at,qq=123456,]/list",
            "[CQ:at,qq=[CQ:at,qq=123456]/list"})
    void incompleteOrMalformedCqIsPreservedAsText(String message) {
        List<MessageSegment> segments = MessageParser.parse(message);
        assertEquals(1, segments.size());
        assertEquals(message, text(segments.get(0)));
    }

    @Test
    void emptyStringAndEmptyArrayContainNoSegments() {
        assertTrue(MessageParser.parse("").isEmpty());
        assertTrue(MessageParser.parse(new JSONArray()).isEmpty());
    }

    @Test
    void otherMessageTypesAreRejectedInsteadOfBeingCoercedToJson() {
        assertThrows(IllegalArgumentException.class, () -> MessageParser.parse(new JSONObject()));
        assertThrows(IllegalArgumentException.class, () -> MessageParser.parse(42));
        assertThrows(IllegalArgumentException.class, () -> MessageParser.parse(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[CQ:at,qq=bogus]/list", "[CQ:reply,id=bogus]/list", "[CQ:at]/list"})
    void invalidSupportedSegmentFieldsRejectWholeMessage(String message) {
        assertThrows(RuntimeException.class, () -> MessageParser.parse(message));
    }

    private static String text(MessageSegment segment) {
        return assertInstanceOf(TextSegment.class, segment).getText();
    }

    private static Message parseEvent(boolean group, Object message) {
        JSONObject event = new JSONObject()
                .fluentPut("time", 1L)
                .fluentPut("self_id", 999001L)
                .fluentPut("sub_type", group ? "normal" : "friend")
                .fluentPut("message_id", 2)
                .fluentPut("group_id", 10001L)
                .fluentPut("user_id", 123456L)
                .fluentPut("message", message)
                .fluentPut("raw_message", "原始消息字段保持不变")
                .fluentPut("sender", new JSONObject().fluentPut("user_id", 123456L).fluentPut("nickname", "测试用户"));
        return group ? GroupMessage.parse(event) : PrivateMessage.parse(event);
    }
}
