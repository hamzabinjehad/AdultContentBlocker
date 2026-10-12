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
import { renderIcons } from "./lib/icons.js";
import { send } from "./lib/messages.js";
import { connectionStatus, connectionFeedback } from "./lib/settings.js";
import { restrictionsActive } from "./lib/settings.js";
import { describeVisual } from "./lib/visual-status.js";
import { initLanguage, t, language, setLanguage, savePreference, translatePage } from "./lib/i18n.js";

await initLanguage();
renderIcons();
let lastState;
let lastRender;
let privateCoverage;
let visualEngine;
setInterval(() => { if (lastState) render(lastState); }, 1000);

/** How long to wait for the worker before saying we could not reach it. */
const REPLY_TIMEOUT_MS = 3000;

function render(state) {
  lastState = state;
  const version = chrome.runtime.getManifest?.().version;
  const s = describeStatus(state, { version });
  const connection = connectionStatus(state);
  const visual = describeVisual(visualEngine, state);
  const signature = JSON.stringify([s, connection, privateCoverage, visual, state?.inspectText]);
  if (signature === lastRender) return;
  lastRender = signature;
  document.getElementById("connectionBadge").textContent = !state ? t("badge.unavailable")
    : !connection.managed ? t("badge.browserOnly")
    : connection.fresh ? t("badge.connected") : t("badge.offline");

  document.getElementById("pDot").className = `dot ${s.protection.level}`;
  document.getElementById("pHead").textContent = s.protection.headline;
  document.getElementById("pDetail").textContent = state && !state.appPresent
    ? t("popup.browserOnlySummary") : s.protection.detail;
  const textActive = state && (state.inspectText !== false || restrictionsActive(state));
  const layers = [
    ["domain", state ? "active" : "unknown", state ? t("popup.worksAlone") : t("opt.coverage.unknown")],
    ["text", textActive ? "active" : "unknown", !state ? t("opt.coverage.unknown") : textActive ? t("popup.checksAllowedPages") : t("details.off")],
    ["private", privateCoverage ? "active" : privateCoverage === false ? "partial" : "unknown", t(privateCoverage === undefined ? "opt.coverage.unknown" : privateCoverage ? "details.on" : "popup.permissionNeeded")],
    ["app", state && connection.fresh ? "active" : state?.appPresent ? "partial" : "unknown", null],
    ["visual", visual.level, visual.text],
  ];
  for (const [id, level, text] of layers) {
    document.getElementById(`${id}Indicator`).className = `layer-indicator ${level}`;
    if (text !== null) document.getElementById(`${id}Status`).textContent = text;
  }
  document.getElementById("connectionAction").textContent = t(connection.fresh ? "popup.checkConnection" : "popup.connectApp");

  document.getElementById("lHead").textContent = s.lock.headline;
  document.getElementById("lDetail").textContent = s.lock.detail;
  // An inactive lock's explanation remains in full-page settings. Reserve the
  // small popup for actionable status without hiding an active/unknown lock.
  document.getElementById("lockBlock").hidden = !!state && !restrictionsActive(state);

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
    privateCoverage = allowed;
    const el = document.getElementById("incognito");
    if (!el) return;
    el.hidden = allowed;
    if (lastState) render(lastState);
    if (allowed) return;

    // Guide, not just a warning. An extension cannot grant itself incognito
    // access or open a private window (both are the browser's own consent
    // gates, on purpose), so the most it can do is walk the user to the exact
    // toggle. Numbered steps plus a button that opens this extension's details
    // page, where "Allow in Incognito" lives.
    const steps = document.createElement("ol");
    for (const key of ["incog.step1", "incog.step2"]) {
      const li = document.createElement("li");
      li.textContent = t(key);
      steps.appendChild(li);
    }
    const guide = document.getElementById("incognitoGuide");
    guide.replaceChildren(steps);

    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = t("incog.button");
    btn.addEventListener("click", openDetailsPage);
    guide.appendChild(btn);
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
document.getElementById("privateSettings").addEventListener("click", openDetailsPage);
for (const button of document.querySelectorAll("[data-view]")) button.addEventListener("click", () => {
  // Direct section links are helpful, but fall back to the browser's options
  // opener when it does not allow creating tabs from an action popup.
  try {
    if (chrome.tabs?.create && chrome.runtime.getURL) {
      chrome.tabs.create({ url: chrome.runtime.getURL(`options.html#${button.dataset.view}`) }, () => void chrome.runtime.lastError);
    } else chrome.runtime.openOptionsPage?.();
    window.close();
  } catch { chrome.runtime.openOptionsPage?.(); }
});

async function refreshVisualStatus() {
  const reply = await send({ type: "getVisualStatus" });
  visualEngine = reply?.ok === false ? undefined : reply;
  if (lastState) render(lastState);
}
refreshVisualStatus();
// Engine loading is asynchronous. This reads status, not page content, and
// runs only while the popup is open; it never activates a model itself.
setInterval(() => { if (lastState?.inspectVisual || lastState?.personBlur && lastState.personBlur !== "off") refreshVisualStatus(); }, 5000);

document.getElementById("checkConnection").addEventListener("click", async (event) => {
  const button = event.currentTarget;
  if (button.disabled) return;
  button.disabled = true;
  const message = document.getElementById("connectionMessage");
  message.textContent = t("conn.checking");
  const result = await send({ type: "forceSync" });
  const state = await send({ type: "getState" });
  message.textContent = connectionFeedback(result, state);
  render(state.ok === false ? undefined : state);
  button.disabled = false;
  refreshVisualStatus();
});
chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes.state?.newValue) render(changes.state.newValue);
});

// The page opens saying "Checking status…" and stays there until the worker
// answers. If it never does, say THAT — `render(undefined)` produces "Status
// unavailable" — rather than letting the timeout look like a verdict.
send({ type: "getState" }, REPLY_TIMEOUT_MS).then((state) => {
  render(state.ok === false ? undefined : state);
});
checkIncognitoAccess();

// The language switch: the other language's own name, one tap.
const langSwitch = document.getElementById("langSwitch");
function labelSwitch() {
  langSwitch.textContent = t("lang.switchTo");
  langSwitch.lang = language() === "ar" ? "en" : "ar";
}
labelSwitch();
langSwitch.addEventListener("click", async () => {
  const next = language() === "ar" ? "en" : "ar";
  setLanguage(next);
  await savePreference(next).catch(() => {});
  translatePage(document);
  lastRender = undefined;
  labelSwitch();
  if (lastState !== undefined) render(lastState);
  checkIncognitoAccess();
});
