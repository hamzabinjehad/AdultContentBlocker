import { canonicalHost, isLocked, HEARTBEAT_GRACE_MS } from "./policy.js";

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
  if (!state) return { title: "Checking connection…", detail: "Waiting for the extension.", managed: true };
  if (!state.appPresent) return {
    title: "Browser-only protection", managed: false,
    detail: "No app connection detected. These settings work in this browser without the Hisn app. If the app connects, it will manage your blocking settings.",
  };
  const fresh = now - (state.lastHeartbeat || 0) < HEARTBEAT_GRACE_MS;
  return {
    title: fresh ? "Managed by the Hisn app" : "Waiting for the Hisn app", managed: true,
    detail: fresh
      ? "Edit blocking settings in the Hisn app. They sync automatically; incorrect-block reports stay in this browser."
      : "Your last app settings are still in use. Open Hisn on your Mac, then check the connection. Settings remain app-managed while disconnected.",
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
  if (result?.field === "customTerms") return "Not saved. Use at most 200 words or phrases, with 4–100 characters each (excluding spaces for the minimum).";
  if (result?.reason === "app-managed") return "The app now manages these settings. Edit them in Hisn on your Mac.";
  if (result?.reason === "app-required") return "Timed locks are managed by the Hisn app.";
  if (result?.reason === "locked") return "This change would weaken an active lock. It has not been saved.";
  if (result?.reason === "unavailable") return "The extension did not respond. Your edits are still here; try again.";
  return "Could not save these settings. Check your entries and try again.";
}

export function restrictionsActive(state) {
  return isLocked(state) || state.failClosed === true;
}
