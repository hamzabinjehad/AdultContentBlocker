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
 */
public final class DailyAppSchedule {
    public final boolean enabled;
    public final int startMinute;
    public final int endMinute;
    public final Set<String> apps;

    public DailyAppSchedule(boolean enabled, int startMinute, int endMinute, Set<String> apps) {
        requireMinute(startMinute);
        requireMinute(endMinute);
        if (startMinute == endMinute) throw new IllegalArgumentException("Schedule endpoints must differ");
        this.enabled = enabled;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.apps = packages(apps);
    }

    /** Disabled default retains an editable 22:00-07:00 window, without choosing any apps. */
    public static DailyAppSchedule disabled() {
        return new DailyAppSchedule(false, 1320, 420, Collections.emptySet());
    }

    public boolean isConfigured() { return enabled && !apps.isEmpty(); }

    public boolean blocks(String packageName, int localMinute) {
        requireMinute(localMinute);
        return isConfigured() && apps.contains(packageName) && containsMinute(localMinute);
    }

    /** Whether this schedule, plus the next permanent app set, preserves all old coverage.
     *
     * An old scheduled app may move to permanent blocking. Otherwise it must
     * remain selected and every previously blocked local minute must still be
     * blocked. The fixed 1,440-minute comparison handles midnight without
     * interval special cases and is independent of mutable wall-clock state.
     */
    public boolean preserves(DailyAppSchedule previous, Set<String> permanentlyBlocked) {
        if (previous == null) throw new IllegalArgumentException("Missing previous schedule");
        Set<String> permanent = packages(permanentlyBlocked);
        if (!previous.isConfigured()) return true;
        boolean needsSchedule = false;
        for (String app : previous.apps) {
            if (permanent.contains(app)) continue;
            if (!isConfigured() || !apps.contains(app)) return false;
            needsSchedule = true;
        }
        if (!needsSchedule) return true;
        for (int minute = 0; minute < 1440; minute++) {
            if (previous.containsMinute(minute) && !containsMinute(minute)) return false;
        }
        return true;
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
        if (input == null || input.size() > 200) throw new IllegalArgumentException("Invalid packages");
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
