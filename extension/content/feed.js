/**
 * Local, granular text checking for X/Twitter's rendered tweet articles.
 *
 * This is deliberately NOT a whole-platform classification. A verdict hides
 * one article and pauses its media; it never navigates a tab or records a host,
 * post identifier, text, or matched term. The worker still owns scoring policy.
 * Domain/Strict rules remain independent and authoritative.
 *
 * Pending articles are withheld immediately on discovery or text changes, but
 * MutationObserver runs after DOM insertion: this is not an image classifier
 * or a promise of zero exposure. Media without useful text may be missed.
 */
(() => {
  const ITEM_SELECTOR = 'article[data-testid="tweet"]';
  const SUPPORTED_HOSTS = new Set(["x.com", "www.x.com", "mobile.x.com",
    "twitter.com", "www.twitter.com", "mobile.twitter.com"]);
  const MAX_ITEM_TEXT = 20_000;
  const MAX_CAPTION_NODES = 100;
  const MAX_PARALLEL = 4;
  const MAX_SCAN_ITEMS = 40;
  const MAX_QUEUED_TEXT = 64;
  const MAX_CACHED_ITEMS = 512;
  const BATCH_MS = 25;
  const RETRY_LIMIT = 5;
  const REPLY_TIMEOUT_MS = 5_000;
  const RECONNECT_MS = 30_000;
  const POLL_MS = 1_000;
  const OBSERVER_OPTIONS = { childList: true, subtree: true, characterData: true,
    attributes: true, attributeFilter: ["alt", "aria-label", "hidden", "style", "class", "data-testid", "href", "inert"] };

  function isSupportedLocation(loc) {
    return ["https:", "http:"].includes(loc?.protocol)
      && SUPPORTED_HOSTS.has(String(loc?.hostname || "").toLowerCase().replace(/\.$/, ""));
  }
  function boundedText(value, max = MAX_ITEM_TEXT) {
    const text = typeof value === "string" ? value : "";
    return text.length <= max ? text : text.slice(0, max / 2) + "\n" + text.slice(-max / 2);
  }
  function signature(zones, identity = "") {
    // Equality, not a compact hash: a collision must not reuse a clean verdict.
    return JSON.stringify([identity, zones.body || "", zones.alt || ""]);
  }
  function isRendered(el, root, win) {
    for (let n = el; n; n = n.parentElement) {
      if (n.hidden) return false;
      const style = win.getComputedStyle?.(n);
      if (style?.display === "none" || style?.visibility === "hidden" || style?.visibility === "collapse") return false;
      if (n === root) break;
    }
    return true;
  }
  function collectItemZones(item, win) {
    // innerText respects author-hidden nodes and excludes script/style payloads.
    // Our opacity withholding preserves rendering/layout and therefore innerText.
    const body = boundedText(item.innerText || "");
    const captions = [];
    const nodes = item.querySelectorAll("img[alt], [aria-label]");
    // A hostile/huge article must not allocate an unbounded joined payload.
    // Sample both ends, matching the bounded text path's documented limits.
    let selected = nodes;
    if (nodes.length > MAX_CAPTION_NODES) {
      selected = [];
      for (let i = 0; i < MAX_CAPTION_NODES / 2; i++) selected.push(nodes[i]);
      for (let i = nodes.length - MAX_CAPTION_NODES / 2; i < nodes.length; i++) selected.push(nodes[i]);
    }
    for (const el of selected) {
      if (!isRendered(el, item, win)) continue;
      const text = el.getAttribute("alt") || el.getAttribute("aria-label");
      if (text) captions.push(boundedText(text, 2_000));
    }
    return { body, alt: boundedText(captions.join("\n")) };
  }

  function createScanner(deps = {}) {
    const doc = deps.doc ?? globalThis.document;
    const win = deps.win ?? globalThis.window;
    const loc = deps.loc ?? globalThis.location;
    const timers = deps.timers ?? globalThis;
    const now = deps.now ?? (() => Date.now());
    const send = deps.send ?? ((zones) => globalThis.chrome.runtime.sendMessage({ type: "scoreFeedItem", zones }));
    const observe = deps.observe ?? ((cb) => {
      const observer = new globalThis.MutationObserver(cb);
      observer.observe(doc.documentElement, OBSERVER_OPTIONS);
      return () => observer.disconnect();
    });
    const records = new Map(); // attached nodes only; no persisted browsing data
    const cached = new Map(); // bound retained equality signatures, not just requests
    let pendingItems = new Set();
    let cursor = 0, drainTimer = null;
    let revision = 0, active = 0, stopped = false, stopObserving = null, poll = null;
    let lastHref = loc.href;
    const stats = { requests: 0, failures: 0, stale: 0, verdicts: 0 };
    const arabic = () => String(doc.documentElement?.lang || win.navigator?.language || "").startsWith("ar");
    const noticeText = (status) => arabic()
      ? (status === "blocked" ? "أخفى حصن هذا المنشور بسبب إشارات نصية لمحتوى غير مناسب."
        : status === "unavailable" ? "المنشور مخفي مؤقتاً حتى يعاود فحص حصن الاتصال."
        : "يفحص حصن نص هذا المنشور…")
      : (status === "blocked" ? "Hisn hid this post because its text suggests adult content."
        : status === "unavailable" ? "Post hidden while Hisn reconnects to text checking."
        : "Hisn is checking this post’s text…");

    function pauseMedia(item) {
      for (const media of item.querySelectorAll("video, audio")) {
        try { media.pause(); } catch { /* an uninitialised media element */ }
      }
    }
    function setNotice(record, status) {
      if (!record.notice) {
        const notice = doc.createElement("div");
        notice.setAttribute("data-hisn-feed-notice", "");
        notice.setAttribute("role", "status");
        notice.style.cssText = "font:14px/1.5 system-ui;padding:12px 16px;color:CanvasText;background:Canvas;border:1px solid GrayText;";
        record.notice = notice;
      }
      const text = noticeText(status);
      if (record.notice.textContent !== text) record.notice.textContent = text;
      // A virtualised feed can move the article without moving its sibling.
      if (record.item.previousSibling !== record.notice) record.item.before(record.notice);
    }
    function withhold(record, status = "pending") {
      const item = record.item;
      if (!record.original) {
        record.original = {
          opacity: item.style.getPropertyValue("opacity"), opacityPriority: item.style.getPropertyPriority("opacity"),
          pointer: item.style.getPropertyValue("pointer-events"), pointerPriority: item.style.getPropertyPriority("pointer-events"),
          aria: item.getAttribute("aria-hidden"),
          inert: item.inert === true,
        };
      }
      if (item.style.getPropertyValue("opacity") !== "0" || item.style.getPropertyPriority("opacity") !== "important") item.style.setProperty("opacity", "0", "important");
      if (item.style.getPropertyValue("pointer-events") !== "none" || item.style.getPropertyPriority("pointer-events") !== "important") item.style.setProperty("pointer-events", "none", "important");
      if (item.getAttribute("aria-hidden") !== "true") item.setAttribute("aria-hidden", "true");
      if (item.inert !== true) item.inert = true;
      pauseMedia(item);
      if (status === "blocked") {
        // Keep the generic notice, not an enormous invisible video-sized gap.
        // Do not use display:none: that changes innerText's semantics and can
        // accidentally score hidden scripts when a virtualised node is reused.
        record.collapse ??= new Map();
        for (const [property, value] of [["max-height", "0px"], ["min-height", "0px"], ["overflow", "hidden"]]) {
          if (!record.collapse.has(property)) record.collapse.set(property, {
            value: item.style.getPropertyValue(property), priority: item.style.getPropertyPriority(property), applied: value,
          });
          if (item.style.getPropertyValue(property) !== value || item.style.getPropertyPriority(property) !== "important") item.style.setProperty(property, value, "important");
        }
      }
      setNotice(record, status);
    }
    function release(record) {
      const item = record.item, original = record.original;
      if (original) {
        // Do not overwrite unrelated style changes made by the page.
        if (item.style.getPropertyValue("opacity") === "0" && item.style.getPropertyPriority("opacity") === "important") {
          if (original.opacity) item.style.setProperty("opacity", original.opacity, original.opacityPriority); else item.style.removeProperty("opacity");
        }
        if (item.style.getPropertyValue("pointer-events") === "none" && item.style.getPropertyPriority("pointer-events") === "important") {
          if (original.pointer) item.style.setProperty("pointer-events", original.pointer, original.pointerPriority); else item.style.removeProperty("pointer-events");
        }
        if (item.getAttribute("aria-hidden") === "true") {
          if (original.aria === null) item.removeAttribute("aria-hidden"); else item.setAttribute("aria-hidden", original.aria);
        }
        if (item.inert === true) item.inert = original.inert;
      }
      record.original = null;
      for (const [property, previous] of record.collapse ?? []) {
        if (item.style.getPropertyValue(property) === previous.applied && item.style.getPropertyPriority(property) === "important") {
          if (previous.value) item.style.setProperty(property, previous.value, previous.priority); else item.style.removeProperty(property);
        }
      }
      record.collapse = null;
      record.notice?.remove();
      record.notice = null;
      // Never autoplay media after checking: permission to display is not a
      // request to resume audio/video the user did not start.
    }
    function snapshot(item) {
      const zones = collectItemZones(item, win);
      const statusLink = item.querySelector('a[href*="/status/"]');
      return { zones, signature: signature(zones, statusLink?.getAttribute("href") || "") };
    }
    function retainSignature(record) {
      cached.delete(record); cached.set(record, true);
      while (cached.size > MAX_CACHED_ITEMS) {
        const oldest = cached.keys().next().value;
        cached.delete(oldest);
        if (!oldest.inFlight && ["clean", "blocked"].includes(oldest.status)) oldest.signature = null;
      }
    }
    function queuedTextCount() {
      let count = 0;
      for (const record of records.values()) if (record.zones) count++;
      return count;
    }
    function scheduleDrain() {
      if (stopped || drainTimer !== null || !pendingItems.size || queuedTextCount() >= MAX_QUEUED_TEXT) return;
      drainTimer = timers.setTimeout(() => { drainTimer = null; drain(); }, BATCH_MS);
    }
    function drain() {
      if (stopped) return;
      let visited = 0;
      for (const item of pendingItems) {
        if (visited >= MAX_SCAN_ITEMS || queuedTextCount() >= MAX_QUEUED_TEXT) break;
        pendingItems.delete(item); visited++;
        if (!item.isConnected || (typeof item.matches === "function" && !item.matches(ITEM_SELECTOR))) continue;
        if (!isRendered(item, null, win)) {
          const previous = records.get(item);
          if (previous) { records.delete(item); cached.delete(previous); previous.token++; release(previous); }
          continue;
        }
        let current;
        try { current = snapshot(item); } catch { continue; }
        let record = records.get(item);
        if (!record) {
          record = { item, token: 0, inFlight: false, original: null, notice: null };
          records.set(item, record);
        }
        if (record.signature !== current.signature || record.revision !== revision) {
          record.signature = current.signature; record.zones = current.zones; record.revision = revision;
          record.token++; record.status = "pending"; record.attempts = 0; record.notBefore = 0;
        }
        if (record.status !== "clean") withhold(record, record.status);
        retainSignature(record);
      }
      pump(); scheduleDrain();
    }
    function refresh(changedItems) {
      if (stopped || !isSupportedLocation(loc)) return;
      const nodes = Array.from(doc.querySelectorAll(ITEM_SELECTOR));
      const items = new Set(nodes);
      for (const [item, record] of records) {
        if (!items.has(item) || !item.isConnected) {
          records.delete(item); cached.delete(record); pendingItems.delete(item); record.token++; release(record);
        }
      }
      if (changedItems) {
        // Changed/in-flight items take priority over the historical polling
        // backlog. Never apply an old answer because the backlog is large.
        pendingItems = new Set([...Array.from(changedItems).filter((item) => items.has(item)), ...pendingItems]);
      } else if (nodes.length) {
        // A rotating bounded polling pass catches changes missed by an observer
        // without repeatedly reading every old post in an infinite-scroll feed.
        const count = Math.min(nodes.length, MAX_SCAN_ITEMS);
        for (let i = 0; i < count; i++) pendingItems.add(nodes[(cursor + i) % nodes.length]);
        cursor = (cursor + count) % nodes.length;
      }
      drain();
    }
    function onMutation(events) {
      if (!events) { refresh(); return; } // injectable deterministic harness
      const changed = new Set();
      for (const event of events) {
        const owner = (event.target?.nodeType === 3 ? event.target.parentElement : event.target)?.closest?.(ITEM_SELECTOR);
        if (owner) changed.add(owner);
        for (const node of event.addedNodes ?? []) {
          if (node.matches?.(ITEM_SELECTOR)) changed.add(node);
          for (const item of node.querySelectorAll?.(ITEM_SELECTOR) ?? []) changed.add(item);
        }
      }
      if (changed.size) refresh(changed);
    }
    function fail(record) {
      stats.failures++;
      record.attempts++;
      record.status = record.attempts >= RETRY_LIMIT ? "unavailable" : "pending";
      record.notBefore = now() + (record.attempts >= RETRY_LIMIT ? RECONNECT_MS : 500 * 2 ** (record.attempts - 1));
      withhold(record, record.status);
    }
    async function judge(record) {
      record.inFlight = true; active++;
      const token = record.token, href = loc.href, sentRevision = revision;
      let verdict = null, timeout;
      try {
        stats.requests++;
        verdict = await Promise.race([send(record.zones), new Promise((resolve) => {
          timeout = timers.setTimeout(() => resolve(null), REPLY_TIMEOUT_MS);
        })]);
      } catch { verdict = null; }
      finally { if (timeout !== undefined) timers.clearTimeout(timeout); record.inFlight = false; active--; }
      if (stopped || records.get(record.item) !== record || !record.item.isConnected) { pump(); return; }
      if (!isRendered(record.item, null, win)) {
        records.delete(record.item); cached.delete(record); pendingItems.delete(record.item);
        release(record); pump(); scheduleDrain(); return;
      }
      let current;
      try { current = snapshot(record.item); } catch { fail(record); pump(); return; }
      if (token !== record.token || sentRevision !== revision || href !== loc.href || current.signature !== record.signature) {
        // Free the old bounded payload before queuing its replacement, even
        // when every queue slot is full of other pending posts.
        record.zones = null;
        record.signature = null;
        stats.stale++; refresh([record.item]); return;
      }
      if (!verdict || typeof verdict.block !== "boolean" || typeof verdict.enabled !== "boolean") {
        fail(record); pump(); return;
      }
      stats.verdicts++; record.attempts = 0; record.notBefore = 0;
      record.status = verdict.enabled && verdict.block ? "blocked" : "clean";
      record.zones = null;
      if (record.status === "blocked") withhold(record, "blocked"); else release(record);
      pump(); scheduleDrain();
    }
    function pump() {
      if (stopped) return;
      for (const record of records.values()) {
        if (active >= MAX_PARALLEL) break;
        if (record.inFlight || !record.zones || ["clean", "blocked"].includes(record.status) || record.notBefore > now()) continue;
        judge(record);
      }
    }
    function recheck() { revision++; refresh(Array.from(doc.querySelectorAll(ITEM_SELECTOR))); }
    function onPlay(event) {
      const item = event.target?.closest?.(ITEM_SELECTOR), record = records.get(item);
      if (record && record.status !== "clean") pauseMedia(item);
    }
    function stop() {
      stopped = true; stopObserving?.();
      if (poll !== null && timers.clearInterval) timers.clearInterval(poll);
      if (drainTimer !== null) timers.clearTimeout(drainTimer);
      doc.removeEventListener?.("play", onPlay, true);
      for (const record of records.values()) release(record);
      records.clear(); cached.clear(); pendingItems.clear();
    }
    function start() {
      if (!isSupportedLocation(loc)) return;
      stopObserving = observe(onMutation);
      doc.addEventListener("play", onPlay, true);
      refresh(Array.from(doc.querySelectorAll(ITEM_SELECTOR)));
      if (doc.readyState === "loading") doc.addEventListener("DOMContentLoaded", () => refresh(Array.from(doc.querySelectorAll(ITEM_SELECTOR))), { once: true });
      poll = timers.setInterval(() => {
        if (loc.href !== lastHref) { lastHref = loc.href; revision++; }
        refresh();
      }, POLL_MS);
    }
    return { start, refresh, recheck, stop,
      get stats() { return { ...stats, cachedItems: cached.size, queuedText: queuedTextCount() }; } };
  }
  globalThis.HisnFeed = { ITEM_SELECTOR, MAX_ITEM_TEXT, MAX_CAPTION_NODES, MAX_PARALLEL, MAX_SCAN_ITEMS,
    MAX_QUEUED_TEXT, MAX_CACHED_ITEMS, BATCH_MS, RETRY_LIMIT,
    REPLY_TIMEOUT_MS, RECONNECT_MS, POLL_MS, OBSERVER_OPTIONS,
    isSupportedLocation, boundedText, signature, collectItemZones, createScanner };
})();
