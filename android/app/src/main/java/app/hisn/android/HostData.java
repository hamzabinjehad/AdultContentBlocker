package app.hisn.android;

import android.content.Context;

import app.hisn.core.HostDecision;
import app.hisn.core.HostTermPolicy;

/** Builds the read-only checker's evaluator from the same store and bundled terms DNS uses. */
final class HostData {
    private static volatile HostTermPolicy terms;
    private static volatile boolean termsFailed;

    static HostDecision.Policy policy(Context context) {
        HostListStore.Active list = HostListStore.active(context);
        HostTermPolicy hostTerms = terms(context);
        return new HostDecision.Policy(PolicyStore.get(context).snapshot().domains,
                list.policy, list.available(), list.generation, hostTerms);
    }

    static boolean termsFailed() { return termsFailed; }

    private static HostTermPolicy terms(Context context) {
        HostTermPolicy held = terms;
        if (held != null) return held;
        synchronized (HostData.class) {
            if (terms != null) return terms;
            try {
                terms = AdultPolicyAssets.loadHostPolicy(context);
                termsFailed = false;
            } catch (Exception unavailable) {
                termsFailed = true;
            }
            return terms;
        }
    }

    private HostData() { }
}
