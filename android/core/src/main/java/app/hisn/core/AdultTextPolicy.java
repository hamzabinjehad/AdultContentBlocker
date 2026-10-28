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
 * Complete two-word explicit media labels can supply that intent without a verb, and so
 * can a short leaf of up to three words that are all lone explicit terms ("porn",
 * "#hentai", "سكس"), unless a protective word or negative phrase such as "porn addiction"
 * appears anywhere on the same screen ({@link #blocksAnyLine}).
 * Signed negative context subtracts evidence only within eight tokens of that
 * window; distant medical prose cannot cancel a separate promotional cluster.
 * Punctuated ("p.o.r.n") or separated ("p o r n", "p/o/r/n") spellings are joined
 * only as a whole run that spells a configured strong explicit term.
 * A few Arabic terms are also a common name, place or food. Any hit containing one,
 * including seed phrases such as "watch X", only adds evidence beside another
 * distinct unambiguous strong signal in the same window.
 * Franco-Arabic (Arabizi, "ar-latn") seed terms use the same rules. Most of its single
 * words are generated spellings that are also everyday chat ("97ab" is Gulf "friends"),
 * Hinglish ("abhi"), Turkish ("inek") or names ("Dara", "Nika"), so only a reviewed
 * list of single words counts as clear; every other one is contextual. A phrase made
 * only of words English also uses ("video sex", "sex arab") is contextual too, and so
 * is one holding the names "nik" or "neek", as نيك phrases are. As with the Arabic
 * اباحي, only the spellings of "pornographic" (aba7i, abahi, ebahia, ...) are trusted
 * alone; other clear single words (sharmota, sksy) need a second strong signal. Contextual Arabizi evidence joins a window only beside a clear Arabizi signal,
 * so names in English text cannot add up against "pussy cats" or recovery context.
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
        /** A Franco-Arabic (Arabizi, seed language "ar-latn") entry; see the class notes. */
        public final boolean arabizi;

        public Rule(String phrase, int weight) { this(phrase, weight, false); }

        public Rule(String phrase, int weight, boolean arabizi) {
            if (phrase == null || weight == 0 || weight < -100 || weight > 100) {
                throw new IllegalArgumentException("Invalid adult-text rule");
            }
            this.phrase = phrase;
            this.weight = weight;
            this.arabizi = arabizi;
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
            "handjob", "pornhub", "xvideos", "redtube", "youporn", "brazzers",
            "camgirl", "camgirls", "sexcam", "livesex", "sexchat", "milf", "buttplug",
            "titties", "deepthroat", "اباحي", "اباحيه", "اباحيات", "بورنو", "سكس",
            "سكسي", "سكسيه", "سيكس", "سكساوي", "نيك");
    // Explicit terms that mark adult content even alone in a short leaf. Not "pornography" or
    // "pornographic", which name the subject in encyclopedia headings, law and research.
    // "porno" is left out too: it is the generic noun in Spanish, Italian and German news and
    // the title of Irvine Welsh's novel; "Porno videos" and promotion still block.
    private static final Set<String> LONE = immutableSet("porn", "hentai", "blowjob", "cumshot",
            "creampie", "gangbang", "handjob", "اباحي", "اباحيه", "سكس");
    private static final int LONE_TOKENS = 3;
    // Words that mark a screen as about quitting, blocking, treating or studying adult content.
    // Any of them (or a configured negative phrase) on a screen stops a bare term from blocking
    // alone; a promotion still blocks. Specific words only, not "stop", "ban" or "block", which
    // are ordinary player and chat buttons.
    private static final Set<String> PROTECTIVE = immutableSet("anti", "against", "quit", "quitting",
            "blocker", "blockers", "blocking", "addiction", "addicted", "addict", "recovery", "recovering",
            "relapse", "detox", "nofap", "harms", "therapy", "research", "awareness", "accountability",
            "ضد", "ادمان", "علاج", "حجب", "حظر", "مكافحه", "تعافي", "اقلاع", "اضرار", "توعيه",
            "edman", "idman", "2edman", "3ilaj", "3elaj", "3laj");
    // Canonical tokens that are also Nick, Borno (Nigeria), Bourne, couscous or taboon
    // bread; كسكس and كسكسي otherwise fold to the prefix ك plus سكس/سكسي. Two of these
    // never confirm each other ("Nick Bourne" is a name). Latin "nik" and "neek" (niek, neik)
    // are the names Nik and Neek, and spell the ambiguous نيك, so seed phrases holding them
    // ("neek masri", "aflam neek") stay contextual like "نيك سعودي".
    private static final Set<String> AMBIGUOUS = immutableSet("نيك", "بورنو", "بورن", "كسكس", "كسكسي", "طابون",
            "nik", "neek", "niek", "neik");
    // The seed's spellings of "pornographic" (Arabic اباحي, اباحيه) with 7 or h, trusted alone like them.
    private static final Set<String> ARABIZI_EXPLICIT = immutableSet("aba7i", "aba7ia", "aba7iat", "aba7ih",
            "aba7it", "aba7y", "aba7ya", "aba7yat", "aba7yh", "aba7yt", "aba7e", "aba7ea", "aba7eat", "aba7eh",
            "aba7et", "alaba7ia", "alaba7ih", "alaba7ya", "alaba7yh", "alaba7ea", "alaba7eh", "abahi", "abahia",
            "abahiat", "abahih", "abahit", "abahy", "abahya", "abahyat", "abahyh", "abahyt", "abahe", "abahea",
            "abaheat", "abaheh", "abahet", "alabahia", "alabahih", "alabahya", "alabahyh", "alabahea", "alabaheh",
            "ebahia", "ebahiya", "ibahia", "ibahiya");
    // Reviewed Arabizi spellings with no everyday meaning: whore, porn(ographic), fucked,
    // prostitution, sexy (سكسي: sksy, sksi; not "sks", which is also Indonesian university
    // credits and a rifle). The seed's other ~800 generated single words stay contextual,
    // neek among them. Package-visible for tests.
    static final Set<String> ARABIZI_CLEAR = union(ARABIZI_EXPLICIT, "sharmota", "sharmoot", "sharameet",
            "shrmota", "shrmuta", "shrmot", "shrmut", "shramit", "shrmo6a", "shrm6a", "kahba", "kahbat",
            "gahba", "3ahra", "3ahira", "3ahrat", "3aherat", "arabsex", "sexarab", "sksarab", "mtnak", "mtnaka",
            "d3ara", "da3ara", "mnyok", "mnyuk", "sksy", "sksi", "sksya", "sksia", "sksyh", "sksih");
    // Latin words with ordinary English use, or common surnames. An Arabizi phrase made
    // only of these ("video sex", "sex arab", "sour sex") is contextual.
    private static final Set<String> ARABIZI_SHARED = immutableSet("sex", "film", "video", "sour", "soar",
            "arab", "arabi", "saudi", "masri", "masry", "souri", "hd", "massage", "aryan");
    private static final Set<String> PROMOTION = immutableSet("watch", "download", "stream",
            "streaming", "free", "شاهد", "مشاهده", "تحميل", "تنزيل", "مجانا", "مجاني",
            "tahmil", "ta7mil", "mojani", "majani", "majanan", "moshahda", "mushahada");
    private static final String[] PREFIXES = {"وبال", "فبال", "وال", "بال", "فال", "كال",
            "لل", "ال", "و", "ف", "ب", "ك", "ل"};
    private final Trie index = new Trie();
    private final List<Entry> entries;
    private final Map<String, Entry> singleWords;

    public AdultTextPolicy(Iterable<Rule> rules) {
        if (rules == null) throw new IllegalArgumentException("Missing adult-text rules");
        Map<String, Integer> weights = new LinkedHashMap<>();
        Set<String> arabizi = new HashSet<>();
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
            // A phrase also submitted as Arabizi keeps the more cautious Arabizi treatment.
            if (rule.arabizi) arabizi.add(phrase);
        }
        List<Entry> compiled = new ArrayList<>();
        Map<String, Entry> singles = new HashMap<>();
        for (Map.Entry<String, Integer> rule : weights.entrySet()) {
            String[] words = rule.getKey().split(" ");
            Entry entry = new Entry(compiled.size(), rule.getKey(), words, rule.getValue(),
                    arabizi.contains(rule.getKey()));
            compiled.add(entry);
            Trie branch = index;
            for (String word : words) branch = branch.children.computeIfAbsent(word, unused -> new Trie());
            branch.entry = entry;
            if (words.length == 1) singles.put(rule.getKey(), entry);
        }
        // A phrase also matches its no-space spelling: "nudephotos" is one
        // token, so an exact phrase trie alone would miss it. The alias points
        // at the SAME Entry, preserving its weight, ambiguity flag and window
        // counting; it never overrides a real single-word rule.
        Map<String, Entry> aliases = new HashMap<>();
        for (Entry entry : compiled) {
            String[] words = entry.phrase.split(" ");
            if (words.length < 2) continue;
            StringBuilder glued = new StringBuilder();
            boolean letters = true;
            for (String word : words) {
                for (int at = 0; at < word.length();) {
                    int point = word.codePointAt(at);
                    at += Character.charCount(point);
                    if (!Character.isLetterOrDigit(point)) { letters = false; break; }
                    glued.appendCodePoint(point);
                }
                if (!letters) break;
            }
            if (!letters) continue;
            String alias = glued.toString();
            if (alias.isEmpty() || alias.codePointCount(0, alias.length()) > MAX_RULE_CODEPOINTS
                    || singles.containsKey(alias) || aliases.containsKey(alias)) continue;
            Trie branch = index.children.get(alias);
            if (branch != null && branch.entry != null) continue;
            if (branch == null) {
                branch = new Trie();
                index.children.put(alias, branch);
            }
            branch.entry = entry;
            aliases.put(alias, entry);
            singles.put(alias, entry);
        }
        entries = Collections.unmodifiableList(compiled);
        singleWords = Collections.unmodifiableMap(singles);
    }

    public int ruleCount() { return entries.size(); }

    /** Classify a bounded sample. Oversized input is rejected, never silently truncated. */
    public boolean blocks(String input) {
        String[] tokens = tokens(input);
        return tokens != null && judge(tokens, true);
    }

    /** Classify each line of a screen sample (one visible label per line) on its own, as
     * {@link #blocks} does, so unrelated medical or news wording never exempts an adult
     * promotion elsewhere. A bare explicit term ("Porn", "سكس") counts as a label only when
     * no line on the screen names protective context: a "Porn" tag chip beside the headline
     * "Study links porn addiction to anxiety" is not a call to action. */
    public boolean blocksAnyLine(String sample) {
        if (sample == null || sample.isEmpty()) return false;
        checkLength(sample, "Adult-text sample exceeds inspection limit");
        String[] lines = sample.split("\n");
        String[][] tokens = new String[lines.length][];
        boolean lone = true;
        for (int at = 0; at < lines.length; at++) {
            tokens[at] = tokens(lines[at]);
            if (tokens[at] != null && protective(tokens[at])) lone = false;
        }
        for (String[] line : tokens) if (line != null && judge(line, lone)) return true;
        return false;
    }

    private static void checkLength(String text, String message) {
        if (text.codePointCount(0, text.length()) > MAX_TEXT_CODEPOINTS) throw new IllegalArgumentException(message);
    }

    /** Canonical tokens, or null when there is nothing to judge. */
    private String[] tokens(String input) {
        if (input == null || input.isEmpty()) return null;
        checkLength(input, "Adult-text sample exceeds inspection limit");
        if (entries.isEmpty()) return null;
        String normalized = canonical(recoverSeparatedSpellings(
                recoverCompactSpellings(Normalizer.normalize(input, Normalizer.Form.NFKC))));
        if (normalized.isEmpty()) return null;
        // NFKC can expand compatibility characters. Bound the normalized work
        // too, rather than permitting large hidden allocation after validation.
        checkLength(normalized, "Normalized adult-text sample exceeds inspection limit");
        return normalized.split(" ");
    }

    /** A protective word or a configured negative phrase anywhere in the tokens. */
    private boolean protective(String[] tokens) {
        for (int start = 0; start < tokens.length; start++) {
            if (inSet(PROTECTIVE, tokens[start])) return true;
            Trie branch = index;
            for (int end = start; end < Math.min(tokens.length, start + MAX_PHRASE_TOKENS); end++) {
                branch = branch.children.get(tokens[end]);
                if (branch == null) break;
                if (branch.entry != null && branch.entry.weight < 0) return true;
            }
        }
        return false;
    }

    private boolean judge(String[] tokens, boolean lone) {
        boolean[] promotions = new boolean[tokens.length];
        boolean anyPromotion = false;
        for (int at = 0; at < tokens.length; at++) {
            promotions[at] = isPromotion(tokens[at]);
            anyPromotion |= promotions[at];
        }
        // A complete two-word media label is evidence without a call to action.
        // Do not apply this to substrings of research/recovery prose or generic
        // "adult" labels; configured strong terms and negative context still score.
        if (!anyPromotion && (explicitMediaLabel(tokens) || (lone && loneExplicitLabel(tokens)))) {
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
                if (branch.entry != null) record(hits, starts, new Hit(branch.entry, start, end,
                        ambiguous(tokens[start], branch.entry)));
            }
            Entry variant = variant(tokens[start]);
            if (variant != null && !tokens[start].equals(variant.phrase)) {
                record(hits, starts, new Hit(variant, start, start, ambiguous(tokens[start], variant)));
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
        return entry != null && entry.weight >= STRONG_WEIGHT && EXPLICIT.contains(entry.phrase)
                && !ambiguous(explicit, entry);
    }

    /** A short leaf (a title, caption, button or search suggestion) of at most three words,
     * every one a lone explicit term, states its intent like a media label. Any other word
     * may be protective ("Porn, hentai blocker", "ضد سكس اباحي", "porn porn recovery"),
     * and only reviewed bigrams carry negative weight, so one other word is never allowed. */
    private boolean loneExplicitLabel(String[] tokens) {
        if (tokens.length > LONE_TOKENS) return false;
        for (String token : tokens) if (!loneExplicit(token)) return false;
        return true;
    }

    private boolean loneExplicit(String token) {
        // Only the bare word: a prefixed Arabic noun (الاباحيه, السكس) is how headings and
        // headlines name the subject, so it never stands alone.
        Entry entry = singleWords.get(token);
        if (entry == null && !containsArabic(token)) entry = variant(token);
        return entry != null && entry.weight >= STRONG_WEIGHT && !ambiguous(token, entry)
                && (LONE.contains(entry.phrase) || (entry.arabizi && ARABIZI_EXPLICIT.contains(entry.phrase)));
    }

    /** The token covers a folded spelling such as كسكس; the entry covers whole phrases. */
    private static boolean ambiguous(String token, Entry entry) {
        return entry.ambiguous || AMBIGUOUS.contains(token);
    }

    private boolean classifyWindows(List<Hit>[] hits, List<Hit>[] starts, boolean[] promotions) {
        int[] clearCounts = new int[entries.size()];
        int[] ambiguousCounts = new int[entries.size()];
        int[] negativeCounts = new int[entries.size()];
        // Each positive entry counts once: as clear evidence, or else as contextual
        // evidence that only joins a window already holding a clear strong signal
        // (a clear strong Arabizi signal for contextual Arabizi evidence).
        Evidence clear = new Evidence(), contextual = new Evidence(), latin = new Evidence();
        int negativeScore = 0, promotion = 0;
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
                if (entry.weight <= 0) continue;
                Evidence context = entry.arabizi ? latin : contextual;
                if (hit.ambiguous) {
                    if (ambiguousCounts[entry.id]++ == 0 && clearCounts[entry.id] == 0) context.add(entry, 1);
                } else if (clearCounts[entry.id]++ == 0) {
                    clear.add(entry, 1);
                    if (ambiguousCounts[entry.id] > 0) context.add(entry, -1);
                }
            }
            if (end >= WINDOW && starts[end - WINDOW] != null) for (Hit hit : starts[end - WINDOW]) {
                Entry entry = hit.entry;
                Evidence context = entry.arabizi ? latin : contextual;
                if (hit.ambiguous) {
                    if (--ambiguousCounts[entry.id] == 0 && clearCounts[entry.id] == 0) context.add(entry, -1);
                } else if (--clearCounts[entry.id] == 0) {
                    clear.add(entry, -1);
                    if (ambiguousCounts[entry.id] > 0) context.add(entry, 1);
                }
            }
            int with = clear.strong > 0 ? 1 : 0, withLatin = clear.arabizi > 0 ? 1 : 0;
            int strong = clear.strong + with * contextual.strong + withLatin * latin.strong;
            int trusted = clear.trusted + with * contextual.trusted + withLatin * latin.trusted;
            int positiveScore = clear.score + with * contextual.score + withLatin * latin.score;
            // A contextual phrase must earn its signed weight; neither one
            // generic "education" nor distant benign paragraphs grant immunity.
            int threshold = strong >= 2 ? 16 : 8;
            if (promotion > 0 && strong > 0 && (trusted > 0 || strong >= 2)
                    && positiveScore - negativeScore >= threshold) return true;
        }
        return false;
    }

    private static final class Evidence {
        int score, strong, trusted, arabizi;
        void add(Entry entry, int sign) {
            score += sign * entry.weight;
            if (entry.weight >= STRONG_WEIGHT) {
                strong += sign;
                if (entry.trusted) trusted += sign;
                if (entry.arabizi) arabizi += sign;
            }
        }
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
            String recovered = null;
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
                if (punctuated) recovered = recoveredTerm(canonical(candidate.toString()));
            }
            if (recovered != null) {
                if (result == null) result = new StringBuilder(input.length());
                result.append(input, copiedUntil, start).append(recovered);
                copiedUntil = at;
            }
        }
        return result == null ? input : result.append(input, copiedUntil, input.length()).toString();
    }

    /** The configured strong explicit term a joined spelling stands for, or null. An
     * ambiguous word keeps its own spelling so scoring still requires adult context. */
    private String recoveredTerm(String word) {
        Entry entry = singleWords.get(word);
        if (entry == null) entry = variant(word);
        if (entry == null || entry.weight < STRONG_WEIGHT || !EXPLICIT.contains(entry.phrase)) return null;
        return ambiguous(word, entry) ? word : entry.phrase;
    }

    /** Join one whole run of 3-32 single letters split by one repeated separator
     * (1-3 spaces, or "/", "|" or "," with at most one space each side) only when it
     * spells a strong explicit term. Runs beside another single letter, a different
     * separator, or path/URL punctuation stay unjoined; whole words never join. */
    private String recoverSeparatedSpellings(String input) {
        StringBuilder result = null;
        int copiedUntil = 0, length = input.length();
        int first = -1, last = -1, units = 0, leftFrom = 0, separatorAt = 0, separatorLength = 0;
        int previousEnd = 0, symbol = 0;
        boolean previousUnit = false, leftWord = false, leftUnit = false;
        for (int at = 0; ; ) {
            while (at < length && !unitLetter(input.codePointAt(at))) at += Character.charCount(input.codePointAt(at));
            int start = at, letters = 0;
            while (at < length) {
                int point = input.codePointAt(at);
                if (unitLetter(point)) letters++;
                else if (!attached(point)) break;
                at += Character.charCount(point);
            }
            boolean unit = letters == 1;
            if (unit && previousUnit) {
                int gap = separator(input, previousEnd, start);
                if (units == 1 && gap != 0) {
                    symbol = gap; separatorAt = previousEnd; separatorLength = start - previousEnd;
                }
                if (gap != 0 && start - previousEnd == separatorLength
                        && input.regionMatches(previousEnd, input, separatorAt, separatorLength)) {
                    units++; last = at;
                } else {
                    // Two single letters split differently: neither part is a whole run.
                    first = start; last = at; units = 1; leftFrom = previousEnd; leftWord = false; leftUnit = true;
                }
            } else if (unit) {
                first = start; last = at; units = 1; leftFrom = previousEnd; leftWord = previousEnd > 0; leftUnit = false;
            } else {
                if (previousUnit && units >= 3 && units <= 32 && last - first <= 512 && !leftUnit
                        && edge(input, leftFrom, first, !leftWord, true, symbol)
                        && edge(input, last, start, start >= length, false, symbol)) {
                    StringBuilder joined = new StringBuilder(last - first);
                    for (int pos = first; pos < last; ) {
                        int point = input.codePointAt(pos);
                        if (unitLetter(point) || attached(point)) joined.appendCodePoint(point);
                        pos += Character.charCount(point);
                    }
                    String recovered = recoveredTerm(canonical(joined.toString()));
                    if (recovered != null) {
                        if (result == null) result = new StringBuilder(length);
                        result.append(input, copiedUntil, first).append(recovered);
                        copiedUntil = last;
                    }
                }
                units = 0;
            }
            previousUnit = unit;
            previousEnd = at;
            if (start >= length) break;
        }
        return result == null ? input : result.append(input, copiedUntil, length).toString();
    }

    /** The separator symbol for an allowed gap between two single letters, else 0. */
    private static int separator(String input, int from, int to) {
        int length = to - from, spaces = 0;
        while (spaces < length && input.charAt(from + spaces) == ' ') spaces++;
        if (spaces == length) return length <= 3 ? ' ' : 0;
        char value = input.charAt(from + spaces);
        if (spaces > 1 || (value != '/' && value != '|' && value != ',')) return 0;
        int after = length - spaces - 1;
        return after == 0 || (after == 1 && input.charAt(to - 1) == ' ') ? value : 0;
    }

    /** A run's outer gap must reach whitespace or the text edge, and may touch the run
     * only with brackets, quotes or sentence punctuation, never its own separator. */
    private static boolean edge(String input, int from, int to, boolean textEdge, boolean left, int symbol) {
        boolean space = false;
        for (int pos = left ? to - 1 : from; pos >= from && pos < to; pos += left ? -1 : 1) {
            char value = input.charAt(pos);
            if (value == symbol && symbol != ' ') return false;
            if (Character.isWhitespace(value)) space = true;
            else if (!space && "()[]{}\"'«»“”‘’.,!?;:".indexOf(value) < 0) return false;
        }
        return space || textEdge;
    }

    private static boolean unitLetter(int point) {
        return Character.isLetterOrDigit(point) && point != 0x0640;
    }

    private static boolean attached(int point) {
        int type = Character.getType(point);
        return point == 0x0640 || type == Character.FORMAT || type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK;
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

    private static boolean isPromotion(String token) { return inSet(PROMOTION, token); }

    /** The token, or an Arabic token without one leading prefix (الادمان, وعلاج), is in the set. */
    private static boolean inSet(Set<String> words, String token) {
        if (words.contains(token)) return true;
        if (containsArabic(token)) for (String prefix : PREFIXES) {
            if (token.startsWith(prefix)) return words.contains(token.substring(prefix.length()));
        }
        return false;
    }

    private static Set<String> immutableSet(String... values) {
        // Set.of is unavailable on Android 26 without library desugaring.
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    private static Set<String> union(Set<String> base, String... values) {
        Set<String> all = new HashSet<>(base);
        all.addAll(Arrays.asList(values));
        return Collections.unmodifiableSet(all);
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
            if (TextFolding.ignored(point)) continue;
            point = TextFolding.fold(point);
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
        final boolean trusted, ambiguous, arabizi;
        Entry(int id, String phrase, String[] words, int weight, boolean arabizi) {
            this.id = id;
            this.phrase = phrase;
            this.weight = weight;
            this.arabizi = arabizi;
            this.trusted = words.length > 1 || EXPLICIT.contains(phrase)
                    || (arabizi && ARABIZI_EXPLICIT.contains(phrase));
            boolean shared = false, everyday = true;
            for (String word : words) {
                shared |= AMBIGUOUS.contains(word);
                everyday &= ARABIZI_SHARED.contains(word);
            }
            boolean clearArabizi = words.length == 1
                    ? ARABIZI_CLEAR.contains(phrase) || EXPLICIT.contains(phrase) : !everyday;
            this.ambiguous = shared || (arabizi && !clearArabizi);
        }
    }

    private static final class Trie {
        final Map<String, Trie> children = new HashMap<>();
        Entry entry;
    }

    private static final class Hit {
        final Entry entry;
        final int start, end;
        final boolean ambiguous;
        Hit(Entry entry, int start, int end, boolean ambiguous) {
            this.entry = entry; this.start = start; this.end = end; this.ambiguous = ambiguous;
        }
    }
}
