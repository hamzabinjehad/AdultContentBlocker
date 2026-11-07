package app.hisn.core;

/**
 * How long the image layer waits after a classifier failure.
 *
 * <p>A transient failure (a killed WebView renderer, a frame that timed out, memory pressure)
 * used to blind the layer for a flat 30 s and throw away the loaded model. The first retry now
 * comes after {@link #FIRST_MILLIS}, each further failure doubles the wait, and a finished sample
 * clears the escalation. The cap stays the old 30 s so a genuinely broken engine cannot loop.
 */
public final class VisualRetry {
    public static final long FIRST_MILLIS = 5_000, MAX_MILLIS = 30_000;

    private long delay = FIRST_MILLIS;

    /** Records a failure and returns the wait before the next attempt. */
    public synchronized long failed() {
        long wait = delay;
        delay = Math.min(delay * 2, MAX_MILLIS);
        return wait;
    }

    /** A sample finished with a verdict: the next failure starts from the short wait again. */
    public synchronized void recovered() {
        delay = FIRST_MILLIS;
    }

    /** The wait the next failure would return; for diagnostics and tests. */
    public synchronized long pending() {
        return delay;
    }
}
