package app.hisn.core;

/** Why a saved-rule change was refused, so the app names the real cause instead of always
 * blaming the commitment. The exceptions keep their old types for existing callers. */
public enum PolicyChangeError {
    TOO_MANY_APPS, INVALID, COMMITMENT, NEEDS_RULES, SAVE_FAILED, UNAVAILABLE;

    /** Input the rules cannot hold. Still an IllegalArgumentException. */
    public static final class Invalid extends IllegalArgumentException {
        public final PolicyChangeError reason;
        public Invalid(PolicyChangeError reason, String message) { super(message); this.reason = reason; }
    }

    /** A well-formed change refused by state: a commitment, missing rules or storage. Still an IllegalStateException. */
    public static final class Refused extends IllegalStateException {
        public final PolicyChangeError reason;
        public Refused(PolicyChangeError reason, String message) { super(message); this.reason = reason; }
    }

    public static PolicyChangeError of(RuntimeException failure) {
        if (failure instanceof Invalid) return ((Invalid) failure).reason;
        if (failure instanceof Refused) return ((Refused) failure).reason;
        if (failure instanceof IllegalArgumentException) return INVALID;
        // Unreadable settings, a missing boot counter or anything unexpected: never "locked".
        return UNAVAILABLE;
    }
}
