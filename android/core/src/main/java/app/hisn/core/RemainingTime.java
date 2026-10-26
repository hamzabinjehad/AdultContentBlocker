package app.hisn.core;

/** How a remaining commitment is worded: "about N hours" up to 48 hours, then "about N days".
 * Hours round up, so the last hours are never understated; days round to the nearest day. */
public final class RemainingTime {
    public enum Unit { HOURS, DAYS }
    public static final int MAX_HOURS = 48;
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;

    public final Unit unit;
    public final int count;

    private RemainingTime(Unit unit, long count) {
        this.unit = unit;
        this.count = (int) Math.min(Integer.MAX_VALUE, count);
    }

    public static RemainingTime of(long millis) {
        if (millis <= 0) throw new IllegalArgumentException("Nothing remains");
        long hours = (millis - 1) / HOUR + 1;
        if (hours <= MAX_HOURS) return new RemainingTime(Unit.HOURS, hours);
        return new RemainingTime(Unit.DAYS, (millis + DAY / 2) / DAY);
    }
}
