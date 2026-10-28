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

/** A browser API receipt confirms installation, not traffic or classification. */
export function describeEnforcement(state) {
  const receipt = state?.enforcement;
  const current = receipt?.version === 1 && Number.isSafeInteger(state.desiredRevision)
    && receipt.desiredRevision === state.desiredRevision;
  const complete = receipt?.layers?.domains === "applied" && receipt?.layers?.search === "applied"
    && ["applied", "off"].includes(receipt?.layers?.text) && ["applied", "off"].includes(receipt?.layers?.visual);
  const status = current && receipt.status === "failed" ? "failed"
    : current && complete && receipt.status === "applied"
      && receipt.appliedRevision === state.desiredRevision
      && state.appliedRevision === state.desiredRevision ? "applied" : "pending";
  const layers = {};
  for (const name of ["domains", "search", "text", "visual"]) {
    const applied = receipt?.version === 1 && status !== "pending" && receipt.layers?.[name] === "applied";
    const off = receipt?.version === 1 && status !== "pending" && receipt.layers?.[name] === "off";
    layers[name] = { level: applied ? "active" : status === "failed" ? "partial" : "unknown",
      text: t(applied ? "details.on" : off ? "details.off" : status === "failed" ? "enforcement.failed" : "enforcement.pending") };
  }
  return { status, layers };
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

  if (!state || typeof state !== "object" || state.ok === false) {
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
  const appFresh = state.appPresent && state.bridgeConnection?.status !== "unavailable"
    && now - (state.lastHeartbeat || 0) < HEARTBEAT_GRACE_MS;
  const notices = [];
  const enforcement = describeEnforcement(state);
  // A list that cannot change is a coverage problem even when every rule that
  // IS installed is working. Say so plainly; never let a version number imply
  // freshness. `lastListUpdate` is written only after a verified install.
  const listVersion = state.listVersion || 0;
  const listAt = state.lastListUpdate || 0;
  const listAgeDays = listAt ? Math.max(0, Math.floor((now - listAt) / 86_400_000)) : null;
  if (enforcement.status === "applied") {
    if (!listVersion) notices.push({ kind: "info", text: t("status.listBundled") });
    else if (listAgeDays !== null && listAgeDays >= 14) {
      notices.push({ kind: "warn", text: t("status.listStale", listAgeDays) });
    }
  }

  // ── protection ──────────────────────────────────────────────────────────
  // Stored intent or a heartbeat alone is not proof Chrome installed it.
  // Only the current rule/scanner receipt permits the active headline.
  let protection;
  if (enforcement.status === "pending") {
    protection = { level: "unknown", headline: t("status.applying"), detail: t("status.applying.detail") };
  } else if (enforcement.status === "failed") {
    protection = { level: "partial", headline: t("status.repair"), detail: t("status.repair.detail") };
    notices.push({ kind: "warn", text: t("status.repair.notice") });
  } else if (!state.appPresent) {
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
  const rules = state.enforcement?.downloadedRules?.domains || 0;
  const listVerified = rules === state.rulesApplied && (state.enforcement?.downloadedRules?.keywords || 0) === (state.keywordRulesApplied || 0);
  const details = [
    [t("details.enforcement"), t(enforcement.status === "applied" ? "enforcement.applied"
      : enforcement.status === "failed" ? "enforcement.failed" : "enforcement.pending")],
    [t("details.blockList"), enforcement.layers.domains.level !== "active" ? enforcement.layers.domains.text
      : rules ? listVerified ? t("details.blockListValue", state.enforcement.downloadedRules.listVersion, rules)
        : t("details.downloadedCount", rules) : t("details.bundledOnly")],
    [t("details.listUpdated"), listAt ? when(listAt) : t("details.never")],
    [t("details.mode"), enforcement.layers.domains.level === "active"
      ? effectiveStrict(state) ? t("mode.strict") : t("mode.standard")
      : t("details.requestedValue", effectiveStrict(state) ? t("mode.strict") : t("mode.standard"))],
    [t("details.pageText"), enforcement.layers.text.level === "active"
      ? t("details.onSensitivity", state.textSensitivity ?? 50) : enforcement.layers.text.text],
    [t("details.app"), !state.appPresent ? t("details.notConnected")
      : appFresh ? t("details.connected")
      : t("details.lastHeard", when(state.lastHeartbeat || 0))],
  ];
  if (opts.version) details.push([t("details.extVersion"), opts.version]);

  return { protection, lock, notices, details };
}
