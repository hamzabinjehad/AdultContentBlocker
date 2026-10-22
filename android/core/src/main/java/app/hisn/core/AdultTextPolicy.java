package app.hisn.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bounded, local adult-promotion classification for an explicitly selected text surface.
 *
 * Unlike user-selected literal rules, a word such as "sex" or a single ambiguous
 * seed term does not itself block. A 16-token window needs promotional wording
 * and a reviewed explicit term/strong phrase or two distinct strong signals.
 * Complete two-word explicit media labels can supply that intent without a verb.
 * Signed negative context subtracts evidence only within eight tokens of that
 * window; distant medical prose cannot cancel a separate promotional cluster.
 * This is not an image classifier or a guarantee of detecting all adult text.
 * The immutable policy never stores, logs or sends submitted text anywhere.
 */
public final class AdultTextPolicy {
    public static final int MAX_RULES = 8192;
    public static final int MAX_RULE_CODEPOINTS = 128;
    public static final int MAX_TEXT_CODEPOINTS = 65536;
    private static final int MAX_PHRASE_TOKENS = 8;
    private static final int WINDOW = 16;
    private static final int CONTEXT = 8;
    private static final int STRONG_WEIGHT = 8;

    /** A signed seed entry; negative weights describe specific benign context. */
    public static final class Rule {
        public final String phrase;
        public final int weight;

        public Rule(String phrase, int weight) {
            if (phrase == null || weight == 0 || weight < -100 || weight > 100) {
                throw new IllegalArgumentException("Invalid adult-text rule");
            }
            this.phrase = phrase;
            this.weight = weight;
        }
    }

    /** Reviewed app-side context, separate from the signed vocabulary asset. */
    public static List<Rule> reviewedContextRules() {
        return Collections.unmodifiableList(Arrays.asList(new Rule("porn addiction", -8),
                new Rule("addiction recovery", -8), new Rule("علاج ادمان الاباحيه", -8),
                new Rule("porn blocker", -8), new Rule("pornography blocker", -8),
                new Rule("adult content blocker", -8), new Rule("porn blocking", -8),
                new Rule("blocking porn", -8), new Rule("حجب الاباحيه", -8), new Rule("حظر الاباحيه", -8)));
    }

    private static final Set<String> EXPLICIT = immutableSet("porn", "porno", "pornography",
            "pornographic", "hentai", "blowjob", "cumshot", "creampie", "gangbang",
            "handjob", "اباحي", "اباحيه", "بورنو", "سكس", "نيك");
    private static final Set<String> PROMOTION = immutableSet("watch", "download", "stream",
            "streaming", "free", "شاهد", "مشاهده", "تحميل", "تنزيل", "مجانا", "مجاني");
    private static final String[] PREFIXES = {"وبال", "فبال", "وال", "بال", "فال", "كال",
            "لل", "ال", "و", "ف", "ب", "ك", "ل"};
    private final Trie index = new Trie();
    private final List<Entry> entries;
    private final Map<String, Entry> singleWords;

    public AdultTextPolicy(Iterable<Rule> rules) {
        if (rules == null) throw new IllegalArgumentException("Missing adult-text rules");
        Map<String, Integer> weights = new LinkedHashMap<>();
        int submitted = 0;
        for (Rule rule : rules) {
            if (rule == null || ++submitted > MAX_RULES) {
                throw new IllegalArgumentException("Too many or invalid adult-text rules");
            }
            if (rule.phrase.codePointCount(0, rule.phrase.length()) > MAX_RULE_CODEPOINTS) {
                throw new IllegalArgumentException("Adult-text phrase too long");
            }
            String phrase = canonical(rule.phrase);
            if (phrase.isEmpty() || phrase.codePointCount(0, phrase.length()) > MAX_RULE_CODEPOINTS) {
                throw new IllegalArgumentException("Invalid normalized adult-text phrase");
            }
            if (phrase.split(" ").length > MAX_PHRASE_TOKENS) {
                throw new IllegalArgumentException("Too many words in adult-text phrase");
            }
            // Compiler aliases can canonicalize to the same phrase. Never let
            // duplicates inflate evidence or turn conflicting context positive.
            Integer old = weights.get(phrase);
            if (old == null) weights.put(phrase, rule.weight);
            else if (old < 0 || rule.weight < 0) weights.put(phrase, Math.min(old, rule.weight));
            else weights.put(phrase, Math.max(old, rule.weight));
        }
        List<Entry> compiled = new ArrayList<>();
        Map<String, Entry> singles = new HashMap<>();
        for (Map.Entry<String, Integer> rule : weights.entrySet()) {
            String[] words = rule.getKey().split(" ");
            Entry entry = new Entry(compiled.size(), rule.getKey(), rule.getValue(), words.length);
            compiled.add(entry);
            Trie branch = index;
            for (String word : words) branch = branch.children.computeIfAbsent(word, unused -> new Trie());
            branch.entry = entry;
            if (words.length == 1) singles.put(rule.getKey(), entry);
        }
        entries = Collections.unmodifiableList(compiled);
        singleWords = Collections.unmodifiableMap(singles);
    }

    public int ruleCount() { return entries.size(); }

    /** Classify a bounded sample. Oversized input is rejected, never silently truncated. */
    public boolean blocks(String input) {
        if (input == null || input.isEmpty()) return false;
        if (input.codePointCount(0, input.length()) > MAX_TEXT_CODEPOINTS) {
            throw new IllegalArgumentException("Adult-text sample exceeds inspection limit");
        }
        if (entries.isEmpty()) return false;
        String normalized = canonical(recoverCompactSpellings(Normalizer.normalize(input, Normalizer.Form.NFKC)));
        if (normalized.isEmpty()) return false;
        // NFKC can expand compatibility characters. Bound the normalized work
        // too, rather than permitting large hidden allocation after validation.
        if (normalized.codePointCount(0, normalized.length()) > MAX_TEXT_CODEPOINTS) {
            throw new IllegalArgumentException("Normalized adult-text sample exceeds inspection limit");
        }
        String[] tokens = normalized.split(" ");
        boolean[] promotions = new boolean[tokens.length];
        boolean anyPromotion = false;
        for (int at = 0; at < tokens.length; at++) {
            promotions[at] = isPromotion(tokens[at]);
            anyPromotion |= promotions[at];
        }
        // A complete two-word media label is evidence without a call to action.
        // Do not apply this to substrings of research/recovery prose or generic
        // "adult" labels; configured strong terms and negative context still score.
        if (!anyPromotion && explicitMediaLabel(tokens)) {
            promotions[0] = true;
            anyPromotion = true;
        }
        // Most UI labels are ordinary navigation or prose. Avoid allocating
        // per-rule scoring buffers and hit lists when no verdict is possible.
        if (!anyPromotion) return false;
        @SuppressWarnings("unchecked")
        List<Hit>[] hits = (List<Hit>[]) new List<?>[tokens.length];
        @SuppressWarnings("unchecked")
        List<Hit>[] starts = (List<Hit>[]) new List<?>[tokens.length];
        for (int start = 0; start < tokens.length; start++) {
            Trie branch = index;
            for (int end = start; end < Math.min(tokens.length, start + MAX_PHRASE_TOKENS); end++) {
                branch = branch.children.get(tokens[end]);
                if (branch == null) break;
                if (branch.entry != null) record(hits, starts, new Hit(branch.entry, start, end));
            }
            Entry variant = variant(tokens[start]);
            if (variant != null && !tokens[start].equals(variant.phrase)) {
                record(hits, starts, new Hit(variant, start, start));
            }
        }
        return classifyWindows(hits, starts, promotions);
    }

    private boolean explicitMediaLabel(String[] tokens) {
        if (tokens.length != 2) return false;
        String explicit;
        if (tokens[1].equals("videos") || tokens[1].equals("video")
                || tokens[1].equals("photos") || tokens[1].equals("images")) {
            explicit = tokens[0];
        } else if (tokens[0].equals("محتوي") || tokens[0].equals("فيديو")
                || tokens[0].equals("صور")) {
            explicit = tokens[1];
        } else return false;
        Entry entry = singleWords.get(explicit);
        if (entry == null) entry = variant(explicit);
        return entry != null && entry.weight >= STRONG_WEIGHT && EXPLICIT.contains(entry.phrase);
    }

    private boolean classifyWindows(List<Hit>[] hits, List<Hit>[] starts, boolean[] promotions) {
        int[] positiveCounts = new int[entries.size()];
        int[] negativeCounts = new int[entries.size()];
        int positiveScore = 0, negativeScore = 0, strong = 0, trusted = 0, promotion = 0;
        // All mutations concern one bounded sliding window, never rescan the
        // vocabulary for each text position. Each hit enters/leaves once.
        for (int end = 0; end < hits.length; end++) {
            int contextEnd = Math.min(hits.length - 1, end + CONTEXT);
            int oldContextEnd = end == 0 ? -1 : Math.min(hits.length - 1, end - 1 + CONTEXT);
            for (int at = oldContextEnd + 1; at <= contextEnd; at++) {
                if (hits[at] == null) continue;
                for (Hit hit : hits[at]) if (hit.entry.weight < 0 && negativeCounts[hit.entry.id]++ == 0) {
                    negativeScore -= hit.entry.weight;
                }
            }
            int expiredContext = end - WINDOW - CONTEXT;
            if (expiredContext >= 0 && hits[expiredContext] != null) {
                for (Hit hit : hits[expiredContext]) if (hit.entry.weight < 0
                        && --negativeCounts[hit.entry.id] == 0) negativeScore += hit.entry.weight;
            }
            if (promotions[end]) promotion++;
            if (end >= WINDOW && promotions[end - WINDOW]) promotion--;
            if (hits[end] != null) for (Hit hit : hits[end]) {
                Entry entry = hit.entry;
                if (entry.weight > 0 && positiveCounts[entry.id]++ == 0) {
                    positiveScore += entry.weight;
                    if (entry.weight >= STRONG_WEIGHT) {
                        strong++;
                        if (entry.trusted) trusted++;
                    }
                }
            }
            if (end >= WINDOW && starts[end - WINDOW] != null) for (Hit hit : starts[end - WINDOW]) {
                Entry entry = hit.entry;
                if (entry.weight > 0 && --positiveCounts[entry.id] == 0) {
                    positiveScore -= entry.weight;
                    if (entry.weight >= STRONG_WEIGHT) {
                        strong--;
                        if (entry.trusted) trusted--;
                    }
                }
            }
            // A contextual phrase must earn its signed weight; neither one
            // generic "education" nor distant benign paragraphs grant immunity.
            int threshold = strong >= 2 ? 16 : 8;
            if (promotion > 0 && strong > 0 && (trusted > 0 || strong >= 2)
                    && positiveScore - negativeScore >= threshold) return true;
        }
        return false;
    }

    private static void record(List<Hit>[] hits, List<Hit>[] starts, Hit hit) {
        if (hits[hit.end] == null) hits[hit.end] = new ArrayList<>();
        // Exact and a reviewed alias may identify the same terminal. Avoid
        // double counting without creating an unbounded global set of text.
        for (Hit old : hits[hit.end]) if (old.entry.id == hit.entry.id && old.start == hit.start) return;
        hits[hit.end].add(hit);
        if (hit.entry.weight > 0) {
            if (starts[hit.start] == null) starts[hit.start] = new ArrayList<>();
            starts[hit.start].add(hit);
        }
    }

    /** Recover only an entire compact run matching a configured strong explicit
     * term. Never join whitespace, URL separators, unrelated whole words or
     * substrings of a larger decorated word. Work is linear with 128-char runs. */
    private String recoverCompactSpellings(String input) {
        StringBuilder result = null;
        int copiedUntil = 0;
        for (int at = 0; at < input.length();) {
            int point = input.codePointAt(at);
            if (!compactCharacter(point)) {
                at += Character.charCount(point); continue;
            }
            int start = at;
            boolean hasJoiner = false;
            do {
                hasJoiner |= joiner(input.codePointAt(at));
                at += Character.charCount(input.codePointAt(at));
            }
            while (at < input.length() && compactCharacter(input.codePointAt(at)));
            Entry recovered = null;
            if (hasJoiner && at - start <= 128) {
                int left = start, right = at;
                while (left < right && joiner(input.charAt(left))) left++;
                while (right > left && joiner(input.charAt(right - 1))) right--;
                boolean punctuated = false;
                StringBuilder candidate = new StringBuilder(right - left);
                for (int pos = left; pos < right; pos++) {
                    char value = input.charAt(pos);
                    if (joiner(value)) punctuated = true;
                    else candidate.append(value);
                }
                if (punctuated) {
                    String word = canonical(candidate.toString());
                    recovered = singleWords.get(word);
                    if (recovered == null) recovered = variant(word);
                    if (recovered != null && (recovered.weight < STRONG_WEIGHT || !EXPLICIT.contains(recovered.phrase)))
                        recovered = null;
                }
            }
            if (recovered != null) {
                if (result == null) result = new StringBuilder(input.length());
                result.append(input, copiedUntil, start).append(recovered.phrase);
                copiedUntil = at;
            }
        }
        return result == null ? input : result.append(input, copiedUntil, input.length()).toString();
    }

    private static boolean joiner(int value) {
        return value == '.' || value == '-' || value == '_' || value == '*' || value == 0x2022;
    }

    private static boolean compactCharacter(int point) {
        int type = Character.getType(point);
        return Character.isLetterOrDigit(point) || joiner(point) || type == Character.FORMAT
                || type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK;
    }

    private Entry variant(String token) {
        if (containsArabic(token)) for (String prefix : PREFIXES) {
            if (!token.startsWith(prefix)) continue;
            String rest = token.substring(prefix.length());
            Entry entry = singleWords.get(rest);
            if (rest.length() >= 3 && entry != null) return entry;
            break;
        }
        // Evasion aliases only recover an already configured reviewed strong
        // term. Never fold every number/homoglyph or arbitrary dictionary word.
        if (token.length() < 3 || token.length() > 32) return null;
        StringBuilder folded = new StringBuilder();
        for (int at = 0; at < token.length(); at++) {
            char value = token.charAt(at);
            switch (value) {
                case '0': value = 'o'; break;
                case '1': value = 'i'; break;
                case '3': value = 'e'; break;
                case '4': value = 'a'; break;
                case '7': value = 't'; break;
                case '\u043e': case '\u03bf': value = 'o'; break;
                case '\u0440': case '\u03c1': value = 'p'; break;
                case '\u0430': value = 'a'; break;
                case '\u0435': value = 'e'; break;
                case '\u0456': value = 'i'; break;
                case '\u04bb': value = 'h'; break;
                default: break;
            }
            folded.append(value);
        }
        String candidate = folded.toString();
        if (!EXPLICIT.contains(candidate)) return null;
        Entry entry = singleWords.get(candidate);
        return entry != null && entry.weight >= STRONG_WEIGHT ? entry : null;
    }

    private static boolean isPromotion(String token) {
        if (PROMOTION.contains(token)) return true;
        if (containsArabic(token)) for (String prefix : PREFIXES) {
            if (token.startsWith(prefix)) return PROMOTION.contains(token.substring(prefix.length()));
        }
        return false;
    }

    private static Set<String> immutableSet(String... values) {
        // Set.of is unavailable on Android 26 without library desugaring.
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    private static boolean containsArabic(String token) {
        for (int at = 0; at < token.length(); at++) {
            char value = token.charAt(at);
            if (value >= '\u0600' && value <= '\u06ff') return true;
        }
        return false;
    }

    private static String canonical(String input) {
        String compatible = Normalizer.normalize(input, Normalizer.Form.NFKC);
        String decomposed = Normalizer.normalize(compatible, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder();
        boolean separator = true;
        for (int at = 0; at < decomposed.length();) {
            int point = decomposed.codePointAt(at);
            at += Character.charCount(point);
            int kind = Character.getType(point);
            if (point == 0x0640 || kind == Character.FORMAT || kind == Character.NON_SPACING_MARK
                    || kind == Character.ENCLOSING_MARK) continue;
            if (point == 0x0629) point = 0x0647;
            else if (point == 0x0649) point = 0x064a;
            else if (point >= 0x0660 && point <= 0x0669) point = '0' + point - 0x0660;
            else if (point >= 0x06f0 && point <= 0x06f9) point = '0' + point - 0x06f0;
            if (Character.isLetterOrDigit(point)) {
                normalized.appendCodePoint(point);
                separator = false;
            } else if (!separator) {
                normalized.append(' ');
                separator = true;
            }
        }
        if (normalized.length() > 0 && normalized.charAt(normalized.length() - 1) == ' ') {
            normalized.setLength(normalized.length() - 1);
        }
        return Normalizer.normalize(normalized, Normalizer.Form.NFC);
    }

    private static final class Entry {
        final int id, weight;
        final String phrase;
        final boolean trusted;
        Entry(int id, String phrase, int weight, int words) {
            this.id = id;
            this.phrase = phrase;
            this.weight = weight;
            this.trusted = words > 1 || EXPLICIT.contains(phrase);
        }
    }

    private static final class Trie {
        final Map<String, Trie> children = new HashMap<>();
        Entry entry;
    }

    private static final class Hit {
        final Entry entry;
        final int start, end;
        Hit(Entry entry, int start, int end) { this.entry = entry; this.start = start; this.end = end; }
    }
}
