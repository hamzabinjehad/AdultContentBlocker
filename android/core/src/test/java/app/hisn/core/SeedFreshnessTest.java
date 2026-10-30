package app.hisn.core;

/** Bundled-list age and the stale warning, without a runtime list download. */
public final class SeedFreshnessTest {
    public static void main(String[] args) {
        long now = java.time.Instant.parse("2026-10-30T12:00:00Z").toEpochMilli();
        check(SeedFreshness.ageDays("2026-10-01T20:29:42+00:00", now) == 28L, "age in whole days wrong");
        check(SeedFreshness.ageDays("2026-10-30T00:00:00Z", now) == 0L, "same-day age not zero");
        check(SeedFreshness.ageDays("2026-10-31T00:00:00Z", now) == 0L, "future age not clamped");
        check(SeedFreshness.ageDays("2026-10-01", now) == 29L, "plain date age wrong");
        check(SeedFreshness.ageDays("not-a-date", now) == null, "unparsable date returned an age");
        check(SeedFreshness.ageDays(null, now) == null, "null date returned an age");

        check(!SeedFreshness.stale("2026-10-01T20:29:42+00:00", now), "fresh seed warned");
        check(SeedFreshness.stale("2026-07-01T00:00:00Z", now), "90-day-old seed did not warn");
        check(SeedFreshness.stale("2026-07-31T23:59:59Z", now), "over-90-day seed did not warn");
        check(!SeedFreshness.stale("2026-08-02T00:00:00Z", now), "under-90-day seed warned");
        check(!SeedFreshness.stale("broken", now), "unparsable seed warned");

        System.out.println("SeedFreshness tests passed: whole-day age, date forms, clamping and the 90-day warning");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
