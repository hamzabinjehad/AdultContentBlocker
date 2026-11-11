package app.hisn.core;

import java.util.List;

/**
 * One honest reading of an opted-in protection layer.
 *
 * <p>“Configured”, “running” and “verified” are separate facts. A service can be bound with
 * no completed check, a finished check can be older than the network it was made on, and a
 * saved policy revision can differ from the one the enforcing service applied. The dashboard
 * must not turn any of those into “working”. This class only decides; it never reads Android
 * state, holds screen text or claims that one layer covers another.</p>
 */
public final class ProtectionReadiness {
    /** A route check older than this is reported as not checked unless the caller sets its own. */
    public static final long DEFAULT_MAX_EVIDENCE_AGE_MILLIS = 15 * 60_000L;

    public enum Layer { DNS, APPS, VISUAL }

    public enum State {
        /** The layer was not chosen; it is not counted as coverage. */
        OFF,
        /** Chosen and running, but no fresh evidence that it did its job. */
        NOT_CHECKED,
        STARTING,
        WORKING,
        /** A real failure is visible; a later success clears it. */
        DEGRADED,
        /** A permission, a binding or an applied revision has to be repaired. */
        NEEDS_ACTION,
        /** Chosen, but Android on this device cannot provide the layer. */
        UNSUPPORTED
    }

    public enum Reason {
        NONE, NOT_CHOSEN, NOT_SUPPORTED, PERMISSION, NOT_RUNNING, NO_EVIDENCE, STALE_EVIDENCE,
        REVISION, FAILURE
    }

    /** The single repair the dashboard should offer for a status. */
    public enum Action { NONE, CHOOSE, GRANT_PERMISSION, START_SERVICE, RUN_CHECK, RETRY }

    public enum Summary { OFF, NOT_CHECKED, STARTING, PARTIAL, WORKING, ACTION_NEEDED }

    /** Facts the caller observed. Times are monotonic milliseconds; -1 means unknown or absent. */
    public static final class Facts {
        public final boolean chosen;
        public final boolean supported;
        /** TRUE granted, FALSE refused, null not read yet. */
        public final Boolean permission;
        public final boolean running;
        public final boolean starting;
        public final boolean failed;
        public final long evidenceAt;
        /** The layer identity the saved settings describe. */
        public final long savedIdentity;
        /** The layer identity the evidence was produced under; -1 when no receipt exists. */
        public final long appliedIdentity;

        private Facts(boolean chosen, boolean supported, Boolean permission, boolean running,
                boolean starting, boolean failed, long evidenceAt, long savedIdentity,
                long appliedIdentity) {
            this.chosen = chosen;
            this.supported = supported;
            this.permission = permission;
            this.running = running;
            this.starting = starting;
            this.failed = failed;
            this.evidenceAt = evidenceAt;
            this.savedIdentity = savedIdentity;
            this.appliedIdentity = appliedIdentity;
        }

        /** The owner did not choose this layer. */
        public static Facts off() {
            return new Facts(false, true, null, false, false, false, -1, -1, -1);
        }

        /** The owner chose the layer, but this Android version cannot provide it. */
        public static Facts unsupported() {
            return new Facts(true, false, null, false, false, false, -1, -1, -1);
        }

        /** The owner chose the layer and these are the live facts about it. */
        public static Facts expected(Boolean permission, boolean running, boolean starting,
                boolean failed, long evidenceAt, long savedIdentity, long appliedIdentity) {
            return new Facts(true, true, permission, running, starting, failed, evidenceAt,
                    savedIdentity, appliedIdentity);
        }
    }

    public static final class Status {
        public final Layer layer;
        public final State state;
        public final Reason reason;
        /** How old the accepted evidence is, or -1 when there is none. */
        public final long evidenceAgeMillis;
        public final boolean identityMismatch;

        private Status(Layer layer, State state, Reason reason, long evidenceAgeMillis,
                boolean identityMismatch) {
            this.layer = layer;
            this.state = state;
            this.reason = reason;
            this.evidenceAgeMillis = evidenceAgeMillis;
            this.identityMismatch = identityMismatch;
        }

        /** True for a state the dashboard should surface as needing the owner's attention. */
        public boolean attention() {
            return state == State.DEGRADED || state == State.NEEDS_ACTION;
        }

        public boolean working() { return state == State.WORKING; }

        public Action action() {
            switch (reason) {
                case NOT_CHOSEN: return Action.CHOOSE;
                case PERMISSION: return Action.GRANT_PERMISSION;
                case NOT_RUNNING: return Action.START_SERVICE;
                case NO_EVIDENCE:
                case STALE_EVIDENCE: return Action.RUN_CHECK;
                case REVISION:
                case FAILURE: return Action.RETRY;
                default: return Action.NONE;
            }
        }
    }

    /** Decide one layer. A null fact is refused rather than guessed at. */
    public static Status evaluate(Layer layer, Facts facts, long now, long maxEvidenceAge) {
        if (layer == null || facts == null) throw new IllegalArgumentException("Missing readiness facts");
        if (maxEvidenceAge <= 0) throw new IllegalArgumentException("Invalid evidence age");
        long age = facts.evidenceAt < 0 || now < facts.evidenceAt
                ? -1 : now - facts.evidenceAt;
        boolean mismatch = facts.savedIdentity >= 0 && facts.appliedIdentity >= 0
                && facts.savedIdentity != facts.appliedIdentity;
        if (!facts.chosen) return status(layer, State.OFF, Reason.NOT_CHOSEN, -1, false);
        if (!facts.supported) return status(layer, State.UNSUPPORTED, Reason.NOT_SUPPORTED, -1, false);
        if (Boolean.FALSE.equals(facts.permission)) {
            return status(layer, State.NEEDS_ACTION, Reason.PERMISSION, age, mismatch);
        }
        if (facts.starting) return status(layer, State.STARTING, Reason.NONE, age, mismatch);
        if (!facts.running) {
            return status(layer, State.NEEDS_ACTION, Reason.NOT_RUNNING, age, mismatch);
        }
        if (facts.failed) return status(layer, State.DEGRADED, Reason.FAILURE, age, mismatch);
        if (mismatch) return status(layer, State.NEEDS_ACTION, Reason.REVISION, age, true);
        if (facts.evidenceAt < 0 || now < facts.evidenceAt) {
            return status(layer, State.NOT_CHECKED, Reason.NO_EVIDENCE, -1, false);
        }
        if (age > maxEvidenceAge) {
            return status(layer, State.NOT_CHECKED, Reason.STALE_EVIDENCE, age, false);
        }
        return status(layer, State.WORKING, Reason.NONE, age, false);
    }

    /**
     * The shortest honest sentence about a set of layers. A layer that was not chosen is
     * ignored; an unsupported layer is a coverage gap, not a failure.
     */
    public static Summary summarize(List<Status> statuses) {
        if (statuses == null) throw new IllegalArgumentException("Missing readiness statuses");
        int chosen = 0, working = 0, starting = 0;
        boolean action = false;
        for (Status status : statuses) {
            if (status == null) throw new IllegalArgumentException("Missing readiness status");
            if (status.state == State.OFF || status.state == State.UNSUPPORTED) continue;
            chosen++;
            if (status.state == State.WORKING) working++;
            if (status.state == State.STARTING) starting++;
            if (status.attention()) action = true;
        }
        if (chosen == 0) return Summary.OFF;
        if (action) return Summary.ACTION_NEEDED;
        if (working == chosen) return Summary.WORKING;
        if (starting == chosen) return Summary.STARTING;
        if (working > 0 || starting > 0) return Summary.PARTIAL;
        return Summary.NOT_CHECKED;
    }

    private static Status status(Layer layer, State state, Reason reason, long age,
            boolean identityMismatch) {
        return new Status(layer, state, reason, age, identityMismatch);
    }

    private ProtectionReadiness() { }
}
