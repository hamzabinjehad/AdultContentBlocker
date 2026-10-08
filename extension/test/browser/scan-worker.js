import "./background.js";
let opened = false;
chrome.tabs.onUpdated.addListener(async (_, change, tab) => {
  if (opened || change.status !== "complete" || tab.url !== "https://policy.hisn.test/") return;
  opened = true;
  // Allow the initial clean verdict to land before editing through a trusted page.
  setTimeout(() => chrome.tabs.create({ url: chrome.runtime.getURL("scan-setup.html") }), 1500);
});
