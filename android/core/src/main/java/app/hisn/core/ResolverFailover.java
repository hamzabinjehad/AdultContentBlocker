package app.hisn.core;

/**
 * Chooses among resolvers that are authenticated the same way, in a fixed order. The first is used
 * while it works. After it cannot be reached or authenticated, or times out, new connections use the next one, and the
 * first is tried again once its wait ends: 30 seconds, doubling on each further failure up to 5
 * minutes. One connection at a time probes a resolver whose wait has ended; a success clears its
 * failures. A network change clears everything. This only orders the given resolvers; it never adds one.
 */
public final class ResolverFailover {
    public static final long FIRST_WAIT = 30000, LONGEST_WAIT = 300000;
    private final long[] retryAt;
    private final int[] failures;
    private final boolean[] probing;

    public ResolverFailover(int count) {
        if (count < 1 || count > 8) throw new IllegalArgumentException("Invalid resolver count");
        retryAt = new long[count]; failures = new int[count]; probing = new boolean[count];
    }

    /**
     * The resolver for the next exchange. {@code current} is the resolver of a reusable connection,
     * or -1; it is kept unless an earlier resolver works or is due to be tried again. Returning a
     * resolver whose wait has ended claims its probe, so other callers keep their resolver meanwhile.
     */
    public synchronized int choose(long now, int current) {
        for (int i = 0; i < retryAt.length; i++) {
            if (failures[i] == 0) return i;
            if (now >= retryAt[i]) return claim(i, now);
            if (i == current) return i;
        }
        if (current >= 0 && current < retryAt.length) return current;
        int soonest = 0;
        for (int i = 1; i < retryAt.length; i++) if (retryAt[i] < retryAt[soonest]) soonest = i;
        return soonest;
    }

    /** The next resolver after a failure in one exchange, or -1. {@code tried} is a bit mask of
     * resolvers this exchange already used. A resolver still waiting is skipped, so an exchange makes
     * at most one attempt per resolver and only one when every resolver is failing. */
    public synchronized int next(int tried, long now) {
        for (int i = 0; i < retryAt.length; i++) {
            if ((tried & (1 << i)) != 0) continue;
            if (failures[i] == 0) return i;
            if (now >= retryAt[i]) return claim(i, now);
        }
        return -1;
    }

    /** Counts a failure to connect or authenticate, or a timeout. A failure from an attempt that began before this
     * resolver was already marked failing does not lengthen its wait again. */
    public synchronized void failed(int index, long now) {
        if (!probing[index] && failures[index] > 0 && now < retryAt[index]) return;
        failures[index] = Math.min(failures[index] + 1, 16);
        probing[index] = false;
        retryAt[index] = now + Math.min(LONGEST_WAIT, FIRST_WAIT << Math.min(failures[index] - 1, 8));
    }

    public synchronized void succeeded(int index) {
        failures[index] = 0; probing[index] = false; retryAt[index] = 0;
    }

    /** A new underlying network: every resolver gets a fresh chance, the first one first. */
    public synchronized void reset() {
        java.util.Arrays.fill(failures, 0); java.util.Arrays.fill(probing, false); java.util.Arrays.fill(retryAt, 0);
    }

    /** The resolver a new connection would use now, without claiming a probe. */
    public synchronized int preferred(long now) {
        for (int i = 0; i < retryAt.length; i++) if (failures[i] == 0 || now >= retryAt[i]) return i;
        return -1;
    }

    // A probe holds the resolver for one first wait; if its result never arrives, another may start.
    private int claim(int index, long now) {
        probing[index] = true;
        retryAt[index] = now + FIRST_WAIT;
        return index;
    }
}
