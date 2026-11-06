package app.hisn.core;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** One explicitly chosen app restriction that repeats on chosen days of the week.
 *
 * The model knows only the supplied local minute and day of the week, not a
 * system clock or time zone. It is therefore not a clock-tampering defense or a
 * background scheduler. Start is inclusive and end is exclusive; an end before
 * the start wraps over midnight, and the window belongs to the day it starts on:
 * a 22:00-07:00 window that repeats on Monday runs Monday evening into Tuesday
 * morning. Equal endpoints are rejected instead of meaning either no time or an
 * ambiguous full day. Permanent app blocking remains a separate layer.
 *
 * <p>Two modes share the window. The default blocks the apps in {@link #apps}.
 * {@link #allApps} inverts it: every app is blocked except the ones in
 * {@link #apps}, which are the apps to keep available. The system windows the
 * guard already excludes (settings, launchers, dialers, installers and Hisn
 * itself) stay reachable in both modes; this model does not know about them.
 *
 * <p>{@link #days} is a Monday-first bit mask (bit 0 is Monday, bit 6 is
 * Sunday) so saved values stay small integers; {@link #bitOf} converts from
 * {@link DayOfWeek}. A mask of zero days is rejected, because a schedule that
 * never runs is not a rule, and a disabled schedule keeps the days it will use
 * once re-enabled.
 */
public final class DailyAppSchedule {
    /** How long before the window the guard starts watching an allowlist app, so the start is
     *  enforced by the one-second checks instead of an all-day heartbeat. */
    public static final int ARM_MINUTES = 10;
    /** Every day of the week; what an upgrade from every-day schedules keeps. */
    public static final int ALL_DAYS = 0x7F;

    public final boolean enabled;
    public final int startMinute;
    public final int endMinute;
    /** Apps to block, or with {@link #allApps}, the apps to keep available. */
    public final Set<String> apps;
    /** Block every app except {@link #apps} inside the window. */
    public final boolean allApps;
    /** Monday-first bit mask of the days the window starts on. */
    public final int days;

    public DailyAppSchedule(boolean enabled, int startMinute, int endMinute, Set<String> apps) {
        this(enabled, startMinute, endMinute, apps, false, ALL_DAYS);
    }

    public DailyAppSchedule(boolean enabled, int startMinute, int endMinute, Set<String> apps, boolean allApps) {
        this(enabled, startMinute, endMinute, apps, allApps, ALL_DAYS);
    }

    public DailyAppSchedule(boolean enabled, int startMinute, int endMinute, Set<String> apps,
            boolean allApps, int days) {
        requireMinute(startMinute);
        requireMinute(endMinute);
        if (startMinute == endMinute) throw new IllegalArgumentException("Schedule endpoints must differ");
        if (days < 1 || days > ALL_DAYS) throw new IllegalArgumentException("Invalid day mask");
        this.enabled = enabled;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.apps = packages(apps);
        this.allApps = allApps;
        this.days = days;
    }

    /** The saved bit for one day of the ISO week (Monday 1 to Sunday 7). */
    public static int bitOf(DayOfWeek day) { return 1 << (day.getValue() - 1); }

    /** Disabled default retains an editable 22:00-07:00 window, without choosing any apps. */
    public static DailyAppSchedule disabled() {
        return new DailyAppSchedule(false, 1320, 420, Collections.emptySet(), false, ALL_DAYS);
    }

    /** An allowlist with no apps kept available is configured: it blocks everything in the window. */
    public boolean isConfigured() { return enabled && (allApps || !apps.isEmpty()); }

    /** Whether the saved window is open now, in either mode. */
    public boolean activeAt(int localMinute, DayOfWeek day) {
        requireMinute(localMinute);
        requireDay(day);
        return isConfigured() && coversDay(day, localMinute);
    }

    /** Whether the window blocks this app now. Never true for a null package. */
    public boolean blocks(String packageName, int localMinute, DayOfWeek day) {
        requireMinute(localMinute);
        requireDay(day);
        return targets(packageName) && coversDay(day, localMinute);
    }

    /** Whether the schedule names this app at all, ignoring the time of day.
     *
     * A blocklist names its apps directly. An allowlist names every app it does
     * not exempt, so this is true for anything missing from {@link #apps}. */
    public boolean targets(String packageName) {
        if (packageName == null || !isConfigured()) return false;
        return allApps ? !apps.contains(packageName) : apps.contains(packageName);
    }

    /**
     * Whether the guard should watch this app at this minute. A blocklist keeps
     * watching all day, as before. An allowlist watches inside its window and for
     * {@link #ARM_MINUTES} before it, which bounds the one-second checks to the
     * hours that matter; a once-a-minute sweep covers an app opened earlier and
     * left untouched. Arming honours the day mask: the ten minutes before the
     * window belong to the day the window starts on, including the minutes before
     * midnight when the window starts at or just after midnight.
     */
    public boolean selects(String packageName, int localMinute, DayOfWeek day) {
        requireMinute(localMinute);
        requireDay(day);
        if (!targets(packageName)) return false;
        if (!allApps) return true;
        return coversDay(day, localMinute) || armsWithin(localMinute, day);
    }

    /** Whether this schedule, plus the next permanent app set, preserves all old coverage.
     *
     * An old scheduled app may move to permanent blocking. Otherwise it must
     * remain blocked and every previously blocked day-of-week and local minute
     * must still be blocked. The fixed 7x1,440 comparison handles midnight and
     * the day mask without interval special cases and is independent of mutable
     * wall-clock state.
     *
     * <p>Switching modes is checked as a coverage question, not a menu change: a
     * blocklist may become an allowlist only if every app it blocked is still
     * blocked (none of them moved to the keep list). An allowlist blocks every
     * app that is not named, which no blocklist can promise, so it may only
     * become another allowlist whose keep list did not grow.
     */
    public boolean preserves(DailyAppSchedule previous, Set<String> permanentlyBlocked) {
        if (previous == null) throw new IllegalArgumentException("Missing previous schedule");
        Set<String> permanent = packages(permanentlyBlocked);
        if (!previous.isConfigured()) return true;
        if (previous.allApps) {
            if (!isConfigured() || !allApps) return false;
            if (!previous.apps.containsAll(apps)) return false;
            return coversDays(previous);
        }
        boolean needsSchedule = false;
        for (String app : previous.apps) {
            if (permanent.contains(app)) continue;
            needsSchedule = true;
            if (!isConfigured()) return false;
            if (allApps ? apps.contains(app) : !apps.contains(app)) return false;
        }
        return !needsSchedule || coversDays(previous);
    }

    /** Every day the previous window covered, including the mornings after its
     * overnight portions, must stay covered; no day, minute or app may lose. */
    private boolean coversDays(DailyAppSchedule previous) {
        for (DayOfWeek day : DayOfWeek.values()) {
            for (int minute = 0; minute < 1440; minute++) {
                if (previous.coversDay(day, minute) && !coversDay(day, minute)) return false;
            }
        }
        return true;
    }

    /** Whether the window, with the days it starts on, covers this local minute of this day.
     *
     * A window that wraps over midnight covers the evening minutes of a selected
     * day and the early-morning minutes of the day after it. A window that fits
     * inside one day covers a selected day's own minutes only. */
    private boolean coversDay(DayOfWeek day, int minute) {
        if (startMinute < endMinute) {
            return (days & bitOf(day)) != 0 && minute >= startMinute && minute < endMinute;
        }
        return ((days & bitOf(day)) != 0 && minute >= startMinute)
                || ((days & bitOf(day.minus(1))) != 0 && minute < endMinute);
    }

    /** Whether the window starts within {@link #ARM_MINUTES} on the day it starts on. */
    private boolean armsWithin(int minute, DayOfWeek day) {
        int distance = (startMinute - minute + 1440) % 1440;
        if (distance == 0 || distance > ARM_MINUTES) return false;
        // A window that starts at or before the current minute opens tomorrow at
        // start: the arm belongs to the next day, for example 23:55 before 00:10.
        DayOfWeek anchor = startMinute > minute ? day : day.plus(1);
        return (days & bitOf(anchor)) != 0;
    }

    private static void requireMinute(int minute) {
        if (minute < 0 || minute >= 1440) throw new IllegalArgumentException("Invalid local minute");
    }

    private static void requireDay(DayOfWeek day) {
        if (day == null) throw new IllegalArgumentException("Invalid day of week");
    }

    private static Set<String> packages(Set<String> input) {
        if (input == null) throw new IllegalArgumentException("Invalid packages");
        if (input.size() > 200) throw new PolicyChangeError.Invalid(PolicyChangeError.TOO_MANY_APPS, "Too many apps");
        Set<String> result = new LinkedHashSet<>();
        for (String name : input) {
            if (name == null || name.length() > 255
                    || !name.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) {
                throw new IllegalArgumentException("Invalid package");
            }
            result.add(name);
        }
        return Collections.unmodifiableSet(result);
    }
}
