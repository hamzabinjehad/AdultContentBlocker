package app.hisn.core;

/** When selected-window image sampling may run, and when to free the model.
 *
 * The model is preloaded while the screen is on and the image layer is enabled, even before a
 * selected app comes forward, so the first frame after switching to that app can be classified
 * instead of waiting out a three-second cold start. It stays loaded while the screen is on: a
 * reload would blind the layer exactly when the person opens the next app. Sampling never runs
 * while the screen is off; after {@link #SCREEN_OFF_GRACE_MILLIS} the WebView and model are freed,
 * and a short screen-off keeps them warm for an immediate screen-on. */
public final class VisualDuty {
    public static final long SCREEN_OFF_GRACE_MILLIS = 2 * 60_000;
    public enum Action { SAMPLE, PRELOAD, PAUSE, RELEASE }

    private long lastInteractive;

    /** @param now a monotonic clock that keeps counting while the device sleeps (elapsedRealtime). */
    public synchronized Action next(boolean interactive, boolean selectedWindow, boolean modelLoaded, long now) {
        // Nothing is sampled with the screen off. Keep the model through a short screen-off so
        // turning the screen back on does not pay the model load again; free it after the grace.
        if (!interactive) {
            if (modelLoaded && now >= lastInteractive && now - lastInteractive >= SCREEN_OFF_GRACE_MILLIS)
                return Action.RELEASE;
            return Action.PAUSE;
        }
        lastInteractive = now;
        if (!modelLoaded) {
            // The guard is on and the screen is on: warm the model before the next frame.
            return Action.PRELOAD;
        }
        return selectedWindow ? Action.SAMPLE : Action.PAUSE;
    }
}
