package app.hisn.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A preview of a personal rule paste. It never writes anything: it canonicalizes
 * each non-empty line with the same grammar the enforcing component uses, marks
 * duplicates after canonicalization, and names rejected lines. The caller applies
 * the accepted set in one ordinary save.
 */
public final class RulePreview {
    public enum Kind { DOMAIN, WORD }
    public enum Status { VALID, DUPLICATE, INVALID }

    public static final class Entry {
        public final String raw;
        public final String normalized;
        public final Status status;
        public final String reason;

        Entry(String raw, String normalized, Status status, String reason) {
            this.raw = raw;
            this.normalized = normalized;
            this.status = status;
            this.reason = reason;
        }
    }

    public static final class Report {
        public final List<Entry> entries;
        public final List<String> accepted;
        public final int duplicates;
        public final int rejected;

        Report(List<Entry> entries, List<String> accepted, int duplicates, int rejected) {
            this.entries = Collections.unmodifiableList(entries);
            this.accepted = Collections.unmodifiableList(accepted);
            this.duplicates = duplicates;
            this.rejected = rejected;
        }

        public boolean hasRejected() { return rejected > 0 || duplicates > 0; }

        public String acceptedText() { return String.join("\n", accepted); }
    }

    /** @throws IllegalArgumentException when the whole paste is over a hard limit */
    public static Report of(String text, Kind kind, int maxLines) {
        if (text == null) text = "";
        if (text.codePointCount(0, text.length()) > 65536) throw new IllegalArgumentException("Rules too long");
        if (maxLines < 0) throw new IllegalArgumentException("Invalid line limit");
        String[] lines = text.split("\\R", -1);
        if (lines.length > maxLines) throw new IllegalArgumentException("Too many lines");
        List<Entry> entries = new ArrayList<>();
        List<String> accepted = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int duplicates = 0, rejected = 0;
        for (String line : lines) {
            String raw = line.trim();
            if (raw.isEmpty()) continue;
            String normalized;
            try {
                normalized = kind == Kind.DOMAIN
                        ? Hostname.normalize(raw)
                        : new WordMatcher(Collections.singleton(raw)).configuredRules().iterator().next();
            } catch (RuntimeException invalid) {
                entries.add(new Entry(raw, null, Status.INVALID,
                        kind == Kind.DOMAIN ? "hostname" : "word"));
                rejected++;
                continue;
            }
            if (!seen.add(normalized)) {
                entries.add(new Entry(raw, normalized, Status.DUPLICATE, "duplicate"));
                duplicates++;
            } else {
                entries.add(new Entry(raw, normalized, Status.VALID, ""));
                accepted.add(normalized);
            }
        }
        return new Report(entries, accepted, duplicates, rejected);
    }

    private RulePreview() { }
}
