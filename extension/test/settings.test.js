import { parseDomains, settingsAccess, connectionStatus, connectionFeedback, restrictionsActive } from "../lib/settings.js";

let checks = 0;
function check(ok, label) { checks++; if (!ok) throw new Error(label); }
const solo = { appPresent: false, lockUntil: 0, failClosed: false };
const managed = { ...solo, appPresent: true, lastHeartbeat: 1000 };
const parsed = parseDomains("https://www.example.com/path\nexample.com\nbad domain\n-invalid.com\n\n");
check(parsed.domains.join() === "example.com", "normalize and deduplicate domains");
check(parsed.invalid.join() === "3,4", "identify invalid lines rather than silently discarding them");
check(parseDomains("").domains.length === 0, "empty list is valid");
for (const key of ["mode", "allowlist", "customBlocks", "customTerms", "inspectText", "textSensitivity"]) {
  check(settingsAccess(solo, { [key]: [] }).ok, `${key}: standalone editable`);
  check(!settingsAccess(managed, { [key]: [] }).ok, `${key}: app authoritative even without a lock`);
}
check(!settingsAccess(solo, { lockUntil: 100 }).ok, "browser cannot invent a native lock");
check(settingsAccess(managed, { textAllow: [] }).ok, "local reports remain browser-owned");
check(connectionStatus(solo).managed === false, "standalone has editable settings");
check(connectionStatus(managed, 2000).title === "Managed by the Hisn app", "fresh connection");
check(connectionStatus(managed, 1000000).managed === true, "disconnect preserves authority");
check(restrictionsActive({ ...solo, failClosed: true }), "failure safeguards persist after displayed deadline");
check(connectionStatus({ok:false}).managed, "worker errors never imply browser-only ownership");
check(connectionFeedback({ok:true}, managed, 2000).includes("Settings synced"), "sync success requires fresh app state");
check(connectionFeedback({ok:true}, managed, 1000000).includes("remain app-managed"), "stale readback never claims sync success");
check(connectionFeedback({ok:false}, solo).includes("No connection"), "standalone reconnect explains optional app connection");
check(connectionFeedback({ok:false}, managed).includes("remain app-managed"), "managed reconnect preserves ownership guidance");
check(connectionFeedback({ok:true}, {ok:false}).includes("could not be verified"), "missing readback is not success");
check(connectionFeedback({ok:false,reason:"unavailable"}, solo).includes("could not be verified"), "worker outage is not missing-app evidence");
print(`${checks}/${checks} settings checks passed`);
