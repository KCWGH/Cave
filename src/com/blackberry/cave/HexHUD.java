package com.blackberry.cave;

import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Color;
import net.rim.device.api.system.Bitmap;
import java.util.Vector;

public class HexHUD {
    private int height = 80;
    private Bitmap imgFocus;

    public HexHUD() {
        imgFocus = Bitmap.getBitmapResource("icon_focus.png");
    }

    public int getHeight() {
        return height;
    }

    public void draw(Graphics g, Party party, int screenW, int screenH) {
        int y = screenH - height;

        g.setGlobalAlpha(200);
        g.setColor(0x000000);
        g.fillRect(0, y, screenW, height);
        g.setGlobalAlpha(255);

        g.setColor(0x444444);
        g.drawLine(0, y, screenW, y);
        g.setColor(0x222222);
        g.drawLine(0, y + 1, screenW, y + 1);

        int slotW = screenW / 4;
        Unit active = party.getActiveMember();

        for (int i = 0; i < party.members.size(); i++) {
            Unit u = (Unit) party.members.elementAt(i);
            int x = i * slotW;

            if (u == active) {
                g.setColor(0x443300);
                g.fillRect(x, y + 2, slotW, height - 2);
                g.setColor(Color.GOLDENROD);
                g.drawRect(x + 2, y + 2, slotW - 4, height - 4);
            }

            if (i > 0) {
                g.setColor(0x333333);
                g.drawLine(x, y + 10, x, y + height - 10);
            }

            if (u.hudSprite != null) {
                g.drawBitmap(x + 5, y + 15, 50, 50, u.hudSprite, 0, 0);
            } else if (u.sprite != null) {
                g.drawBitmap(x + 5, y + 15, 50, 50, u.sprite, 0, 0);
            }

            g.setColor(Color.WHITE);
            g.setFont(g.getFont().derive(net.rim.device.api.ui.Font.BOLD, 14));
            g.drawText(u.name, x + 55, y + 8);

            int barW = slotW - 70;
            g.setColor(0x330000);
            g.fillRect(x + 55, y + 28, barW, 8);
            g.setColor(0x00CC00);
            int hpW = (int) ((long) u.curHp * barW / u.getMaxHp());
            if (hpW > 0)
                g.fillRect(x + 55, y + 28, hpW, 8);

            int focusSpacing = u.getMaxFocus() <= 1 ? 18
                    : Math.min(18, Math.max(1, (slotW - 73) / (u.getMaxFocus() - 1)));
            for (int f = 0; f < u.getMaxFocus(); f++) {
                int fx = x + 55 + f * focusSpacing;
                int fy = y + 42;
                if (f < u.curFocus) {
                    if (imgFocus != null) {
                        g.drawBitmap(fx, fy, 16, 16, imgFocus, 0, 0);
                    } else {
                        g.setColor(Color.GOLDENROD);
                        g.fillArc(fx, fy, 12, 12, 0, 360);
                        g.setColor(Color.WHITE);
                        g.drawArc(fx, fy, 12, 12, 0, 360);
                    }
                } else {
                    g.setColor(0x333333);
                    g.drawArc(fx, fy, 12, 12, 0, 360);
                }
            }

            g.setColor(0xAAAAFF);
            g.setFont(g.getFont().derive(net.rim.device.api.ui.Font.PLAIN, 12));
            g.drawText("M:" + u.curMove + " F:" + u.curFocus + "/" + u.getMaxFocus(), x + 55, y + 60);
        }
    }
}
