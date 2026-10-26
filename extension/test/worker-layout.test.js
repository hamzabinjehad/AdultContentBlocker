/**
 * The worker's shape, as a regression test.
 *
 * `background.js` had grown into a 1,286-line file that did six unrelated
 * things: rule writes, native IPC, list downloads, page scoring, settings
 * validation and the message table. Every one of those is now a module under
 * `worker/`, and the entry point is wiring plus the one transaction lock.
 *
 * This test is not a style opinion. The split is what lets the other suites
 * reach a concern directly (and what made the evasion ruleset possible to add
 * without editing the service worker), so a future change that quietly moves
 * rule building or native IPC back into the entry point should fail here and be
 * a deliberate decision rather than a drift.
 */

let checks = 0, failures = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

const source = (path) => readFile(`../${path}`);
const lines = (text) => text.split("\n").length;

const background = source("background.js");
const workerFiles = ["state", "scanners", "tabs", "rules", "native", "lists",
                     "updates", "inspection"];

// ── the entry point is wiring ─────────────────────────────────────────────
check(lines(background) < 320,
      `background.js stays an entry point (${lines(background)} lines)`);
for (const name of workerFiles) {
  const text = source(`worker/${name}.js`);
  check(text.length > 0, `worker/${name}.js exists`);
  check(lines(text) < 320, `worker/${name}.js is focused (${lines(text)} lines)`);
  check(!/from\s+["'][^"']*background\.js["']/.test(text),
        `worker/${name}.js does not import the entry point (no cycle)`);
}

// ── rules, IPC and downloads live in their modules ────────────────────────
const responsibilities = {
  "chrome.declarativeNetRequest.updateDynamicRules": "worker/rules.js",
  "chrome.runtime.sendNativeMessage": "worker/native.js",
  "chrome.scripting.registerContentScripts": "worker/scanners.js",
  "const LIST_BASE": "worker/lists.js",
};
for (const [needle, owner] of Object.entries(responsibilities)) {
  check(source(owner).includes(needle), `${owner} owns ${needle}`);
  check(!background.includes(needle), `background.js does not inline ${needle}`);
}

// ── the entry point still exposes the surface its callers import ──────────
for (const name of ["applyRules", "guardedUpdate", "DEFAULT_STATE", "resolveDisputed",
                    "ensureAlarms", "restoreWorkerProtection", "booted", "ALARMS",
                    "senderAllowed", "ensureBlocked", "syncVisualScanning", "toolbarStatus"]) {
  check(new RegExp(`\\b${name}\\b`).test(background), `background.js still exports ${name}`);
}

// ── pure modules stay pure ────────────────────────────────────────────────
// `lib/normalize.js` promises no chrome and no DOM; the test harness imports it
// outside an extension, which is the property that keeps the normalisation
// contract testable in three languages.
const normalize = source("lib/normalize.js");
check(!/globalThis\.|window\.|document\./.test(normalize),
      "lib/normalize.js remains free of browser globals");

print(`  ${checks - failures}/${checks} worker-layout checks passed`);
if (failures) throw new Error(`${failures} check(s) failed`);
