package app.hisn.core;

/** When selected-window image sampling may run, and when to free the model.
 *
 * The model is preloaded while the screen is on and the image layer is enabled, even before a
 * selected app comes forward, so the first frame after switching to that app can be classified
 * instead of waiting out a cold start. Sampling never runs while the screen is off, and the model
 * is freed when the screen turns off and after {@link #IDLE_RELEASE_MILLIS} without a sample. */
public final class VisualDuty {
    public static final long IDLE_RELEASE_MILLIS = 3 * 60_000;
    public enum Action { SAMPLE, PRELOAD, PAUSE, RELEASE }

    private boolean used;
    private long lastUse;

    /** @param now a monotonic clock that keeps counting while the device sleeps (elapsedRealtime). */
    public synchronized Action next(boolean interactive, boolean selectedWindow, boolean modelLoaded, long now) {
        // Nothing is sampled with the screen off; free the WebView and its model too.
        if (!interactive) return modelLoaded ? Action.RELEASE : Action.PAUSE;
        if (!modelLoaded) {
            // The guard is on and the screen is on: warm the model before the next frame.
            used = true;
            lastUse = now;
            return Action.PRELOAD;
        }
        if (selectedWindow) {
            used = true;
            lastUse = now;
            return Action.SAMPLE;
        }
        if (!used) { used = true; lastUse = now; return Action.PAUSE; }
        return now >= lastUse && now - lastUse >= IDLE_RELEASE_MILLIS ? Action.RELEASE : Action.PAUSE;
    }
}
