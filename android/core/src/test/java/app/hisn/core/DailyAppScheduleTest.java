package app.hisn.core;

import java.time.DayOfWeek;
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

    /** The oracle for window coverage: an independent restatement of the day rule. */
    private static boolean covers(int days, int start, int end, DayOfWeek day, int minute) {
        int bit = 1 << (day.getValue() - 1);
        if (start < end) return (days & bit) != 0 && minute >= start && minute < end;
        int before = 1 << (day.minus(1).getValue() - 1);
        return ((days & bit) != 0 && minute >= start) || ((days & before) != 0 && minute < end);
    }

    public static void main(String[] args) {
        DailyAppSchedule disabled = DailyAppSchedule.disabled();
        check(!disabled.enabled && !disabled.isConfigured() && disabled.apps.isEmpty()
                && disabled.startMinute == 1320 && disabled.endMinute == 420
                && disabled.days == DailyAppSchedule.ALL_DAYS, "explicit disabled defaults");
        check(!disabled.blocks(ONE, 1320, DayOfWeek.MONDAY), "disabled schedule cannot block");
        DailyAppSchedule daytime = new DailyAppSchedule(true, 540, 1020, apps(ONE, TWO));
        for (DayOfWeek day : DayOfWeek.values()) {
            for (int minute = 0; minute < 1440; minute++) {
                check(daytime.blocks(ONE, minute, day) == (minute >= 540 && minute < 1020),
                        "daytime boundary " + day + " " + minute);
                check(daytime.blocks(TWO, minute, day) == daytime.blocks(ONE, minute, day),
                        "all selected apps share coverage");
                check(!daytime.blocks(THREE, minute, day) && !daytime.blocks(null, minute, day),
                        "unselected apps stay available");
            }
        }
        DailyAppSchedule overnight = new DailyAppSchedule(true, 1320, 420, apps(ONE));
        for (DayOfWeek day : DayOfWeek.values()) {
            for (int minute = 0; minute < 1440; minute++) {
                check(overnight.blocks(ONE, minute, day) == (minute >= 1320 || minute < 420),
                        "overnight boundary " + day + " " + minute);
            }
        }
        check(!new DailyAppSchedule(false, 1320, 420, apps(ONE)).blocks(ONE, 0, DayOfWeek.MONDAY),
                "disabled retained choices do not block");
        check(!new DailyAppSchedule(true, 1320, 420, apps()).isConfigured(),
                "enabled empty app set has no coverage");

        // Every valid start minute, with an exactly one-minute restriction,
        // including 23:59-00:00 and the largest expressible 1,439-minute window.
        for (int start = 0; start < 1440; start++) {
            int end = (start + 1) % 1440;
            DailyAppSchedule oneMinute = new DailyAppSchedule(true, start, end, apps(ONE));
            DailyAppSchedule allButOne = new DailyAppSchedule(true, end, start, apps(ONE));
            int smallCount = 0, largeCount = 0;
            for (int minute = 0; minute < 1440; minute++) {
                if (oneMinute.blocks(ONE, minute, DayOfWeek.WEDNESDAY)) smallCount++;
                if (allButOne.blocks(ONE, minute, DayOfWeek.WEDNESDAY)) largeCount++;
                check(oneMinute.blocks(ONE, minute, DayOfWeek.WEDNESDAY) == (minute == start),
                        "exhaustive single minute");
                check(oneMinute.blocks(ONE, minute, DayOfWeek.WEDNESDAY)
                        != allButOne.blocks(ONE, minute, DayOfWeek.WEDNESDAY), "complementary windows");
            }
            check(smallCount == 1 && largeCount == 1439, "exact daily coverage cardinality");
        }
        for (int value : new int[]{-1, 1440, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            invalid(() -> new DailyAppSchedule(true, value, 60, apps()), "invalid start accepted");
            invalid(() -> new DailyAppSchedule(true, 60, value, apps()), "invalid end accepted");
            invalid(() -> disabled.blocks(ONE, value, DayOfWeek.MONDAY), "invalid current minute accepted");
            invalid(() -> disabled.blocks(ONE, 0, null), "null day accepted");
            invalid(() -> disabled.activeAt(0, null), "null day accepted by activeAt");
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

        // Per-day windows: a Monday-only overnight window covers Monday evening and
        // Tuesday morning, and nothing else.
        int monday = DailyAppSchedule.bitOf(DayOfWeek.MONDAY);
        DailyAppSchedule mondayNight = new DailyAppSchedule(true, 1320, 420, apps(ONE), false, monday);
        check(mondayNight.days == 1 && mondayNight.blocks(ONE, 0, DayOfWeek.TUESDAY),
                "overnight window reaches into the next day");
        check(mondayNight.blocks(ONE, 23 * 60 + 30, DayOfWeek.MONDAY), "overnight window covers its start day evening");
        check(!mondayNight.blocks(ONE, 12 * 60, DayOfWeek.TUESDAY), "day after morning stays open");
        check(!mondayNight.blocks(ONE, 23 * 60 + 30, DayOfWeek.TUESDAY), "next day evening stays open");
        check(!mondayNight.blocks(ONE, 12 * 60, DayOfWeek.MONDAY), "own morning stays open");
        for (DayOfWeek day : DayOfWeek.values()) {
            for (int minute = 0; minute < 1440; minute++) {
                check(mondayNight.blocks(ONE, minute, day)
                        == covers(monday, 1320, 420, day, minute), "anchored coverage oracle " + day + " " + minute);
            }
        }
        // A window that fits inside one day touches nothing on the other days.
        DailyAppSchedule fridayNoon = new DailyAppSchedule(true, 720, 780, apps(ONE), false,
                DailyAppSchedule.bitOf(DayOfWeek.FRIDAY));
        check(fridayNoon.blocks(ONE, 721, DayOfWeek.FRIDAY), "selected day inside window");
        check(!fridayNoon.blocks(ONE, 721, DayOfWeek.THURSDAY), "unselected day inside window");
        check(!fridayNoon.blocks(ONE, 780, DayOfWeek.FRIDAY), "end is exclusive");
        // Day masks are validated: no day, a week plus a bit, and negatives are refused.
        for (int mask : new int[]{0, 128, 255, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            invalid(() -> new DailyAppSchedule(true, 0, 60, apps(ONE), false, mask), "invalid day mask " + mask);
        }
        new DailyAppSchedule(false, 0, 60, apps(ONE), false, 1);
        // bitOf covers the ISO week exactly once per day.
        int allBits = 0;
        for (DayOfWeek day : DayOfWeek.values()) {
            int bit = DailyAppSchedule.bitOf(day);
            check((allBits & bit) == 0, "day bit repeated");
            allBits |= bit;
        }
        check(allBits == DailyAppSchedule.ALL_DAYS, "day bits do not make the week");
        // Allowlist arming honours the day mask and the midnight boundary.
        DailyAppSchedule mondayAllowlist = new DailyAppSchedule(true, 0, 60, apps(ONE), true, monday);
        check(mondayAllowlist.selects(TWO, 23 * 60 + 55, DayOfWeek.SUNDAY),
                "armed before a window that starts at midnight");
        check(!mondayAllowlist.selects(TWO, 23 * 60 + 55, DayOfWeek.MONDAY),
                "not armed when tomorrow is not selected");
        check(!mondayAllowlist.selects(TWO, 23 * 60 + 55, DayOfWeek.TUESDAY),
                "tuesday midnight arm waits for a selected wednesday");
        check(mondayAllowlist.selects(TWO, 0, DayOfWeek.MONDAY), "monday midnight window is open on monday");
        check(new DailyAppSchedule(true, 1320, 420, apps(ONE), true, monday).selects(TWO, 21 * 60 + 55,
                DayOfWeek.MONDAY), "armed ten minutes before an overnight start on its day");
        check(!new DailyAppSchedule(true, 1320, 420, apps(ONE), true, monday).selects(TWO, 21 * 60 + 55,
                DayOfWeek.TUESDAY), "not armed on an unselected day");
        // Every-day schedules keep the previous behaviour on every day.
        DailyAppSchedule everyday = new DailyAppSchedule(true, 540, 1020, apps(ONE));
        for (DayOfWeek day : DayOfWeek.values()) {
            check(everyday.activeAt(600, day) && !everyday.activeAt(500, day),
                    "every-day schedule active identically " + day);
        }

        check(daytime.preserves(daytime, apps()), "identical coverage preserved");
        check(new DailyAppSchedule(true, 480, 1080, apps(ONE, TWO, THREE)).preserves(daytime, apps()),
                "time and apps can expand");
        check(!new DailyAppSchedule(true, 541, 1020, apps(ONE, TWO)).preserves(daytime, apps()),
                "one-minute delayed start weakens");
        check(!new DailyAppSchedule(true, 540, 1019, apps(ONE, TWO)).preserves(daytime, apps()),
                "one-minute early end weakens");
        check(!new DailyAppSchedule(true, 540, 1020, apps(ONE)).preserves(daytime, apps()),
                "removing old app weakens");
        check(!disabled.preserves(daytime, apps()), "disabling selected coverage weakens");
        check(disabled.preserves(daytime, apps(ONE, TWO)), "permanent blocking covers all scheduled apps");
        check(!disabled.preserves(daytime, apps(ONE)), "partial permanent coverage is insufficient");
        check(new DailyAppSchedule(true, 540, 1020, apps(ONE)).preserves(daytime, apps(TWO)),
                "mixed schedule and permanent coverage");
        check(!new DailyAppSchedule(true, 541, 1020, apps(ONE)).preserves(daytime, apps(TWO)),
                "mixed coverage cannot lose minutes");

        // Allowlist mode: every app except the kept ones is blocked inside the window.
        DailyAppSchedule night = new DailyAppSchedule(true, 1320, 420, apps(ONE), true);
        check(night.isConfigured() && night.allApps, "allowlist schedule not configured");
        check(new DailyAppSchedule(true, 1320, 420, apps(), true).isConfigured(), "empty allowlist has no coverage");
        check(night.blocks(TWO, 1320, DayOfWeek.MONDAY) && !night.blocks(ONE, 1320, DayOfWeek.MONDAY),
                "allowlist blocks the unkept app only");
        check(!night.blocks(TWO, 600, DayOfWeek.MONDAY) && !night.blocks(ONE, 600, DayOfWeek.MONDAY),
                "allowlist blocks nothing outside its window");
        check(!night.blocks(null, 1320, DayOfWeek.MONDAY), "allowlist blocked a null package");
        check(night.targets(TWO) && !night.targets(ONE) && night.selects(TWO, 1320, DayOfWeek.MONDAY),
                "allowlist targets wrong app");
        check(!night.selects(TWO, 1200, DayOfWeek.MONDAY), "allowlist armed too early");
        check(night.selects(TWO, 1310, DayOfWeek.MONDAY), "allowlist not armed before the window");
        check(!night.selects(ONE, 1320, DayOfWeek.MONDAY), "allowlist watched a kept app");
        check(daytime.selects(ONE, 0, DayOfWeek.SUNDAY) && daytime.selects(ONE, 600, DayOfWeek.SUNDAY),
                "blocklist no longer watched all day");
        check(!new DailyAppSchedule(false, 1320, 420, apps(ONE), true).blocks(TWO, 1320, DayOfWeek.MONDAY),
                "disabled allowlist blocked");

        // Mode changes are judged by coverage, not by the menu used.
        check(!new DailyAppSchedule(true, 540, 1020, apps(ONE), true).preserves(daytime, apps()),
                "blocklist moved to keep list");
        check(new DailyAppSchedule(true, 480, 1080, apps(THREE), true).preserves(daytime, apps()),
                "blocklist became a wider allowlist");
        check(!night.preserves(daytime, apps()), "blocklist became a narrower allowlist");
        check(!daytime.preserves(night, apps()), "allowlist cannot become a blocklist");
        check(new DailyAppSchedule(true, 1320, 420, apps(), true).preserves(night, apps()),
                "allowlist exemptions can shrink");
        check(!new DailyAppSchedule(true, 1320, 420, apps(ONE, TWO), true).preserves(night, apps()),
                "allowlist exemption can grow");
        check(!new DailyAppSchedule(true, 1321, 420, apps(ONE), true).preserves(night, apps()),
                "allowlist window can shrink");
        check(new DailyAppSchedule(true, 1260, 480, apps(ONE), true).preserves(night, apps()),
                "allowlist window can expand");
        check(new DailyAppSchedule(true, 1260, 480, apps(ONE)).preserves(overnight, apps()),
                "overnight window can expand");
        check(!new DailyAppSchedule(true, 1321, 420, apps(ONE)).preserves(overnight, apps()),
                "overnight start cannot shrink");
        check(!new DailyAppSchedule(true, 1320, 419, apps(ONE)).preserves(overnight, apps()),
                "overnight end cannot shrink");
        check(!new DailyAppSchedule(true, 420, 1320, apps(ONE)).preserves(overnight, apps()),
                "complementary time is not preservation");
        check(disabled.preserves(disabled, apps()), "disabled prior imposes no coverage");
        check(disabled.preserves(new DailyAppSchedule(false, 60, 120, apps(ONE)), apps()),
                "disabled retained choices impose no coverage");
        check(disabled.preserves(new DailyAppSchedule(true, 60, 120, apps()), apps()),
                "empty prior choices impose no coverage");
        invalid(() -> daytime.preserves(null, apps()), "null old schedule accepted");
        invalid(() -> daytime.preserves(daytime, null), "null permanent set accepted");
        invalid(() -> daytime.preserves(daytime, apps("invalid")), "invalid permanent package accepted");
        invalid(() -> daytime.preserves(daytime, maximum), "oversized permanent set accepted");

        // Day-mask preservation: adding days and keeping the window keeps coverage;
        // removing a day, or moving the overnight part off the morning after, weakens.
        check(new DailyAppSchedule(true, 1320, 420, apps(ONE), false,
                monday | DailyAppSchedule.bitOf(DayOfWeek.TUESDAY)).preserves(mondayNight, apps()),
                "adding a day keeps coverage");
        check(!new DailyAppSchedule(true, 1320, 420, apps(ONE), false,
                DailyAppSchedule.bitOf(DayOfWeek.TUESDAY)).preserves(mondayNight, apps()),
                "removing the anchored day weakens");
        check(!new DailyAppSchedule(true, 0, 420, apps(ONE), false, monday).preserves(mondayNight, apps()),
                "dropping the evening start weakens");
        check(!new DailyAppSchedule(true, 1320, 60, apps(ONE), false, monday).preserves(mondayNight, apps()),
                "shortening the next morning weakens");
        check(!new DailyAppSchedule(true, 1320, 60, apps(ONE), false,
                monday | DailyAppSchedule.bitOf(DayOfWeek.TUESDAY)).preserves(mondayNight, apps()),
                "a later night does not cover the morning it left open");
        check(new DailyAppSchedule(true, 1320, 60, apps(ONE), false, monday)
                .preserves(new DailyAppSchedule(true, 0, 60, apps(ONE), false,
                        DailyAppSchedule.bitOf(DayOfWeek.TUESDAY)), apps()),
                "the Monday night covers Tuesday's early morning");
        check(new DailyAppSchedule(true, 540, 1020, apps(ONE, TWO), false,
                DailyAppSchedule.ALL_DAYS).preserves(daytime, apps()),
                "every-day window preserves an every-day window");
        check(!new DailyAppSchedule(true, 540, 1020, apps(ONE, TWO), false,
                DailyAppSchedule.ALL_DAYS & ~DailyAppSchedule.bitOf(DayOfWeek.SUNDAY))
                .preserves(daytime, apps()), "dropping any day weakens an every-day window");
        checkRandomCoverage();
        System.out.println("DailyAppSchedule tests passed; all 1,440 boundaries, per-day windows, "
                + "overnight and commitment coverage verified");
    }

    /** Separate minute-grid oracle checks preservation across mixed per-app layers and day masks. */
    private static void checkRandomCoverage() {
        Random random = new Random(1439);
        String[] packages = {ONE, TWO, THREE};
        for (int trial = 0; trial < 3000; trial++) {
            int oldStart = random.nextInt(1440), nextStart = random.nextInt(1440);
            int oldEnd = (oldStart + 1 + random.nextInt(1439)) % 1440;
            int nextEnd = (nextStart + 1 + random.nextInt(1439)) % 1440;
            int oldDays = 1 + random.nextInt(DailyAppSchedule.ALL_DAYS);
            int nextDays = 1 + random.nextInt(DailyAppSchedule.ALL_DAYS);
            Set<String> oldApps = apps(), nextApps = apps(), permanent = apps();
            for (String name : packages) {
                if (random.nextBoolean()) oldApps.add(name);
                if (random.nextBoolean()) nextApps.add(name);
                if (random.nextBoolean()) permanent.add(name);
            }
            DailyAppSchedule previous = new DailyAppSchedule(random.nextBoolean(), oldStart, oldEnd,
                    oldApps, false, oldDays);
            DailyAppSchedule next = new DailyAppSchedule(random.nextBoolean(), nextStart, nextEnd,
                    nextApps, false, nextDays);
            boolean expected = true;
            for (String name : packages) {
                for (DayOfWeek day : DayOfWeek.values()) {
                    for (int minute = 0; minute < 1440; minute++) {
                        boolean oldBlocked = previous.enabled && oldApps.contains(name)
                                && covers(oldDays, oldStart, oldEnd, day, minute);
                        boolean nextBlocked = permanent.contains(name)
                                || (next.enabled && nextApps.contains(name)
                                && covers(nextDays, nextStart, nextEnd, day, minute));
                        if (oldBlocked && !nextBlocked) expected = false;
                    }
                }
            }
            check(next.preserves(previous, permanent) == expected,
                    "random independent per-app coverage oracle " + trial);
        }
    }
}
