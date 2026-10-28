package app.hisn.core;

/**
 * Pauses shown on the restriction screen today: a local day and a count, never an app or a reason.
 * The same rules as the extension's block page (extension/lib/pause.js).
 */
public final class PauseTally {
    /** Restriction screens within this long of the last counted one are the same moment. */
    public static final long MERGE_MS = 30_000L;
    /** The number of pause_reminder_N strings. */
    public static final int REMINDER_COUNT = 5;
    private static final int MAX_COUNT = 9999;

    public final long day;
    public final int count;
    public final long at;

    private PauseTally(long day, int count, long at) {
        this.day = day;
        this.count = count;
        this.at = at;
    }

    /** The tally after one more restriction screen on {@code day} at {@code now}. Measured from the
     *  counted pause, so a screen reopened every few seconds cannot hold one pause open forever. */
    public static PauseTally record(long storedDay, int storedCount, long storedAt, long day, long now) {
        if (storedDay != day || storedCount <= 0) return new PauseTally(day, 1, now);
        long since = now - storedAt;
        if (since >= 0 && since < MERGE_MS) return new PauseTally(day, storedCount, storedAt);
        return new PauseTally(day, Math.min(storedCount + 1, MAX_COUNT), now);
    }

    /** Which reminder to show, 1-based; they take turns through the day. */
    public static int reminderFor(int count) {
        int n = count > 0 ? count : 1;
        return (n - 1) % REMINDER_COUNT + 1;
    }
}
