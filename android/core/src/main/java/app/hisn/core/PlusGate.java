package app.hisn.core;

/**
 * What Unrot Plus sells, and what it never does. One table, so the line between free and paid is
 * decided in one place and every screen asks the same question.
 *
 * Never sold: blocking adult content (every pane), Breathe and Help now, the glass, the lock,
 * quiet hours, and the clean wood with its earned trees. A person fighting adult content must
 * never meet a paywall between them and the help. Plus is for everything around it.
 */
public final class PlusGate {
    /** Time and feed rules a free account can keep (the adult filter is not a rule and is not counted). */
    public static final int FREE_RULES = 3;
    /** Woods a free account can keep: the clean wood and one more. */
    public static final int FREE_WOODS = 2;
    /** Days of history a free account can look back over. */
    public static final int FREE_HISTORY_DAYS = 14;

    public enum Feature {
        /** A rule beyond the first three. */
        MORE_RULES,
        /** A wood beyond the clean wood and one more. */
        MORE_WOODS,
        /** Tree kinds that are not earned by clean days. */
        TREE_KINDS,
        /** The hill changing with the season. */
        SEASONS,
        /** The wood on the home screen. */
        WOOD_WIDGET,
        /** Progress further back than two weeks. */
        LONG_HISTORY
    }

    private PlusGate() {}

    /**
     * Whether [feature] is open. [have] is how many the person already keeps (rules or woods);
     * it is ignored for features that are simply on or off.
     */
    public static boolean open(Feature feature, boolean plus, int have) {
        if (plus) return true;
        switch (feature) {
            case MORE_RULES: return have < FREE_RULES;
            case MORE_WOODS: return have < FREE_WOODS;
            default: return false;
        }
    }

    /** Plus is on while a subscription runs (until [paidUntil]) or for good after a one-time purchase. */
    public static boolean active(long now, long paidUntil, boolean lifetime) {
        return lifetime || paidUntil > now;
    }
}
