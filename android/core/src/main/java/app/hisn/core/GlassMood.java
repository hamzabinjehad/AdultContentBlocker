package app.hisn.core;

/**
 * The colour of the moment in the general edition: the app has no single colour, it takes the
 * colour of what the person is doing. One state at a time, the most urgent first, so a cracked
 * glass is never hidden behind a running Focus session.
 */
public enum GlassMood {
    /** Clean days, the plant is rising. Green ground, leaf-gold. */
    GROWING,
    /** Focus is running. Cool and still, pale gold. */
    WORKING,
    /** The glass is cracked: an adult page was stopped and the seal window is open. Ember. */
    HARD,
    /** Sealed with gold today. The lamp-lit room. */
    REST,
    /** Quiet hours are on now. Indigo, low light. */
    NIGHT,
    /** The glass is broken. Colour drains out until it is whole again. */
    SLIP;

    public static GlassMood of(GlassState.Look look, boolean focusRunning, boolean quietNow) {
        if (look == GlassState.Look.BROKEN) return SLIP;
        if (look == GlassState.Look.CRACKED) return HARD;
        if (quietNow) return NIGHT;
        if (focusRunning) return WORKING;
        if (look == GlassState.Look.GOLD) return REST;
        return GROWING;
    }
}
