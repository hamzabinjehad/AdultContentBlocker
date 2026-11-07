package app.hisn.android;

/** The bounded answer from one local frame. No pixels, faces or scores cross this boundary. */
final class VisualResult {
    final String verdict;
    /** At least one face matched the selected people setting in this frame. */
    final boolean personBlur;

    VisualResult(String verdict, boolean personBlur) {
        this.verdict = verdict;
        this.personBlur = personBlur;
    }
}
