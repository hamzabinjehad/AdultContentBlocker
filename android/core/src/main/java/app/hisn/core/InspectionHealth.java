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

    public synchronized void record(Outcome outcome) {
        if (outcome == null) throw new IllegalArgumentException("Missing inspection outcome");
        if (outcome == Outcome.COMPLETE) incomplete = false;
        else if (outcome == Outcome.INCOMPLETE) incomplete = true;
    }

    public synchronized boolean incomplete() { return incomplete; }
}
