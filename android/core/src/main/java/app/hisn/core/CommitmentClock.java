package app.hisn.core;

/** A commitment clock that does not trust the editable wall clock. */
public final class CommitmentClock {
    public final long remainingAtCheckpoint;
    public final long elapsedAtCheckpoint;
    public final int bootAtCheckpoint;

    public CommitmentClock(long remaining, long elapsed, int boot) {
        if (remaining < 0 || remaining > 90L * 86400000 || elapsed < 0 || boot < 0) {
            throw new IllegalArgumentException("Invalid commitment clock");
        }
        remainingAtCheckpoint = remaining;
        elapsedAtCheckpoint = elapsed;
        bootAtCheckpoint = boot;
    }

    public long remaining(long elapsed, int boot) {
        if (elapsed < 0 || boot < 0) throw new IllegalArgumentException("Invalid clock sample");
        // After a restart, elapsed time since boot is real running time; only power-off is not counted.
        if (boot != bootAtCheckpoint) return Math.max(0, remainingAtCheckpoint - elapsed);
        if (elapsed < elapsedAtCheckpoint) return remainingAtCheckpoint;
        return Math.max(0, remainingAtCheckpoint - (elapsed - elapsedAtCheckpoint));
    }

    public CommitmentClock checkpoint(long elapsed, int boot) {
        return new CommitmentClock(remaining(elapsed, boot), elapsed, boot);
    }
}
