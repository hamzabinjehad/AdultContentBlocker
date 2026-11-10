package app.hisn.core;

/**
 * What one completed check actually proved, and what it was allowed to act on.
 *
 * <p>A receipt carries the layer identity, the service session, the relevant network or
 * window generation, the kind of check, its result and the monotonic time it completed.
 * It contains no host, query, screen text or model input. A later policy, a restarted
 * service or a different window makes the receipt unmatchable; it can then neither certify
 * the new state nor act on it.</p>
 */
public final class PolicyReceipt {
    public enum Kind {
        /** A DNS route check proved the tunnel path for the network it ran on. */
        ROUTE_CHECK,
        /** The app rules reached a decision (blocked, scheduled, limited or nothing to inspect). */
        APP_DECISION,
        /** Visible text was actually read by a text pass. */
        TEXT_SCAN,
        /** A captured frame was classified. */
        VISUAL_SAMPLE
    }

    public enum Result { PASSED, FAILED }

    public final long identity;
    public final int session;
    public final long generation;
    public final Kind kind;
    public final Result result;
    /** Monotonic completion time in milliseconds. */
    public final long completedAt;

    public PolicyReceipt(long identity, int session, long generation, Kind kind, Result result,
            long completedAt) {
        if (identity < 0) throw new IllegalArgumentException("Invalid policy identity");
        if (session < 0) throw new IllegalArgumentException("Invalid service session");
        if (generation < 0) throw new IllegalArgumentException("Invalid generation");
        if (kind == null || result == null) throw new IllegalArgumentException("Missing receipt details");
        if (completedAt < 0) throw new IllegalArgumentException("Invalid completion time");
        this.identity = identity;
        this.session = session;
        this.generation = generation;
        this.kind = kind;
        this.result = result;
        this.completedAt = completedAt;
    }

    /** True when this receipt belongs to the named policy, service session and generation. */
    public boolean matches(long identity, int session, long generation) {
        return this.identity == identity && this.session == session && this.generation == generation;
    }

    /**
     * True when this receipt may be shown as current evidence: it passed, still belongs to
     * the named context, and is neither in the future nor older than the layer's limit.
     */
    public boolean usable(long identity, int session, long generation, long now, long maxAgeMillis) {
        if (maxAgeMillis <= 0) throw new IllegalArgumentException("Invalid evidence age");
        if (result != Result.PASSED || !matches(identity, session, generation)) return false;
        if (completedAt < 0 || now < completedAt) return false;
        return now - completedAt <= maxAgeMillis;
    }

    public boolean failed() { return result == Result.FAILED; }
}
