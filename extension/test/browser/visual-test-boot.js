// Test-only tail appended to the staged background.js. It keeps the worker's
// real URL (chrome-extension://<id>/background.js) so the offscreen sender gate
// is exercised exactly as it is in production.
chrome.tabs.create({ url: chrome.runtime.getURL("visual-test.html") });
