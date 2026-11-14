package app.hisn.core;

import java.util.Set;

public final class OpenWaitTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(OpenWait.sanitize(5) == 5 && OpenWait.sanitize(30) == 30, "offered waits are kept");
        check(OpenWait.sanitize(0) == OpenWait.DEFAULT_WAIT && OpenWait.sanitize(7) == OpenWait.DEFAULT_WAIT,
                "other values fall back to the default");

        check(OpenWait.seconds(10, false, 9) == 10, "a fixed wait does not grow");
        check(OpenWait.seconds(10, true, 0) == 10, "the first open waits the base");
        check(OpenWait.seconds(10, true, 2) == 20, "each open today adds five seconds");
        check(OpenWait.seconds(30, true, 20) == OpenWait.MAX_WAIT, "growth stops at the maximum");
        check(OpenWait.seconds(10, true, -3) == 10, "a bad count does not shorten the wait");

        long now = 10_000_000L;
        check(!OpenWait.needsWait(false, now, 0, 0), "an app not chosen never waits");
        check(OpenWait.needsWait(true, now, 0, 0), "a chosen app waits on its first open");
        check(!OpenWait.needsWait(true, now, 0, now + 1), "the opening window after Open anyway");
        check(!OpenWait.needsWait(true, now, now - 60_000L, 0), "coming back within the visit");
        check(OpenWait.needsWait(true, now, now - OpenWait.SAME_VISIT_MILLIS - 1, 0),
                "coming back after the visit ended waits again");
        check(OpenWait.needsWait(true, now, now + 5_000L, 0), "a clock that went back waits");

        check(OpenWait.inVisit(now, 0, now + 1), "the opening window counts as a visit");
        check(OpenWait.inVisit(now, now - 1_000L, 0), "use just now continues the visit");
        check(!OpenWait.inVisit(now, 0, 0), "an app never passed is not in a visit");
        check(!OpenWait.inVisit(now, now - OpenWait.SAME_VISIT_MILLIS - 1, 0), "an old visit is over");

        Set<String> one = Set.of("a"), two = Set.of("a", "b");
        check(!OpenWait.loosens(one, 10, true, two, 10, true), "adding an app is not loosening");
        check(OpenWait.loosens(two, 10, true, one, 10, true), "removing an app loosens");
        check(OpenWait.loosens(one, 10, true, one, 5, true), "a shorter wait loosens");
        check(!OpenWait.loosens(one, 10, false, one, 30, true), "a longer, growing wait does not");
        check(OpenWait.loosens(one, 10, true, one, 10, false), "turning growth off loosens");
        check(!OpenWait.loosens(Set.of(), 30, true, Set.of(), 5, false),
                "with no apps chosen, the wait settings change nothing");
        System.out.println("OpenWait tests passed (choices, growth, first open, visit, opening window, lock)");
    }
}
