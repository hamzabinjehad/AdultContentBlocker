package app.hisn.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * How old the list bundled into this build is.
 *
 * Android receives list changes only with a new signed APK; it never downloads
 * an unsigned list at runtime. A client can therefore be honest about a stale
 * starting point without adding a network trust path: after the warning age the
 * UI says how old the built-in list is and that a newer build is the update.
 * The age is informational, not a protection claim; the list still enforces.
 */
public final class SeedFreshness {
    /** Warn after this many whole days. */
    public static final int WARN_DAYS = 90;

    private SeedFreshness() { }

    /** Whole days since the build, clamped at zero; null when the timestamp cannot be read. */
    public static Long ageDays(String builtAt, long nowMillis) {
        Instant built = parse(builtAt);
        if (built == null) return null;
        long days = ChronoUnit.DAYS.between(built, Instant.ofEpochMilli(nowMillis));
        return days < 0 ? 0 : days;
    }

    /** True when the bundled list is old enough that the UI should say so. */
    public static boolean stale(String builtAt, long nowMillis) {
        Long days = ageDays(builtAt, nowMillis);
        return days != null && days >= WARN_DAYS;
    }

    /** The seed manifest uses an ISO offset timestamp; accept a plain date too. */
    static Instant parse(String builtAt) {
        if (builtAt == null || builtAt.trim().isEmpty()) return null;
        try {
            return OffsetDateTime.parse(builtAt.trim()).toInstant();
        } catch (RuntimeException notOffset) {
            try {
                return LocalDate.parse(builtAt.trim()).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (RuntimeException notDate) {
                return null;
            }
        }
    }
}
