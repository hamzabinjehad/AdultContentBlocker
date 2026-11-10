package app.hisn.core;

/** The dashboard's "last screen scan incomplete" state. An incomplete or failed inspection sets
 * it; only a later complete inspection clears it, so a failure is neither hidden nor kept forever. */
public final class InspectionHealth {
    public enum Outcome {
        /** The selected window and the rules were read and a decision made: the app is blocked or
         * scheduled, it has nothing to check, or all of its visible text was checked. */
        COMPLETE,
        /** Text was cut short, the opted-in adult-text policy was missing, the window never
         * appeared, settings were unreadable, the check failed or its restriction screen did not open. */
        INCOMPLETE,
        /** No decision yet, for example while a retry waits for the window to settle. */
        PENDING
    }

    private boolean incomplete;
    private long completedAt = -1;
    private PolicyReceipt receipt;

    public synchronized void record(Outcome outcome) {
        record(outcome, -1);
    }

    /**
     * Record a scan result and, when the caller has a monotonic clock, when the last
     * complete scan finished. A later incomplete scan keeps that receipt; it is still
     * true that the layer completed one, while {@link #incomplete()} carries the failure.
     */
    public synchronized void record(Outcome outcome, long nowMillis) {
        if (outcome == null) throw new IllegalArgumentException("Missing inspection outcome");
        if (outcome == Outcome.COMPLETE) {
            incomplete = false;
            if (nowMillis >= 0) completedAt = nowMillis;
        } else if (outcome == Outcome.INCOMPLETE) {
            incomplete = true;
        }
    }

    /**
     * Record a completed inspection under the policy, session and window it actually checked.
     * A failed outcome keeps the last passed receipt but is visible through {@link #incomplete()}.
     */
    public synchronized void record(Outcome outcome, PolicyReceipt receipt) {
        if (outcome == null) throw new IllegalArgumentException("Missing inspection outcome");
        if (outcome == Outcome.COMPLETE) {
            incomplete = false;
            if (receipt != null) this.receipt = receipt;
        } else if (outcome == Outcome.INCOMPLETE) {
            incomplete = true;
        }
    }

    public synchronized boolean incomplete() { return incomplete; }

    /** The last completed inspection's receipt, or null when none has completed in this service. */
    public synchronized PolicyReceipt receipt() { return receipt; }

    /** When the last complete inspection finished, or -1 when none has in this service. */
    public synchronized long completedAt() {
        return receipt != null ? receipt.completedAt : completedAt;
    }
}
