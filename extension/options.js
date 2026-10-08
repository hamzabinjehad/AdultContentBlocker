/**
 * Options page.
 *
 * ── TWO CONFIGURATIONS, AND THE PAGE HAS TO BE HONEST ABOUT WHICH ─────────
 * This extension is legitimately run two ways, and the settings below mean
 * different things in each:
 *
 *   standalone   — browser-only. This page IS the authoring surface. What is
 *                  typed here is what is enforced, and it persists.
 *   with the app — the Hisn app owns the lists and overwrites this side on
 *                  every heartbeat, so anything typed here survives at most a
 *                  minute. The fields go read-only and say so.
 *
 * Silently accepting edits that a background sync is about to discard is the
 * kind of small dishonesty that makes people distrust the whole tool, so the
 * page reads `appPresent` and changes its mind about what it is.
 *
 * ── THE ASYMMETRY, IN BOTH CONFIGURATIONS ────────────────────────────────
 * While a lock is active you may ADD blocks but never remove them, and you may
 * SHRINK the allowlist but never grow it. `guardedUpdate` in the service worker
 * enforces that; this page only reflects it, because a UI-only guard is no
 * guard at all — anything here can be posted from a devtools console.
 */

import { send } from "./lib/messages.js";
import { describeStatus } from "./lib/status.js";
import { renderIcons } from "./lib/icons.js";
import { siteEditor } from "./lib/site-editor.js";
import { connectionStatus, connectionFeedback, parseDomains, settingsError, restrictionsActive } from "./lib/settings.js";
import { initLanguage, t, setLanguage, resolveLanguage, savePreference, translatePage, appLanguage } from "./lib/i18n.js";

const { preference: languagePreference } = await initLanguage();
const $ = (id) => document.getElementById(id);
renderIcons();
$("rules").append($("reportSettings"), $("disputedFs"));
const views = ["overview", "rules", "lock", "settings"];
const legacyViews = { mode: "overview", modeSettings: "rules", siteSettings: "rules", reportSettings: "rules", contentSettings: "settings" };
function showView() {
  const hash = location.hash.slice(1);
  const view = views.includes(hash) ? hash : legacyViews[hash] || "overview";
  for (const id of views) $(id).hidden = id !== view;
  for (const link of document.querySelectorAll(".sidebar nav a")) {
    if (link.hash === `#${view}`) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  }
  window.scrollTo(0, 0);
  $(view).scrollTo(0, 0);
}
window.addEventListener("hashchange", showView);
for (const link of document.querySelectorAll(".sidebar nav a")) link.addEventListener("click", (event) => {
  event.preventDefault();
  location.hash = link.hash;
  showView();
});
showView();
let current = null;
let saving = false;
const siteEditors = new Map();
let privateCoverage;
let overviewSignature;
chrome.extension?.isAllowedIncognitoAccess?.((allowed) => {
  privateCoverage = allowed;
  renderOverview();
});
function renderOverview() {
  const s = describeStatus(current, { version: chrome.runtime.getManifest?.().version });
  const signature = JSON.stringify([s, privateCoverage, current?.inspectText, current?.failClosed]);
  if (signature === overviewSignature) return;
  overviewSignature = signature;
  $("protectionDot").className = `dot ${s.protection.level}`;
  $("protectionTitle").textContent = s.protection.headline;
  $("protectionDetail").textContent = s.protection.detail;
  $("lockTitle").textContent = s.lock.headline;
  $("lockDetail").textContent = s.lock.detail;
  $("lockNotice").textContent = current && restrictionsActive(current) ? t("opt.lockHint.active")
    : current?.appPresent ? t("opt.lockHint.managed") : t("opt.lockHint.browser");
  $("domainLayer").textContent = $("searchLayer").textContent = current ? t("details.on") : t("opt.coverage.unknown");
  $("textLayer").textContent = !current ? t("opt.coverage.unknown") : current.inspectText !== false || restrictionsActive(current) ? t("details.on") : t("details.off");
  $("privateLayer").textContent = t(privateCoverage === undefined ? "opt.coverage.unknown" : privateCoverage ? "details.on" : "opt.coverage.notEnabled");
  $("privateHint").hidden = privateCoverage !== false;
  $("statusDetails").replaceChildren(...s.details.flatMap(([key, value]) => {
    const dt = document.createElement("dt"), dd = document.createElement("dd");
    dt.textContent = key; dd.textContent = value;
    return [dt, dd];
  }));
  $("protectionNotices").replaceChildren(...s.notices.map((notice) => {
    const p = document.createElement("p");
    p.className = "notice"; p.textContent = notice.text;
    return p;
  }));
}
// Timers can expire and connections can go stale without a storage write.
let previousRestrictions;
setInterval(() => {
  if (!current) return;
  renderOverview();
  renderConnection();
  const restricted = restrictionsActive(current);
  if (restricted !== previousRestrictions) { previousRestrictions = restricted; refreshEditors(); }
}, 1000);
const fields = ["custom", "allow", "customTerms", "blockingMode", "inspectText", "textSensitivity"];
const saves = ["saveCustom", "saveAllow", "saveTerms", "saveMode", "saveChecks"];
for (const id of [...saves, "checkConnection"]) {
  const button = $(id), label = document.createElement("span"), icon = document.createElement("span");
  label.dataset.i18n = button.dataset.i18n;
  label.textContent = button.textContent;
  delete button.dataset.i18n;
  icon.dataset.icon = id === "checkConnection" ? "refresh-cw" : "save";
  icon.setAttribute("aria-hidden", "true");
  button.replaceChildren(icon, label);
}
renderIcons();
const sections = [
  { button: "saveCustom", message: "msgCustom", controls: { custom: "customBlocks" } },
  { button: "saveAllow", message: "msgAllow", controls: { allow: "allowlist" } },
  { button: "saveTerms", message: "msgTerms", controls: { customTerms: "customTerms" } },
  { button: "saveMode", message: "msgMode", controls: { blockingMode: "mode" } },
  { button: "saveChecks", message: "msgChecks", controls: { inspectText: "inspectText", textSensitivity: "textSensitivity" } },
];

function savedValue(id, key) {
  if (id === "blockingMode") return current.mode === "strict" ? "strict" : "blocklist";
  if (id === "inspectText") return current.inspectText !== false;
  if (id === "textSensitivity") return String(current.textSensitivity ?? 50);
  return (current[key] || []).join("\n");
}
function isDirty(section) {
  return current && !current.appPresent && Object.entries(section.controls).some(([id, key]) =>
    (id === "inspectText" ? $(id).checked : $(id).value) !== savedValue(id, key));
}
function updateDrafts() {
  const dirtySections = sections.filter(isDirty);
  for (const section of sections) {
    const dirty = isDirty(section);
    section.note.textContent = dirty ? t("opt.unsaved") : "";
    section.reset.hidden = !dirty;
    $(section.button).hidden = true;
    section.reset.disabled = saving;
  }
  $("saveBar").hidden = !dirtySections.length;
  $("draftCount").textContent = t("opt.draftCount", dirtySections.length);
  $("saveAll").disabled = $("discardAll").disabled = saving;
  for (const id of views) {
    const link = document.querySelector(`.sidebar a[href="#${id}"]`);
    link.classList.toggle("has-draft", dirtySections.some(section => $(section.button).closest(".page-view").id === id));
  }
  refreshEditors();
}
function refreshEditors() {
  const restricted = current && restrictionsActive(current);
  $("lockHint").hidden = !restricted && !current?.appPresent;
  $("lockHint").textContent = t(restricted ? "opt.lockHint.active" : "opt.lockHint.managed");
  for (const editor of siteEditors.values()) editor.refresh();
  for (const radio of document.querySelectorAll('[name="modeChoice"]')) {
    radio.checked = radio.value === $("blockingMode").value;
    radio.disabled = !current || !!current.appPresent || saving ||
      (restrictionsActive(current) && current.mode === "strict" && radio.value === "blocklist");
  }
  $("modeDescription").textContent = t($("blockingMode").value === "strict" ? "opt.mode.strict" : "opt.mode.standard");
}
for (const section of sections) {
  const actions = document.createElement("div");
  actions.className = "field-actions";
  const button = $(section.button);
  button.before(actions);
  actions.append(button);
  const reset = document.createElement("button");
  reset.type = "button";
  reset.className = "secondary";
  reset.dataset.i18n = "opt.discard";
  reset.textContent = t("opt.discard");
  reset.hidden = true;
  reset.onclick = () => {
    for (const [id, key] of Object.entries(section.controls)) {
      if (id === "inspectText") $(id).checked = savedValue(id, key);
      else $(id).value = savedValue(id, key);
    }
    $("sensitivityValue").textContent = $("textSensitivity").value;
    $(section.message).textContent = t("opt.discarded");
    $("saveMessage").textContent = "";
    updateDrafts();
  };
  actions.append(reset);
  section.reset = reset;
  section.note = document.createElement("p");
  section.note.className = "draft-note";
  section.note.setAttribute("role", "status");
  actions.before(section.note);
  for (const id of Object.keys(section.controls)) $(id).addEventListener("input", () => {
    $(section.message).textContent = "";
    $("saveMessage").textContent = $("savedMessage").textContent = "";
    updateDrafts();
  });
}
for (const [id, key] of [["custom", "customBlocks"], ["allow", "allowlist"]]) {
  siteEditors.set(id, siteEditor($(id), key, () => current));
}
for (const radio of document.querySelectorAll('[name="modeChoice"]')) radio.onchange = () => {
  $("blockingMode").value = radio.value;
  $("blockingMode").dispatchEvent(new Event("input", { bubbles: true }));
};
$("discardAll").onclick = () => {
  if (saving || !current || current.appPresent) return;
  fillFields(current);
  for (const section of sections) $(section.message).textContent = "";
  $("saveMessage").textContent = "";
  $("savedMessage").textContent = t("opt.discarded");
  updateDrafts();
};
window.addEventListener("beforeunload", (event) => {
  if (sections.some(isDirty)) { event.preventDefault(); event.returnValue = ""; }
});

/**
 * The false-positive corrections the user made from block pages: words they
 * disowned (`ignoreTerms`) and hosts they exempted (`textAllow`). Unlike the
 * lists above, the app never owns these, so this section stays editable even
 * when managed — and REMOVING an entry re-enables that blocking, which is a
 * tightening, so `guardedUpdate` allows it even during a lock. Each item mutates
 * the local `state` copy so the list can re-render without a round trip.
 */
function renderReported(state) {
  for (const key of ["ignoreTerms", "textAllow"]) {
    const box = $(key);
    box.textContent = "";
    if (!state[key]?.length) {
      const note = document.createElement("span");
      note.className = "empty-note"; note.textContent = t("opt.corr.empty");
      box.append(note);
    }
    for (const item of state[key] || []) {
      const chip = document.createElement("span");
      chip.className = "chip";
      const label = document.createElement("span");
      label.textContent = item;                 // textContent, never innerHTML
      const x = document.createElement("button");
      x.type = "button";
      x.textContent = "×";                  // ×
      x.title = t("opt.unblockTitle");
      x.setAttribute("aria-label", t("opt.removeAria", item));
      x.onclick = async () => {
        const next = (state[key] || []).filter((v) => v !== item);
        const r = await send({ type: "update", patch: { [key]: next } });
        if (r.ok) {
          state[key] = next;
          renderReported(state);
          $("msgReported").textContent = "";
        } else {
          $("msgReported").textContent = t("opt.couldNotRemove", r.reason);
        }
      };
      chip.append(label, x);
      box.appendChild(chip);
    }
  }
}

/**
 * Reports made while a lock was running. They could not loosen the lock then,
 * so they were parked in `disputed`. Here the user can APPLY one (move it into
 * the real list — allowed only once the lock has ended, since applying is a
 * loosening) or DISMISS it (drop it, which loosens nothing and works any time).
 * Hidden entirely when the queue is empty, which is the normal case.
 */
function renderDisputed(state) {
  const fs = $("disputedFs");
  const list = state.disputed || [];
  if (!list.length) { fs.hidden = true; return; }

  const locked = restrictionsActive(state);
  fs.hidden = false;
  $("disputedHint").textContent = locked ? t("opt.disputed.locked") : t("opt.disputed.ended");

  const box = $("disputed");
  box.textContent = "";
  for (const entry of list) {
    const value = entry.term || entry.host || "";
    if (!value) continue;
    const chip = document.createElement("span");
    chip.className = "chip";
    const label = document.createElement("span");
    label.textContent = value;                  // textContent, never innerHTML
    chip.appendChild(label);

    if (!locked) {
      const apply = document.createElement("button");
      apply.type = "button";
      apply.className = "apply";
      apply.textContent = t("opt.apply");
      apply.onclick = () => resolveDisputed(state, entry, true);
      chip.appendChild(apply);
    }
    const x = document.createElement("button");
    x.type = "button";
    x.textContent = "×";                    // ×
    x.title = t("opt.dismiss");
    x.setAttribute("aria-label", t("opt.dismissAria", value));
    x.onclick = () => resolveDisputed(state, entry, false);
    chip.appendChild(x);

    box.appendChild(chip);
  }
}

async function resolveDisputed(state, entry, apply) {
  const payload = entry.term ? { term: entry.term, apply } : { host: entry.host, apply };
  const r = await send({ type: "resolveDisputed", entry: payload });
  if (r.ok) {
    Object.assign(state, r.state);              // worker returns the new state
    renderDisputed(state);
    renderReported(state);                      // an applied item now appears above
    $("msgDisputed").textContent = r.applied ? t("opt.applied") : "";
  } else {
    $("msgDisputed").textContent = r.reason === "locked"
      ? t("opt.cannotUntilEnd")
      : t("opt.couldNotDo", r.reason);
  }
}

async function init() {
  const state = await send({ type: "getState" });
  if (!state || state.ok === false) {
    renderOverview();
    $("modeTitle").textContent = t("status.unavailable");
    $("connectionMessage").textContent = settingsError({ reason: "unavailable" });
    return;
  }
  showState(state, true);
}

function renderConnection() {
  const connection = connectionStatus(current);
  $("mode").className = `mode ${connection.managed ? "managed" : "standalone"}`;
  $("modeTitle").textContent = connection.title;
  $("modeDetail").textContent = connection.detail;
}

function showState(state, fill = false) {
  const drafts = new Set(sections.filter(isDirty));
  current = state;
  previousRestrictions = restrictionsActive(state);
  renderOverview();
  const connection = connectionStatus(state);
  renderConnection();
  for (const id of [...fields, ...saves]) $(id).disabled = connection.managed || saving;
  for (const notice of document.querySelectorAll(".managed-notice")) notice.hidden = !connection.managed;
  if (fill) fillFields(state);
  else for (const section of sections) {
    if (drafts.has(section)) continue;
    for (const [id, key] of Object.entries(section.controls)) {
      if (id === "inspectText") $(id).checked = savedValue(id, key);
      else $(id).value = savedValue(id, key);
    }
  }
  $("sensitivityValue").textContent = $("textSensitivity").value;
  renderReported(state);
  renderDisputed(state);
  updateDrafts();
}

function fillFields(state) {
  $("custom").value = (state.customBlocks || []).join("\n");
  $("allow").value = (state.allowlist || []).join("\n");
  $("customTerms").value = (state.customTerms || []).join("\n");
  $("blockingMode").value = state.mode === "strict" ? "strict" : "blocklist";
  $("inspectText").checked = state.inspectText !== false;
  $("textSensitivity").value = state.textSensitivity ?? 50;
  $("sensitivityValue").textContent = state.textSensitivity ?? 50;
}

async function save(patch, messageID, buttonID) {
  if (saving || !current || current.appPresent) return;
  saving = true;
  for (const id of [...fields, ...saves]) $(id).disabled = true;
  updateDrafts();
  $(messageID).className = "msg";
  $(messageID).textContent = t("opt.saving");
  const r = await send({ type: "update", patch });
  if (r.ok) {
    // Update only the saved fields: another section may contain unsaved edits.
    showState(r.state);
    for (const section of sections) for (const [id, key] of Object.entries(section.controls)) {
      if (!(key in patch)) continue;
      if (id === "inspectText") $(id).checked = savedValue(id, key);
      else $(id).value = savedValue(id, key);
    }
    $(messageID).textContent = t("opt.saved");
    $(messageID).className = "msg success";
    $("savedMessage").textContent = t("opt.saved");
    $("savedMessage").className = "msg success";
    updateDrafts();
  } else {
    $(messageID).textContent = settingsError(r);
    $(messageID).className = "msg error";
    const section = sections.find(item => Object.values(item.controls).includes(r.field));
    if (section) {
      location.hash = $(section.button).closest(".page-view").id; showView();
      $(section.message).className = "msg error";
      $(section.message).textContent = settingsError(r);
    }
    if (r.reason === "app-managed") await init();
  }
  saving = false;
  for (const id of [...fields, ...saves]) $(id).disabled = !current || !!current.appPresent;
  updateDrafts();
  if (!r.ok && r.field) {
    const section = sections.find(item => Object.values(item.controls).includes(r.field));
    const id = section && Object.keys(section.controls).find(id => section.controls[id] === r.field);
    if (id) { if (siteEditors.has(id)) siteEditors.get(id).reveal(); else $(id).focus(); }
  }
}

$("saveAll").onclick = () => {
  const patch = {};
  for (const section of sections.filter(isDirty)) {
    for (const [id, key] of Object.entries(section.controls)) {
      if (id === "custom" || id === "allow") {
        const parsed = parseDomains($(id).value);
        if (parsed.invalid.length) {
          $("saveMessage").className = "msg error";
          $("saveMessage").textContent = t("opt.badLines", parsed.invalid.join(", "));
          location.hash = "rules"; showView(); siteEditors.get(id).reveal(); return;
        }
        patch[key] = parsed.domains;
      } else if (id === "customTerms") {
        patch[key] = [...new Set($(id).value.split(/\r?\n/).map(term => term.trim()).filter(Boolean))];
      } else patch[key] = id === "inspectText" ? $(id).checked : id === "textSensitivity" ? Number($(id).value) : $(id).value;
    }
  }
  if (!Object.keys(patch).length) return;
  if (patch.mode === "strict" && current.mode !== "strict" && !window.confirm(t("opt.strictConfirm"))) return;
  save(patch, "saveMessage", "saveAll");
};

for (const [input, key, msg, button] of [
  ["custom", "customBlocks", "msgCustom", "saveCustom"],
  ["allow", "allowlist", "msgAllow", "saveAllow"],
]) {
  $(button).onclick = () => {
    const result = parseDomains($(input).value);
    if (result.invalid.length) {
      $(msg).className = "msg error";
      $(msg).textContent = t("opt.badLines", result.invalid.join(", "));
      return;
    }
    save({ [key]: result.domains }, msg, button);
  };
}
$("saveMode").onclick = () => {
  const mode = $("blockingMode").value;
  if (mode === "strict" && current?.mode !== "strict"
      && !window.confirm(t("opt.strictConfirm"))) return;
  save({ mode }, "msgMode", "saveMode");
};
$("saveTerms").onclick = () => save({ customTerms:
  [...new Set($("customTerms").value.split(/\r?\n/).map((t) => t.trim()).filter(Boolean))],
}, "msgTerms", "saveTerms");
$("textSensitivity").oninput = () => { $("sensitivityValue").textContent = $("textSensitivity").value; };
$("saveChecks").onclick = () => save({
  inspectText: $("inspectText").checked,
  textSensitivity: Number($("textSensitivity").value),
}, "msgChecks", "saveChecks");
$("checkConnection").onclick = async () => {
  $("checkConnection").disabled = true;
  $("connectionMessage").textContent = t("conn.checking");
  const r = await send({ type: "forceSync" });
  const state = await send({ type: "getState" });
  $("connectionMessage").textContent = connectionFeedback(r, state);
  if (state && state.ok !== false) showState(state, !!state.appPresent);
  $("checkConnection").disabled = false;
};
// Reflect a native connection that happens while Settings is open. Preserve
// drafts for browser-only edits; app-owned values are always shown read-only.
chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes.state?.newValue) {
    const state = changes.state.newValue;
    showState(state, !!state.appPresent);
  }
});
for (const id of [...fields, ...saves]) $(id).disabled = true;
updateDrafts();

// Language: Automatic follows the browser; a choice here is remembered for
// every Hisn page. Switching re-renders in place — drafts in the fields stay.
$("uiLanguage").value = languagePreference === "ar" || languagePreference === "en"
  ? languagePreference : "auto";
$("uiLanguage").onchange = async () => {
  for (const el of document.querySelectorAll(".msg, #connectionMessage")) el.textContent = "";
  const preference = $("uiLanguage").value;
  await savePreference(preference).catch(() => {});
  setLanguage(resolveLanguage(preference, chrome.i18n?.getUILanguage?.() ?? navigator.language,
                              await appLanguage()));
  translatePage(document);
  overviewSignature = undefined;
  if (current) showState(current);
};
init();
