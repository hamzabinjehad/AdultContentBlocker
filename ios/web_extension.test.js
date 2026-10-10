// The Python package test places this harness beside the staged resources.
import { checkedRequest, messageHandler } from "./background.js";
import { buildIndex } from "./lib/score.js";

// JavaScriptCore's bare shell has no browser URL class; Node and real browsers
// use their native implementation. This shim covers only the test URL fixtures.
if (!globalThis.URL) globalThis.URL = class {
  constructor(value) {
    const match = /^([a-z]+:)\/\/([^/?#]+)(?:[/?#]|$)/i.exec(value);
    if (!match) throw new Error("Invalid fixture URL");
    this.protocol = match[1].toLowerCase();
    this.hostname = match[2].toLowerCase();
  }
};
const say = globalThis.print ?? ((value) => console.log(value));
let checks = 0;
function check(condition, label) {
  if (!condition) throw new Error(label);
  checks++;
}
const sender = { id: "safari-fixture", tab: { id: 1 }, frameId: 0, url: "https://x.com/home" };
const item = { type: "scoreFeedItem", zones: { body: "porn porn porn", alt: "" } };
check(!!checkedRequest(item, sender, sender.id), "Top-level X item permitted");
for (const altered of [{ ...sender, id: "foreign" }, { ...sender, frameId: 1 },
  { ...sender, tab: undefined }, { ...sender, tab: { id: -1 } },
  { ...sender, url: "https://x.com.evil.example/home" }, { ...sender, url: "https://evilx.com/home" },
  { ...sender, url: "file:///x.com" }, { ...sender, url: "not a URL" }]) {
  check(checkedRequest(item, altered, sender.id) === null, "Invalid/unrelated sender rejected");
}
for (const zones of [null, [], { body: 7 }, { body: "a".repeat(20_002) },
  { body: "test", title: "not item-scoped" }]) {
  check(checkedRequest({ ...item, zones }, sender, sender.id) === null, "Malformed item zones rejected");
}
check(!!checkedRequest({ type: "scoreText", zones: { body: "a".repeat(200_001) } }, sender, sender.id), "Bounded page accepted");
check(checkedRequest({ type: "scoreText", zones: { body: "a".repeat(200_002) } }, sender, sender.id) === null, "Oversized page rejected");
check(checkedRequest({ type: "saveSettings", zones: {} }, sender, sender.id) === null, "Mutation message rejected");

async function main() {
  const index = buildIndex({ terms: [{ t: "porn", w: 4 }], negatives: [], exempt_domains: ["wikipedia.org"] });
  const updates = [], replacements = [], removed = [];
  let currentURL = sender.url;
  const api = { runtime: { id: sender.id, getURL: (name) => "safari-extension://fixture/" + name },
    tabs: { get: async () => ({ url: currentURL, windowId: 3, index: 2, active: false }),
      update: async (id, options) => updates.push({ id, options }),
      create: async (options) => { replacements.push(options); return { id: 2 }; },
      remove: async (id) => removed.push(id) } };
  async function score(message, source = sender, load = async () => index) {
    return new Promise((resolve, reject) => {
      if (!messageHandler(api, load)(message, source, resolve)) reject(new Error("Message rejected"));
    });
  }
  const blocked = await score(item);
  check(blocked.block === true && blocked.enabled === true, "Adult item flagged");
  check(updates.length === 0 && replacements.length === 0 && removed.length === 0, "Item never redirects entire feed");
  const clean = await score({ type: "scoreFeedItem", zones: { body: "A programming workshop for students" } });
  check(clean.block === false && clean.enabled === true, "Clean post remains available");
  const exempt = await score({ type: "scoreText", zones: { body: "porn porn porn" } },
    { ...sender, url: "https://en.wikipedia.org/wiki/Test" });
  check(exempt.block === false && exempt.enabled === false, "Text exemption only");
  const unavailable = await score(item, sender, async () => { throw new Error("Fixture unavailable"); });
  check(unavailable.unavailable === true && unavailable.block === undefined, "Failure never becomes clean verdict");
  const page = { type: "scoreText", zones: { body: "porn porn porn" } };
  await score(page);
  for (let i = 0; i < 6; i++) await Promise.resolve();
  check(updates.length === 0 && replacements.length === 1 && removed[0] === sender.tab.id,
    "Current flagged page replaced, not updated with refused-page Back history");
  check(replacements[0].url.endsWith("blocked.html") && replacements[0].windowId === 3
    && replacements[0].index === 2 && replacements[0].active === false, "Generic replacement preserves tab position and activation");
  currentURL = "https://x.com/changed";
  await score(page);
  await Promise.resolve(); await Promise.resolve();
  check(replacements.length === 1, "Stale page verdict never redirects new route");
  await score(page, { ...sender, frameId: 1 });
  await Promise.resolve(); await Promise.resolve();
  check(replacements.length === 1, "Subframe verdict never redirects top page");
  say(`Safari bridge contract checks passed (${checks})`);
}
main().catch((error) => { say(String(error)); if (globalThis.quit) quit(1); else throw error; });
