package app.hisn.android;

import app.hisn.core.BrowserCanary;
import app.hisn.core.DnsPolicy;
import app.hisn.core.DomainPolicy;

/** Debug-only additive test lease. Never changes PolicyStore or browser settings. */
final class BrowserTestHooks {
    private final BrowserCanary canary = new BrowserCanary(android.os.SystemClock::elapsedRealtime);

    DomainPolicy.Decision evaluate(String hostname, DnsPolicy.Rules fallback) {
        return canary.evaluate(hostname, fallback);
    }

    void arm() { canary.arm(); }
    void clear() { canary.clear(); }

    String status() {
        BrowserCanary.Snapshot state = canary.status();
        return "BROWSER_CANARY_HOST " + BrowserCanary.HOST
                + " BROWSER_CANARY_ACTIVE " + state.active
                + " BROWSER_CANARY_REMAINING_MS " + state.remainingMillis
                + " BROWSER_CANARY_EVALUATIONS " + state.policyEvaluations;
    }
}
