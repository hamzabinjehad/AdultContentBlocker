/**
 * Glued multi-word terms.
 *
 * The scorer tokenises on the complement of letters and digits, so a listed
 * phrase is found when its words are adjacent (`nude photos`), separated by
 * punctuation (`nude-photos`), or repeated (`nude   photos`). The one spelling
 * it used to miss was the concatenation: `nudephotos`, which is one token and
 * matched nothing. That is not an exotic bypass — it is how tube titles,
 * captions and hashtags are written.
 *
 * The alias is derived from the reviewed list at compile time. These tests
 * pin the three properties that make that safe:
 *
 *   1. only a phrase already in the list produces an alias;
 *   2. the alias shares the phrase's score AND its occurrence cap;
 *   3. ignoring the phrase removes the alias too, and a real term always owns
 *      its own spelling.
 */

import { buildIndex, scorePage, withoutTerms } from "../lib/score.js";

let checks = 0, failures = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

const seed = JSON.parse(readFile("../seed/terms.json"));
const index = buildIndex(seed);

// ── an English phrase and its concatenation ────────────────────────────────
const glued = scorePage({ body: "nudepics" }, index, 50);
check(glued.block, "a glued listed phrase blocks: nudepics");
check(glued.hits.get("nude pics") === 1,
      "the glued form scores as its canonical phrase, not as a new term");
check(glued.hits.size === 1, "one hit, not one per fragment");

// Both spellings are the same signal, and the cap counts them together: four
// mentions score as three, exactly as four separated mentions would.
const mixed = scorePage({ body: "nudepics nude pics nudepics nude pics" }, index, 50);
check(mixed.hits.get("nude pics") === 3,
      "the glued and separated spellings share the occurrence cap");

// Punctuation and repeated spaces were already token separators. This keeps
// that working rather than "fixing" it into a substring rule.
for (const text of ["nude-pics", "nude_pics", "nude.pics", "nude   pics"]) {
  check(scorePage({ body: text }, index, 50).hits.get("nude pics") === 1,
        `separated spelling still matches: ${text}`);
}

// ── Arabic, where the list itself is mostly multi-word ─────────────────────
const arabic = scorePage({ body: "اباحيجديد" }, index, 50);
check(arabic.block, "a glued Arabic phrase blocks: اباحيجديد");
check(arabic.hits.get("اباحي جديد") === 1,
      "the glued Arabic form scores as its canonical phrase");

// ── it is an alias, not fuzzy matching ─────────────────────────────────────
// A longer token that merely STARTS with the glued form must not match. This
// is the whole-token boundary the module promises; a substring rule here would
// start matching hostnames, version strings and ordinary compounds.
for (const text of ["nudepicsarchive", "nudepicsx", "thenudepics"]) {
  check(!scorePage({ body: text }, index, 50).block,
        `only the whole token matches: ${text}`);
}

// A real term always owns its spelling. With both `garden fixture` and
// `gardenfixture` in the vocabulary, the alias must not be created, or the
// explicitly weightier/phrase rule would silently double-count.
const owned = buildIndex({
  terms: [{ t: "garden fixture", w: 8, l: "fixture" },
          { t: "gardenfixture", w: 2, l: "fixture" }],
  negatives: [],
});
check(!owned.canonical.has("gardenfixture"),
      "no alias is created when the glued spelling is already a term");
check(scorePage({ body: "gardenfixture" }, owned, 50).score === 2,
      "the real term keeps its own weight");

// Positive and negative spellings must not fight over one token. When the
// glued form already exists as a negative, the positive phrase does not take
// it over.
const conflict = buildIndex({
  terms: [{ t: "garden fixture", w: 8, l: "fixture" }],
  negatives: [{ t: "gardenfixture", w: 4, l: "fixture" }],
});
check(!conflict.canonical.has("gardenfixture"),
      "a negative spelling blocks the positive alias");
check(scorePage({ body: "gardenfixture" }, conflict, 50).score === -4,
      "the existing negative keeps its meaning");

// A user's own multi-word rule gets the same treatment: they typed a phrase
// because that is the trigger they know, and the concatenation is the same
// trigger on the page.
const customPhrase = buildIndex({ terms: [{ t: "garden fixture", w: 8, l: "user" }], negatives: [] });
check(scorePage({ body: "gardenfixture" }, customPhrase, 50).block,
      "a custom phrase also matches its glued spelling");

// Only multi-word terms produce aliases. A single word needs no help, and
// inventing one would turn every term into an unofficial prefix rule.
check(!index.canonical.has("nude"), "single-word terms produce no alias");

// ── ignoring the phrase removes the concatenation too ──────────────────────
const ignored = withoutTerms(index, ["nude pics"]);
const afterIgnore = scorePage({ body: "nudepics" }, ignored, 50);
check(!afterIgnore.block && !afterIgnore.hits.has("nude pics"),
      "ignoring a phrase also stops its glued spelling");

// Ignoring something else must leave the alias working.
const otherIgnored = withoutTerms(index, ["nudity"]);
check(scorePage({ body: "nudepics" }, otherIgnored, 50).block,
      "ignoring an unrelated term does not disable the alias");

// ── every generated alias is grounded in a real list entry ─────────────────
// The alias map may only point at a canonical key that exists in one of the
// maps. A dangling mapping would score a token through a term that is not in
// the vocabulary at all.
let dangling = 0, aliased = 0;
for (const [alias, canonical] of index.canonical) {
  aliased++;
  if (!index.positive.has(canonical) && !index.negative.has(canonical)) dangling++;
  if (!index.positive.has(alias) && !index.negative.has(alias)) dangling++;
}
check(aliased > 0, "the shipped vocabulary produces glued aliases");
check(dangling === 0, `every alias is grounded (${dangling} dangling)`);

print(`  ${checks - failures}/${checks} compound-term checks passed`);
if (failures) throw new Error(`${failures} check(s) failed`);
