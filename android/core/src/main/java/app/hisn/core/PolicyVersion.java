package app.hisn.core;

/**
 * A committed policy version. The stored value travels with the sealed settings it
 * identifies, so a reader can compare two snapshots without a clock or object identity.
 * Version zero is a legacy record that has not changed since identities were added.
 */
public final class PolicyVersion {
    public static final long LEGACY = 0;

    /**
     * The next version after a successful effective change. An identical save, a
     * checkpoint or a restore keeps the current version.
     */
    public static long next(long current, boolean changed) {
        if (current < 0) throw new IllegalArgumentException("Invalid policy version");
        if (!changed) return current;
        if (current == Long.MAX_VALUE) throw new IllegalStateException("Policy version exhausted");
        return current + 1;
    }

    private PolicyVersion() { }
}
