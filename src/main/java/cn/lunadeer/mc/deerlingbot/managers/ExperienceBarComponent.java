package cn.lunadeer.mc.deerlingbot.managers;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Ten discrete pixel-art slots; the exact percentage is painted inside the shared bar. */
final class ExperienceBarComponent {
    private ExperienceBarComponent() { }

    static void draw(Graphics2D graphics, PlayerCardProfile.Achievement achievement, int x, int y, int width, int height) {
        draw(graphics, achievement.litSegments(), achievement.progressPercentText(), x, y, width, height);
    }

    static void draw(Graphics2D graphics, int litSlots, String percentage, int x, int y, int width, int height) {
        if (litSlots < 0 || litSlots > 10) throw new IllegalArgumentException("Lit slots must be within 0..10");
        if (width < 10 || height < 1) throw new IllegalArgumentException("Experience bar dimensions are too small");
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.clipRect(x, y, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            for (int slot = 0; slot < 10; slot++) {
                int left = x + slot * width / 10;
                int right = x + (slot + 1) * width / 10;
                g.drawImage(Images.EMPTY, left, y, right - left, height, null);
                if (slot < litSlots) g.drawImage(Images.FILLED, left, y, right - left, height, null);
            }
            // The integer-lit slots do not replace the exact percentage label.
            float fontSize = Math.min(15f, Math.max(8f, height * 0.62f));
            Font font = InfoCardRenderer.font(InfoCardRenderer.LATIN, Font.BOLD, fontSize);
            GlyphVector text = font.createGlyphVector(g.getFontRenderContext(), percentage);
            var bounds = text.getVisualBounds();
            float textX = (float) (x + (width - bounds.getWidth()) / 2 - bounds.getX());
            float textY = (float) (y + (height - bounds.getHeight()) / 2 - bounds.getY());
            Shape outline = text.getOutline(textX, textY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(18, 27, 16));
            g.setStroke(new BasicStroke(Math.max(1.5f, fontSize * 0.15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(outline);
            g.setColor(Color.WHITE);
            g.fill(outline);
        } finally { g.dispose(); }
    }

    private static final class Images {
        static final BufferedImage EMPTY = load("slot-empty");
        static final BufferedImage FILLED = load("slot-filled");

        private static BufferedImage load(String name) {
            String resource = "/assets/experience/" + name + ".png";
            try (InputStream stream = ExperienceBarComponent.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Experience bar asset is missing: " + resource);
                BufferedImage image = ImageIO.read(stream);
                if (image == null || image.getWidth() > 128 || image.getHeight() > 128)
                    throw new IOException("Invalid or oversized experience bar asset: " + resource);
                return image;
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load experience bar component", e);
            }
        }
    }
}
