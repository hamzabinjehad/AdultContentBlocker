package app.hisn.core;

/** The Android fold table matches the shared Python/JavaScript/Swift contract. */
public final class TextFoldingTest {
    public static void main(String[] args) {
        check(TextFolding.fold('\u0623') == '\u0627', "hamza alef");
        check(TextFolding.fold('\u0625') == '\u0627', "hamza below alef");
        check(TextFolding.fold('\u0622') == '\u0627', "madda alef");
        check(TextFolding.fold('\u0671') == '\u0627', "wasla alef");
        check(TextFolding.fold('\u0649') == '\u064a', "alef maqsura");
        check(TextFolding.fold('\u0626') == '\u064a', "hamza yeh");
        check(TextFolding.fold('\u06cc') == '\u064a', "farsi yeh");
        check(TextFolding.fold('\u06d2') == '\u064a', "yeh barree");
        check(TextFolding.fold('\u0624') == '\u0648', "hamza waw");
        check(TextFolding.fold('\u0629') == '\u0647', "taa marbuta");
        check(TextFolding.fold('\u06c1') == '\u0647', "heh goal");
        check(TextFolding.fold('\u06a9') == '\u0643', "keheh");
        check(TextFolding.fold('\u06ba') == '\u0646', "noon ghunna");
        check(TextFolding.fold('\u0663') == '3', "Arabic-Indic digit");
        check(TextFolding.fold('\u06f3') == '3', "extended Arabic-Indic digit");
        check(TextFolding.fold('3') == '3', "ASCII digit unchanged");
        check(TextFolding.ignored('\u0640'), "tatweel ignored");
        check(TextFolding.ignored('\u200b'), "zero-width space ignored");
        check(TextFolding.ignored('\u0651'), "shadda ignored");
        check(!TextFolding.ignored('\u0627'), "ordinary alef is not ignored");
        check(!TextFolding.ignored('\u067e'), "peh is a different letter and is not folded");
        check(TextFolding.fold('\u067e') == '\u067e', "peh stays peh");
        check(TextFolding.fold('\u0686') == '\u0686', "tcheh stays tcheh");
        checkQuickCanonical();
        System.out.println("TextFolding tests passed: Arabic/Persian/Urdu folds, digits, invisible controls"
                + " and the normalizer fast path");
    }

    /** The fast path must equal the three-pass reference for every text it accepts. */
    private static void checkQuickCanonical() {
        // Bases that compose with madda/hamza marks under NFKC, the marks themselves, the
        // precomposed hamza letters, punctuation, digits and every ASCII char.
        String arabic = "\u0621\u0622\u0623\u0624\u0625\u0626\u0627\u0628\u0629\u062a\u0633\u0643"
                + "\u0640\u0641\u0646\u0647\u0648\u0649\u064a\u064b\u064e\u0651\u0652\u0653\u0654"
                + "\u0655\u0656\u065f\u0660\u0663\u0669\u060c\u061b\u061f";
        java.util.Random random = new java.util.Random(20261112L);
        for (int round = 0; round < 200000; round++) {
            StringBuilder text = new StringBuilder();
            int length = random.nextInt(24);
            for (int i = 0; i < length; i++) {
                text.append(random.nextInt(3) == 0 ? (char) random.nextInt(0x80)
                        : arabic.charAt(random.nextInt(arabic.length())));
            }
            String input = text.toString();
            String quick = TextFolding.quickCanonical(input);
            check(quick != null, "accepted alphabet");
            check(quick.equals(AdultTextPolicy.canonicalSlow(input)), "adult fast path: " + escape(input));
            check(quick.equals(WordMatcher.normalizeSlow(input)), "word fast path: " + escape(input));
        }
        for (String declined : new String[]{"caf\u00e9", "\u00bd", "\uff21", "\ufeeb", "\u06cc", "\u200b", "\ud83d\ude00"}) {
            check(TextFolding.quickCanonical("a " + declined) == null, "needs the reference path: " + escape(declined));
        }
        check(TextFolding.ascii("Plain label 123") && !TextFolding.ascii("Caf\u00e9"), "ASCII detection");
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) out.append(c < 0x80 && c >= 0x20 ? String.valueOf(c) : String.format("\\u%04x", (int) c));
        return out.toString();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
