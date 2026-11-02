/**
 * Page-language detection and vocabulary selection.
 *
 * WHY THIS EXISTS
 * ---------------
 * The compiled vocabulary carries a language tag per term (`en`, `ar`,
 * `ar-latn`, `de`, `es`, `fr`, `it`, `pt`). Before this module the scorer
 * ignored that tag and matched every language against every page. That is
 * safe but noisy: a weak German or Spanish word can add evidence to an
 * English page that never meant it.
 *
 * The rule here is deliberately NOT "only the declared language may match".
 * A page controls its own `<html lang>`, so language-exclusive matching would
 * be a one-attribute bypass: set `lang="en"` on an Arabic page and the Arabic
 * vocabulary disappears. Instead:
 *
 *   * script evidence overrides a declared Latin language when the rendered
 *     text is dominantly Arabic;
 *   * the detected language's terms and English (the common base) are used;
 *   * terms with a strong explicit weight are shared across languages, so an
 *     explicit adult label still blocks wherever it appears;
 *   * no confident language means the full vocabulary, exactly as before.
 *
 * None of this is classification by itself. The scorer still requires the
 * threshold and density rules; this only chooses which reviewed words are
 * relevant to the page in front of the reader.
 */

export const SUPPORTED_LANGUAGES = Object.freeze(["en", "ar", "de", "es", "fr", "it", "pt"]);

/** Tags that are close enough to a supported language for vocabulary choice. */
const ALIASES = Object.freeze({
  "ar-latn": "ar", "ar-latin": "ar", "arabizi": "ar",
  "en-us": "en", "en-gb": "en", "en-au": "en", "en-ca": "en",
  "de-de": "de", "de-at": "de", "de-ch": "de",
  "es-es": "es", "es-mx": "es", "es-ar": "es", "es-419": "es",
  "fr-fr": "fr", "fr-ca": "fr", "fr-be": "fr",
  "it-it": "it", "pt-pt": "pt", "pt-br": "pt",
});

const NAMES = Object.freeze({
  en: { en: "English", ar: "الإنجليزية" },
  ar: { en: "Arabic", ar: "العربية" },
  de: { en: "German", ar: "الألمانية" },
  es: { en: "Spanish", ar: "الإسبانية" },
  fr: { en: "French", ar: "الفرنسية" },
  it: { en: "Italian", ar: "الإيطالية" },
  pt: { en: "Portuguese", ar: "البرتغالية" },
});

/** A bare supported language code, or null. Handles `en_US`, `ar-EG`,
 *  `de-DE,de;q=0.9` and the Arabizi tags the seed uses. */
export function baseLanguage(tag) {
  if (typeof tag !== "string") return null;
  const first = tag.trim().toLowerCase().replace(/_/g, "-").split(/[;,]/)[0].trim();
  if (!first) return null;
  if (ALIASES[first]) return ALIASES[first];
  const base = first.split("-")[0];
  return SUPPORTED_LANGUAGES.includes(base) ? base : null;
}

/** A readable name in the extension UI's language. */
export function languageName(tag, uiLanguage = "en") {
  const code = baseLanguage(tag);
  if (!code) return uiLanguage === "ar" ? "غير معروفة" : "Unknown";
  return NAMES[code]?.[uiLanguage === "ar" ? "ar" : "en"] ?? code;
}

/** Letter counts for the scripts that can change the vocabulary decision.
 *  Digits, punctuation and symbols are ignored — including Arabic-Indic digits
 *  and Arabic punctuation, which also carry the Arabic script property. */
export function scriptCounts(sample) {
  let arabic = 0, latin = 0;
  for (const ch of String(sample || "")) {
    if (!/\p{L}/u.test(ch)) continue;
    if (/\p{Script=Arabic}/u.test(ch)) arabic++;
    else if (/\p{Script=Latin}/u.test(ch)) latin++;
  }
  return { arabic, latin };
}

/** The first supported declared tag, in the order the page publishes them. */
export function declaredLanguage(tags = {}) {
  for (const tag of [tags.htmlLang, tags.metaContentLanguage, tags.ogLocale, tags.navigatorLanguage]) {
    const code = baseLanguage(tag);
    if (code) return code;
  }
  return null;
}

/**
 * The language this page is actually written in, or null when the evidence is
 * not confident. A null result means "use the full vocabulary", never "block
 * nothing".
 *
 * Script evidence wins only when Arabic is clearly dominant. A declared `ar`
 * with mostly Latin text stays Arabic so Arabizi terms are included; a
 * declared Latin language with dominant Arabic text becomes Arabic, closing
 * the one-attribute bypass described above.
 */
export function detectLanguage(tags = {}, sample = "") {
  const declared = declaredLanguage(tags);
  const { arabic, latin } = scriptCounts(sample);
  if (arabic >= 12 && arabic > latin * 1.2) return "ar";
  return declared;
}
