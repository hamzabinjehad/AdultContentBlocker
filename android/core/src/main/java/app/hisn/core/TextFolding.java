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
