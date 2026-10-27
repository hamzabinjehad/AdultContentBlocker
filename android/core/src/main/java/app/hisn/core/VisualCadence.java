package app.hisn.core;

/** How often selected-window image sampling runs. Android-free so the policy is tested.
 *
 * About every 2 s by default. While a selected window scrolls or its content changes (accessibility
 * events) it samples every {@link #FAST_MILLIS}, never sooner after the previous sample started.
 * A video or animation that sends no events still keeps the default, because each sample's coarse
 * signature differs from the last one of the same window. When consecutive samples of a window
 * are unchanged and no events arrive, the interval grows by 1 s per sample up to
 * {@link #STILL_MAX_MILLIS}; with the screen off, polls (which never sample) slow to
 * {@link #OFF_MILLIS}. One inference at a time and the frame gate are the caller's, unchanged.
 */
public final class VisualCadence {
    public static final long FAST_MILLIS = 1000, BASE_MILLIS = 2000, STILL_STEP_MILLIS = 1000,
            STILL_MAX_MILLIS = 5000, OFF_MILLIS = 10000, ACTIVE_FOR_MILLIS = 2000, MIN_WAIT_MILLIS = 200;

    private boolean active, started, known;
    private long lastActivity, lastStart, lastSignature;
    private int lastWindow, still;

    /** A selected window scrolled, changed content or changed state. */
    public synchronized void activity(long now) {
        active = true;
        lastActivity = now;
        still = 0;
    }

    /** A sample of a window was requested. */
    public synchronized void started(long now) {
        started = true;
        lastStart = now;
    }

    /** A sample finished with a verdict. {@code signature} summarises the downscaled frame. */
    public synchronized void finished(int window, long signature) {
        boolean same = known && window == lastWindow && signature == lastSignature;
        still = same ? Math.min(still + 1, (int) ((STILL_MAX_MILLIS - BASE_MILLIS) / STILL_STEP_MILLIS)) : 0;
        known = true;
        lastWindow = window;
        lastSignature = signature;
    }

    /** Whether events arrived within the last {@link #ACTIVE_FOR_MILLIS}. */
    public synchronized boolean busyScreen(long now) {
        return active && now >= lastActivity && now - lastActivity <= ACTIVE_FOR_MILLIS;
    }

    /** Time from one poll to the next. */
    public synchronized long interval(boolean interactive, long now) {
        if (!interactive) return OFF_MILLIS;
        if (busyScreen(now)) return FAST_MILLIS;
        return BASE_MILLIS + still * STILL_STEP_MILLIS;
    }

    /** The shortest wait before a poll may sample again: the interval since the last sample started,
     * at least {@link #MIN_WAIT_MILLIS}, so an event or a finished sample can bring a poll forward. */
    public synchronized long earliest(boolean interactive, long now) {
        long interval = interval(interactive, now);
        if (!started || now < lastStart) return interactive ? MIN_WAIT_MILLIS : interval;
        return Math.max(MIN_WAIT_MILLIS, Math.min(interval, lastStart + interval - now));
    }

    /** Applies the coarse signature used by {@link #finished}: low colour bits are ignored, so the same
     * screen gives the same value. Not an image: the pixels cannot be recovered from it. */
    public static long signature(int[] pixels) {
        long hash = 1125899906842597L;
        for (int pixel : pixels) hash = 31 * hash + (pixel & 0xF0F0F0);
        return hash;
    }
}
