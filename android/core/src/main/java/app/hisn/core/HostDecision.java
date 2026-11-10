package app.hisn.core;

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
        private final DomainPolicy personal;
        private final DomainPolicy published;
        private final AdultTldPolicy adultTlds;
        private final HostTermPolicy terms;
        private final boolean publishedAvailable;
        private final long generation;

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
            DomainPolicy.Decision own = personal.evaluate(host);
            if (own == DomainPolicy.Decision.BLOCK) {
                return new Result(host, Outcome.BLOCKED, Source.PERSONAL_BLOCK, generation);
            }
            if (own == DomainPolicy.Decision.INVALID) {
                return new Result(host, Outcome.INVALID, Source.NONE, generation);
            }
            if (adultTlds.matches(host)) {
                return new Result(host, Outcome.BLOCKED, Source.ADULT_TLD, generation);
            }
            if (terms != null && terms.matches(host)) {
                return new Result(host, Outcome.BLOCKED, Source.HOST_TERM, generation);
            }
            if (!publishedAvailable) {
                return new Result(host, Outcome.UNAVAILABLE, Source.NONE, generation);
            }
            if (published.evaluate(host) == DomainPolicy.Decision.BLOCK) {
                return new Result(host, Outcome.BLOCKED, Source.PUBLISHED_LIST, generation);
            }
            return new Result(host, Outcome.NOT_LISTED, Source.NONE, generation);
        }

        public long generation() { return generation; }
        public boolean publishedAvailable() { return publishedAvailable; }
    }

    private HostDecision() { }
}
