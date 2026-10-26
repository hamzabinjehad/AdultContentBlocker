package app.hisn.core;

public final class RemainingTimeTest {
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;

    private static void expect(RemainingTime.Unit unit, int count, long millis) {
        RemainingTime actual = RemainingTime.of(millis);
        if (actual.unit != unit || actual.count != count)
            throw new AssertionError(millis + " ms: expected " + count + " " + unit + ", got " + actual.count + " " + actual.unit);
    }

    public static void main(String[] args) {
        expect(RemainingTime.Unit.HOURS, 1, 1);
        expect(RemainingTime.Unit.HOURS, 1, HOUR);
        expect(RemainingTime.Unit.HOURS, 2, HOUR + 1);
        expect(RemainingTime.Unit.HOURS, 24, DAY);
        expect(RemainingTime.Unit.HOURS, 48, 2 * DAY);
        // Past 48 hours: days, never "about 1 day" and never more than half a day off.
        expect(RemainingTime.Unit.DAYS, 2, 2 * DAY + 1);
        expect(RemainingTime.Unit.DAYS, 2, 2 * DAY + 12 * HOUR - 1);
        expect(RemainingTime.Unit.DAYS, 3, 2 * DAY + 12 * HOUR);
        expect(RemainingTime.Unit.DAYS, 7, 7 * DAY);
        expect(RemainingTime.Unit.DAYS, 30, 30 * DAY - HOUR);
        expect(RemainingTime.Unit.DAYS, 90, 90 * DAY);
        for (long millis = 1; millis <= 90 * DAY; millis += 7 * 60_000 + 13) {
            RemainingTime time = RemainingTime.of(millis);
            long shown = time.count * (time.unit == RemainingTime.Unit.HOURS ? HOUR : DAY);
            if (time.unit == RemainingTime.Unit.HOURS && (shown < millis || time.count > RemainingTime.MAX_HOURS))
                throw new AssertionError("Hours understated or too many at " + millis);
            if (time.unit == RemainingTime.Unit.DAYS && (time.count < 2 || Math.abs(shown - millis) > DAY / 2))
                throw new AssertionError("Days too far off at " + millis);
        }
        for (long none : new long[]{0, -1}) {
            try { RemainingTime.of(none); throw new AssertionError("Nothing remaining accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("RemainingTime tests passed (hours up to 48, then nearest days, never understated hours)");
    }
}
