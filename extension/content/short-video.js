/**
 * Short-video blocking inside the page, for what network rules cannot see.
 *
 * `rules/short_video.json` refuses a Shorts or Reels address when the browser
 * loads it. YouTube and Instagram are single-page apps: tapping a Short changes
 * the address with the History API and makes no page request, so no rule ever
 * runs. This script watches the address and leaves for the block page when it
 * turns into a short-video one, and hides the shelves and tabs that lead there.
 *
 * Registered only while the person has short-video blocking on, and only on
 * the sites below. It reads the address and nothing else on the page; it
 * records nothing and sends nothing.
 *
 * Shelf selectors follow each site's current markup and will drift. A missed
 * shelf still leads to a blocked address, so drift costs tidiness, not coverage.
 */
(() => {
  // Keep in step with rules/short_video.json; test/short-video.test.js holds them together.
  const BLOCKED_PATHS = [
    { host: "youtube.com", path: /^\/shorts(\/|$)/i },
    { host: "instagram.com", path: /^\/(reels?|explore)(\/|$)/i },
    { host: "facebook.com", path: /^\/(reels?|watch\/reels?)(\/|$)/i },
    { host: "snapchat.com", path: /^\/spotlight(\/|$)/i },
  ];
  const HIDDEN = [
    // YouTube desktop and mobile web: Shorts shelves, Shorts tiles and the Shorts tab.
    "ytd-reel-shelf-renderer", "ytd-rich-shelf-renderer[is-shorts]",
    "ytd-rich-section-renderer:has(ytd-rich-shelf-renderer[is-shorts])",
    "ytd-guide-entry-renderer:has(a[href^='/shorts'])", "ytd-mini-guide-entry-renderer:has(a[href^='/shorts'])",
    "yt-tab-shape[tab-title='Shorts']", "ytm-reel-shelf-renderer", "ytm-shorts-lockup-view-model",
    "ytm-pivot-bar-item-renderer:has(.pivot-shorts)", "grid-shelf-view-model:has(ytm-shorts-lockup-view-model)",
    "ytd-video-renderer:has(a[href^='/shorts/'])", "ytd-rich-item-renderer:has(a[href^='/shorts/'])",
    // Instagram and Facebook: links to Reels and Explore.
    "a[href^='/reel']", "a[href^='/explore/']", "a[href*='facebook.com/reel']",
  ];
  const POLL_MS = 500;

  function siteOf(hostname) {
    const host = String(hostname || "").toLowerCase().replace(/\.$/, "");
    return BLOCKED_PATHS.find((site) => host === site.host || host.endsWith(`.${site.host}`));
  }

  /** True for an address the person asked to keep out. */
  function isShortVideo(href) {
    const match = /^https?:\/\/([^/?#]*)(\/[^?#]*)?/i.exec(String(href || ""));
    if (!match) return false;
    const site = siteOf(match[1].replace(/^[^@]*@/, "").replace(/:\d+$/, ""));
    return !!site && site.path.test(match[2] || "/");
  }

  globalThis.HisnShortVideo = { isShortVideo, BLOCKED_PATHS, HIDDEN };
  if (typeof document === "undefined" || typeof chrome === "undefined" || !chrome.runtime?.getURL) return;
  if (globalThis.__hisnShortVideoStarted) return;
  globalThis.__hisnShortVideoStarted = true;

  const blockPage = chrome.runtime.getURL("blocked.html?reason=shortvideo");
  let last = "";
  function check() {
    if (location.href === last) return;
    last = location.href;
    // replace, not assign: nothing to go Back to.
    if (isShortVideo(location.href)) location.replace(blockPage);
  }
  check();
  setInterval(check, POLL_MS);
  addEventListener("popstate", check);
  addEventListener("yt-navigate-finish", check, true);
  // Catch a tap on a link before the app starts the video, rather than half a
  // second after the address changes.
  addEventListener("click", (event) => {
    const link = event.target?.closest?.("a[href]");
    if (link && isShortVideo(link.href)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      location.replace(blockPage);
    }
  }, true);

  const style = document.createElement("style");
  style.textContent = `${HIDDEN.join(",\n")} { display: none !important; }`;
  (document.head || document.documentElement).appendChild(style);
})();
