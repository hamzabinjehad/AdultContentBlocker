package app.hisn.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One explicitly chosen daily foreground-time limit per app.
 *
 * <p>The rule is only minutes per local day. It does not read the clock, Android
 * usage records or a counter, so it cannot itself be used as proof that a limit
 * was enforced. The Android layer reads Android's own foreground-time record
 * and asks this class whether the saved limit has been reached. The record
 * resets at local midnight; changing the device clock, time zone, date or
 * clearing Android's usage data can change or reset it.
 *
 * <p>Five minutes is the smallest limit and 1,440 minutes (a full day) the
 * largest. A limit is separate from permanent blocking and from the daily
 * schedule: whichever layer blocks first wins.
 */
public final class AppTimeLimit {
    public static final int MAX_APPS = 200;
    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 1440;
    private static final char SEPARATOR = '=';

    private final Map<String, Integer> minutes;

    public AppTimeLimit(Map<String, Integer> minutes) {
        if (minutes == null) throw new IllegalArgumentException("Missing time limits");
        if (minutes.size() > MAX_APPS) throw new PolicyChangeError.Invalid(
                PolicyChangeError.TOO_MANY_APPS, "Too many app time limits");
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : minutes.entrySet()) {
            String name = entry.getKey();
            Integer limit = entry.getValue();
            requirePackage(name);
            requireMinutes(limit);
            copy.put(name, limit);
        }
        this.minutes = Collections.unmodifiableMap(copy);
    }

    /** No time limits. */
    public static AppTimeLimit none() { return new AppTimeLimit(Collections.emptyMap()); }

    /** Reads the saved form written by {@link #serialized()}. Missing values are empty. */
    public static AppTimeLimit parse(Set<String> values) {
        if (values == null) throw new IllegalArgumentException("Missing time limits");
        Map<String, Integer> parsed = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null) throw new IllegalArgumentException("Null time limit");
            int split = value.lastIndexOf(SEPARATOR);
            if (split <= 0 || split == value.length() - 1)
                throw new IllegalArgumentException("Invalid time limit");
            String name = value.substring(0, split);
            int limit;
            try {
                limit = Integer.parseInt(value.substring(split + 1));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid time limit", invalid);
            }
            if (parsed.containsKey(name)) throw new IllegalArgumentException("Duplicate time limit");
            parsed.put(name, limit);
        }
        return new AppTimeLimit(parsed);
    }

    /** The stable stored form: a set of {@code package=minutes} strings. */
    public Set<String> serialized() {
        Set<String> values = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : minutes.entrySet()) {
            values.add(entry.getKey() + SEPARATOR + entry.getValue());
        }
        return Collections.unmodifiableSet(values);
    }

    public boolean configured() { return !minutes.isEmpty(); }

    public int size() { return minutes.size(); }

    public Set<String> apps() { return Collections.unmodifiableSet(minutes.keySet()); }

    /** The chosen minutes for this app, or null when it has no limit. */
    public Integer minutesFor(String packageName) {
        return packageName == null ? null : minutes.get(packageName);
    }

    public boolean monitored(String packageName) {
        return minutesFor(packageName) != null;
    }

    /** Whether {@code usedMillis} of foreground time today has reached this app's limit. */
    public boolean exceeded(String packageName, long usedMillis) {
        Integer limit = minutesFor(packageName);
        if (limit == null) return false;
        return Math.max(0, usedMillis) >= limit * 60000L;
    }

    /** Time left today for this app; {@link Long#MAX_VALUE} when it has no limit. */
    public long remainingMillis(String packageName, long usedMillis) {
        Integer limit = minutesFor(packageName);
        if (limit == null) return Long.MAX_VALUE;
        return Math.max(0, limit * 60000L - Math.max(0, usedMillis));
    }

    /**
     * Whether every limit in {@code previous} is still present and no limit was
     * raised. Adding an app, or lowering a limit, tightens; removing an app or
     * raising a limit loosens.
     */
    public boolean tightens(AppTimeLimit previous) {
        if (previous == null) throw new IllegalArgumentException("Missing previous time limits");
        for (Map.Entry<String, Integer> entry : previous.minutes.entrySet()) {
            Integer next = minutes.get(entry.getKey());
            if (next == null || next > entry.getValue()) return false;
        }
        return true;
    }

    @Override public boolean equals(Object other) {
        return other instanceof AppTimeLimit && minutes.equals(((AppTimeLimit) other).minutes);
    }

    @Override public int hashCode() { return minutes.hashCode(); }

    @Override public String toString() { return "AppTimeLimit" + minutes; }

    static void requirePackage(String packageName) {
        if (packageName == null || packageName.length() > 255
                || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) {
            throw new IllegalArgumentException("Invalid package");
        }
    }

    private static void requireMinutes(Integer minutes) {
        if (minutes == null || minutes < MIN_MINUTES || minutes > MAX_MINUTES)
            throw new IllegalArgumentException("Invalid time limit");
    }
}
