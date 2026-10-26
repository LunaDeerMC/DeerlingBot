package cn.lunadeer.mc.deerlingbot.managers;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Pure-Java2D rendering of the approved BaiLuYuan player card (V5). No browser, HTML or network I/O. */
public final class InfoCardRenderer {
    static final int W = 480, H = 552, SCALE = 3;
    static final Color BG_TOP = c("#F7FAFF");
    static final Color BG_BOTTOM = c("#FAF7FB");
    static final Color INK = c("#262A38");
    static final Color MUTED = c("#8890A0");
    static final Color LINE = c("#F0EFF4");
    static final Color PINK = c("#D86195");
    static final Color SOFT_PINK = c("#FEEDF4");
    static final Color GREEN = c("#209A78");
    static final Color SOFT_GREEN = c("#E8F8F1");
    static final String CJK = "Noto Sans CJK SC";
    static final String LATIN = "Inter";

    // Bundled OFL font subsets make the Java2D output independent of the host OS.
    private static final Font CJK_REGULAR = loadFont("/assets/fonts/NotoSansCJKsc-Regular-subset.otf");
    private static final Font CJK_BOLD = loadFont("/assets/fonts/NotoSansCJKsc-Bold-subset.otf");
    private static final Font LATIN_REGULAR = loadFont("/assets/fonts/Inter-subset.otf");
    private static final Font LATIN_BOLD = loadFont("/assets/fonts/Inter-Bold-subset.otf");

    private final BufferedImage officialLogo;

    public InfoCardRenderer() {
        try (InputStream stream = InfoCardRenderer.class.getResourceAsStream("/assets/bailuyuan-logo.png")) {
            if (stream == null) throw new IOException("logo asset missing in plugin JAR");
            officialLogo = ImageIO.read(stream);
            if (officialLogo == null) throw new IOException("invalid logo image");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load bundled BaiLuYuan logo", e);
        }
    }

    public BufferedImage render(PlayerSnapshotService.InfoSnapshot snapshot, BufferedImage avatar) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        BufferedImage out = new BufferedImage(W * SCALE, H * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(SCALE, SCALE);
            g.setPaint(new GradientPaint(0, 0, BG_TOP, W, H, BG_BOTTOM));
            g.fillRect(0, 0, W, H);
            drawHeader(g, officialLogo, avatar, snapshot);
            drawStats(g, snapshot);
            g.setColor(c("#A2A8B5"));
            g.setFont(font(CJK, Font.PLAIN, 10));
            center(g, "白鹿原 · 玩家数据", W / 2f, 536);
        } finally {
            g.dispose();
        }
        return out;
    }

    static void drawHeader(Graphics2D g, BufferedImage officialLogo, BufferedImage avatar, PlayerSnapshotService.InfoSnapshot snapshot) {
        card(g, 36, 30, 408, 187, 24);

        // Official logo: 23 logical pixels (was 18). Maintain its vertical center,
        // and shift the accent bar and caption right to keep the original gaps.
        final int logoSize = 23;
        g.drawImage(officialLogo, 61, 58, logoSize, logoSize, null);

        g.setColor(PINK);
        g.fillRoundRect(93, 61, 4, 17, 4, 4);
        g.setFont(font(CJK, Font.BOLD, 13));
        g.setColor(c("#616778"));
        g.drawString("白鹿原玩家档案", 108, 75);

        g.setColor(INK);
        Font nameFont = font(LATIN, Font.BOLD, 23);
        // Keep player names clear of the enlarged avatar.
        while (nameFont.getSize2D() > 16 && g.getFontMetrics(nameFont).stringWidth(snapshot.name()) > 213) {
            nameFont = nameFont.deriveFont(nameFont.getSize2D() - 1);
        }
        g.setFont(nameFont);
        g.drawString(snapshot.name(), 61, 121);

        g.setColor(snapshot.online() ? SOFT_GREEN : c("#F1F2F5"));
        g.fillRoundRect(61, 137, 84, 27, 27, 27);
        g.setColor(snapshot.online() ? GREEN : MUTED);
        g.fillOval(73, 148, 7, 7);
        g.setFont(font(CJK, Font.BOLD, 12));
        g.drawString(snapshot.online() ? "当前在线" : "上次在线", 85, 156);

        g.setFont(font(CJK, Font.PLAIN, 11));
        g.setColor(MUTED);
        String subtitle = snapshot.online() ? "白鹿原 · 生存世界" : "最近上线：" + snapshot.lastJoin();
        while (g.getFontMetrics().stringWidth(subtitle) > 209 && subtitle.length() > 2) subtitle = subtitle.substring(0, subtitle.length() - 2) + "…";
        g.drawString(subtitle, 62, 191);
        // Avatar has exactly the same inset from the card's top, right and bottom.
        // Its old outside caption is omitted to preserve the equal margins.
        final int cardTop = 30, cardRight = 444, cardBottom = 217;
        final int avatarInset = 32;
        final int avatarSize = cardBottom - cardTop - avatarInset * 2;
        final int avatarX = cardRight - avatarInset - avatarSize;
        final int avatarY = cardTop + avatarInset;
        drawAvatar(g, avatar, avatarX, avatarY, avatarSize);
    }

    static void drawStats(Graphics2D g, PlayerSnapshotService.InfoSnapshot snapshot) {
        card(g, 36, 235, 408, 277, 24);
        g.setColor(INK);
        g.setFont(font(CJK, Font.BOLD, 17));
        g.drawString("游玩统计", 61, 273);
        g.setColor(MUTED);
        g.setFont(font(CJK, Font.PLAIN, 10));
        right(g, "数据概览", 420, 271);
        g.setColor(LINE);
        g.setStroke(new BasicStroke(1));
        g.draw(new Line2D.Float(61, 288, 419, 288));

        stat(g, 0, 296, "累计在线时间", snapshot.time(), true);
        stat(g, 1, 348, "走过的总路程", snapshot.walk(), false);
        stat(g, 2, 400, "累计挖掘", snapshot.mined(), false);
        stat(g, 3, 452, "累计合成", snapshot.crafted(), false);
    }

    static void stat(Graphics2D g, int type, int top, String label, String value, boolean chineseValue) {
        int iconX = 62, iconY = top + 5;
        g.setColor(SOFT_PINK);
        g.fillRoundRect(iconX, iconY, 36, 36, 12, 12);
        drawIcon(g, type, iconX + 7, iconY + 7);
        g.setColor(c("#5E6674"));
        g.setFont(font(CJK, Font.PLAIN, 13));
        g.drawString(label, 112, top + 29);
        g.setColor(INK);
        boolean hasChinese = value.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
        Font valueFont = font(chineseValue || hasChinese ? CJK : LATIN, Font.BOLD, 14);
        // Unavailable/long values must remain inside the card, not overlap the labels.
        while (valueFont.getSize2D() > 10 && g.getFontMetrics(valueFont).stringWidth(value) > 152) {
            valueFont = valueFont.deriveFont(valueFont.getSize2D() - 1);
        }
        g.setFont(valueFont);
        right(g, value, 420, top + 29);
        if (type < 3) {
            g.setColor(LINE);
            g.setStroke(new BasicStroke(0.9f));
            g.draw(new Line2D.Float(112, top + 51, 420, top + 51));
        }
    }

    // All four icons use the same 24x24 viewbox, line thickness, and cap geometry.
    static void drawIcon(Graphics2D g, int type, int ox, int oy) {
        Graphics2D i = (Graphics2D) g.create();
        i.translate(ox, oy);
        i.setColor(PINK);
        i.setStroke(new BasicStroke(1.85f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (type) {
            case 0 -> { // clock
                i.draw(new Ellipse2D.Float(3.0f, 3.0f, 18, 18));
                i.draw(new Line2D.Float(12, 7, 12, 12));
                i.draw(new Line2D.Float(12, 12, 15.5f, 14.1f));
            }
            case 1 -> { // map route, more legible than a stick-figure runner
                i.draw(new Ellipse2D.Float(3.0f, 15.8f, 4.3f, 4.3f));
                i.draw(new Ellipse2D.Float(16.7f, 3.6f, 4.3f, 4.3f));
                Path2D p = new Path2D.Float();
                p.moveTo(6, 15.7);
                p.curveTo(8, 9.7, 17, 17, 18.6, 8.0);
                i.draw(p);
            }
            case 2 -> { // ordinary, clean vector pickaxe (not pixel art)
                // Angle the crosshead so it reads as a real pickaxe rather than a parasol.
                i.rotate(Math.toRadians(-32), 12, 12);
                // A long diagonal handle attached to an asymmetric metal head.
                // The slanted crosspiece (rather than a symmetric arch) avoids
                // making the tool look like an umbrella.
                Path2D.Float handle = new Path2D.Float();
                handle.moveTo(11.2f, 4.9f);
                handle.lineTo(14.0f, 6.6f);
                handle.lineTo(8.5f, 21.2f);
                handle.quadTo(8.0f, 21.9f, 7.3f, 21.5f);
                handle.lineTo(5.3f, 20.4f);
                handle.quadTo(4.9f, 20.1f, 5.3f, 19.4f);
                handle.closePath();
                i.fill(handle);

                // Long pointed left pick, reinforced right heel and visible
                // broad socket around where the wooden grip joins the head.
                Path2D.Float head = new Path2D.Float();
                head.moveTo(1.9f, 6.0f);
                head.curveTo(8.4f, 3.2f, 13.4f, 3.3f, 17.0f, 5.0f);
                head.curveTo(19.5f, 6.2f, 20.8f, 8.1f, 22.1f, 11.0f);
                head.lineTo(18.7f, 9.4f);
                head.curveTo(16.6f, 7.6f, 12.8f, 6.9f, 9.7f, 7.1f);
                head.curveTo(7.0f, 7.2f, 4.6f, 8.0f, 2.1f, 9.4f);
                head.lineTo(1.9f, 6.0f);
                head.closePath();
                i.fill(head);
                // Slim highlight along the metal edge: clearly a tool head.
                i.setColor(c("#EFA1C1"));
                i.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                i.draw(new Line2D.Float(7.8f, 5.1f, 15.5f, 5.2f));
            }
            case 3 -> { // isometric crafting cube
                Path2D p = new Path2D.Float();
                p.moveTo(12, 2.8); p.lineTo(20, 7.2); p.lineTo(20, 16.7);
                p.lineTo(12, 21); p.lineTo(4, 16.7); p.lineTo(4, 7.2);
                p.closePath();
                p.moveTo(4.5, 7.3); p.lineTo(12, 11.7); p.lineTo(19.5, 7.3);
                p.moveTo(12, 11.7); p.lineTo(12, 20.5);
                i.draw(p);
            }
        }
        i.dispose();
    }

    static void drawAvatar(Graphics2D g, BufferedImage actual, int x, int y, int size) {
        Graphics2D a = (Graphics2D) g.create();
        for (int k = 5; k >= 1; k--) {
            a.setColor(new Color(54, 50, 80, 5));
            a.fillRoundRect(x - k / 2, y + 2, size + k, size + k, 17, 17);
        }
        a.setColor(Color.WHITE);
        a.fillRoundRect(x - 3, y - 3, size + 6, size + 6, 17, 17);
        a.setClip(new RoundRectangle2D.Float(x, y, size, size, 12, 12));
        if (actual != null) {
            a.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            a.drawImage(actual, x, y, size, size, null);
        } else {
            // Placeholder made from an explicit pixel grid; no network or real player skin.
            String[] sprite = {
                "HHHHHHHHHHHH",
                "HHHHHHHHHHHH",
                "HHHHHHHHHHHH",
                "HHSSSSSSSSHH",
                "HSSSSSSSSSSH",
                "SSSSSSSSSSSS",
                "SSWWSSSSWWSS",
                "SSEESSSSEESS",
                "SSSSSRRSSSSS",
                "SSSSSRRSSSSS",
                "SSSSMMMMSSSS",
                "HSSSSSSSSSSH"
            };
            java.util.Map<Character, Color> palette = java.util.Map.of(
                'H', c("#533A32"), 'S', c("#DFA780"),
                'W', c("#FFF8EA"), 'E', c("#426B8C"),
                'R', c("#BF8669"), 'M', c("#875747"));
            a.setColor(c("#E7BCA8"));
            a.fillRect(x, y, size, size);
            for (int ry = 0; ry < sprite.length; ry++) {
                for (int rx = 0; rx < sprite[ry].length(); rx++) {
                    a.setColor(palette.get(sprite[ry].charAt(rx)));
                    int px1 = x + rx * size / 12;
                    int px2 = x + (rx + 1) * size / 12;
                    int py1 = y + ry * size / 12;
                    int py2 = y + (ry + 1) * size / 12;
                    a.fillRect(px1, py1, px2 - px1, py2 - py1);
                }
            }
        }
        a.dispose();
    }

    static void card(Graphics2D g, int x, int y, int w, int h, int arc) {
        for (int k = 9; k >= 1; k--) {
            float alpha = 0.005f + 0.0025f * (10 - k);
            g.setColor(new Color(50 / 255f, 40 / 255f, 70 / 255f, alpha));
            g.fill(new RoundRectangle2D.Float(x - k / 3f, y + 3 + k / 4f, w + k * 2 / 3f, h + k / 3f, arc, arc));
        }
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
        g.setColor(c("#F3F0F5"));
        g.setStroke(new BasicStroke(0.7f));
        g.draw(new RoundRectangle2D.Float(x + 0.4f, y + 0.4f, w - 0.8f, h - 0.8f, arc, arc));
    }

    private static Font loadFont(String path) {
        try (InputStream stream = InfoCardRenderer.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing embedded font: " + path);
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Cannot load embedded font " + path, e);
        }
    }

    static Font font(String family, int weight, float size) {
        Font face = CJK.equals(family)
                ? (weight == Font.BOLD ? CJK_BOLD : CJK_REGULAR)
                : (weight == Font.BOLD ? LATIN_BOLD : LATIN_REGULAR);
        return face.deriveFont(size);
    }

    static void right(Graphics2D g, String s, float rightX, float baseline) {
        g.drawString(s, rightX - g.getFontMetrics().stringWidth(s), baseline);
    }

    static void center(Graphics2D g, String s, float centerX, float baseline) {
        g.drawString(s, centerX - g.getFontMetrics().stringWidth(s) / 2f, baseline);
    }

    static Color c(String s) { return Color.decode(s); }
}