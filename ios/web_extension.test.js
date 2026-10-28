// The Python package test places this harness beside the staged resources.
import { checkedRequest, messageHandler, checkedVisualRequest,
  checkedVisualPatch, visualMessageHandler } from "./background.js";
import { buildIndex } from "./lib/score.js";
import { createOptionsController } from "./options.js";

// JavaScriptCore's bare shell has no browser URL class; Node and real browsers
// use their native implementation. This shim covers only the test URL fixtures.
if (!globalThis.URL) globalThis.URL = class {
  constructor(value) {
    const match = /^([a-z]+:)\/\/([^/?#]+)(?:[/?#]|$)/i.exec(value);
    if (!match) throw new Error("Invalid fixture URL");
    this.protocol = match[1].toLowerCase();
    this.hostname = match[2].toLowerCase();
  }
};
const say = globalThis.print ?? ((value) => console.log(value));
let checks = 0;
function check(condition, label) {
  if (!condition) throw new Error(label);
  checks++;
}
function optionsDocument() {
  const nodes = new Map(), listeners = new Map();
  function element(value = "") {
    const events = new Map();
    return { value, checked:false, disabled:true, hidden:true, textContent:"", dataset:{},
      attributes:{}, addEventListener(type, listener) { events.set(type, listener); },
      setAttribute(name, content) { this.attributes[name] = content; },
      emit(type) { return events.get(type)?.(); } };
  }
  const modes = ["off", "female", "male", "all"].map(element);
  return { documentElement:{}, visibilityState:"visible", modes,
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, element()); return nodes.get(id); },
    querySelectorAll(selector) { if (selector !== 'input[name="personBlur"]') throw new Error("Unexpected selector"); return modes; },
    addEventListener(type, listener) { listeners.set(type, listener); },
    emit(type) { listeners.get(type)?.(); },
  };
}
async function optionsContract() {
  const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
  const timers = new Map(); let nextTimer = 0;
  const controllerFor = options => createOptionsController({ ...options,
    delay:callback => { const id = ++nextTimer; timers.set(id, callback); return id; },
    cancelDelay:id => timers.delete(id) });
  for (const language of ["en", "ar"]) {
    const root = optionsDocument(), get = id => root.getElementById(id), calls = [], patches = [];
    let preferences = { inspectVisual:false, personBlur:"off" }, failRead = true, failSave = false;
    let engineState = "ready", openCalls = 0, rejectOpen = false, nextStatus;
    const api = { runtime:{
      openOptionsPage:async () => { openCalls++; if (rejectOpen) throw new Error("Open unavailable"); },
      sendMessage:async message => {
        calls.push(message.type);
        if (message.type === "getVisualSettings") {
          if (failRead) throw new Error("Storage unavailable"); return { ...preferences };
        }
        if (message.type === "saveVisualSettings") {
          patches.push(message.patch);
          if (failSave) throw new Error("Write unavailable");
          preferences = { ...preferences, ...message.patch }; return { settings:{ ...preferences } };
        }
        if (nextStatus) { const response = nextStatus; nextStatus = null; return response; }
        return { state:engineState };
      },
    } };
    const polling = [];
    const controller = controllerFor({ api, root, language, schedule:callback => polling.push(callback) });
    check(get("inspectVisual").disabled && root.modes.every(mode => mode.disabled), "Visual choices unavailable before confirmed read");
    check(root.documentElement.dir === (language === "ar" ? "rtl" : "ltr"), "Safari controls localize direction");
    check(get("modeFemale").textContent === (language === "ar" ? "النساء" : "Women")
      && get("modeMale").textContent === (language === "ar" ? "الرجال" : "Men"), "Appearance choices localize separately from adult checks");
    await controller.load();
    check(get("inspectVisual").disabled && !get("retrySettings").hidden && get("saveStatus").dataset.state === "unavailable", "Failed read keeps controls disabled and offers a real retry");
    failRead = false; get("retrySettings").emit("click"); await flush();
    check(!get("inspectVisual").disabled && get("retrySettings").hidden && root.modes[0].checked, "Retry loads confirmed opt-out preferences");
    check(get("engineStatus").textContent.includes(language === "ar" ? "لا يمكن فحص كل" : "Not every"), "Model readiness does not promise complete checking");
    const adult = get("inspectVisual"); adult.checked = true; adult.emit("change");
    check(adult.disabled && !adult.checked, "Pending save disables visual choices and shows confirmed value");
    await flush();
    check(preferences.inspectVisual && adult.checked && !adult.disabled && get("saveStatus").dataset.state === "saved", "Adult-media switch saves independent opt-in");
    for (const value of ["female", "male", "all", "off"]) {
      const mode = root.modes.find(input => input.value === value);
      mode.checked = true; mode.emit("change"); await flush();
      check(preferences.personBlur === value && root.modes.filter(input => input.checked).length === 1
        && mode.checked && preferences.inspectVisual, "Face preference saves without disabling adult-media checking");
    }
    for (const [key,value] of [['personRegion','faces'],['personEffect','blur'],['personMedia','images']]) {
      get(key).value=value;get(key).emit('change');await flush();
      check(preferences[key]===value&&get(key).value===value&&preferences.inspectVisual,'Safari presentation choice saves without changing adult protection');
    }
    check(patches.every(patch => Object.keys(patch).length === 1 && ["inspectVisual", "personBlur", "personRegion", "personEffect", "personMedia"].includes(Object.keys(patch)[0])), "Controls never send website permissions or app-policy changes");
    failSave = true; adult.checked = false; adult.emit("change"); await flush();
    check(adult.checked && preferences.inspectVisual && get("saveStatus").dataset.state === "failed", "Failed save restores confirmed UI without claiming successful persistence");
    await controller.refreshStatus();
    check(get("saveStatus").dataset.state === "failed", "Model status refresh preserves a failed-save notice");
    failSave = false; adult.checked = false; adult.emit("change"); await flush();
    check(!adult.checked && get("saveStatus").dataset.state === "saved", "Successful retry clears the old save error");
    engineState = "failed"; await controller.refreshStatus();
    check(get("statusDot").dataset.state === "failed", "Unavailable model has a distinct non-clean status");
    engineState = "unsupported-clean"; await controller.refreshStatus();
    check(get("statusDot").dataset.state === "unavailable", "Malformed checker status never becomes ready");
    let finishStale; nextStatus = new Promise(resolve => { finishStale = resolve; });
    const stale = controller.refreshStatus();
    engineState = "waiting"; adult.checked = true; adult.emit("change"); await flush();
    finishStale({ state:"ready" }); await stale;
    check(get("statusDot").dataset.state === "waiting", "Late model status cannot overwrite status for changed preferences");
    get("language").emit("click");
    check(root.documentElement.dir === (language === "ar" ? "ltr" : "rtl") && adult.checked,
      "Language switch changes presentation, not saved visual preferences");
    const count = calls.length; root.visibilityState = "hidden"; polling[0](); await flush();
    check(calls.length === count, "Hidden settings page does not poll or wake visual runtime");
    root.visibilityState = "visible"; root.emit("visibilitychange"); await flush();
    check(calls.length > count, "Returning to settings refreshes checker status");
    check(!get("openSettings").hidden, "Full settings action is exposed only when supported");
    await get("openSettings").emit("click");
    check(openCalls === 1, "Full settings action calls the browser options API");
    rejectOpen = true; await get("openSettings").emit("click");
    check(get("saveStatus").dataset.state === "open-failed", "Failed full-page opening remains visible and recoverable");
  }
  const root = optionsDocument();
  controllerFor({ api:{ runtime:{} }, root, schedule:() => {} });
  check(root.getElementById("openSettings").hidden, "Unsupported full settings action stays hidden");
  const malformed = controllerFor({ api:{ runtime:{ sendMessage:async () => ({ inspectVisual:true, personBlur:"unknown" }) } }, root, schedule:() => {} });
  await malformed.load();
  check(root.getElementById("inspectVisual").disabled && root.getElementById("saveStatus").dataset.state === "unavailable", "Malformed stored preferences do not activate visual controls");
  check(timers.size === 0, "Completed requests clear their timeout timers");
  const stalledRoot = optionsDocument(); let finishRead, finishSave, stallRead = true, stallSave = true;
  let prefs = { inspectVisual:false, personBlur:"off" };
  const stalled = controllerFor({ root:stalledRoot, schedule:() => {}, api:{ runtime:{ sendMessage:async message => {
    if (message.type === "getVisualSettings") return stallRead ? new Promise(resolve => finishRead = resolve) : prefs;
    if (message.type === "saveVisualSettings") return stallSave ? new Promise(resolve => finishSave = resolve) : {settings:prefs};
    return {state:"disabled"};
  } } } });
  const expire = async () => { await flush(); for (const [id, callback] of [...timers]) { timers.delete(id); callback(); } await flush(); };
  const waiting = stalled.load(); await expire(); await waiting;
  check(!stalledRoot.getElementById("retrySettings").hidden && stalledRoot.getElementById("saveStatus").dataset.state === "unavailable", "Stalled load times out with a usable retry");
  finishRead(prefs); await flush();
  check(stalledRoot.getElementById("inspectVisual").disabled, "Late timed-out load cannot silently enable controls");
  stallRead = false; await stalled.load();
  const stalledAdult = stalledRoot.getElementById("inspectVisual"); stalledAdult.checked = true; stalledAdult.emit("change"); await expire();
  check(!stalledAdult.disabled && !stalledAdult.checked && stalledRoot.getElementById("saveStatus").dataset.state === "failed", "Stalled save restores confirmed controls instead of freezing forever");
  prefs = {inspectVisual:true,personBlur:"off"}; finishSave({settings:prefs}); await flush();
  check(!stalledAdult.checked, "Late unconfirmed save cannot overwrite confirmed UI");
  stalledRoot.getElementById("retrySettings").emit("click"); await flush();
  check(stalledAdult.checked && !stalledAdult.disabled, "Retry reconciles a save that completed after timeout");
}
const sender = { id: "safari-fixture", tab: { id: 1 }, frameId: 0, url: "https://x.com/home" };
const item = { type: "scoreFeedItem", zones: { body: "porn porn porn", alt: "" } };
check(!!checkedRequest(item, sender, sender.id), "Top-level X item permitted");
for (const altered of [{ ...sender, id: "foreign" }, { ...sender, frameId: 1 },
  { ...sender, tab: undefined }, { ...sender, tab: { id: -1 } },
  { ...sender, url: "https://x.com.evil.example/home" }, { ...sender, url: "https://evilx.com/home" },
  { ...sender, url: "file:///x.com" }, { ...sender, url: "not a URL" }]) {
  check(checkedRequest(item, altered, sender.id) === null, "Invalid/unrelated sender rejected");
}
for (const zones of [null, [], { body: 7 }, { body: "a".repeat(20_002) },
  { body: "test", title: "not item-scoped" }]) {
  check(checkedRequest({ ...item, zones }, sender, sender.id) === null, "Malformed item zones rejected");
}
check(!!checkedRequest({ type: "scoreText", zones: { body: "a".repeat(200_001) } }, sender, sender.id), "Bounded page accepted");
check(checkedRequest({ type: "scoreText", zones: { body: "a".repeat(200_002) } }, sender, sender.id) === null, "Oversized page rejected");
check(checkedRequest({ type: "saveSettings", zones: {} }, sender, sender.id) === null, "Mutation message rejected");

async function main() {
  const index = buildIndex({ terms: [{ t: "porn", w: 4 }], negatives: [], exempt_domains: ["wikipedia.org"] });
  const updates = [], replacements = [], removed = [];
  let currentURL = sender.url;
  const api = { runtime: { id: sender.id, getURL: (name) => "safari-extension://fixture/" + name },
    tabs: { get: async () => ({ url: currentURL, windowId: 3, index: 2, active: false }),
      update: async (id, options) => updates.push({ id, options }),
      create: async (options) => { replacements.push(options); return { id: 2 }; },
      remove: async (id) => removed.push(id) } };
  let stored = { inspectVisual: true, personBlur: "all" };
  api.storage = { local: { get: async () => ({ state: stored }),
    set: async ({ state }) => { stored = state; } } };
  const options = { id: sender.id, url: api.runtime.getURL("options.html") };
  for (const type of ["getVisualSettings", "inspectVisualMedia", "cancelVisualMedia"]) {
    check(checkedVisualRequest({ type }, sender, api), "HTTP content visual request permitted");
    for (const altered of [{ ...sender, id: "foreign" }, { ...sender, frameId: -1 },
      { ...sender, tab: { id: -1 } }, { ...sender, url: "file:///example" },
      { ...sender, url: "not a URL" }]) {
      check(!checkedVisualRequest({ type }, altered, api), "Untrusted visual sender rejected");
    }
  }
  for (const type of ["getVisualSettings", "getVisualStatus", "saveVisualSettings"]) {
    check(checkedVisualRequest({ type }, options, api), "Own options visual request permitted");
    // Safari's "Open full settings" shows the same page in a normal tab.
    const tabbed = { ...options, tab: { id: 5 }, frameId: 0 };
    check(checkedVisualRequest({ type }, tabbed, api), "Own options page in a tab permitted");
    check(checkedVisualRequest({ type }, { ...tabbed, url: options.url + "#visual" }, api), "Options page fragment permitted");
    if (type !== "getVisualSettings") check(!checkedVisualRequest({ type }, { ...tabbed, url: "https://example.com/options.html" }, api),
      "Webpage named options.html cannot use options-only messages");
    check(!checkedVisualRequest({ type }, { ...tabbed, id: "foreign" }, api), "Foreign extension options page rejected");
  }
  check(!checkedVisualRequest({ type: "saveVisualSettings" }, sender, api), "Content cannot change visual preferences");
  check(!checkedVisualRequest({ type: "getVisualStatus" }, sender, api), "Content cannot probe engine status");
  check(!checkedVisualRequest({ type: "saveVisualSettings" }, { ...options, url: api.runtime.getURL("blocked.html") }, api),
    "Another extension page cannot mutate preferences");
  for (const patch of [null, [], {}, { inspectVisual: 1 }, { personBlur: "unknown" },
    { inspectVisual: true, allowlist: ["x.com"] }, { inspectVisual: "true" }, {personRegion:'body'}, {personEffect:'none'}, {personMedia:'all'}]) {
    check(checkedVisualPatch(patch) === null, "Malformed visual settings rejected");
  }
  check(checkedVisualPatch({ inspectVisual: true, personBlur: "female" })?.personBlur === "female", "Valid opt-in visual preferences accepted");
  const inspectionInputs = [];
  const visual = visualMessageHandler(api, { inspect: async (message, source, preferences) => {
    inspectionInputs.push(preferences); return { state: "ready" };
  }, status: async () => ({ state: "loading" }) });
  function visualRequest(message, source = options, handler = visual) {
    return new Promise((resolve, reject) => {
      if (!handler(message, source, resolve)) reject(new Error("Visual request rejected"));
    });
  }
  check((await visualRequest({ type: "getVisualSettings" })).inspectVisual === true, "Visual defaults read only from local settings");
  await visualRequest({ type: "inspectVisualMedia", inspectVisual: false, personBlur: "off" }, sender);
  check(inspectionInputs[0].inspectVisual === true && inspectionInputs[0].personBlur === "all", "Page payload cannot override saved visual policy");
  check((await visualRequest({ type: "getVisualStatus" })).state === "loading", "Model status returned without clean-media claim");
  check((await visualRequest({ type: "saveVisualSettings", patch: { personBlur: "male" } })).settings.personBlur === "male", "Options persist a bounded visual setting");
  check((await visualRequest({ type: "saveVisualSettings", patch: { inspectText: false } })).error === "invalid-settings", "Visual options cannot disable text scanner");
  stored = { inspectVisual: "yes", personBlur: "unsupported" };
  const sanitized = await visualRequest({ type: "getVisualSettings" });
  check(sanitized.inspectVisual === false && sanitized.personBlur === "off", "Malformed stored preferences fail to opt in");
  api.storage.local.get = async () => { throw new Error("Storage unavailable"); };
  const visualUnavailable = await visualRequest({ type: "getVisualSettings" });
  check(visualUnavailable.state === "unavailable" && visualUnavailable.block === undefined, "Storage failure never becomes a clean verdict");
  async function score(message, source = sender, load = async () => index) {
    return new Promise((resolve, reject) => {
      if (!messageHandler(api, load)(message, source, resolve)) reject(new Error("Message rejected"));
    });
  }
  const blocked = await score(item);
  check(blocked.block === true && blocked.enabled === true, "Adult item flagged");
  check(updates.length === 0 && replacements.length === 0 && removed.length === 0, "Item never redirects entire feed");
  const clean = await score({ type: "scoreFeedItem", zones: { body: "A programming workshop for students" } });
  check(clean.block === false && clean.enabled === true, "Clean post remains available");
  const exempt = await score({ type: "scoreText", zones: { body: "porn porn porn" } },
    { ...sender, url: "https://en.wikipedia.org/wiki/Test" });
  check(exempt.block === false && exempt.enabled === false, "Text exemption only");
  const unavailable = await score(item, sender, async () => { throw new Error("Fixture unavailable"); });
  check(unavailable.unavailable === true && unavailable.block === undefined, "Failure never becomes clean verdict");
  const page = { type: "scoreText", zones: { body: "porn porn porn" } };
  await score(page);
  for (let i = 0; i < 6; i++) await Promise.resolve();
  check(updates.length === 0 && replacements.length === 1 && removed[0] === sender.tab.id,
    "Current flagged page replaced, not updated with refused-page Back history");
  check(replacements[0].url.endsWith("blocked.html") && replacements[0].windowId === 3
    && replacements[0].index === 2 && replacements[0].active === false, "Generic replacement preserves tab position and activation");
  currentURL = "https://x.com/changed";
  await score(page);
  await Promise.resolve(); await Promise.resolve();
  check(replacements.length === 1, "Stale page verdict never redirects new route");
  await score(page, { ...sender, frameId: 1 });
  await Promise.resolve(); await Promise.resolve();
  check(replacements.length === 1, "Subframe verdict never redirects top page");
  await optionsContract();
  say(`Safari bridge contract checks passed (${checks})`);
}
main().catch((error) => { say(String(error)); if (globalThis.quit) quit(1); else throw error; });
