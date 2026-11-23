package app.hisn.core;

/**
 * What Unrot's glass looks like right now (design/glasshouse-2026-11-23).
 *
 * <ul>
 *   <li>An adult page closed by Unrot cracks the glass for {@link #SEAL_WINDOW_MS}.</li>
 *   <li>A breathing pause inside that window seals the crack with gold, like kintsugi. The gold
 *       shows for the rest of that local day; the count of seams is kept for good.</li>
 *   <li>The glass only breaks when the person says adult content got through: Unrot cannot see
 *       what it did not block, so this is their own honest report. It shows for
 *       {@link #BROKEN_SHOWS_MS}, then new glass is on.</li>
 * </ul>
 * Pure logic: times are epoch millis, days are local epoch days, so it is tested without Android.
 */
public final class GlassState {
    public static final long SEAL_WINDOW_MS = 10 * 60_000L;
    public static final long BROKEN_SHOWS_MS = 60 * 60_000L;

    public enum Look { CLEAR, CRACKED, GOLD, BROKEN }

    public final Look look;
    /** While cracked: how long is left to seal it. Zero otherwise. */
    public final long sealLeftMs;

    private GlassState(Look look, long sealLeftMs) {
        this.look = look;
        this.sealLeftMs = sealLeftMs;
    }

    /**
     * @param crackedAt last adult page closed, 0 if never
     * @param sealedAt  last crack sealed with a breath, 0 if never
     * @param brokenAt  last time the person reported adult content got through, 0 if never
     * @param sealedDay local epoch day of sealedAt
     * @param today     local epoch day of now
     */
    public static GlassState at(long now, long crackedAt, long sealedAt, long brokenAt, long sealedDay, long today) {
        // A clock moved backwards never leaves the glass stuck in a state.
        if (brokenAt > 0 && brokenAt <= now && now - brokenAt < BROKEN_SHOWS_MS && brokenAt >= crackedAt) {
            return new GlassState(Look.BROKEN, 0);
        }
        boolean sealedThisCrack = sealedAt >= crackedAt && crackedAt > 0;
        if (crackedAt > 0 && crackedAt <= now && !sealedThisCrack && now - crackedAt < SEAL_WINDOW_MS) {
            return new GlassState(Look.CRACKED, SEAL_WINDOW_MS - (now - crackedAt));
        }
        if (sealedAt > 0 && sealedAt <= now && sealedDay == today) return new GlassState(Look.GOLD, 0);
        return new GlassState(Look.CLEAR, 0);
    }

    /** A breath at {@code now} seals the crack only inside the window and only once. */
    public static boolean breathSeals(long now, long crackedAt, long sealedAt) {
        return crackedAt > 0 && sealedAt < crackedAt && now >= crackedAt && now - crackedAt < SEAL_WINDOW_MS;
    }
}
