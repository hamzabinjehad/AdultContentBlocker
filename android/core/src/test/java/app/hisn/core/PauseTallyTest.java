package app.hisn.core;

public final class PauseTallyTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError("Pause tally: " + label); }
    public static void main(String[] args) {
        long day = 20_388, now = 1_800_000_000_000L;
        PauseTally first = PauseTally.record(0, 0, 0, day, now);
        check(first.count == 1 && first.day == day && first.at == now, "first pause");
        check(PauseTally.record(day, 1, now, day, now + 5_000).count == 1, "merge window");
        check(PauseTally.record(day, 1, now, day, now + 5_000).at == now, "merge keeps counted time");
        PauseTally merged = PauseTally.record(day, 1, now, day, now + 20_000);
        check(PauseTally.record(merged.day, merged.count, merged.at, day, now + PauseTally.MERGE_MS).count == 2,
                "reopening cannot hold a pause open forever");
        check(PauseTally.record(day, 7, now, day + 1, now + 60_000).count == 1, "new day starts over");
        check(PauseTally.record(day, 1, now, day, now - 60_000).count == 2, "clock moved backwards");
        check(PauseTally.record(day, -3, now, day, now).count == 1, "malformed count starts over");
        check(PauseTally.record(day, 9999, 0, day, now).count == 9999, "count is capped");
        check(PauseTally.reminderFor(1) == 1 && PauseTally.reminderFor(PauseTally.REMINDER_COUNT) == PauseTally.REMINDER_COUNT
                && PauseTally.reminderFor(PauseTally.REMINDER_COUNT + 1) == 1 && PauseTally.reminderFor(0) == 1, "reminders");
        System.out.println("PauseTally tests passed (merge window, day rollover, malformed values and reminders)");
    }
}
