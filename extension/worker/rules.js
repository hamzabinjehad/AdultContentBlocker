/**
 * Rule application: put the policy the state describes into Chrome, then prove
 * that Chrome actually holds it.
 *
 * The dynamic rules themselves are built by `lib/policy.js`, the same array the
 * Mac filter's contract tests evaluate. This module owns the ORDER of the write
 * and the readback, not the decision. It exists because "updateDynamicRules
 * resolved" is not evidence of enforcement: Chrome may persist an old ruleset,
 * a registration may be missing after an update, and a write can be accepted
 * while a read returns something else. Every layer is read back before the
 * receipt is allowed to say "applied".
 */

import {
  RULE_DOWNLOADED_BASE, effectiveStrict, isLocked, policyRules,
} from "../lib/policy.js";
import { RULE_DOWNLOADED_KEYWORDS_BASE } from "../lib/generation.js";
import { WORKER_SESSION, desiredPolicyKey, setState } from "./state.js";
import { scannerRegistrationMatches, syncTextScanning, syncVisualScanning,
         TEXT_SCANNER, VISUAL_SCANNER } from "./scanners.js";
import { enforceOpenTabs } from "./tabs.js";

const RULESET_BLOCKLIST = "blocklist";
const RULESET_KEYWORDS = "keywords";
const RULESET_LOCKDOWN = "lockdown";
// Forces SafeSearch on the search engines and restricted mode on YouTube.
// Priority 1 on purpose: a custom block or strict mode (also at 1, where a
// block beats a redirect) still wins, so SafeSearch can never be a way to
// reach a search engine someone blocked. policyRules also adds scoped copies
// above each allowance, without outranking matching custom blocks.
const RULESET_SAFESEARCH = "safesearch";
const RULESET_WEB_PROTECTION = "web_protection";
// Generated from the reviewed alias table (blocklist/evasion.py): obfuscated
// spellings of terms the keyword layer already covers, e.g. p0rn and s3x.
// Static rather than dynamic so it is present before the worker's first wake.
const RULESET_EVASION = "evasion";
// Percent-encoded explicit Arabic search queries. Chrome sees a URI, so Arabic
// terms cannot live in a keyword rule; this generated ruleset matches the
// encoded form in a query parameter only (never a bare path).
const RULESET_ENCODED_QUERIES = "encoded_queries";
// Path rules for single sites whose words mean something only in one place:
// "nsfw" in a Reddit community's name. Kept out of the keyword list, which
// matches every site.
const RULESET_PATHS = "paths";
export const BASELINE_RULESETS = [RULESET_BLOCKLIST, RULESET_KEYWORDS, RULESET_SAFESEARCH,
                                  RULESET_PATHS, RULESET_WEB_PROTECTION, RULESET_EVASION,
                                  RULESET_ENCODED_QUERIES];

/*
 * The dynamic rules — strict catch-all, allowlist carve-outs, custom blocks —
 * are built by `policyRules` in lib/policy.js. That file is the decision
 * contract shared with the macOS filter, and `blocklist/terms/policy_cases.json` is
 * asserted against the very array it returns. Do not build rules here.
 */
export async function applyRules(state) {
  const dynamic = policyRules(state);
  const receipt = { version: 1, status: "pending", worker: WORKER_SESSION,
    policyKey: desiredPolicyKey(state), desiredRevision: state.desiredRevision || 0,
    appliedRevision: state.appliedRevision || 0, checkedAt: 0, layers: {} };
  await setState({ enforcement: receipt });
  let phase = "baseline-rulesets";
  let previousPolicy;
  let replacedPolicy = false;
  try {
    // Baseline protection is unconditional: neither an absent app nor an
    // unlocked browser is a request to disable its independent rulesets.
    await chrome.declarativeNetRequest.updateEnabledRulesets({ enableRulesetIds: BASELINE_RULESETS });
    phase = "ruleset-readback";
    const baseline = await chrome.declarativeNetRequest.getEnabledRulesets();
    if (BASELINE_RULESETS.some(id => !baseline.includes(id))) throw new Error("baseline ruleset readback mismatch");
    phase = "policy-snapshot";
    const existing = await chrome.declarativeNetRequest.getDynamicRules();
    previousPolicy = existing.filter(rule => rule.id < RULE_DOWNLOADED_BASE);
    if (canonicalRules(previousPolicy) === canonicalRules(dynamic) && !baseline.includes(RULESET_LOCKDOWN)) {
      receipt.layers.domains = receipt.layers.search = "applied";
    }
    // Complete scanner repair and readback before replacing stricter rules.
    // A missing registration must not create a transient relaxed unlock.
    phase = "text-scanner";
    await syncTextScanning(state);
    phase = "visual-scanner";
    await syncVisualScanning(state);
    phase = "scanner-readback";
    const scripts = await chrome.scripting.getRegisteredContentScripts({ ids: ["hisn-scan", "hisn-visual"] });
    for (const [layer, expected, wanted] of [
      ["text", TEXT_SCANNER, state.inspectText || state.failClosed || isLocked(state)],
      ["visual", VISUAL_SCANNER, state.inspectVisual === true || (state.personBlur && state.personBlur !== "off")],
    ]) {
      const actual = scripts.find(script => script.id === expected.id);
      if (wanted ? !scannerRegistrationMatches(actual, expected) : !!actual) {
        phase = `${layer}-registration-readback`;
        throw new Error("scanner registration readback mismatch");
      }
      receipt.layers[layer] = wanted ? "applied" : "off";
    }
    // Only replace owned policy ids. Downloaded rules retain their separate
    // band, so lock/settings changes cannot wipe the signed blocklist.
    phase = "dynamic-rules";
    await chrome.declarativeNetRequest.updateDynamicRules({
      removeRuleIds: previousPolicy.map(rule => rule.id), addRules: dynamic,
    });
    replacedPolicy = true;
    phase = "rules-readback";
    const actualRules = await chrome.declarativeNetRequest.getDynamicRules();
    if (canonicalRules(actualRules.filter(rule => rule.id < RULE_DOWNLOADED_BASE)) !== canonicalRules(dynamic)) {
      throw new Error("policy rule readback mismatch");
    }
    receipt.downloadedRules = {
      domains: actualRules.filter(rule => rule.id >= RULE_DOWNLOADED_BASE && rule.id < RULE_DOWNLOADED_KEYWORDS_BASE).length,
      keywords: actualRules.filter(rule => rule.id >= RULE_DOWNLOADED_KEYWORDS_BASE).length,
      listVersion: state.listVersion,
    };
    phase = "downloaded-rules-readback";
    if ((state.rulesApplied || state.keywordRulesApplied) &&
        (receipt.downloadedRules.domains !== state.rulesApplied || receipt.downloadedRules.keywords !== state.keywordRulesApplied)) {
      throw new Error("downloaded rule count readback mismatch");
    }
    // Chrome persists static ruleset enablement. Retire its legacy catch-all
    // only after the dynamic replacement and registrations are verified.
    phase = "ruleset-retirement";
    await chrome.declarativeNetRequest.updateEnabledRulesets({ enableRulesetIds: BASELINE_RULESETS,
      disableRulesetIds: [RULESET_LOCKDOWN] });
    phase = "ruleset-readback";
    const enabled = await chrome.declarativeNetRequest.getEnabledRulesets();
    if (BASELINE_RULESETS.some(id => !enabled.includes(id)) || enabled.includes(RULESET_LOCKDOWN)) {
      throw new Error("enabled ruleset readback mismatch");
    }
    receipt.layers.domains = receipt.layers.search = "applied";
    phase = "open-tabs";
    await enforceOpenTabs(state);
    receipt.status = "applied";
    receipt.appliedRevision = receipt.desiredRevision;
    receipt.checkedAt = Date.now();
    await setState({ appliedRevision: receipt.appliedRevision, enforcement: receipt });
    console.info("[hisn] rules applied", { mode: state.mode, strict: effectiveStrict(state),
      failClosed: state.failClosed, locked: isLocked(state), dynamicRules: dynamic.length });
  } catch (error) {
    // An authoritative unlock may be valid even though its browser repair
    // failed. Keep a previously installed strict catch-all until a later
    // complete application succeeds. Do not roll back a tightening.
    if (previousPolicy?.some(rule => rule.action.type === "block" && rule.condition.urlFilter === "*")
        && !dynamic.some(rule => rule.action.type === "block" && rule.condition.urlFilter === "*")) {
      try {
        if (replacedPolicy) {
          const currentRules = await chrome.declarativeNetRequest.getDynamicRules();
          await chrome.declarativeNetRequest.updateDynamicRules({
            removeRuleIds: currentRules.filter(rule => rule.id < RULE_DOWNLOADED_BASE).map(rule => rule.id),
            addRules: previousPolicy,
          });
        }
        receipt.retainedStrict = true;
        receipt.layers.domains = "pending";
      } catch { receipt.strictRecoveryUnconfirmed = true; }
    }
    receipt.status = "failed";
    receipt.failure = { code: phase, at: Date.now() };
    await setState({ enforcement: receipt });
    error.enforcementFailure = phase;
    throw error;
  }
}

// Chrome may return object keys/rules in a different order. Compare the full
// owned policy contract rather than treating an acknowledged write as proof.
export function canonicalRules(rules) {
  const canonical = value => Array.isArray(value) ? value.map(canonical)
    : value && typeof value === "object" ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;
  return JSON.stringify(rules.slice().sort((a, b) => a.id - b.id).map(canonical));
}
