package app.hisn.core;

public final class BlazeFaceTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static boolean near(float a, float b) { return Math.abs(a - b) < 1e-3f; }

    /** A face of size w x h input pixels centred on anchor i, with the given logit. */
    static void put(float[] regressors, float[] logits, int i, float dx, float dy, float w, float h, float logit) {
        regressors[i * 16] = dx;
        regressors[i * 16 + 1] = dy;
        regressors[i * 16 + 2] = w;
        regressors[i * 16 + 3] = h;
        logits[i] = logit;
    }

    public static void main(String[] args) {
        float[] anchors = BlazeFace.anchors();
        check(anchors.length == 896 * 2, "896 anchors");
        check(near(anchors[0], 0.5f / 16) && near(anchors[1], 0.5f / 16), "the first anchor is the first 16 x 16 cell");
        check(near(anchors[2], anchors[0]), "two anchors share each 16 x 16 cell");
        check(near(anchors[512 * 2], 0.5f / 8) && near(anchors[512 * 2 + 1], 0.5f / 8), "anchor 512 starts the 8 x 8 grid");
        check(near(anchors[895 * 2], 7.5f / 8) && near(anchors[895 * 2 + 1], 7.5f / 8), "the last anchor is the last 8 x 8 cell");

        float[] regressors = new float[896 * 16], logits = new float[896];
        java.util.Arrays.fill(logits, -10f);
        check(BlazeFace.faces(regressors, logits, 256, 256).length == 0, "no confident anchor, no face");

        // Anchor 600 is in the 8 x 8 grid: (600 - 512) / 6 = cell 14, x = 6, y = 1: centre (0.8125, 0.1875).
        put(regressors, logits, 600, 0f, 0f, 64f, 64f, 3f);
        float[] one = BlazeFace.faces(regressors, logits, 256, 256);
        check(one.length == 4, "one confident anchor is one face");
        check(near(one[0], 0.8125f - 0.175f) && near(one[1], 0.1875f - 0.175f) && near(one[2], 0.35f),
                "the box is centred on the anchor, sized in input pixels, enlarged 1.4 times as Human does");
        float[] grown = BlazeFace.enlarged(new float[]{0.4f, 0.4f, 0.1f, 0.2f});
        check(near(grown[2], 0.28f) && near(grown[3], 0.28f) && near(grown[0] + grown[2] / 2, 0.45f) && near(grown[1] + grown[3] / 2, 0.5f),
                "a box is squared on its longer side and keeps its centre");

        put(regressors, logits, 601, 2f, 2f, 64f, 64f, 2f);
        check(BlazeFace.faces(regressors, logits, 256, 256).length == 4, "an overlapping weaker box is suppressed");
        put(regressors, logits, 100, 0f, 0f, 40f, 40f, 1f);
        float[] two = BlazeFace.faces(regressors, logits, 256, 256);
        check(two.length == 8 && two[0] > 0.55f, "a separate face is kept, strongest first");
        put(regressors, logits, 100, 0f, 0f, 40f, 40f, 0.2f);
        check(BlazeFace.faces(regressors, logits, 256, 256).length == 4, "a score under 0.60 is not a face");

        // A tall 100 x 200 frame sits centred in a 200 square: x from 50 to 150 of it.
        float[] mapped = BlazeFace.toFrame(new float[]{0.25f, 0.5f, 0.5f, 0.25f}, 100, 200);
        check(mapped != null && near(mapped[0], 0f) && near(mapped[2], 1f) && near(mapped[1], 0.5f) && near(mapped[3], 0.25f),
                "boxes map from the padded square back to the frame");
        check(BlazeFace.toFrame(new float[]{0f, 0f, 0.2f, 0.2f}, 100, 200) == null, "a box only on padding is dropped");
        check(BlazeFace.sourceX(0, 100, 200) < 0 && BlazeFace.sourceX(128, 100, 200) > 0 && BlazeFace.sourceY(255, 100, 200) < 200,
                "the input reads padding left and right of a tall frame");

        // Closer slices: a 173 x 384 phone frame gets the whole frame plus three 173 px squares.
        BlazeFace.Region[] tall = BlazeFace.regions(173, 384, true);
        check(tall.length == 4 && near(tall[0].side, 384) && near(tall[0].left, -105.5f), "the whole frame comes first, padded");
        check(near(tall[1].top, 0) && near(tall[3].top, 384 - 173) && near(tall[2].side, 173),
                "slices run the frame's length, the last ending at the bottom");
        check(tall[2].top - tall[1].top <= 0.75f * 173 + 0.01f, "slices overlap by at least a quarter");
        check(BlazeFace.regions(173, 384, false).length == 1, "without closer, only the whole frame");
        check(BlazeFace.regions(300, 330, true).length == 1, "a nearly square frame needs no slices");
        check(BlazeFace.regions(384, 173, true)[1].top == 0 && BlazeFace.regions(384, 173, true)[1].side == 173,
                "a wide frame is sliced across its width");
        check(near(tall[1].sourceX(0), 0.5f * 173 / 256 - 0.5f), "a slice reads its own pixels, no padding");

        // Merging: the same face from the whole frame and from a slice becomes one box.
        java.util.List<float[]> views = new java.util.ArrayList<>();
        views.add(new float[]{50, 100, 40, 40, 0.7f});
        views.add(new float[]{52, 104, 30, 30, 0.9f});
        views.add(new float[]{10, 300, 30, 30, 0.65f});
        views.add(new float[]{120, 20, 30, 30, 0.4f});
        float[] merged = BlazeFace.merge(views, 173, 384, 0.6f);
        check(merged.length == 8, "two faces: the duplicate joins, the weak one is dropped");
        float side = 40 * BlazeFace.ENLARGE;
        check(near(merged[2] * 173, side) && near(merged[3] * 384, side), "the joined box covers both views, then grows 1.4 times");
        check(near(merged[0] * 173 + merged[2] * 173 / 2, 70), "and stays centred on the face");

        boolean refused = false;
        try { BlazeFace.faces(new float[10], logits, 1, 1); } catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "a wrong output shape is refused, not read as no face");
        System.out.println("BlazeFaceTest passed");
    }
}
