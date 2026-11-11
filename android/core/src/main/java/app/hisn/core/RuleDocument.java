package app.hisn.core;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Android-supported subset of the browser's {@code hisn.rules} document.
 * Only personal blocked domains and literal word rules travel. Allowlists,
 * corrections, browser modes, deadlines, list generations and history do not.
 * Import is add-only and validates the whole document before the caller merges
 * it through the ordinary policy store.
 */
public final class RuleDocument {
    public static final String SCHEMA = "hisn.rules";
    public static final int VERSION = 1;
    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_RULES = 1000;
    private static final String[] UNSUPPORTED = {"allowlist", "ignoreTerms", "textAllow"};

    public static final class Invalid extends IllegalArgumentException {
        public final String reason;
        public Invalid(String reason) { super(reason); this.reason = reason; }
    }

    public static final class Result {
        public final List<String> blocks;
        public final List<String> words;
        public final int skippedHosts;
        public final int skippedWords;
        public final List<String> unsupported;
        public final String platform;

        Result(List<String> blocks, List<String> words, int skippedHosts, int skippedWords,
                List<String> unsupported, String platform) {
            this.blocks = Collections.unmodifiableList(blocks);
            this.words = Collections.unmodifiableList(words);
            this.skippedHosts = skippedHosts;
            this.skippedWords = skippedWords;
            this.unsupported = Collections.unmodifiableList(unsupported);
            this.platform = platform;
        }
    }

    /** A browser-compatible document with only the two lists Android enforces. */
    public static String export(String domains, String words) {
        RulePreview.Report domainPreview = RulePreview.of(domains, RulePreview.Kind.DOMAIN, MAX_RULES);
        RulePreview.Report wordPreview = RulePreview.of(words, RulePreview.Kind.WORD, MAX_RULES);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schema", SCHEMA);
        document.put("version", (long) VERSION);
        document.put("platform", "android");
        document.put("customBlocks", new ArrayList<Object>(domainPreview.accepted));
        document.put("customTerms", new ArrayList<Object>(wordPreview.accepted));
        return Json.write(document) + "\n";
    }

    public static Result parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new Invalid("size");
        }
        final Map<String, Object> root;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            root = Json.object(text);
        } catch (CharacterCodingException invalid) { throw new Invalid("encoding"); }
        catch (Json.Invalid invalid) { throw new Invalid("json"); }
        Object schema = root.get("schema");
        Object version = root.get("version");
        if (!SCHEMA.equals(schema) || !(version instanceof Long) || (Long) version != VERSION) {
            throw new Invalid("schema");
        }
        String platform = root.get("platform") instanceof String
                ? (String) root.get("platform") : "browser";
        List<String> unsupported = new ArrayList<>();
        for (String field : UNSUPPORTED) {
            Object value = root.get(field);
            if (value instanceof List && !((List<?>) value).isEmpty()) unsupported.add(field);
        }
        List<String> blocks = list(root.get("customBlocks"), true, platform, unsupported);
        List<String> words = list(root.get("customTerms"), false, platform, unsupported);
        int skippedHosts = 0, skippedWords = 0;
        // Canonicalize through the enforcing grammars rather than trusting the file.
        RulePreview.Report domainPreview = RulePreview.of(String.join("\n", blocks),
                RulePreview.Kind.DOMAIN, MAX_RULES);
        skippedHosts = domainPreview.rejected + domainPreview.duplicates;
        RulePreview.Report wordPreview = RulePreview.of(String.join("\n", words),
                RulePreview.Kind.WORD, MAX_RULES);
        skippedWords = wordPreview.rejected + wordPreview.duplicates;
        return new Result(domainPreview.accepted, wordPreview.accepted, skippedHosts, skippedWords,
                unsupported, platform);
    }

    /**
     * Add-only merge: every existing line is kept and imported lines are appended.
     * Deduplication happens through {@link RulePreview} in the caller's ordinary save.
     */
    public static String merge(String existing, List<String> imported) {
        List<String> lines = new ArrayList<>();
        if (existing != null) {
            for (String line : existing.split("\\R")) if (!line.trim().isEmpty()) lines.add(line.trim());
        }
        lines.addAll(imported);
        return String.join("\n", lines);
    }

    private static List<String> list(Object value, boolean hosts, String platform,
            List<String> unsupported) {
        if (value == null) return Collections.emptyList();
        if (!(value instanceof List) || ((List<?>) value).size() > MAX_RULES) {
            throw new Invalid(hosts ? "customBlocks" : "customTerms");
        }
        if (!"android".equals(platform) && !"browser".equals(platform)) {
            // A file from another platform is accepted for the fields Android can enforce;
            // its platform-specific lists are reported by the caller, never applied.
            if (!unsupported.contains(platform)) unsupported.add(platform);
        }
        List<String> out = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof String)) throw new Invalid(hosts ? "customBlocks" : "customTerms");
            String text = (String) item;
            if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
                throw new Invalid(hosts ? "customBlocks" : "customTerms");
            }
            out.add(text);
        }
        return out;
    }

    private RuleDocument() { }
}
