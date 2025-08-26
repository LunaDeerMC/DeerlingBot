package cn.lunadeer.mc.deerlingbot.protocols.segments;

import com.alibaba.fastjson2.JSONObject;

import java.awt.image.BufferedImage;

import static cn.lunadeer.mc.deerlingbot.utils.Misc.imageToBase64;

public class ImageSegment extends MessageSegment {

    private final BufferedImage image;

    public ImageSegment(BufferedImage image) {
        this.image = image;
    }

    public BufferedImage getImage() {
        return image;
    }

    @Override
    public JSONObject parse() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "image");
        // base64
        jsonObject.put("data", new JSONObject().fluentPut("file", "base64://" + imageToBase64(image)));
        return jsonObject;
    }

    @Override
    public SegmentType getType() {
        return SegmentType.image;
    }
}
