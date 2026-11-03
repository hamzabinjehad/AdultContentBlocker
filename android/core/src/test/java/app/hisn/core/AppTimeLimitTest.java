package app.hisn.core;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class AppTimeLimitTest {
    private static final String ONE = "org.example.one";
    private static final String TWO = "org.example.two";
    private static final String THREE = "org.example.three";

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void invalid(Runnable operation, String message) {
        try { operation.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static Set<String> set(String... items) {
        return new LinkedHashSet<>(Arrays.asList(items));
    }

    private static Map<String, Integer> limits(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put((String) pairs[index], (Integer) pairs[index + 1]);
        }
        return map;
    }

    public static void main(String[] args) {
        AppTimeLimit none = AppTimeLimit.none();
        check(!none.configured() && none.size() == 0 && none.serialized().isEmpty(), "empty limits");
        check(!none.exceeded(ONE, Long.MAX_VALUE), "empty limits exceed");
        check(none.remainingMillis(ONE, Long.MAX_VALUE) == Long.MAX_VALUE, "empty limits remaining");

        AppTimeLimit limits = new AppTimeLimit(limits(ONE, 30, TWO, 5, THREE, 1440));
        check(limits.configured() && limits.size() == 3, "limits configured");
        check(Integer.valueOf(30).equals(limits.minutesFor(ONE)), "minutes read back");
        check(limits.monitored(THREE) && !limits.monitored("org.example.other"), "monitored set");
        check(!limits.exceeded(ONE, 29L * 60000 + 59999), "blocked before the limit");
        check(limits.exceeded(ONE, 30L * 60000), "not blocked at the exact limit");
        check(limits.exceeded(ONE, 31L * 60000), "not blocked after the limit");
        check(limits.remainingMillis(ONE, 10L * 60000) == 20L * 60000, "remaining time");
        check(limits.remainingMillis(ONE, 40L * 60000) == 0, "remaining clamps at zero");
        check(limits.remainingMillis(ONE, -5) == 30L * 60000, "negative use reads as zero");
        check(!limits.exceeded(ONE, -5), "negative use exceeds");
        check(!limits.exceeded("org.example.other", Long.MAX_VALUE), "unmonitored app blocked");
        check(limits.remainingMillis("org.example.other", Long.MAX_VALUE) == Long.MAX_VALUE,
                "unmonitored remaining is unlimited");
        check(!limits.monitored(null) && !limits.exceeded(null, Long.MAX_VALUE), "null app");

        // A limit of one full day still blocks, and five minutes is accepted.
        check(new AppTimeLimit(limits(ONE, 1440)).exceeded(ONE, 86400000L), "full-day limit");
        check(!new AppTimeLimit(limits(ONE, 5)).exceeded(ONE, 4L * 60000), "five-minute limit early");
        check(new AppTimeLimit(limits(ONE, 5)).exceeded(ONE, 5L * 60000), "five-minute limit reached");

        // Validation and defensive copies.
        for (int value : new int[]{4, 1441, -1, 0, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            invalid(() -> new AppTimeLimit(limits(ONE, value)), "invalid minutes accepted: " + value);
        }
        invalid(() -> new AppTimeLimit(null), "null map accepted");
        invalid(() -> new AppTimeLimit(limits("invalid", 30)), "invalid package accepted");
        invalid(() -> new AppTimeLimit(limits(null, 30)), "null package accepted");
        Map<String, Integer> withNull = new LinkedHashMap<>();
        withNull.put(ONE, null);
        invalid(() -> new AppTimeLimit(withNull), "null minutes accepted");
        Map<String, Integer> original = limits(ONE, 30);
        AppTimeLimit immutable = new AppTimeLimit(original);
        original.put(TWO, 60);
        check(!immutable.monitored(TWO), "defensive copy");
        try { immutable.apps().add(TWO); throw new AssertionError("mutable public app set"); }
        catch (UnsupportedOperationException expected) { }
        Map<String, Integer> maximum = limits();
        for (int index = 0; index < AppTimeLimit.MAX_APPS; index++) maximum.put("org.example.app" + index, 30);
        check(new AppTimeLimit(maximum).size() == AppTimeLimit.MAX_APPS, "maximum apps accepted");
        maximum.put("org.example.excess", 30);
        invalid(() -> new AppTimeLimit(maximum), "too many limits accepted");

        // The saved form is stable, parseable, and refuses malformed entries.
        Set<String> serialized = limits.serialized();
        check(serialized.equals(set(ONE + "=30", TWO + "=5", THREE + "=1440")), "serialized form");
        check(AppTimeLimit.parse(serialized).equals(limits), "round trip");
        check(AppTimeLimit.parse(set()).configured() == false, "empty parse");
        invalid(() -> AppTimeLimit.parse(null), "null saved set accepted");
        for (String bad : new String[]{null, "", "=", "=30", ONE + "=", ONE + "=abc", ONE + "=4",
                ONE + "=1441", "invalid=30", ONE + "=30=40"}) {
            invalid(() -> AppTimeLimit.parse(set(bad)), "invalid saved value accepted: " + bad);
        }
        invalid(() -> AppTimeLimit.parse(set(ONE + "=30", ONE + "=60")), "duplicate app accepted");
        // A trailing duplicate with a different spelling is still the same app.
        invalid(() -> AppTimeLimit.parse(set(ONE + "=30", " " + ONE + "=60")), "whitespace app accepted");

        // Tightening: adding an app or lowering a limit is allowed; removing or raising is not.
        AppTimeLimit base = new AppTimeLimit(limits(ONE, 30, TWO, 60));
        check(base.tightens(base), "identical limits tighten");
        check(none.tightens(none), "empty limits tighten");
        check(base.tightens(none), "adding limits tightens");
        check(new AppTimeLimit(limits(ONE, 30, TWO, 30, THREE, 5)).tightens(base), "adding an app tightens");
        check(new AppTimeLimit(limits(ONE, 29, TWO, 60)).tightens(base), "lowering one limit tightens");
        check(new AppTimeLimit(limits(ONE, 30, TWO, 59, THREE, 5)).tightens(base), "lowering and adding tightens");
        check(!new AppTimeLimit(limits(ONE, 30)).tightens(base), "removing an app loosens");
        check(!new AppTimeLimit(limits(ONE, 31, TWO, 60)).tightens(base), "raising a limit loosens");
        check(!new AppTimeLimit(limits(ONE, 30, TWO, 61)).tightens(base), "raising the other limit loosens");
        check(!none.tightens(base), "removing every limit loosens");
        invalid(() -> base.tightens(null), "null previous limits accepted");

        System.out.println("AppTimeLimit tests passed (minutes, daily boundary, validation, saved form and tightening)");
    }
}
