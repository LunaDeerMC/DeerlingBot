package cn.lunadeer.mc.deerlingbot.managers;

import java.awt.*;
import java.awt.image.BufferedImage;

import static cn.lunadeer.mc.deerlingbot.managers.InfoCardRenderer.*;

/** Composes the familiar profile header, seven-row statistics and six reusable achievement cells. */
final class PlayerProfileCardRenderer {
    static final int WIDTH = 760, HEIGHT = 1400, OUTPUT_SCALE = 2;

    BufferedImage render(PlayerSnapshotService.InfoSnapshot snapshot, BufferedImage avatar, BufferedImage logo) {
        if(snapshot.profile()==null)throw new IllegalArgumentException("profile statistics required");
        BufferedImage out=new BufferedImage(WIDTH*OUTPUT_SCALE,HEIGHT*OUTPUT_SCALE,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=out.createGraphics();
        try {
            g.scale(OUTPUT_SCALE,OUTPUT_SCALE);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
            g.setPaint(new GradientPaint(0,0,BG_TOP,WIDTH,HEIGHT,BG_BOTTOM));g.fillRect(0,0,WIDTH,HEIGHT);
            header(g,snapshot,avatar,logo);
            StatisticsTableComponent.draw(g,snapshot.profile(),32,282,696);
            AchievementGridComponent.draw(g,snapshot.profile().achievements(),32,868,696);
            g.setColor(c("#A2A8B5"));g.setFont(font(CJK,Font.PLAIN,13));center(g,"白鹿原 · 玩家数据",WIDTH/2f,1373);
        }finally{g.dispose();}
        return out;
    }

    private static void header(Graphics2D g,PlayerSnapshotService.InfoSnapshot s,BufferedImage avatar,BufferedImage logo) {
        card(g,32,28,696,236,24);
        g.drawImage(logo,60,59,31,31,null);
        g.setColor(PINK);g.fillRoundRect(108,62,5,25,5,5);
        g.setColor(c("#616778"));g.setFont(font(CJK,Font.BOLD,20));g.drawString("白鹿原玩家档案",132,83);
        Font name=font(LATIN,Font.BOLD,42);
        while(name.getSize2D()>23&&g.getFontMetrics(name).stringWidth(s.name())>439)name=name.deriveFont(name.getSize2D()-1);
        g.setFont(name);g.setColor(INK);g.drawString(s.name(),60,148);
        g.setColor(s.online()?SOFT_GREEN:c("#F1F2F5"));g.fillRoundRect(60,165,115,34,34,34);
        g.setColor(s.online()?GREEN:MUTED);g.fillOval(75,178,9,9);
        g.setFont(font(CJK,Font.BOLD,15));g.drawString(s.online()?"当前在线":"上次在线",94,188);
        g.setColor(MUTED);g.setFont(font(CJK,Font.PLAIN,15));
        String subtitle=s.online()?"白鹿原 · 生存世界":"最近上线："+readableDate(s.lastJoin());
        while(g.getFontMetrics().stringWidth(subtitle)>436&&subtitle.length()>2)subtitle=subtitle.substring(0,subtitle.length()-2)+"…";
        g.drawString(subtitle,60,231);
        drawAvatar(g,avatar,542,61,158);
    }

    private static String readableDate(String text) {
        if(text==null||text.isBlank())return "暂不可用";
        String value=text.replace('T',' ');
        return value.length()>=16&&value.charAt(4)=='-'&&value.charAt(7)=='-'?value.substring(0,16):value;
    }
}
