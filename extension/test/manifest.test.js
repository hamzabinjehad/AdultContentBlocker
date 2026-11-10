/**
 * The manifest is part of the program, not decoration.
 *
 * Chromium exposes an API namespace only when its permission is declared. The
 * opt-in visual layer opened an offscreen document for months while the
 * manifest no longer declared `"offscreen"`; `chrome.offscreen` was undefined,
 * `supported()` returned false, and the layer reported "unavailable" while the
 * visual gate could keep media withheld. No test compared the two lists.
 *
 * This suite does that comparison, plus the small set of manifest facts the
 * worker and the store record depend on. It is deliberately source-based:
 * `chrome.*` is grepped from the shipped files rather than inferred from a
 * build graph, so a namespace added in a new helper is caught even if nobody
 * remembers this file.
 *
 *     extension/test/run.sh
 */

let checks = 0, failures = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

function read(path) {
  try { return readFile(path); } catch (error) {
    failures++; checks++;
    print(`  FAIL  cannot read ${path}: ${error?.message ?? error}`);
    return "";
  }
}

const manifest = JSON.parse(read("../manifest.json"));
const permissions = new Set(manifest.permissions || []);
const sources = [
  "../background.js", "../popup.js", "../options.js", "../blocked.js",
  "../lib/generation.js", "../lib/i18n.js", "../lib/icons.js", "../lib/language.js",
  "../lib/messages.js", "../lib/normalize.js", "../lib/pause.js", "../lib/policy.js",
  "../lib/score.js", "../lib/serial.js", "../lib/settings.js", "../lib/site-editor.js",
  "../lib/status.js", "../lib/toolbar-status.js", "../lib/transfer.js", "../lib/verify.js",
  "../lib/visual-status.js", "../lib/web-protection.js",
  "../worker/inspection.js", "../worker/lists.js", "../worker/native.js", "../worker/rules.js",
  "../worker/scanners.js", "../worker/state.js", "../worker/tabs.js", "../worker/updates.js",
  "../content/feed.js", "../content/scan.js", "../content/short-video.js", "../content/visual.js",
  "../content/visual-effects.js",
  "../visual/broker.js", "../visual/capture.js", "../visual/decisions.js", "../visual/engine.js",
  "../visual/offscreen.js", "../visual/queue.js", "../visual/verdict-cache.js",
];

// Read every shipped source once. A missing file is a failure here because the
// list above is the test's own claim about what the extension runs.
const source = new Map(sources.map((path) => [path, read(path)]));
const allSource = [...source.values()].join("\n");

// Namespaces whose availability depends on a declared permission. `tabs`,
// `action`, `i18n`, `dom`, `extension` and `runtime` (except native messaging)
// are available without one, so they are not listed.
const REQUIRED = [
  ["offscreen", /(?:chrome|api\(\))\??\s*\.\s*offscreen\b/],
  ["storage", /(?:chrome|api\(\))\??\s*\.\s*storage\b/],
  ["scripting", /(?:chrome|api\(\))\??\s*\.\s*scripting\b/],
  ["alarms", /(?:chrome|api\(\))\??\s*\.\s*alarms\b/],
  ["declarativeNetRequest", /(?:chrome|api\(\))\??\s*\.\s*declarativeNetRequest\b/],
  ["nativeMessaging", /\.sendNativeMessage\b/],
];

check(manifest.manifest_version === 3, "manifest is Manifest V3");
check(typeof manifest.key === "string" && manifest.key.length > 0,
      "the unpacked copy still pins its development extension ID");
check(typeof manifest.minimum_chrome_version === "string",
      "minimum_chrome_version is declared");
check(manifest.background?.type === "module" && typeof manifest.background?.service_worker === "string",
      "the background is a module service worker");
check(typeof manifest.action?.default_popup === "string" && typeof manifest.options_page === "string",
      "the popup and options page are declared");
check(Array.isArray(manifest.host_permissions) && manifest.host_permissions.includes("<all_urls>"),
      "host access is declared for the scanner and the network rules");
check(manifest.incognito === "spanning",
      "incognito remains spanning so a running lock carries in");

for (const [permission, pattern] of REQUIRED) {
  if (!pattern.test(allSource)) continue;
  check(permissions.has(permission),
        `a source file uses chrome.${permission}, so "${permission}" must be in permissions`);
}
check(permissions.has("offscreen"),
      "the visual broker needs the offscreen permission again (regression from 1.2.1)");

// The store record and the privacy policy make these claims. A new sensitive
// permission must be a deliberate, documented decision, not a quiet addition.
for (const forbidden of ["tabs", "history", "cookies", "webRequest", "management", "downloads"]) {
  check(!permissions.has(forbidden), `permissions do not include "${forbidden}"`);
}
check(new Set(manifest.permissions || []).size === (manifest.permissions || []).length,
      "no permission is declared twice");

const war = manifest.web_accessible_resources || [];
check(war.length === 1 && Array.isArray(war[0].resources)
      && war[0].resources.length === 1 && war[0].resources[0] === "blocked.html",
      "only blocked.html is web-accessible");
check(war[0]?.matches?.includes("<all_urls>"),
      "the block page is reachable after a redirect from any listed host");

const resources = manifest.declarative_net_request?.rule_resources || [];
const ruleIds = resources.map((rule) => rule.id);
check(resources.length >= 10, "the shipped rulesets are declared");
check(new Set(ruleIds).size === ruleIds.length, "ruleset ids are unique");
for (const rule of resources) {
  check(typeof rule.path === "string" && rule.path.startsWith("rules/") && rule.path.endsWith(".json"),
        `ruleset "${rule.id}" points at a packaged rule file`);
}

// The version the worker reports and the version the release record names must
// be one number. This is the drift that left the store record on 1.5.2 while
// the manifest had moved on.
const versions = JSON.parse(read("../../versions.json"));
const extensionEntry = (versions.platforms || []).find((platform) => platform.id === "extension");
check(!!extensionEntry, "versions.json describes the extension");
check(extensionEntry?.version === manifest.version,
      `versions.json says ${extensionEntry?.version}; the manifest says ${manifest.version}`);

// The IDs the store record tells the owner to wire are the ones the app reads.
check(manifest.name === "Hisn Protection" && manifest.short_name === "Hisn",
      "the store-facing name and short name are the reviewed ones");

if (failures) print(`  ${failures} of ${checks} manifest checks FAILED`);
else print(`  ${checks}/${checks} manifest checks passed`);
if (failures) throw new Error("manifest contract failed");
