/**
 * The adult-TLD guard: every name under a sponsored adult TLD, not only the
 * ones a list has already seen.
 *
 * `rules/adult_tlds.json` is a static ruleset at the same priority as the
 * published domain list. It is what a domain registered this morning meets
 * before any list build could carry it. `.cam` is deliberately absent: unlike
 * `.xxx`, `.porn`, `.sex` and `.adult`, it is generic and has ordinary uses.
 *
 * The regexes are Chrome's, not a model of them; `decide` is asserted against
 * the same cases so the two clients cannot drift.
 */

import { ADULT_TLDS, isAdultTld, decide, tabsToBlock, PRIORITY_LIST } from "../lib/policy.js";

let failures = 0, checks = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}
function eq(actual, expected, label) {
  check(JSON.stringify(actual) === JSON.stringify(expected),
        `${label}\n        got ${JSON.stringify(actual)}\n        want ${JSON.stringify(expected)}`);
}

const manifest = JSON.parse(readFile("../manifest.json"));
const resource = manifest.declarative_net_request.rule_resources.find((r) => r.id === "adult_tlds");
check(Boolean(resource), "the adult_tlds ruleset is in the manifest");
check(resource?.path === "rules/adult_tlds.json", "the ruleset points at its file");
check(resource?.enabled === true, "the adult-TLD guard cannot be switched off in the manifest");

const rules = JSON.parse(readFile("../rules/adult_tlds.json"));
eq(rules.length, 2, "one document rule and one embedded rule");
check(rules.every((r) => r.priority === PRIORITY_LIST),
      "the guard sits at the same priority as the published list, so a hand allowance still wins");
const document = rules.find((r) => (r.condition.resourceTypes || []).includes("main_frame"));
const embedded = rules.find((r) => !(r.condition.resourceTypes || []).includes("main_frame"));
check(document?.action.type === "redirect"
      && document.action.redirect?.extensionPath === "/blocked.html?reason=domain",
      "a blocked document lands on the block page with the site-list reason");
check(embedded?.action.type === "block", "embedded traffic from a guarded TLD is dropped, not redirected");
for (const type of ["script", "image", "media", "xmlhttprequest", "stylesheet", "font",
                    "object", "websocket", "ping", "csp_report", "webtransport", "webbundle", "other"]) {
  check((embedded?.condition.resourceTypes || []).includes(type),
        `embedded rule covers ${type}`);
}
check(document?.condition.regexFilter === embedded?.condition.regexFilter,
      "both rules judge exactly the same authority");
check(document?.condition.isUrlFilterCaseSensitive === false
      && embedded?.condition.isUrlFilterCaseSensitive === false,
      "matching is case-insensitive");

// Every TLD the policy names is in the expression, and nothing else was added.
const filter = document.condition.regexFilter;
for (const tld of ADULT_TLDS) {
  const alternatives = /\(\?:([a-z|]+)\)/.exec(filter)?.[1]?.split("|") ?? [];
  check(alternatives.includes(tld), `${tld} is one of the guarded alternatives`);
}
check(!filter.includes("cam"), "cam is not a guard");
check(!filter.includes("webcam"), "the expression does not smuggle in a generic TLD through a term");

const regex = new RegExp(filter, "i");
const cases = [
  ["https://new.porn/", true, "a name registered after the last list build"],
  ["http://foo.xxx", true, "http and no trailing slash"],
  ["https://xxx", true, "the TLD itself, at the end of the URL"],
  ["https://a.b.sex:8443/watch", true, "subdomains and a port"],
  ["https://user:pass@adult.example.adult/path", true, "userinfo is skipped and the final label decides"],
  ["HTTPS://NEW.PORN/", true, "the URL is case-insensitive"],
  ["https://new.porn./", true, "an absolute FQDN's trailing dot does not bypass the guard"],
  ["https://notporn.com/", false, "the TLD is the last label, not a substring"],
  ["https://porn.example.com/", false, "an adult word in a label is the keyword layer's job, not this one"],
  ["https://example.com/new.porn", false, "a path is not an authority"],
  ["https://cam.example.com/", false, "cam is not guarded"],
  ["https://foo.xxx.example.com/", false, "a guarded TLD in a middle label does not match"],
  ["https://xxx.evil.com/", false, "a delegated subdomain of a guarded name is not that name"],
  ["https://notxxx.com/", false, "a suffix collision"],
];
for (const [url, blocked, why] of cases) {
  // The expression is matched against the full URL, as Chrome does.
  check(regex.test(url) === blocked, `${why}: ${url} -> ${blocked}`);
}

// The pure evaluator, the open-tab sweep and the shared policy fixture.
const base = { mode: "blocklist", lockUntil: 0, allowlist: [], customBlocks: [],
               failClosed: false, appPresent: false, lastHeartbeat: 0 };
eq(decide(base, "new.porn", { listed: false }), "block",
   "decide() models the guard even when the list is not passed");
eq(decide(base, "cdn.new.xxx", { listed: false }), "block", "subdomains too");
eq(decide(base, "example.cam", { listed: false }), "allow", "cam stays reachable");
eq(decide({ ...base, allowlist: ["new.porn"] }, "new.porn"), "allow",
   "a hand allowance outranks the guard, as it outranks the list");
eq(decide(base, "new.porn"), "block", "the guard is on in blocklist mode");
eq(decide({ ...base, mode: "strict", allowlist: ["new.porn"] }, "new.porn"), "allow",
   "an allowed name stays reachable in strict mode");
eq(decide(base, "example.com"), "allow", "an ordinary host is untouched");

eq(tabsToBlock([{ id: 1, url: "https://new.porn/watch" }], base),
   [{ id: 1, reason: "domain" }], "an open guarded tab is replaced with the block page");
eq(tabsToBlock([{ id: 1, url: "https://new.porn/watch" }], { ...base, allowlist: ["new.porn"] }),
   [], "an allowed guarded tab is not touched");

check(isAdultTld("new.porn"), "isAdultTld accepts the raw host");
check(isAdultTld("NEW.PORN."), "case and trailing dot");
check(isAdultTld("porn"), "a bare guarded label");
check(!isAdultTld("porn.example.com"), "not the last label");
check(!isAdultTld("cam.example.com"), "cam is not guarded");
check(!isAdultTld(""), "empty is not a host");
check(!isAdultTld(null), "null is not a host");

// The shared contract file names the same behaviour; the two suites assert it.
const fixture = JSON.parse(readFile("../../blocklist/terms/policy_cases.json"));
const named = fixture.cases.filter((c) => isAdultTld(c.host));
check(named.length >= 3, "the shared policy fixture carries adult-TLD cases");
for (const c of named) {
  const state = { ...base, mode: c.mode, allowlist: c.allowlist, customBlocks: c.customBlocks };
  const got = decide(state, c.host, { listed: c.listed, resourceType: c.resourceType ?? "main_frame" });
  eq(got, c.expect, `policy fixture: ${c.host} — ${c.why}`);
}

print(`  ${checks - failures}/${checks} adult-TLD checks passed`);
if (failures) throw new Error(`${failures} adult-TLD checks failed`);
