package app.hisn.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Domain-only policy; this does not capture or block network traffic. */
public final class DomainPolicy {
    public enum Decision { BLOCK, ALLOW, INVALID }
    private static final int MAX_RULES = 250000;
    private final Set<String> blocked;
    private final Set<String> allowed;
    /** Repeated DNS questions skip IDNA/validation; bounded so a hostile stream cannot grow it. */
    private final ConcurrentHashMap<String, Decision> decisions = new ConcurrentHashMap<>();
    private static final int DECISION_CACHE_LIMIT = 8192;

    public Set<String> blockedRules() { return blocked; }

    public DomainPolicy(Iterable<String> blocked, Iterable<String> allowed) {
        this.blocked = compile(blocked);
        this.allowed = compile(allowed);
    }

    /**
     * A policy over names that are already {@link Hostname#normalize}d. The caller must
     * hand over a set it no longer mutates; the seed loader uses this to keep one set
     * instead of copying a 236k-entry list into a second set.
     */
    static DomainPolicy fromCanonical(Set<String> canonicalBlocked) {
        if (canonicalBlocked == null || canonicalBlocked.size() > MAX_RULES)
            throw new IllegalArgumentException("Missing or oversized rules");
        // A CompactHostSet is already immutable; wrapping it would only add a call per lookup.
        Set<String> blocked = canonicalBlocked instanceof CompactHostSet ? canonicalBlocked
                : Collections.unmodifiableSet(canonicalBlocked);
        return new DomainPolicy(blocked,
                Collections.emptySet(), true);
    }

    private DomainPolicy(Set<String> blocked, Set<String> allowed, boolean precompiled) {
        this.blocked = blocked;
        this.allowed = allowed;
    }

    /** Invalid input is distinct from a valid name that is allowed. */
    public Decision evaluate(String hostname) {
        if (hostname == null) return Decision.INVALID;
        Decision cached = decisions.get(hostname);
        if (cached != null) return cached;
        final String name;
        try {
            name = normalize(hostname);
        } catch (IllegalArgumentException invalid) {
            remember(hostname, Decision.INVALID);
            return Decision.INVALID;
        }
        Decision result = !containsParent(allowed, name) && containsParent(blocked, name)
                ? Decision.BLOCK : Decision.ALLOW;
        remember(hostname, result);
        return result;
    }

    private void remember(String hostname, Decision decision) {
        if (decisions.size() >= DECISION_CACHE_LIMIT) decisions.clear();
        decisions.put(hostname, decision);
    }

    int cacheSize() { return decisions.size(); }

    /** Convenience for callers that already handle invalid names separately. */
    public boolean blocks(String hostname) {
        return evaluate(hostname) == Decision.BLOCK;
    }

    private static Set<String> compile(Iterable<String> rules) {
        if (rules == null) throw new IllegalArgumentException("Missing rules");
        Set<String> result = new HashSet<>();
        int count = 0;
        for (String rule : rules) {
            if (++count > MAX_RULES) throw new IllegalArgumentException("Too many rules");
            result.add(normalize(rule));
        }
        return Collections.unmodifiableSet(result);
    }

    private static boolean containsParent(Set<String> rules, String name) {
        String current = name;
        while (true) {
            if (rules.contains(current)) return true;
            int dot = current.indexOf('.');
            if (dot < 0) return false;
            current = current.substring(dot + 1);
        }
    }

    private static String normalize(String value) {
        return Hostname.normalize(value);
    }
}
