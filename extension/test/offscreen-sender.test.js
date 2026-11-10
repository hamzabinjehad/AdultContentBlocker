/** The offscreen visual engine must accept the real MV3 service-worker sender. */
import { fromBackgroundWorker } from "../visual/offscreen-sender.js";

let checks = 0, failures = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

const id = "abcdefghijklmnopabcdefghijklmnop";
const background = `chrome-extension://${id}/background.js`;

check(fromBackgroundWorker({ id }, id, background),
  "Chrome MV3 service-worker sender (sender.url undefined) is the worker");
check(fromBackgroundWorker({ id, url: background }, id, background),
  "Firefox-style worker script URL is the worker");
check(fromBackgroundWorker({ id, origin: `chrome-extension://${id}` }, id, background),
  "an origin-only worker sender is the worker");
check(!fromBackgroundWorker({ id, url: `chrome-extension://${id}/popup.html` }, id, background),
  "the popup is not the worker");
check(!fromBackgroundWorker({ id, tab: { id: 7 }, url: "https://example.com/" }, id, background),
  "a content script is not the worker");
check(!fromBackgroundWorker({ id: "other-extension" }, id, background),
  "another extension is not the worker");
check(!fromBackgroundWorker(null, id, background), "a missing sender is rejected");
check(!fromBackgroundWorker({ id }, "", background), "a missing extension id is rejected");

print(`${checks - failures}/${checks} offscreen sender checks passed`);
if (failures) throw new Error(`${failures} offscreen sender checks failed`);
