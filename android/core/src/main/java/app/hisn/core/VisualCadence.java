package app.hisn.core;

import java.util.HashMap;
import java.util.Map;

/** How often selected-window image sampling runs. Android-free so the policy is tested.
 *
 * About every 2 s by default. While a selected window scrolls or its content changes (accessibility
 * events) it samples every {@link #FAST_MILLIS}, never sooner after the previous sample started, and
 * no poll runs sooner than {@link #FAST_MILLIS} after the previous poll, whether or not it sampled.
 * A video or animation that sends no events still keeps the default, because each sample's coarse
 * signature differs from the last one of the same window. When every window in the rotation gave
 * an unchanged sample and no events arrive, the interval grows by 1 s per unchanged round up to
 * {@link #STILL_MAX_MILLIS}; with the screen off, polls (which never sample) slow to
 * {@link #OFF_MILLIS}. One inference at a time and the frame gate are the caller's, unchanged.
 */
public final class VisualCadence {
    public static final long FAST_MILLIS = 1000, BASE_MILLIS = 2000, STILL_STEP_MILLIS = 1000,
            STILL_MAX_MILLIS = 5000, OFF_MILLIS = 10000, ACTIVE_FOR_MILLIS = 2000, MIN_WAIT_MILLIS = 200;
    private static final int STILL_MAX = (int) ((STILL_MAX_MILLIS - BASE_MILLIS) / STILL_STEP_MILLIS);

    private boolean active, started, polled;
    private long lastActivity, lastStart, lastPoll;
    /** Per window id: the last signature and how many unchanged samples followed it. */
    private final Map<Integer, long[]> windows = new HashMap<>();

    /** A selected window scrolled, changed content or changed state. */
    public synchronized void activity(long now) {
        active = true;
        lastActivity = now;
        for (long[] window : windows.values()) window[1] = 0;
    }

    /** A poll ran, whether or not it sampled. */
    public synchronized void polled(long now) {
        polled = true;
        lastPoll = now;
    }

    /** A sample of a window was requested. */
    public synchronized void started(long now) {
        started = true;
        lastStart = now;
    }

    /** A sample finished with a verdict. {@code signature} summarises the downscaled frame. */
    public synchronized void finished(int window, long signature) {
        long[] last = windows.get(window);
        if (last == null && windows.size() >= GuardWindows.MAX_WINDOWS) windows.clear();
        if (last == null) windows.put(window, new long[]{signature, 0});
        else if (last[0] == signature) last[1] = Math.min(last[1] + 1, STILL_MAX);
        else { last[0] = signature; last[1] = 0; }
    }

    /** Forgets windows no longer in the rotation, so a closed window neither holds nor keeps a back-off. */
    public synchronized void retain(java.util.Collection<Integer> ids) {
        windows.keySet().retainAll(ids);
    }

    /** Whether events arrived within the last {@link #ACTIVE_FOR_MILLIS}. */
    public synchronized boolean busyScreen(long now) {
        return active && now >= lastActivity && now - lastActivity <= ACTIVE_FOR_MILLIS;
    }

    /** Time from one poll to the next. A still screen backs off only as far as its least still window. */
    public synchronized long interval(boolean interactive, long now) {
        if (!interactive) return OFF_MILLIS;
        if (busyScreen(now)) return FAST_MILLIS;
        long still = windows.isEmpty() ? 0 : STILL_MAX;
        for (long[] window : windows.values()) still = Math.min(still, window[1]);
        return BASE_MILLIS + still * STILL_STEP_MILLIS;
    }

    /** The shortest wait before a poll may run again: the interval since the last sample started, and
     * at least {@link #FAST_MILLIS} after the last poll and {@link #MIN_WAIT_MILLIS} from now, so an
     * event or a finished sample can bring a poll forward but never make polls more frequent. */
    public synchronized long earliest(boolean interactive, long now) {
        long interval = interval(interactive, now);
        if (!interactive) return interval;
        long wait = started && now >= lastStart ? Math.min(interval, lastStart + interval - now) : MIN_WAIT_MILLIS;
        if (polled && now >= lastPoll) wait = Math.max(wait, lastPoll + FAST_MILLIS - now);
        return Math.max(MIN_WAIT_MILLIS, wait);
    }

    /** Applies the coarse signature used by {@link #finished}: low colour bits are ignored, so the same
     * screen gives the same value. Not an image: the pixels cannot be recovered from it. */
    public static long signature(int[] pixels) {
        long hash = 1125899906842597L;
        for (int pixel : pixels) hash = 31 * hash + (pixel & 0xF0F0F0);
        return hash;
    }
}
