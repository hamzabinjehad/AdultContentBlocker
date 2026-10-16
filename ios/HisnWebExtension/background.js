/** Safari-specific bridge; the DOM scanner and scorer are canonical shared JS.
 * Nothing here persists page text, hosts, hits, or browsing timestamps. Safari
 * may suspend this nonpersistent background page; scanners retry and show unavailable
 * states, rather than interpreting a missing response as a clean verdict.
 */
import { buildIndex, scorePage, isExempt } from "./lib/score.js";
import { handleVisualMessage, getVisualStatus, disableVisualEngine, cancelVisualTab } from "./visual/broker.js";
import { visualPreferences, presentationPreferences, PERSON_MODES } from "./visual/decisions.js";
import "./content/feed.js";

const PAGE_KEYS = ["url", "title", "meta", "heading", "body", "alt"];
const ITEM_KEYS = ["body", "alt"];
const SENSITIVITY = 50;
let indexPromise;

const VISUAL_CONTENT_MESSAGES = ["getVisualSettings", "inspectVisualMedia", "cancelVisualMedia"];
const VISUAL_PAGE_MESSAGES = ["getVisualStatus", "saveVisualSettings"];

/** Only the extension's own options page may mutate preferences or read engine
 * status. Content scripts may submit bounded captures, never policy values.
 * HTTP(S) sender checks are kept here even though the shared broker also
 * validates tab/frame ownership. Nothing accepts messages from a webpage.
 */
export function checkedVisualRequest(message, sender, api) {
  if (!message || typeof message !== "object" || Array.isArray(message)
      || !api.runtime.id || sender?.id !== api.runtime.id) return false;
  if (VISUAL_PAGE_MESSAGES.includes(message.type)
      || (message.type === "getVisualSettings" && !sender.tab)) {
    return !sender.tab && sender.url === api.runtime.getURL("options.html");
  }
  if (!VISUAL_CONTENT_MESSAGES.includes(message.type)
      || !Number.isInteger(sender?.tab?.id) || sender.tab.id < 0
      || !Number.isInteger(sender.frameId) || sender.frameId < 0
      || typeof sender.url !== "string" || sender.url.length > 32_768) return false;
  try { return ["http:", "https:"].includes(new URL(sender.url).protocol); }
  catch { return false; }
}

export function checkedVisualPatch(patch) {
  if (!patch || typeof patch !== "object" || Array.isArray(patch)
      || Object.keys(patch).length === 0
      || Object.keys(patch).some(key => !["inspectVisual", "personBlur", "personRegion", "personEffect", "personMedia"].includes(key))
      || ("inspectVisual" in patch && typeof patch.inspectVisual !== "boolean")
      || ("personBlur" in patch && !PERSON_MODES.includes(patch.personBlur))
      || ("personRegion" in patch && !['full','faces'].includes(patch.personRegion))
      || ("personEffect" in patch && !['solid','blur'].includes(patch.personEffect))
      || ("personMedia" in patch && !['both','images','videos'].includes(patch.personMedia))) return null;
  return { ...patch };
}

async function savedVisualPreferences(api) {
  const stored = await api.storage.local.get("state");
  return {...visualPreferences(stored.state),...presentationPreferences(stored.state)};
}

export function visualMessageHandler(api, dependencies = {}) {
  const inspect = dependencies.inspect ?? handleVisualMessage;
  const status = dependencies.status ?? getVisualStatus;
  const disable = dependencies.disable ?? disableVisualEngine;
  return (message, sender, respond) => {
    if (!checkedVisualRequest(message, sender, api)) return false;
    (async () => {
      try {
        const preferences = await savedVisualPreferences(api);
        if (message.type === "getVisualSettings") respond(preferences);
        else if (message.type === "saveVisualSettings") {
          const patch = checkedVisualPatch(message.patch);
          if (!patch) { respond({ error: "invalid-settings" }); return; }
          const next = { ...preferences, ...patch };
          await api.storage.local.set({ state: next });
          if (!next.inspectVisual && next.personBlur === 'off') await disable();
          respond({ settings: next });
        } else if (message.type === "getVisualStatus") {
          respond(await status(preferences));
        } else respond(await inspect(message, sender, preferences));
      } catch {
        respond({ state: "unavailable", available: false, reason: "visual-runtime-unavailable" });
      }
    })();
    return true;
  };
}

export function checkedRequest(message, sender, extensionID) {
  if (!message || typeof message !== "object" || Array.isArray(message)
      || !["scoreText", "scoreFeedItem"].includes(message.type)
      || !extensionID || sender?.id !== extensionID
      || !Number.isInteger(sender?.tab?.id) || sender.tab.id < 0
      || !Number.isInteger(sender.frameId) || sender.frameId < 0
      || typeof sender.url !== "string" || sender.url.length > 32_768) return null;
  let location;
  try { location = new URL(sender.url); } catch { return null; }
  if (!["http:", "https:"].includes(location.protocol)) return null;
  const item = message.type === "scoreFeedItem";
  if (item && (sender.frameId !== 0 || !globalThis.HisnFeed.isSupportedLocation(location))) return null;
  const keys = item ? ITEM_KEYS : PAGE_KEYS;
  const limit = item ? 20_001 : 200_001;
  const raw = message.zones;
  if (!raw || typeof raw !== "object" || Array.isArray(raw)
      || Object.keys(raw).some((key) => !keys.includes(key))) return null;
  const zones = {};
  for (const key of keys) {
    if (raw[key] === undefined) continue;
    if (typeof raw[key] !== "string" || raw[key].length > limit) return null;
    zones[key] = raw[key];
  }
  return { item, zones, host: location.hostname };
}

async function bundledIndex(api) {
  if (!indexPromise) {
    indexPromise = fetch(api.runtime.getURL("seed/terms.json"))
      .then((response) => {
        if (!response.ok) throw new Error("Bundled terms unavailable");
        return response.json();
      })
      .then((terms) => {
        if (!Array.isArray(terms.terms) || terms.terms.length === 0) throw new Error("Empty bundled terms");
        return buildIndex(terms);
      })
      .catch((error) => { indexPromise = undefined; throw error; });
  }
  return indexPromise;
}

/** The page scanner guards route changes itself. This fallback guards again
 * before the extension changes a top-frame tab, never carrying an old verdict
 * onto a new route. Individual feed verdicts never call this function.
 */
async function replaceBlockedPage(api, sender) {
  if (sender.frameId !== 0) return;
  const tab = await api.tabs.get(sender.tab.id);
  if (tab.pendingUrl || tab.url !== sender.url) return;
  // Updating the original tab leaves the refused page one Back/BFCache away.
  // A fresh block tab contains no refused-page history. Remove the old tab only
  // after replacement succeeds; granular item verdicts never take this path.
  const options = { url: api.runtime.getURL("blocked.html"), active: tab.active !== false };
  if (Number.isInteger(tab.windowId)) options.windowId = tab.windowId;
  if (Number.isInteger(tab.index)) options.index = tab.index;
  await api.tabs.create(options);
  await api.tabs.remove(sender.tab.id);
}

export function messageHandler(api, getIndex = () => bundledIndex(api)) {
  return (message, sender, respond) => {
    const request = checkedRequest(message, sender, api.runtime.id);
    if (!request) return false;
    (async () => {
      try {
        const index = await getIndex();
        if (isExempt(request.host, index)) {
          respond({ block: false, enabled: false });
          return;
        }
        const verdict = scorePage(request.zones, index, SENSITIVITY);
        respond({ block: verdict.block, enabled: true });
        if (verdict.block && !request.item) {
          // Native Safari tab API is only a fallback; the shared content
          // scanner can hide a subframe or navigate its own current route.
          replaceBlockedPage(api, sender).catch(() => {});
        }
      } catch {
        // Do not send block:false on an unavailable scorer. A malformed
        // verdict triggers the scanner's bounded retry/unavailable path.
        respond({ unavailable: true });
      }
    })();
    return true;
  };
}

const api = globalThis.browser ?? globalThis.chrome;
if (api?.runtime?.onMessage) {
  api.runtime.onMessage.addListener(messageHandler(api));
  api.runtime.onMessage.addListener(visualMessageHandler(api));
}
api?.tabs?.onRemoved?.addListener(tabId => { cancelVisualTab(tabId).catch(() => {}); });
api?.tabs?.onUpdated?.addListener((tabId, change) => {
  if (change.status === 'loading') cancelVisualTab(tabId).catch(() => {});
});
