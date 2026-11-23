package app.hisn.core;

public final class GlassStateTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError("Glass state: " + label); }

    public static void main(String[] args) {
        long t = 1_800_000_000_000L, day = 20_833, min = 60_000L;
        check(GlassState.at(t, 0, 0, 0, 0, day).look == GlassState.Look.CLEAR, "untouched glass is clear");

        GlassState cracked = GlassState.at(t + min, t, 0, 0, 0, day);
        check(cracked.look == GlassState.Look.CRACKED, "an adult page cracks the glass");
        check(cracked.sealLeftMs == 9 * min, "nine minutes left to seal it");
        check(GlassState.at(t + 10 * min, t, 0, 0, 0, day).look == GlassState.Look.CLEAR, "an unsealed crack fades after the window, never breaks");

        check(GlassState.breathSeals(t + 4 * min, t, 0), "a breath inside the window seals");
        check(!GlassState.breathSeals(t + 11 * min, t, 0), "a breath after the window does not");
        check(!GlassState.breathSeals(t + 5 * min, t, t + 4 * min), "a crack seals only once");
        check(!GlassState.breathSeals(t + 5 * min, 0, 0), "a breath with no crack seals nothing");

        check(GlassState.at(t + 5 * min, t, t + 4 * min, 0, day, day).look == GlassState.Look.GOLD, "sealed shows gold");
        check(GlassState.at(t + 9 * 3_600_000L, t, t + 4 * min, 0, day, day).look == GlassState.Look.GOLD, "gold lasts the day");
        check(GlassState.at(t + 30 * 3_600_000L, t, t + 4 * min, 0, day, day + 1).look == GlassState.Look.CLEAR, "a new day is clear");
        check(GlassState.at(t + 6 * min, t + 5 * min, t + 4 * min, 0, day, day).look == GlassState.Look.CRACKED, "a new crack over gold is a crack");

        check(GlassState.at(t + min, 0, 0, t, 0, day).look == GlassState.Look.BROKEN, "a report breaks the glass");
        check(GlassState.at(t + 61 * min, 0, 0, t, 0, day).look == GlassState.Look.CLEAR, "new glass after an hour");
        check(GlassState.at(t + 2 * min, t + min, 0, t, 0, day).look == GlassState.Look.CRACKED, "a later crack shows over an older break");
        check(GlassState.at(t - min, t, 0, t, 0, day).look == GlassState.Look.CLEAR, "a clock moved back never sticks");
        System.out.println("GlassState tests passed (crack window, gold seal once, day rollover, broken by report, clock moved back)");
    }
}
