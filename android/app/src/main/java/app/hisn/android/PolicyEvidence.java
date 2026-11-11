package app.hisn.android;

import app.hisn.core.PolicyIdentity;

/**
 * The policy inputs each enforcing layer actually depends on. The saved rules version is
 * common to all; the extra component keeps a list, vocabulary or model change from
 * certifying a check made against the previous one.
 */
final class PolicyEvidence {
    static long dns(PolicyStore store, long listGeneration) {
        long component = PolicyIdentity.combine(store.safeSearchEnabled() ? 1L : 0L, listGeneration);
        component = PolicyIdentity.combine(component,
                PolicyIdentity.component(BuildConfig.DOMAIN_SEED_SHA256));
        return PolicyIdentity.of(store.policyRevision(), component);
    }

    static long text(PolicyStore store) {
        return text(store.policyRevision());
    }

    /** The text identity for a captured snapshot's own version, not a later store version. */
    static long text(long revision) {
        return PolicyIdentity.of(revision, PolicyIdentity.component(BuildConfig.TERMS_SEED_SHA256));
    }

    static long visual(PolicyStore store) {
        return PolicyIdentity.of(store.policyRevision(),
                PolicyIdentity.component(BuildConfig.VISUAL_ASSET_REVISION));
    }

    private PolicyEvidence() { }
}
