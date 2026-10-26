package app.hisn.core;

/** One deterministic next step shared by setup status and its action button.
 * Each layer is optional: DNS alone, the selected-app layer alone, or both can be ready.
 * A ready guide describes the chosen experimental layers, not complete device protection. */
public final class SetupGuide {
    public enum Assets { DISABLED, LOADING, READY, ERROR }
    /** Opted-in image checks: off (or no apps to inspect), working, or reporting a failure. */
    public enum Visual { OFF, OK, FAILED }
    public enum Network { OFF, STARTING, RUNNING, DEGRADED, ERROR }
    public enum Probe { IDLE, CHECKING, PASSED, FAILED }
    public enum Step {
        CHOOSE_RULES, CONNECT_SERVICE, LOAD_POLICY, REPAIR_POLICY, REVIEW_APP_LAYER, REVIEW_VISUAL,
        ENABLE_DNS, STARTING_DNS, REVIEW_DNS, TEST_DNS, CHECKING_DNS,
        /** Both layers chosen and healthy. */
        READY,
        /** Only the selected-app layer was chosen; DNS filtering is off. */
        READY_APPS,
        /** Only DNS filtering was chosen; no app restrictions or checks. */
        READY_DNS
    }

    /** @param dnsWanted the owner left DNS filtering on (it counts as chosen even while it is off). */
    public static Step next(boolean appRules, boolean dnsWanted, GuardHealth health, Assets assets,
            Visual visual, Network network, Probe probe) {
        if (health == null || assets == null || visual == null || network == null || probe == null)
            throw new IllegalArgumentException("Missing setup state");
        // Unreadable settings hide which layers were chosen, so nothing can be ready.
        if (health == GuardHealth.SETTINGS_UNAVAILABLE) return Step.REVIEW_APP_LAYER;
        boolean dns = dnsWanted || network != Network.OFF;
        if (appRules) {
            switch (health) {
                case PERMISSION_UNKNOWN:
                case READY_NO_RULES:
                    return Step.REVIEW_APP_LAYER;
                case DISABLED:
                case PERMISSION_REQUIRED:
                case RECONNECT_REQUIRED:
                    return Step.CONNECT_SERVICE;
                default:
                    break;
            }
            // An unavailable opted-in policy is more actionable than the generic
            // LIMITED state it also produces. Custom-only protection needs no asset.
            if (assets == Assets.LOADING) return Step.LOAD_POLICY;
            if (assets == Assets.ERROR) return Step.REPAIR_POLICY;
            if (health == GuardHealth.LIMITED) return Step.REVIEW_APP_LAYER;
            if (visual == Visual.FAILED) return Step.REVIEW_VISUAL;
            if (!dns) return Step.READY_APPS;
        } else if (!dns) {
            return Step.CHOOSE_RULES;
        }
        switch (network) {
            case STARTING: return Step.STARTING_DNS;
            case DEGRADED: return Step.REVIEW_DNS;
            case OFF:
            case ERROR: return Step.ENABLE_DNS;
            default: break;
        }
        if (probe == Probe.CHECKING) return Step.CHECKING_DNS;
        if (probe != Probe.PASSED) return Step.TEST_DNS;
        return appRules ? Step.READY : Step.READY_DNS;
    }

    private SetupGuide() { }
}
