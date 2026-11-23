package app.hisn.core;

/**
 * The geometry and thresholds of the face-only blur, kept free of Android so they can be tested.
 *
 * <p>Boxes are normalized to the captured window: x, y, width and height between 0 and 1,
 * repeated four values per person. Nothing here sees pixels.</p>
 */
public final class PeopleRegions {
    /** Margin around a blurred face, as a share of its box. Hair sits above the detected box, so
     * the top margin is larger. The next screenshot is a third of a second away. */
    public static final float SIDE_MARGIN = 0.25f, TOP_MARGIN = 0.45f, BOTTOM_MARGIN = 0.25f;
    /** NSFW model output order. */
    public static final int DRAWING = 0, HENTAI = 1, NEUTRAL = 2, PORN = 3, SEXY = 4;
    /** Revealing clothes: the Sexy score of the area under a face, alone or with Porn and Hentai. */
    public static final float REVEALING = 0.50f, REVEALING_SUM = 0.60f;
    /** Below this many pixels on a side, the area under a face is too small to judge. */
    public static final int MIN_TORSO_PIXELS = 12;
    /** A scroll step stretches a moving patch by this many steps, up to a cap in pixels. */
    public static final float STRETCH_STEPS = 1.5f;

    private PeopleRegions() { }

    /** The blurred area for one box: the box plus its margins, not clamped (it may run off screen). */
    public static float[] padded(float x, float y, float width, float height) {
        float side = width * SIDE_MARGIN, top = height * TOP_MARGIN, bottom = height * BOTTOM_MARGIN;
        return new float[]{x - side, y - top, width + 2 * side, height + top + bottom};
    }

    /** The area under a face to judge clothes on, in pixels of a frame: as wide as three faces, from
     * the chin to about four faces down, clamped to the frame. Null when too small to judge. */
    public static int[] torso(float x, float y, float width, float height, int frameWidth, int frameHeight) {
        int left = clamp((x - width) * frameWidth, frameWidth);
        int right = clamp((x + 2 * width) * frameWidth, frameWidth);
        int top = clamp((y + height) * frameHeight, frameHeight);
        int bottom = clamp((y + 5 * height) * frameHeight, frameHeight);
        if (right - left < MIN_TORSO_PIXELS || bottom - top < MIN_TORSO_PIXELS) return null;
        return new int[]{left, top, right - left, bottom - top};
    }

    /** The whole person for a face box: from just above the head to well below the torso, clamped
     * to the frame. */
    public static float[] body(float x, float y, float width, float height) {
        float x0 = Math.max(0f, x - 1.4f * width), x1 = Math.min(1f, x + 2.4f * width);
        float y0 = Math.max(0f, y - 0.3f * height), y1 = Math.min(1f, y + 8f * height);
        return new float[]{x0, y0, x1 - x0, y1 - y0};
    }

    /** Whether the model's scores for the area under a face call for blurring the whole person:
     * revealing clothes, or anything that looks explicit. Never a whole-screen block on its own:
     * a small crop of ordinary clothing can score as explicit. */
    public static boolean wholePerson(float[] scores) {
        if (scores == null || scores.length < 5) return false;
        float porn = scores[PORN], hentai = scores[HENTAI], sexy = scores[SEXY];
        return porn >= 0.70f || hentai >= 0.70f || porn + hentai >= 0.85f
                || sexy >= REVEALING || sexy + porn + hentai >= REVEALING_SUM;
    }

    /** Android reports a scroll without a distance as -1 in both directions. Absurd steps are
     * treated as unknown too. */
    public static boolean knownScroll(int dx, int dy) {
        return !(dx == -1 && dy == -1) && Math.abs(dx) < 5000 && Math.abs(dy) < 5000;
    }

    /** How far to stretch a patch each way for one scroll step, in pixels. */
    public static float stretch(int delta, float capPixels) {
        return Math.min(capPixels, Math.abs(delta) * STRETCH_STEPS);
    }

    private static int clamp(float value, int limit) {
        return Math.max(0, Math.min(limit, Math.round(value)));
    }
}
