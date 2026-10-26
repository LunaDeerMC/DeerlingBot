package cn.lunadeer.mc.deerlingbot.managers;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InfoCardRendererTest {
    private static final PlayerSnapshotService.InfoSnapshot ONLINE =
            new PlayerSnapshotService.InfoSnapshot(true, "Alex", "昨天", "12天 6小时",
                    "1,208 km", "386,472", "24,109");

    @Test
    void java2dCardIsOpaqueAtInteriorAndHasExpectedSize() {
        BufferedImage image = new InfoCardRenderer().render(ONLINE, null);
        assertEquals(1440, image.getWidth());
        assertEquals(1656, image.getHeight());
        assertEquals(Color.WHITE.getRGB(), image.getRGB(200 * 3, 100 * 3));
        boolean logoVisible = false;
        for (int y = 58; y < 81; y++) {
            for (int x = 61; x < 84; x++) {
                if (image.getRGB(x * 3, y * 3) != Color.WHITE.getRGB()) logoVisible = true;
            }
        }
        assertTrue(logoVisible, "Bundled logo must be visibly drawn in the header");
    }

    @Test
    void playerAvatarIsUsedWhenAvailableAndFallbackWorks() {
        BufferedImage avatar = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++)
            avatar.setRGB(x, y, Color.RED.getRGB());
        InfoCardRenderer renderer = new InfoCardRenderer();
        BufferedImage withAvatar = renderer.render(ONLINE, avatar);
        BufferedImage fallback = renderer.render(ONLINE, null);
        // Avatar spans logical x=289..412, y=62..185.
        assertEquals(Color.RED.getRGB(), withAvatar.getRGB(350 * 3, 122 * 3));
        assertNotEquals(Color.RED.getRGB(), fallback.getRGB(350 * 3, 122 * 3));
    }

    @Test
    void boundedRenderingServiceCanBeClosedAndRejectsLaterWork() throws Exception {
        InfoImageService service = new InfoImageService();
        try {
            BufferedImage result = service.render(ONLINE, null).get(10, TimeUnit.SECONDS);
            assertEquals(1440, result.getWidth());
            assertFalse(service.isClosed());
        } finally {
            service.close();
        }
        assertTrue(service.isClosed());
        assertTrue(service.render(ONLINE, null).isCompletedExceptionally());
    }

    @Test
    void bundledFontsCoverAllDynamicStatisticsUnitsAndAsciiPlayerNames() {
        java.awt.Font cjk = InfoCardRenderer.font(InfoCardRenderer.CJK, java.awt.Font.BOLD, 14);
        java.awt.Font latin = InfoCardRenderer.font(InfoCardRenderer.LATIN, java.awt.Font.BOLD, 14);
        for (char ch : "分钟小时天千百万亿暂不可用".toCharArray()) {
            assertTrue(cjk.canDisplay(ch), "Missing embedded unit glyph: " + ch);
        }
        for (char ch : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-".toCharArray()) {
            assertTrue(latin.canDisplay(ch), "Missing Latin player-name glyph: " + ch);
        }
    }

    @Test
    void offlineStatusCanBeRenderedWithoutBukkit() {
        PlayerSnapshotService.InfoSnapshot offline =
                new PlayerSnapshotService.InfoSnapshot(false, "Alex", "昨天",
                        "暂不可用", "暂不可用", "暂不可用", "暂不可用");
        assertDoesNotThrow(() -> new InfoCardRenderer().render(offline, null));
    }
}
