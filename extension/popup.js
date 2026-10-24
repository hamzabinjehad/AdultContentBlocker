/**
 * Popup — status and embedded settings, using the same options controller.
 * Native-owned fields and commitment restrictions remain enforced by the
 * service worker, not a second, weaker popup implementation. Native locks
 * still start/end through the app's existing workflow.
 *
 * What to SAY is decided in lib/status.js, where the tests can see it. This
 * file only puts the words on the page.
 */

import { describeStatus, describeEnforcement } from "./lib/status.js";
import { renderIcons } from "./lib/icons.js";
import { send } from "./lib/messages.js";
import { connectionStatus, connectionFeedback, repairFeedback } from "./lib/settings.js";
import { restrictionsActive, quickBlockTarget, settingsError } from "./lib/settings.js";
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
  const enforcement = describeEnforcement(state);
  if (enforcement.status === "failed") document.getElementById("protectionDetails").open = true;
  const signature = JSON.stringify([s, connection, privateCoverage, visual, state?.inspectText]);
  if (signature === lastRender) return;
  lastRender = signature;
  document.getElementById("connectionBadge").textContent = !state ? t("badge.unavailable")
    : !connection.managed ? t("badge.browserOnly")
    : connection.fresh ? t("badge.connected") : t("badge.offline");

  document.getElementById("pDot").className = `dot ${s.protection.level}`;
  document.getElementById("pHead").textContent = s.protection.headline;
  document.getElementById("pDetail").textContent = state && !state.appPresent && enforcement.status === "applied"
    ? t("popup.browserOnlySummary") : s.protection.detail;
  const layers = [
    ["domain", enforcement.layers.domains.level, enforcement.layers.domains.level === "active"
      ? t("popup.worksAlone") : enforcement.layers.domains.text],
    ["text", enforcement.layers.text.level, enforcement.layers.text.level === "active"
      ? t("popup.checksAllowedPages") : enforcement.layers.text.text],
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

  renderQuickBlock();

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

// One-tap block for the site in the current tab. Shown only when the browser
// owns the list; the worker still rechecks every patch through guardedUpdate.
let activeTab;
let quickBusy = false;
function renderQuickBlock() {
  const target = quickBlockTarget(activeTab?.url, lastState);
  const show = target.ok || target.reason === "blocked";
  document.getElementById("quickBlock").hidden = !show;
  if (!show) return;
  document.getElementById("quickHost").textContent = target.host;
  const button = document.getElementById("quickBlockButton");
  button.hidden = !target.ok;
  button.disabled = quickBusy;
  button.setAttribute("aria-label", t("quick.blockSite", target.host));
  document.getElementById("quickBlocked").hidden = target.ok;
}
chrome.tabs?.query?.({ active: true, currentWindow: true }, (tabs) => {
  void chrome.runtime.lastError;
  activeTab = tabs?.[0];
  renderQuickBlock();
});
document.getElementById("quickBlockButton").addEventListener("click", async () => {
  const target = quickBlockTarget(activeTab?.url, lastState);
  if (!target.ok || quickBusy) return;
  const message = document.getElementById("quickMessage");
  quickBusy = true;
  renderQuickBlock();
  message.textContent = t("quick.blocking");
  try {
    const result = await send({ type: "update", patch: { customBlocks: target.customBlocks } });
    if (result?.ok) {
      // The worker moves open tabs on this host to the block page itself.
      message.textContent = t("quick.done", target.host);
      render(result.state);
    } else {
      message.textContent = result?.reason === "unavailable" ? t("quick.failed") : settingsError(result);
    }
  } finally {
    quickBusy = false;
    lastRender = undefined;
    renderQuickBlock();
  }
});

const settingsFrame = document.getElementById("settingsFrame");
function openEmbeddedSettings(view = "overview") {
  const allowed = ["overview", "rules", "media", "lock", "settings"];
  if (!allowed.includes(view)) view = "overview";
  if (!settingsFrame.hasAttribute("src")) {
    const url = chrome.runtime.getURL?.("options.html") || new URL("options.html", document.baseURI).href;
    settingsFrame.src = `${url}?popup=1#${view}`;
  } else {
    // Keep the existing document and its drafts when returning to status.
    try { settingsFrame.contentWindow.location.hash = view; } catch { /* Keep its current view. */ }
  }
  document.getElementById("popupEditor").hidden = false;
  document.body.classList.add("popup-editing");
  document.getElementById("backToStatus").focus();
}
document.getElementById("openSettings").addEventListener("click", () => openEmbeddedSettings());
document.getElementById("backToStatus").addEventListener("click", () => {
  document.getElementById("popupEditor").hidden = true;
  document.body.classList.remove("popup-editing");
  document.getElementById("openSettings").focus();
});
document.getElementById("openFullSettings").addEventListener("click", () => {
  // Browser popups are ephemeral. Do not silently discard a draft on pop-out.
  try {
    if (settingsFrame.contentWindow.hasUnsavedSettings?.() && !window.confirm(t("popup.draftWarning"))) return;
  } catch { return; }
  chrome.runtime.openOptionsPage?.();
});
document.getElementById("privateSettings").addEventListener("click", openDetailsPage);
for (const button of document.querySelectorAll("[data-view]")) button.addEventListener("click", () => {
  openEmbeddedSettings(button.dataset.view);
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
document.getElementById("repairProtection").addEventListener("click", async (event) => {
  const button = event.currentTarget, message = document.getElementById("repairMessage");
  if (button.disabled) return;
  button.disabled = true;
  button.setAttribute("aria-busy", "true");
  message.textContent = t("repair.checking");
  try {
    const result = await send({ type: "repairProtection" });
    // Read current state after queued writes; the command's snapshot may be old.
    const state = await send({ type: "getState" });
    message.textContent = repairFeedback(result, state);
    render(state?.ok === false ? undefined : state);
    void refreshVisualStatus();
  } finally {
    button.disabled = false;
    button.removeAttribute("aria-busy");
  }
});
chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes.state?.newValue) render(changes.state.newValue);
  if (area === "local" && changes.uiLanguage) {
    initLanguage().then(() => {
      translatePage(document);
      document.getElementById("repairMessage").textContent = document.getElementById("repairProtection").disabled ? t("repair.checking") : "";
      lastRender = undefined;
      labelSwitch();
      render(lastState);
      checkIncognitoAccess();
    });
  }
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
  document.getElementById("repairMessage").textContent = document.getElementById("repairProtection").disabled ? t("repair.checking") : "";
  lastRender = undefined;
  labelSwitch();
  if (lastState !== undefined) render(lastState);
  checkIncognitoAccess();
});
