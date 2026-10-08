async function run() {
  const { WEB_PROTECTION_RULES, SAFESEARCH_TEMPLATES } = await import("./lib/web-protection.js");
  for (const rule of [...WEB_PROTECTION_RULES, ...SAFESEARCH_TEMPLATES]) {
    if (!rule.condition.regexFilter) continue;
    const result = await chrome.declarativeNetRequest.isRegexSupported({
      regex: rule.condition.regexFilter, isCaseSensitive: false,
    });
    if (!result.isSupported) throw new Error("unsupported policy regex: " + JSON.stringify(result));
  }
  const response = await chrome.runtime.sendMessage({ type: "update", patch: {
    inspectText: false,
    allowlist: ["google.com", "bing.com", "duckduckgo.com", "youtube.com", "search.brave.com",
      "yandex.com", "yandex.ru", "ya.ru", "yandex.com.tr", "nitter.net", "xcancel.com",
      "sotwe.com", "twstalker.com"],
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
