package app.hisn.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AdultTextPolicyTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static AdultTextPolicy.Rule rule(String phrase, int weight) {
        return new AdultTextPolicy.Rule(phrase, weight);
    }

    private static void invalid(Runnable operation, String message) {
        try { operation.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        AdultTextPolicy policy = new AdultTextPolicy(Arrays.asList(
                rule("porn", 8), rule("porno", 8), rule("pornography", 8), rule("hentai", 8),
                rule("sex", 6), rule("pussy", 8), rule("onlyfans", 8), rule("adult videos", 8),
                rule("nude photos", 8), rule("اباحي", 8), rule("اباحيه", 8), rule("سكس", 8),
                rule("sexual health", -6), rule("sex education", -6), rule("breast cancer", -6),
                rule("porn addiction", -8), rule("التربيه الجنسيه", -6)));
        for (String sample : Arrays.asList("Watch porn", "FREE hentai now", "Download adult videos",
                "Stream nude photos", "Watch porn and hentai now", "Watch p0rn and hent41 now",
                "Watch p\u043ern", "Watch ｐｏｒｎ", "Watch p\u200bo\u00adrn", "شاهد الإباحية الآن",
                "شاهد إِبَاحِيّ", "تحميل سكس", "مشاهدة اباحي وسكس", "Watch PORN!!!",
                "لتحميل سكس", "لمشاهدة الإباحية")) {
            check(policy.blocks(sample), "adult promotion detected: " + sample);
        }
        for (String sample : Arrays.asList("", "A normal day", "Essex and sextants", "sex education",
                "Watch a sexual health presentation", "Download breast cancer research",
                "Watch sex education", "Research definitions include porn and hentai",
                "Watch porn addiction recovery resources", "Watch pussy cats playing",
                "A free OnlyFans budgeting guide", "Download photos of ordinary adults",
                "شاهد التربية الجنسية", "الجنسية في جواز السفر", "Download freeport information",
                "Watch pornographyresearch", "Watch p0rnographyresearch", "Watch xhent41y")) {
            check(!policy.blocks(sample), "benign/boundary sample stays available: " + sample);
        }
        check(!policy.blocks(null), "null sample is empty");
        for (String label : Arrays.asList("Porn videos", "p.o.r.n images", "محتوى إباحي", "صور اباحيه"))
            check(policy.blocks(label), "complete explicit media label detected: " + label);
        for (String label : Arrays.asList("Research about porn videos", "Porn videos addiction recovery",
                "Adult videos", "Sex photos", "محتوى إباحي في بحث طبي"))
            check(!policy.blocks(label), "label rule does not broaden to ambiguous prose");
        check(!new AdultTextPolicy(Collections.singletonList(rule("porn", 7))).blocks("Porn videos"),
                "media label cannot resurrect weak rules");
        check(!new AdultTextPolicy(Collections.singletonList(rule("hentai", 8))).blocks("Porn videos"),
                "media label cannot invent missing rules");
        check(!new AdultTextPolicy(Arrays.asList(rule("porn", 8), rule("porn videos", -8)))
                .blocks("Porn videos"), "negative context still applies to exact media labels");
        String gap = "ordinary ".repeat(40);
        check(policy.blocks("sexual health ".repeat(8) + gap + "Watch porn and hentai" + gap
                + "sex education ".repeat(8)), "distant benign context cannot cancel local promotion");
        check(!policy.blocks("Watch porn and hentai: sexual health"), "nearby clinical context subtracts evidence");
        check(policy.blocks("Watch porn and hentai: education"), "generic education is not a bypass");
        check(!policy.blocks("Watch " + gap + "porn and hentai"), "distant promotion cannot join signals");
        check(policy.blocks("Watch " + "ordinary ".repeat(14) + "porn"), "promotion at 16-token boundary");
        check(!policy.blocks("Watch " + "ordinary ".repeat(15) + "porn"), "promotion outside 16-token boundary");
        check(!policy.blocks("nude photos " + "ordinary ".repeat(14) + "watch"),
                "phrase crossing the left window boundary cannot provide evidence");
        check(!policy.blocks("Watch pussy pussy pussy"), "repeating ambiguous term is not diverse evidence");
        check(policy.blocks("Watch pussy and onlyfans"), "distinct strong promotional signals combine");
        check(!new AdultTextPolicy(Collections.singletonList(rule("porn", 7))).blocks("Watch p0rn"),
                "aliases do not resurrect reweighted weak terms");
        check(!new AdultTextPolicy(Collections.singletonList(rule("hentai", 8))).blocks("Watch p0rn"),
                "aliases do not invent unconfigured rules");
        for (String spelling : Arrays.asList("p.o.r.n", "p-o-r-n", "p_0_r_n", "p•o•r•n", "p***o***r***n")) {
            check(policy.blocks("Watch " + spelling), "configured compact spelling recovered");
            check(!new AdultTextPolicy(Collections.singletonList(rule("porn", 7))).blocks("Watch " + spelling),
                    "compact alias must not resurrect a weak term");
            check(!new AdultTextPolicy(Collections.singletonList(rule("hentai", 8))).blocks("Watch " + spelling),
                    "compact alias must not invent a term");
            check(!policy.blocks("Watch x" + spelling + "y"), "compact recovery is not substring matching");
        }
        for (String spelling : Arrays.asList("p o r n", "p  o  r  n", "p/o/r/n", "p / o / r / n", "p|o|r|n",
                "p,o,r,n", "p, o, r, n", "(p o r n)", "h e n t a i")) {
            check(policy.blocks("Watch " + spelling), "separated spelling recovered: " + spelling);
            check(!new AdultTextPolicy(Collections.singletonList(rule("porn", 7))).blocks("Watch " + spelling),
                    "separated alias must not resurrect a weak term");
        }
        // Whole runs only: initials, option lists, mixed separators, paths and longer runs stay apart.
        for (String sample : Arrays.asList("Watch a b c d", "Choose a, b, c or d", "Watch J. R. R. Tolkien",
                "Watch x p o r n", "Watch p o r n x", "Watch p/o r n", "Watch p o/r n", "Open /p/o/r/n/",
                "Open https://example.com/p/o/r/n", "Watch p/o/r/n/x", "Watch p o r t", "Watch s e x",
                "Watch po r n", "Watch p    o    r    n")) {
            check(!policy.blocks(sample), "separated recovery stays conservative: " + sample);
        }
        AdultTextPolicy arabic = new AdultTextPolicy(Arrays.asList(rule("سكس", 8), rule("نيك", 8),
                rule("بورنو", 8), rule("اباحي", 8)));
        // Nick, Borno (Nigeria) and couscous are ordinary words; alone they never confirm adult intent.
        for (String sample : Arrays.asList("شاهد نيك", "شاهد فيلم نيك كيج", "تحميل أخبار بورنو مجانا",
                "شاهد نيك بورنو", "طبق كسكس مجاني", "شاهد طريقة الكسكسي")) {
            check(!arabic.blocks(sample), "ambiguous Arabic word alone stays allowed: " + sample);
        }
        check(arabic.blocks("شاهد سكس"), "unambiguous Arabic term still detected");
        check(arabic.blocks("شاهد سكس نيك"), "ambiguous word still adds evidence beside a clear term");
        check(arabic.blocks("شاهد ا ب ا ح ي"), "separated Arabic letters recovered");
        check(!WordRuleList.compile("porn").matches("Download a free porn blocker").isEmpty(),
                "built-in benign contexts never exempt custom literal rules");
        check(!new AdultTextPolicy(Arrays.asList(rule("porn", 8), rule("porn", -8))).blocks("Watch porn"),
                "conflicting canonical context remains negative");
        AdultTextPolicy duplicates = new AdultTextPolicy(Arrays.asList(rule("pussy", 8), rule("PUSSY", 8)));
        check(duplicates.ruleCount() == 1 && !duplicates.blocks("Watch pussy"), "duplicate aliases never inflate evidence");
        check(!new AdultTextPolicy(Collections.emptyList()).blocks("Watch porn"), "empty policy stays inert");

        List<AdultTextPolicy.Rule> many = new ArrayList<>();
        for (int at = 0; at < AdultTextPolicy.MAX_RULES - 1; at++) many.add(rule("fixture" + at, 1));
        many.add(rule("porn", 8));
        AdultTextPolicy maximum = new AdultTextPolicy(many);
        check(maximum.ruleCount() == AdultTextPolicy.MAX_RULES, "maximum rule count accepted");
        String maximumText = "x".repeat(AdultTextPolicy.MAX_TEXT_CODEPOINTS - 11) + " watch porn";
        long began = System.nanoTime();
        check(maximum.blocks(maximumText), "match at maximum text boundary");
        long elapsed = System.nanoTime() - began;
        invalid(() -> maximum.blocks(maximumText + "x"), "oversized text accepted");
        invalid(() -> maximum.blocks("\ufb03".repeat(30000)), "expanded normalized text accepted");
        List<AdultTextPolicy.Rule> excessive = new ArrayList<>(many);
        excessive.add(rule("one more", 8));
        invalid(() -> new AdultTextPolicy(excessive), "too many input records accepted");
        invalid(() -> new AdultTextPolicy(Collections.singletonList(rule("x".repeat(129), 8))), "long phrase accepted");
        invalid(() -> new AdultTextPolicy(Collections.singletonList(rule("\ufb03".repeat(43), 8))), "expanded long phrase accepted");
        invalid(() -> new AdultTextPolicy(Collections.singletonList(rule("one two three four five six seven eight nine", 8))),
                "unbounded phrase token count accepted");
        invalid(() -> new AdultTextPolicy(Collections.singletonList(rule("---", 8))), "empty normalized rule accepted");
        invalid(() -> rule("porn", 0), "zero weight accepted");
        invalid(() -> rule("porn", 101), "unbounded weight accepted");
        invalid(() -> new AdultTextPolicy(null), "null iterable accepted");

        // Unicode normalization/candidate generation must never produce index
        // errors or exceed the fixed token/rule bounds on arbitrary input.
        java.util.Random random = new java.util.Random(19);
        String[] pieces = {"watch", "porn", "p0rn", "ordinary", "sexual", "health", "سكس", "شاهد",
                "p.o.r.n", "x.p.o.r.n.y", "h-e-n-t-4-1", "\u200b", "\ud83d\ude42", "\ufb03", "\u0651", ",", "\ud800"};
        for (int sample = 0; sample < 1000; sample++) {
            StringBuilder text = new StringBuilder();
            for (int at = 0; at < 40; at++) text.append(pieces[random.nextInt(pieces.length)]).append(' ');
            check(policy.blocks(text.toString()) == policy.blocks(text.toString()), "deterministic random sample");
        }
        checkSlidingWindows();
        if (args.length == 1) checkSeed(args[0]);
        System.out.println("AdultTextPolicy tests passed; " + AdultTextPolicy.MAX_RULES
                + " rules / " + AdultTextPolicy.MAX_TEXT_CODEPOINTS + " code points checked in "
                + (elapsed / 1_000_000L) + " ms (diagnostic, not a device speed guarantee)");
    }

    /** Optional bounded TSV adapter for testing the actual signed seed without
     * introducing a JSON dependency into this platform-independent core. */
    private static void checkSeed(String path) throws Exception {
        java.nio.file.Path source = java.nio.file.Paths.get(path);
        check(java.nio.file.Files.size(source) <= 4 * 1024 * 1024, "bounded seed fixture");
        List<AdultTextPolicy.Rule> rules = new ArrayList<>();
        for (String row : java.nio.file.Files.readAllLines(source, java.nio.charset.StandardCharsets.UTF_8)) {
            int tab = row.indexOf('\t');
            check(tab > 0, "seed TSV entry has weight");
            rules.add(rule(row.substring(tab + 1), Integer.parseInt(row.substring(0, tab))));
            check(rules.size() <= AdultTextPolicy.MAX_RULES, "bounded seed entry count");
        }
        rules.addAll(AdultTextPolicy.reviewedContextRules());
        AdultTextPolicy seed = new AdultTextPolicy(rules);
        for (String text : Arrays.asList("Watch porn and hentai", "Download adult videos", "شاهد الإباحية",
                "مشاهدة سكس", "Watch p0rn", "لتحميل اباحي")) {
            check(seed.blocks(text), "actual seed recognizes promotional fixture");
        }
        for (String text : Arrays.asList("Watch sex education", "شاهد التربية الجنسية", "Essex council",
                "Watch porn addiction recovery resources", "Download breast cancer research",
                "Research definitions include porn and hentai", "Watch pussy cats playing",
                "شاهد الجنسية في جواز السفر", "علاج ادمان الاباحيه", "Download sexual health advice")) {
            check(!seed.blocks(text), "actual seed keeps benign fixture available");
        }
        String gap = "ordinary ".repeat(40);
        check(seed.blocks("sexual health ".repeat(8) + gap + "Watch porn and hentai" + gap
                + "sex education ".repeat(8)), "actual seed local evidence survives distant negative prose");
        System.out.println("AdultTextPolicy actual seed passed: " + rules.size() + " submitted / "
                + seed.ruleCount() + " canonical rules");
    }

    private static void checkSlidingWindows() {
        List<AdultTextPolicy.Rule> rules = Arrays.asList(rule("porn", 8), rule("hentai", 8),
                rule("pussy", 8), rule("onlyfans", 8), rule("nude photos", 8),
                rule("photos", 2), rule("sexual health", -6), rule("education", -6));
        AdultTextPolicy policy = new AdultTextPolicy(rules);
        java.util.Random random = new java.util.Random(120);
        String[] words = {"ordinary", "ordinary", "ordinary", "ordinary", "ordinary", "ordinary",
                "porn", "hentai", "pussy", "onlyfans", "nude", "photos", "watch", "free",
                "education", "sexual", "health"};
        for (int at = 0; at < 2000; at++) {
            String[] sample = new String[5 + random.nextInt(80)];
            for (int token = 0; token < sample.length; token++) sample[token] = words[random.nextInt(words.length)];
            check(policy.blocks(String.join(" ", sample)) == referenceWindows(sample, rules),
                    "compiled sliding windows agree with direct bounded reference");
        }
    }

    /** Deliberately slow and separate from the production hit/count machinery. */
    private static boolean referenceWindows(String[] text, List<AdultTextPolicy.Rule> rules) {
        for (int end = 0; end < text.length; end++) {
            int left = Math.max(0, end - 15);
            boolean promotion = false;
            for (int at = left; at <= end; at++) promotion |= text[at].equals("watch") || text[at].equals("free");
            if (!promotion) continue;
            int positive = 0, negative = 0, strong = 0;
            boolean trusted = false;
            for (AdultTextPolicy.Rule rule : rules) {
                String[] phrase = rule.phrase.split(" ");
                boolean seen = false;
                for (int start = 0; start + phrase.length <= text.length && !seen; start++) {
                    int hitEnd = start + phrase.length - 1;
                    boolean eligible = rule.weight > 0 ? start >= left && hitEnd <= end
                            : hitEnd >= Math.max(0, end - 23) && hitEnd <= Math.min(text.length - 1, end + 8);
                    if (!eligible) continue;
                    boolean equal = true;
                    for (int token = 0; token < phrase.length; token++) equal &= phrase[token].equals(text[start + token]);
                    seen = equal;
                }
                if (!seen) continue;
                if (rule.weight < 0) negative -= rule.weight;
                else {
                    positive += rule.weight;
                    if (rule.weight >= 8) {
                        strong++;
                        trusted |= phrase.length > 1 || rule.phrase.equals("porn") || rule.phrase.equals("hentai");
                    }
                }
            }
            if (strong > 0 && (trusted || strong >= 2) && positive - negative >= (strong >= 2 ? 16 : 8)) return true;
        }
        return false;
    }
}
