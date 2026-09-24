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
  terms: {
    title: "Blocked by content check",
    subtitle:
      "This page's words matched what you asked Hisn to keep out.",
  },
  default: {
    title: "This page is blocked",
    subtitle: "This page matches your protection settings.",
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

/** How recent a stashed block must be to trust that it is THIS page's. The
 *  block page carries no identifier of its own — deliberately, so nothing about
 *  the blocked address lands in history — so recency is the only link back to
 *  the `chrome.storage.session` entry the worker wrote. */
const RECENT_MS = 5 * 60 * 1000;

function showStatus(el, kind, text) {
  el.className = "report-status " + kind;
  el.textContent = text;
  el.hidden = false;
}

const LOCKED_NOTE =
  "Saved for review after your lock ends. Open extension Settings to apply " +
  "or dismiss the report then. Protection has not changed.";

/**
 * Only for a content-check block: show the words that triggered it and let the
 * person point at the one that is WRONG, or say the whole site is fine.
 *
 * The words come from session storage the worker filled — memory-only, never
 * persisted, and read here in an extension page, a trusted context. Each is put
 * on screen with textContent, never innerHTML, so a term can never act as
 * markup. Reporting is a local correction, not a message to anyone: tapping a
 * word stops it counting as adult text; the site button exempts this host. Both
 * only tighten-nothing while a lock is active.
 */
async function setupContentReport() {
  if (new URLSearchParams(location.search).get("reason") !== "terms") return;

  const rowEl = document.getElementById("reportRow");
  const matchedEl = document.getElementById("matched");
  const hintEl = document.getElementById("reportHint");
  const statusEl = document.getElementById("reportStatus");
  const siteBtn = document.getElementById("reportSite");

  let last = null;
  try {
    ({ lastTextBlock: last } = await chrome.storage.session.get("lastTextBlock"));
  } catch { /* no session storage — offer the site button without the words */ }

  const fresh = last && Date.now() - (last.at || 0) < RECENT_MS;
  const host = fresh ? (last.host || "") : "";
  const terms = fresh && Array.isArray(last.terms) ? last.terms : [];

  if (terms.length) {
    matchedEl.textContent = "Matched: ";
    for (const term of terms) {
      const chip = document.createElement("button");
      chip.type = "button";
      chip.className = "w";
      chip.textContent = term;               // never innerHTML
      chip.addEventListener("click", () => {
        chip.disabled = true;
        chrome.runtime.sendMessage({ type: "reportWrongWord", term }, (res) => {
          res = res || {};
          if (res.ok) {
            chip.classList.add("done");
            showStatus(statusEl, "ok",
              `Got it — “${res.term}” will no longer count as adult content.`);
          } else if (res.reason === "locked") {
            chip.disabled = false;
            showStatus(statusEl, "locked", LOCKED_NOTE);
          } else {
            chip.disabled = false;
            showStatus(statusEl, "err", "Could not file that just now.");
          }
        });
      });
      matchedEl.appendChild(chip);
    }
    matchedEl.hidden = false;
  } else {
    hintEl.hidden = true;                     // no words to tap; just the site option
  }

  rowEl.hidden = false;
  siteBtn.addEventListener("click", () => {
    siteBtn.disabled = true;
    chrome.runtime.sendMessage({ type: "reportWrongBlock", host }, (res) => {
      res = res || {};
      if (res.ok) {
        showStatus(statusEl, "ok",
          `Thanks — Hisn will not block ${res.host} by its content again. ` +
          `The site's blocklist status is unchanged.`);
        if (res.host) {
          const a = document.createElement("a");
          a.href = "https://" + res.host + "/";
          a.textContent = "Open " + res.host;
          statusEl.appendChild(document.createElement("br"));
          statusEl.appendChild(a);
        }
      } else if (res.reason === "locked") {
        showStatus(statusEl, "locked", LOCKED_NOTE);
      } else if (res.reason === "no-host") {
        showStatus(statusEl, "err",
          "This page was open too long to identify the site automatically — " +
          "add it to your content allowlist in the app or options instead.");
        siteBtn.disabled = false;
      } else {
        showStatus(statusEl, "err", "Could not file that just now.");
        siteBtn.disabled = false;
      }
    });
  });
}

chrome.runtime.sendMessage({ type: "getState" }, (state) => {
  render(state || { lockUntil: 0 });
});

setupContentReport();

document.getElementById("openSettings").addEventListener("click", () => chrome.runtime.openOptionsPage());
document.getElementById("leavePage").addEventListener("click", () => {
  // A new tab avoids navigating back to the same blocked page or guessing a
  // destination that might itself be outside the user's allowed sites.
  chrome.tabs.getCurrent((tab) => {
    if (chrome.runtime.lastError || !tab?.id) {
      document.getElementById("note").textContent = "Use your browser’s New Tab button to continue.";
      return;
    }
    chrome.tabs.update(tab.id, { url: "chrome://newtab/" }, () => {
      if (chrome.runtime.lastError) document.getElementById("note").textContent = "Use your browser’s New Tab button to continue.";
    });
  });
