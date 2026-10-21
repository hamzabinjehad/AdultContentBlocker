/** Compare the prefix matcher with the former exhaustive phrase algorithm.
 * Keep this reference independent: an optimisation must not silently change
 * weights, negatives, boundaries, overlapping matches or ignored words. */
import { buildIndex, scorePage, threshold, withoutTerms, ZONE_WEIGHTS } from "../lib/score.js";
import { normalize, tokenize, variants } from "../lib/normalize.js";

let checks = 0, failures = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

function reference(zones, index, sensitivity) {
  let score = 0, words = 0;
  const hits = new Map();
  for (const [zone, weight] of Object.entries(ZONE_WEIGHTS)) {
    if (!zones[zone]) continue;
    const tokens = tokenize(normalize(zones[zone])), counts = new Map();
    const count = (term) => counts.set(term, (counts.get(term) ?? 0) + 1);
    for (let i = 0; i < tokens.length; i++) {
      for (const v of variants(tokens[i])) {
        if (index.positive.has(v)) count(v);
        if (index.negative.has(v)) count(v);
      }
      for (let n = 2; n <= index.maxPhrase && i + n <= tokens.length; n++) {
        const phrase = tokens.slice(i, i + n).join(" ");
        if (index.positive.has(phrase) || index.negative.has(phrase)) count(phrase);
      }
    }
    if (zone === "body") words += tokens.length;
    for (const [term, raw] of counts) {
      const occurrences = Math.min(raw, 3);
      const w = index.positive.get(term) ?? index.negative.get(term) ?? 0;
      score += w * occurrences * weight;
      if (w > 0) hits.set(term, (hits.get(term) ?? 0) + occurrences);
    }
  }
  const t = threshold(sensitivity), density = score / Math.max(1, words / 1000);
  return { score, density, words, threshold: t, block: score >= t && density >= 2, hits };
}
function same(a, b) {
  return JSON.stringify({ ...a, hits: [...a.hits] }) === JSON.stringify({ ...b, hits: [...b.hits] });
}

const index = buildIndex(JSON.parse(readFile("../seed/terms.json")));
const corpus = ["benign", "adult"].flatMap((name) =>
  JSON.parse(readFile(`../eval/corpus/${name}.json`)).cases);
for (const sensitivity of [0, 20, 50, 80, 100]) {
  for (const c of corpus) {
    const actual = scorePage(c.zones, index, sensitivity), previous = reference(c.zones, index, sensitivity);
    // This is the one deliberate correction in the original corpus, not an
    // optimisation discrepancy to hide by weakening the equality assertion.
    if (c.id === "fr-form-1") {
      check(actual.score === 0 && previous.score === 6 && !actual.block && actual.hits.size === 0,
        `French form corrects exactly its old weight-6 label signal (${sensitivity})`);
    } else {
      check(same(actual, previous), `${c.id} preserves the complete verdict at sensitivity ${sensitivity}`);
    }
  }
}

// Exercise every shipped positive AND contextual-negative phrase, including
// caps, repeated prefixes and all weighted zones. A mixed page alone could
// hide differences through score cancellation.
for (const map of [index.positive, index.negative]) {
  for (const term of map.keys()) {
    if (!term.includes(" ")) continue;
    const zones = Object.fromEntries(Object.keys(ZONE_WEIGHTS).map((key) =>
      [key, `ordinary ${term} ${term} ${term} ${term} ordinary`]));
    check(same(scorePage(zones, index), reference(zones, index, 50)), `shipped phrase: ${term}`);
  }
}

const removed = [...index.positive.keys()].filter((term) => term.includes(" ")).slice(0, 40);
const pruned = withoutTerms(index, removed);
const removedZones = { title: removed.join(" ordinary "), body: removed.join("\n") };
check(same(scorePage(removedZones, pruned), reference(removedZones, pruned, 50)),
  "ignored phrase keys cannot survive in the compiled matcher");

// A long ordinary page interleaved with real terms verifies the hot path.
const longZones = { body: ("The committee met on Tuesday to review the quarterly report. ".repeat(200)
  + "قصص جنسية sexual health free porn ordinary ").repeat(10) };
const start = Date.now();
const before = reference(longZones, index, 50), referenceMs = Date.now() - start;
const next = Date.now();
const after = scorePage(longZones, index), prefixMs = Date.now() - next;
check(same(after, before), "long mixed-language page preserves scores and matched terms");
// Timing is diagnostic, not a flaky release gate or a universal speed claim.
print(`  long-page sample: ${longZones.body.length} characters; exhaustive ${referenceMs}ms; prefix ${prefixMs}ms`);
print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} check(s) failed`);
