package app.hisn.core;

/**
 * Session health for the DNS tunnel. A real failure degrades it; only a quiet period
 * with successful upstream answers clears that. Callers keep cumulative counters, so
 * earlier failures stay visible. Times are monotonic milliseconds.
 */
public final class DnsHealth {
    public static final long QUIET_MILLIS = 120_000;
    public static final int MIN_SUCCESSES = 10;
    private boolean degraded;
    private long lastFailure;
    private int successes;

    public synchronized void failure(long nowMillis) {
        degraded = true;
        lastFailure = nowMillis;
        successes = 0;
    }

    /** Returns true only for the success that ends a degraded period. */
    public synchronized boolean success(long nowMillis) {
        if (!degraded) return false;
        if (successes < MIN_SUCCESSES) successes++;
        if (successes < MIN_SUCCESSES || nowMillis - lastFailure < QUIET_MILLIS) return false;
        degraded = false;
        return true;
    }

    public synchronized boolean degraded() { return degraded; }
}
