package app.hisn.android;

import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

import app.hisn.core.DomainPolicy;
import app.hisn.core.HostDecision;
import app.hisn.core.HostTermPolicy;

/**
 * The site decision for a browser's address bar, made with the same composed layers DNS
 * uses (personal block, adult TLD, hostname terms, published list).
 *
 * <p>It exists for the pages DNS never sees: a browser using its own encrypted DNS, an
 * answer cached before the tunnel started, or DNS filtering that is paused. The accessibility
 * thread never loads the 230,000-name list itself; until another layer or the background
 * load below has published it, the check answers "not known" and the page is left to the
 * other layers.
 */
final class AddressBarCheck {
    private static final AtomicBoolean loading = new AtomicBoolean();
    private static volatile Composed composed;

    private AddressBarCheck() { }

    /** True when the composed rules block this canonical host; false when allowed or not yet loaded. */
    static boolean blocks(Context context, PolicyStore.Snapshot rules, String host) {
        if (host == null || rules == null) return false;
        HostDecision.Policy policy = policy(context, rules);
        return policy != null && policy.enforce(host) == DomainPolicy.Decision.BLOCK;
    }

    private static HostDecision.Policy policy(Context context, PolicyStore.Snapshot rules) {
        HostListStore.Active active = HostListStore.peek();
        HostTermPolicy terms = active == null ? null
                : active.hostTerms != null ? active.hostTerms : HostData.loadedTerms();
        if (active == null || (terms == null && !HostData.termsFailed())) {
            load(context);
            return null;
        }
        Composed held = composed;
        if (held != null && held.active == active && held.rules == rules && held.terms == terms) return held.policy;
        HostDecision.Policy next = new HostDecision.Policy(rules.domains, active.policy, active.available(),
                active.generation, terms);
        composed = new Composed(active, rules, terms, next);
        return next;
    }

    private static void load(Context context) {
        if (!loading.compareAndSet(false, true)) return;
        Context application = context.getApplicationContext() == null ? context : context.getApplicationContext();
        Thread worker = new Thread(() -> {
            try { HostData.policy(application); }
            catch (RuntimeException unavailable) { /* Retried on the next address-bar check. */ }
            finally { loading.set(false); }
        }, "hisn-address-bar-rules");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private static final class Composed {
        final HostListStore.Active active;
        final PolicyStore.Snapshot rules;
        final HostTermPolicy terms;
        final HostDecision.Policy policy;

        Composed(HostListStore.Active active, PolicyStore.Snapshot rules, HostTermPolicy terms,
                HostDecision.Policy policy) {
            this.active = active;
            this.rules = rules;
            this.terms = terms;
            this.policy = policy;
        }
    }
}
