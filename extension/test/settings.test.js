import { parseDomains, settingsAccess, connectionStatus, connectionFeedback, repairFeedback, restrictionsActive, settingsError, quickBlockTarget } from "../lib/settings.js";

let checks = 0;
function check(ok, label) { checks++; if (!ok) throw new Error(label); }
const solo = { appPresent: false, lockUntil: 0, failClosed: false };
const managed = { ...solo, appPresent: true, lastHeartbeat: 1000, desiredRevision:1,appliedRevision:1,
  enforcement:{version:1,status:"applied",desiredRevision:1,appliedRevision:1,
    layers:{domains:"applied",search:"applied",text:"applied",visual:"off"}} };
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
check(connectionFeedback({ok:false,connected:true,reason:"enforcement-failed"},
  {...managed,enforcement:{status:"failed"}},2000).includes("App connected"), "browser enforcement failure retains connection evidence");
check(settingsError({reason:"enforcement-failed"}).includes("stored"), "failed application is not reported as an unsaved setting");
check(connectionFeedback({ok:true,connected:true},{...managed,enforcement:undefined},2000).includes("could not be confirmed"),
      "a positive native reply cannot certify browser application without a receipt");
check(!connectionStatus({...managed,bridgeConnection:{status:"unavailable"}},2000).fresh,
      "an explicit native failure is visible even before the trusted heartbeat grace period ends");
check(connectionFeedback({ok:false,reason:"authority-unreachable"},managed,2000).includes("lock authority"),
      "authority outage has a distinct repair explanation without transferring ownership");
check(repairFeedback({ok:true}, managed).includes("configuration verified"), "successful repair needs verified configuration readback, not native connectivity");
check(repairFeedback({ok:true}, {...managed, appPresent:false}).includes("configuration verified"), "standalone browser repair does not require the app");
check(repairFeedback({ok:false,reason:"unavailable"}, managed).includes("did not confirm"), "timeout cannot use an old applied receipt to claim repair succeeded");
check(repairFeedback({ok:true}, {...managed,enforcement:undefined}).includes("could not be fully verified"), "positive command cannot replace a missing receipt");
check(repairFeedback({ok:true}, {ok:false}).includes("did not confirm"), "missing post-repair readback is unverified");
check(repairFeedback({ok:false,reason:"enforcement-failed"}, managed).includes("unchanged"), "failed repair explains that saved choices and lock were not reset");
const qb = (url, state = solo) => quickBlockTarget(url, state);
check(qb("https://www.example.com/a?b").host === "example.com" && qb("https://www.example.com/").ok, "quick block canonicalizes the tab host");
check(qb("HTTPS://User@Sub.Example.com:8443?q#f").host === "sub.example.com", "quick block strips userinfo, port, query and fragment");
check(qb("https://xn--mgbh0fb.xn--wgbh1c/").host === "xn--mgbh0fb.xn--wgbh1c", "quick block keeps punycode hosts");
check(qb("https://new.example").customBlocks.join() === "new.example", "quick block adds the host to an empty list");
check(qb("https://a.example", { ...solo, customBlocks: ["b.example"] }).customBlocks.join() === "b.example,a.example", "quick block keeps existing blocks");
check(qb("https://m.example.com", { ...solo, customBlocks: ["example.com"] }).reason === "blocked", "a parent block already covers a subdomain");
check(qb("https://notexample.com", { ...solo, customBlocks: ["example.com"] }).ok, "suffix match respects label boundaries");
for (const url of ["chrome://extensions", "chrome-extension://x/popup.html", "file:///tmp/a.html", "http://192.168.1.1/", "about:blank", "", undefined]) {
  check(qb(url).reason === "page", `quick block is not offered for ${String(url)}`);
}
check(qb("https://example.com", managed).reason === "app-managed", "app-owned lists are not edited from the popup");
check(qb("https://example.com", { ok: false }).reason === "unavailable", "no worker state, no quick block");
check(qb("https://example.com", { ...solo, lockUntil: Date.now() + 1e6 }).ok, "adding a block stays available during a browser lock");
print(`${checks}/${checks} settings checks passed`);
