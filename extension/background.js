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

const NATIVE_HOST = "app.hisn.bridge";
const LIST_BASE = "https://raw.githubusercontent.com/hisn-app/blocklist/lists";

const RULESET_BLOCKLIST = "blocklist";
const RULESET_LOCKDOWN = "lockdown";

// Dynamic rule id space. Static rules live in their own space, so these are
// free to reuse.
const RULE_STRICT_BLOCK_ALL = 1;
const RULE_STRICT_ALLOW = 2;
const RULE_CUSTOM_BLOCK_BASE = 100;

/** How long the native app may stay silent before we assume tampering. */
const HEARTBEAT_GRACE_MS = 5 * 60 * 1000;

const DEFAULT_STATE = {
  mode: "off",              // off | blocklist | strict
  lockUntil: 0,             // epoch ms; 0 = not locked
  allowlist: [],            // strict mode: the only domains permitted
  customBlocks: [],         // user-added domains
  listVersion: 0,
  lastHeartbeat: 0,
  failClosed: false,        // true once we lost the native app mid-lock
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

  const existing = await chrome.declarativeNetRequest.getDynamicRules();
  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: existing.map((r) => r.id),
    addRules: dynamic,
  });

  // The bundled blocklist stays on in every mode except a genuinely unlocked
  // "off" — turning it off while locked is exactly what we are preventing.
  const blocklistOn = state.mode !== "off" || isLocked(state) || state.failClosed;

  await chrome.declarativeNetRequest.updateEnabledRulesets({
    enableRulesetIds: blocklistOn ? [RULESET_BLOCKLIST] : [],
    disableRulesetIds: blocklistOn ? [] : [RULESET_BLOCKLIST],
  });

  console.info("[hisn] rules applied", {
    mode: state.mode,
    strict: effectiveStrict,
    failClosed: state.failClosed,
    locked: isLocked(state),
    dynamicRules: dynamic.length,
  });
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
    const state = await setState({
      lockUntil: reply.lockUntil,
      mode: reply.mode ?? "blocklist",
      allowlist: reply.allowlist ?? [],
      customBlocks: reply.customBlocks ?? [],
      lastHeartbeat: Date.now(),
      failClosed: false,
    });
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
 * Check for a newer blocklist and verify it before doing anything with it.
 *
 * A failed update is a non-event: we keep the list we already trust. That is
 * always the correct trade — a stale blocklist still blocks.
 */
async function updateList() {
  const state = await getState();
  try {
    const [manifestBytes, sigBytes] = await Promise.all([
      fetchBytes(`${LIST_BASE}/manifest.json`),
      fetchBytes(`${LIST_BASE}/manifest.json.sig`),
    ]);
    const signatureHex = new TextDecoder().decode(sigBytes).trim();

    const result = await acceptList({
      manifestBytes,
      signatureHex,
      artifacts: new Map(),
      heldVersion: state.listVersion,
    });

    if (!result.ok) {
      console.error("[hisn] list rejected:", result.reason);
      return;
    }
    if (result.manifest.version === state.listVersion) return;

    await setState({ listVersion: result.manifest.version });
    console.info("[hisn] list manifest accepted, version",
      result.manifest.version);
  } catch (err) {
    console.warn("[hisn] list update failed, keeping current list:",
      err?.message ?? err);
  }
}

// --------------------------------------------------------------------------
// Guards
// --------------------------------------------------------------------------

/**
 * Refuse to relax settings while locked. Every path that could weaken
 * protection funnels through here.
 */
async function guardedUpdate(patch) {
  const state = await getState();
  if (isLocked(state)) {
    const relaxing =
      (patch.mode && patch.mode === "off") ||
      (patch.mode === "blocklist" && state.mode === "strict") ||
      (patch.lockUntil !== undefined && patch.lockUntil < state.lockUntil) ||
      (patch.allowlist && patch.allowlist.length > state.allowlist.length);
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
      default:
        sendResponse({ ok: false, reason: "unknown-message" });
    }
  })();
  return true; // async response
});

export { applyRules, guardedUpdate, strictRules, DEFAULT_STATE };
