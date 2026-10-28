/**
 * Hisn Protection — MV3 service worker entry point.
 *
 * The worker has three jobs, and each lives in `worker/`:
 *   1. Keep declarativeNetRequest rules in sync with the lock state
 *      (`worker/rules.js`).
 *   2. Stay in contact with the native macOS app, which owns the authoritative
 *      lock clock (`worker/native.js`).
 *   3. FAIL CLOSED. If the native app stops answering while a lock is active,
 *      tighten to lockdown instead of relaxing (`worker/native.js`).
 *
 * This file is deliberately only wiring: lifecycle events, the message table
 * and the lock every write transaction runs under. The safe default is always
 * the restrictive one, because the failure mode of a self-control tool is not
 * "the user is annoyed", it is "the tool quietly stopped working and nobody
 * noticed".
 */

import { isLocked } from "./lib/policy.js";
import { createLock } from "./lib/serial.js";
import { describeEnforcement } from "./lib/status.js";
import { cancelVisualTab, getVisualStatus, handleVisualMessage } from "./visual/broker.js";
import { DEFAULT_STATE, getState, toolbarStatus } from "./worker/state.js";
import { applyRules } from "./worker/rules.js";
import { pollNative } from "./worker/native.js";
import { updateList, updateListNow } from "./worker/lists.js";
import { guardedUpdate } from "./worker/updates.js";
import {
  reportWrongBlock, reportWrongWord, resolveDisputed, scoreFeedItem, scoreText,
} from "./worker/inspection.js";
import { injectScannerIntoOpenTabs, syncVisualScanning } from "./worker/scanners.js";
import { ensureBlocked } from "./worker/tabs.js";

// The worker's single write transaction. Taken at the entry points (messages,
// alarms, lifecycle events), never inside the functions they call, so a
// transaction that calls another (pollNative → handleNativeLoss) does not wait
// for itself. Reads (`getState`, `scoreText`) do not take it.
const transaction = createLock();

// --------------------------------------------------------------------------
// Lifecycle
// --------------------------------------------------------------------------

/** Thirty-second app check-ins; list downloads retain their six-hour cadence.
 *  Chrome may delay alarms. Missing checks are not an instant uninstall event.
 */
const ALARMS = { heartbeat: 0.5, listUpdate: 360 };

/**
 * Make sure both alarms exist. Runs on EVERY worker start, not only on install.
 *
 * They used to be created in `onInstalled` alone. Chrome documents that alarms
 * "may be cleared upon browser restart", and they do not come back when an
 * extension is disabled and re-enabled — neither of which fires
 * `onInstalled`. After either, the heartbeat simply stopped: no more polls of
 * the app, so no lock updates, no fail-closed detection, and — now that the
 * app closes a browser whose extension stops checking in during a lock — a
 * browser shut for an extension that was on the whole time.
 */
async function ensureAlarms() {
  for (const [name, periodInMinutes] of Object.entries(ALARMS)) {
    const existing = await chrome.alarms.get(name).catch(() => null);
    if (!existing || existing.periodInMinutes !== periodInMinutes) {
      await chrome.alarms.create(name, { periodInMinutes });
    }
  }
}

/**
 * Once per worker instance: restore the alarms and, if the app has not heard
 * from us for a while, check in now rather than at the next alarm. This is
 * the path a re-enabled extension takes — it fires no lifecycle event at all
 * — and checking in immediately is what tells the app's browser guard, within
 * seconds, that the extension is back.
 *
 * Throttled on `lastHeartbeat` because a worker is restarted for nearly every
 * event after thirty idle seconds, and each poll launches the bridge process.
 */
const BOOT_POLL_AFTER_MS = 30 * 1000;
async function restoreWorkerProtection() {
  // Connectivity is not proof that browser rules or scanner registrations
  // survived disable/re-enable. Repair enforcement before waiting for IPC.
  await transaction(async () => applyRules(await getState()));
  const state = await getState();
  if (Date.now() - (state.lastHeartbeat || 0) > BOOT_POLL_AFTER_MS) {
    await transaction(pollNative);
  }
}
const booted = (async () => {
  try {
    await ensureAlarms();
    await restoreWorkerProtection();
  } catch (err) {
    console.warn("[hisn] worker start-up check failed:", err?.message ?? err);
  }
})();

chrome.runtime.onInstalled.addListener(async () => {
  await transaction(async () => applyRules(await getState()));
  // After an update every content script in an open tab is orphaned — it can
  // no longer reach this worker — so give each page a live one.
  const s = await getState();
  if (s.inspectText || s.failClosed || isLocked(s)) await injectScannerIntoOpenTabs();
  await ensureAlarms();
  await transaction(pollNative);
  await updateList(transaction);
});

chrome.runtime.onStartup.addListener(async () => {
  // Re-assert on every browser start. A service worker that never woke up is
  // a browser session with no rules applied.
  await transaction(async () => applyRules(await getState()));
  await transaction(pollNative);
});

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === "heartbeat") await transaction(pollNative);
  if (alarm.name === "listUpdate") await updateList(transaction);
});

// --------------------------------------------------------------------------
// Messages
// --------------------------------------------------------------------------

/**
 * Who may send what.
 *
 * Every message used to be honoured from any sender. The content script runs
 * inside every web page, in that page's renderer process — the least trusted
 * place this extension has code — and it only ever needs `scoreText`. A
 * compromised renderer could otherwise post `update`, `reportWrongWord` or
 * `resolveDisputed` straight to the worker and borrow the extension's own
 * authority. Everything but `scoreText` must come from one of this
 * extension's own pages (popup, options, block page), and `scoreText` from a
 * frame in a tab.
 */
const PAGE_MESSAGES = new Set(["getState", "update", "forceSync", "reportWrongBlock",
                               "reportWrongWord", "resolveDisputed", "getVisualStatus", "repairProtection",
                               "updateListNow"]);

function fromExtensionPage(sender) {
  return sender?.id === chrome.runtime.id
    && typeof sender.url === "string"
    && sender.url.startsWith(chrome.runtime.getURL(""));
}

function senderAllowed(type, sender) {
  if (PAGE_MESSAGES.has(type)) return fromExtensionPage(sender);
  if (type === "scoreText" || type === "scoreFeedItem") return sender?.id === chrome.runtime.id && !!sender.tab;
  if (["getVisualSettings", "inspectVisualMedia", "cancelVisualMedia"].includes(type)) {
    return sender?.id === chrome.runtime.id && Number.isInteger(sender?.tab?.id)
      && sender.tab.id >= 0 && Number.isInteger(sender.frameId) && sender.frameId >= 0;
  }
  return false;
}

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  // Only the private engine listener answers relayed engine operations. It
  // validates the extension sender itself; this worker must not race its reply.
  if (msg?.target === "hisn-visual-engine") return false;
  if (!senderAllowed(msg?.type, _sender)) {
    sendResponse({ ok: false, reason: "forbidden-sender" });
    return false;
  }
  (async () => {
    switch (msg?.type) {
      case "getState":
        sendResponse(await getState());
        break;
      case "getVisualSettings": {
        const state = await getState();
        sendResponse({ inspectVisual: state.inspectVisual === true, personBlur: state.personBlur ?? "off",
          personRegion: state.personRegion ?? "full", personEffect: state.personEffect ?? "solid",
          personMedia: state.personMedia ?? "both" });
        break;
      }
      case "getVisualStatus":
        sendResponse(await getVisualStatus(await getState()));
        break;
      case "inspectVisualMedia":
      case "cancelVisualMedia":
        sendResponse(await handleVisualMessage(msg, _sender, await getState()));
        break;
      case "update":
        sendResponse(await transaction(() => guardedUpdate(msg.patch || {})));
        break;
      case "forceSync":
        sendResponse(await transaction(pollNative));
        break;
      case "updateListNow":
        sendResponse(await updateListNow(transaction));
        break;
      case "repairProtection":
        // A browser-only repair reasserts the policy already held here. It
        // neither authors settings nor asks the app to refresh its authority.
        // Refuse a settings-shaped request rather than silently applying or
        // appearing to accept a patch through this independent repair path.
        if (Object.keys(msg).some(key => key !== "type")) {
          sendResponse({ ok: false, reason: "invalid-request" });
          break;
        }
        sendResponse(await transaction(async () => {
          await applyRules(await getState());
          const state = await getState();
          return describeEnforcement(state).status === "applied"
            ? { ok: true, state } : { ok: false, reason: "enforcement-failed", state };
        }));
        break;
      case "scoreText":
        sendResponse(await scoreText(msg.zones || {}, _sender));
        break;
      case "scoreFeedItem":
        sendResponse(await scoreFeedItem(msg.zones || {}, _sender));
        break;
      case "reportWrongBlock":
        sendResponse(await transaction(() => reportWrongBlock(msg.host)));
        break;
      case "reportWrongWord":
        sendResponse(await transaction(() => reportWrongWord(msg.term)));
        break;
      case "resolveDisputed":
        sendResponse(await transaction(() => resolveDisputed(msg.entry)));
        break;
      default:
        sendResponse({ ok: false, reason: "unknown-message" });
    }
  })().catch((err) => {
    console.error("[hisn] message failed:", msg?.type, err?.message ?? err);
    sendResponse({ ok: false, reason: err?.enforcementFailure ? "enforcement-failed" : "internal-error" });
  });
  return true; // async response
});

// --------------------------------------------------------------------------
// Visual engine tab lifecycle
// --------------------------------------------------------------------------

chrome.tabs.onRemoved?.addListener(tabId => { cancelVisualTab(tabId).catch(() => {}); });
chrome.tabs.onUpdated?.addListener((tabId, change) => {
  if (change.status === "loading") cancelVisualTab(tabId).catch(() => {});
});

// The public surface the tests and the on-install path import. Re-exported from
// the modules that own each concern so there is one implementation, not two.
export { applyRules, guardedUpdate, DEFAULT_STATE, resolveDisputed, ensureAlarms,
         restoreWorkerProtection, booted, ALARMS, senderAllowed, ensureBlocked,
         syncVisualScanning, toolbarStatus };
