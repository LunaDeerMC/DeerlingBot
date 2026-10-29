package cn.lunadeer.mc.deerlingbot.managers;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Seven shared PNG icons, decoded and reduced once per plugin classloader. */
final class StatisticsIconComponent {
    private static final String[] NAMES = {
            "playtime", "walking", "mining", "crafting", "fishing", "trading", "elytra"
    };
    // Covers the largest 39px icon even at the legacy card's 3x output scale.
    private static final int CACHE_SIZE = 128;

    private StatisticsIconComponent() { }

    static void draw(Graphics2D graphics, int type, int x, int y, int size) {
        if (type < 0 || type >= NAMES.length) throw new IllegalArgumentException("Unknown statistic icon: " + type);
        if (size <= 0) throw new IllegalArgumentException("Statistic icon size must be positive");
        BufferedImage image = Images.ALL[type];
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(image, x, y, size, size, null);
        } finally { g.dispose(); }
    }

    private static final class Images {
        private static final BufferedImage[] ALL = load();

        private static BufferedImage[] load() {
            BufferedImage[] images = new BufferedImage[NAMES.length];
            for (int i = 0; i < NAMES.length; i++) images[i] = loadImage(NAMES[i]);
            return images;
        }

        private static BufferedImage loadImage(String name) {
            String resource = "/assets/statistics/" + name + ".png";
            try (InputStream stream = StatisticsIconComponent.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Bundled statistic icon is missing: " + resource);
                BufferedImage source = ImageIO.read(stream);
                if (source == null) throw new IOException("Statistic icon is not a readable PNG: " + resource);
                try {
                    if (!source.getColorModel().hasAlpha())
                        throw new IOException("Statistic icon must have transparency: " + resource);
                    if (source.getWidth() > 4096 || source.getHeight() > 4096)
                        throw new IOException("Statistic icon exceeds the 4096px asset budget: " + resource);
                    BufferedImage cached = new BufferedImage(CACHE_SIZE, CACHE_SIZE, BufferedImage.TYPE_INT_ARGB_PRE);
                    Graphics2D g = cached.createGraphics();
                    try {
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        double scale = (double) CACHE_SIZE / Math.max(source.getWidth(), source.getHeight());
                        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                        g.drawImage(source, (CACHE_SIZE - width) / 2, (CACHE_SIZE - height) / 2, width, height, null);
                    } finally { g.dispose(); }
                    return cached;
                } finally { source.flush(); }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load player statistic icon: " + resource, e);
            }
        }
    }
}
