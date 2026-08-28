/**
 * Popup — status only.
 *
 * There are intentionally NO controls here. Anything that can turn protection
 * off from a one-click popup is the first thing a person reaches for at 2am.
 * Starting a lock happens in the native app; ending one happens when the timer
 * expires or an accountability partner approves it.
 */

const MODE_LABEL = {
  off: "Off",
  blocklist: "Block list",
  strict: "Strict allowlist",
};

function humanRemaining(ms) {
  if (ms <= 0) return "—";
  const t = Math.floor(ms / 1000);
  const d = Math.floor(t / 86400);
  const h = Math.floor((t % 86400) / 3600);
  const m = Math.floor((t % 3600) / 60);
  if (d) return `${d}d ${h}h`;
  if (h) return `${h}h ${m}m`;
  return `${m}m`;
}

function render(state) {
  const locked = (state.lockUntil || 0) > Date.now();

  const statusEl = document.getElementById("status");
  statusEl.innerHTML = locked
    ? '<span class="pill">Locked</span>'
    : '<span class="pill off">Unlocked</span>';

  document.getElementById("mode").textContent =
    MODE_LABEL[state.failClosed ? "strict" : state.mode] ?? state.mode;
  document.getElementById("left").textContent =
    humanRemaining((state.lockUntil || 0) - Date.now());
  document.getElementById("ver").textContent = state.listVersion || "—";

  if (state.failClosed) {
    const b = document.getElementById("banner");
    b.hidden = false;
    b.className = "banner";
    b.textContent =
      "The Hisn app stopped responding during an active lock, so filtering " +
      "tightened to strict mode automatically. Reopen the app to restore it.";
  }

  document.getElementById("foot").textContent = locked
    ? "Settings are frozen until the lock ends."
    : "Start a lock from the Hisn app.";
}

chrome.runtime.sendMessage({ type: "getState" }, (state) => {
  render(state || {});
});
