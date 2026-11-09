package cn.lunadeer.mc.deerlingbot.managers;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Eleven shared PNG components, decoded once per plugin classloader, never once per player. */
final class AchievementArtwork {
    static final List<String> ICONS = List.of("pickaxe", "cube", "wing", "fish", "trade", "route");
    static final Map<String, String> LEVELS = Map.of("木", "wood", "铁", "iron", "铜", "copper", "钻", "diamond", "金", "gold");

    private AchievementArtwork() { }

    static BufferedImage frame(String level) { return frame(level, false); }

    static BufferedImage frame(String level, boolean muted) {
        String tier = "未解锁".equals(level) ? "wood" : LEVELS.get(level);
        if (tier == null) throw new IllegalArgumentException("Unknown achievement level: " + level);
        return (muted ? Images.MUTED : Images.ALL).get("frame-" + tier);
    }

    static BufferedImage content(String icon) { return content(icon, false); }

    static BufferedImage content(String icon, boolean muted) {
        if (!ICONS.contains(icon)) throw new IllegalArgumentException("Unknown achievement artwork: " + icon);
        return (muted ? Images.MUTED : Images.ALL).get("content-" + icon);
    }

    private static final class Images {
        private static final Map<String, BufferedImage> ALL = load();
        private static final Map<String, BufferedImage> MUTED = mute(ALL);

        private static Map<String, BufferedImage> load() {
            Map<String, BufferedImage> images = new LinkedHashMap<>();
            for (String tier : LEVELS.values()) images.put("frame-" + tier, loadImage("frame-" + tier));
            for (String icon : ICONS) images.put("content-" + icon, loadImage("content-" + icon));
            return Map.copyOf(images);
        }

        private static BufferedImage loadImage(String key) {
            String resource = "/assets/achievement-components/" + key + ".png";
            try (InputStream stream = AchievementArtwork.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Bundled achievement component is missing: " + resource);
                BufferedImage image = ImageIO.read(stream);
                if (image == null) throw new IOException("Achievement component is not a readable PNG: " + resource);
                if (!image.getColorModel().hasAlpha()) throw new IOException("Achievement component must have transparency: " + resource);
                if (image.getWidth() > 2048 || image.getHeight() > 2048)
                    throw new IOException("Achievement component exceeds the 2048px source asset budget: " + resource);
                return fitCacheBudget(image);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load player achievement component", e);
            }
        }

        /** Keep the approved source PNG intact; downsample only once for the shared render cache. */
        private static BufferedImage fitCacheBudget(BufferedImage source) {
            int longest = Math.max(source.getWidth(), source.getHeight());
            if (longest <= 512) return source;
            double scale = 512.0 / longest;
            BufferedImage result = new BufferedImage(Math.max(1, (int) Math.round(source.getWidth() * scale)),
                    Math.max(1, (int) Math.round(source.getHeight() * scale)), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = result.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(source, 0, 0, result.getWidth(), result.getHeight(), null);
            } finally {
                graphics.dispose();
            }
            return result;
        }

        private static Map<String, BufferedImage> mute(Map<String, BufferedImage> originals) {
            Map<String, BufferedImage> result = new LinkedHashMap<>();
            originals.forEach((key, source) -> {
                BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < source.getHeight(); y++) {
                    for (int x = 0; x < source.getWidth(); x++) {
                        int argb = source.getRGB(x, y);
                        int luma = (((argb >>> 16) & 255) * 54 + ((argb >>> 8) & 255) * 183 + (argb & 255) * 19) / 256;
                        gray.setRGB(x, y, (argb & 0xFF000000) | luma << 16 | luma << 8 | luma);
                    }
                }
                result.put(key, gray);
            });
            return Map.copyOf(result);
        }
    }
}
