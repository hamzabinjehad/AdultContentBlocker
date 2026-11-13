package app.hisn.android;

/** The bounded answer from one local frame. No pixels, faces or scores cross this boundary. */
final class VisualResult {
    final String verdict;
    /** At least one face matched the selected people setting in this frame. */
    final boolean personBlur;
    /** A block far above the threshold: act at once instead of waiting for a second look. */
    final boolean strong;

    VisualResult(String verdict, boolean personBlur) {
        this(verdict, personBlur, false);
    }

    VisualResult(String verdict, boolean personBlur, boolean strong) {
        this.verdict = verdict;
        this.personBlur = personBlur;
        this.strong = strong && "block".equals(verdict);
    }
}
