package app.hisn.core;

/** When selected-window image sampling may run, and when to free the model. Sampling never runs
 * while the screen is off, and a loaded model is released after a few minutes without a sample. */
public final class VisualDuty {
    public static final long IDLE_RELEASE_MILLIS = 3 * 60_000;
    public enum Action { SAMPLE, PAUSE, RELEASE }

    private boolean sampled;
    private long lastSample;

    /** @param now a monotonic clock that keeps counting while the device sleeps (elapsedRealtime). */
    public synchronized Action next(boolean interactive, boolean selectedWindow, boolean modelLoaded, long now) {
        if (interactive && selectedWindow) {
            sampled = true;
            lastSample = now;
            return Action.SAMPLE;
        }
        if (modelLoaded && (!sampled || now - lastSample >= IDLE_RELEASE_MILLIS)) return Action.RELEASE;
        return Action.PAUSE;
    }
}
