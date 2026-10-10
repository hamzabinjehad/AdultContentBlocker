// Exercise the real worker entry point with Chrome's transport/storage mocked.
// Model an existing installation upgraded while the legacy catch-all remains
// enabled. Manifest defaults do not reset Chrome's persisted enabled rulesets.
let state, dynamic = [], enabled = ["lockdown"], listener, nativeReply, rejectDynamic = false;
let registeredScripts = [{ id: "hisn-scan", js: ["content/scan.js"] }], registrations = 0;
globalThis.chrome = {
  storage: { local: {
    get: async () => ({ state }),
    set: async (value) => { state = value.state; },
  } },
  declarativeNetRequest: {
    getDynamicRules: async () => dynamic,
    // Chrome's semantics: remove the named ids, then add. Replacing the whole
    // set here (as this mock once did) could never catch a lost downloaded rule.
    updateDynamicRules: async ({ removeRuleIds = [], addRules = [] }) => {
      if (rejectDynamic) throw new Error("dynamic update rejected");
      dynamic = dynamic.filter((r) => !removeRuleIds.includes(r.id)).concat(addRules);
    },
    // Chrome enables/disables only the named rulesets; enabling the baseline
    // does not implicitly turn off an inherited static lockdown ruleset.
    updateEnabledRulesets: async ({ enableRulesetIds = [], disableRulesetIds = [] }) => {
      enabled = [...new Set(enabled.filter((id) => !disableRulesetIds.includes(id)).concat(enableRulesetIds))];
    },
  },
  scripting: {
    getRegisteredContentScripts: async () => registeredScripts,
    registerContentScripts: async (scripts) => { registeredScripts = scripts; registrations++; },
    unregisterContentScripts: async () => { registeredScripts = []; },
  },
  tabs: { query: async () => [] },
  runtime: {
    onInstalled: { addListener() {} }, onStartup: { addListener() {} },
    onMessage: { addListener(fn) { listener = fn; } },
    sendNativeMessage: async () => {
      if (nativeReply === "hang") return new Promise(() => {});
      if (nativeReply) return nativeReply;
      throw new Error("no native host");
    },
    id: "hisnextid",
    getURL: (path) => `chrome-extension://hisnextid/${path}`,
  },
  alarms: {
    onAlarm: { addListener() {} },
    get: async (name) => alarms.get(name),
    create: async (name, info) => {
      alarmCreations.push({ name, ...info });
      alarms.set(name, { name, ...info });
    },
  },
};
// An upgraded installation can retain the old one-minute heartbeat.
// Its list alarm is absent, as it can be after browser restart. No lifecycle
// callback is invoked here: worker boot must repair both conditions.
const alarms = new Map([
  ["heartbeat", { name: "heartbeat", periodInMinutes: 1 }],
]);
const alarmCreations = [];
globalThis.console = { info() {}, warn() {}, error() {} };
// Timers under our control: jsc has no clearTimeout, and a real 10 s timer
// would hold the run open. `fire(ms)` runs every pending timer of that length.
const timers = [];
globalThis.setTimeout = (fn, ms) => { timers.push({ fn, ms }); return timers.length; };
globalThis.clearTimeout = () => {};
const fire = (ms) => { for (const t of timers.splice(0)) if (t.ms === ms) t.fn(); };
let nativeCalls = 0;
const realSend = globalThis.chrome.runtime.sendNativeMessage;
globalThis.chrome.runtime.sendNativeMessage = async (...a) => { nativeCalls++; return realSend(...a); };
const { DEFAULT_STATE, booted, ensureAlarms, ALARMS, ensureBlocked, applyRules } = await import("../background.js");
const OPTIONS = { id: "hisnextid", url: "chrome-extension://hisnextid/options.html" };
const PAGE = { id: "hisnextid", url: "https://evil.example/", tab: { id: 3 }, frameId: 0 };
const message = (msg, sender = OPTIONS) => new Promise((resolve) => listener(msg, sender, resolve));
let checks = 0;
function check(ok, label) { checks++; if (!ok) throw new Error(label); }
await booted;
check(registeredScripts[0].js.join(",") === "content/feed.js,content/scan.js" && registrations === 1,
      "worker migrates legacy persisted scanner registration to feed dependency order");
check(!enabled.includes("lockdown"), "worker startup retires an inherited legacy static lockdown");
check(!dynamic.some((r) => r.condition.urlFilter === "*"),
      "a fresh unlocked worker does not replace stale static lockdown with dynamic lockdown");
enabled.push("lockdown");
rejectDynamic = true;
let rejected = false;
try { await applyRules({ ...DEFAULT_STATE, mode: "strict" }); } catch { rejected = true; }
check(rejected && enabled.includes("lockdown"),
      "a rejected dynamic replacement does not first remove the legacy protection");
rejectDynamic = false;
await applyRules({ ...DEFAULT_STATE, mode: "blocklist" });
check(!enabled.includes("lockdown") && !dynamic.some((r) => r.condition.urlFilter === "*")
      && ["blocklist", "keywords", "safesearch", "paths", "web_protection"].every((id) => enabled.includes(id)),
      "Standard mode retires stale lockdown without a catch-all while preserving baseline sets");
check(ALARMS.heartbeat === 0.5 && ALARMS.listUpdate === 360,
      "heartbeat is thirty seconds without accelerating list downloads");
check(alarms.get("heartbeat")?.periodInMinutes === 0.5,
      "worker boot migrates a legacy one-minute heartbeat without lifecycle events");
check(alarms.get("listUpdate")?.periodInMinutes === 360 && alarms.size === 2,
      "worker boot restores the missing list alarm without an install event");
check(nativeCalls === 1, "a worker start with a stale heartbeat checks in at once");
const alreadyConfigured = alarmCreations.length;
await ensureAlarms();
check(alarmCreations.length === alreadyConfigured,
      "healthy alarms are not recreated or their next firing postponed");
alarms.clear();
await ensureAlarms();
check(alarms.size === 2 && alarms.get("heartbeat")?.periodInMinutes === 0.5
      && alarms.get("listUpdate")?.periodInMinutes === 360,
      "alarms cleared by a browser restart return with current periods");
alarms.set("heartbeat", { name: "heartbeat", periodInMinutes: 60 });
await ensureAlarms();
check(alarms.get("heartbeat")?.periodInMinutes === 0.5,
      "an incorrect persisted heartbeat period is corrected");
check(alarms.get("listUpdate")?.periodInMinutes === 360,
      "correcting the heartbeat preserves the list-update schedule");
state = { ...DEFAULT_STATE };
check(!(await message({ type: "forceSync" })).ok, "missing native app is tolerated");
check(!state.failClosed && !state.appPresent, "fresh browser-only install does not lock down");
let r = await message({ type: "update", patch: { customBlocks: ["example.com"], customTerms: ["GAMBLING"] } });
check(r.ok && state.customTerms[0] === "gambling", "standalone saves normalized terms through real worker");
check(enabled.includes("blocklist") && enabled.includes("keywords") && enabled.includes("safesearch")
      && enabled.includes("paths") && enabled.includes("web_protection"),
      "baseline rule sets, SafeSearch and path rules included, enabled without app");
check(dynamic.some((r) => r.condition.requestDomains?.includes("example.com")), "standalone rules installed");
r = await message({ type: "update", patch: { mode: "strict", allowlist: ["safe.example"] } });
check(r.ok && dynamic.some((r) => r.condition.urlFilter === "*"), "standalone strict mode is operational");
check(!enabled.includes("lockdown") && dynamic.some((r) => r.condition.urlFilter === "*"
      && r.action.type === "block" && r.condition.resourceTypes?.includes("media")),
      "intentional Strict mode retains dynamic embedded blocking without the legacy static catch-all");
nativeReply = { lockUntil: 0, mode: "off", customBlocks: ["managed.example"], allowlist: [] };
check((await message({ type: "forceSync" })).ok, "native app can connect after standalone use");
check(state.appPresent && state.customBlocks[0] === "managed.example", "app settings become authoritative");
r = await message({ type: "update", patch: { customBlocks: [] } });
check(!r.ok && r.reason === "app-managed", "raw message cannot overwrite app settings");
check((await message({ type: "update", patch: { textAllow: ["medical.example"] } })).ok, "local corrections work with unlocked app");
nativeReply = null;
state.lockUntil = Date.now() + 100000;
state.lastHeartbeat = Date.now() - 600000;
await message({ type: "forceSync" });
check(state.failClosed, "app loss during lock fails closed");
state.lockUntil = 0;
check(!(await message({ type: "update", patch: { textAllow: ["other.example"] } })).ok, "fail-closed still guards corrections");
// Settings changes rebuild only the policy rules; a downloaded generation's
// rules (ids at or above RULE_DOWNLOADED_BASE) survive every one of them.
dynamic.push({ id: 10000, priority: 3, action: { type: "block" },
               condition: { requestDomains: ["listed.example"], resourceTypes: ["main_frame"] } });
await message({ type: "update", patch: { textSensitivity: 60 } });
nativeReply = { lockUntil: Date.now() + 3600000, mode: "strict", allowlist: [], customBlocks: [] };
await message({ type: "forceSync" });
nativeReply = null;
check(dynamic.some((r) => r.id === 10000), "downloaded rules survive settings and lock changes");
check(dynamic.some((r) => r.condition.urlFilter === "*"), "and the lock's strict rules are in place beside them");

// A responsive native host cannot claim unlocked mirrors when its configured
// system authority disappears, including after damaged-policy strict recovery.
nativeReply = { lockUntil: 64092211200000, mode: "strict", allowlist: [], customBlocks: [] };
await message({ type: "forceSync" });
const recoveredDeadline = state.lockUntil, recoveredHeartbeat = state.lastHeartbeat;
nativeReply = { ok: false, reason: "authority-unreachable" };
check(!(await message({ type: "forceSync" })).ok, "authority loss is a failed heartbeat even if host replies");
check(state.mode === "strict" && state.lockUntil === recoveredDeadline,
      "authority loss after policy recovery never replaces strict policy with unlocked mirrors");
check(state.lastHeartbeat === recoveredHeartbeat, "an absent authority cannot refresh its trusted heartbeat");
nativeReply = { lockUntil: 0, mode: "off", allowlist: [], customBlocks: [] };
check((await message({ type: "forceSync" })).ok && !state.failClosed && state.lockUntil === 0,
      "a fresh authoritative unlocked reply can end restrictive recovery");

// Failed durable expiry uses a transport-only hold, including standard mode.
// Local text exemptions cannot relax a lock the authority still holds.
nativeReply = { lockUntil: 64092211200000, mode: "blocklist", allowlist: [], customBlocks: [] };
await message({ type: "forceSync" });
r = await message({ type: "update", patch: { textAllow: ["held.example"] } });
check(!r.ok && r.reason === "locked", "an authority-held expiry still guards local text exemptions");
r = await message({ type: "update", patch: { ignoreTerms: ["porn"] } });
check(!r.ok && r.reason === "locked", "an authority-held expiry still guards term exemptions");
nativeReply = null;
state.lastHeartbeat = Date.now() - 600000;
await message({ type: "forceSync" });
check(state.failClosed, "authority loss after a held standard lock remains eligible for lockdown");
nativeReply = { lockUntil: 0, mode: "off", allowlist: [], customBlocks: [] };
check((await message({ type: "forceSync" })).ok && !state.failClosed,
      "a successful authoritative unlock releases a held expiry");
check((await message({ type: "update", patch: { textAllow: ["held.example"] } })).ok,
      "local corrections become editable after a confirmed unlock");

// A bridge that never answers is silence, not a heartbeat that holds every
// other transaction behind it forever.
nativeReply = "hang";
const pending = message({ type: "forceSync" });
await Promise.resolve();
fire(10000);
check(!(await pending).ok, "a hung native host times out and counts as unreachable");
nativeReply = null;

// Who may send what: a web page's content script only ever scores text.
for (const type of ["update", "resolveDisputed", "reportWrongWord", "reportWrongBlock", "getState", "forceSync"]) {
  r = await message({ type, patch: { customBlocks: [] } }, PAGE);
  check(!r.ok && r.reason === "forbidden-sender", `a content script cannot send ${type}`);
}
r = await message({ type: "update", patch: {} }, { id: "otherext", url: "chrome-extension://otherext/x.html" });
check(r.reason === "forbidden-sender", "another extension cannot send update");
r = await message({ type: "scoreText", zones: {} }, OPTIONS);
check(r.reason === "forbidden-sender", "scoreText comes from a tab, not an extension page");
let testTab = { id: 3, url: "https://fixture.example/original" }, replacements = 0;
chrome.tabs.get = async () => testTab;
chrome.tabs.create = async () => { replacements++; return { id: 4 }; };
chrome.tabs.remove = async () => {};
ensureBlocked(3, testTab.url);
testTab.url = "https://fixture.example/clean";
fire(1500); await Promise.resolve(); await Promise.resolve();
check(replacements === 0, "delayed enforcement never blocks a new page for an old verdict");
ensureBlocked(3, testTab.url);
testTab.pendingUrl = "https://fixture.example/next";
fire(1500); await Promise.resolve(); await Promise.resolve();
check(replacements === 0, "delayed enforcement leaves a pending navigation alone");
delete testTab.pendingUrl;
ensureBlocked(3, testTab.url);
fire(1500); await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
check(replacements === 1, "a refused page still open is replaced by the worker");

// The actual granular worker branch, not a reimplementation of its policy.
// jsc has no URL/fetch; provide only the protocol surface this branch needs.
globalThis.URL = class {
  constructor(value) {
    const match = /^(https?):\/\/([^/?#]+)(?:[/?#]|$)/.exec(value);
    if (!match) throw new Error("invalid fixture URL");
    this.protocol = match[1] + ":"; this.hostname = match[2];
  }
};
const seedTerms = JSON.parse(readFile("../seed/terms.json"));
let termsFetches = 0, sessionWrites = 0;
globalThis.fetch = async () => { termsFetches++; return { json: async () => seedTerms }; };
chrome.storage.session = { set: async () => { sessionWrites++; } };
const FEED = { id: "hisnextid", url: "https://x.com/home", tab: { id: 3 }, frameId: 0 };
state = { ...DEFAULT_STATE, customTerms: ["fixtureadult"] };
const replacementCount = replacements, timerCount = timers.filter((t) => t.ms === 1500).length;
r = await message({ type: "scoreFeedItem", zones: { body: "fixtureadult" } }, FEED);
check(r.block && r.enabled, "granular real worker detects selected post text");
check(Object.keys(r).sort().join(",") === "block,enabled", "feed verdict reveals no matched terms/host/text");
check(sessionWrites === 0 && replacements === replacementCount
      && timers.filter((t) => t.ms === 1500).length === timerCount,
      "granular adult verdict writes no session record and schedules no whole-tab enforcement");
r = await message({ type: "scoreFeedItem", zones: { body: "Gardening notes", title: "fixtureadult", url: "fixtureadult", meta: "fixtureadult" } }, FEED);
check(!r.block && r.enabled, "granular worker ignores supplied page title/path/meta");
r = await message({ type: "scoreFeedItem", zones: { body: "Weather", alt: "fixtureadult" } }, FEED);
check(r.block, "granular worker includes visible caption/alt signals");
r = await message({ type: "scoreFeedItem", zones: { body: { malicious: "fixtureadult" }, alt: [] } }, FEED);
check(!r.block, "non-string granular payloads are never coerced to text");
state.ignoreTerms = ["fixtureadult"];
check(!(await message({ type: "scoreFeedItem", zones: { body: "fixtureadult" } }, FEED)).block,
      "granular scores preserve user term corrections");
state.ignoreTerms = []; state.textAllow = ["x.com"];
r = await message({ type: "scoreFeedItem", zones: { body: "fixtureadult" } }, FEED);
check(!r.block && !r.enabled, "narrow text-only host exception suppresses granular scoring");
state.textAllow = []; state.inspectText = false;
check(!(await message({ type: "scoreFeedItem", zones: { body: "fixtureadult" } }, FEED)).enabled,
      "unlocked text-scanning off disables granular layer, not baseline rules");
state.lockUntil = Date.now() + 60000;
check((await message({ type: "scoreFeedItem", zones: { body: "fixtureadult" } }, FEED)).block,
      "active commitment keeps granular scoring despite stale inspect flag");
for (const sender of [PAGE, { ...FEED, frameId: 1 }, { ...FEED, url: "https://x.com.evil.test/home" }, { ...FEED, url: "https://cdn.x.com/home" }]) {
  r = await message({ type: "scoreFeedItem", zones: { body: "fixtureadult" } }, sender);
  check(r.reason === "unsupported-feed", "feed scoring rejects unsupported origins/frames based on trusted sender");
}
check((await message({ type: "scoreFeedItem", zones: {} }, OPTIONS)).reason === "forbidden-sender",
      "extension settings page cannot impersonate feed content frame");
check(termsFetches === 1, "same worker vocabulary cached across individual item scores");

print(`${checks}/${checks} production worker checks passed`);
