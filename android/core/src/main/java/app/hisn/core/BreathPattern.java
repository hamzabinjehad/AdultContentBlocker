package app.hisn.core;

/**
 * The timing of the voluntary breathing pause, kept free of Android so it can be tested.
 *
 * <p>{@link #SIGH} is cyclic sighing: a slow inhale, a short second inhale that tops the lungs up,
 * then a long, slow exhale. In a randomized study (Balban et al., Cell Reports Medicine, 2023) it
 * improved mood and lowered breathing rate more than box breathing, cyclic hyperventilation or
 * mindfulness meditation. {@link #EVEN} is the plain 4-in, 6-out breath the app used before, kept
 * for people who find the double inhale awkward.
 *
 * <p>Both run three breaths: about thirty seconds, the length the home screen promises.
 */
public enum BreathPattern {
    SIGH("sigh", new Phase[]{Phase.IN, Phase.TOP_UP, Phase.OUT}, new long[]{3000, 1000, 6000}),
    EVEN("even", new Phase[]{Phase.IN, Phase.OUT}, new long[]{4000, 6000});

    /** What the person does in a step; the screen turns each into words and a ring size. */
    public enum Phase { IN, TOP_UP, OUT }

    public static final int BREATHS = 3;

    public final String key;
    private final Phase[] phases;
    private final long[] durations;

    BreathPattern(String key, Phase[] phases, long[] durations) {
        this.key = key;
        this.phases = phases;
        this.durations = durations;
    }

    /** The stored key back to a pattern; anything unknown or missing is the recommended one. */
    public static BreathPattern fromKey(String key) {
        for (BreathPattern pattern : values()) if (pattern.key.equals(key)) return pattern;
        return SIGH;
    }

    public int phaseCount() { return phases.length; }

    public Phase phase(int index) { return phases[index]; }

    public long duration(int index) { return durations[index]; }

    public long breathMillis() {
        long total = 0;
        for (long duration : durations) total += duration;
        return total;
    }

    public long totalMillis() { return breathMillis() * BREATHS; }

    /**
     * Where the session is after {@code elapsed} milliseconds: the breath (0-based), the step
     * inside it, and how far that step has run. Past the end it reports the last step complete.
     */
    public Position at(long elapsed) {
        long clamped = Math.max(0, Math.min(elapsed, totalMillis()));
        int breath = (int) Math.min(BREATHS - 1, clamped / breathMillis());
        long within = clamped - breath * breathMillis();
        for (int index = 0; index < phases.length; index++) {
            long length = durations[index];
            boolean last = index == phases.length - 1;
            if (within < length || last) {
                long done = Math.min(within, length);
                return new Position(breath, index, phases[index], done, length,
                        clamped >= totalMillis());
            }
            within -= length;
        }
        throw new AssertionError("unreachable");
    }

    /**
     * The ring's relative size at the end of a step: an inhale grows it most of the way, the
     * top-up finishes it, and the exhale brings it back to rest. Without a top-up, the inhale
     * fills it on its own.
     */
    public float ringTarget(Phase phase) {
        switch (phase) {
            case IN: return this == SIGH ? 0.9f : 1f;
            case TOP_UP: return 1f;
            default: return REST;
        }
    }

    /** The ring's size at rest, before the first inhale and after each exhale. */
    public static final float REST = 0.62f;

    /** One moment of the session. */
    public static final class Position {
        public final int breath;
        public final int index;
        public final Phase phase;
        public final long done;
        public final long length;
        public final boolean finished;

        Position(int breath, int index, Phase phase, long done, long length, boolean finished) {
            this.breath = breath;
            this.index = index;
            this.phase = phase;
            this.done = done;
            this.length = length;
            this.finished = finished;
        }

        /** Whole seconds left in this step, rounded up, so the count never shows zero mid-step. */
        public int secondsLeft() {
            long left = length - done;
            return left <= 0 ? 0 : (int) ((left + 999) / 1000);
        }
    }
}
