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
import { t, locale as uiLocale } from "./i18n.js";

/** "Strict" / "صارم" — read at call time, so it follows the page's language. */
export const MODE_NAME = {
  get strict() { return t("mode.strict"); },
  get blocklist() { return t("mode.standard"); },
};

/** "20 Sept 2026, 14:00" in the page's language. Injectable for the tests. */
export function formatWhen(ms, locale = uiLocale()) {
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
        headline: t("status.unavailable"),
        detail: t("status.unavailable.detail"),
      },
      lock: { locked: false, headline: t("status.lockUnknown"), detail: "" },
      notices: [],
      details: opts.version ? [[t("details.extVersion"), opts.version]] : [],
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
      headline: t("status.active"),
      detail: t("status.browserOnly.detail"),
    };
  } else if (appFresh) {
    protection = {
      level: "active",
      headline: t("status.active"),
      detail: t("status.connected.detail"),
    };
  } else if (state.failClosed) {
    protection = {
      level: "active",
      headline: t("status.active"),
      detail: t("status.failClosed.detail"),
    };
    notices.push({ kind: "warn", text: t("status.failClosed.notice") });
  } else {
    protection = {
      level: "partial",
      headline: t("status.partial"),
      detail: t("status.partial.detail"),
    };
    notices.push({ kind: "warn", text: t("status.reconnect") });
  }

  // ── lock ────────────────────────────────────────────────────────────────
  let lock;
  if (locked) {
    const mode = MODE_NAME[effectiveStrict(state) ? "strict" : "blocklist"];
    lock = {
      locked: true,
      headline: t("status.lockedUntil", when(state.lockUntil)),
      detail: t("status.lockedDetail", mode),
    };
  } else {
    lock = {
      locked: false,
      headline: t("status.noLock"),
      detail: state.appPresent ? t("status.noLock.app") : t("status.noLock.browser"),
    };
  }

  // ── details ─────────────────────────────────────────────────────────────
  // Report what is ENFORCED, not what was downloaded. A version number
  // climbing while no rules are installed is precisely the "looks healthy,
  // blocks nothing" state the threat model rates worse than being switched off.
  const rules = state.rulesApplied || 0;
  const details = [
    [t("details.blockList"), rules ? t("details.blockListValue", state.listVersion, rules)
                                     : t("details.bundledOnly")],
    [t("details.mode"), effectiveStrict(state) ? t("mode.strict") : t("mode.standard")],
    [t("details.pageText"), state.inspectText === false && !locked && !state.failClosed
      ? t("details.off")
      : t("details.onSensitivity", state.textSensitivity ?? 50)],
    [t("details.app"), !state.appPresent ? t("details.notConnected")
      : appFresh ? t("details.connected")
      : t("details.lastHeard", when(state.lastHeartbeat || 0))],
  ];
  if (opts.version) details.push([t("details.extVersion"), opts.version]);

  return { protection, lock, notices, details };
}
