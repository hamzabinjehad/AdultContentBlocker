package app.hisn.core;

public final class RestPeriodTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(RestPeriod.DEFAULT_MINUTES == 5, "default is five minutes");
        check(RestPeriod.sanitize(5) == 5, "allowed value kept");
        check(RestPeriod.sanitize(1) == 1, "one minute kept");
        check(RestPeriod.sanitize(0) == 5 && RestPeriod.sanitize(-4) == 5,
                "zero and negative values fall back to the default");
        check(RestPeriod.sanitize(7) == 5 && RestPeriod.sanitize(61) == 5,
                "a value outside the choices falls back to the default");
        check(RestPeriod.millis(1) == 60_000L && RestPeriod.millis(15) == 900_000L
                && RestPeriod.millis(60) == 3_600_000L, "minutes convert to milliseconds");
        check(RestPeriod.millis(999) == RestPeriod.millis(RestPeriod.DEFAULT_MINUTES),
                "an invalid period uses the default in milliseconds");
        check(RestPeriod.remainingMinutes(0, 0) == 0 && RestPeriod.remainingMinutes(10, 20) == 0,
                "an ended period has no remaining minutes");
        check(RestPeriod.remainingMinutes(60_000L, 0) == 1
                && RestPeriod.remainingMinutes(60_001L, 0) == 2
                && RestPeriod.remainingMinutes(300_000L, 1) == 5,
                "remaining minutes round up");
        System.out.println("RestPeriod tests passed (default, choices, invalid fallback, millis, rounded remaining)");
    }
}
