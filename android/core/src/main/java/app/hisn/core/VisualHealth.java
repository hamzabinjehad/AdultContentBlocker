package app.hisn.core;

/** Image-check status for the dashboard. Sampling reports a state about every two seconds, and
 * transient states (waiting, loading, checking, paused for input) must not hide the latest
 * failure. Only a later inspected sample, or the layer turning off or being unsupported, clears it. */
public final class VisualHealth {
    public enum State {
        OFF, UNSUPPORTED, WAITING, LOADING, CHECKING, SAMPLED, UNREADABLE, INPUT, ERROR;

        public boolean failure() { return this == UNREADABLE || this == ERROR; }
    }

    private State current = State.OFF;
    private State failure;

    public synchronized void report(State next) {
        if (next == null) throw new IllegalArgumentException("Missing visual state");
        current = next;
        if (next.failure()) failure = next;
        else if (next == State.SAMPLED || next == State.OFF || next == State.UNSUPPORTED) failure = null;
    }

    /** The latest state reported by sampling, for diagnostics. */
    public synchronized State current() { return current; }

    /** What the dashboard shows: the latest failure until a later success, otherwise the current state. */
    public synchronized State shown() { return failure != null ? failure : current; }
}
