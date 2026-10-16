// Settings pages must recover from a sleeping/restarting worker and must not
// interpret an absent reply as a successfully saved setting.
export const MESSAGE_TIMEOUT_MS = 5000;
// Native IPC has its own ten-second deadline and can follow an in-flight
// heartbeat. Keep the connection action pending through both; a worker timeout
// still means "not verified", never "the native app is absent".
export const CONNECTION_TIMEOUT_MS = 30000;
// Browser repair can queue behind a bounded native check and must finish its
// own rules/scanner readback. A deadline still means unverified, not applied.
export const REPAIR_TIMEOUT_MS = 30000;
export function send(msg, timeout = msg?.type === "forceSync" ? CONNECTION_TIMEOUT_MS
  : msg?.type === "repairProtection" ? REPAIR_TIMEOUT_MS : MESSAGE_TIMEOUT_MS) {
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve({ ok: false, reason: "unavailable" }), timeout);
    try {
      chrome.runtime.sendMessage(msg, (reply) => {
        clearTimeout(timer);
        resolve(chrome.runtime.lastError || !reply
          ? { ok: false, reason: "unavailable" } : reply);
      });
    } catch {
      clearTimeout(timer);
      resolve({ ok: false, reason: "unavailable" });
    }
  });
}
