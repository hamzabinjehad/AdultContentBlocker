import Foundation

/// The Swift third of a three-language normalisation contract.
///
/// `blocklist/terms.py`'s `normalize()`, `extension/lib/normalize.js` and this
/// type are three implementations of one function. Nothing at build time forces
/// them to agree, and a divergence fails in the worst possible way: a signed
/// term that is visibly present in the list and simply never matches on the
/// device. `blocklist/terms/normalize_cases.json` is the shared fixture table
/// all three test suites assert — the same device this repo already uses for
/// `test_collapse_lookup_invariant` on the domain list.
///
/// Order is part of the contract, not an implementation detail. NFKC runs
/// first because it collapses the Arabic presentation forms (U+FB50–FDFF,
/// U+FE70–FEFF) that pasted text is full of, and every fold below assumes the
/// standard code points NFKC produces.
public enum TextNormalizer {

    /// Bidirectional and joiner controls: invisible, survive a copy-paste, and
    /// the cheapest possible way to write a term that looks identical to a
    /// human and matches nothing at all.
    private static let invisible: Set<UInt32> = [
        0x200B, 0x200C, 0x200D, 0x200E, 0x200F, 0x061C, 0xFEFF,
    ]

    /// Tatweel (kashida) — stretches a word without changing it.
    private static let tatweel: UInt32 = 0x0640

    /// Orthographic folds. Arabic writers are inconsistent about every one of
    /// these, and a list that distinguishes them matches about half of what it
    /// should.
    private static let folds: [UInt32: Character] = [
        0x0623: "ا", 0x0625: "ا", 0x0622: "ا", 0x0671: "ا",   // أ إ آ ٱ
        0x0649: "ي", 0x0626: "ي",                             // ى ئ
        0x0624: "و",                                          // ؤ
        0x0629: "ه",                                          // ة
    ]

    /// True for tashkeel/harakat. Optional in written Arabic, so the same word
    /// appears with and without them and the two must compare equal.
    @inline(__always)
    private static func isTashkeel(_ v: UInt32) -> Bool {
        (0x064B...0x065F).contains(v) || v == 0x0670 || (0x06D6...0x06ED).contains(v)
    }

    /// Arabic-Indic (U+0660…) and Extended Arabic-Indic (U+06F0…) digits.
    @inline(__always)
    private static func asciiDigit(_ v: UInt32) -> Character? {
        if (0x0660...0x0669).contains(v) {
            return Character(UnicodeScalar(0x30 + (v - 0x0660))!)
        }
        if (0x06F0...0x06F9).contains(v) {
            return Character(UnicodeScalar(0x30 + (v - 0x06F0))!)
        }
        return nil
    }

    /// Reduce text to the one spelling the term list is written in.
    ///
    /// Note what is deliberately NOT folded: ASCII digits. In romanised
    /// Franco-Arabic they are letters (2→ء 3→ع 5→خ 7→ح 9→ص), so `3ahira` must
    /// survive intact. Folding them would silently delete the entire
    /// `ar-latn` tier, which is the tier that makes hostname matching work for
    /// Arabic-audience domains at all.
    public static func normalize(_ text: String) -> String {
        guard !text.isEmpty else { return "" }

        var out = String.UnicodeScalarView()
        for scalar in text.precomposedStringWithCompatibilityMapping.unicodeScalars {
            let v = scalar.value
            if invisible.contains(v) || v == tatweel || isTashkeel(v) { continue }
            if let folded = folds[v] {
                out.append(contentsOf: String(folded).unicodeScalars)
            } else if let digit = asciiDigit(v) {
                out.append(contentsOf: String(digit).unicodeScalars)
            } else {
                out.append(scalar)
            }
        }

        // Latin diacritics: decompose, drop the combining marks, recompose.
        // Arabic tashkeel is also category Mn but was removed above, so this
        // step is a no-op for Arabic and does the work for "café" → "cafe".
        let stripped = String(String(out).decomposedStringWithCanonicalMapping
            .unicodeScalars
            .filter { $0.properties.generalCategory != .nonspacingMark })

        return stripped.precomposedStringWithCanonicalMapping
            .lowercased()
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Split already-normalised text into word tokens.
    ///
    /// Splits on the complement of letters and digits rather than on a word
    /// boundary. `\b` — and `CharacterSet.alphanumerics` used naively — do not
    /// describe Arabic word boundaries; the complement rule needs no
    /// per-language knowledge and behaves identically in all three languages.
    public static func tokenize(_ text: String) -> [String] {
        text.split { scalar in
            !(scalar.isLetter || scalar.isNumber)
        }.map(String.init)
    }

    /// Bounded clitic affixes.
    ///
    /// Arabic glues the definite article and prepositions onto the following
    /// word, so `الإباحية` has to match the listed term `اباحيه`. The stripping
    /// is BOUNDED — one prefix, one suffix, never recursive — because unbounded
    /// stemming is exactly how `جنس` (sex) starts matching `الجنسية`
    /// (nationality) and blocks every Arabic passport and visa page.
    private static let prefixes = ["وبال", "فبال", "وال", "بال", "فال", "كال",
                                   "لل", "ال", "و", "ف", "ب", "ك", "ل"]
    private static let suffixes = ["ات", "ون", "ين", "ها", "هم", "هن", "ك", "ي"]

    /// A token plus the affix-stripped forms worth also testing.
    public static func variants(of token: String) -> Set<String> {
        var out: Set<String> = [token]

        for prefix in prefixes where token.hasPrefix(prefix) {
            let rest = String(token.dropFirst(prefix.count))
            if rest.count >= 3 { out.insert(rest) }
            break
        }
        for suffix in suffixes where token.hasSuffix(suffix) {
            let rest = String(token.dropLast(suffix.count))
            if rest.count >= 3 { out.insert(rest) }
            break
        }
        // A TRAILING digit run is hostname decoration ("sharmota123"); a
        // leading or embedded one is a Franco-Arabic letter ("3ahira") and must
        // be left alone.
        let trimmed = String(token.reversed().drop { $0.isNumber }.reversed())
        if trimmed != token, trimmed.count >= 3 { out.insert(trimmed) }

        // Letter elongation: "pooorn", "نيييك" are the same word held down on
        // the keyboard. `collapseElongation` only alters a token that carries a
        // run of three or more identical characters (ordinary words top out at
        // doubles), so `one != token` is the presence test. Both the one- and
        // two-letter reductions are kept, so a term's real gemination survives
        // ("ass" from "asssss") while "porn" is still reached from "pooorn".
        // Identical to normalize.js `variants` and terms.py `token_variants`.
        let one = collapseElongation(token, keep: 1)
        if one != token {
            if one.count >= 3 { out.insert(one) }
            let two = collapseElongation(token, keep: 2)
            if two.count >= 3 { out.insert(two) }
        }

        return out
    }

    /// Reduce every run of three-or-more identical characters to `keep` of them,
    /// leaving runs of one or two untouched. Compares by `Character`, so Arabic
    /// letters (single grapheme clusters) collapse the same way ASCII does.
    private static func collapseElongation(_ token: String, keep: Int) -> String {
        var out = ""
        var i = token.startIndex
        while i < token.endIndex {
            let ch = token[i]
            var j = token.index(after: i)
            var count = 1
            while j < token.endIndex, token[j] == ch {
                count += 1
                j = token.index(after: j)
            }
            out += String(repeating: ch, count: count >= 3 ? keep : count)
            i = j
        }
        return out
    }
}
