// SafeSearch rules (rules/safesearch.json): which URLs they rewrite, and that
// they can never outrank a block.
//
// JavaScript's RegExp stands in for Chrome's RE2 here; the patterns use only
// the syntax the two share. `test/browser/safesearch.sh` checks the same rules
// in a real browser, redirects and all.

const rules = JSON.parse(readFile("../rules/safesearch.json"));
const manifest = JSON.parse(readFile("../manifest.json"));
let checks = 0;
function check(ok, label) { checks++; if (!ok) throw new Error(label); }

const byParam = (key) => rules.find((r) =>
  r.action.redirect?.transform?.queryTransform?.addOrReplaceParams?.[0]?.key === key);

function rewrites(rule, url) {
  return new RegExp(rule.condition.regexFilter).test(url);
}

check(new Set(rules.map((r) => r.id)).size === rules.length, "rule ids are unique");
check(rules.every((r) => r.priority === 1),
      "every SafeSearch rule sits at priority 1, where a block of the same host wins");
check(manifest.declarative_net_request.rule_resources
        .some((r) => r.id === "safesearch" && r.enabled && r.path === "rules/safesearch.json"),
      "the ruleset is declared and enabled from install");

const google = byParam("safe");
for (const url of ["https://www.google.com/search?q=x", "https://google.com/search?q=x",
                   "https://www.google.co.uk/search?q=x", "https://www.google.com.sa/search?q=x",
                   "https://www.google.ae/webhp?q=x", "https://www.google.com/imgres?imgurl=x"]) {
  check(rewrites(google, url), `Google: ${url}`);
}
for (const url of ["https://mail.google.com/search", "https://www.google.com/maps",
                   "https://www.google.com.evil.example/search?q=x",
                   "https://notgoogle.com/search?q=x", "https://www.google.com/searchfoo"]) {
  check(!rewrites(google, url), `Google must not rewrite ${url}`);
}
check(google.action.redirect.transform.queryTransform.addOrReplaceParams[0].value === "active",
      "Google gets safe=active");

const bing = byParam("adlt");
check(rewrites(bing, "https://www.bing.com/search?q=x"), "Bing web");
check(rewrites(bing, "https://www.bing.com/images/search?q=x"), "Bing images");
check(!rewrites(bing, "https://www.bing.com/maps"), "Bing maps untouched");

const ddg = byParam("kp");
check(rewrites(ddg, "https://duckduckgo.com/?q=x"), "DuckDuckGo");
check(rewrites(ddg, "https://html.duckduckgo.com/html/?q=x"), "DuckDuckGo html");
check(!rewrites(ddg, "https://duckduckgo.com/about"), "DuckDuckGo non-search untouched");

check(rewrites(byParam("vm"), "https://search.yahoo.com/search?p=x"), "Yahoo");
check(rewrites(byParam("safesearch"), "https://search.brave.com/search?q=x"), "Brave Search");

const yt = rules.find((r) => r.action.type === "modifyHeaders");
check(yt.action.requestHeaders[0].header === "YouTube-Restrict"
      && yt.action.requestHeaders[0].value === "Strict", "YouTube strict restricted mode");
check(yt.condition.requestDomains.includes("youtube.com")
      && yt.condition.requestDomains.includes("youtubei.googleapis.com"),
      "YouTube pages and its API both carry the header");

print(`${checks}/${checks} SafeSearch checks passed`);
