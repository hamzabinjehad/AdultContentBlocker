/**
 * Popup — status only.
 *
 * There are intentionally NO controls here that weaken anything. Anything
 * that can turn protection off from a one-click popup is the first thing a
 * person reaches for at 2am. Starting a lock happens in the native app;
 * ending one happens when the timer expires or an accountability partner
 * approves it. The one button opens the options page, which has its own
 * guards.
 *
 * What to SAY is decided in lib/status.js, where the tests can see it. This
 * file only puts the words on the page.
 */

import { describeStatus } from "./lib/status.js";
import { send } from "./lib/messages.js";
import { connectionStatus } from "./lib/settings.js";

/** How long to wait for the worker before saying we could not reach it. */
const REPLY_TIMEOUT_MS = 3000;

function render(state) {
  const version = chrome.runtime.getManifest?.().version;
  const s = describeStatus(state, { version });
  const connection = connectionStatus(state);
  document.getElementById("connectionBadge").textContent = !state ? "Unavailable"
    : !connection.managed ? "Browser only"
    : connection.title === "Managed by the Hisn app" ? "App connected" : "App offline";

  document.getElementById("pDot").className = `dot ${s.protection.level}`;
  document.getElementById("pHead").textContent = s.protection.headline;
  document.getElementById("pDetail").textContent = s.protection.detail;

  document.getElementById("lHead").textContent = s.lock.headline;
  document.getElementById("lDetail").textContent = s.lock.detail;

  const notices = document.getElementById("notices");
  notices.replaceChildren(...s.notices.map((n) => {
    const el = document.createElement("div");
    el.className = "notice";
    el.setAttribute("role", n.kind === "warn" ? "alert" : "status");
    el.textContent = n.text;
    return el;
  }));

  const dl = document.getElementById("details");
  dl.replaceChildren(...s.details.flatMap(([k, v]) => {
    const dt = document.createElement("dt");
    dt.textContent = k;
    const dd = document.createElement("dd");
    dd.textContent = v;
    return [dt, dd];
  }));
}

/**
 * Ask the user to enable the extension in private windows.
 *
 * The manifest is `incognito: spanning`, which means the extension CAN run in a
 * private window — but Chrome keeps it off there until the user turns on "Allow
 * in Incognito/Private". That opt-in is deliberate (it is the browser's own
 * consent gate), so the honest thing is not to pretend it happened: this
 * detects when the extension has NOT been allowed in private windows and says
 * so, rather than letting page-text scanning silently not run there.
 *
 * If the profile has removed private browsing entirely (the other answer to the
 * incognito question), there is no private window to allow into and this is
 * simply never relevant — `isAllowedIncognitoAccess` reports the real setting.
 */
function checkIncognitoAccess() {
  if (!chrome.extension?.isAllowedIncognitoAccess) return;
  chrome.extension.isAllowedIncognitoAccess((allowed) => {
    if (allowed) return;
    const el = document.getElementById("incognito");
    if (!el) return;

    // Guide, not just a warning. An extension cannot grant itself incognito
    // access or open a private window (both are the browser's own consent
    // gates, on purpose), so the most it can do is walk the user to the exact
    // toggle. Numbered steps plus a button that opens this extension's details
    // page, where "Allow in Incognito" lives.
    el.hidden = false;
    el.innerHTML =
      "<strong>Not active in private windows yet.</strong>"
      + "<ol>"
      + "<li>Open this extension's details page (button below).</li>"
      + "<li>Turn on <strong>Allow in Incognito</strong> (or “Allow in "
      + "Private”).</li>"
      + "</ol>";

    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = "Open extension settings";
    btn.addEventListener("click", openDetailsPage);
    el.appendChild(btn);
  });
}

/**
 * Open this extension's own details page — where the "Allow in Incognito"
 * toggle is. `chrome://extensions/?id=<id>` is the Chromium target and works
 * on the forks too (Helium included). Wrapped because a browser that refuses
 * to let an extension open its internal pages throws here, and the numbered
 * steps above already tell the user how to get there by hand.
 */
function openDetailsPage() {
  const url = `chrome://extensions/?id=${chrome.runtime.id}`;
  try {
    // A refusal to open an internal page arrives via lastError, not a throw,
    // so read it to avoid an unchecked-error warning. Either way the written
    // steps stay on screen as the fallback.
    chrome.tabs.create({ url }, () => void chrome.runtime.lastError);
  } catch (_) {
    /* leave the written steps as the fallback */
  }
}

document.getElementById("openSettings").addEventListener("click", () => {
  chrome.runtime.openOptionsPage?.();
  window.close();
});

document.getElementById("checkConnection").addEventListener("click", async (event) => {
  event.target.disabled = true;
  const message = document.getElementById("connectionMessage");
  message.textContent = "Checking for the Hisn app…";
  const result = await send({ type: "forceSync" });
  message.textContent = result.ok ? "App connected. Settings synced."
    : "No connection. Open Hisn on your Mac and retry, or continue with your current browser protection.";
  const state = await send({ type: "getState" });
  render(state.ok === false ? undefined : state);
  event.target.disabled = false;
});
chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes.state?.newValue) render(changes.state.newValue);
});

// The page opens saying "Checking status…" and stays there until the worker
// answers. If it never does, say THAT — `render(undefined)` produces "Status
// unavailable" — rather than letting the timeout look like a verdict.
let answered = false;
const giveUp = setTimeout(() => { if (!answered) render(undefined); }, REPLY_TIMEOUT_MS);
chrome.runtime.sendMessage({ type: "getState" }, (state) => {
  answered = true;
  clearTimeout(giveUp);
  // lastError means the worker is gone; `state` is undefined in that case and
  // must not be replaced with defaults.
  render(chrome.runtime.lastError ? undefined : state);
});
checkIncognitoAccess();
