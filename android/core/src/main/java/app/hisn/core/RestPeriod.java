package app.hisn.core;

/** The quiet period an app stays closed after a word or image detection.
 *
 * <p>This is a comfort and loop-breaking setting, not a detection rule: a detection always blocks,
 * and changing this value never makes a word or image pass. Only the app whose screen was
 * detected is held; other selected apps are unaffected. The allowed choices are fixed so a saved
 * value cannot become arbitrary or negative.</p>
 */
public final class RestPeriod {
    public static final int DEFAULT_MINUTES = 5;
    public static final int[] ALLOWED_MINUTES = {1, 5, 15, 30, 60};

    /** The saved minutes if allowed, else the default. */
    public static int sanitize(int minutes) {
        for (int allowed : ALLOWED_MINUTES) if (allowed == minutes) return minutes;
        return DEFAULT_MINUTES;
    }

    /** Milliseconds for a sanitized period. */
    public static long millis(int minutes) {
        return sanitize(minutes) * 60_000L;
    }

    /** Whole minutes still to wait, rounded up and never negative; 0 when the period has ended. */
    public static int remainingMinutes(long untilElapsedRealtime, long nowElapsedRealtime) {
        long left = untilElapsedRealtime - nowElapsedRealtime;
        if (left <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, (left + 59_999L) / 60_000L);
    }

    private RestPeriod() { }
}
