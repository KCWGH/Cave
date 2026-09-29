package com.blackberry.cave;

import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Color;
import net.rim.device.api.ui.Font;
import net.rim.device.api.ui.Keypad;
import net.rim.device.api.ui.TouchEvent;

public class GameOverScreen extends MainScreen {

    public GameOverScreen() {
        super(MainScreen.NO_VERTICAL_SCROLL | MainScreen.NO_HORIZONTAL_SCROLL);
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);

        g.setColor(Color.RED);
        g.setFont(g.getFont().derive(Font.BOLD, 40));
        String title = "GAME OVER";
        int tw = g.getFont().getAdvance(title);
        g.drawText(title, (w - tw) / 2, h / 2 - 40);

        g.setColor(0xAAAAAA);
        g.setFont(g.getFont().derive(Font.PLAIN, 16));
        String hint = "The party has fallen.";
        int hw = g.getFont().getAdvance(hint);
        g.drawText(hint, (w - hw) / 2, h / 2 + 10);

        g.setColor(0x888888);
        String hint2 = "Press any key to exit";
        int hw2 = g.getFont().getAdvance(hint2);
        g.drawText(hint2, (w - hw2) / 2, h / 2 + 40);
    }

    protected boolean keyChar(char c, int status, int time) {
        System.exit(0);
        return true;
    }

    protected boolean keyDown(int keycode, int time) {
        System.exit(0);
        return true;
    }

    protected boolean touchEvent(TouchEvent message) {
        if (message.getEvent() == TouchEvent.UP) {
            System.exit(0);
        }
        return true;
    }
}
