/**
 * The decision contract — the browser's half of a two-language agreement.
 *
 * `blocklist/terms/policy_cases.json` says what a hostname decision must be for a
 * given mode, allowlist, custom-block list and published-list membership.
 * `PolicyContractTests.swift` asserts every case against `BlocklistStore`;
 * this file asserts every case against `decide`, a reference evaluator with
 * Chrome's precedence over the EXACT rules `policyRules` hands to
 * `updateDynamicRules`. If the two clients ever answer differently, one of
 * these two suites fails and names the case.
 *
 * Also pinned here: the lock-state decisions (`isLocked`, `effectiveStrict`,
 * `shouldFailClosed`) and the settings schema, which closes the internal-state
 * overwrite that used to let a devtools message disable fail-closed.
 */

import {
  decide, policyRules, isLocked, effectiveStrict, shouldFailClosed, tabsToBlock,
  validatePatch, bridgePatch, canonicalHost,
  HEARTBEAT_GRACE_MS, RULE_DOWNLOADED_BASE, PRIORITY_LIST, PRIORITY_HAND,
  PRIORITY_CATCHALL, PRIORITY_PLUMBING, PRIORITY_KEYWORDS, RESOURCE_TYPES,
} from "../lib/policy.js";

let failures = 0, checks = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}
function eq(actual, expected, label) {
  check(JSON.stringify(actual) === JSON.stringify(expected),
        `${label}\n        got ${JSON.stringify(actual)}\n        want ${JSON.stringify(expected)}`);
}

const base = { mode: "blocklist", lockUntil: 0, allowlist: [], customBlocks: [],
               failClosed: false, appPresent: false, lastHeartbeat: 0 };

// ── the shared fixture ─────────────────────────────────────────────────────
const fixture = JSON.parse(readFile("../../blocklist/terms/policy_cases.json"));
check(fixture.cases.length >= 20, "fixture has a meaningful number of cases");
const opts = (c) => ({ listed: c.listed, resourceType: c.resourceType ?? "main_frame",
                       initiator: c.initiator ?? null });
for (const c of fixture.cases) {
  const state = { ...base, mode: c.mode, allowlist: c.allowlist, customBlocks: c.customBlocks };
  const got = decide(state, c.host, opts(c));
  check(got === c.expect, `${c.host} ${c.resourceType ?? "main_frame"} (${c.mode}): ${c.why}\n        got ${got}, want ${c.expect}`);
}
check(fixture.cases.some((c) => c.browserOnly), "the fixture marks the browser-only plumbing carve-out");

// Fail-closed is strict mode with the last allowlist, whatever `mode` says.
for (const c of fixture.cases.filter((c) => c.mode === "strict")) {
  const state = { ...base, mode: "blocklist", failClosed: true,
                  allowlist: c.allowlist, customBlocks: c.customBlocks };
  check(decide(state, c.host, opts(c)) === c.expect,
        `fail-closed behaves as strict: ${c.host} — ${c.why}`);
}

// ── the rules themselves ───────────────────────────────────────────────────
{
  const rules = policyRules({ ...base, mode: "strict",
                              allowlist: ["google.com", "api.github.com", "GitHub.com."],
                              customBlocks: ["mail.google.com", "reddit.com", "not a host"] });
  check(rules.every((r) => r.id < RULE_DOWNLOADED_BASE),
        "every policy rule id stays below the downloaded range applyRules must not touch");
  const ids = rules.map((r) => r.id);
  check(new Set(ids).size === ids.length, "rule ids are unique");
  const allow = rules.filter((r) => r.action.type === "allow" && r.condition.requestDomains);
  const block = rules.filter((r) => r.action.type === "redirect" && r.condition.requestDomains);
  eq(allow.map((r) => r.condition.requestDomains).flat().sort(),
     ["api.github.com", "github.com", "google.com"],
     "allow rules carry the canonical allowlist (case folded, trailing dot dropped)");
  eq(block.map((r) => r.condition.requestDomains).flat().sort(),
     ["mail.google.com", "reddit.com"],
     "an entry that is not a host is dropped rather than sent to Chrome, where it would fail the whole update");
  for (const a of allow) {
    const depth = a.condition.requestDomains[0].split(".").length;
    check(a.priority === PRIORITY_HAND + 2 * depth, `allow priority encodes depth ${depth}`);
    const b = block.find((r) => r.condition.requestDomains[0].split(".").length === depth);
    if (b) check(b.priority === a.priority + 1, `block at depth ${depth} sits one above the allow`);
  }
  check(rules.filter((r) => r.condition.requestDomains).every((r) => r.priority > PRIORITY_KEYWORDS),
        "every hand-list rule outranks everything published");
  check(rules.filter((r) => r.condition.urlFilter === "*").every((r) => r.priority === PRIORITY_CATCHALL),
        "strict mode's catch-all is the floor, so any allow beats it");
  const carveOut = rules.find((r) => r.condition.initiatorDomains);
  check(carveOut.priority === PRIORITY_PLUMBING && PRIORITY_PLUMBING < PRIORITY_LIST,
        "the plumbing carve-out sits BELOW the published list: a listed domain stays blocked");
  check(PRIORITY_CATCHALL < PRIORITY_PLUMBING && PRIORITY_LIST < PRIORITY_KEYWORDS
        && PRIORITY_KEYWORDS < PRIORITY_HAND, "the ladder is in order");

  // Rule 6: the catch-all covers every resource type, split into documents
  // (block page) and everything else (dropped); the plumbing carve-out names
  // the allowlist as initiators and only the plumbing types.
  const catchAll = rules.filter((r) => r.condition.urlFilter === "*");
  const typesCovered = new Set(catchAll.flatMap((r) => r.condition.resourceTypes));
  for (const t of ["main_frame", "sub_frame", "media", "websocket", "xmlhttprequest", "script", "image", "object"]) {
    check(typesCovered.has(t), `strict catch-all covers ${t}`);
  }
  const plumbing = rules.find((r) => r.condition.initiatorDomains);
  check(plumbing && plumbing.action.type === "allow", "an allowed page may load its plumbing");
  eq([...plumbing.condition.initiatorDomains].sort(), ["api.github.com", "github.com", "google.com"],
     "the plumbing carve-out is keyed on the allowlist as initiator");
  for (const t of ["sub_frame", "media", "websocket", "object", "main_frame"]) {
    check(!plumbing.condition.resourceTypes.includes(t), `plumbing never includes ${t}`);
  }
  const embeddedBlocks = rules.filter((r) => r.action.type === "block" && r.condition.requestDomains);
  check(embeddedBlocks.length === block.length, "custom blocks drop embedded traffic too, per depth");

  const noStrict = policyRules({ ...base, allowlist: ["github.com"], customBlocks: ["reddit.com"] });
  check(noStrict.some((r) => r.action.type === "allow" && r.priority > PRIORITY_LIST),
        "RULE 2: the allowlist is installed in blocklist mode too, above the published rulesets");
  check(!noStrict.some((r) => r.condition.urlFilter === "*" || r.condition.initiatorDomains),
        "no catch-all and no initiator carve-out outside strict mode");
  check(noStrict.some((r) => r.action.type === "redirect"),
        "custom blocks are installed in blocklist mode");
  eq(policyRules(base), [], "off/blocklist with empty lists installs nothing dynamic");
}

// ── lock state ─────────────────────────────────────────────────────────────
{
  const now = 1_700_000_000_000;
  check(isLocked({ lockUntil: now + 1 }, now), "locked until a future instant");
  check(!isLocked({ lockUntil: now }, now), "the deadline instant itself is unlocked");
  check(!isLocked({ lockUntil: 0 }, now), "0 means no lock");
  check(!isLocked({}, now), "a state with no lockUntil is unlocked, not NaN-locked");

  check(effectiveStrict({ mode: "strict" }), "strict when asked");
  check(effectiveStrict({ mode: "blocklist", failClosed: true }), "strict when clamped");
  check(!effectiveStrict({ mode: "blocklist", failClosed: "true" }),
        "failClosed must be the boolean true, not a truthy string");

  const locked = { ...base, appPresent: true, lockUntil: now + 3_600_000,
                   lastHeartbeat: now - HEARTBEAT_GRACE_MS - 1 };
  check(shouldFailClosed(locked, now), "RULE 5: silent past the grace period during a lock → clamp");
  check(!shouldFailClosed({ ...locked, appPresent: false }, now),
        "RULE 5: never heard from the app → browser-only, no clamp");
  check(!shouldFailClosed({ ...locked, lockUntil: 0 }, now), "no lock → no clamp");
  check(!shouldFailClosed({ ...locked, lastHeartbeat: now - HEARTBEAT_GRACE_MS + 1 }, now),
        "inside the grace period → not yet");
  check(!shouldFailClosed({ ...locked, failClosed: true }, now), "already clamped → idempotent");
  check(!shouldFailClosed({ ...locked, lockUntil: now }, now),
        "RULE 4: at the effective deadline the lock is over, so silence no longer clamps");
}

// ── pages already open when the rules tighten ─────────────────────────────
{
  const tabs = [
    { id: 1, url: "https://github.com/x" },
    { id: 2, url: "https://tube.example/watch?v=1" },
    { id: 3, url: "https://reddit.com/r/x" },
    { id: 4, url: "chrome://extensions" },
    { id: 5, url: "chrome-extension://abc/blocked.html?reason=strict" },
    { id: 6, url: "" },
  ];
  const strict = { ...base, mode: "strict", allowlist: ["github.com"], customBlocks: ["reddit.com"] };
  eq(tabsToBlock(tabs, strict), [{ id: 2, reason: "strict" }, { id: 3, reason: "custom" }],
     "strict mode: open tabs off the allowlist are sent to the block page; the allowed one and non-web tabs stay");
  const blocklist = { ...base, customBlocks: ["reddit.com"] };
  eq(tabsToBlock(tabs, blocklist), [{ id: 3, reason: "custom" }],
     "blocklist mode: only a custom-blocked open tab is closed — the published list cannot be judged here");
  eq(tabsToBlock(tabs, base), [], "nothing strict, nothing custom: nothing to do");
  eq(tabsToBlock(tabs, { ...base, failClosed: true, allowlist: ["github.com"] }),
     [{ id: 2, reason: "strict" }, { id: 3, reason: "strict" }],
     "fail-closed behaves as strict for open tabs too");
}

// ── hostnames ──────────────────────────────────────────────────────────────
{
  eq(canonicalHost("https://www.Example.com/watch?v=1"), "example.com", "URL → apex");
  eq(canonicalHost("user@host.example.org:8080"), "host.example.org", "userinfo and port stripped");
  eq(canonicalHost("Example.COM."), "example.com", "case and trailing dot");
  eq(canonicalHost("localhost"), null, "a single label is not a domain");
  eq(canonicalHost("192.168.1.1"), null, "an address is not a domain");
  eq(canonicalHost("xn--mgbh0fb.com"), "xn--mgbh0fb.com", "punycode is ASCII and accepted");
  eq(canonicalHost("مثال.com"), null, "raw Unicode is refused — DNR requires ASCII");
  eq(canonicalHost("-bad.com"), null, "a label may not start with a hyphen");
  eq(canonicalHost(""), null, "empty");
  eq(canonicalHost(null), null, "null");
}

// ── settings schema ────────────────────────────────────────────────────────
{
  // THE bypasses this schema exists to close.
  for (const field of ["appPresent", "failClosed", "lastHeartbeat", "listVersion",
                       "rulesApplied", "disputed"]) {
    const r = validatePatch({ [field]: false });
    check(!r.ok && r.reason === "unknown-field" && r.field === field,
          `internal field ${field} cannot be set through a settings message`);
  }
  const mixed = validatePatch({ mode: "strict", appPresent: false });
  check(!mixed.ok, "one internal field refuses the whole patch — no partial writes");

  const good = validatePatch({
    mode: "strict", lockUntil: 1_700_000_000_000,
    allowlist: ["https://GitHub.com/", "github.com", "api.github.com."],
    customBlocks: [], textAllow: ["Example.org"],
    customTerms: [" word ", "word"], ignoreTerms: ["x"],
    inspectText: true, textSensitivity: 80,
  });
  check(good.ok, "a well-formed patch is accepted");
  eq(good.patch.allowlist, ["github.com", "api.github.com"], "hosts canonicalised and de-duplicated");
  eq(good.patch.customTerms, ["word"], "terms trimmed and de-duplicated");

  const bad = [
    [{ mode: "lockdown" }, "invalid-field", "mode outside the enum"],
    [{ lockUntil: -1 }, "invalid-field", "negative lockUntil"],
    [{ lockUntil: 1.5 }, "invalid-field", "fractional lockUntil"],
    [{ lockUntil: "9999999999999" }, "invalid-field", "lockUntil as a string"],
    [{ textSensitivity: 101 }, "invalid-field", "sensitivity above 100"],
    [{ textSensitivity: "50" }, "invalid-field", "sensitivity as a string"],
    [{ inspectText: "false" }, "invalid-field", "inspectText as a string"],
    [{ allowlist: "github.com" }, "invalid-field", "a list must be an array"],
    [{ allowlist: ["not a host"] }, "invalid-field", "an invalid host refuses the patch"],
    [{ allowlist: [42] }, "invalid-field", "a non-string entry"],
    [{ customTerms: [""] }, "invalid-field", "an empty term"],
    [{ customTerms: ["x".repeat(101)] }, "invalid-field", "an oversized term"],
    [{ allowlist: Array.from({ length: 5001 }, (_, i) => `h${i}.example.com`) }, "invalid-field", "an oversized list"],
    [null, "invalid-patch", "null patch"],
    [[], "invalid-patch", "array patch"],
  ];
  for (const [patch, reason, label] of bad) {
    const r = validatePatch(patch);
    check(!r.ok && r.reason === reason, `refused: ${label} (${JSON.stringify(r)})`);
  }
  eq(validatePatch({}), { ok: true, patch: {} }, "an empty patch is a no-op, not an error");
}

// ── the bridge reply ───────────────────────────────────────────────────────
{
  eq(bridgePatch({ lockUntil: 1, mode: "strict", allowlist: ["GitHub.com"], customBlocks: "oops",
                   inspectText: true, textSensitivity: 200, extra: 1, appPresent: false }),
     { mode: "strict", allowlist: ["github.com"], inspectText: true },
     "a malformed field is left alone, unknown fields are ignored, good fields are canonical");
  eq(bridgePatch({ lockUntil: 0 }), {},
     "a field the bridge does not mention is not touched (upgrade safety)");
}

// The static rules shipped in the package sit on the ladder too — they are
// what a fresh install enforces before any download.
{
  const staticBlock = JSON.parse(readFile("../rules/dnr_block_rules.json"));
  const staticKeywords = JSON.parse(readFile("../rules/dnr_keyword_rules.json"));
  check(staticBlock.every((r) => r.priority === PRIORITY_LIST),
        "static domain rules sit at PRIORITY_LIST");
  check(staticKeywords.every((r) => r.priority === PRIORITY_KEYWORDS),
        "static keyword rules sit at PRIORITY_KEYWORDS");
  const covered = new Set(staticBlock.flatMap((r) => r.condition.resourceTypes));
  eq([...covered].sort(), [...RESOURCE_TYPES.list].sort(),
     "decide() models the resource types the static list really covers");
}

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} policy checks failed`);
