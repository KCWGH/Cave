package com.blackberry.cave;

import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Color;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.DrawStyle;
import net.rim.device.api.system.Bitmap;

public class HexHUD {
    private int height = 80;
    private Bitmap imgFocus;
    private Bitmap compactFocus;
    private Font titleFont = Font.getDefault().derive(Font.BOLD,14);
    private Font infoFont = Font.getDefault().derive(Font.PLAIN,12);

    public HexHUD() {
        imgFocus = Bitmap.getBitmapResource("icon_focus.png");
        if(imgFocus!=null) {
            compactFocus = new Bitmap(8,8);
            imgFocus.scaleInto(compactFocus,Bitmap.FILTER_LANCZOS,Bitmap.SCALE_STRETCH);
        }
    }
    public int getHeight() { return height; }

    public void draw(Graphics g,Party party,int screenW,int screenH) {
        int y=Math.max(0,screenH-height), count=party.members.size();
        if(count==0) return;
        Font previousFont=g.getFont(); int previousAlpha=g.getGlobalAlpha();
        try {
            g.setGlobalAlpha(200); g.setColor(Color.BLACK); g.fillRect(0,y,screenW,height);
            g.setGlobalAlpha(255); g.setColor(0x444444); g.drawLine(0,y,screenW,y);
            Unit active=party.getActiveMember();
            for(int i=0;i<count;i++) {
                Unit u=(Unit)party.members.elementAt(i);
                int x=i*screenW/count, right=(i+1)*screenW/count, slotW=right-x;
                if(u==active) {
                    g.setColor(0x443300); g.fillRect(x,y+2,slotW,height-2);
                    g.setColor(Color.GOLDENROD); g.drawRect(x+2,y+2,Math.max(0,slotW-4),height-4);
                }
                if(i>0) {g.setColor(0x333333);g.drawLine(x,y+10,x,y+height-10);}
                Bitmap portrait=u.hudSprite!=null?u.hudSprite:u.sprite;
                int portraitW=Math.min(50,Math.max(0,slotW/3));
                if(portrait!=null && portraitW>0)
                    g.drawBitmap(x+4,y+17,Math.min(portraitW,portrait.getWidth()),Math.min(50,portrait.getHeight()),portrait,0,0);
                int tx=x+portraitW+8, textW=Math.max(0,right-tx-5);
                g.setFont(titleFont);g.setColor(Color.WHITE);
                g.drawText(u.name+" L"+u.getLevel(),tx,y+7,DrawStyle.ELLIPSIS,textW);
                g.setColor(0x330000);g.fillRect(tx,y+26,textW,6);
                g.setColor(u.curHp>0?0x00CC00:0x777777);
                int hpW=(int)((long)u.curHp*textW/u.getMaxHp());
                if(hpW>0)g.fillRect(tx,y+26,Math.min(hpW,textW),6);
                g.setFont(infoFont);g.setColor(Color.WHITE);
                g.drawText(u.curHp<=0?"KO":"HP "+u.curHp+"/"+u.getMaxHp(),tx,y+34,DrawStyle.ELLIPSIS,textW);
                int maximum=u.getMaxFocus();
                Bitmap focus=imgFocus;
                if(maximum>0 && textW<maximum*16)focus=compactFocus;
                int icon=focus==null?8:focus.getWidth();
                int spacing=maximum<=1?icon:Math.min(icon+2,Math.max(1,(textW-icon)/(maximum-1)));
                for(int f=0;f<maximum;f++) {
                    int fx=tx+f*spacing;
                    if(fx+icon>right-5) break;
                    if(f<u.curFocus && focus!=null)g.drawBitmap(fx,y+49,focus.getWidth(),focus.getHeight(),focus,0,0);
                    else {g.setColor(f<u.curFocus?Color.GOLDENROD:0x777777);g.drawArc(fx,y+49,icon,icon,0,360);}
                }
                g.setColor(0xAAAAFF);
                g.drawText("M:"+u.curMove+" F:"+u.curFocus+"/"+maximum,tx,y+65,DrawStyle.ELLIPSIS,textW);
            }
        }finally{g.setFont(previousFont);g.setGlobalAlpha(previousAlpha);}
    }
}
