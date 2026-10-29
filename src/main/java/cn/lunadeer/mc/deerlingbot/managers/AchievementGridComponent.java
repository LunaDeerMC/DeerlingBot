package cn.lunadeer.mc.deerlingbot.managers;

import java.awt.*;
import java.util.List;

import static cn.lunadeer.mc.deerlingbot.managers.InfoCardRenderer.*;

/** Two rows of three achievement components, with the exact percentage inside each ten-slot bar. */
final class AchievementGridComponent {
    private AchievementGridComponent() { }

    static void draw(Graphics2D g,List<PlayerCardProfile.Achievement> achievements,int x,int y,int width) {
        if(achievements.size()!=6)throw new IllegalArgumentException("The collection must contain all six achievement types");
        card(g,x,y,width,472,24);
        g.setFont(font(CJK,Font.BOLD,25));g.setColor(INK);g.drawString("成就",x+28,y+45);
        int gap=14,cellWidth=(width-56-2*gap)/3;
        for(int i=0;i<achievements.size();i++) {
            PlayerCardProfile.Achievement achievement=achievements.get(i);
            int left=x+28+(i%3)*(cellWidth+gap),top=y+68+(i/3)*202;
            g.setColor(c("#F8F7FA"));g.fillRoundRect(left,top,cellWidth,184,14,14);
            AchievementComponent.draw(g,achievement,left+(cellWidth-112)/2,top+3,112);
            String title=achievement.title()+" · "+achievement.level();
            Font titleFont=font(CJK,Font.BOLD,15);
            while(titleFont.getSize2D()>12&&g.getFontMetrics(titleFont).stringWidth(title)>cellWidth-10)titleFont=titleFont.deriveFont(titleFont.getSize2D()-1);
            g.setFont(titleFont);g.setColor(achievement.locked()?MUTED:INK);center(g,title,left+cellWidth/2f,top+138);
            ExperienceBarComponent.draw(g,achievement,left+10,top+153,cellWidth-20,24);
        }
    }
}
