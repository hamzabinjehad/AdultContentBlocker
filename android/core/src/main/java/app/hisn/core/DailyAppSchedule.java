package app.hisn.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** One explicitly chosen, every-day local-time app restriction.
 *
 * The model knows only the supplied local minute, not a system clock or time
 * zone. It is therefore not a clock-tampering defense or a background scheduler.
 * Start is inclusive and end is exclusive; an end before the start wraps over
 * midnight. Equal endpoints are rejected instead of meaning either no time or
 * an ambiguous full day. Permanent app blocking remains a separate layer.
 *
 * <p>Two modes share the window. The default blocks the apps in {@link #apps}.
 * {@link #allApps} inverts it: every app is blocked except the ones in
 * {@link #apps}, which are the apps to keep available. The system windows the
 * guard already excludes (settings, launchers, dialers, installers and Hisn
 * itself) stay reachable in both modes; this model does not know about them.
 */
public final class DailyAppSchedule {
    /** How long before the window the guard starts watching an allowlist app, so the start is
     *  enforced by the one-second checks instead of an all-day heartbeat. */
    public static final int ARM_MINUTES = 10;

    public final boolean enabled;
    public final int startMinute;
    public final int endMinute;
    /** Apps to block, or with {@link #allApps}, the apps to keep available. */
    public final Set<String> apps;
    /** Block every app except {@link #apps} inside the window. */
    public final boolean allApps;

    public DailyAppSchedule(boolean enabled, int startMinute, int endMinute, Set<String> apps) {
        this(enabled, startMinute, endMinute, apps, false);
    }

    public DailyAppSchedule(boolean enabled, int startMinute, int endMinute, Set<String> apps, boolean allApps) {
        requireMinute(startMinute);
        requireMinute(endMinute);
        if (startMinute == endMinute) throw new IllegalArgumentException("Schedule endpoints must differ");
        this.enabled = enabled;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.apps = packages(apps);
        this.allApps = allApps;
    }

    /** Disabled default retains an editable 22:00-07:00 window, without choosing any apps. */
    public static DailyAppSchedule disabled() {
        return new DailyAppSchedule(false, 1320, 420, Collections.emptySet(), false);
    }

    /** An allowlist with no apps kept available is configured: it blocks everything in the window. */
    public boolean isConfigured() { return enabled && (allApps || !apps.isEmpty()); }

    /** Whether the saved window is open now, in either mode. */
    public boolean activeAt(int localMinute) {
        requireMinute(localMinute);
        return isConfigured() && containsMinute(localMinute);
    }

    /** Whether the window blocks this app now. Never true for a null package. */
    public boolean blocks(String packageName, int localMinute) {
        requireMinute(localMinute);
        return targets(packageName) && containsMinute(localMinute);
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
     * left untouched.
     */
    public boolean selects(String packageName, int localMinute) {
        requireMinute(localMinute);
        if (!targets(packageName)) return false;
        return !allApps || containsMinute(localMinute) || startsWithin(localMinute, ARM_MINUTES);
    }

    /** Whether this schedule, plus the next permanent app set, preserves all old coverage.
     *
     * An old scheduled app may move to permanent blocking. Otherwise it must
     * remain blocked and every previously blocked local minute must still be
     * blocked. The fixed 1,440-minute comparison handles midnight without
     * interval special cases and is independent of mutable wall-clock state.
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
            return coversMinutes(previous);
        }
        boolean needsSchedule = false;
        for (String app : previous.apps) {
            if (permanent.contains(app)) continue;
            needsSchedule = true;
            if (!isConfigured()) return false;
            if (allApps ? apps.contains(app) : !apps.contains(app)) return false;
        }
        return !needsSchedule || coversMinutes(previous);
    }

    private boolean coversMinutes(DailyAppSchedule previous) {
        for (int minute = 0; minute < 1440; minute++) {
            if (previous.containsMinute(minute) && !containsMinute(minute)) return false;
        }
        return true;
    }

    /** True when the window begins within {@code withinMinutes}, not counting a window already open. */
    private boolean startsWithin(int minute, int withinMinutes) {
        int distance = (startMinute - minute + 1440) % 1440;
        return distance > 0 && distance <= withinMinutes;
    }

    private boolean containsMinute(int minute) {
        return startMinute < endMinute
                ? minute >= startMinute && minute < endMinute
                : minute >= startMinute || minute < endMinute;
    }

    private static void requireMinute(int minute) {
        if (minute < 0 || minute >= 1440) throw new IllegalArgumentException("Invalid local minute");
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
