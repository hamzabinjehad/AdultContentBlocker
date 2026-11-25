/**
 * Distraction rules in the browser — the half that touches tabs.
 *
 * For a page on a host the app listed, ask the app what to do and do it:
 * leave it, hide its feed, or put the pause or closed page in its place. The
 * rules themselves never live here, so nothing in the browser can edit them.
 *
 * A failure anywhere means "leave the page alone". This layer is about
 * attention, not safety: adult protection has its own rules and its own
 * fail-closed path, and neither depends on anything in this file.
 */

import { feedCSS, hostOf, meterFlush, meterFocus, plan, usageHost, watched } from "../lib/distraction.js";
import { t } from "../lib/i18n.js";
import { createLink } from "../lib/native-link.js";

const NATIVE_HOST = "app.hisn.bridge";
const TIMEOUT_MS = 6000;
const WATCH_FRESH_MS = 25 * 1000;
const KEY = "hisnDistraction";

/** One port kept open for a burst of questions; a new process only as the
 *  fallback (see `lib/native-link.js` for the numbers and the reasons). */
const link = createLink({
  connect: () => (typeof chrome.runtime.connectNative === "function" ? chrome.runtime.connectNative(NATIVE_HOST) : null),
  send: (message) => chrome.runtime.sendNativeMessage(NATIVE_HOST, message),
});

async function native(message) {
  try {
    return await link.ask({ type: "distraction", ...message }, TIMEOUT_MS);
  } catch {
    return null;
  }
}

async function load() {
  const stored = await chrome.storage.session.get(KEY).catch(() => ({}));
  return { watch: null, watchAt: 0, moments: {}, feeds: {}, meter: null, ...(stored?.[KEY] ?? {}) };
}

async function save(state) {
  await chrome.storage.session.set({ [KEY]: state }).catch(() => {});
}

async function freshWatch(state, force = false) {
  if (!force && state.watch && Date.now() - state.watchAt < WATCH_FRESH_MS) return state;
  const reply = await native({ op: "watch" });
  if (reply?.ok === true && Array.isArray(reply.hosts)) {
    state.watch = { all: reply.all === true, hosts: reply.hosts, usageHosts: reply.usageHosts ?? [] };
  } else if (!state.watch) {
    state.watch = { all: false, hosts: [], usageHosts: [] };
  }
  state.watchAt = Date.now();
  return state;
}

const momentURL = () => chrome.runtime.getURL("moment.html");

async function clearFeed(state, tabId) {
  const css = state.feeds[tabId];
  if (!css) return;
  delete state.feeds[tabId];
  await chrome.scripting.removeCSS({ target: { tabId }, css }).catch(() => {});
}

/** Decide one tab. Safe to call as often as the tab changes. */
export async function checkTab(tabId, url) {
  const host = hostOf(url);
  let state = await load();
  if (!host) { if (state.feeds[tabId]) { await clearFeed(state, tabId); await save(state); } return; }
  state = await freshWatch(state);
  if (!watched(host, state.watch)) {
    if (state.feeds[tabId]) await clearFeed(state, tabId);
    await save(state);
    return;
  }
  const action = plan(await native({ op: "decide", url }), url);
  if (action.kind === "hideFeed") {
    const css = feedCSS(action.surface, t("moment.feedHidden"));
    if (css && state.feeds[tabId] !== css) {
      await clearFeed(state, tabId);
      await chrome.scripting.insertCSS({ target: { tabId }, css }).catch(() => {});
      state.feeds[tabId] = css;
    }
  } else {
    await clearFeed(state, tabId);
    if (action.kind === "moment") {
      state.moments[tabId] = { ...action.info, at: Date.now() };
      await save(state);
      await chrome.tabs.update(tabId, { url: momentURL() }).catch(() => {});
      return;
    }
  }
  await save(state);
}

/** Time in front goes to the tab the person is looking at, and only for listed hosts. */
export async function noteFront() {
  const state = await freshWatch(await load());
  let host = null;
  try {
    const [tab] = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
    const window = tab ? await chrome.windows.get(tab.windowId).catch(() => null) : null;
    if (tab && window?.focused) host = usageHost(hostOf(tab.url), state.watch);
  } catch { /* no window in front */ }
  state.meter = meterFocus(state.meter, host, Date.now());
  await save(state);
}

/** Every heartbeat: report time, refresh the list, and look at open tabs again
 *  (a limit runs out, quiet hours begin, a Focus starts). */
export async function distractionTick() {
  let state = await freshWatch(await load(), true);
  const flushed = meterFlush(state.meter, Date.now());
  state.meter = flushed.meter;
  await save(state);
  for (const report of flushed.reports) await native({ op: "usage", host: report.host, seconds: report.seconds });
  let tabs = [];
  try { tabs = await chrome.tabs.query({}); } catch { return; }
  for (const tab of tabs) {
    if (!tab.url || tab.url.startsWith(momentURL())) continue;
    if (watched(hostOf(tab.url), state.watch)) await checkTab(tab.id, tab.url);
  }
}

/** Messages from the pause/closed page. `tabId` is the sender's own tab. */
export async function distractionMessage(msg, tabId) {
  const state = await load();
  const info = state.moments[tabId];
  if (!info) return { ok: false, reason: "no-moment" };
  switch (msg.type) {
    case "distractionInfo":
      return { ok: true, info };
    case "distractionOpen": {
      if (info.mode !== "wait") return { ok: false, reason: "not-a-wait" };
      const reply = await native({ op: "opened", rule: info.rule });
      if (reply?.ok !== true) return { ok: false, reason: "not-recorded" };
      return goBack(state, tabId, info);
    }
    case "distractionSnooze": {
      const reply = await native({ op: "snoozed", rule: info.rule });
      if (reply?.ok !== true) return { ok: false, reason: reply?.reason ?? "not-offered" };
      return goBack(state, tabId, info);
    }
    case "distractionRecheck": {
      const action = plan(await native({ op: "decide", url: info.url }), info.url);
      if (action.kind === "moment") {
        state.moments[tabId] = { ...action.info, at: info.at };
        await save(state);
        return { ok: true, info: state.moments[tabId] };
      }
      return goBack(state, tabId, info);
    }
    default:
      return { ok: false, reason: "unknown-message" };
  }
}

async function goBack(state, tabId, info) {
  delete state.moments[tabId];
  await save(state);
  await chrome.tabs.update(tabId, { url: info.url }).catch(() => {});
  return { ok: true, opened: true };
}

export async function forgetTab(tabId) {
  const state = await load();
  if (!state.moments[tabId] && !state.feeds[tabId]) return;
  delete state.moments[tabId];
  delete state.feeds[tabId];
  await save(state);
}

/** Wire the listeners. Called once per worker start. */
export function startDistraction() {
  if (!chrome?.tabs?.onUpdated) return; // a test shell, or a browser without tabs
  chrome.tabs.onUpdated.addListener((tabId, change, tab) => {
    const url = change.url ?? (change.status === "loading" ? tab?.url : null);
    if (url && !url.startsWith(momentURL())) checkTab(tabId, url).catch(() => {});
    if (change.url || change.status === "complete") noteFront().catch(() => {});
  });
  chrome.tabs.onActivated?.addListener(() => { noteFront().catch(() => {}); });
  chrome.windows?.onFocusChanged?.addListener(() => { noteFront().catch(() => {}); });
  chrome.tabs.onRemoved?.addListener((tabId) => { forgetTab(tabId).catch(() => {}); });
}
