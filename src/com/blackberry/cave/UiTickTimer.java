package com.blackberry.cave;

import java.util.Timer;
import java.util.TimerTask;
import net.rim.device.api.ui.UiApplication;

/** Repeating UI ticks with at most one queued callback per timer generation. */
final class UiTickTimer {
    private final Runnable callback;
    private Timer timer;
    private Runnable uiTick;
    private int generation = 0;
    private boolean pending = false;

    UiTickTimer(Runnable callback) {
        this.callback = callback;
    }

    synchronized void start(int period) {
        if (timer != null)
            return;
        final int token = ++generation;
        uiTick = new Runnable() {
            public void run() {
                final UiApplication app = UiApplication.getUiApplication();
                synchronized (UiTickTimer.this) {
                    // A stale callback must not clear a newer generation's pending flag.
                    if (timer == null || token != generation)
                        return;
                }
                // start/stop and callback execution are serialized on the UI thread.
                try {
                    if (app.isForeground())
                        callback.run();
                } finally {
                    synchronized (UiTickTimer.this) {
                        if (token == generation)
                            pending = false;
                    }
                }
            }
        };
        timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                queueTick(token);
            }
        }, period, period);
    }

    synchronized void stop() {
        generation++;
        pending = false;
        uiTick = null;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void queueTick(final int token) {
        final UiApplication app = UiApplication.getUiApplication();
        final Runnable task;
        synchronized (this) {
            if (timer == null || token != generation || pending || !app.isForeground())
                return;
            pending = true;
            task = uiTick;
        }
        // Do not hold the timer monitor while entering the UI queue or game code.
        app.invokeLater(task);
    }
}
