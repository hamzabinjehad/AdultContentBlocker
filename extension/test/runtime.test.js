// Exercise the real worker entry point with Chrome's transport/storage mocked.
// Model an existing installation upgraded while the legacy catch-all remains
// enabled. Manifest defaults do not reset Chrome's persisted enabled rulesets.
let state, dynamic = [], enabled = ["lockdown"], listener, nativeReply, rejectDynamic = false;
let rejectEnabled = false, ignoreDynamicWrite = false, rejectRegistration = false, ignoreRegistration = false;
let dynamicWriteDelayMs = 0;
let uiLanguage = "auto", appLanguage = "", uiLocale = "en", actionBadge = "", actionTitle = "", rejectAction = false;
const actionWrites = [];
let registeredScripts = [{ id: "hisn-scan", js: ["content/scan.js"] }], registrations = 0;
globalThis.chrome = {
  storage: { local: {
    get: async () => ({ state, uiLanguage, appLanguage }),
    set: async (value) => { state = value.state; },
  } },
  declarativeNetRequest: {
    getDynamicRules: async () => dynamic,
    getEnabledRulesets: async () => enabled,
    // Chrome's semantics: remove the named ids, then add. Replacing the whole
    // set here (as this mock once did) could never catch a lost downloaded rule.
    updateDynamicRules: async ({ removeRuleIds = [], addRules = [] }) => {
      if (dynamicWriteDelayMs) await new Promise(resolve => setTimeout(resolve, dynamicWriteDelayMs));
      if (rejectDynamic) throw new Error("dynamic update rejected");
      if (ignoreDynamicWrite) return;
      dynamic = dynamic.filter((r) => !removeRuleIds.includes(r.id)).concat(addRules);
    },
    // Chrome enables/disables only the named rulesets; enabling the baseline
    // does not implicitly turn off an inherited static lockdown ruleset.
    updateEnabledRulesets: async ({ enableRulesetIds = [], disableRulesetIds = [] }) => {
      if (rejectEnabled) throw new Error("enabled ruleset update rejected");
      enabled = [...new Set(enabled.filter((id) => !disableRulesetIds.includes(id)).concat(enableRulesetIds))];
    },
  },
  scripting: {
    getRegisteredContentScripts: async ({ ids } = {}) => registeredScripts.filter(script => !ids || ids.includes(script.id)),
    registerContentScripts: async (scripts) => {
      if (rejectRegistration) throw new Error("scanner registration rejected");
      if (ignoreRegistration) return;
      registeredScripts.push(...scripts); registrations++;
    },
    unregisterContentScripts: async ({ ids } = {}) => { registeredScripts = registeredScripts.filter(script => ids && !ids.includes(script.id)); },
  },
  tabs: { query: async () => [] },
  i18n: { getUILanguage: () => uiLocale },
  action: {
    getBadgeText: async () => actionBadge,
    setBadgeText: async values => { actionWrites.push(["badge", values]); if (rejectAction) throw new Error("badge write rejected"); actionBadge = values.text; },
    setBadgeBackgroundColor: async values => { actionWrites.push(["color", values]); if (rejectAction) throw new Error("badge color rejected"); },
    setTitle: async values => { actionWrites.push(["title", values]); if (rejectAction) throw new Error("action title rejected"); actionTitle = values.title; },
  },
  runtime: {
    onInstalled: { addListener() {} }, onStartup: { addListener() {} },
    onMessage: { addListener(fn) { listener = fn; } },
    sendNativeMessage: async () => {
      if (nativeReply === "hang") return new Promise(() => {});
      if (typeof nativeReply === "function") return nativeReply();
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
globalThis.setTimeout = (fn, ms) => { const timer = { fn, ms }; timers.push(timer); return timer; };
globalThis.clearTimeout = (timer) => { if (timer) timer.cancelled = true; };
const fire = (ms) => {
  for (const t of timers.filter(timer => !timer.cancelled && timer.ms === ms)) { t.cancelled = true; t.fn(); }
};
let nativeCalls = 0;
const realSend = globalThis.chrome.runtime.sendNativeMessage;
globalThis.chrome.runtime.sendNativeMessage = async (...a) => { nativeCalls++; return realSend(...a); };
const { DEFAULT_STATE, booted, ensureAlarms, restoreWorkerProtection, ALARMS, ensureBlocked, applyRules, toolbarStatus } = await import("../background.js");
const OPTIONS = { id: "hisnextid", url: "chrome-extension://hisnextid/options.html" };
const PAGE = { id: "hisnextid", url: "https://evil.example/", tab: { id: 3 }, frameId: 0 };
const message = (msg, sender = OPTIONS) => new Promise((resolve) => listener(msg, sender, resolve));
let checks = 0;
function check(ok, label) { checks++; if (!ok) throw new Error(label); }
await booted;
await toolbarStatus.whenIdle();
check(actionWrites.some(([kind, values]) => kind === "badge" && values.text === "?")
      && actionBadge === "" && actionTitle === "Hisn — Rules and registrations verified",
      "worker starts checking and clears toolbar uncertainty only after verified browser application");
check(registeredScripts[0].js.join(",") === "content/feed.js,content/scan.js" && registrations === 1,
      "worker migrates legacy persisted scanner registration to feed dependency order");
check(!enabled.includes("lockdown"), "worker startup retires an inherited legacy static lockdown");
check(!dynamic.some((r) => r.condition.urlFilter === "*"),
      "a fresh unlocked worker does not replace stale static lockdown with dynamic lockdown");
check(state.enforcement.status === "applied" && state.enforcement.version === 1
      && state.enforcement.appliedRevision === state.desiredRevision,
      "worker publishes an applied revision only after rule and scanner readback");
state = { ...DEFAULT_STATE, mode: "strict", lastHeartbeat: Date.now(), lockUntil: Date.now()+60000 };
enabled=[];dynamic=[];registeredScripts=[];
const callsBeforeRepair=nativeCalls;
await restoreWorkerProtection();
check(dynamic.some(r=>r.condition.urlFilter==='*') && enabled.includes('blocklist'),
      "recent native contact cannot suppress immediate strict-rule recovery");
check(registeredScripts.some(script=>script.id==='hisn-scan'),
      "worker recovery restores missing page-scanner registration");
check(nativeCalls===callsBeforeRepair,
      "immediate enforcement repair does not launch unnecessary native polling");
const scannerContract={...registeredScripts.find(script=>script.id==='hisn-scan')};
for (const [key,value] of [['allFrames',false],['matchOriginAsFallback',false],
  ['persistAcrossSessions',false],['runAt','document_idle'],['world','MAIN'],
  ['matches',['https://safe.example/*']],['excludeMatches',['https://x.com/*']],
  ['includeGlobs',['*safe*']],['excludeGlobs',['*video*']]]) {
  registeredScripts=[{...scannerContract,[key]:value}];
  const before=registrations;
  await restoreWorkerProtection();
  check(registrations===before+1 && registeredScripts[0].allFrames===true
    && registeredScripts[0].runAt==='document_start',
    `worker repairs text-scanner ${key} even when script filenames match`);
}
state={...state,inspectVisual:true};
await restoreWorkerProtection();
registeredScripts=registeredScripts.map(script=>script.id==='hisn-visual'
  ? {...script,allFrames:false,runAt:'document_idle',matches:['https://safe.example/*']} : script);
const beforeVisualRepair=registrations;
await restoreWorkerProtection();
check(registrations===beforeVisualRepair+1
  && registeredScripts.find(script=>script.id==='hisn-visual').matches[0]==='<all_urls>'
  && registeredScripts.find(script=>script.id==='hisn-visual').allFrames===true,
  'visual gate coverage is repaired independently of matching model/script files');
state={...state,inspectVisual:false};
await restoreWorkerProtection();
check(!registeredScripts.some(script=>script.id==='hisn-visual'),
  'recovery does not enable a visual layer the user never chose');
state={...DEFAULT_STATE};
enabled.push("lockdown");
rejectDynamic = true;
let rejected = false;
try { await applyRules({ ...DEFAULT_STATE, mode: "strict" }); } catch { rejected = true; }
check(rejected && enabled.includes("lockdown"),
      "a rejected dynamic replacement does not first remove the legacy protection");
rejectDynamic = false;
await applyRules({ ...DEFAULT_STATE, mode: "blocklist" });
check(!enabled.includes("lockdown") && !dynamic.some((r) => r.condition.urlFilter === "*")
      && ["blocklist", "keywords", "safesearch", "paths", "web_protection", "evasion", "encoded_queries"].every((id) => enabled.includes(id)),
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
check(["blocklist", "keywords", "safesearch", "paths", "web_protection", "evasion", "encoded_queries"].every((id) => enabled.includes(id)),
      "actual worker keeps all baseline layers when native app is absent");
check(registeredScripts.some((script) => script.id === "hisn-scan"),
      "actual worker keeps text scanning registered without native app");
let r = await message({ type: "update", patch: { customBlocks: ["example.com"], customTerms: ["GAMBLING"] } });
check(r.ok && state.customTerms[0] === "gambling", "standalone saves normalized terms through real worker");
check(enabled.includes("blocklist") && enabled.includes("keywords") && enabled.includes("safesearch")
      && enabled.includes("paths") && enabled.includes("web_protection")
      && enabled.includes("evasion") && enabled.includes("encoded_queries"),
      "baseline rule sets, SafeSearch, path and obfuscation rules included, enabled without app");
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
check(["blocklist", "keywords", "safesearch", "paths", "web_protection", "evasion", "encoded_queries"].every((id) => enabled.includes(id))
      && registeredScripts.some((script) => script.id === "hisn-scan")
      && dynamic.some((rule) => rule.condition.urlFilter === "*"),
      "app removal during commitment retains baseline, text scanner and restrictive policy together");
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
check(["blocklist", "keywords", "safesearch", "paths", "web_protection", "evasion", "encoded_queries"].every((id) => enabled.includes(id)),
      "confirmed unlock releases the commitment but never turns off baseline blocking");

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
for (const type of ["update", "resolveDisputed", "reportWrongWord", "reportWrongBlock", "getState", "forceSync", "repairProtection"]) {
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

state = { ...DEFAULT_STATE };
r = await message({ type: 'update', patch: { inspectVisual: true, personBlur: 'female' } });
check(r.ok && registeredScripts.some(script => script.id === 'hisn-visual'
  && script.js.join(',') === 'visual/capture.js,content/visual-effects.js,content/visual.js'), 'visual opt-in registers capture and effects before scanner');
check(registeredScripts.some(script => script.id === 'hisn-visual' && script.css?.includes('content/visual-gate.css') && script.runAt === 'document_start'), 'visual opt-in registers the pre-display stylesheet before page content');
check(registeredScripts.some(script => script.id === 'hisn-scan'), 'visual registration never replaces text registration');
r = await message({ type: 'update', patch: { inspectText: false } });
check(r.ok && !registeredScripts.some(script => script.id === 'hisn-scan')
  && registeredScripts.some(script => script.id === 'hisn-visual'), 'text off leaves independent visual registration');
state.lockUntil = Date.now() + 60000;
for (const patch of [{ inspectVisual: false }, { personBlur: 'off' }, { personBlur: 'male' }, {personRegion:'faces'}, {personEffect:'blur'}, {personMedia:'images'}]) {
  r = await message({ type: 'update', patch });
  check(!r.ok && r.reason === 'locked', 'worker rejects visual weakening during commitment');
}
check((await message({ type: 'update', patch: { personBlur: 'all' } })).ok, 'worker permits strengthening person coverage');
r = await message({ type: 'getVisualSettings' }, PAGE);
check(r.inspectVisual === true && r.personBlur === 'all' && r.lockUntil === undefined, 'content receives only visual preferences');
check((await message({ type: 'getVisualStatus' }, PAGE)).reason === 'forbidden-sender', 'content cannot inspect engine privileged channel');
check((await message({ type: 'inspectVisualMedia', id: 'x', width: 1, height: 1, rgba: 'AAAAAA==' }, OPTIONS)).reason === 'forbidden-sender', 'options cannot impersonate content capture');
check((await message({ type: 'inspectVisualMedia', id: 'x', width: 1, height: 1, rgba: 'AAAAAA==' }, PAGE)).state === 'unavailable', 'unsupported engine never returns clean');
state.lockUntil = 0;
check((await message({ type: 'update', patch: { inspectVisual: false, personBlur: 'off' } })).ok, 'unlocked user can disable visual beta');
check(!registeredScripts.some(script => script.id === 'hisn-visual')
  && ['blocklist', 'keywords', 'safesearch', 'paths', 'web_protection', 'evasion', 'encoded_queries'].every(id => enabled.includes(id)), 'visual off keeps baseline enforcement');

// Healthy delayed IPC stays "checking" beyond the ordinary page-message
// deadline. Exercise the actual UI helper through the actual worker listener.
const { send, MESSAGE_TIMEOUT_MS, CONNECTION_TIMEOUT_MS } = await import("../lib/messages.js");
chrome.runtime.sendMessage = (msg, reply) => listener(msg, OPTIONS, reply);
state = { ...DEFAULT_STATE, customBlocks: ["keep.example"], allowlist: ["safe.example"] };
nativeReply = () => new Promise(resolve => setTimeout(() => resolve({ lockUntil: 0, mode: "blocklist" }), 7000));
let connectionFinished = false;
const delayedConnection = send({ type: "forceSync" }).then(reply => { connectionFinished = true; return reply; });
for (let i = 0; i < 30; i++) await Promise.resolve();
fire(MESSAGE_TIMEOUT_MS);
for (let i = 0; i < 30; i++) await Promise.resolve();
check(!connectionFinished && CONNECTION_TIMEOUT_MS > 10000, "native connection stays pending at five seconds");
fire(7000);
r = await delayedConnection;
check(r.ok && r.connected && state.enforcement.status === "applied", "seven-second native reply succeeds after browser readback");
check(state.customBlocks.join() === "keep.example" && state.allowlist.join() === "safe.example",
      "older bridge omissions preserve existing lists instead of deleting rules");

nativeReply = { lockUntil: Date.now() + 60000, mode: "strict" };
await message({ type: "forceSync" });
const priorAppliedRevision = state.appliedRevision;
rejectDynamic = true;
nativeReply = { lockUntil: Date.now() + 120000, mode: "strict", customBlocks: ["new.example"] };
r = await message({ type: "forceSync" });
check(!r.ok && r.connected && r.reason === "enforcement-failed" && state.appPresent
      && state.lockUntil === nativeReply.lockUntil && state.bridgeConnection.status === "connected",
      "rule failure keeps authoritative lock and native ownership rather than pretending disconnection");
check(state.enforcement.status === "failed" && state.enforcement.failure.code === "dynamic-rules"
      && state.desiredRevision > priorAppliedRevision && state.appliedRevision === priorAppliedRevision,
      "rejected DNR write records typed failure and leaves the applied revision behind desired state");
check(dynamic.some(rule => rule.condition.urlFilter === "*") && enabled.includes("blocklist"),
      "failed stricter-rule update retains prior strict enforcement and independent baseline");
rejectDynamic = false;
r = await message({ type: "forceSync" });
check(r.ok && state.enforcement.status === "applied" && state.appliedRevision === state.desiredRevision,
      "repair converges on the stored desired revision");

rejectEnabled = true;
r = await message({ type: "forceSync" });
check(!r.ok && r.connected && state.enforcement.failure.code === "baseline-rulesets"
      && dynamic.some(rule => rule.condition.urlFilter === "*"), "ruleset error does not replace existing strict policy");
rejectEnabled = false;
await message({ type: "forceSync" });
registeredScripts = [];
rejectRegistration = true;
r = await message({ type: "forceSync" });
check(!r.ok && r.connected && state.enforcement.status === "failed"
      && state.enforcement.failure.code === "text-scanner", "scanner API rejection never appears fully applied");
check(dynamic.some(rule => rule.condition.urlFilter === "*") && enabled.includes("safesearch"),
      "scanner registration failure preserves strict rules and SafeSearch");
rejectRegistration = false;
await message({ type: "forceSync" });
registeredScripts = [];
ignoreRegistration = true;
r = await message({ type: "forceSync" });
check(!r.ok && state.enforcement.failure.code === "text-registration-readback"
      && state.enforcement.layers.domains === "applied", "acknowledged but missing scanner is rejected by readback independently of installed domains");
ignoreRegistration = false;
await message({ type: "forceSync" });
ignoreDynamicWrite = true;
nativeReply = { lockUntil: Date.now() + 120000, mode: "strict", customBlocks: ["readback.example"] };
r = await message({ type: "forceSync" });
check(!r.ok && state.enforcement.failure.code === "rules-readback", "acknowledged but lost policy write is rejected by DNR readback");
ignoreDynamicWrite = false;
await message({ type: "forceSync" });

// An unlock remains authoritative, but incomplete browser application must
// retain the previously stricter rules until repair succeeds.
registeredScripts = [];
ignoreRegistration = true;
nativeReply = { lockUntil: 0, mode: "off" };
r = await message({ type: "forceSync" });
check(!r.ok && r.connected && state.lockUntil === 0 && state.enforcement.retainedStrict
      && dynamic.some(rule => rule.condition.urlFilter === "*"), "failed unlock application keeps the previous stricter browser policy");
ignoreRegistration = false;
check((await message({ type: "forceSync" })).ok && !dynamic.some(rule => rule.condition.urlFilter === "*"),
      "confirmed repair can complete an authoritative unlock without disabling baseline");
state.rulesApplied = 2;
r = await message({ type: "forceSync" });
check(!r.ok && state.enforcement.failure.code === "downloaded-rules-readback"
      && state.enforcement.downloadedRules.domains === 1 && enabled.includes("blocklist"),
      "missing downloaded rules cannot be certified from the stored installation count");
state.rulesApplied = 1;
check((await message({ type: "forceSync" })).ok, "matching downloaded-rule readback permits repair confirmation");
state.enforcement = {...state.enforcement,worker:"previous-worker"};
const staleReceiptState = await message({type:"getState"});
check(staleReceiptState.enforcement.status === "pending" && staleReceiptState.appPresent,
      "previous-worker receipt is pending until API repair without transferring native ownership");
check((await message({type:"forceSync"})).ok && state.enforcement.status === "applied",
      "current worker readback replaces a historical receipt");
for (const key of ["desiredRevision","appliedRevision","enforcement","bridgeConnection"]) {
  check(!(await message({type:"update",patch:{[key]:0}})).ok, "UI cannot manufacture an internal health receipt or revision");
}

const heartbeatBeforeMalformed = state.lastHeartbeat;
nativeReply = { unexpected: true };
r = await message({ type: "forceSync" });
check(!r.ok && r.reason === "native-malformed" && state.lastHeartbeat === heartbeatBeforeMalformed
      && state.appPresent, "malformed native replies cannot refresh trust or transfer ownership");

// Repair browser enforcement without depending on a native host. The held
// strict policy, commitment and historical authority must remain untouched.
state = { ...DEFAULT_STATE, mode: "strict", lockUntil: Date.now() + 600000,
  appPresent: true, failClosed: true, lastHeartbeat: Date.now() - 600000,
  bridgeConnection: { version: 1, status: "unavailable", reason: "authority-unreachable", checkedAt: 1234 },
  customBlocks: ["keep-blocked.example"], allowlist: ["keep-allowed.example"],
  customTerms: ["fixtureadult"], textAllow: ["medical.example"], ignoreTerms: ["fixturecorrection"],
  inspectVisual: true, personBlur: "female", personRegion: "full", personEffect: "solid", personMedia: "both",
  futureCapability: { unrecognizedByThisBuild: true } };
dynamic = [];
const callsBeforeIndependentRepair = nativeCalls;
nativeReply = { lockUntil: 0, mode: "off", customBlocks: [] };
const heldSettings = () => Object.fromEntries(Object.entries(state).filter(([key]) =>
  !["enforcement", "appliedRevision"].includes(key)));
const beforeIndependentRepair = JSON.stringify(heldSettings());
r = await message({ type: "repairProtection" });
check(r.ok && r.state.enforcement.status === "applied"
      && r.state.appliedRevision === r.state.desiredRevision,
      "browser-only repair confirms the current desired revision after API readback");
check(JSON.stringify(heldSettings()) === beforeIndependentRepair,
      "independent repair preserves strict mode, commitment, choices, native ownership, freshness and unknown fields");
check(nativeCalls === callsBeforeIndependentRepair && state.bridgeConnection.status === "unavailable",
      "browser repair neither polls native IPC nor manufactures a restored app connection");
check(dynamic.some(rule => rule.condition.urlFilter === "*")
      && registeredScripts.some(script => script.id === "hisn-scan")
      && registeredScripts.some(script => script.id === "hisn-visual"),
      "independent repair restores strict blocking and both chosen scanner layers");

registeredScripts = [];
rejectRegistration = true;
r = await message({ type: "repairProtection" });
check(!r.ok && r.reason === "enforcement-failed" && state.enforcement.status === "failed"
      && state.enforcement.failure.code === "text-scanner",
      "independent repair exposes registration failure rather than claiming success");
await toolbarStatus.whenIdle();
check(actionBadge === "!" && actionTitle === "Hisn — Protection needs repair",
      "an actual worker registration failure is visible on the toolbar without opening settings");
check(dynamic.some(rule => rule.condition.urlFilter === "*")
      && JSON.stringify(heldSettings()) === beforeIndependentRepair,
      "failed browser repair retains the existing strict policy and every held setting");
rejectRegistration = false;
r = await message({ type: "repairProtection" });
check(r.ok && state.enforcement.status === "applied" && nativeCalls === callsBeforeIndependentRepair,
      "failed browser repair can converge without a native connection");
await toolbarStatus.whenIdle();
check(actionBadge === "" && actionTitle.includes("registrations verified"),
      "independent verified repair clears the toolbar warning without requiring native IPC");

registeredScripts = [];
ignoreRegistration = true;
r = await message({ type: "repairProtection" });
check(!r.ok && r.reason === "enforcement-failed"
      && state.enforcement.failure.code === "text-registration-readback",
      "an acknowledged but missing scanner cannot make independent repair successful");
await toolbarStatus.whenIdle();
state.enforcement = { ...state.enforcement, worker: "historical-worker" };
const pendingToolbarReadback = await message({ type: "getState" });
await toolbarStatus.whenIdle();
check(pendingToolbarReadback.enforcement.status === "pending" && actionBadge === "!",
      "invalidating a historical receipt cannot clear the last toolbar failure before repair");
ignoreRegistration = false;
check((await message({ type: "repairProtection" })).ok,
      "independent retry verifies registration readback before confirming recovery");
const beforePatchRepair = JSON.stringify(state), beforePatchRules = JSON.stringify(dynamic);
r = await message({ type: "repairProtection", patch: { mode: "off", lockUntil: 0, appPresent: false } });
check(!r.ok && r.reason === "invalid-request" && JSON.stringify(state) === beforePatchRepair
      && JSON.stringify(dynamic) === beforePatchRules,
      "the independent repair endpoint refuses settings-shaped payloads without writing state or rules");
r = await message({ type: "repairProtection" }, { id: "otherext", url: "chrome-extension://otherext/options.html" });
check(r.reason === "forbidden-sender", "another extension cannot trigger independent browser repair");

// Slow browser work is not native loss and must not be reported unavailable
// at the ordinary five-second settings deadline.
dynamicWriteDelayMs = 7000;
let repairFinished = false;
const delayedRepair = send({ type: "repairProtection" }).then(reply => { repairFinished = true; return reply; });
for (let i = 0; i < 60; i++) await Promise.resolve();
fire(MESSAGE_TIMEOUT_MS);
for (let i = 0; i < 30; i++) await Promise.resolve();
check(!repairFinished, "independent browser repair remains pending beyond five seconds of rule work");
fire(7000);
r = await delayedRepair;
dynamicWriteDelayMs = 0;
check(r.ok && r.state.enforcement.status === "applied" && nativeCalls === callsBeforeIndependentRepair,
      "seven-second independent repair succeeds only after browser readback without native IPC");

state = { ...DEFAULT_STATE, customBlocks: ["standalone-block.example"], inspectText: false };
enabled = [];
registeredScripts = [];
r = await message({ type: "repairProtection" });
check(r.ok && !state.appPresent && !state.failClosed && state.lockUntil === 0
      && state.enforcement.layers.text === "off" && state.enforcement.layers.visual === "off"
      && nativeCalls === callsBeforeIndependentRepair,
      "standalone repair preserves optional scanner choices without inventing app ownership or a commitment");
check(dynamic.some(rule => rule.condition.requestDomains?.includes("standalone-block.example"))
      && !dynamic.some(rule => rule.condition.urlFilter === "*")
      && ["blocklist", "keywords", "safesearch", "paths", "web_protection", "evasion", "encoded_queries"].every(id => enabled.includes(id)),
      "standalone repair restores independent baseline and custom blocking without unexpectedly imposing strict mode");

await toolbarStatus.whenIdle();
uiLanguage = "ar";
await message({ type: "getState" }); await toolbarStatus.whenIdle();
check(actionTitle === "Hisn — تم التحقق من القواعد وتسجيل الفحص",
      "toolbar follows the saved Arabic preference without changing browser policy");
uiLanguage = "auto"; appLanguage = "ar";
await message({ type: "getState" }); await toolbarStatus.whenIdle();
check(actionTitle === "Hisn — تم التحقق من القواعد وتسجيل الفحص",
      "automatic toolbar language follows the native app language when known");
appLanguage = ""; uiLocale = "ar-DE";
await message({ type: "getState" }); await toolbarStatus.whenIdle();
check(actionTitle === "Hisn — تم التحقق من القواعد وتسجيل الفحص",
      "automatic toolbar language falls back to the browser locale");
uiLanguage = "en";
rejectAction = true;
r = await message({ type: "repairProtection" }); await toolbarStatus.whenIdle();
check(r.ok && state.enforcement.status === "applied" && nativeCalls === callsBeforeIndependentRepair,
      "rejected action UI APIs never fail rule repair or launch native IPC");
const actionCallsBeforeRetry = actionWrites.length;
rejectAction = false;
await message({ type: "getState" }); await toolbarStatus.whenIdle();
check(actionWrites.length > actionCallsBeforeRetry && actionTitle === "Hisn — Rules and registrations verified",
      "failed toolbar writes retry on a later current state read");
const quietToolbarCalls = actionWrites.length;
await message({ type: "getState" }); await toolbarStatus.whenIdle();
await message({ type: "getState" }); await toolbarStatus.whenIdle();
check(actionWrites.length === quietToolbarCalls, "unchanged verified worker state does not produce noisy toolbar writes");
check(actionWrites.every(([, values]) => !JSON.stringify(values).includes("://")),
      "worker toolbar payloads never include visited URLs or page content");

print(`${checks}/${checks} production worker checks passed`);
