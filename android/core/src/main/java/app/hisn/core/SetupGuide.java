package app.hisn.core;

/** One deterministic next step shared by setup status and its action button.
 * A ready guide describes the chosen experimental layers, not complete device protection. */
public final class SetupGuide {
    public enum Assets { DISABLED, LOADING, READY, ERROR }
    public enum Network { OFF, STARTING, RUNNING, DEGRADED, ERROR }
    public enum Probe { IDLE, CHECKING, PASSED, FAILED }
    public enum Step {
        CHOOSE_RULES, CONNECT_SERVICE, LOAD_POLICY, REPAIR_POLICY, REVIEW_APP_LAYER,
        ENABLE_DNS, STARTING_DNS, REVIEW_DNS, TEST_DNS, CHECKING_DNS, READY
    }

    public static Step next(boolean appRules, GuardHealth health, Assets assets,
            Network network, Probe probe) {
        if (health == null || assets == null || network == null || probe == null)
            throw new IllegalArgumentException("Missing setup state");
        if (!appRules) return Step.CHOOSE_RULES;
        switch (health) {
            case SETTINGS_UNAVAILABLE:
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
        switch (network) {
            case STARTING: return Step.STARTING_DNS;
            case DEGRADED: return Step.REVIEW_DNS;
            case OFF:
            case ERROR: return Step.ENABLE_DNS;
            default: break;
        }
        if (probe == Probe.CHECKING) return Step.CHECKING_DNS;
        if (probe != Probe.PASSED) return Step.TEST_DNS;
        return Step.READY;
    }

    private SetupGuide() { }
}
