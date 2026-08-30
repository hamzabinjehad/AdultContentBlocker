/**
 * The JavaScript third of the three-language normalisation contract.
 *
 * `blocklist/test_terms.py::TestNormalize` and
 * `macos/HisnTests/KeywordLayerTests.swift::testNormalizeGolden` assert the
 * SAME table, read from the same file. Three implementations, one fixture.
 *
 * The failure this prevents is silent and total: if the JS normaliser drifts
 * from the Python one that normalised the term list at build time, every
 * Arabic term in a signed, verified, correctly-installed list matches nothing,
 * and no error is raised anywhere.
 */

import { normalize, tokenize, variants } from "../lib/normalize.js";

let failures = 0;
let checks = 0;

function check(ok, label) {
  checks++;
  if (!ok) {
    failures++;
    print(`  FAIL  ${label}`);
  }
}

function eq(actual, expected, label) {
  check(actual === expected, `${label}\n        got ${JSON.stringify(actual)}` +
                             `\n        want ${JSON.stringify(expected)}`);
}

// ── the golden table ───────────────────────────────────────────────────────
// Read from the shared fixture rather than copied into this file. A copy would
// drift, and drift between the three implementations is the entire failure
// this test exists to catch.
const fixture = JSON.parse(readFile("../../blocklist/terms/normalize_cases.json"));

for (const c of fixture.cases) {
  eq(normalize(c.in), c.out, `${c.why}  (input ${JSON.stringify(c.in)})`);
}

// Prevents a fold that changes its own output. Terms are normalised once at
// build time and page text on every pass; a non-idempotent function produces
// two different strings for one word.
for (const c of fixture.cases) {
  const once = normalize(c.in);
  eq(normalize(once), once, `idempotent for ${JSON.stringify(c.in)}`);
}

// ── Franco-Arabic digits are LETTERS ───────────────────────────────────────
// Prevents digit-folding from deleting the whole romanised tier: `3ahira`
// becoming `ahira` matches nothing, and that tier is what makes Arabic
// hostname matching work at all.
eq(normalize("3ahira"), "3ahira", "Franco-Arabic leading digit survives");
eq(normalize("2a7a"), "2a7a", "Franco-Arabic embedded digits survive");

// ── tokenizing ─────────────────────────────────────────────────────────────
// Prevents a \b-based tokenizer, which is ASCII-word-based even with /u and
// does not see Arabic word boundaries at all.
eq(tokenize("sks-arab.net").join(","), "sks,arab,net", "latin tokenizing");
eq(tokenize(normalize("صور إباحية")).join(","), "صور,اباحيه", "arabic tokenizing");

// ── bounded affix stripping ────────────────────────────────────────────────
// Prevents the definite article silently disabling every Arabic term.
check(variants(normalize("الإباحية")).has("اباحيه"),
      "definite article stripped: الإباحية -> اباحيه");

// Prevents unbounded stemming, which is how `جنس` starts matching `الجنسية`
// and blocks every Arabic passport and visa page.
check(!variants(normalize("الجنسية")).has("جنس"),
      "stripping stays bounded: الجنسية must NOT yield جنس");

// Both halves of the digit problem at once.
check(variants("sharmota123").has("sharmota"), "trailing digits are decoration");
check(!variants("3ahira").has("ahira"), "leading digit is not decoration");

// ── result ─────────────────────────────────────────────────────────────────
print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} check(s) failed`);
