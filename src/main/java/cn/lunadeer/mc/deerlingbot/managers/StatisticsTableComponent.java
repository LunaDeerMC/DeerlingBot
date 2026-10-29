package cn.lunadeer.mc.deerlingbot.managers;

import java.awt.*;
import java.awt.geom.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import static cn.lunadeer.mc.deerlingbot.managers.InfoCardRenderer.*;

/** Seven rows sharing the existing icon, label, right-aligned value and separator layout. */
final class StatisticsTableComponent {
    private record Row(String label,String value,int icon) { }
    private StatisticsTableComponent() { }

    static void draw(Graphics2D g,PlayerCardProfile p,int x,int y,int width) {
        card(g,x,y,width,568,24);
        g.setColor(INK);g.setFont(font(CJK,Font.BOLD,25));g.drawString("游玩统计",x+28,y+45);
        g.setColor(LINE);g.setStroke(new BasicStroke(1));g.drawLine(x+28,y+68,x+width-28,y+68);
        List<Row> rows=List.of(
                new Row("累计在线时间",time(p.playedTicks()),0),
                new Row("走过的总路程",distance(p.walkedCentimeters()),1),
                new Row("累计挖掘",PlayerCardProfile.formatCount(p.minedBlocks())+" 块",2),
                new Row("累计合成",PlayerCardProfile.formatCount(p.craftedItems())+" 件",3),
                new Row("钓鱼次数",count(p.fishCaught())+" 次",4),
                new Row("村民交易次数",count(p.tradedWithVillager())+" 次",5),
                new Row("鞘翅飞行距离",distance(p.aviatedCentimeters()),6));
        for(int i=0;i<rows.size();i++)drawRow(g,rows.get(i),x+28,y+84+i*66,width-56,i+1<rows.size());
    }

    private static void drawRow(Graphics2D g,Row row,int x,int y,int width,boolean separator) {
        g.setColor(SOFT_PINK);g.fillRoundRect(x,y+10,45,45,14,14);
        Graphics2D icon=(Graphics2D)g.create();
        try{icon.translate(x+9,y+19);icon.scale(27d/24,27d/24);
            if(row.icon()<4)drawIcon(icon,row.icon(),0,0);else extraIcon(icon,row.icon());
        }finally{icon.dispose();}
        g.setColor(c("#5E6674"));g.setFont(font(CJK,Font.PLAIN,20));g.drawString(row.label(),x+68,y+38);
        Font value=font(CJK,Font.BOLD,23);
        while(value.getSize2D()>16&&g.getFontMetrics(value).stringWidth(row.value())>285)value=value.deriveFont(value.getSize2D()-1);
        g.setFont(value);g.setColor(INK);right(g,row.value(),x+width,y+38);
        if(separator){g.setColor(LINE);g.setStroke(new BasicStroke(1));g.drawLine(x+68,y+65,x+width,y+65);}
    }

    private static void extraIcon(Graphics2D g,int type) {
        g.setColor(PINK);g.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        switch(type) {
            case 4->{g.draw(new Ellipse2D.Float(2,6,15,12));g.draw(new Polygon(new int[]{16,22,22},new int[]{12,5,19},3));g.fillOval(6,10,2,2);}
            case 5->{g.drawLine(3,7,21,7);g.drawLine(17,3,21,7);g.drawLine(17,11,21,7);
                g.drawLine(3,17,21,17);g.drawLine(3,17,7,13);g.drawLine(3,17,7,21);}
            case 6->{Path2D path=new Path2D.Double();path.moveTo(11,5);path.lineTo(3,2);path.lineTo(2,13);path.lineTo(9,21);path.closePath();
                path.moveTo(13,5);path.lineTo(21,2);path.lineTo(22,13);path.lineTo(15,21);path.closePath();g.draw(path);g.drawLine(5,7,8,16);g.drawLine(19,7,16,16);}
            default->throw new IllegalArgumentException("Unknown statistic icon");
        }
    }

    private static String time(long ticks) {
        long minutes=ticks/1200,days=minutes/1440,hours=minutes/60%24;
        if(days>0)return days+" 天 "+hours+" 小时";
        if(hours>0)return hours+" 小时 "+minutes%60+" 分钟";
        return minutes+" 分钟";
    }
    private static String count(long n){return new DecimalFormat("#,##0",DecimalFormatSymbols.getInstance(Locale.ROOT)).format(n);}
    private static String distance(long cm) {
        if(cm<100)return cm+" cm";
        double value=cm<100_000?cm/100d:cm/100_000d;
        return new DecimalFormat("#,##0.##",DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value)+(cm<100_000?" m":" km");
    }
}
