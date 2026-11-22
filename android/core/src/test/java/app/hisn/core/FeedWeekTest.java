package app.hisn.core;

public final class FeedWeekTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError("Feed week: " + label);
    }

    private static long[] days(long... values) { return values; }

    public static void main(String[] args) {
        long h = 3_600_000L;
        FeedWeek half = new FeedWeek(days(h, h, h, h, h, h, h, h / 2, h / 2, h / 2, h / 2, h / 2, h / 2, h / 2), 1f);
        check(half.thisWeekTotal() == 7 * h / 2, "this week total");
        check(half.lastWeekTotal() == 7 * h, "last week total");
        check(half.changePercent() == -50, "half the time is minus fifty percent");
        check(half.peak() == h / 2, "peak of this week");
        check(half.thisWeek().length == 7 && half.thisWeek()[6] == h / 2, "bars are this week, today last");

        FeedWeek morning = new FeedWeek(days(h, h, h, h, h, h, h, h, h, h, h, h, h, h / 4), 0.25f);
        check(morning.changePercent() == 0, "a quarter of today against a quarter of last week's day is even");

        FeedWeek fresh = new FeedWeek(days(0, 0, 0, 0, 0, h, h, h, h, h, h, h, h, h), 1f);
        check(fresh.changePercent() == null, "too few days last week means no comparison");

        FeedWeek none = new FeedWeek(new long[14], 0.5f);
        check(none.changePercent() == null && none.peak() == 1, "no data, no claims");

        FeedWeek negative = new FeedWeek(days(-5, h, h, h, h, h, h, h, h, h, h, h, h, h), 1f);
        check(negative.lastWeekTotal() == 6 * h, "a negative reading counts as zero");
        boolean refused = false;
        try { new FeedWeek(new long[3], 1f); } catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "wrong length refused");
        System.out.println("FeedWeek passed");
    }
}
