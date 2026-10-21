/** Deliberate coverage changes, separate from legacy matcher parity. */
import { buildIndex, scorePage, withoutTerms } from "../lib/score.js";

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

print(`  ${checks - failures}/${checks} text evasion/context checks passed`);
if (failures) throw new Error(`${failures} check(s) failed`);
