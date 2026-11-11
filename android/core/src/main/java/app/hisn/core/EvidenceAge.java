package app.hisn.core;

/** Whole-unit age for a "checked … ago" line; it never renders sub-minute noise. */
public final class EvidenceAge {
    public static final long JUST_NOW_MILLIS = 60_000L;
    public static final long HOUR_MILLIS = 3_600_000L;

    public enum Unit { NOW, MINUTES, HOURS }

    public static Unit unit(long ageMillis) {
        if (ageMillis < 0) throw new IllegalArgumentException("Invalid evidence age");
        if (ageMillis < JUST_NOW_MILLIS) return Unit.NOW;
        if (ageMillis < HOUR_MILLIS) return Unit.MINUTES;
        return Unit.HOURS;
    }

    /** The whole count for the unit; zero for {@link Unit#NOW}. */
    public static int count(long ageMillis) {
        switch (unit(ageMillis)) {
            case NOW: return 0;
            case MINUTES: return (int) Math.max(1, ageMillis / JUST_NOW_MILLIS);
            default: return (int) Math.max(1, ageMillis / HOUR_MILLIS);
        }
    }

    private EvidenceAge() { }
}
