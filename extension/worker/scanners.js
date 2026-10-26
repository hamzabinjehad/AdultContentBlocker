/**
 * Content-script registration.
 *
 * The scanners are registered dynamically rather than declared in the manifest:
 * a manifest `content_scripts` block is unconditional and would inject into
 * every page even when the user turned text or image checks off. Dynamic
 * registration is also the only way to repair an inherited registration from an
 * older version, which is why every field of the contract is read back and
 * compared rather than trusting that "it exists" means "it scans".
 */

import { isLocked } from "../lib/policy.js";
import { disableVisualEngine, prepareVisualEngine } from "../visual/broker.js";

export const TEXT_SCANNER = {
  id: "hisn-scan", js: ["content/feed.js", "content/scan.js"], matches: ["<all_urls>"],
};
export const VISUAL_SCANNER = {
  id: "hisn-visual",
  js: ["visual/capture.js", "content/visual-effects.js", "content/visual.js"],
  css: ["content/visual-gate.css"], matches: ["<all_urls>"],
};

// Matching file names alone is not proof of scanning coverage. A persisted
// registration can still omit frames/sites, run after first paint, or execute
// in the page's MAIN world. Repair the complete owned registration contract.
export function scannerRegistrationMatches(current, expected) {
  if (!current || current.id !== expected.id) return false;
  for (const key of ["js", "css", "matches"]) {
    if (JSON.stringify(current[key] || []) !== JSON.stringify(expected[key] || [])) return false;
  }
  return current.runAt === "document_start" && current.allFrames === true
    && current.matchOriginAsFallback === true && current.persistAcrossSessions === true
    && (current.world === undefined || current.world === "ISOLATED")
    && !(current.excludeMatches || []).length
    && !(current.includeGlobs || []).length && !(current.excludeGlobs || []).length;
}

/**
 * Register or remove the content script that reads page text.
 *
 * `persistAcrossSessions` means Chrome re-registers it before the worker wakes
 * on a cold start, so there is no window where pages load unscanned while the
 * worker spins up.
 */
export async function syncTextScanning(state) {
  const wanted = state.inspectText || state.failClosed || isLocked(state);
  const existing = await chrome.scripting.getRegisteredContentScripts({
    ids: ["hisn-scan"],
  }).catch(() => []);

  if (!wanted) {
    if (existing.length) {
      await chrome.scripting.unregisterContentScripts({ ids: ["hisn-scan"] });
    }
    return;
  }
  const scannerFiles = TEXT_SCANNER.js;
  if (scannerRegistrationMatches(existing[0], TEXT_SCANNER)) return;
  // A registered script survives updates. Migrate older registrations that
  // lack the granular feed dependency before accepting newly opened pages.
  if (existing.length) await chrome.scripting.unregisterContentScripts({ ids: ["hisn-scan"] });

  await chrome.scripting.registerContentScripts([{
    id: "hisn-scan",
    matches: ["<all_urls>"],
    js: scannerFiles,
    runAt: "document_start",
    allFrames: true,
    // `allFrames` alone still misses the frames that carry no real URL of their
    // own — `about:srcdoc`, `blob:` and `data:` iframes — which is exactly how
    // embedded players and popunder ad frames render their content. These
    // inherit their embedder's origin, so `matchOriginAsFallback` injects the
    // scanner into them by that inherited origin; without it a tube page can put
    // the whole video in a srcdoc frame the text layer never reads.
    matchOriginAsFallback: true,
    persistAcrossSessions: true,
  }]);
  console.info("[hisn] page-text scanning registered");
  // Registration covers pages loaded from now on. Tabs already open — a lock
  // started while checking was off, pages opened while the extension was
  // switched off — would otherwise go unscanned for as long as they stay open.
  await injectScannerIntoOpenTabs();
}

/** A separate registration keeps visual inference independent of the text
 *  layer and avoids running/decoding media when the user did not opt in. */
export async function syncVisualScanning(state) {
  const id = "hisn-visual";
  const wanted = state.inspectVisual === true || (state.personBlur && state.personBlur !== "off");
  const existing = await chrome.scripting.getRegisteredContentScripts({ ids: [id] }).catch(() => []);
  const files = VISUAL_SCANNER.js;
  const css = VISUAL_SCANNER.css;
  if (!wanted) {
    await disableVisualEngine();
    if (existing.some((script) => script.id === id)) {
      await chrome.scripting.unregisterContentScripts({ ids: [id] });
    }
    return;
  }
  // Prepare only the opted-in local models, without delaying rule application
  // or waiting for a webpage to submit its first image. No media is captured.
  void prepareVisualEngine(state);
  const current = existing.find((script) => script.id === id);
  if (!scannerRegistrationMatches(current, VISUAL_SCANNER)) {
    if (current) await chrome.scripting.unregisterContentScripts({ ids: [id] });
    await chrome.scripting.registerContentScripts([{
      id, matches: ["<all_urls>"], js: files, css, runAt: "document_start",
      allFrames: true, matchOriginAsFallback: true, persistAcrossSessions: true,
    }]);
  }
  let tabs = [];
  try { tabs = await chrome.tabs.query({ url: ["http://*/*", "https://*/*"] }); } catch { return; }
  await Promise.all(tabs.map(async (tab) => {
    const target = { tabId: tab.id, allFrames: true };
    try {
      await chrome.scripting.insertCSS({ target, files: css });
      await chrome.scripting.executeScript({ target, files });
    } catch { /* A closed or unpermitted tab cannot be injected. */ }
  }));
}

/** Run the scanner in every open web page now. It starts once per frame
 *  however often it is injected (see the bootstrap in content/scan.js). */
export async function injectScannerIntoOpenTabs() {
  let tabs = [];
  try { tabs = await chrome.tabs.query({ url: ["http://*/*", "https://*/*"] }); } catch { return; }
  await Promise.all(tabs.map((t) => chrome.scripting.executeScript({
    target: { tabId: t.id, allFrames: true }, files: ["content/feed.js", "content/scan.js"],
  }).catch(() => { /* a tab that closed, or a page we may not script */ })));
}
