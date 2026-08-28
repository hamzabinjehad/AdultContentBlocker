/**
 * Block page.
 *
 * Deliberately unhelpful about *how* to get around it, and deliberately calm.
 * Shame and alarm language ("ACCESS DENIED", warning triangles) reliably makes
 * people fight the tool. A neutral page that reminds them this was their own
 * decision, and shows how long is left, is what keeps a lock intact.
 */

const REASONS = {
  strict: {
    title: "Not on your allowed list",
    subtitle:
      "Strict mode is on, so only sites you approved when you started this " +
      "session will open.",
  },
  custom: {
    title: "You blocked this site",
    subtitle: "You added this one to your own block list.",
  },
  default: {
    title: "This page is blocked",
    subtitle: "You set this up. It is working.",
  },
};

function formatRemaining(ms) {
  if (ms <= 0) return null;
  const total = Math.floor(ms / 1000);
  const d = Math.floor(total / 86400);
  const h = Math.floor((total % 86400) / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (d > 0) return `${d}d ${h}h ${m}m`;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

function render(state) {
  const params = new URLSearchParams(location.search);
  const reason = REASONS[params.get("reason")] || REASONS.default;

  document.getElementById("title").textContent = reason.title;
  document.getElementById("subtitle").textContent = reason.subtitle;

  const timerEl = document.getElementById("timer");
  const noteEl = document.getElementById("note");

  const tick = () => {
    const remaining = formatRemaining((state.lockUntil || 0) - Date.now());
    if (!remaining) {
      timerEl.hidden = true;
      return;
    }
    timerEl.hidden = false;
    timerEl.innerHTML = `Lock ends in <strong>${remaining}</strong>`;
  };
  tick();
  setInterval(tick, 1000);

  if (state.failClosed) {
    noteEl.textContent =
      "The Hisn app is not responding, so protection tightened automatically. " +
      "Reopen the app to restore normal filtering.";
  }
}

chrome.runtime.sendMessage({ type: "getState" }, (state) => {
  render(state || { lockUntil: 0 });
});
