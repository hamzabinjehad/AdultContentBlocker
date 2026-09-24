/**
 * What the popup says — computed here, without `chrome`, so the tests can pin
 * every state.
 *
 * Two questions, answered separately: *is this browser protected?* and *is a
 * lock running?* They used to share one line, which produced "Protecting" and
 * "Mode: Off" side by side — both true, and together unreadable. The lock has
 * a mode; protection does not.
 *
 * The rule that matters most: **a missing answer is never a positive status.**
 * `describeStatus(undefined)` — the background worker did not reply — says so,
 * and does not fall through to the defaults, which would have read "Protecting"
 * on the strength of nothing at all.
 */

import { isLocked, effectiveStrict, HEARTBEAT_GRACE_MS } from "./policy.js";

export const MODE_NAME = { strict: "Strict", blocklist: "Standard" };

/** "20 Sept 2026, 14:00" in the user's locale. Injectable for the tests. */
export function formatWhen(ms, locale = undefined) {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: "medium", timeStyle: "short",
  }).format(new Date(ms));
}

/**
 * @param state   the reply to `getState`, or nullish when there was none
 * @param opts    { now, version, formatWhen }
 * @returns {{
 *   protection: { level: "unknown"|"active"|"partial", headline, detail },
 *   lock:       { locked: boolean, headline, detail },
 *   notices:    Array<{ kind: "warn"|"info", text }>,
 *   details:    Array<[label, value]>,
 * }}
 */
export function describeStatus(state, opts = {}) {
  const now = opts.now ?? Date.now();
  const when = opts.formatWhen ?? formatWhen;

  if (!state || typeof state !== "object") {
    return {
      protection: {
        level: "unknown",
        headline: "Status unavailable",
        detail: "The extension did not answer. Close and reopen this window; "
          + "or use Check app connection in Settings to retry.",
      },
      lock: { locked: false, headline: "Lock status unknown", detail: "" },
      notices: [],
      details: opts.version ? [["Extension version", opts.version]] : [],
    };
  }

  const locked = isLocked(state, now);
  const appFresh = state.appPresent
    && now - (state.lastHeartbeat || 0) < HEARTBEAT_GRACE_MS;
  const notices = [];

  // ── protection ──────────────────────────────────────────────────────────
  // The two bundled rulesets are unconditional (see applyRules), so this
  // browser is always at least baseline-protected once the worker is alive.
  // What varies is whether the app half is there and answering.
  let protection;
  if (!state.appPresent) {
    protection = {
      level: "active",
      headline: "Protection active",
      detail: "In this browser only. No Hisn app connection detected. "
        + "Manage blocking in Settings; the app is optional for browser filtering.",
    };
  } else if (appFresh) {
    protection = {
      level: "active",
      headline: "Protection active",
      detail: "This browser and the Hisn app are connected.",
    };
  } else if (state.failClosed) {
    protection = {
      level: "active",
      headline: "Protection active",
      detail: "Tightened to strict mode automatically: the Hisn app stopped "
        + "responding during a lock.",
    };
    notices.push({
      kind: "warn",
      text: "Open the Hisn app to restore your normal settings. Strict mode "
        + "stays on until it answers.",
    });
  } else {
    protection = {
      level: "partial",
      headline: "Partially active",
      detail: "This browser is blocking, but the Hisn app is not responding, so "
        + "your lists and settings may be out of date.",
    };
    notices.push({ kind: "warn", text: "Open the Hisn app to reconnect." });
  }

  // ── lock ────────────────────────────────────────────────────────────────
  let lock;
  if (locked) {
    const mode = MODE_NAME[effectiveStrict(state) ? "strict" : "blocklist"];
    lock = {
      locked: true,
      headline: `Locked until ${when(state.lockUntil)}`,
      detail: `${mode} mode. Changes that weaken protection wait until the lock ends.`,
    };
  } else {
    lock = {
      locked: false,
      headline: "No active lock",
      detail: state.appPresent
        ? "Blocking stays on. Start a lock from the Hisn app to make it "
          + "last for a set time. Resistance to removal depends on your Mac setup."
        : "Locks need the Hisn app.",
    };
  }

  // ── details ─────────────────────────────────────────────────────────────
  // Report what is ENFORCED, not what was downloaded. A version number
  // climbing while no rules are installed is precisely the "looks healthy,
  // blocks nothing" state the threat model rates worse than being switched off.
  const rules = state.rulesApplied || 0;
  const details = [
    ["Block list", rules ? `v${state.listVersion} · ${rules} rules` : "bundled list only"],
    ["Blocking mode", effectiveStrict(state) ? "Strict" : "Standard"],
    ["Page-text check", state.inspectText === false && !locked && !state.failClosed
      ? "off"
      : `on · sensitivity ${state.textSensitivity ?? 50}`],
    ["Hisn app", !state.appPresent ? "not connected"
      : appFresh ? "connected"
      : `last heard ${when(state.lastHeartbeat || 0)}`],
  ];
  if (opts.version) details.push(["Extension version", opts.version]);

  return { protection, lock, notices, details };
}
