/**
 * Hisn Protection — MV3 service worker.
 *
 * Three jobs:
 *   1. Keep the declarativeNetRequest rules in sync with the lock state.
 *   2. Stay in contact with the native macOS app, which owns the authoritative
 *      lock clock.
 *   3. FAIL CLOSED. If the native app stops answering while a lock is active,
 *      tighten to lockdown instead of relaxing. Every "safe" default here is
 *      the restrictive one, because the failure mode of a self-control tool is
 *      not "the user is annoyed", it is "the tool quietly stopped working and
 *      nobody noticed".
 */

import { acceptList } from "./lib/verify.js";
import { buildIndex, scorePage, isExempt } from "./lib/score.js";

const NATIVE_HOST = "app.hisn.bridge";
const LIST_BASE = "https://raw.githubusercontent.com/hisn-app/blocklist/lists";

const RULESET_BLOCKLIST = "blocklist";
const RULESET_KEYWORDS = "keywords";
const RULESET_LOCKDOWN = "lockdown";

// Dynamic rule id space. Static rules live in their own space, so these are
// free to reuse.
const RULE_STRICT_BLOCK_ALL = 1;
const RULE_STRICT_ALLOW = 2;
const RULE_CUSTOM_BLOCK_BASE = 100;

/// Downloaded blocklist rules start here, above everything `applyRules` owns.
/// The split is what lets a lock change and a list update coexist: each
/// function clears only its own range, so neither can wipe the other's rules.
const RULE_DOWNLOADED_BASE = 10000;

/** How long the native app may stay silent before we assume tampering. */
const HEARTBEAT_GRACE_MS = 5 * 60 * 1000;

const DEFAULT_STATE = {
  mode: "off",              // off | blocklist | strict
  lockUntil: 0,             // epoch ms; 0 = not locked
  allowlist: [],            // strict mode: the only domains permitted
  customBlocks: [],         // user-added domains
  listVersion: 0,
  rulesApplied: 0,          // downloaded rules actually installed, not claimed
  lastHeartbeat: 0,
  failClosed: false,        // true once we lost the native app mid-lock
  inspectText: true,        // page-text scoring
  textSensitivity: 50,      // 0-100, higher = stricter (see lib/score.js)

  // Has the native app ever answered? Distinguishes the two ways this
  // extension is legitimately run, which behave differently and must be
  // described differently:
  //
  //   standalone  — browser-only. Baseline blocking is on, the user authors
  //                 their own lists here, and nothing is unremovable.
  //   with the app — the app is the authority: it owns the lists, it can
  //                 impose a lock, and this side stops being editable.
  //
  // Never used to decide whether to FILTER. Baseline protection does not
  // depend on the app being there, and a field that could switch it off would
  // be one uninstall away from being the bypass.
  appPresent: false,
};

// --------------------------------------------------------------------------
// State
// --------------------------------------------------------------------------

async function getState() {
  const stored = await chrome.storage.local.get("state");
  return { ...DEFAULT_STATE, ...(stored.state || {}) };
}

async function setState(patch) {
  const next = { ...(await getState()), ...patch };
  await chrome.storage.local.set({ state: next });
  return next;
}

function isLocked(state) {
  return state.lockUntil > Date.now();
}

// --------------------------------------------------------------------------
// Rule application
// --------------------------------------------------------------------------

/**
 * Strict allowlist mode: deny everything, then carve out the allowlist.
 *
 * Two rules, not two thousand — `requestDomains` accepts a list, and one allow
 * rule at higher priority beats the catch-all block. Chrome's rule budget is
 * small enough that per-domain rules would be a real constraint.
 */
function strictRules(allowlist) {
  const rules = [
    {
      id: RULE_STRICT_BLOCK_ALL,
      priority: 1,
      action: {
        type: "redirect",
        redirect: { extensionPath: "/blocked.html?reason=strict" },
      },
      condition: { urlFilter: "*", resourceTypes: ["main_frame"] },
    },
  ];
  if (allowlist.length) {
    rules.push({
      id: RULE_STRICT_ALLOW,
      priority: 100,
      action: { type: "allow" },
      condition: {
        requestDomains: allowlist,
        resourceTypes: [
          "main_frame", "sub_frame", "script", "image", "media",
          "xmlhttprequest", "stylesheet", "font", "object", "websocket", "other",
        ],
      },
    });
  }
  return rules;
}

function customBlockRules(domains) {
  if (!domains.length) return [];
  return [{
    id: RULE_CUSTOM_BLOCK_BASE,
    priority: 50,
    action: {
      type: "redirect",
      redirect: { extensionPath: "/blocked.html?reason=custom" },
    },
    condition: {
      requestDomains: domains,
      resourceTypes: ["main_frame", "sub_frame"],
    },
  }];
}

async function applyRules(state) {
  const effectiveStrict = state.mode === "strict" || state.failClosed;

  const dynamic = [
    ...(effectiveStrict ? strictRules(state.allowlist) : []),
    ...customBlockRules(state.customBlocks),
  ];

  // Clear only the ids this function owns. Removing every dynamic rule here
  // would also delete the downloaded blocklist, so every lock state change
  // would silently unblock 148k domains until the next successful update.
  const existing = await chrome.declarativeNetRequest.getDynamicRules();
  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: existing
      .filter((r) => r.id < RULE_DOWNLOADED_BASE)
      .map((r) => r.id),
    addRules: dynamic,
  });

  // ── BASELINE PROTECTION IS UNCONDITIONAL ─────────────────────────────────
  // These two rulesets are never disabled. Not when unlocked, not when the
  // native app is absent, not on a fresh install.
  //
  // This used to be gated on `state.mode !== "off" || isLocked(state)`, and
  // that gate was inverted in a way that made the extension useless on its own:
  // `DEFAULT_STATE.mode` is "off", so a newly installed extension with no lock
  // running — which is every install, before any lock is ever started —
  // DISABLED the 148k-domain blocklist and blocked precisely nothing. The
  // product's whole premise is a blocklist that is always there; a lock makes
  // it stricter and unremovable, it is not the switch that turns it on.
  //
  // Anyone tempted to re-add a condition here should note what the two states
  // actually mean: "no lock" means the person has not committed to a period
  // yet, and "no app" means they installed only the browser half. Neither is a
  // request to stop filtering.
  await chrome.declarativeNetRequest.updateEnabledRulesets({
    enableRulesetIds: [RULESET_BLOCKLIST, RULESET_KEYWORDS],
    disableRulesetIds: [],
  });

  await syncTextScanning(state);

  console.info("[hisn] rules applied", {
    mode: state.mode,
    strict: effectiveStrict,
    failClosed: state.failClosed,
    locked: isLocked(state),
    dynamicRules: dynamic.length,
  });
}

// --------------------------------------------------------------------------
// Page-text scoring
// --------------------------------------------------------------------------

/** The compiled term list, loaded once and kept for the worker's lifetime.
 *  A service worker is torn down aggressively, so this is a cache, not state:
 *  everything it holds can be rebuilt from the bundled file. */
let termIndex = null;

async function getTermIndex() {
  if (termIndex) return termIndex;
  const res = await fetch(chrome.runtime.getURL("seed/terms.json"));
  termIndex = buildIndex(await res.json());
  return termIndex;
}

/**
 * Register or remove the content script that reads page text.
 *
 * Registered dynamically rather than declared in the manifest, because a
 * manifest `content_scripts` block is unconditional: it would inject into every
 * page on every site even with text scanning switched off. `persistAcrossSessions`
 * means Chrome re-registers it before the worker wakes on a cold start, so
 * there is no window where pages load unscanned while the worker spins up.
 */
async function syncTextScanning(state) {
  const wanted = state.inspectText || state.failClosed || isLocked(state);
  const existing = await chrome.scripting.getRegisteredContentScripts({
    ids: ["hisn-scan"],
  }).catch(() => []);

  if (!wanted) {
    if (existing.length) {
      await chrome.scripting.unregisterContentScripts({ ids: ["hisn-scan"] });
    }
    return;
  }
  if (existing.length) return;

  await chrome.scripting.registerContentScripts([{
    id: "hisn-scan",
    matches: ["<all_urls>"],
    js: ["content/scan.js"],
    runAt: "document_start",
    allFrames: true,
    persistAcrossSessions: true,
  }]);
  console.info("[hisn] page-text scanning registered");
}

/**
 * Score one page's text.
 *
 * The exempt-domain rail is applied HERE rather than in the content script, so
 * a page cannot talk its way out of it. It suppresses this layer only: the
 * domain blocklist and the URL keyword rules still apply to an exempt host, and
 * that narrowness is what makes granting an exemption safe.
 */
async function scoreText(zones, sender) {
  const state = await getState();
  if (!(state.inspectText || state.failClosed || isLocked(state))) {
    return { block: false };
  }

  const index = await getTermIndex();
  let host = "";
  try {
    host = new URL(sender?.url ?? "").hostname;
  } catch { /* opaque origin — no exemption, judge it on its text */ }
  if (host && isExempt(host, index)) return { block: false };

  const sensitivity = state.failClosed ? 80 : (state.textSensitivity ?? 50);
  const result = scorePage(zones, index, sensitivity);

  // Return the verdict and NOTHING else. Sending back which terms matched
  // would put that list one console.log away from being written somewhere it
  // survives, which is the record docs/THREAT_MODEL.md Part 5 forbids.
  return { block: result.block };
}

// --------------------------------------------------------------------------
// Native app link
// --------------------------------------------------------------------------

/**
 * Ask the native app for the authoritative lock state.
 *
 * The extension's own storage is not trustworthy as the source of truth: it
 * lives in a profile directory the user can edit. The native app holds the
 * clock in a location the filter itself protects.
 */
async function pollNative() {
  try {
    const reply = await chrome.runtime.sendNativeMessage(NATIVE_HOST, {
      type: "getLockState",
    });
    if (!reply || typeof reply.lockUntil !== "number") {
      throw new Error("malformed reply");
    }
    // Build the patch by PRESENCE, not by defaulting.
    //
    // The obvious version — `allowlist: reply.allowlist ?? []` — has a trap in
    // it that only shows up during an upgrade. If the bridge is an older build
    // that does not yet send a field, `??` substitutes the empty value and
    // silently deletes whatever the user had. For `customBlocks` that quietly
    // unblocks every domain they added by hand; for `inspectText` it would turn
    // a layer off. A field the bridge does not mention is a field we know
    // nothing about, and the safe reading of that is "leave it alone".
    const patch = {
      lockUntil: reply.lockUntil,
      mode: reply.mode ?? "blocklist",
      lastHeartbeat: Date.now(),
      failClosed: false,
      appPresent: true,
    };
    for (const key of ["allowlist", "customBlocks",
                       "inspectText", "textSensitivity", "hostKeywords"]) {
      if (key in reply) patch[key] = reply[key];
    }
    const state = await setState(patch);
    await applyRules(state);
    return true;
  } catch (err) {
    console.warn("[hisn] native host unreachable:", err?.message ?? err);
    await handleNativeLoss();
    return false;
  }
}

/**
 * The native app went away.
 *
 * If a lock was running, treat this as tampering — the user uninstalling the
 * app is the most obvious way to try to escape — and clamp down rather than
 * open up. If no lock was running, losing the app is unremarkable.
 */
async function handleNativeLoss() {
  const state = await getState();
  const silentFor = Date.now() - (state.lastHeartbeat || 0);

  // Never having heard from the app is not the app going away. A browser-only
  // install is a supported configuration, not a fault, and treating it as
  // tampering would clamp every standalone user into strict mode forever —
  // which is both wrong and the fastest way to get the extension removed.
  if (!state.appPresent) return;

  if (!isLocked(state)) return;
  if (silentFor < HEARTBEAT_GRACE_MS) return;
  if (state.failClosed) return;

  console.error("[hisn] native app silent during an active lock — failing closed");
  const next = await setState({ failClosed: true });
  await applyRules(next);
}

// --------------------------------------------------------------------------
// List updates
// --------------------------------------------------------------------------

async function fetchBytes(url) {
  const res = await fetch(url, { cache: "no-store" });
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return new Uint8Array(await res.arrayBuffer());
}

/**
 * Install a downloaded, verified blocklist as dynamic rules.
 *
 * Downloaded rules live in their OWN id space, above
 * `RULE_DOWNLOADED_BASE`, and `applyRules` only ever clears ids below it. Both
 * functions call `updateDynamicRules`, and without that split whichever ran
 * last would wipe the other's work — a lock change would silently delete the
 * blocklist, or a list update would silently delete strict mode.
 *
 * The ids in the artifact start at 1 and would collide with the strict-mode
 * rules, so they are reassigned on the way in rather than trusted.
 */
async function applyDownloadedRules(rules) {
  const existing = await chrome.declarativeNetRequest.getDynamicRules();
  const previous = existing
    .filter((r) => r.id >= RULE_DOWNLOADED_BASE)
    .map((r) => r.id);

  const remapped = rules.map((rule, i) => ({
    ...rule,
    id: RULE_DOWNLOADED_BASE + i,
  }));

  // One call, so the swap is atomic: there is no window in which the old rules
  // are gone and the new ones are not yet in.
  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: previous,
    addRules: remapped,
  });
  return remapped.length;
}

/**
 * Check for a newer blocklist, verify it, and actually apply it.
 *
 * ── WHAT WAS BROKEN HERE ──────────────────────────────────────────────────
 * This function used to pass `artifacts: new Map()`. It fetched the manifest,
 * checked its signature, recorded the version — and downloaded no rules at
 * all. The extension's blocking was therefore whatever JSON happened to be
 * hand-copied into the package at build time, and it never changed, however
 * many times this ran or how new the published list was. The version number in
 * the popup climbed while the rules behind it stood still, which is the exact
 * "looks healthy, enforces nothing" failure the threat model rates as worse
 * than being switched off.
 *
 * It also meant the hash-checking loop in `acceptList` had never once executed
 * in production. It does now: the artifact bytes go through it, so a CDN that
 * serves a valid manifest alongside a swapped rules file is caught.
 *
 * The two-step fetch is deliberate. The manifest is small and the rules file is
 * megabytes, so the version is checked first and the large download only
 * happens when there is genuinely something newer to install.
 *
 * A failed update remains a non-event: the rules already installed stay
 * installed. A stale blocklist still blocks; an empty one does not.
 */
async function updateList() {
  const state = await getState();
  try {
    const [manifestBytes, sigBytes] = await Promise.all([
      fetchBytes(`${LIST_BASE}/manifest.json`),
      fetchBytes(`${LIST_BASE}/manifest.json.sig`),
    ]);
    const signatureHex = new TextDecoder().decode(sigBytes).trim();

    const probe = await acceptList({
      manifestBytes,
      signatureHex,
      artifacts: new Map(),
      heldVersion: state.listVersion,
    });
    if (!probe.ok) {
      console.error("[hisn] list rejected:", probe.reason);
      return;
    }
    if (probe.manifest.version === state.listVersion && state.rulesApplied) {
      return;
    }

    const rulesBytes = await fetchBytes(`${LIST_BASE}/dnr_block_rules.json`);

    // Re-run acceptance WITH the artifact, so the signed manifest's SHA-256 is
    // checked against the bytes we are about to enforce. Verifying the manifest
    // alone proves only that someone signed a description of a file.
    const verified = await acceptList({
      manifestBytes,
      signatureHex,
      artifacts: new Map([["dnr_block_rules.json", rulesBytes]]),
      heldVersion: state.listVersion,
    });
    if (!verified.ok) {
      console.error("[hisn] rules rejected:", verified.reason);
      return;
    }

    let rules;
    try {
      rules = JSON.parse(new TextDecoder().decode(rulesBytes));
    } catch {
      console.error("[hisn] rules are not valid JSON — keeping current list");
      return;
    }

    // The client-side plausibility floor, matching the one the build and CI
    // already apply to domain counts. A validly signed list that collapsed to
    // a handful of rules is a broken build, and keeping yesterday's is
    // strictly safer than applying it.
    const expected = verified.manifest.dnr_rule_count;
    if (!Array.isArray(rules) || rules.length < 50
        || (expected && rules.length !== expected)) {
      console.error("[hisn] implausible ruleset:", rules?.length,
        "rules, manifest says", expected, "— keeping current list");
      return;
    }

    const applied = await applyDownloadedRules(rules);
    await setState({
      listVersion: verified.manifest.version,
      rulesApplied: applied,
    });
    console.info("[hisn] blocklist v%d applied — %d rules",
      verified.manifest.version, applied);
  } catch (err) {
    console.warn("[hisn] list update failed, keeping current list:",
      err?.message ?? err);
  }
}

// --------------------------------------------------------------------------
// Guards
// --------------------------------------------------------------------------

/** Entries in `next` that are not in `prev`. */
function added(next, prev) {
  const before = new Set(prev || []);
  return (next || []).filter((d) => !before.has(d));
}

/**
 * Refuse to relax settings while locked. Every path that could weaken
 * protection funnels through here.
 *
 * The list checks compare membership, not length. Counting was the obvious
 * version and it is wrong in the case that matters: swapping one allowed domain
 * for another leaves the count identical, and in strict mode — where the
 * allowlist is the only thing reachable at all — that swap is not a small
 * loosening, it is a complete bypass.
 */
async function guardedUpdate(patch) {
  const state = await getState();
  if (isLocked(state)) {
    const relaxing =
      (patch.mode && patch.mode === "off") ||
      (patch.mode === "blocklist" && state.mode === "strict") ||
      (patch.lockUntil !== undefined && patch.lockUntil < state.lockUntil) ||
      (patch.allowlist && added(patch.allowlist, state.allowlist).length > 0) ||
      // Dropping a custom block is the same move in the other direction.
      // options.js merges add-only before sending, but a UI-only guard is no
      // guard at all — anything can post this message from a devtools console.
      (patch.customBlocks &&
        added(state.customBlocks, patch.customBlocks).length > 0) ||
      // Switching text scanning off, or making it less sensitive, are both
      // weakenings and both wait for the lock to end. Turning it on or raising
      // sensitivity is tightening and is always allowed — the same asymmetry
      // every other guard in this file enforces.
      (patch.inspectText === false && state.inspectText) ||
      (patch.textSensitivity !== undefined &&
        patch.textSensitivity < state.textSensitivity);
    if (relaxing) {
      return { ok: false, reason: "locked", until: state.lockUntil };
    }
  }
  const next = await setState(patch);
  await applyRules(next);
  return { ok: true, state: next };
}

// --------------------------------------------------------------------------
// Wiring
// --------------------------------------------------------------------------

chrome.runtime.onInstalled.addListener(async () => {
  const state = await getState();
  await applyRules(state);
  await chrome.alarms.create("heartbeat", { periodInMinutes: 1 });
  await chrome.alarms.create("listUpdate", { periodInMinutes: 360 });
  await pollNative();
  await updateList();
});

chrome.runtime.onStartup.addListener(async () => {
  // Re-assert on every browser start. A service worker that never woke up is
  // a browser session with no rules applied.
  await applyRules(await getState());
  await pollNative();
});

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === "heartbeat") await pollNative();
  if (alarm.name === "listUpdate") await updateList();
});

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  (async () => {
    switch (msg?.type) {
      case "getState":
        sendResponse(await getState());
        break;
      case "update":
        sendResponse(await guardedUpdate(msg.patch || {}));
        break;
      case "forceSync":
        sendResponse({ ok: await pollNative() });
        break;
      case "scoreText":
        sendResponse(await scoreText(msg.zones || {}, _sender));
        break;
      default:
        sendResponse({ ok: false, reason: "unknown-message" });
    }
  })();
  return true; // async response
});

export { applyRules, guardedUpdate, strictRules, DEFAULT_STATE };
