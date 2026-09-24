// Exercise the real worker entry point with Chrome's transport/storage mocked.
let state, dynamic = [], enabled = [], listener, nativeReply;
globalThis.chrome = {
  storage: { local: {
    get: async () => ({ state }),
    set: async (value) => { state = value.state; },
  } },
  declarativeNetRequest: {
    getDynamicRules: async () => dynamic,
    updateDynamicRules: async ({ addRules }) => { dynamic = addRules; },
    updateEnabledRulesets: async ({ enableRulesetIds }) => { enabled = enableRulesetIds; },
  },
  scripting: {
    getRegisteredContentScripts: async () => [],
    registerContentScripts: async () => {},
    unregisterContentScripts: async () => {},
  },
  tabs: { query: async () => [] },
  runtime: {
    onInstalled: { addListener() {} }, onStartup: { addListener() {} },
    onMessage: { addListener(fn) { listener = fn; } },
    sendNativeMessage: async () => { if (nativeReply) return nativeReply; throw new Error("no native host"); },
  },
  alarms: {
    onAlarm: { addListener() {} },
    get: async (name) => alarms.get(name),
    create: async (name, info) => { alarms.set(name, { name, ...info }); },
  },
};
const alarms = new Map();
globalThis.console = { info() {}, warn() {}, error() {} };
let nativeCalls = 0;
const realSend = globalThis.chrome.runtime.sendNativeMessage;
globalThis.chrome.runtime.sendNativeMessage = async (...a) => { nativeCalls++; return realSend(...a); };
const { DEFAULT_STATE, booted, ensureAlarms, ALARMS } = await import("../background.js");
const message = (msg) => new Promise((resolve) => listener(msg, {}, resolve));
let checks = 0;
function check(ok, label) { checks++; if (!ok) throw new Error(label); }
await booted;
check(alarms.get("heartbeat")?.periodInMinutes === ALARMS.heartbeat
      && alarms.get("listUpdate")?.periodInMinutes === ALARMS.listUpdate,
      "a worker start restores both alarms without an install event");
check(nativeCalls === 1, "a worker start with a stale heartbeat checks in at once");
alarms.clear();
await ensureAlarms();
check(alarms.size === 2, "alarms cleared by a browser restart come back");
alarms.set("heartbeat", { name: "heartbeat", periodInMinutes: 60 });
await ensureAlarms();
check(alarms.get("heartbeat").periodInMinutes === 1, "a wrong period is corrected");
state = { ...DEFAULT_STATE };
check(!(await message({ type: "forceSync" })).ok, "missing native app is tolerated");
check(!state.failClosed && !state.appPresent, "fresh browser-only install does not lock down");
let r = await message({ type: "update", patch: { customBlocks: ["example.com"], customTerms: ["GAMBLING"] } });
check(r.ok && state.customTerms[0] === "gambling", "standalone saves normalized terms through real worker");
check(enabled.includes("blocklist") && enabled.includes("keywords"), "baseline rule sets enabled without app");
check(dynamic.some((r) => r.condition.requestDomains?.includes("example.com")), "standalone rules installed");
r = await message({ type: "update", patch: { mode: "strict", allowlist: ["safe.example"] } });
check(r.ok && dynamic.some((r) => r.condition.urlFilter === "*"), "standalone strict mode is operational");
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
print(`${checks}/${checks} production worker checks passed`);
