package app.hisn.android;

/** The bounded answer from one local frame. No pixels, faces or scores cross this boundary,
 * except {@link #detail} in debug accuracy runs that ask for it. */
final class VisualResult {
    final String verdict;
    /** At least one face matched the selected people setting in this frame. */
    final boolean personBlur;
    /** A block far above the threshold: act at once instead of waiting for a second look. */
    final boolean strong;
    /** Per-face appearance estimates as JSON, only from a runtime created with evaluation on
     * (debug accuracy runs); null otherwise. */
    final String detail;
    /** Normalized face boxes (x, y, w, h repeated) from a faces-only WebView asked for boxes, so
     * the native FaceRes runtime can estimate women/men; null otherwise. */
    final float[] faceBoxes;

    VisualResult(String verdict, boolean personBlur) {
        this(verdict, personBlur, false);
    }

    VisualResult(String verdict, boolean personBlur, boolean strong) {
        this(verdict, personBlur, strong, null);
    }

    VisualResult(String verdict, boolean personBlur, boolean strong, String detail) {
        this(verdict, personBlur, strong, detail, null);
    }

    VisualResult(String verdict, boolean personBlur, boolean strong, String detail, float[] faceBoxes) {
        this.verdict = verdict;
        this.personBlur = personBlur;
        this.strong = strong && "block".equals(verdict);
        this.detail = detail;
        this.faceBoxes = faceBoxes;
    }
}
