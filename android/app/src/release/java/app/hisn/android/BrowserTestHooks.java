package app.hisn.android;

import app.hisn.core.DnsPolicy;
import app.hisn.core.DomainPolicy;

/** Release builds have no temporary browser test override. */
final class BrowserTestHooks {
    DomainPolicy.Decision evaluate(String hostname, DnsPolicy.Rules fallback) {
        return fallback.evaluate(hostname);
    }
    void arm() { }
    void clear() { }
    String status() { return "BROWSER_CANARY_UNAVAILABLE"; }
}
