package cn.lunadeer.mc.deerlingbot.managers;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static cn.lunadeer.mc.deerlingbot.managers.InfoCardRenderer.*;

/** A local contact sheet composed from the exact PNG assets used by player cards. */
public final class AchievementArtworkPreview {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("output-png");
        BufferedImage out=new BufferedImage(1480,1604,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(c("#F1F4EE"));g.fillRect(0,0,1480,1604);
            g.setFont(font(CJK,Font.BOLD,32));g.setColor(c("#253D32"));g.drawString("白鹿原 · 成就",48,70);
            String[] names={"地下常住人口","方块批发商","空中通勤族","资深钓客","贸易伙伴","远行者"};
            g.setFont(font(CJK,Font.BOLD,19));g.setColor(c("#56665C"));
            for(int col=0;col<6;col++)center(g,names[col],220+col*220,128);
            String[] levels={"木","铁","铜","钻","金"};
            Color[] colors={c("#89613D"),c("#687B90"),c("#B76E41"),c("#27A9C7"),c("#A87822")};
            for(int row=0;row<5;row++) {
                int y=151+row*282;
                g.setColor(Color.WHITE);g.fillRoundRect(102,y,1326,254,24,24);
                g.setColor(colors[row]);g.setFont(font(CJK,Font.BOLD,26));center(g,levels[row],58,y+138);
                for(int col=0;col<6;col++)AchievementComponent.drawArtwork(g,AchievementArtwork.ICONS.get(col),levels[row],false,
                        112+col*220,y+16,218);
            }
            g.setFont(font(LATIN,Font.PLAIN,12));g.setColor(c("#899288"));
            g.drawString("DEERLINGBOT / ACHIEVEMENT ARTWORK PREVIEW",108,1577);
        } finally {g.dispose();}
        Path output=Path.of(args[0]).toAbsolutePath();Files.createDirectories(output.getParent());
        ImageIO.write(out,"PNG",output.toFile());System.out.println("Preview: "+output);
    }
}
