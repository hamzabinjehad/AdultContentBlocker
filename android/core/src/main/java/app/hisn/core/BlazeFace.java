package app.hisn.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decoding for MediaPipe's BlazeFace "back" detector (256 x 256 input, 896 anchors), the model
 * Human's {@code blazeface.json} in the WebView engine was converted from. Pure Java, no pixels.
 *
 * <p>The frame is padded to a centred square and resized to {@link #SIZE}; the model returns 16
 * numbers per anchor (box centre, size and six keypoints, in input pixels relative to the anchor)
 * and one score logit. Settings match the WebView engine: a face needs a score of at least
 * {@link #MIN_SCORE}, overlapping boxes above {@link #IOU} are suppressed, at most
 * {@link #MAX_FACES} are kept.</p>
 */
public final class BlazeFace {
    public static final int SIZE = 256, ANCHORS = 896, COORDS = 16, MAX_FACES = 10;
    public static final float MIN_SCORE = 0.60f, IOU = 0.30f;
    /** Human enlarges each detected box by this much about its centre and makes it square
     * (util.enlargeBox, util.squarifyBox) before FaceRes reads it; the same box is blurred. */
    public static final float ENLARGE = 1.4f;
    /** SSD anchor layout of the back model: one 16 x 16 grid with 2 anchors per cell (stride 16),
     * then three stride-32 layers sharing an 8 x 8 grid, 6 anchors per cell. Fixed anchor size. */
    private static final int[][] GRIDS = {{16, 2}, {8, 6}};
    private static final float[] CENTRES = anchors();

    private BlazeFace() { }

    /** Anchor centres, x then y per anchor, in 0..1 of the input. */
    static float[] anchors() {
        float[] out = new float[ANCHORS * 2];
        int n = 0;
        for (int[] grid : GRIDS) {
            int cells = grid[0], perCell = grid[1];
            for (int y = 0; y < cells; y++) {
                for (int x = 0; x < cells; x++) {
                    for (int a = 0; a < perCell; a++) {
                        out[n++] = (x + 0.5f) / cells;
                        out[n++] = (y + 0.5f) / cells;
                    }
                }
            }
        }
        if (n != ANCHORS * 2) throw new IllegalStateException("BlazeFace anchor count");
        return out;
    }

    static float anchorX(int i) { return CENTRES[i * 2]; }

    static float anchorY(int i) { return CENTRES[i * 2 + 1]; }

    /** Faces in a frame of width x height: normalized x, y, w, h repeated, strongest first, clamped
     * to the frame. regressors is ANCHORS x COORDS, logits is ANCHORS long. Never null. */
    public static float[] faces(float[] regressors, float[] logits, int width, int height) {
        Region full = regions(width, height, false)[0];
        return merge(inFrame(detect(regressors, logits, MIN_SCORE), full), width, height, MIN_SCORE);
    }

    /** A square part of the frame the model looks at: its top-left corner and side, in frame
     * pixels. The first region of every frame is the whole frame padded to a centred square, as
     * the WebView engine pads it; a negative corner is padding. */
    public static final class Region {
        public final float left, top, side;
        Region(float left, float top, float side) { this.left = left; this.top = top; this.side = side; }

        /** Frame x that model input column x reads (bilinear sample position). */
        public float sourceX(int x) { return left + (x + 0.5f) * side / SIZE - 0.5f; }

        public float sourceY(int y) { return top + (y + 0.5f) * side / SIZE - 0.5f; }
    }

    /** At most this many closer slices besides the whole frame. */
    public static final int MAX_SLICES = 3;
    /** A frame this much longer than wide (or wider than long) also gets closer slices. */
    public static final float SLICE_ASPECT = 1.3f;

    /** The regions to look at: the whole frame, and with closer, square slices along a long
     * frame's length, overlapping by at least a quarter. On a 9:20 phone screen the whole frame
     * shrinks its width to under half the model's input; a slice shows each face about twice as
     * large, so small faces in a feed reach the detector at a size it finds. */
    public static Region[] regions(int width, int height, boolean closer) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("frame");
        int edge = Math.max(width, height);
        Region whole = new Region(-(edge - width) / 2f, -(edge - height) / 2f, edge);
        int shorter = Math.min(width, height);
        if (!closer || edge < shorter * SLICE_ASPECT) return new Region[]{whole};
        int count = Math.min(MAX_SLICES, (int) Math.ceil((edge - shorter) / (0.75f * shorter)) + 1);
        Region[] out = new Region[count + 1];
        out[0] = whole;
        for (int i = 0; i < count; i++) {
            float along = count == 1 ? 0 : (edge - shorter) * i / (float) (count - 1);
            out[i + 1] = height >= width ? new Region(0, along, shorter) : new Region(along, 0, shorter);
        }
        return out;
    }

    /** One pass of model output as boxes in the region's input (0..1), each x, y, w, h, score,
     * strongest first, overlaps above IOU removed. */
    public static List<float[]> detect(float[] regressors, float[] logits, float minScore) {
        if (regressors == null || logits == null || regressors.length < ANCHORS * COORDS || logits.length < ANCHORS) {
            throw new IllegalArgumentException("BlazeFace output shape");
        }
        List<float[]> found = new ArrayList<>();
        for (int i = 0; i < ANCHORS; i++) {
            float score = sigmoid(logits[i]);
            if (!(score >= minScore)) continue;
            int r = i * COORDS;
            float cx = regressors[r] / SIZE + anchorX(i), cy = regressors[r + 1] / SIZE + anchorY(i);
            float w = regressors[r + 2] / SIZE, h = regressors[r + 3] / SIZE;
            if (!(w > 0 && h > 0) || !Float.isFinite(cx) || !Float.isFinite(cy)) continue;
            found.add(new float[]{cx - w / 2, cy - h / 2, w, h, score});
        }
        return suppress(found);
    }

    /** Boxes from a region's input as boxes in frame pixels, score kept. */
    public static List<float[]> inFrame(List<float[]> boxes, Region region) {
        List<float[]> out = new ArrayList<>();
        for (float[] b : boxes) {
            out.add(new float[]{region.left + b[0] * region.side, region.top + b[1] * region.side,
                    b[2] * region.side, b[3] * region.side, b[4]});
        }
        return out;
    }

    /** Faces from every region, in frame pixels, as the final boxes: overlaps merged (a face cut
     * at a slice edge joins the whole-frame box for it), each grown by {@link #ENLARGE} and squared
     * as Human does, clamped and normalized to the frame, strongest first, at most MAX_FACES. */
    public static float[] merge(List<float[]> pixelBoxes, int width, int height, float minScore) {
        List<float[]> strong = new ArrayList<>();
        for (float[] box : pixelBoxes) if (box[4] >= minScore) strong.add(box.clone());
        strong.sort((a, b) -> Float.compare(b[4], a[4]));
        List<float[]> kept = new ArrayList<>();
        for (float[] box : strong) {
            float[] same = null;
            for (float[] other : kept) {
                if (iou(box, other) > IOU || contained(box, other) > CONTAINED) { same = other; break; }
            }
            if (same == null) { if (kept.size() < MAX_FACES) kept.add(box); continue; }
            // The same face seen twice: cover both views of it.
            float x0 = Math.min(same[0], box[0]), y0 = Math.min(same[1], box[1]);
            float x1 = Math.max(same[0] + same[2], box[0] + box[2]), y1 = Math.max(same[1] + same[3], box[1] + box[3]);
            same[0] = x0; same[1] = y0; same[2] = x1 - x0; same[3] = y1 - y0;
        }
        float[] out = new float[kept.size() * 4];
        int n = 0;
        for (float[] box : kept) {
            float side = Math.max(box[2], box[3]) * ENLARGE;
            float cx = box[0] + box[2] / 2, cy = box[1] + box[3] / 2;
            float x0 = clamp((cx - side / 2) / width), y0 = clamp((cy - side / 2) / height);
            float x1 = clamp((cx + side / 2) / width), y1 = clamp((cy + side / 2) / height);
            if (!(x1 > x0 && y1 > y0)) continue;
            out[n++] = x0; out[n++] = y0; out[n++] = x1 - x0; out[n++] = y1 - y0;
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    /** Two boxes this much of the smaller inside the larger are the same face. */
    static final float CONTAINED = 0.6f;

    static float contained(float[] a, float[] b) {
        float x0 = Math.max(a[0], b[0]), y0 = Math.max(a[1], b[1]);
        float x1 = Math.min(a[0] + a[2], b[0] + b[2]), y1 = Math.min(a[1] + a[3], b[1] + b[3]);
        float inter = Math.max(0, x1 - x0) * Math.max(0, y1 - y0);
        float smaller = Math.min(a[2] * a[3], b[2] * b[3]);
        return smaller <= 0 ? 0 : inter / smaller;
    }

    private static List<float[]> suppress(List<float[]> found) {
        found.sort((a, b) -> Float.compare(b[4], a[4]));
        List<float[]> kept = new ArrayList<>();
        for (float[] box : found) {
            boolean overlaps = false;
            for (float[] other : kept) if (iou(box, other) > IOU) { overlaps = true; break; }
            if (!overlaps) kept.add(box);
            if (kept.size() == MAX_FACES) break;
        }
        return kept;
    }

    /** The box grown by {@link #ENLARGE} and squared on its longer side, about its centre. */
    static float[] enlarged(float[] box) {
        float side = Math.max(box[2], box[3]) * ENLARGE;
        float cx = box[0] + box[2] / 2, cy = box[1] + box[3] / 2;
        return new float[]{cx - side / 2, cy - side / 2, side, side};
    }

    /** A box in the padded square (0..1) as a box in the frame (0..1), clamped; null when it falls
     * outside the frame. */
    static float[] toFrame(float[] box, int width, int height) {
        int edge = Math.max(width, height);
        float padX = (edge - width) / 2f, padY = (edge - height) / 2f;
        float x0 = (box[0] * edge - padX) / width, y0 = (box[1] * edge - padY) / height;
        float x1 = ((box[0] + box[2]) * edge - padX) / width, y1 = ((box[1] + box[3]) * edge - padY) / height;
        x0 = clamp(x0); y0 = clamp(y0); x1 = clamp(x1); y1 = clamp(y1);
        return x1 > x0 && y1 > y0 ? new float[]{x0, y0, x1 - x0, y1 - y0} : null;
    }

    /** Where model input column x reads from the frame for the whole-frame region. */
    public static float sourceX(int x, int width, int height) { return regions(width, height, false)[0].sourceX(x); }

    public static float sourceY(int y, int width, int height) { return regions(width, height, false)[0].sourceY(y); }

    static float iou(float[] a, float[] b) {
        float x0 = Math.max(a[0], b[0]), y0 = Math.max(a[1], b[1]);
        float x1 = Math.min(a[0] + a[2], b[0] + b[2]), y1 = Math.min(a[1] + a[3], b[1] + b[3]);
        float inter = Math.max(0, x1 - x0) * Math.max(0, y1 - y0);
        float union = a[2] * a[3] + b[2] * b[3] - inter;
        return union <= 0 ? 0 : inter / union;
    }

    private static float sigmoid(float logit) {
        // Same clipping as MediaPipe (score_clipping_thresh 100).
        float clipped = Math.max(-100f, Math.min(100f, logit));
        return (float) (1 / (1 + Math.exp(-clipped)));
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
