package app.hisn.core;

/**
 * This week's time in the person's feed apps against last week's, from Android's own daily usage
 * record. Fourteen days, oldest first; the last entry is today so far.
 *
 * <p>A comparison is only offered when it is fair: last week must have real use on enough days,
 * and today counts as the share of the day that has passed, so a morning never looks like a win
 * against a whole day. Without that it says nothing, rather than a flattering or scary number.
 */
public final class FeedWeek {
    public static final int DAYS = 7;
    /** Last week needs use on at least this many days before a percentage means anything. */
    public static final int MIN_DAYS_TO_COMPARE = 4;

    private final long[] days;
    private final float todayFraction;

    /**
     * @param fourteenDays foreground milliseconds per day, oldest first, today last
     * @param todayFraction how much of today has passed, 0 to 1
     */
    public FeedWeek(long[] fourteenDays, float todayFraction) {
        if (fourteenDays == null || fourteenDays.length != DAYS * 2) {
            throw new IllegalArgumentException("fourteen days expected");
        }
        days = fourteenDays.clone();
        for (int i = 0; i < days.length; i++) days[i] = Math.max(0, days[i]);
        this.todayFraction = Math.max(0.05f, Math.min(1f, todayFraction));
    }

    /** This week's seven days, oldest first, for the bars. */
    public long[] thisWeek() {
        long[] week = new long[DAYS];
        System.arraycopy(days, DAYS, week, 0, DAYS);
        return week;
    }

    public long thisWeekTotal() { return sum(DAYS, DAYS * 2); }

    public long lastWeekTotal() { return sum(0, DAYS); }

    /** The busiest day this week, for scaling the bars; at least one millisecond. */
    public long peak() {
        long peak = 1;
        for (long day : thisWeek()) peak = Math.max(peak, day);
        return peak;
    }

    /**
     * Percent change against last week, negative when the person spent less, or null when the
     * comparison would not be fair. Last week is scaled by the same share of its last day as
     * has passed today.
     */
    public Integer changePercent() {
        int used = 0;
        for (int i = 0; i < DAYS; i++) if (days[i] > 0) used++;
        if (used < MIN_DAYS_TO_COMPARE) return null;
        long previous = sum(0, DAYS - 1) + Math.round(days[DAYS - 1] * todayFraction);
        if (previous <= 0) return null;
        long current = thisWeekTotal();
        return Math.round((current - previous) * 100f / previous);
    }

    private long sum(int from, int to) {
        long total = 0;
        for (int i = from; i < to; i++) total += days[i];
        return total;
    }
}
