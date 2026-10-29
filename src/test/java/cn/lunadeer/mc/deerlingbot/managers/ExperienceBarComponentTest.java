package cn.lunadeer.mc.deerlingbot.managers;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class ExperienceBarComponentTest {
    @Test
    void fillingOneOfTenSlotsDoesNotPartiallyFillAnyOtherSlot() {
        BufferedImage empty = bar(0), one = bar(1), full = bar(10);
        assertTrue(different(empty, one, 0), "First slot must light up");
        for (int slot = 1; slot < 10; slot++) {
            assertFalse(different(empty, one, slot), "Only the first discrete slot changes");
            assertTrue(different(empty, full, slot), "All slots light at 100 percent");
        }
    }

    @Test
    void percentagesAreContainedInTheBarAndInvalidSegmentCountsFail() {
        BufferedImage output = new BufferedImage(240, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        try {
            ExperienceBarComponent.draw(g, 0, "9.99%", 20, 10, 200, 24);
            assertThrows(IllegalArgumentException.class, () -> ExperienceBarComponent.draw(g, 11, "100%", 20, 10, 200, 24));
        } finally { g.dispose(); }
        for (int y = 0; y < 50; y++) {
            for (int x = 0; x < 240; x++) {
                if (x < 20 || x >= 220 || y < 10 || y >= 34) assertEquals(0, output.getRGB(x, y));
            }
        }
    }

    private static BufferedImage bar(int lit) {
        BufferedImage image = new BufferedImage(200, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { ExperienceBarComponent.draw(g, lit, "25.67%", 0, 0, 200, 24); }
        finally { g.dispose(); }
        return image;
    }

    private static boolean different(BufferedImage a, BufferedImage b, int slot) {
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = slot * 20; x < (slot + 1) * 20; x++)
                if (a.getRGB(x, y) != b.getRGB(x, y)) return true;
        return false;
    }
}
