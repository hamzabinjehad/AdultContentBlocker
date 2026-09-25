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
import { connectionStatus, parseDomains, settingsError, restrictionsActive } from "./lib/settings.js";
import { initLanguage, t, setLanguage, resolveLanguage, savePreference, translatePage } from "./lib/i18n.js";

const { preference: languagePreference } = await initLanguage();
const $ = (id) => document.getElementById(id);
let current = null;
const fields = ["custom", "allow", "customTerms", "blockingMode", "inspectText", "textSensitivity"];
const saves = ["saveCustom", "saveAllow", "saveTerms", "saveMode", "saveChecks"];
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
  for (const section of sections) {
    const dirty = isDirty(section);
    section.note.textContent = dirty ? t("opt.unsaved") : "";
    section.reset.hidden = !dirty;
  }
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
    updateDrafts();
  });
}
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
    $("connectionMessage").textContent = settingsError({ reason: "unavailable" });
    return;
  }
  showState(state, true);
}

function showState(state, fill = false) {
  current = state;
  const connection = connectionStatus(state);
  $("mode").className = `mode ${connection.managed ? "managed" : "standalone"}`;
  $("modeTitle").textContent = connection.title;
  $("modeDetail").textContent = connection.detail;
  $("lockHint").textContent = restrictionsActive(state) ? t("opt.lockHint.active")
    : connection.managed ? t("opt.lockHint.managed")
    : t("opt.lockHint.browser");
  for (const id of [...fields, ...saves]) $(id).disabled = connection.managed;
  if (fill) fillFields(state);
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
  $(buttonID).disabled = true;
  $(messageID).className = "msg";
  $(messageID).textContent = t("opt.saving");
  const r = await send({ type: "update", patch });
  if (r.ok) {
    // Update only the saved fields: another section may contain unsaved edits.
    showState(r.state);
    if (patch.customBlocks) $("custom").value = r.state.customBlocks.join("\n");
    if (patch.allowlist) $("allow").value = r.state.allowlist.join("\n");
    if (patch.customTerms) $("customTerms").value = r.state.customTerms.join("\n");
    $(messageID).textContent = t("opt.saved");
    $(messageID).className = "msg success";
    updateDrafts();
  } else {
    $(messageID).textContent = settingsError(r);
    $(messageID).className = "msg error";
    if (r.reason === "app-managed") await init();
  }
  $(buttonID).disabled = !current || !!current.appPresent;
}

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
  $("connectionMessage").textContent = r.ok ? t("opt.connected") : t("opt.notConnected");
  const state = await send({ type: "getState" });
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

// Language: Automatic follows the browser; a choice here is remembered for
// every Hisn page. Switching re-renders in place — drafts in the fields stay.
$("uiLanguage").value = languagePreference === "ar" || languagePreference === "en"
  ? languagePreference : "auto";
$("uiLanguage").onchange = async () => {
  const preference = $("uiLanguage").value;
  await savePreference(preference).catch(() => {});
  setLanguage(resolveLanguage(preference, chrome.i18n?.getUILanguage?.() ?? navigator.language));
  translatePage(document);
  if (current) showState(current);
};
init();
