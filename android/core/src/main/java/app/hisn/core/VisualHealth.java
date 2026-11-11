package app.hisn.core;

/** Image-check status for the dashboard. Sampling reports a state on every poll (about once a
 * second while the screen is on, more often while content moves), and transient states (waiting,
 * loading, checking, paused for input) must not hide the latest failure. Only a later inspected
 * sample, or the layer turning off or being unsupported, clears it. */
public final class VisualHealth {
    public enum State {
        OFF, UNSUPPORTED, WAITING, LOADING, CHECKING, SAMPLED, UNREADABLE, PROTECTED, INPUT, ERROR;

        public boolean failure() { return this == UNREADABLE || this == PROTECTED || this == ERROR; }
    }

    private State current = State.OFF;
    private State failure;
    private long sampledAt = -1;
    private PolicyReceipt receipt;

    public synchronized void report(State next) {
        report(next, -1);
    }

    /** Report a state; when a sample was inspected, pass the monotonic time it finished. */
    public synchronized void report(State next, long nowMillis) {
        report(next, nowMillis, null);
    }

    /** Report a state and, for an inspected sample, the policy/session/window receipt. */
    public synchronized void report(State next, long nowMillis, PolicyReceipt receipt) {
        if (next == null) throw new IllegalArgumentException("Missing visual state");
        current = next;
        if (next.failure()) failure = next;
        else if (next == State.SAMPLED || next == State.OFF || next == State.UNSUPPORTED) {
            failure = null;
            if (next == State.SAMPLED) {
                if (nowMillis >= 0) sampledAt = nowMillis;
                if (receipt != null) this.receipt = receipt;
            }
        }
    }

    /** The latest state reported by sampling, for diagnostics. */
    public synchronized State current() { return current; }

    /** What the dashboard shows: the latest failure until a later success, otherwise the current state. */
    public synchronized State shown() { return failure != null ? failure : current; }

    /** When the last inspected sample finished, or -1 when none has in this service. */
    public synchronized long sampledAt() {
        return receipt != null ? receipt.completedAt : sampledAt;
    }

    /** The last inspected sample's receipt, or null when none has completed in this service. */
    public synchronized PolicyReceipt receipt() { return receipt; }
}
