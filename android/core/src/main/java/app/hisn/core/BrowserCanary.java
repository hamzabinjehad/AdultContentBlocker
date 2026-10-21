package app.hisn.core;

import java.util.function.LongSupplier;

/**
 * A fixed-host, temporary, additive DNS-policy diagnostic. No settings, network
 * traffic or browsing history are stored. Callers must separately restrict the
 * ability to arm this diagnostic to their debug/test entry points.
 */
public final class BrowserCanary {
    public static final String HOST = "www.example.org";
    public static final long LEASE_MILLIS = 120000;

    public static final class Snapshot {
        public final boolean active;
        public final long remainingMillis;
        /** Policy evaluations, not distinct queries, browser requests or delivered replies. */
        public final long policyEvaluations;

        private Snapshot(boolean active, long remainingMillis, long policyEvaluations) {
            this.active = active;
            this.remainingMillis = remainingMillis;
            this.policyEvaluations = policyEvaluations;
        }
    }

    private final LongSupplier clock;
    private boolean active;
    private long armedAt;
    private long lastSampleAt;
    private long policyEvaluations;

    /** Supply elapsed/monotonic milliseconds, never an adjustable wall clock. */
    public BrowserCanary(LongSupplier monotonicMillis) {
        if (monotonicMillis == null) throw new IllegalArgumentException("Missing monotonic clock");
        clock = monotonicMillis;
    }

    /** Start a fresh, fixed two-minute lease and reset only its diagnostic count. */
    public synchronized void arm() {
        // If reading the clock fails, an earlier lease must not remain armed.
        active = false;
        policyEvaluations = 0;
        long now = clock.getAsLong();
        armedAt = now;
        lastSampleAt = now;
        active = true;
    }

    /** Remove only this temporary deny; retain its count until the next arm. */
    public synchronized void clear() {
        active = false;
    }

    /** Expiry/clear never modifies or bypasses the supplied owner's policy. */
    public DomainPolicy.Decision evaluate(String hostname, DnsPolicy.Rules fallback) {
        if (fallback == null) throw new IllegalArgumentException("Missing fallback policy");
        synchronized (this) {
            if (remainingMillis() > 0 && isExactHost(hostname)) {
                if (policyEvaluations != Long.MAX_VALUE) policyEvaluations++;
                return DomainPolicy.Decision.BLOCK;
            }
        }
        // Do not hold the diagnostic's lock while invoking arbitrary owner rules.
        return fallback.evaluate(hostname);
    }

    public synchronized Snapshot snapshot() {
        long remaining = remainingMillis();
        return new Snapshot(active, remaining, policyEvaluations);
    }

    /** Alias for callers presenting the diagnostic as status. */
    public Snapshot status() {
        return snapshot();
    }

    /** Called only with this object's monitor held. */
    private long remainingMillis() {
        if (!active) return 0;
        final long now;
        try {
            now = clock.getAsLong();
        } catch (RuntimeException unavailable) {
            active = false;
            return 0;
        }
        long elapsed = now - armedAt;
        // A rollback, wrap/overflow or the exact deadline expires, never extends,
        // this diagnostic. Check against the last sample, not just arm time.
        if (now < lastSampleAt || elapsed < 0 || elapsed >= LEASE_MILLIS) {
            active = false;
            return 0;
        }
        lastSampleAt = now;
        return LEASE_MILLIS - elapsed;
    }

    private static boolean isExactHost(String hostname) {
        if (hostname == null) return false;
        int length = hostname.length();
        if (length > 0 && hostname.charAt(length - 1) == '.') length--;
        return length == HOST.length() && hostname.regionMatches(true, 0, HOST, 0, length);
    }
}
