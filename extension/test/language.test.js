/**
 * Page-language detection and language-vocabulary views.
 *
 * Two things are being protected here.
 *
 * 1. The detector must not trust the page's own `<html lang>`. That tag is an
 *    attribute a page can set to anything, so language-EXCLUSIVE matching would
 *    be a one-line bypass: an Arabic page that claims `lang="en"` would lose
 *    the Arabic vocabulary. Script evidence therefore outranks a declared
 *    Latin language when the rendered text is dominantly Arabic.
 *
 * 2. A language view may narrow weak words, never remove the safety net. A
 *    page whose language cannot be identified scores against the full list;
 *    explicit strong labels travel into every language; negatives are never
 *    filtered; the user's own words are always present.
 */

import { buildIndex, indexForLanguage, scorePage, withoutTerms } from "../lib/score.js";
import { SUPPORTED_LANGUAGES, baseLanguage, languageName, scriptCounts,
         declaredLanguage, detectLanguage } from "../lib/language.js";

let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL ${label}`); } }

// ── language tags ──────────────────────────────────────────────────────────
check(baseLanguage("en") === "en" && baseLanguage("EN") === "en", "bare tags normalise");
check(baseLanguage("en_US") === "en" && baseLanguage("ar-EG") === "ar", "region tags use their base language");
check(baseLanguage("de-DE,de;q=0.9") === "de", "an Accept-Language list uses its first language");
check(baseLanguage("ar-latn") === "ar", "Arabizi shares the Arabic vocabulary");
check(baseLanguage("pt-BR") === "pt" && baseLanguage("es-419") === "es", "regional variants map to the list's language");
check(baseLanguage("nl") === null && baseLanguage("ru-RU") === null, "unsupported languages stay unidentified");
check(baseLanguage("") === null && baseLanguage(null) === null && baseLanguage(42) === null, "non-tags are not guessed");
check(SUPPORTED_LANGUAGES.length === 7 && SUPPORTED_LANGUAGES.includes("ar"), "the supported set is explicit");

check(languageName("ar", "en") === "Arabic" && languageName("ar-EG", "ar") === "العربية",
      "names localise without changing the code");
check(languageName("nl", "en") === "Unknown" && languageName("nl", "ar") === "غير معروفة",
      "an unsupported language has an honest label");

// ── declared vs rendered evidence ─────────────────────────────────────────
const counts = scriptCounts("مرحبا بالعالم hello");
check(counts.arabic === 12 && counts.latin === 5, `scripts are counted, not guessed (${counts.arabic}/${counts.latin})`);
check(scriptCounts("1234 !!!").arabic === 0 && scriptCounts("1234 !!!").latin === 0,
      "digits and punctuation do not count as script evidence");
check(scriptCounts("١٢٣٤٥٦٧٨٩٠ ، ؟").arabic === 0,
      "Arabic-Indic digits and Arabic punctuation are not letters");

check(declaredLanguage({ htmlLang: "ar-EG", metaContentLanguage: "en" }) === "ar",
      "the first supported declaration wins");
check(declaredLanguage({ metaContentLanguage: "de-DE", ogLocale: "fr_FR" }) === "de",
      "meta declarations are considered in order");
check(declaredLanguage({ ogLocale: "pt_BR" }) === "pt", "Open Graph locale is a declaration");
check(declaredLanguage({ navigatorLanguage: "en_US" }) === "en", "the browser locale is the last resort");
check(declaredLanguage({ htmlLang: "nl" }) === null, "an unsupported declaration is not coerced");

const arabicProse = "هذا نص عربي طويل يتحدث عن المحتوى غير المناسب وعن كلمات أخرى كثيرة. ";
check(detectLanguage({ htmlLang: "en" }, arabicProse) === "ar",
      "THE BYPASS: dominant Arabic script overrides a declared Latin language");
check(detectLanguage({ htmlLang: "ar" }, "hello world, this is latin text") === "ar",
      "a declared Arabic page keeps Arabic for its Arabizi terms");
check(detectLanguage({ htmlLang: "en" }, "a short English article about gardening") === "en",
      "an honest Latin declaration is used when the script agrees");
check(detectLanguage({}, "a page that declares nothing") === null,
      "no declaration and no decisive script means the full vocabulary");
check(detectLanguage({ htmlLang: "nl" }, "een Nederlandse pagina") === null,
      "an unsupported declaration leaves the language unidentified");
check(detectLanguage({ htmlLang: "ar" }, "") === "ar", "a declaration survives an empty sample");
check(detectLanguage({ htmlLang: "en" }, "مرحبا") === "en",
      "a tiny Arabic fragment does not falsely reclassify an English page");

// ── the vocabulary is tagged ──────────────────────────────────────────────
const termsJson = {
  terms: [
    { t: "nackt", w: 5, l: "de" },
    { t: "erotik", w: 6, l: "de" },
    { t: "erotique", w: 6, l: "fr" },
    { t: "sexe", w: 6, l: "fr" },
    { t: "gatito", w: 5, l: "es" },
    { t: "اثداء", w: 6, l: "ar" },
    { t: "3ahira", w: 6, l: "ar-latn" },
    { t: "pornographie", w: 8, l: "fr" },
    { t: "colour", w: 5, l: "en" },
    { t: "sharedword", w: 5, l: "en" },
    { t: "sharedword", w: 4, l: "de" },
    { t: "neutral", w: 5 },
  ],
  negatives: [{ t: "research", w: 3 }],
  exempt_domains: [],
};
const index = buildIndex(termsJson);
check(index.termLanguage.get("nackt") === "de", "a German term is tagged German");
check(index.termLanguage.get("3ahira") === "ar", "Arabizi is folded into Arabic");
check(index.termLanguage.get("neutral") === "shared", "a term without a language is shared");
check(index.termLanguage.get("sharedword") === "shared",
      "one spelling in two languages is shared, not owned by the first");
check(index.positive.get("sharedword") === 9, "shared spellings still accumulate their weights");
check(index.negative.has("research"), "negative context is compiled as before");

const seed = buildIndex(JSON.parse(readFile("../seed/terms.json")));
const seedLanguages = new Set(seed.termLanguage.values());
check([...seedLanguages].every((l) => l === "shared" || l === "user" || SUPPORTED_LANGUAGES.includes(l)),
      `every shipped term maps to a supported language (${[...seedLanguages].sort().join(",")})`);

// ── views ─────────────────────────────────────────────────────────────────
const german = indexForLanguage(index, "de");
check(german.positive.has("nackt") && german.positive.has("erotik"), "a German view keeps German terms");
check(german.positive.has("colour"), "English is the common base for every view");
check(german.positive.has("neutral"), "shared terms are in every view");
check(german.positive.has("sharedword"), "shared spellings survive every view");
check(german.positive.has("pornographie"), "a strong explicit label crosses into every language");
check(!german.positive.has("erotique") && !german.positive.has("sexe"),
      "weak French terms are not applied to a German page");
check(!german.positive.has("gatito"), "weak Spanish terms are not applied to a German page");
check(german.negative.has("research"), "negative context is never filtered by language");
check(german.termLanguage.get("sharedword") === "shared", "a view carries its language metadata");

const french = indexForLanguage(index, "fr");
check(french.positive.has("erotique") && french.positive.has("sexe"), "a French view keeps French terms");
check(!french.positive.has("nackt") && !french.positive.has("erotik"), "a French view drops weak German terms");
check(french.positive.has("pornographie"), "the French strong label is in its own view");

const arabic = indexForLanguage(index, "ar");
check(arabic.positive.has("اثداء") && arabic.positive.has("3ahira"),
      "Arabic and Arabizi share one view");
check(indexForLanguage(index, "ar-latn") === arabic, "Arabizi resolves to the cached Arabic view");
check(indexForLanguage(index, null) === index, "no language returns the complete index");
check(indexForLanguage(index, "nl") === index, "an unsupported language returns the complete index");
check(indexForLanguage(index, "de") === german, "views are cached per language");
check([...index.views.keys()].sort().join(",") === "ar,de,fr",
      `only used languages build a view (${[...index.views.keys()].join(",")})`);

// ── scoring through a view ────────────────────────────────────────────────
const verdict = (body, language, extra = {}) =>
  scorePage({ body, ...extra }, index, 50, { language });

check(verdict("nackt nackt", "de").block, "a weak German term blocks a German page");
check(!verdict("nackt nackt", "fr").block, "the same term does not block a French page");
check(verdict("erotique erotique", "fr").block, "a weak French term blocks a French page");
check(!verdict("erotique erotique", "de").block, "the same term does not block a German page");
check(verdict("nackt nackt", "de", { title: "nackt" }).score > verdict("nackt nackt", "de").score,
      "zone weighting still applies inside a view");

check(verdict("اثداء اثداء", "ar").block, "a weak Arabic term blocks an Arabic page");
check(!verdict("اثداء اثداء", "en").block, "a weak Arabic term is not applied to an English page");
check(verdict("3ahira 3ahira", "ar").block, "Arabizi blocks in the Arabic view");

check(verdict("pornographie", "de").block,
      "an explicit strong label still blocks a page in another language");
check(verdict("colour colour", "de").block, "the English base applies on a German page");
check(verdict("sharedword sharedword", "es").block, "a shared spelling applies on any page");

check(verdict("nackt nackt", "nl").block,
      "THE SAFETY NET: an unidentified language uses the full list");

// The real bypass, end to end: Arabic body, `<html lang="en">`, no explicit
// language. If script evidence is removed from detectLanguage, `اثداء` is not
// in the English view and this page gets through.
const bypass = scorePage(
  { body: "اثداء اثداء اثداء", langTags: { htmlLang: "en" }, langSample: "اثداء اثداء اثداء" },
  index, 50);
check(bypass.block,
      "an Arabic page cannot hide from the Arabic list by declaring lang=en");
const honestEnglish = scorePage(
  { body: "nackt nackt", langTags: { htmlLang: "en" }, langSample: "nackt nackt" },
  index, 50);
check(!honestEnglish.block,
      "an English page with a stray German homograph is not blocked by the German list");

// ── withoutTerms keeps the language contract ──────────────────────────────
const cleaned = withoutTerms(index, ["nackt"]);
check(cleaned.termLanguage?.get("erotik") === "de",
      "dropping a word keeps the language of every remaining term");
check(cleaned.views.size === 0, "dropping a word invalidates cached views");
const cleanedGerman = indexForLanguage(cleaned, "de");
check(!cleanedGerman.positive.has("nackt"), "an ignored word stays gone from the language view");
check(cleanedGerman.positive.has("erotik"), "the rest of the German view survives");

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} language checks failed`);
