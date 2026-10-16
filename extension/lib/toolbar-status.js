/**
 * Generic toolbar health, never page content or a browsing-history record.
 * A current rule/registration receipt is configuration evidence, not a claim
 * that every image, video or request has been classified successfully.
 */
import { describeEnforcement } from "./status.js";
import en from "./locale/en.js";
import ar from "./locale/ar.js";

export function describeToolbar(status, language = "en", warningLatched = false) {
  const table = language === "ar" ? ar : en;
  const warning = status === "failed" || status !== "applied" && warningLatched;
  return {
    text: warning ? "!" : status === "applied" ? "" : "?",
    color: warning ? "#b45309" : "#6b7280",
    title: `Hisn — ${table[warning ? "status.repair" : status === "applied"
      ? "enforcement.applied" : "status.applying"]}`,
  };
}

/** Serialize best-effort presentation writes without holding protection work.
 * Repeated successful states are deduplicated; rejected API writes are not
 * cached as success, so the next state read can retry them. */
export function createToolbarStatus(action) {
  let warningLatched = false, priorKnown = false;
  let tail = Promise.resolve(), tailKey, pending = 0, successfulKey;

  async function present(status, language, key) {
    let priorReadFailed = false;
    if (status === "failed") { warningLatched = true; priorKnown = true; }
    else if (status === "applied") { warningLatched = false; priorKnown = true; }
    else if (!priorKnown) {
      // Chrome retains an action badge across worker lifetimes. A restarted
      // worker must not erase its earlier failure merely by becoming pending.
      try {
        if (typeof action?.getBadgeText !== "function") throw new Error("badge read unavailable");
        warningLatched = await action.getBadgeText({}) === "!";
        priorKnown = true;
      } catch { priorReadFailed = true; }
    }
    const view = describeToolbar(status, language, warningLatched);
    let succeeded = !priorReadFailed;
    for (const [method, values] of [
      ["setBadgeBackgroundColor", { color: view.color }],
      ["setTitle", { title: view.title }],
      // If the historical badge could not be read, retain it while pending.
      // A verified applied receipt can still clear it without that read.
      ["setBadgeText", priorReadFailed ? null : { text: view.text }],
    ]) {
      if (!values || typeof action?.[method] !== "function") continue;
      try { await action[method](values); } catch { succeeded = false; }
    }
    successfulKey = succeeded ? key : undefined;
  }

  return {
    update(state, language = "en") {
      const status = describeEnforcement(state).status;
      const key = `${status}:${language === "ar" ? "ar" : "en"}`;
      if (pending ? tailKey === key : successfulKey === key) return tail;
      pending++; tailKey = key;
      tail = tail.then(() => present(status, language, key)).catch(() => {
        // Toolbar availability must never decide whether rules are applied.
        successfulKey = undefined;
      }).finally(() => { if (!--pending) tailKey = undefined; });
      return tail;
    },
    whenIdle() { return tail; },
  };
}
