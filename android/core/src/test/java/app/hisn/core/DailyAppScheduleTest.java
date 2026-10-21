package app.hisn.core;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

public final class DailyAppScheduleTest {
    private static final String ONE = "org.example.one";
    private static final String TWO = "org.example.two";
    private static final String THREE = "org.example.three";

    private static Set<String> apps(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void invalid(Runnable operation, String message) {
        try { operation.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    public static void main(String[] args) {
        DailyAppSchedule disabled = DailyAppSchedule.disabled();
        check(!disabled.enabled && !disabled.isConfigured() && disabled.apps.isEmpty()
                && disabled.startMinute == 1320 && disabled.endMinute == 420, "explicit disabled defaults");
        check(!disabled.blocks(ONE, 1320), "disabled schedule cannot block");
        DailyAppSchedule daytime = new DailyAppSchedule(true, 540, 1020, apps(ONE, TWO));
        for (int minute = 0; minute < 1440; minute++) {
            check(daytime.blocks(ONE, minute) == (minute >= 540 && minute < 1020), "daytime boundary " + minute);
            check(daytime.blocks(TWO, minute) == daytime.blocks(ONE, minute), "all selected apps share coverage");
            check(!daytime.blocks(THREE, minute) && !daytime.blocks(null, minute), "unselected apps stay available");
        }
        DailyAppSchedule overnight = new DailyAppSchedule(true, 1320, 420, apps(ONE));
        for (int minute = 0; minute < 1440; minute++) {
            check(overnight.blocks(ONE, minute) == (minute >= 1320 || minute < 420), "overnight boundary " + minute);
        }
        check(!new DailyAppSchedule(false, 1320, 420, apps(ONE)).blocks(ONE, 0), "disabled retained choices do not block");
        check(!new DailyAppSchedule(true, 1320, 420, apps()).isConfigured(), "enabled empty app set has no coverage");

        // Every valid start minute, with an exactly one-minute restriction,
        // including 23:59-00:00 and the largest expressible 1,439-minute window.
        for (int start = 0; start < 1440; start++) {
            int end = (start + 1) % 1440;
            DailyAppSchedule oneMinute = new DailyAppSchedule(true, start, end, apps(ONE));
            DailyAppSchedule allButOne = new DailyAppSchedule(true, end, start, apps(ONE));
            int smallCount = 0, largeCount = 0;
            for (int minute = 0; minute < 1440; minute++) {
                if (oneMinute.blocks(ONE, minute)) smallCount++;
                if (allButOne.blocks(ONE, minute)) largeCount++;
                check(oneMinute.blocks(ONE, minute) == (minute == start), "exhaustive single minute");
                check(oneMinute.blocks(ONE, minute) != allButOne.blocks(ONE, minute), "complementary windows");
            }
            check(smallCount == 1 && largeCount == 1439, "exact daily coverage cardinality");
        }
        for (int value : new int[]{-1, 1440, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            invalid(() -> new DailyAppSchedule(true, value, 60, apps()), "invalid start accepted");
            invalid(() -> new DailyAppSchedule(true, 60, value, apps()), "invalid end accepted");
            invalid(() -> disabled.blocks(ONE, value), "invalid current minute accepted");
        }
        for (boolean enabled : new boolean[]{false, true}) {
            invalid(() -> new DailyAppSchedule(enabled, 0, 0, apps()), "equal midnight accepted");
            invalid(() -> new DailyAppSchedule(enabled, 1439, 1439, apps()), "equal endpoints accepted");
        }
        invalid(() -> new DailyAppSchedule(true, 0, 1, null), "null packages accepted");
        for (String name : new String[]{null, "", "example", ".org.example", "org.example.", "org..example",
                "org.-example", "1org.example", "org.ex ample", "org.example\n", "org." + "a".repeat(252)}) {
            invalid(() -> new DailyAppSchedule(true, 0, 1, apps(name)), "invalid package accepted: " + name);
        }
        new DailyAppSchedule(true, 0, 1, apps("Org.Example_2", "org.1example", "o." + "a".repeat(253)));
        Set<String> maximum = apps();
        for (int index = 0; index < 200; index++) maximum.add("org.example.app" + index);
        check(new DailyAppSchedule(true, 0, 1, maximum).apps.size() == 200, "200 apps accepted");
        maximum.add("org.example.excess");
        invalid(() -> new DailyAppSchedule(false, 0, 1, maximum), "too many retained packages accepted");
        Set<String> original = apps(ONE);
        DailyAppSchedule immutable = new DailyAppSchedule(true, 0, 60, original);
        original.add(TWO);
        check(!immutable.apps.contains(TWO), "defensive copy");
        try { immutable.apps.add(TWO); throw new AssertionError("mutable public app set"); }
        catch (UnsupportedOperationException expected) { }

        check(daytime.preserves(daytime, apps()), "identical coverage preserved");
        check(new DailyAppSchedule(true, 480, 1080, apps(ONE, TWO, THREE)).preserves(daytime, apps()), "time and apps can expand");
        check(!new DailyAppSchedule(true, 541, 1020, apps(ONE, TWO)).preserves(daytime, apps()), "one-minute delayed start weakens");
        check(!new DailyAppSchedule(true, 540, 1019, apps(ONE, TWO)).preserves(daytime, apps()), "one-minute early end weakens");
        check(!new DailyAppSchedule(true, 540, 1020, apps(ONE)).preserves(daytime, apps()), "removing old app weakens");
        check(!disabled.preserves(daytime, apps()), "disabling selected coverage weakens");
        check(disabled.preserves(daytime, apps(ONE, TWO)), "permanent blocking covers all scheduled apps");
        check(!disabled.preserves(daytime, apps(ONE)), "partial permanent coverage is insufficient");
        check(new DailyAppSchedule(true, 540, 1020, apps(ONE)).preserves(daytime, apps(TWO)), "mixed schedule and permanent coverage");
        check(!new DailyAppSchedule(true, 541, 1020, apps(ONE)).preserves(daytime, apps(TWO)), "mixed coverage cannot lose minutes");
        check(new DailyAppSchedule(true, 1260, 480, apps(ONE)).preserves(overnight, apps()), "overnight window can expand");
        check(!new DailyAppSchedule(true, 1321, 420, apps(ONE)).preserves(overnight, apps()), "overnight start cannot shrink");
        check(!new DailyAppSchedule(true, 1320, 419, apps(ONE)).preserves(overnight, apps()), "overnight end cannot shrink");
        check(!new DailyAppSchedule(true, 420, 1320, apps(ONE)).preserves(overnight, apps()), "complementary time is not preservation");
        check(disabled.preserves(disabled, apps()), "disabled prior imposes no coverage");
        check(disabled.preserves(new DailyAppSchedule(false, 60, 120, apps(ONE)), apps()), "disabled retained choices impose no coverage");
        check(disabled.preserves(new DailyAppSchedule(true, 60, 120, apps()), apps()), "empty prior choices impose no coverage");
        invalid(() -> daytime.preserves(null, apps()), "null old schedule accepted");
        invalid(() -> daytime.preserves(daytime, null), "null permanent set accepted");
        invalid(() -> daytime.preserves(daytime, apps("invalid")), "invalid permanent package accepted");
        invalid(() -> daytime.preserves(daytime, maximum), "oversized permanent set accepted");
        checkRandomCoverage();
        System.out.println("DailyAppSchedule tests passed; all 1,440 boundaries, overnight and commitment coverage verified");
    }

    /** Separate minute-grid oracle checks preservation across mixed per-app layers. */
    private static void checkRandomCoverage() {
        Random random = new Random(1439);
        String[] packages = {ONE, TWO, THREE};
        for (int trial = 0; trial < 3000; trial++) {
            int oldStart = random.nextInt(1440), nextStart = random.nextInt(1440);
            int oldEnd = (oldStart + 1 + random.nextInt(1439)) % 1440;
            int nextEnd = (nextStart + 1 + random.nextInt(1439)) % 1440;
            Set<String> oldApps = apps(), nextApps = apps(), permanent = apps();
            for (String name : packages) {
                if (random.nextBoolean()) oldApps.add(name);
                if (random.nextBoolean()) nextApps.add(name);
                if (random.nextBoolean()) permanent.add(name);
            }
            DailyAppSchedule previous = new DailyAppSchedule(random.nextBoolean(), oldStart, oldEnd, oldApps);
            DailyAppSchedule next = new DailyAppSchedule(random.nextBoolean(), nextStart, nextEnd, nextApps);
            boolean expected = true;
            for (String name : packages) for (int minute = 0; minute < 1440; minute++) {
                int oldDistance = (minute - oldStart + 1440) % 1440;
                int oldDuration = (oldEnd - oldStart + 1440) % 1440;
                int newDistance = (minute - nextStart + 1440) % 1440;
                int newDuration = (nextEnd - nextStart + 1440) % 1440;
                boolean oldBlocked = previous.enabled && oldApps.contains(name) && oldDistance < oldDuration;
                boolean nextBlocked = permanent.contains(name)
                        || (next.enabled && nextApps.contains(name) && newDistance < newDuration);
                if (oldBlocked && !nextBlocked) expected = false;
            }
            check(next.preserves(previous, permanent) == expected, "random independent per-app coverage oracle " + trial);
        }
    }
}
