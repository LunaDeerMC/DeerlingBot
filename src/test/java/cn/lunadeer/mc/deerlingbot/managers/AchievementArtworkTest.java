package cn.lunadeer.mc.deerlingbot.managers;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AchievementArtworkTest {
    @Test
    void nineSharedComponentsAreTransparentAndLoadedOnlyOnce() {
        Set<BufferedImage> decoded = new HashSet<>();
        for (String level : AchievementArtwork.LEVELS.keySet()) {
            BufferedImage frame = AchievementArtwork.frame(level);
            assertSame(frame, AchievementArtwork.frame(level));
            assertValid(frame, "frame / " + level);
            decoded.add(frame);
        }
        for (String icon : AchievementArtwork.ICONS) {
            BufferedImage content = AchievementArtwork.content(icon);
            assertSame(content, AchievementArtwork.content(icon));
            assertValid(content, "content / " + icon);
            decoded.add(content);
        }
        assertEquals(9, decoded.size());
    }

    @Test
    void lockedVariantsAreFixedGrayscaleImagesWithOriginalAlpha() {
        assertSame(AchievementArtwork.frame("铜", true), AchievementArtwork.frame("未解锁", true));
        BufferedImage source = AchievementArtwork.content("cube");
        BufferedImage gray = AchievementArtwork.content("cube", true);
        assertSame(gray, AchievementArtwork.content("cube", true));
        assertNotSame(source, gray);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int original = source.getRGB(x, y), muted = gray.getRGB(x, y);
                assertEquals(original >>> 24, muted >>> 24);
                assertEquals((muted >>> 16) & 255, (muted >>> 8) & 255);
                assertEquals((muted >>> 8) & 255, muted & 255);
            }
        }
    }

    @Test
    void unsupportedAchievementOrTierFailsClearly() {
        assertThrows(IllegalArgumentException.class, () -> AchievementArtwork.content("unknown"));
        assertThrows(IllegalArgumentException.class, () -> AchievementArtwork.frame("铂金"));
    }

    private static void assertValid(BufferedImage image, String description) {
        assertTrue(image.getWidth() > 0 && image.getWidth() <= 512);
        assertTrue(image.getHeight() > 0 && image.getHeight() <= 512);
        assertTrue(image.getColorModel().hasAlpha());
        boolean transparent = false, visible = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                transparent |= alpha == 0;
                visible |= alpha > 0;
            }
        }
        assertTrue(transparent, "Component must have transparent background: " + description);
        assertTrue(visible, "Component must contain visible artwork: " + description);
    }
}
