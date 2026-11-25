// The site-access answer the heartbeat carries: true, false, or no answer.
let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL  ${label}`); } }
globalThis.setTimeout = globalThis.setTimeout ?? (() => 0);
globalThis.clearTimeout = globalThis.clearTimeout ?? (() => {});
globalThis.chrome = { storage: { local: { get: async () => ({}), set: async () => {} }, session: { get: async () => ({}), set: async () => {} } },
  runtime: {}, permissions: {} };
const { siteAccess } = await import("../worker/native.js");

let asked = null;
chrome.permissions.contains = (query, callback) => { asked = query; callback(true); };
check(await siteAccess() === true && JSON.stringify(asked) === '{"origins":["<all_urls>"]}', "asks the browser about every site and reports full access");
chrome.permissions.contains = (_query, callback) => callback(false);
check(await siteAccess() === false, "on click or specific sites is reported as limited");
chrome.permissions.contains = async () => false;
check(await siteAccess() === false, "a promise-style answer is read too");
chrome.permissions.contains = () => { throw new Error("no"); };
check(await siteAccess() === undefined, "an error is no answer, never limited");
delete chrome.permissions.contains;
check(await siteAccess() === undefined, "a browser without the question gives no answer");

print(`  ${checks - failures}/${checks} site access checks passed`);
if (failures) throw new Error(`${failures} site access check(s) failed`);
