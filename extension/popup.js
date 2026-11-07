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
import { detectLanguage, languageName } from "./lib/language.js";

await initLanguage();
renderIcons();
let lastState;
let lastRender;
let visualEngine;
let pageLanguageReply; // undefined = not asked, null = no answer, object = answered
setInterval(() => { if (lastState) render(lastState); }, 1000);

/** How long to wait for the worker before saying we could not reach it. */
const REPLY_TIMEOUT_MS = 3000;

function render(state) {
  lastState = state;
  renderPageLanguage();
  const version = chrome.runtime.getManifest?.().version;
  const s = describeStatus(state, { version });
  const connection = connectionStatus(state);
  const visual = describeVisual(visualEngine, state);
  const enforcement = describeEnforcement(state);
  if (enforcement.status === "failed") document.getElementById("protectionDetails").open = true;
  const signature = JSON.stringify([s, connection, visual, state?.inspectText, state?.personBlur]);
  if (signature === lastRender) return;
  lastRender = signature;

  const hero = document.getElementById("hero");
  hero.dataset.level = s.protection.level;
  document.getElementById("pDot").className = `dot ${s.protection.level}`;
  document.getElementById("pHead").textContent = s.protection.headline;
  document.getElementById("pDetail").textContent = s.protection.detail;

  /**
   * A layer's status is a word, not a sentence. "On", "Off", "Checking" and
   * "Issue" are the whole vocabulary; the explanation lives in Details and in
   * the page the gear opens.
   */
  const stateLabel = (level, enabled = true, unavailable = false) => {
    if (!enabled) return t("state.off");
    if (unavailable) return t("state.issue");
    if (level === "active") return t("state.on");
    if (level === "partial") return t("state.issue");
    return t("state.checking");
  };
  const paintLayer = (indicatorId, statusId, level, enabled = true, unavailable = false) => {
    const indicator = document.getElementById(indicatorId);
    const status = document.getElementById(statusId);
    const shown = enabled ? level : "off";
    indicator.className = `layer-indicator ${shown}`;
    status.className = `layer-state ${shown}`;
    status.textContent = stateLabel(level, enabled, unavailable);
  };

  const textEnabled = !state || state.inspectText !== false
    || state.failClosed === true || restrictionsActive(state);
  const visualEnabled = !!state && (state.inspectVisual === true
    || ["all", "female", "male"].includes(state.personBlur));
  paintLayer("domainIndicator", "domainStatus", enforcement.layers.domains.level);
  paintLayer("textIndicator", "textStatus", enforcement.layers.text.level, textEnabled);
  paintLayer("visualIndicator", "visualStatus", visual.level, visualEnabled,
    visualEnabled && visual.available === false);
  // The app row is a connection, not an enforcement receipt: fresh means
  // connected, a known app that has gone quiet means attention, no app at all
  // means the optional row is off. Paint the icon and the word together so the
  // row cannot contradict itself.
  const appLevel = state && connection.fresh ? "active" : state?.appPresent ? "partial" : "off";
  document.getElementById("appIndicator").className = `layer-indicator ${appLevel}`;
  const badge = document.getElementById("connectionBadge");
  badge.className = `layer-state ${appLevel}`;
  badge.textContent = !state ? t("badge.unavailable")
    : !connection.managed ? t("popup.optional")
    : connection.fresh ? t("badge.connected") : t("badge.offline");
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
    el.className = `notice ${n.kind}`;
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
  refreshPageLanguage();
});

/**
 * Show the language the scanner already detected for this page.
 *
 * Display only: the worker scores the page itself and never trusts this
 * round-trip or the page's own language tag. The content script answers with
 * its collected tags and the bounded sample it already sends for scoring; the
 * detector runs here so the popup and the scorer share one decision. Nothing
 * is written or sent off the device.
 */
function refreshPageLanguage(attempt = 0) {
  if (!activeTab?.id || !/^https?:/i.test(activeTab.url || "") || !chrome.tabs?.sendMessage) {
    pageLanguageReply = null;
    renderPageLanguage();
    return;
  }
  try {
    chrome.tabs.sendMessage(activeTab.id, { type: "hisnPageLanguage" }, { frameId: 0 }, (reply) => {
      // Reading lastError marks "no scanner in this tab" as expected. A tab
      // that just opened may still be injecting, so retry once before hiding.
      if (chrome.runtime.lastError || !reply?.tags) {
        if (attempt === 0) { setTimeout(() => refreshPageLanguage(1), 400); return; }
        pageLanguageReply = null;
      } else {
        pageLanguageReply = { code: detectLanguage(reply.tags, reply.sample) };
      }
      renderPageLanguage();
    });
  } catch {
    pageLanguageReply = null;
    renderPageLanguage();
  }
}
function renderPageLanguage() {
  const section = document.getElementById("pageLanguage");
  if (!section) return;
  // The language is context for the text layer. If that layer is off and no
  // lock keeps it on, the scanner should not be active and the row would
  // describe a check that is not running.
  const textActive = !lastState || lastState.inspectText || lastState.failClosed
    || restrictionsActive(lastState);
  section.hidden = !pageLanguageReply || !textActive;
  if (!pageLanguageReply) return;
  document.getElementById("pageLanguageValue").textContent = pageLanguageReply.code
    ? languageName(pageLanguageReply.code, language())
    : t("popup.pageLanguageUnknown");
}
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
      renderPageLanguage();
      render(lastState);
    });
  }
});

// The page opens saying "Checking status…" and stays there until the worker
// answers. If it never does, say THAT — `render(undefined)` produces "Status
// unavailable" — rather than letting the timeout look like a verdict.
send({ type: "getState" }, REPLY_TIMEOUT_MS).then((state) => {
  render(state.ok === false ? undefined : state);
});

// The language switch: the other language's own name, one tap. The status
// header and the embedded settings bar both carry it, because the settings
// page's own language row no longer fits inside the popup.
const langSwitch = document.getElementById("langSwitch");
const editorLangSwitch = document.getElementById("editorLangSwitch");
function labelSwitch() {
  for (const button of [langSwitch, editorLangSwitch]) {
    button.textContent = t("lang.switchTo");
    button.lang = language() === "ar" ? "en" : "ar";
  }
}
labelSwitch();
async function switchLanguage() {
  const next = language() === "ar" ? "en" : "ar";
  setLanguage(next);
  await savePreference(next).catch(() => {});
  translatePage(document);
  document.getElementById("repairMessage").textContent = document.getElementById("repairProtection").disabled ? t("repair.checking") : "";
  lastRender = undefined;
  labelSwitch();
  renderPageLanguage();
  if (lastState !== undefined) render(lastState);
}
langSwitch.addEventListener("click", switchLanguage);
editorLangSwitch.addEventListener("click", switchLanguage);
