/**
 * The native app link.
 *
 * The extension's own storage is not trustworthy as the source of truth: it
 * lives in a profile directory the user can edit. The macOS app holds the lock
 * clock in a location its own filter protects, so its reply is the authority.
 * A silent app during a lock is treated as tampering; a silent app with no lock
 * running — or one that has never answered, which is a browser-only install —
 * is unremarkable.
 */

import { HEARTBEAT_GRACE_MS, bridgePatch, shouldFailClosed } from "../lib/policy.js";
import { getState, setState } from "./state.js";
import { applyRules } from "./rules.js";

const NATIVE_HOST = "app.hisn.bridge";

/** A bridge that never answers must not hold every other transaction behind
 *  it — and must still count as silence, so fail-closed can engage. */
const NATIVE_TIMEOUT_MS = 10 * 1000;

function withTimeout(promise, ms, what) {
  let timer;
  return Promise.race([
    promise,
    new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(`${what} timed out`)), ms); }),
  ]).finally(() => clearTimeout(timer));
}

/**
 * Whether the browser lets this extension run in private/incognito windows.
 *
 * Chrome's "Allow in Incognito" switch is the person's, not the extension's:
 * a spanning manifest makes one process possible but does not turn the
 * permission on. The Mac app records this answer and shows a private-window
 * gap it would otherwise miss. Older Chrome versions and other engines may
 * not offer the API; `undefined` means "no answer", not "not allowed".
 */
function incognitoAccess() {
  return new Promise((resolve) => {
    let done = false;
    const finish = (value) => {
      if (done) return;
      done = true;
      resolve(value);
    };
    try {
      if (typeof chrome?.extension?.isAllowedIncognitoAccess !== "function") {
        finish(undefined);
        return;
      }
      // A browser API that never calls back must not hold the heartbeat.
      const timer = setTimeout(() => finish(undefined), 1000);
      chrome.extension.isAllowedIncognitoAccess((allowed) => {
        clearTimeout(timer);
        finish(!!allowed);
      });
    } catch {
      finish(undefined);
    }
  });
}

/**
 * Ask the native app for the authoritative lock state.
 *
 * A malformed or timed-out reply is a visibility failure first: it marks the
 * bridge unavailable and runs the same loss path as a missing host. A valid
 * reply proves connectivity even when applying its settings fails; that repair
 * failure is reported independently and is not relabelled as native loss.
 */
export async function pollNative() {
  let reply;
  let incognitoAllowed;
  try {
    incognitoAllowed = await incognitoAccess();
    const message = { type: "getLockState" };
    if (typeof incognitoAllowed === "boolean") message.incognitoAllowed = incognitoAllowed;
    reply = await withTimeout(
      chrome.runtime.sendNativeMessage(NATIVE_HOST, message),
      NATIVE_TIMEOUT_MS, "native host");
    if (!reply || !Number.isFinite(reply.lockUntil)) {
      throw new Error("malformed reply");
    }
  } catch (err) {
    const reason = reply?.reason === "authority-unreachable" ? "authority-unreachable"
      : /timed out/.test(err?.message || "") ? "native-timeout"
      : /malformed reply/.test(err?.message || "") ? "native-malformed"
      : "native-unavailable";
    console.warn("[hisn] native host unreachable:", err?.message ?? err);
    await setState({ bridgeConnection: { version: 1, status: "unavailable", reason, checkedAt: Date.now() } });
    try { await handleNativeLoss(); } catch (repairError) {
      return { ok: false, connected: false, reason: "enforcement-failed", nativeReason: reason };
    }
    return { ok: false, connected: false, reason };
  }
  // A valid authoritative reply proves connectivity even when applying its
  // settings fails. Preserve that lock/ownership and report the repair failure
  // independently; do not relabel it as native loss or standalone operation.
  try {
    // Build the patch by PRESENCE, not by defaulting.
    //
    // The obvious version — `allowlist: reply.allowlist ?? []` — has a trap in
    // it that only shows up during an upgrade. If the bridge is an older build
    // that does not yet send a field, `??` substitutes the empty value and
    // silently deletes whatever the user had. For `customBlocks` that quietly
    // unblocks every domain they added by hand; for `inspectText` it would turn
    // a layer off. A field the bridge does not mention is a field we know
    // nothing about, and the safe reading of that is "leave it alone".
    //
    // `bridgePatch` applies that rule and one more: a field the reply mentions
    // with a value of the wrong shape is ALSO left alone. It crossed a pipe as
    // JSON; a malformed list is not an empty list.
    const patch = {
      lockUntil: Math.max(0, Math.floor(reply.lockUntil)),
      // `mode` is deliberately absent here. The bridge sends it on every reply
      // today, and a field a reply does not mention must be left alone; a
      // default of "blocklist" here would rewrite a running Strict lock to
      // Standard if an older or broken bridge omitted it.
      ...bridgePatch(reply),
      lastHeartbeat: Date.now(),
      failClosed: false,
      appPresent: true,
      bridgeConnection: { version: 1, status: "connected", checkedAt: Date.now() },
    };
    const state = await setState(patch);
    // The Mac app's language, which pages set to Automatic follow
    // (lib/i18n.js). Beside the protection state, not in it: it is a
    // preference, like the person's own choice of language.
    if (reply.appLanguage === "ar" || reply.appLanguage === "en") {
      chrome.storage.local.set({ appLanguage: reply.appLanguage }).catch(() => {});
    }
    await applyRules(state);
    return { ok: true, connected: true };
  } catch (err) {
    console.warn("[hisn] authoritative browser settings could not be applied:", err?.message ?? err);
    return { ok: false, connected: true, reason: "enforcement-failed" };
  }
}

/**
 * The native app went away.
 *
 * If a lock was running, treat this as tampering — the user uninstalling the
 * app is the most obvious way to try to escape — and clamp down rather than
 * open up. If no lock was running, losing the app is unremarkable.
 */
export async function handleNativeLoss() {
  const state = await getState();

  // Never having heard from the app is not the app going away. A browser-only
  // install is a supported configuration, not a fault, and treating it as
  // tampering would clamp every standalone user into strict mode forever —
  // which is both wrong and the fastest way to get the extension removed.
  // That gate, the lock check, the grace period and the already-clamped check
  // are all in `shouldFailClosed`, where the tests can see them.
  if (!shouldFailClosed(state)) {
    // Nothing to change — but re-assert what is in force anyway. A failed
    // heartbeat used to return here without touching the rules, so once
    // fail-closed was on (or in a browser-only install) nothing ever
    // re-applied the rulesets or strict mode again, whatever happened to them.
    await applyRules(state);
    return;
  }

  console.error("[hisn] native app silent for %d minutes during an active lock "
                + "— failing closed", HEARTBEAT_GRACE_MS / 60000);
  const next = await setState({ failClosed: true });
  await applyRules(next);
}
