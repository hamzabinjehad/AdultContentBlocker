package app.hisn.core;

public final class PauseHoursTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError("Pause hours: " + label);
    }

    public static void main(String[] args) {
        int[] empty = PauseHours.parse(null);
        check(empty.length == 24 && PauseHours.total(empty) == 0, "missing value is empty");
        check(PauseHours.total(PauseHours.parse("1,2,3")) == 0, "wrong length is empty");
        check(PauseHours.total(PauseHours.parse("x" + ",0".repeat(23))) == 0, "non-number is empty");
        check(PauseHours.total(PauseHours.parse("-1" + ",0".repeat(23))) == 0, "negative is empty");
        check(PauseHours.total(PauseHours.parse("10000" + ",0".repeat(23))) == 0, "over the bound is empty");

        String saved = PauseHours.record(null, 23);
        saved = PauseHours.record(saved, 23);
        saved = PauseHours.record(saved, 0);
        int[] counts = PauseHours.parse(saved);
        check(counts[23] == 2 && counts[0] == 1 && PauseHours.total(counts) == 3, "records by hour");
        check(PauseHours.format(counts).equals(saved), "format round-trips");
        check(PauseHours.record(saved, 24).equals(saved), "hour 24 is ignored");
        check(PauseHours.record(saved, -1).equals(saved), "negative hour is ignored");
        check(PauseHours.total(PauseHours.parse(PauseHours.record("garbage", 5))) == 1,
                "damaged value starts again");

        check(PauseHours.peakStart(counts) == -1, "too few pauses give no peak");
        saved = PauseHours.record(PauseHours.record(saved, 1), 23);
        counts = PauseHours.parse(saved);
        // 23h: 3, 0h: 1, 1h: 1 -> the window starting at 23 wraps midnight and holds all five.
        check(PauseHours.peakStart(counts) == 23, "peak wraps past midnight");

        int[] day = new int[24];
        day[14] = 4; day[15] = 4; day[21] = 4; day[22] = 4;
        check(PauseHours.peakStart(day) == 13, "a tie goes to the earliest window");
        check(PauseHours.peakStart(new int[3]) == -1, "wrong length gives no peak");

        int[] full = new int[24];
        full[7] = 9999;
        check(PauseHours.parse(PauseHours.record(PauseHours.format(full), 7))[7] == 9999, "count is bounded");
        System.out.println("PauseHours tests passed (parsing, bounds, midnight wrap and tie rule)");
    }
}
