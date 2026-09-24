// Settings pages must recover from a sleeping/restarting worker and must not
// interpret an absent reply as a successfully saved setting.
export function send(msg, timeout = 5000) {
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
