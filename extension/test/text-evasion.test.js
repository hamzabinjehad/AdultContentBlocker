/** Deliberate coverage changes, separate from legacy matcher parity. */
import { buildIndex, scorePage, withoutTerms, EVASION_TERM_LIMIT } from "../lib/score.js";

let checks = 0, failures = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}
const seed = JSON.parse(readFile("../seed/terms.json"));
const index = buildIndex(seed);
for (const text of ["p0rn", "pоrn", "pοrn", "pоrn0", "p0rn0graphy", "hent41", "p.o.r.n",
  "p_o_r_n", "p*o*r*n", "p-o-r-n", "p.о.r.n", "س.ك.س", "إ.ب.ا.ح.ي.ة"]) {
  const r = scorePage({ body: text }, index);
  check(r.block, `disguised explicit term blocks: ${text}`);
  check(r.hits.size === 1, `one canonical hit, not each fragment: ${text}`);
}
for (const text of ["pop-corn", "Essex", "passage", "passport", "p.o.r.n.e.r",
  "v.1.2.3", "R2D2 C3PO 2026", "Прогноз погоды", "الجنسية", "p o r n",
  "p/о/r/n", "p\no\nr\nn", "p.o.r.news"]) {
  check(!scorePage({ title: text, body: text }, index).block, `benign or unsupported spelling stays unchanged: ${text}`);
}
const one = scorePage({ body: "p0rn p.o.r.n pоrn p0rn" }, index);
check(one.score === 24 && one.hits.get("porn") === 3,
  "aliases share the original canonical occurrence cap");
for (const body of ["p0rn", "p.o.r.n", "pоrn", "porn0", "pоrn0", "p0rn123"]) {
  check(!scorePage({ body }, withoutTerms(index, ["porn"])).block,
    `ignored canonical word cannot reappear through an alias: ${body}`);
}
check(scorePage({ body: "pоrn0" }, index).hits.get("porn") === 1,
  "a trailing digit stays decoration even when an internal letter is disguised");
const customOnly = buildIndex({ terms: [{ t: "gardenfixture", w: 8 }], negatives: [] });
check(scorePage({ body: "p0rn p.o.r.n" }, customOnly).score === 0,
  "reviewed aliases cannot create terms absent from the effective vocabulary");
const weak = buildIndex({ terms: [{ t: "porn", w: 2 }], negatives: [] });
check(scorePage({ body: "p0rn p.o.r.n" }, weak).score === 0,
  "a weak reweighted term is not silently promoted by evasion matching");

for (const body of ["Sexe : Femme / Homme / Autre", "Sexe : Homme / Femme",
  "Sexe : Masculin / Féminin", "Sexe : Féminin / Masculin"]) {
  for (const sensitivity of [50, 80, 100]) {
    check(!scorePage({ title: "Créer un compte", body }, index, sensitivity).block,
      `a French form choice is not adult content (${sensitivity}): ${body}`);
  }
  check(scorePage({ body: body + ". Watch porn now." }, index, 80).block,
    "a form label cannot exempt explicit material elsewhere in the item");
}
check(scorePage({ body: "Sexe videos now" }, index, 80).block,
  "an ambiguous term without the reviewed local form choices still scores");
check(scorePage({ body: "Sexe. Other text elsewhere. Femme et homme." }, index, 80).block,
  "distant choice words cannot suppress a separate signal");
const custom = buildIndex({ ...seed, terms: [...seed.terms, { t: "sexe", w: 8, l: "user" }] });
check(scorePage({ body: "Sexe : Femme / Homme" }, custom, 80).block,
  "the form heuristic cannot cancel a stronger deliberate custom rule");
const again = scorePage({ body: "Sexe : Femme / Homme. Sexe videos." }, index, 80);
check(again.block && again.hits.get("sexe") === 1,
  "only the form-label occurrence is skipped, not a separate occurrence");

// Bound long hostile tokens; no fuzzy edit search, fragment concatenation
// across words, or proportional retained data per page is permitted.
const huge = scorePage({ body: ("p0".repeat(20_000) + ".").repeat(3) }, index);
check(huge.score === 0, "oversized non-word candidates do not become substring matches");

for (const c of JSON.parse(readFile("../eval/corpus/text-hardening.json")).cases) {
  for (const sensitivity of [50, 80, 100]) {
    check(scorePage(c.zones, index, sensitivity).block === (c.label === "adult"),
      `labelled hardening regression: ${c.id} at ${sensitivity}`);
  }
}

// ── the reviewed alias set covers strong single-token brands too ───────────
// All of these are exactly one token in the signed list and carry weight 8.
// The lane only ever accepts a term that is present at that weight, so these
// aliases cannot promote a word the list does not already treat as explicit.
for (const [spelling, term] of [
  ["p0rnhub", "pornhub"], ["0nlyf4ns", "onlyfans"], ["xv1de0s", "xvideos"],
  ["r3dtub3", "redtube"], ["y0up0rn", "youporn"], ["br4zz3rs", "brazzers"],
  ["c4mg1rls", "camgirls"], ["s3xc4m", "sexcam"], ["l1v3s3x", "livesex"],
  ["s3xch4t", "sexchat"], ["m1lf", "milf"], ["bl0wj0b", "blowjob"],
  ["h4ndj0b", "handjob"], ["cr34mpi3", "creampie"], ["cumsh0t", "cumshot"],
  ["g4ngb4ng", "gangbang"], ["d33pthr04t", "deepthroat"], ["t1tt13s", "titties"],
  ["p0rn0gr4ph1c", "pornographic"],
]) {
  const r = scorePage({ body: spelling }, index, 50);
  check(r.block && r.hits.has(term), `brand alias resolves to its canonical term: ${spelling}`);
}
for (const spelling of ["0nlyf4ns", "p0rnhub"]) {
  const term = spelling === "0nlyf4ns" ? "onlyfans" : "pornhub";
  check(!scorePage({ body: spelling }, withoutTerms(index, [term])).block,
    `ignoring ${term} also stops its alias ${spelling}`);
}

// ── the local lane's bitmask spans every reviewed term ─────────────────────
// LOCAL_TERMS outgrew a byte; a Uint8Array used to truncate the evidence for
// every term past the eighth, so the promotion lane silently stopped seeing
// late entries. The filler makes the whole-page score too diluted to fire, so
// only the local lane can produce this block.
const filler = "the committee met on tuesday to review the quarterly report and road repairs. ";
const high = scorePage({ body: "t1tt13s buttplug watch. " + filler.repeat(1000) }, index, 50);
check(high.words > 5000, `the dilution fixture is long (${high.words} words)`);
check(high.localEvidence, "the local lane sees terms in the high bits (titties, buttplug)");
check(high.block, "a local promotion still blocks a diluted page");

// The local lane packs one bit per reviewed term into a 32-bit word. The
// module throws at load if this is exceeded; the assertion documents the bound
// where a future reviewer will see it.
check(EVASION_TERM_LIMIT === 31 && Number.isInteger(EVASION_TERM_LIMIT),
      "the reviewed evasion vocabulary fits the bitmask");

print(`  ${checks - failures}/${checks} text evasion/context checks passed`);
if (failures) throw new Error(`${failures} check(s) failed`);
