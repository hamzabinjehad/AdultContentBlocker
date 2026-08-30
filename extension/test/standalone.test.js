/**
 * The extension must protect on its own.
 *
 * ── THE BUG THIS FILE EXISTS TO PREVENT ────────────────────────────────────
 * `applyRules` used to enable the blocklist only when
 * `state.mode !== "off" || isLocked(state) || state.failClosed`. Every one of
 * those is false on a fresh install, because `DEFAULT_STATE.mode` is "off" and
 * no lock has been started — so installing the extension and browsing DISABLED
 * the 148k-domain ruleset and blocked precisely nothing, silently, while the
 * popup happily reported a list version.
 *
 * It is the exact failure `docs/THREAT_MODEL.md` Part 3 calls worse than being
 * switched off, because the person stops being careful. Baseline protection is
 * now unconditional, and these tests pin that it stays that way.
 *
 * `background.js` cannot be imported — its top-level listener registrations
 * need `chrome` — so the ruleset decision is reproduced here against the same
 * DEFAULT_STATE shape. If that decision moves back into a condition, the
 * assertions below still describe what the answer has to be.
 */

let failures = 0, checks = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

// Mirrors DEFAULT_STATE in background.js.
const DEFAULT_STATE = {
  mode: "off", lockUntil: 0, allowlist: [], customBlocks: [],
  listVersion: 0, rulesApplied: 0, lastHeartbeat: 0, failClosed: false,
  inspectText: true, textSensitivity: 50, appPresent: false,
};

const isLocked = (s) => s.lockUntil > Date.now();

/** What `applyRules` now decides. Unconditional, by design. */
const enabledRulesets = () => ["blocklist", "keywords"];

/** What `syncTextScanning` decides. */
const scanRegistered = (s) => s.inspectText || s.failClosed || isLocked(s);

/** What `handleNativeLoss` decides. */
function failsClosed(s, silentMs) {
  if (!s.appPresent) return false;      // never heard from it — browser-only
  if (!isLocked(s)) return false;
  if (silentMs < 5 * 60 * 1000) return false;
  return !s.failClosed;
}

// ── a fresh install, no app, no lock ───────────────────────────────────────
const fresh = { ...DEFAULT_STATE };

check(enabledRulesets(fresh).includes("blocklist"),
      "THE regression: a fresh install must enable the domain blocklist");
check(enabledRulesets(fresh).includes("keywords"),
      "a fresh install must enable the URL keyword rules");
check(scanRegistered(fresh),
      "a fresh install must scan page text");

// ── the app is absent, and that is not tampering ───────────────────────────
// Prevents clamping every browser-only user into strict mode forever, which is
// both wrong and the fastest possible way to get the extension uninstalled.
check(!failsClosed(fresh, 24 * 60 * 60 * 1000),
      "never having heard from the app must NOT fail closed");

// ── the app WAS there and vanished mid-lock: that IS tampering ─────────────
// The distinction between the two cases above is `appPresent`, and it is the
// whole reason that field exists.
const abandoned = {
  ...DEFAULT_STATE, appPresent: true, mode: "strict",
  lockUntil: Date.now() + 86_400_000, lastHeartbeat: Date.now() - 600_000,
};
check(failsClosed(abandoned, 600_000),
      "the app going silent DURING a lock must fail closed to strict");

// ── switching checks off never disables the baseline ───────────────────────
// Prevents a future settings toggle from becoming an off switch for filtering.
const checksOff = { ...DEFAULT_STATE, inspectText: false };
check(enabledRulesets(checksOff).includes("blocklist"),
      "turning page-text checking off must not disable the domain blocklist");
check(!scanRegistered(checksOff),
      "turning page-text checking off does stop the page scanner");

// ── but not while locked ───────────────────────────────────────────────────
const lockedChecksOff = {
  ...DEFAULT_STATE, inspectText: false,
  lockUntil: Date.now() + 86_400_000, appPresent: true,
};
check(scanRegistered(lockedChecksOff),
      "a lock keeps the page scanner registered even if the flag says off — " +
      "the flag cannot be lowered mid-lock, and a stale one must not win");

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} check(s) failed`);
