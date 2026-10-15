package cn.lunadeer.mc.deerlingbot.protocols.segments;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

/** OneBot 11 的数组消息与 CQ 字符串共用同一消息段入口。 */
public final class MessageParser {
    private MessageParser() {
    }

    public static List<MessageSegment> parse(Object message) {
        if (message instanceof JSONArray array) {
            // 数组字段已经是真实值，不能再进行 CQ 反转义。
            return MessageSegment.parse(array);
        }
        if (message instanceof String text) {
            return MessageSegment.parse(parseString(text));
        }
        throw new IllegalArgumentException("OneBot message 必须是消息段数组或 CQ 字符串");
    }

    static JSONArray parseString(String message) {
        JSONArray segments = new JSONArray();
        StringBuilder text = new StringBuilder();
        int cursor = 0;
        while (cursor < message.length()) {
            int start = message.indexOf("[CQ:", cursor);
            if (start < 0) {
                text.append(message, cursor, message.length());
                break;
            }
            text.append(message, cursor, start);
            int end = message.indexOf(']', start + 4);
            if (end < 0) {
                text.append(message, start, message.length());
                break;
            }
            JSONObject segment = parseCode(message.substring(start + 4, end));
            if (segment == null) {
                // 不完整或语法错误的 CQ 码按普通文本保留，不能丢掉前缀后误识别指令。
                text.append(message, start, end + 1);
            } else {
                appendText(segments, text);
                // 未支持的段仍由 MessageSegment 产生 null 占位，保持原始段顺序。
                segments.add(segment);
            }
            cursor = end + 1;
        }
        appendText(segments, text);
        return segments;
    }

    private static JSONObject parseCode(String code) {
        if (code.isEmpty() || code.indexOf('[') >= 0) return null;
        String[] fields = code.split(",", -1);
        String type = fields[0];
        if (type.isEmpty()) return null;
        JSONObject data = new JSONObject();
        for (int index = 1; index < fields.length; index++) {
            String field = fields[index];
            int separator = field.indexOf('=');
            if (separator <= 0) return null;
            data.put(field.substring(0, separator), decode(field.substring(separator + 1), true));
        }
        return new JSONObject().fluentPut("type", type).fluentPut("data", data);
    }

    private static void appendText(JSONArray segments, StringBuilder text) {
        if (text.isEmpty()) return;
        segments.add(new TextSegment(decode(text.toString(), false)).parse());
        text.setLength(0);
    }

    private static String decode(String text, boolean parameter) {
        // 先分段再反转义，且最后处理 &amp;，避免转义文本被当作 CQ 码或重复解码。
        String decoded = text.replace("&#91;", "[").replace("&#93;", "]");
        if (parameter) decoded = decoded.replace("&#44;", ",");
        return decoded.replace("&amp;", "&");
    }
}
