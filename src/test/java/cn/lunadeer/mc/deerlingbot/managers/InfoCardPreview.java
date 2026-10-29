package cn.lunadeer.mc.deerlingbot.managers;

import com.alibaba.fastjson2.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline preview using a saved stats JSON. Player samples stay outside the repository. */
public final class InfoCardPreview {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("stats-json avatar-or-dash player-name last-join output-png");
        PlayerCardProfile profile = PlayerCardProfile.fromJson(JSONObject.parseObject(Files.readString(Path.of(args[0]))));
        BufferedImage avatar = "-".equals(args[1]) ? null : ImageIO.read(Path.of(args[1]).toFile());
        PlayerSnapshotService.InfoSnapshot snapshot = new PlayerSnapshotService.InfoSnapshot(false, args[2], args[3],
                "", "", "", "", profile);
        Path output = Path.of(args[4]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        BufferedImage image = new InfoCardRenderer().render(snapshot, avatar);
        if (!ImageIO.write(image, "PNG", output.toFile())) throw new IllegalStateException("PNG writer unavailable");
        System.out.println("Preview: " + output + " (" + image.getWidth() + "x" + image.getHeight() + ")");
    }
}
