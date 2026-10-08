import { decide, policyRules, tabsToBlock, PRIORITY_HAND } from "../lib/policy.js";
import { WEB_PROTECTION_RULES, SAFESEARCH_TEMPLATES } from "../lib/web-protection.js";
const source = JSON.parse(readFile("../../blocklist/web_protection.json"));
let checks = 0;
function check(ok, label) { checks++; if (!ok) throw new Error(label); }
const base = { mode: "off", allowlist: [], customBlocks: [] };
for (const host of source.blockedSearchHosts.concat(source.blockedViewerDomains)) {
  for (const mode of ["off", "blocklist", "strict"]) {
    const state = { ...base, mode, allowlist: [host, "com", "ru"] };
    for (const resourceType of ["main_frame", "sub_frame", "image", "xmlhttprequest", "media", "websocket"]) {
      check(decide(state, host, { resourceType }) === "block", `${mode}: ${resourceType} ${host}`);
    }
  }
  check(decide(base, host.toUpperCase() + ".") === "block", `case/trailing dot ${host}`);
  check(decide(base, host + ".example.org") === "allow", `lookalike ${host}`);
}
for (const host of source.blockedViewerDomains) {
  check(decide({ ...base, allowlist: ["api." + host] }, "api." + host) === "block", `viewer subdomain ${host}`);
}
check(decide(base, "mail.yandex.ru") === "allow", "unrelated Yandex mail not blocked by search policy");
check(decide(base, "search.brave.com") === "allow", "Brave remains available");
check(tabsToBlock([{ id: 1, url: "https://yandex.com/search" }], base)[0]?.reason === "domain",
      "already-open search tab uses domain block reason");
check(JSON.stringify(WEB_PROTECTION_RULES) === JSON.stringify(JSON.parse(readFile("../rules/web_protection.json"))),
      "reference evaluator uses exactly the installed static rules");
check(JSON.stringify(SAFESEARCH_TEMPLATES) === JSON.stringify(JSON.parse(readFile("../rules/safesearch.json"))),
      "allowlisted search rules use the same templates as baseline");
const covers = (domains, host) => domains.some((d) => host === d || host.endsWith("." + d));
function matches(rule, host, path) {
  const c = rule.condition;
  return (!c.requestDomains || covers(c.requestDomains, host))
    && (!c.excludedRequestDomains || !covers(c.excludedRequestDomains, host))
    && (!c.regexFilter || new RegExp(c.regexFilter).test(`https://${host}${path}`));
}
for (const mode of ["off", "blocklist", "strict"]) {
  for (const [allowed, host, path, key, value] of [
    ["google.com", "www.google.com", "/search?q=test&safe=off", "safe", "active"],
    ["bing.com", "www.bing.com", "/images/search?q=test&adlt=off", "adlt", "strict"],
    ["duckduckgo.com", "duckduckgo.com", "/?q=test&kp=-2", "kp", "1"],
    ["brave.com", "search.brave.com", "/images?q=test&safesearch=off", "safesearch", "strict"],
    ["yahoo.com", "search.yahoo.com", "/search?p=test&vm=i", "vm", "r"],
  ]) {
    const state = { ...base, mode, allowlist: [allowed] };
    const matching = policyRules(state).filter((r) => matches(r, host, path));
    const allow = matching.find((r) => r.action.type === "allow");
    const safe = matching.find((r) => r.action.redirect?.transform);
    check(safe && safe.priority > allow.priority, `${mode}: SafeSearch outranks allowance for ${host}`);
    check(safe.action.redirect.transform.queryTransform.addOrReplaceParams.some((p) => p.key === key && p.value === value),
          `${mode}: strict parameter for ${host}`);
    const blocked = { ...state, customBlocks: [host] };
    check(!policyRules(blocked).some((r) => r.action.redirect?.transform && matches(r, host, path)),
          `${mode}: custom denial excludes SafeSearch for ${host}`);
    check(decide(blocked, host) === "block", `${mode}: custom denial wins for ${host}`);
  }
}
const yt = policyRules({ ...base, allowlist: ["youtube.com", "googleapis.com"] })
  .filter((r) => r.action.type === "modifyHeaders");
check(yt.some((r) => matches(r, "www.youtube.com", "/results") && r.priority > PRIORITY_HAND),
      "allowlisted YouTube still gets restricted header");
check(yt.some((r) => matches(r, "youtubei.googleapis.com", "/youtubei/v1/search")), "YouTube API intersection");
check(!yt.some((r) => matches(r, "maps.googleapis.com", "/")), "never set YouTube headers on unrelated APIs");
const moreSpecific = policyRules({ ...base, allowlist: ["search.brave.com"], customBlocks: ["brave.com"] });
check(moreSpecific.some((r) => r.action.redirect?.transform && matches(r, "search.brave.com", "/search?q=test")),
      "more-specific allowance preserves SafeSearch above a parent custom block");
print(`${checks}/${checks} web protection checks passed`);
