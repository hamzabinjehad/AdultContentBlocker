package app.hisn.core;

import java.util.concurrent.ConcurrentHashMap;

/**
 * One composed hostname decision, in the order Android DNS actually enforces it:
 * a personal block, then the adult-TLD rail, then the hostname term layer, then the
 * published domain list. There is no hand allowlist exception on Android and none is
 * introduced here. An unreadable published list is {@link Outcome#UNAVAILABLE}, never
 * "not listed".
 */
public final class HostDecision {
    public enum Outcome { BLOCKED, NOT_LISTED, INVALID, UNAVAILABLE }

    public enum Source { PERSONAL_BLOCK, ADULT_TLD, HOST_TERM, PUBLISHED_LIST, NONE }

    public static final class Result {
        /** The canonical ASCII hostname, or null for invalid input. */
        public final String host;
        public final Outcome outcome;
        public final Source source;
        /** The published list generation that answered, or the bundled seed version. */
        public final long generation;

        private Result(String host, Outcome outcome, Source source, long generation) {
            this.host = host;
            this.outcome = outcome;
            this.source = source;
            this.generation = generation;
        }

        public boolean blocked() { return outcome == Outcome.BLOCKED; }
        public boolean answered() {
            return outcome == Outcome.BLOCKED || outcome == Outcome.NOT_LISTED;
        }

        /** The verdict the DNS service needs; UNAVAILABLE is invalid, never allowed. */
        public DomainPolicy.Decision enforcement() {
            switch (outcome) {
                case BLOCKED: return DomainPolicy.Decision.BLOCK;
                case NOT_LISTED: return DomainPolicy.Decision.ALLOW;
                default: return DomainPolicy.Decision.INVALID;
            }
        }
    }

    /** An immutable set of rule layers. The caller owns generation changes and reloads. */
    public static final class Policy {
        private static final int MAX_DECISIONS = 8192;
        private final DomainPolicy personal;
        private final DomainPolicy published;
        private final AdultTldPolicy adultTlds;
        private final HostTermPolicy terms;
        private final boolean publishedAvailable;
        private final long generation;
        // Scoped to one immutable rule set. Rule edits or a new list get a new cache.
        private final ConcurrentHashMap<String, DomainPolicy.Decision> decisions = new ConcurrentHashMap<>();

        public Policy(DomainPolicy personal, DomainPolicy published, boolean publishedAvailable,
                long generation, HostTermPolicy terms) {
            if (personal == null) throw new IllegalArgumentException("Missing personal rules");
            if (generation < 0) throw new IllegalArgumentException("Invalid list generation");
            this.personal = personal;
            this.published = published;
            this.publishedAvailable = publishedAvailable && published != null;
            this.generation = generation;
            this.adultTlds = new AdultTldPolicy();
            this.terms = terms;
        }

        public Result evaluate(String input) {
            final String host;
            try { host = Hostname.normalize(input); }
            catch (IllegalArgumentException invalid) {
                return new Result(null, Outcome.INVALID, Source.NONE, generation);
            }
            Source source = source(host);
            Outcome outcome = source != Source.NONE ? Outcome.BLOCKED
                    : publishedAvailable ? Outcome.NOT_LISTED : Outcome.UNAVAILABLE;
            return new Result(host, outcome, source, generation);
        }

        /** The same ordered verdict for the packet path, without allocating an explanation. */
        public DomainPolicy.Decision enforce(String input) {
            if (input == null) return DomainPolicy.Decision.INVALID;
            DomainPolicy.Decision cached = decisions.get(input);
            if (cached != null) return cached;
            final String host;
            try { host = Hostname.normalize(input); }
            catch (IllegalArgumentException invalid) { return DomainPolicy.Decision.INVALID; }
            if (host != input) {
                cached = decisions.get(host);
                if (cached != null) return cached;
            }
            DomainPolicy.Decision verdict = source(host) != Source.NONE ? DomainPolicy.Decision.BLOCK
                    : publishedAvailable ? DomainPolicy.Decision.ALLOW : DomainPolicy.Decision.INVALID;
            if (decisions.size() >= MAX_DECISIONS) decisions.clear();
            decisions.put(host, verdict);
            return verdict;
        }

        private Source source(String host) {
            if (personal.evaluateCanonical(host) == DomainPolicy.Decision.BLOCK) return Source.PERSONAL_BLOCK;
            if (adultTlds.matchesCanonical(host)) return Source.ADULT_TLD;
            if (terms != null && terms.matchesCanonical(host)) return Source.HOST_TERM;
            if (publishedAvailable && published.evaluateCanonical(host) == DomainPolicy.Decision.BLOCK) {
                return Source.PUBLISHED_LIST;
            }
            return Source.NONE;
        }

        public long generation() { return generation; }
        public boolean publishedAvailable() { return publishedAvailable; }
    }

    private HostDecision() { }
}
