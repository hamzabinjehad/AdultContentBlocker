import "./background.js";
let opening = false;
chrome.runtime.onInstalled.addListener(async () => {
  if (opening) return;
  opening = true;
  if ((await chrome.storage.session.get("dnrStarted")).dnrStarted) return;
  await chrome.storage.session.set({ dnrStarted: true });
  await chrome.tabs.create({ url: chrome.runtime.getURL("dnr-setup.html") });
});
