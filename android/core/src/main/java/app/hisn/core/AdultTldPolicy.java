package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The adult-TLD rail: a name under a sponsored adult TLD is blocked before any
 * list has heard of it.
 *
 * `blocklist/build.py` already uses these TLDs as a selection signal for the
 * bundled core tier. That is not enforcement: a domain registered after the
 * last build is on no list. `.xxx`, `.porn`, `.sex` and `.adult` are
 * adult-sponsored at the registry, so the final label itself is the signal.
 *
 * `.cam` is deliberately absent. It is a generic TLD with ordinary camera and
 * webcam uses; blocking it whole would take out sites that are not adult
 * content. The keyword layer still catches a `.cam` host whose own labels name
 * adult material.
 *
 * Kept in step with `extension/lib/policy.js`, `ios/prepare_rules.py` and
 * `macos/Hisn/AdultTldGuard.swift`. This is a policy constant, not a downloaded
 * artifact, so it works when the seed list cannot be loaded.
 */
public final class AdultTldPolicy {
    public static final Set<String> TLDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("adult", "porn", "sex", "xxx")));

    /** True when the host's final label is one of {@link #TLDS}. */
    public boolean matches(String hostname) {
        String host = HostTermPolicy.canonicalHost(hostname);
        if (host.isEmpty()) return false;
        int dot = host.lastIndexOf('.');
        String tld = dot < 0 ? host : host.substring(dot + 1);
        return TLDS.contains(tld);
    }

    /** The guard is a suffix rule; exposing it keeps the predicate obvious in tests. */
    static boolean isGuardedTld(String term) {
        return term != null && TLDS.contains(term.toLowerCase(Locale.ROOT));
    }
}
