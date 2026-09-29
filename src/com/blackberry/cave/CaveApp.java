package com.blackberry.cave;

import net.rim.device.api.ui.UiApplication;

public class CaveApp extends UiApplication {
    public static void main(String[] args) {
        CaveApp app = new CaveApp();
        app.pushScreen(new HexGameScreen());
        app.enterEventDispatcher();
    }

    public void activate() {
        super.activate();
        net.rim.device.api.ui.Screen screen = getActiveScreen();
        if (screen instanceof HexGameScreen)
            ((HexGameScreen) screen).resumeUpdates();
        else if (screen instanceof CombatScreen)
            ((CombatScreen) screen).resumeUpdates();
        else if (screen instanceof MapEncounterScreen)
            ((MapEncounterScreen) screen).resumeUpdates();
    }

    public void deactivate() {
        net.rim.device.api.ui.Screen screen = getActiveScreen();
        if (screen instanceof HexGameScreen)
            ((HexGameScreen) screen).pauseUpdates();
        else if (screen instanceof CombatScreen)
            ((CombatScreen) screen).pauseUpdates();
        else if (screen instanceof MapEncounterScreen)
            ((MapEncounterScreen) screen).pauseUpdates();
        super.deactivate();
    }

    public CaveApp() {
        // Constructor needed
    }
}
