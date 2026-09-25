/**
 * A published generation, as the browser installs it.
 *
 * The list is several artifacts under one signed manifest: the domain rules,
 * the URL keyword rules, and `terms.json` — the vocabulary the page scanner
 * scores with. `updateList` used to download the domain rules alone, so the
 * keyword layer in the browser was whatever shipped in the package, forever:
 * a term added upstream reached nobody, and a false-positive fix to the term
 * list could not be delivered without a new extension release.
 *
 * This module is the pure part of installing all three as ONE generation.
 * `planGeneration` decides whether a downloaded set is installable — every
 * artifact present, parseable, and the size the manifest says — and shapes
 * the dynamic rules. `background.js` fetches, verifies the hashes through
 * `acceptList`, and applies what this returns in a single
 * `updateDynamicRules` call, so there is no moment with new domain rules and
 * old keyword rules. If any artifact fails, none is applied and the previous
 * generation stays in force.
 *
 * Downloaded rules live above `RULE_DOWNLOADED_BASE`, in two bands so the two
 * kinds can be reasoned about separately; `applyRules` never touches either.
 */

import { RULE_DOWNLOADED_BASE, PRIORITY_LIST, PRIORITY_KEYWORDS } from "./policy.js";

export const RULE_DOWNLOADED_KEYWORDS_BASE = 20000;

/** The three artifacts a generation must carry, by manifest name. */
export const GENERATION_ARTIFACTS = Object.freeze([
  "dnr_block_rules.json", "dnr_keyword_rules.json", "terms.json",
]);

/** Bytes from `fetch`, or already a string (the tests, which run under jsc
 *  with no TextDecoder). */
function parseJSON(bytes) {
  try {
    const text = typeof bytes === "string" ? bytes : new TextDecoder().decode(bytes);
    return { ok: true, value: JSON.parse(text) };
  } catch {
    return { ok: false };
  }
}

/**
 * Decide whether a downloaded generation is installable, and shape it.
 *
 * `artifacts` maps manifest names to bytes that `acceptList` has ALREADY
 * verified against the manifest's hashes — this function trusts the bytes and
 * checks their shape. Returns `{ ok: true, blockRules, keywordRules, terms }`
 * or `{ ok: false, reason }`.
 *
 * The plausibility floors mirror what the build and CI apply: a validly
 * signed list that collapsed to a handful of rules, or a term list that lost
 * its vocabulary, is a broken build, and keeping yesterday's is strictly
 * safer than installing it.
 */
export function planGeneration(manifest, artifacts) {
  for (const name of GENERATION_ARTIFACTS) {
    if (!artifacts.has(name)) return { ok: false, reason: `missing:${name}` };
  }

  const block = parseJSON(artifacts.get("dnr_block_rules.json"));
  if (!block.ok || !Array.isArray(block.value)) return { ok: false, reason: "block-rules-unparseable" };
  const expectedBlock = manifest.dnr_rule_count;
  if (block.value.length < 50 || (expectedBlock && block.value.length !== expectedBlock)) {
    return { ok: false, reason: `block-rules-implausible:${block.value.length}/${expectedBlock ?? "?"}` };
  }

  const keyword = parseJSON(artifacts.get("dnr_keyword_rules.json"));
  if (!keyword.ok || !Array.isArray(keyword.value)) return { ok: false, reason: "keyword-rules-unparseable" };
  const expectedKeyword = manifest.dnr_keyword_rule_count;
  if (keyword.value.length < 1 || (expectedKeyword && keyword.value.length !== expectedKeyword)) {
    return { ok: false, reason: `keyword-rules-implausible:${keyword.value.length}/${expectedKeyword ?? "?"}` };
  }

  const terms = parseJSON(artifacts.get("terms.json"));
  const t = terms.value;
  if (!terms.ok || !t || !Array.isArray(t.terms) || !Array.isArray(t.host_terms)
      || !Array.isArray(t.negatives) || !Array.isArray(t.never_keyword)) {
    return { ok: false, reason: "terms-unparseable" };
  }
  if (manifest.term_count && t.terms.length !== manifest.term_count) {
    return { ok: false, reason: `terms-count:${t.terms.length}/${manifest.term_count}` };
  }
  if (manifest.host_term_count && t.host_terms.length !== manifest.host_term_count) {
    return { ok: false, reason: `host-terms-count:${t.host_terms.length}/${manifest.host_term_count}` };
  }
  if (t.terms.length < 200 || t.host_terms.length < 50) {
    return { ok: false, reason: `terms-implausible:${t.terms.length}/${t.host_terms.length}` };
  }
  // Every entry must be usable by the scorer. A signed file whose entries
  // lacked `t` made buildIndex throw on every page, and the page-text layer
  // died silently for the life of that generation.
  const badTerm = t.terms.find((e) => !e || typeof e.t !== "string" || !e.t
                                      || !Number.isFinite(e.w));
  if (badTerm !== undefined) return { ok: false, reason: "terms-entry-malformed" };
  if (t.host_terms.some((e) => typeof e !== "string" && typeof e?.t !== "string")) {
    return { ok: false, reason: "host-terms-entry-malformed" };
  }
  if (typeof t.version === "number" && t.version !== manifest.version) {
    return { ok: false, reason: `terms-version:${t.version}/${manifest.version}` };
  }

  return {
    ok: true,
    blockRules: remap(block.value, RULE_DOWNLOADED_BASE, PRIORITY_LIST),
    keywordRules: remap(keyword.value, RULE_DOWNLOADED_KEYWORDS_BASE, PRIORITY_KEYWORDS),
    terms: t,
  };
}

/** The ids in an artifact start at 1 and would collide with the policy rules,
 *  so they are reassigned on the way in rather than trusted — and so are the
 *  priorities, which decide how the list ranks against strict mode's carve-out
 *  (lib/policy.js, the ladder). A generation built before the ladder carried 1
 *  and 2; taking them as given would reopen that hole until the next build. */
function remap(rules, base, priority) {
  return rules.map((rule, i) => ({ ...rule, id: base + i, priority }));
}

/** Every dynamic rule id the downloaded generation owns — what to remove
 *  before installing the next one. */
export function downloadedRuleIds(existingRules) {
  return existingRules.filter((r) => r.id >= RULE_DOWNLOADED_BASE).map((r) => r.id);
}
