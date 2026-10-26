async function run() {
  const { WEB_PROTECTION_RULES, SAFESEARCH_TEMPLATES } = await import("./lib/web-protection.js");
  // Every static ruleset that carries a regex must compile in Chrome, not just
  // the generated policy module. A single unsupported expression silently
  // disables its whole ruleset, which looks exactly like a rule that did not
  // match. The keyword, evasion and encoded-query sets are checked here too.
  const staticRules = [];
  for (const path of ["rules/dnr_keyword_rules.json", "rules/evasion.json",
                      "rules/encoded_queries.json"]) {
    staticRules.push(...await (await fetch(chrome.runtime.getURL(path))).json());
  }
  for (const rule of [...WEB_PROTECTION_RULES, ...SAFESEARCH_TEMPLATES, ...staticRules]) {
    if (!rule.condition.regexFilter) continue;
    const result = await chrome.declarativeNetRequest.isRegexSupported({
      regex: rule.condition.regexFilter, isCaseSensitive: false,
    });
    if (!result.isSupported) throw new Error("unsupported regex in a static ruleset: "
      + JSON.stringify({ condition: rule.condition, result }));
  }
  const response = await chrome.runtime.sendMessage({ type: "update", patch: {
    inspectText: false,
    allowlist: ["google.com", "bing.com", "duckduckgo.com", "youtube.com", "search.brave.com",
      "yandex.com", "yandex.ru", "ya.ru", "yandex.com.tr", "nitter.net", "xcancel.com",
      "sotwe.com", "twstalker.com", "twitterviewer.net", "twitee.co", "xxcancel.com",
      "twitter-viewer.com"],
    customBlocks: ["cn.bing.com", "safe.search.brave.com"],
  } });
  if (!response.ok) throw new Error(JSON.stringify(response));
  const frame = document.createElement("iframe");
  frame.src = "https://harness.hisn.test/";
  document.body.replaceChildren(frame);
}
run().catch((error) => {
  location.href = "https://setup-error.hisn.test/?error=" + encodeURIComponent(String(error));
});
