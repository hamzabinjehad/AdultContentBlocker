import { canonicalHost, isLocked, HEARTBEAT_GRACE_MS } from "./policy.js";
import { t } from "./i18n.js";
import { describeEnforcement } from "./status.js";

// The app owns these fields after its first successful connection. A missing
// heartbeat never silently transfers authority back to the browser.
const APP_FIELDS = new Set([
  "mode", "lockUntil", "allowlist", "customBlocks", "customTerms",
  "inspectText", "textSensitivity",
]);

export function settingsAccess(state, patch) {
  if (state.appPresent && Object.keys(patch).some((key) => APP_FIELDS.has(key))) {
    return { ok: false, reason: "app-managed" };
  }
  // Browser-only mode is editable filtering, not a tamper-resistant timer.
  if ("lockUntil" in patch) return { ok: false, reason: "app-required" };
  return { ok: true };
}

export function connectionStatus(state, now = Date.now()) {
  if (!state || state.ok === false) return { title: t("connection.checking"), detail: t("connection.waitingExtension"),
                       managed: true, fresh: false };
  if (!state.appPresent) return {
    title: t("connection.browserOnly"), managed: false, fresh: false,
    detail: t("connection.browserOnly.detail"),
  };
  // `fresh` is the fact; the words are for the reader. The popup used to
  // compare the TITLE text to decide its badge, which a translation breaks.
  const fresh = state.bridgeConnection?.status !== "unavailable"
    && now - (state.lastHeartbeat || 0) < HEARTBEAT_GRACE_MS;
  return {
    title: fresh ? t("connection.managed") : t("connection.waitingApp"), managed: true, fresh,
    detail: fresh ? t("connection.managed.detail") : t("connection.waiting.detail"),
  };
}

// A failed worker request is not evidence that the native app is absent.
// Only claim a successful sync after reading back fresh, app-managed state.
export function connectionFeedback(result, state, now = Date.now()) {
  if (!state || state.ok === false || result?.reason === "unavailable") return t("conn.unavailable");
  if ((result?.connected || result?.ok) && connectionStatus(state, now).fresh
      && describeEnforcement(state).status !== "applied") return t("conn.enforcementFailed");
  if (result?.reason === "enforcement-failed") return t("status.repair.detail");
  if (result?.reason === "authority-unreachable") return t("conn.authorityUnavailable");
  if (result?.reason === "native-malformed") return t("conn.malformed");
  if (result?.reason === "native-timeout") return t("conn.nativeTimeout");
  if (result?.ok && connectionStatus(state, now).fresh) return t("conn.synced");
  return t(state.appPresent ? "connection.waiting.detail" : "conn.none");
}

// A successful command and a fresh configuration readback are both required.
// An old green receipt alone cannot turn a timed-out repair into success.
export function repairFeedback(result, state) {
  if (!state || state.ok === false || !result || result.reason === "unavailable") return t("repair.unverified");
  if (result.ok === true && describeEnforcement(state).status === "applied") return t("repair.verified");
  return t("repair.failed");
}

export function parseDomains(text) {
  const domains = new Set(), invalid = [];
  String(text).split(/\r?\n/).forEach((line, i) => {
    if (!line.trim()) return;
    const host = canonicalHost(line);
    if (host) domains.add(host);
    else invalid.push(i + 1);
  });
  return { domains: [...domains], invalid };
}

export function settingsError(result) {
  if (result?.field === "customTerms") return t("err.customTerms");
  if (result?.reason === "app-managed") return t("err.appManaged");
  if (result?.reason === "app-required") return t("err.appRequired");
  if (result?.reason === "locked") return t("err.locked");
  if (result?.reason === "unavailable") return t("err.unavailable");
  if (result?.reason === "enforcement-failed") return t("err.enforcementFailed");
  return t("err.generic");
}

export function restrictionsActive(state) {
  return isLocked(state) || state.failClosed === true;
}

// The popup's one-tap "Block this site". Adding a block only tightens policy,
// so it stays available during a browser lock. The worker still rechecks
// ownership and direction, so this only decides whether to offer the button.
export function quickBlockTarget(url, state) {
  // No URL parser: the JavaScriptCore test shell has none. Browser tab URLs
  // already carry ASCII (punycode) hosts, which canonicalHost accepts.
  const authority = /^https?:\/\/([^/?#]*)/i.exec(typeof url === "string" ? url : "")?.[1];
  if (!authority) return { ok: false, reason: "page" };
  const host = canonicalHost(authority);
  if (!host) return { ok: false, reason: "page" };
  if (!state || state.ok === false) return { ok: false, reason: "unavailable", host };
  if (!settingsAccess(state, { customBlocks: [] }).ok) return { ok: false, reason: "app-managed", host };
  const blocked = (state.customBlocks || []).map(canonicalHost).filter(Boolean);
  if (blocked.some((b) => host === b || host.endsWith(`.${b}`))) return { ok: false, reason: "blocked", host };
  return { ok: true, host, customBlocks: [...new Set([...blocked, host])] };
}
