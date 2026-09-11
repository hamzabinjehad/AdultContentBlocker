/**
 * The JavaScript third of a three-language normalisation contract.
 *
 * `blocklist/terms.py`'s `normalize()`, `macos/Hisn/TextNormalizer.swift` and
 * this file are three implementations of one function. Nothing at build time
 * forces them to agree, and a divergence fails in the worst possible way: a
 * signed term that is visibly present in the list and simply never matches on
 * the device. `blocklist/terms/normalize_cases.json` is the shared fixture
 * table all three test suites assert — the same device this repo already uses
 * for `test_collapse_lookup_invariant` on the domain list.
 *
 * ORDER IS PART OF THE CONTRACT, not an implementation detail. NFKC runs first
 * because it collapses the Arabic presentation forms (U+FB50–FDFF,
 * U+FE70–FEFF) that pasted text is full of, and every fold below assumes the
 * standard code points NFKC produces.
 *
 * Pure by design: no `chrome.*`, no DOM, no imports. That is what lets a test
 * runner import it, and what lets the content script and the service worker
 * share one copy.
 */

/** Bidi and joiner controls: invisible, survive a copy-paste, and the cheapest
 *  possible way to write a term that looks identical to a human and matches
 *  nothing at all. */
const INVISIBLE = new Set([
  0x200b, 0x200c, 0x200d, 0x200e, 0x200f, 0x061c, 0xfeff,
]);

/** Tatweel (kashida) — stretches a word without changing it. */
const TATWEEL = 0x0640;

/** Orthographic folds. Arabic writers are inconsistent about every one of
 *  these, and a list that distinguishes them matches about half of what it
 *  should. */
const FOLD = new Map([
  [0x0623, "ا"], [0x0625, "ا"], [0x0622, "ا"], [0x0671, "ا"], // أ إ آ ٱ
  [0x0649, "ي"], [0x0626, "ي"],                               // ى ئ
  [0x0624, "و"],                                              // ؤ
  [0x0629, "ه"],                                              // ة
]);

/** Tashkeel/harakat. Optional in written Arabic, so the same word appears with
 *  and without them and the two must compare equal. */
function isTashkeel(cp) {
  return (cp >= 0x064b && cp <= 0x065f) || cp === 0x0670
      || (cp >= 0x06d6 && cp <= 0x06ed);
}

/** Arabic-Indic (U+0660…) and Extended Arabic-Indic (U+06F0…) digits. */
function asciiDigit(cp) {
  if (cp >= 0x0660 && cp <= 0x0669) return String(cp - 0x0660);
  if (cp >= 0x06f0 && cp <= 0x06f9) return String(cp - 0x06f0);
  return null;
}

/**
 * Reduce text to the one spelling the term list is written in.
 *
 * Note what is deliberately NOT folded: ASCII digits. In romanised
 * Franco-Arabic they are letters (2→ء 3→ع 5→خ 7→ح 9→ص), so `3ahira` must
 * survive intact. Folding them would silently delete the entire `ar-latn`
 * tier — the tier that makes hostname matching work for Arabic domains at all.
 */
export function normalize(text) {
  if (!text) return "";

  let out = "";
  for (const ch of text.normalize("NFKC")) {
    const cp = ch.codePointAt(0);
    if (INVISIBLE.has(cp) || cp === TATWEEL || isTashkeel(cp)) continue;
    const folded = FOLD.get(cp);
    if (folded !== undefined) { out += folded; continue; }
    const digit = asciiDigit(cp);
    out += digit !== null ? digit : ch;
  }

  // Latin diacritics: decompose, drop the combining marks, recompose. Arabic
  // tashkeel is also category Mn but was removed above, so this is a no-op for
  // Arabic and does the work for "café" -> "cafe".
  return out
    .normalize("NFD")
    .replace(/\p{Mn}/gu, "")
    .normalize("NFC")
    .toLowerCase()
    .trim();
}

/**
 * Split already-normalised text into word tokens.
 *
 * Splits on the complement of letters and digits rather than on a word
 * boundary. `\b` is ASCII-word-based even with the `u` flag, so it does not
 * describe Arabic word boundaries at all; the complement rule needs no
 * per-language knowledge and behaves identically in all three languages.
 */
export function tokenize(text) {
  return text.split(/[^\p{L}\p{N}]+/u).filter(Boolean);
}

/** Bounded clitic affixes.
 *
 *  Arabic glues the definite article and prepositions onto the following word,
 *  so `الإباحية` has to match the listed term `اباحيه`. The stripping is
 *  BOUNDED — one prefix, one suffix, never recursive — because unbounded
 *  stemming is exactly how `جنس` (sex) starts matching `الجنسية` (nationality)
 *  and blocks every Arabic passport and visa page. */
const PREFIXES = ["وبال", "فبال", "وال", "بال", "فال", "كال",
                  "لل", "ال", "و", "ف", "ب", "ك", "ل"];
const SUFFIXES = ["ات", "ون", "ين", "ها", "هم", "هن", "ك", "ي"];

/** A token plus the affix-stripped forms worth also testing. */
export function variants(token) {
  const out = new Set([token]);

  for (const p of PREFIXES) {
    if (token.startsWith(p)) {
      const rest = token.slice(p.length);
      if (rest.length >= 3) out.add(rest);
      break;
    }
  }
  for (const s of SUFFIXES) {
    if (token.endsWith(s)) {
      const rest = token.slice(0, -s.length);
      if (rest.length >= 3) out.add(rest);
      break;
    }
  }
  // A TRAILING digit run is decoration ("sharmota123"); a leading or embedded
  // one is a Franco-Arabic letter ("3ahira") and must be left alone.
  const trimmed = token.replace(/[0-9]+$/, "");
  if (trimmed !== token && trimmed.length >= 3) out.add(trimmed);

  // Letter elongation: "pooorn", "seeexxx", "نيييك", "سكسسس" are the same word
  // held down on the keyboard, and one of the commonest ways spam writes an
  // explicit term so a literal list misses it. Only fires on a run of THREE or
  // more of the same character — ordinary words top out at doubles ("pass",
  // "الله"), and interjections like "hmmm"/"هههه" collapse below the 3-letter
  // floor and are dropped — so this practically never touches real prose.
  //
  // Emits BOTH reductions, because the list itself is written with real
  // doubles: collapsing every run to one letter reaches "porn" from "pooorn",
  // while collapsing to two preserves a listed term's own gemination so "ass"
  // is still reachable from "asssss". Matching stays exact against the list;
  // this only ever adds candidate spellings, it never loosens a comparison.
  if (/(.)\1\1/u.test(token)) {
    const one = token.replace(/(.)\1{2,}/gu, "$1");
    const two = token.replace(/(.)\1{2,}/gu, "$1$1");
    if (one.length >= 3) out.add(one);
    if (two.length >= 3) out.add(two);
  }

  return out;
}
