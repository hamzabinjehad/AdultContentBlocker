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
        System.out.println("TextFolding tests passed: Arabic/Persian/Urdu folds, digits and invisible controls");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
