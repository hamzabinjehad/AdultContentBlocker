package app.hisn.core;

/** Ephemeral, bounded text sample. Never stores screen content beyond this inspection. */
public final class VisibleTextSample {
    public static final int LIMIT = 32768;
    private final StringBuilder text = new StringBuilder();
    private boolean partial;
    private final boolean preservePunctuation;

    public VisibleTextSample() { this(false); }
    public VisibleTextSample(boolean preservePunctuation) {
        this.preservePunctuation = preservePunctuation;
    }

    public void markPartial() { partial = true; }
    public boolean isPartial() { return partial; }
    public boolean isFull() { return text.length() >= LIMIT; }
    public String text() { return text.toString(); }

    public void append(CharSequence value) {
        if (value == null || value.length() == 0) return;
        int remaining = LIMIT - text.length() - 1;
        if (remaining <= 0) { partial = true; return; }
        int end = Math.min(value.length(), LIMIT);
        if (end < value.length() && end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        String raw = value.subSequence(0, end).toString();
        // Keep leaf boundaries even when a source label contains line breaks.
        String normalized = preservePunctuation ? raw.replace('\n', ' ').replace('\r', ' ')
                : WordMatcher.normalize(raw);
        boolean truncated = end < value.length();
        if (normalized.length() > remaining) {
            normalized = normalized.substring(0, remaining);
            truncated = true;
        }
        if (truncated) {
            // A cut word must not become an apparent whole-word match. Dropping
            // the final token is conservative even when the cut followed punctuation.
            int boundary = normalized.lastIndexOf(' ');
            normalized = boundary < 0 ? "" : normalized.substring(0, boundary);
            partial = true;
        }
        if (!normalized.isEmpty()) text.append(normalized).append('\n');
    }
}
