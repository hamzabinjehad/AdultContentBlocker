package app.hisn.core;

public final class BreathPatternTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError("Breath pattern: " + label);
    }

    public static void main(String[] args) {
        BreathPattern sigh = BreathPattern.SIGH;
        check(sigh.breathMillis() == 10_000, "a sigh breath is ten seconds");
        check(sigh.totalMillis() == 30_000, "three sigh breaths are thirty seconds");
        check(BreathPattern.EVEN.totalMillis() == 30_000, "three even breaths are thirty seconds");
        check(sigh.phase(0) == BreathPattern.Phase.IN && sigh.phase(1) == BreathPattern.Phase.TOP_UP
                && sigh.phase(2) == BreathPattern.Phase.OUT, "sigh is in, top-up, out");
        check(sigh.duration(2) > sigh.duration(0) + sigh.duration(1), "the exhale is the longest part");

        BreathPattern.Position start = sigh.at(0);
        check(start.breath == 0 && start.phase == BreathPattern.Phase.IN && !start.finished, "starts inhaling");
        check(start.secondsLeft() == 3, "three seconds to inhale");
        check(sigh.at(2_999).phase == BreathPattern.Phase.IN, "still inhaling just before three seconds");
        check(sigh.at(2_999).secondsLeft() == 1, "a partial second counts as one");
        check(sigh.at(3_000).phase == BreathPattern.Phase.TOP_UP, "top-up at three seconds");
        check(sigh.at(4_000).phase == BreathPattern.Phase.OUT, "exhale at four seconds");
        check(sigh.at(4_000).secondsLeft() == 6, "six seconds to exhale");
        check(sigh.at(10_000).breath == 1 && sigh.at(10_000).phase == BreathPattern.Phase.IN,
                "second breath starts at ten seconds");
        check(sigh.at(25_000).breath == 2, "third breath");

        BreathPattern.Position end = sigh.at(30_000);
        check(end.finished && end.breath == 2 && end.phase == BreathPattern.Phase.OUT, "ends after the last exhale");
        check(end.secondsLeft() == 0, "nothing left at the end");
        check(sigh.at(99_000).finished, "past the end stays finished");
        check(sigh.at(-50).breath == 0 && !sigh.at(-50).finished, "a clock before the start is the start");

        check(BreathPattern.fromKey("even") == BreathPattern.EVEN, "even key");
        check(BreathPattern.fromKey("sigh") == BreathPattern.SIGH, "sigh key");
        check(BreathPattern.fromKey(null) == BreathPattern.SIGH, "missing key is the recommended pattern");
        check(BreathPattern.fromKey("box") == BreathPattern.SIGH, "unknown key is the recommended pattern");

        check(sigh.ringTarget(BreathPattern.Phase.IN) < sigh.ringTarget(BreathPattern.Phase.TOP_UP),
                "the top-up grows the ring further");
        check(BreathPattern.EVEN.ringTarget(BreathPattern.Phase.IN) == 1f, "an even inhale fills the ring");
        check(sigh.ringTarget(BreathPattern.Phase.OUT) == BreathPattern.REST, "the exhale returns to rest");
        System.out.println("BreathPattern passed");
    }
}
