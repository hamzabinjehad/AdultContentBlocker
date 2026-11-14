package app.hisn.core;

import java.util.Set;

/**
 * "Wait before opening": a few seconds of breathing before an app the person chose comes up,
 * with "Open anyway" once the wait is over (one sec and ScreenZen's idea). It never refuses the
 * app; that is what Apps to block is for.
 *
 * <p>Pure decisions only. The Android side keeps the clock readings and the per-day counts.</p>
 */
public final class OpenWait {
    /** The waits the settings offer, in seconds. */
    public static final int[] WAITS = {5, 10, 15, 30};
    public static final int DEFAULT_WAIT = 10;
    /** Each open already taken today adds this much, when the wait grows. */
    public static final int GROWTH_SECONDS = 5;
    public static final int MAX_WAIT = 60;
    /** Coming back to the app within this time after using it is the same visit: no new wait. */
    public static final long SAME_VISIT_MILLIS = 3 * 60_000L;
    /** After "Open anyway", the app has this long to come up before it counts as left. */
    public static final long OPENING_MILLIS = 30_000L;

    private OpenWait() { }

    /** A saved wait outside the choices falls back to the default. */
    public static int sanitize(int seconds) {
        for (int allowed : WAITS) if (allowed == seconds) return seconds;
        return DEFAULT_WAIT;
    }

    /** Seconds to wait before this open, given the opens already taken today. */
    public static int seconds(int base, boolean grows, int opensToday) {
        int wait = sanitize(base);
        if (!grows) return wait;
        return Math.min(MAX_WAIT, wait + GROWTH_SECONDS * Math.max(0, opensToday));
    }

    /**
     * Whether an app that just came to the front needs the wait.
     *
     * @param lastSeen when the app was last in use after a wait (0 for never)
     * @param allowedUntil end of the opening window after "Open anyway" (0 for none)
     */
    public static boolean needsWait(boolean chosen, long now, long lastSeen, long allowedUntil) {
        if (!chosen) return false;
        if (now < allowedUntil) return false;
        return lastSeen <= 0 || now - lastSeen > SAME_VISIT_MILLIS || now < lastSeen;
    }

    /** Whether an event from the app extends the current visit (only after a wait was passed). */
    public static boolean inVisit(long now, long lastSeen, long allowedUntil) {
        if (now < allowedUntil) return true;
        return lastSeen > 0 && now >= lastSeen && now - lastSeen <= SAME_VISIT_MILLIS;
    }

    /**
     * Whether a change makes the rule weaker: an app removed, a shorter wait, or growth turned
     * off. A running protection lock refuses these and allows everything else.
     */
    public static boolean loosens(Set<String> appsBefore, int waitBefore, boolean growsBefore,
                                  Set<String> appsAfter, int waitAfter, boolean growsAfter) {
        if (!appsAfter.containsAll(appsBefore)) return true;
        if (appsBefore.isEmpty()) return false;
        if (sanitize(waitAfter) < sanitize(waitBefore)) return true;
        return growsBefore && !growsAfter;
    }
}
