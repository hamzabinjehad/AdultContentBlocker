/**
 * Worker state: the one place the browser's protection state is read,
 * validated against its versioned receipt, and written.
 *
 * State is deliberately a plain object in `chrome.storage.local` rather than a
 * class. The native app is the authority for anything that can lock, so this
 * side treats itself as a cache of an externally-owned policy: it may read,
 * compare and report, but a patch that weakens protection never reaches
 * `setState` without passing `worker/updates.js` first.
 *
 * The receipt (`enforcement`) is the other half of that idea. A stored claim
 * that rules were applied is historical evidence; it only becomes current when
 * the same worker instance has re-read Chrome's rules and found them exactly
 * as expected. `getState` demotes anything else to "pending", which is what
 * stops a restart, an update or a disabled extension from showing green.
 */

import { createToolbarStatus } from "../lib/toolbar-status.js";
import { resolveLanguage } from "../lib/i18n.js";

// A receipt from a previous worker is historical evidence. This worker must
// read Chrome's rules and registrations before presenting it as current.
export const WORKER_SESSION = `${Date.now()}-${Math.random()}`;

const toolbarStatus = createToolbarStatus(chrome.action);
const browserLanguage = () => {
  try { return chrome.i18n?.getUILanguage?.() ?? "en"; } catch { return "en"; }
};
let toolbarLanguage = resolveLanguage("auto", browserLanguage());
// UI writes are deliberately not awaited by rule application or native IPC.
void toolbarStatus.update(undefined, toolbarLanguage);

export { toolbarStatus };

export const DEFAULT_STATE = {
  mode: "off",              // off | blocklist | strict
  lockUntil: 0,             // epoch ms; 0 = not locked
  allowlist: [],            // always reachable: overrides the published list
                            // and keyword rules; in strict mode, the ONLY
                            // domains reachable. See lib/policy.js rule 2.
  customBlocks: [],         // user-added domains
  customTerms: [],          // user-added words, scored like the compiled list
  listVersion: 0,
  rulesApplied: 0,          // downloaded rules actually installed, not claimed
  keywordRulesApplied: 0,   // downloaded keyword rules, same rule
  lastListUpdate: 0,        // epoch ms of the last generation installed
  lastHeartbeat: 0,
  desiredRevision: 0,
  appliedRevision: 0,
  enforcement: null,        // versioned browser API receipt, not stored intent
  failClosed: false,        // true once we lost the native app mid-lock
  inspectText: true,        // page-text scoring
  inspectVisual: false,     // explicit opt-in; model quality is not universal coverage
  personBlur: "off",        // optional estimated-face preference, not adult content
  blockShortVideo: false,   // opt-in: Shorts, Reels, Explore, TikTok (rules/short_video.json)
  textSensitivity: 50,      // 0-100, higher = stricter (see lib/score.js)
  textAllow: [],            // hosts the user reported as WRONG page-text blocks;
                            // page-text scoring skips them. Extension-local (the
                            // app does not own this list), and add-only while
                            // locked — see guardedUpdate. Never the domain
                            // blocklist's business: this suppresses the text
                            // layer only, exactly like the compiled exempt set.
  ignoreTerms: [],          // individual words the user marked wrong on a block
                            // page — a medical term, an over-broad stem. They
                            // stop counting as adult text everywhere. Normalised
                            // to the list's spelling, extension-local, add-only
                            // while locked. The opposite number of customTerms.
  disputed: [],             // {host, at} for reports filed DURING a lock, when
                            // the exemption cannot take effect yet. Holds the
                            // host the user calls safe and the time — never the
                            // words that matched, which stay out of durable
                            // storage entirely (docs/THREAT_MODEL.md Part 5).

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

/**
 * The fields whose change invalidates an enforcement receipt.
 *
 * Kept as one explicit list rather than hashing the whole state object: a
 * timestamp or a UI preference must not force a rule re-read, and a field that
 * changes enforcement but is forgotten here would let a receipt outlive the
 * policy it describes.
 */
export function desiredPolicyKey(state) {
  return JSON.stringify([state.mode, state.lockUntil, state.failClosed, state.allowlist, state.customBlocks,
    state.customTerms, state.inspectText, state.textSensitivity, state.textAllow, state.ignoreTerms,
    state.inspectVisual, state.personBlur, state.personRegion, state.personEffect, state.personMedia,
    state.blockShortVideo,
    state.listVersion, state.rulesApplied, state.keywordRulesApplied]);
}

function receiptIsCurrent(state) {
  const receipt = state.enforcement;
  return receipt?.version === 1 && receipt.worker === WORKER_SESSION
    && receipt.policyKey === desiredPolicyKey(state);
}

export async function getState() {
  const stored = await chrome.storage.local.get(["state", "uiLanguage", "appLanguage"]);
  toolbarLanguage = resolveLanguage(stored.uiLanguage, browserLanguage(), stored.appLanguage);
  const state = { ...DEFAULT_STATE, ...(stored.state || {}) };
  if (!receiptIsCurrent(state)) {
    state.enforcement = { version: 1, status: "pending", desiredRevision: state.desiredRevision,
      appliedRevision: state.appliedRevision, layers: {} };
  }
  refreshToolbar(state);
  return state;
}

function refreshToolbar(state) {
  // The toolbar cannot promote a historical receipt or mere model readiness.
  void toolbarStatus.update(receiptIsCurrent(state) ? state : { ...state, enforcement: undefined },
                            toolbarLanguage);
}

export async function setState(patch) {
  const previous = await getState();
  const next = { ...previous, ...patch };
  if (desiredPolicyKey(next) !== desiredPolicyKey(previous)) {
    next.desiredRevision = (previous.desiredRevision || 0) + 1;
    next.enforcement = { version: 1, status: "pending", desiredRevision: next.desiredRevision,
      appliedRevision: previous.appliedRevision, layers: {} };
  }
  await chrome.storage.local.set({ state: next });
  refreshToolbar(next);
  return next;
}
