/**
 * Tab-level enforcement: the parts of a policy that DNR cannot reach.
 *
 * Declarative rules judge a request as it is made. A tab that was already open
 * when the rules tightened makes no new request, so it can stay up for as long
 * as it is left alone. These helpers enumerate open tabs, ask the shared policy
 * evaluator what it would decide for each, and replace the refused ones with
 * the block page.
 */

import { effectiveStrict, tabsToBlock } from "../lib/policy.js";

/**
 * Send already-open tabs that the policy now refuses to the block page.
 *
 * Cheap when nothing is strict and no custom block exists, which is the common
 * case: one `tabs.query` and no navigations. Errors are logged, not thrown — a
 * tab that vanished mid-walk must not abort the rest.
 */
export async function enforceOpenTabs(state) {
  if (!effectiveStrict(state) && !(state.customBlocks || []).length) return;
  let tabs;
  try {
    tabs = await chrome.tabs.query({});
  } catch (err) {
    console.warn("[hisn] could not enumerate tabs:", err?.message ?? err);
    return;
  }
  const byId = new Map(tabs.map((t) => [t.id, t]));
  for (const { id, reason } of tabsToBlock(tabs, state)) {
    try {
      await replaceTab(byId.get(id), chrome.runtime.getURL(`blocked.html?reason=${reason}`));
    } catch (err) {
      console.warn("[hisn] could not close tab %d:", id, err?.message ?? err);
    }
  }
}

/**
 * Put the block page where `tab` was, as a NEW tab, and close the old one.
 *
 * Navigating the tab in place (`tabs.update`) left the refused page one Back
 * away in that tab's history, and the back-forward cache restores it without
 * a network request — which no blocking rule ever sees. A fresh tab has no
 * history to go back to. Same window, position, pinning and group.
 */
export async function replaceTab(tab, url) {
  if (!tab) return;
  const created = await chrome.tabs.create({
    windowId: tab.windowId, index: tab.index, url, active: tab.active, pinned: tab.pinned,
  });
  await chrome.tabs.remove(tab.id);
  if (typeof tab.groupId === "number" && tab.groupId >= 0 && chrome.tabs.group) {
    await chrome.tabs.group({ groupId: tab.groupId, tabIds: created.id }).catch(() => {});
  }
}

/**
 * The page's own `location.replace` is how a blocked top frame normally leaves
 * — no history entry, nothing to go Back to. But a page can cancel its own
 * navigation (the Navigation API's `navigate` event), so a moment later the
 * worker checks, and if the tab is still not on the block page it replaces
 * the tab itself, which no page can refuse.
 */
export function ensureBlocked(tabId, sourceURL) {
  const blockedPage = chrome.runtime.getURL("blocked.html");
  setTimeout(async () => {
    try {
      const tab = await chrome.tabs.get(tabId);
      // Never carry an old page's content verdict onto a new navigation.
      if (!sourceURL || tab.pendingUrl || tab.url !== sourceURL) return;
      if (!String(tab.url || tab.pendingUrl || "").startsWith(blockedPage)) {
        await replaceTab(tab, chrome.runtime.getURL("blocked.html?reason=terms"));
      }
    } catch { /* the tab is gone — which is also fine */ }
  }, 1500);
}
