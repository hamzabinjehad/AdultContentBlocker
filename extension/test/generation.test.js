/**
 * Installing a published generation: all three artifacts or none.
 *
 * `planGeneration` is what stands between verified bytes and
 * `updateDynamicRules`. These pin that a generation missing any artifact,
 * or with an artifact of the wrong shape or size, is refused whole — the
 * previous generation stays — and that an installable one is shaped into the
 * two id bands `applyRules` never touches.
 */
import { planGeneration, downloadedRuleIds, RULE_DOWNLOADED_KEYWORDS_BASE, GENERATION_ARTIFACTS }
  from "../lib/generation.js";
import { RULE_DOWNLOADED_BASE } from "../lib/policy.js";

let failures = 0, checks = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}
// jsc has no TextEncoder; the planner accepts strings as well as bytes.
const enc = (v) => (typeof v === "string" ? v : JSON.stringify(v));

// The real compiled vocabulary, so the shape check runs against real data.
const seedTerms = JSON.parse(readFile("../seed/terms.json"));
const blockRules = Array.from({ length: 218 }, (_, i) => ({
  id: i + 1, priority: 1, action: { type: "block" },
  condition: { requestDomains: [`d${i}.example`], resourceTypes: ["main_frame"] },
}));
const keywordRules = Array.from({ length: 110 }, (_, i) => ({
  id: i + 1, priority: 2, action: { type: "block" },
  condition: { urlFilter: `*kw${i}*`, resourceTypes: ["main_frame"] },
}));
const manifest = {
  version: seedTerms.version, dnr_rule_count: 218, dnr_keyword_rule_count: 110,
  term_count: seedTerms.terms.length, host_term_count: seedTerms.host_terms.length,
};
const artifacts = () => new Map([
  ["dnr_block_rules.json", enc(blockRules)],
  ["dnr_keyword_rules.json", enc(keywordRules)],
  ["terms.json", enc(seedTerms)],
]);

// ── installable ────────────────────────────────────────────────────────────
{
  const plan = planGeneration(manifest, artifacts());
  check(plan.ok, `a complete generation is installable (${plan.reason})`);
  check(plan.blockRules.length === 218 && plan.blockRules[0].id === RULE_DOWNLOADED_BASE
        && plan.blockRules[217].id === RULE_DOWNLOADED_BASE + 217,
        "domain rules are remapped into the downloaded band");
  check(plan.keywordRules.length === 110 && plan.keywordRules[0].id === RULE_DOWNLOADED_KEYWORDS_BASE,
        "keyword rules are remapped into their own band");
  check(plan.keywordRules.every((r) => r.id > plan.blockRules[217].id),
        "the bands do not overlap");
  check(plan.terms.terms.length === seedTerms.terms.length, "the vocabulary comes through intact");
  const legacy = planGeneration({ ...manifest, dnr_keyword_rule_count: undefined }, artifacts());
  check(legacy.ok, "a manifest without a keyword-rule count (older build) still installs");
}

// ── refused whole ──────────────────────────────────────────────────────────
{
  for (const name of GENERATION_ARTIFACTS) {
    const a = artifacts(); a.delete(name);
    const plan = planGeneration(manifest, a);
    check(!plan.ok && plan.reason === `missing:${name}`, `missing ${name} refuses the generation`);
  }
  const bad = [
    [["dnr_block_rules.json", enc("not json")], "block-rules-unparseable"],
    [["dnr_block_rules.json", enc(blockRules.slice(0, 10))], "block-rules-implausible"],
    [["dnr_block_rules.json", enc(blockRules.slice(0, 217))], "block-rules-implausible"],
    [["dnr_keyword_rules.json", enc([])], "keyword-rules-implausible"],
    [["dnr_keyword_rules.json", enc(keywordRules.slice(0, 5))], "keyword-rules-implausible"],
    [["terms.json", enc({ terms: [] })], "terms-unparseable"],
    [["terms.json", enc({ ...seedTerms, terms: seedTerms.terms.slice(0, 100) })], "terms-count"],
    [["terms.json", enc({ ...seedTerms, host_terms: [] })], "host-terms-count"],
    [["terms.json", enc({ ...seedTerms, version: seedTerms.version + 1 })], "terms-version"],
  ];
  for (const [[name, bytes], reason] of bad) {
    const a = artifacts(); a.set(name, bytes);
    const plan = planGeneration(manifest, a);
    check(!plan.ok && plan.reason.startsWith(reason), `${reason}: refused (${plan.reason})`);
  }
  // A collapsed vocabulary with a manifest that agrees is still a broken build.
  const tiny = { ...seedTerms, terms: seedTerms.terms.slice(0, 10), host_terms: seedTerms.host_terms.slice(0, 5) };
  const a = artifacts(); a.set("terms.json", enc(tiny));
  const plan = planGeneration({ ...manifest, term_count: 10, host_term_count: 5 }, a);
  check(!plan.ok && plan.reason.startsWith("terms-implausible"), "a signed-but-empty vocabulary is refused");
}

// ── clearing the previous generation ──────────────────────────────────────
{
  const existing = [{ id: 1 }, { id: 205 }, { id: RULE_DOWNLOADED_BASE }, { id: RULE_DOWNLOADED_BASE + 5 },
                    { id: RULE_DOWNLOADED_KEYWORDS_BASE + 3 }];
  check(JSON.stringify(downloadedRuleIds(existing)) ===
        JSON.stringify([RULE_DOWNLOADED_BASE, RULE_DOWNLOADED_BASE + 5, RULE_DOWNLOADED_KEYWORDS_BASE + 3]),
        "only the downloaded bands are cleared; policy rules are untouched");
}

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} generation checks failed`);
