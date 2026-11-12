package app.hisn.core;

/**
 * The Android side of the shared Arabic normalisation folds.
 *
 * The signed term list is normalised by `blocklist/terms.py`; the browser uses
 * `extension/lib/normalize.js` and the Mac uses `TextNormalizer.swift`. The
 * Android matchers must apply the same orthographic folds or a term that is
 * visibly present in the signed list never matches on the phone.
 *
 * NFKC/NFD + combining-mark removal already handles the hamza forms (أ إ آ ؤ ئ
 * ٱ) by decomposing the mark. This class covers the folds decomposition cannot
 * do: taa marbuta, alef maqsura, Arabic-Indic digits, and the Persian/Urdu
 * spellings of the same Arabic letters. Do not add peh/tcheh/gaf/jeh: those are
 * different letters, not spelling variants.
 */
final class TextFolding {
    private TextFolding() { }

    /** Fold one code point to the spelling the signed list uses. */
    static int fold(int point) {
        switch (point) {
            case 0x0623: case 0x0625: case 0x0622: case 0x0671: return 0x0627; // أ إ آ ٱ
            case 0x0649: case 0x0626: case 0x06CC: case 0x06D2: case 0x06D3: // ى ئ ی ے ۓ
                return 0x064A;
            case 0x0624: return 0x0648;                                      // ؤ
            case 0x0629: case 0x06C1: case 0x06C3: return 0x0647;             // ة ہ ۃ
            case 0x06A9: return 0x0643;                                      // ک
            case 0x06BA: return 0x0646;                                      // ں
            default: break;
        }
        if (point >= 0x0660 && point <= 0x0669) return '0' + (point - 0x0660);
        if (point >= 0x06F0 && point <= 0x06F9) return '0' + (point - 0x06F0);
        return point;
    }

    /** True when every char is ASCII; NFKC, NFD and NFC all leave such text unchanged. */
    static boolean ascii(CharSequence text) {
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) >= 0x80) return false;
        return true;
    }

    /**
     * The canonical word form ({@code AdultTextPolicy.canonical} and
     * {@code WordMatcher.normalize}) computed without the three Unicode normalizer passes,
     * or null when the text needs them. Screen labels are overwhelmingly ASCII or plain
     * Arabic, and on a phone the passes cost more than the matching itself.
     *
     * Accepted: ASCII, the Arabic comma, semicolon and question mark, the letters
     * U+0621-U+063A and U+0641-U+064A, tatweel, the marks U+064B-U+065F and the
     * Arabic-Indic digits. On these the reference path reduces to the loop below: NFKC
     * may compose alef/waw/yeh with a hamza or madda mark, NFD splits the result again,
     * and every mark is then dropped, so the base letter survives either way; the
     * precomposed hamza letters fold to the same base letters here. None of the kept
     * output composes under NFC, and no accepted char is a spacing mark. A fuzz test
     * checks this against the reference path.
     */
    static String quickCanonical(CharSequence input) {
        int length = input.length();
        StringBuilder out = null;
        boolean separator = true;
        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);
            if (!quick(c)) return null;
            if (out == null) out = new StringBuilder(length);
            if (c >= 'A' && c <= 'Z') c = (char) (c + ('a' - 'A'));
            if (c >= 0x80 && ignored(c)) continue;
            int point = c < 0x80 ? c : fold(c);
            boolean word = point < 0x80
                    ? (point >= 'a' && point <= 'z') || (point >= '0' && point <= '9')
                    : Character.isLetterOrDigit(point);
            if (word) {
                out.append((char) point);
                separator = false;
            } else if (!separator) {
                out.append(' ');
                separator = true;
            }
        }
        if (out == null) return "";
        int end = out.length();
        if (end > 0 && out.charAt(end - 1) == ' ') out.setLength(end - 1);
        return out.toString();
    }

    private static boolean quick(char c) {
        return c < 0x80 || c == 0x060C || c == 0x061B || c == 0x061F
                || (c >= 0x0621 && c <= 0x063A) || (c >= 0x0640 && c <= 0x0669);
    }

    /**
     * Format controls, combining marks, tatweel and invisible fillers are not
     * part of a word. Soft hyphen, zero-width joiners, bidi controls and
     * variation selectors are all in this set or have a mark/format category.
     */
    static boolean ignored(int point) {
        int type = Character.getType(point);
        return point == 0x0640
                || point == 0x115F || point == 0x1160 || point == 0x3164 || point == 0xFFA0
                || type == Character.FORMAT
                || type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }
}
