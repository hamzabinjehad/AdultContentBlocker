package app.hisn.core;

import java.util.HashMap;
import java.util.Map;

/** How often selected-window image sampling runs. Android-free so the policy is tested.
 *
 * A moving surface is sampled every {@link #FAST_MILLIS}; a still one backs off from
 * {@link #BASE_MILLIS} to {@link #STILL_MAX_MILLIS}. A new or changed window (the person switched
 * apps, opened a player, rotated or split the screen) may be sampled at once after
 * {@link #MIN_WAIT_MILLIS}, because the previous frame belonged to another window and its verdict
 * can never be applied here. No sample is ever requested sooner than {@link #FAST_MILLIS} after the
 * previous one started, and no poll runs sooner than {@link #POLL_FLOOR_MILLIS} after the previous
 * poll, whether or not it sampled. A sample whose coarse signature changed also counts as activity,
 * so a video that sends no accessibility events keeps the fast interval instead of the still
 * back-off. With the screen off, polls (which never sample) slow to {@link #OFF_MILLIS}. One
 * inference at a time and the frame gate are the caller's, unchanged.
 */
public final class VisualCadence {
    /** The shortest distance between two samples while a surface is moving. */
    public static final long FAST_MILLIS = 250, BASE_MILLIS = 1000, STILL_STEP_MILLIS = 500,
            STILL_MAX_MILLIS = 2000, OFF_MILLIS = 10000, ACTIVE_FOR_MILLIS = 1500,
            MIN_WAIT_MILLIS = 50, POLL_FLOOR_MILLIS = 100;
    private static final int STILL_MAX = (int) ((STILL_MAX_MILLIS - BASE_MILLIS) / STILL_STEP_MILLIS);

    private boolean active, started, polled, changed;
    private long lastActivity, lastStart, lastPoll;
    /** Per window id: the last signature and how many unchanged samples followed it. */
    private final Map<Integer, long[]> windows = new HashMap<>();

    /** A selected window scrolled or changed content. */
    public synchronized void activity(long now) {
        active = true;
        lastActivity = now;
        for (long[] window : windows.values()) window[1] = 0;
    }

    /** A new, changed or refocused window: its first frame may be sampled without waiting out the
     * previous window's interval. A poll still needs {@link #POLL_FLOOR_MILLIS} after the last one. */
    public synchronized void windowChanged(long now) {
        changed = true;
        active = true;
        lastActivity = now;
        for (long[] window : windows.values()) window[1] = 0;
    }

    /** A poll ran, whether or not it sampled. */
    public synchronized void polled(long now) {
        polled = true;
        changed = false;
        lastPoll = now;
    }

    /** A sample of a window was requested. */
    public synchronized void started(long now) {
        started = true;
        lastStart = now;
    }

    /** A sample finished with a verdict. {@code signature} summarises the downscaled frame.
     * A different signature is activity: the surface moved, so keep the fast interval even when
     * the app sends no accessibility events (a playing video, an animation). */
    public synchronized void finished(int window, long signature, long now) {
        long[] last = windows.get(window);
        if (last == null && windows.size() >= GuardWindows.MAX_WINDOWS) windows.clear();
        if (last == null) {
            windows.put(window, new long[]{signature, 0});
            return;
        }
        if (last[0] == signature) {
            last[1] = Math.min(last[1] + 1, STILL_MAX);
        } else {
            last[0] = signature;
            last[1] = 0;
            active = true;
            lastActivity = now;
        }
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

    /** The shortest wait before a poll may run again: the interval since the last sample started,
     * at least {@link #POLL_FLOOR_MILLIS} after the last poll and {@link #MIN_WAIT_MILLIS} from now.
     * A changed window may ignore the previous window's interval but never the poll floor, so a
     * burst of window events cannot make polls more frequent than the floor allows. */
    public synchronized long earliest(boolean interactive, long now) {
        long interval = interval(interactive, now);
        if (!interactive) return interval;
        long wait;
        if (changed) {
            wait = MIN_WAIT_MILLIS;
        } else {
            wait = started && now >= lastStart ? Math.min(interval, lastStart + interval - now) : MIN_WAIT_MILLIS;
        }
        if (polled && now >= lastPoll) wait = Math.max(wait, lastPoll + POLL_FLOOR_MILLIS - now);
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
