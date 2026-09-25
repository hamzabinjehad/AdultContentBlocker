import { canonicalHost, isLocked, HEARTBEAT_GRACE_MS } from "./policy.js";
import { t } from "./i18n.js";

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
  if (!state) return { title: t("connection.checking"), detail: t("connection.waitingExtension"),
                       managed: true, fresh: false };
  if (!state.appPresent) return {
    title: t("connection.browserOnly"), managed: false, fresh: false,
    detail: t("connection.browserOnly.detail"),
  };
  // `fresh` is the fact; the words are for the reader. The popup used to
  // compare the TITLE text to decide its badge, which a translation breaks.
  const fresh = now - (state.lastHeartbeat || 0) < HEARTBEAT_GRACE_MS;
  return {
    title: fresh ? t("connection.managed") : t("connection.waitingApp"), managed: true, fresh,
    detail: fresh ? t("connection.managed.detail") : t("connection.waiting.detail"),
  };
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
  return t("err.generic");
}

export function restrictionsActive(state) {
  return isLocked(state) || state.failClosed === true;
}
