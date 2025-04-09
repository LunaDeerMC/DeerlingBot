package cn.lunadeer.lagrangeMC.protocols;

import cn.lunadeer.lagrangeMC.configuration.Configuration;
import cn.lunadeer.lagrangeMC.managers.CoreConnector;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Base64;
import java.util.Collections;

import static cn.lunadeer.lagrangeMC.utils.Misc.imageToBase64;

public class MessageSegment {

    public static JSONObject MentionSegment(long qqID) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "at");
        jsonObject.put("data", new JSONObject().fluentPut("qq", String.valueOf(qqID)));
        return jsonObject;
    }

    public static JSONObject TextSegment(String text) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "text");
        jsonObject.put("data", new JSONObject().fluentPut("text", text));
        return jsonObject;
    }

    public static JSONObject ImageSegment(BufferedImage image) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "image");
        // base64
        jsonObject.put("data", new JSONObject().fluentPut("file", "base64://" + imageToBase64(image)));
        return jsonObject;
    }

    public static JSONObject ReplySegment(long messageID) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "reply");
        jsonObject.put("data", new JSONObject().fluentPut("id", String.valueOf(messageID)));
        return jsonObject;
    }

    public static JSONObject NodeSegment(JSONObject... segments) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "node");
        JSONObject data = new JSONObject();
        data.fluentPut("user_id", Long.valueOf(Configuration.botId));
        data.fluentPut("nickname", CoreConnector.getInstance().getBotName());
        JSONArray content = new JSONArray();
        Collections.addAll(content, segments);
        data.fluentPut("content", content);
        jsonObject.put("data", data);
        return jsonObject;
    }

}
