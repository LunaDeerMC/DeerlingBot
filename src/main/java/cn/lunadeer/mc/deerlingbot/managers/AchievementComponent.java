package cn.lunadeer.mc.deerlingbot.managers;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Shared frame and topic artwork composed at the requested size without per-player image caches. */
final class AchievementComponent {
    private AchievementComponent() { }

    static void draw(Graphics2D graphics, PlayerCardProfile.Achievement achievement, int x, int y, int size) {
        drawArtwork(graphics, achievement.icon(), achievement.level(), achievement.locked(), x, y, size);
    }

    static void drawArtwork(Graphics2D graphics, String icon, String level, boolean locked, int x, int y, int size) {
        if (size <= 0) throw new IllegalArgumentException("Achievement size must be positive");
        BufferedImage frame = AchievementArtwork.frame(level, locked);
        BufferedImage content = AchievementArtwork.content(icon, locked);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if (locked) g.setComposite(AlphaComposite.SrcOver.derive(0.55f));
            g.drawImage(frame, x, y, size, size, null);
            int contentSize = Math.max(1, (int) Math.round(size * 0.61));
            int inset = (size - contentSize) / 2;
            g.drawImage(content, x + inset, y + inset, contentSize, contentSize, null);
        } finally { g.dispose(); }
    }
}
