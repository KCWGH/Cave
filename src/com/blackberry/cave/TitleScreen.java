package com.blackberry.cave;

import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.ui.*;

final class TitleScreen extends MainScreen {
    TitleScreen() {
        setTitle("Cave");
        add(new LabelField("New game or continue"));
        ButtonField start = new ButtonField("New Game", ButtonField.CONSUME_CLICK);
        start.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { openGame(false); }
        });
        add(start);
        ButtonField resume = new ButtonField("Continue", ButtonField.CONSUME_CLICK);
        resume.setChangeListener(new FieldChangeListener() {
            public void fieldChanged(Field field, int context) { openGame(true); }
        });
        add(resume);
    }

    private void openGame(boolean resume) {
        try {
            int slot = Dialog.ask(resume ? "Continue from which slot?" : "New game save slot?",
                    new String[] { SaveStore.label(0), SaveStore.label(1), SaveStore.label(2), "Cancel" }, 3);
            if (slot < 0 || slot >= 3) return;
            if (!resume && SaveStore.occupied(slot)
                    && Dialog.ask("This slot will be replaced when the new game is saved.",
                            new String[] { "Cancel", "New Game" }, 0) != 1) return;
            HexGameScreen game = resume ? SaveStore.load(slot) : new HexGameScreen(slot);
            UiApplication.getUiApplication().pushScreen(game);
        } catch (Exception failure) {
            Dialog.alert("Could not open game: " + failure.toString());
        }
    }
}
