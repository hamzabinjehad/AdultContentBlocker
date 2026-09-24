/**
 * Page-text scoring, measured.
 *
 *     extension/eval/run.sh                # default sensitivity (50), shipped vocabulary
 *     extension/eval/run.sh 80             # any sensitivity
 *     extension/eval/run.sh 50 ../../dist/terms.json   # a candidate vocabulary
 *
 * Scores every case in corpus/*.json with the SHIPPED vocabulary
 * (seed/terms.json) through the production scorer, and reports missed blocks
 * and false positives SEPARATELY, per language and per page type — the two
 * numbers a vocabulary change moves in opposite directions, so a single
 * accuracy figure would hide exactly what changed.
 *
 * Also runs two adversarial passes on the adult cases marked `dilute`:
 *   * dilution — the same page padded with growing amounts of benign text,
 *     to find how much filler defeats the block (the density floor exists for
 *     this and this measures where it sits);
 *   * misleading metadata — an adult body under a benign title and meta,
 *     which must still block, and a benign body under adult metadata, which
 *     is reported (a page's own description is evidence, so blocking it is
 *     not wrong, but the number should be visible).
 *
 * Exit status: 0 unless a BENIGN case blocks. The benign corpus is the
 * regression set — medicine, scholarship, news, short pages — and losing
 * any of it is a defect. Coverage numbers are reported, never gated: they
 * are what a vocabulary change is judged against, and gating them would
 * make every improvement to the corpus a red build.
 *
 * Starter corpus. Real coverage numbers need real labelled pages per
 * dialect, and those do not belong in a repository.
 */
import { buildIndex, scorePage } from "../lib/score.js";

const sensitivity = Number(arguments?.[0] ?? 50) || 50;
// A second argument scores a candidate vocabulary before it ships:
//     extension/eval/run.sh 50 /path/to/terms.json
const termsPath = arguments?.[1] || "../seed/terms.json";
const index = buildIndex(JSON.parse(readFile(termsPath)));
const corpus = ["benign", "adult"].flatMap((f) =>
  JSON.parse(readFile(`corpus/${f}.json`)).cases);

const FILLER = ("The committee met on Tuesday to review the quarterly budget and the schedule for the "
  + "library renovation. Members discussed parking, the summer reading programme and a proposal "
  + "to extend opening hours. The minutes will be published next week. ").repeat(40);

const key = (c) => `${c.lang}/${c.type}`;
const groups = new Map();
const bump = (k, field) => {
  const g = groups.get(k) ?? { adult: 0, missed: 0, benign: 0, fp: 0 };
  g[field]++;
  groups.set(k, g);
};

let benignBlocked = 0;
const lines = [];
for (const c of corpus) {
  const r = scorePage(c.zones, index, sensitivity);
  const k = key(c);
  if (c.label === "adult") {
    bump(k, "adult");
    if (!r.block) { bump(k, "missed"); lines.push(`  MISSED  ${c.id}  score=${r.score.toFixed(1)} density=${r.density.toFixed(0)} t=${r.threshold}`); }
  } else {
    bump(k, "benign");
    if (r.block) {
      bump(k, "fp"); benignBlocked++;
      lines.push(`  FALSE+  ${c.id}  score=${r.score.toFixed(1)} hits=${[...r.hits.keys()].slice(0, 5).join(",")}`);
    }
  }
}

print(`sensitivity ${sensitivity} · ${corpus.length} cases · vocabulary ${index.positive.size} terms\n`);
print("  lang/type              adult  missed   benign  false+");
for (const [k, g] of [...groups].sort()) {
  print(`  ${k.padEnd(22)} ${String(g.adult).padStart(5)}  ${String(g.missed).padStart(6)}   ${String(g.benign).padStart(6)}  ${String(g.fp).padStart(6)}`);
}
const totalAdult = [...groups.values()].reduce((a, g) => a + g.adult, 0);
const totalMissed = [...groups.values()].reduce((a, g) => a + g.missed, 0);
const totalBenign = [...groups.values()].reduce((a, g) => a + g.benign, 0);
print(`\n  missed blocks: ${totalMissed}/${totalAdult}   false positives: ${benignBlocked}/${totalBenign}`);
if (lines.length) print("\n" + lines.join("\n"));

// ── dilution ───────────────────────────────────────────────────────────────
print("\ndilution — benign words appended before the block is lost:");
for (const c of corpus.filter((c) => c.label === "adult" && c.dilute)) {
  let lost = null;
  for (const words of [0, 100, 300, 1000, 3000, 8000]) {
    const padded = { ...c.zones, body: (c.zones.body ?? "") + " " + FILLER.split(" ").slice(0, words).join(" ") };
    const r = scorePage(padded, index, sensitivity);
    if (!r.block) { lost = words; break; }
  }
  print(`  ${c.id.padEnd(16)} ${lost === null ? "holds through 8000 words" : `lost at +${lost} words`}`);
}

// ── misleading metadata ────────────────────────────────────────────────────
print("\nmisleading metadata:");
for (const c of corpus.filter((c) => c.label === "adult" && c.type === "tube")) {
  const benignHead = { ...c.zones, title: "Weekend gardening notes", meta: "Gardening, tomatoes, compost" };
  const r = scorePage(benignHead, index, sensitivity);
  print(`  ${c.id.padEnd(16)} adult body under benign title/meta: ${r.block ? "blocked" : "MISSED"}`);
}
{
  const benign = corpus.find((c) => c.id === "en-news-1");
  const adultHead = { ...benign.zones, title: "Free adult videos — watch now", meta: "adult movies, blowjob, big tits" };
  const r = scorePage(adultHead, index, sensitivity);
  print(`  ${"en-news-1".padEnd(16)} benign body under adult title/meta: ${r.block ? "blocked (metadata is evidence)" : "not blocked"}`);
}

if (benignBlocked) {
  print(`\nFAILED: ${benignBlocked} benign page(s) would be blocked — essential content regression`);
  throw new Error("benign regression");
}
print("\nOK — no benign page blocked");
