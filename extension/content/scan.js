/**
 * Page-text scanning.
 *
 * The first code this extension has ever run inside a page. Everything before
 * it worked at the network layer, which can see a URL and nothing else.
 *
 * WHAT THIS LAYER IS FOR
 * ----------------------
 * The URL keyword rules built by `blocklist/build.py` match with explicit word
 * boundaries, because that is the only way `sex` can be blocked without also
 * taking out `essex.gov.uk`. The price is that they miss concatenations like
 * `sexstories`, and they cannot see a page's contents at all. This layer reads
 * the rendered text and scores it, which catches both.
 *
 * WHAT IT DELIBERATELY DOES NOT DO
 * --------------------------------
 *  * **No URL pass.** The DNR keyword rules already judge the URL before a
 *    single byte is requested, which is strictly better than doing it here
 *    after the page has begun loading. Repeating that work in the page would
 *    add a flash of visible content and buy nothing.
 *
 *  * **No hiding the page while it thinks.** Blanking every page until a
 *    verdict arrives is the correct fail-closed posture for IMAGES, where the
 *    thing being judged is the thing being shown. For text it would blank the
 *    entire web on every navigation, and a tool that makes browsing feel broken
 *    gets uninstalled. Text is judged after render, and accepted as such.
 *
 *  * **No reporting of what matched.** Not to a server — there is none — but
 *    also not into the block page's URL or the tab title, both of which land in
 *    browser history. `docs/THREAT_MODEL.md` Part 5 forbids a record of what
 *    someone tried to reach, and the most likely way to create one by accident
 *    is a well-meant `?matched=` parameter.
 *
 * Scoring itself lives in the service worker, not here. Content scripts cannot
 * import ES modules, so doing it in-page would mean either a bundler — which
 * this repo has deliberately never had — or a copy of the scorer that drifts
 * from the tested one. Messaging costs a few milliseconds on a path that is
 * already asynchronous.
 *
 * FIVE WAYS THIS USED TO GET A PAGE WRONG
 * ---------------------------------------
 * Each of the following was a real hole, and each has a test now.
 *
 *  1. Change detection was `title + body.length`. A feed that swapped one post
 *     for another of the same length, or a player that replaced its
 *     description in place, was never re-scored. Now the signature is a hash
 *     of every zone's text.
 *  2. Scheduling was a pure debounce. A page that mutates every second — every
 *     infinite-scroll feed, every live player — pushed the scan out forever.
 *     Now the debounce has a ceiling: `MAX_WAIT_MS` after the first pending
 *     change, the scan runs whatever the page is doing.
 *  3. `decodeURIComponent` threw on a malformed URL (`%E0%A4%A`), before any
 *     text was read, and nothing caught it. Now decoding falls back to the raw
 *     string.
 *  4. The signature was recorded BEFORE the worker answered, so a worker that
 *     was asleep, restarting, or mid-update marked the page as scanned and it
 *     never got another look. Now a page counts as scored only when a verdict
 *     arrives, and a failed request retries with backoff.
 *  5. A verdict that arrived after a single-page app had routed away was
 *     applied to the new route — blocking a clean page for the previous one's
 *     sins, or clearing a bad one on the previous one's innocence. Now a
 *     verdict is discarded if the URL changed while it was in flight.
 *
 * TESTABILITY
 * -----------
 * A content script cannot be imported, so the scanner is a factory over its
 * dependencies — document, timers, the worker channel, navigation — with the
 * real ones as defaults. `test/scan.test.js` drives it with fakes and fake
 * time in `jsc`; `test/browser/` drives the same code against a real DOM in a
 * real browser. The bootstrap at the bottom runs only inside an extension.
 */

(() => {
  const MAX_TEXT = 200_000;      // beyond this, more text does not change a verdict
  const SETTLE_MS = 2_000;       // debounce for pages that keep mutating
  const MAX_WAIT_MS = 5_000;     // ...but never wait longer than this
  const RETRY_BASE_MS = 500;     // worker unreachable: 0.5s, 1s, 2s, 4s, 8s
  const MAX_RETRIES = 5;
  const HREF_POLL_MS = 1_000;
  const MAX_STALE = 3;           // a page that re-routes this often mid-verdict is dodging
  const MAX_SHADOW_NODES = 20_000;
  const REPLY_TIMEOUT_MS = 5_000;
  const OBSERVER_OPTIONS = { childList: true, subtree: true, characterData: true,
    attributes: true, attributeFilter: ["alt", "aria-label", "content", "name", "property", "hidden"] };
  const POLICY_FIELDS = ["inspectText", "textSensitivity", "customTerms", "ignoreTerms",
    "textAllow", "failClosed", "lockUntil", "listVersion"];
  function scoringPolicyChanged(oldState, newState) {
    return POLICY_FIELDS.some((key) => JSON.stringify(oldState?.[key]) !== JSON.stringify(newState?.[key]));
  }

  // ------------------------------------------------------------------------
  // Pure helpers
  // ------------------------------------------------------------------------

  /** `decodeURIComponent` that cannot throw. A malformed escape is left as
   *  typed; the scorer sees `%E0%A4%A` instead of nothing at all. */
  function safeDecode(s) {
    try { return decodeURIComponent(s); } catch { return String(s); }
  }
  function boundedText(text, limit = MAX_TEXT) {
    const value = String(text || "");
    return value.length <= limit ? value : value.slice(0, limit / 2) + "\n" + value.slice(-limit / 2);
  }
  function sampleElements(nodes, limit) {
    if (nodes.length <= limit) return Array.from(nodes);
    const half = Math.floor(limit / 2), result = [];
    for (let i = 0; i < half; i++) result.push(nodes[i]);
    for (let i = nodes.length - (limit - half); i < nodes.length; i++) result.push(nodes[i]);
    return result;
  }

  /** FNV-1a over a string. Not cryptographic — it only has to notice that a
   *  page changed, and it has to be cheap on 200k characters. */
  function fnv1a(str, h = 0x811c9dc5) {
    for (let i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = Math.imul(h, 0x01000193) >>> 0;
    }
    return h >>> 0;
  }

  /** What "the page changed" means: any zone's text, not its length. */
  function contentSignature(zones) {
    let h = 0x811c9dc5;
    for (const key of ["url", "title", "meta", "heading", "alt", "body"]) {
      h = fnv1a(String(zones[key] ?? ""), h);
      h = fnv1a("\u0000", h);
    }
    return `${h.toString(16)}:${(zones.body ?? "").length}`;
  }

  /** Backoff for a worker that did not answer: 0.5s, 1s, 2s, 4s, 8s. */
  function retryDelayMs(attempt) {
    return RETRY_BASE_MS * 2 ** Math.min(attempt, MAX_RETRIES - 1);
  }

  /**
   * A trailing debounce with a ceiling.
   *
   * `request()` schedules `fn` for `settleMs` from now, pushing it back on each
   * call — but never past `maxWaitMs` from the FIRST request of the batch. A
   * page that never stops mutating therefore still gets scanned every
   * `maxWaitMs`, instead of never.
   */
  function createScheduler(fn, { settleMs = SETTLE_MS, maxWaitMs = MAX_WAIT_MS,
                                 setTimeout: set = globalThis.setTimeout,
                                 clearTimeout: clear = globalThis.clearTimeout,
                                 now = () => Date.now() } = {}) {
    let timer = null;
    let firstRequestAt = 0;
    let stopped = false;

    function fire() {
      timer = null;
      firstRequestAt = 0;
      fn();
    }
    return {
      request() {
        if (stopped) return;
        const t = now();
        if (!firstRequestAt) firstRequestAt = t;
        const untilCeiling = Math.max(0, firstRequestAt + maxWaitMs - t);
        if (timer !== null) clear(timer);
        timer = set(fire, Math.min(settleMs, untilCeiling));
      },
      cancel() {
        if (timer !== null) clear(timer);
        timer = null;
        firstRequestAt = 0;
      },
      stop() { this.cancel(); stopped = true; },
      get pending() { return timer !== null; },
    };
  }

  // ------------------------------------------------------------------------
  // The scanner
  // ------------------------------------------------------------------------

  /**
   * Build a scanner over `deps`. Every dependency defaults to the real thing;
   * tests substitute what they need. `start()` wires the passes; `evaluate()`
   * is one scoring pass and is also what the scheduler calls.
   *
   * deps:
   *   doc, win, loc       — document, window, location
   *   send(zones)         — ask the worker; resolves to a verdict or throws
   *   navigate(url)       — replace the page with the block page
   *   hide()              — a subframe hiding itself
   *   blockPageURL        — what to navigate to
   *   isTop               — is this the top-level frame
   *   observe(cb)         — start a MutationObserver-like feed; returns stop()
   *   timers              — { setTimeout, clearTimeout, setInterval }
   *   now()               — the clock
   */
  function createScanner(deps = {}) {
    const doc = deps.doc ?? globalThis.document;
    const win = deps.win ?? globalThis.window;
    const loc = deps.loc ?? globalThis.location;
    const timers = deps.timers ?? {
      setTimeout: (...a) => globalThis.setTimeout(...a),
      clearTimeout: (...a) => globalThis.clearTimeout(...a),
      setInterval: (...a) => globalThis.setInterval(...a),
    };
    const now = deps.now ?? (() => Date.now());
    const isTop = deps.isTop ?? (win.top === win);
    const send = deps.send
      ?? ((zones) => globalThis.chrome.runtime.sendMessage({ type: "scoreText", zones }));
    const blockPageURL = deps.blockPageURL
      ?? (() => globalThis.chrome.runtime.getURL("blocked.html?reason=terms"));
    const navigate = deps.navigate ?? ((url) => loc.replace(url));
    // `!important` on the inline style, re-applied if the page takes it off:
    // a plain `display:none` loses to a page stylesheet's
    // `html { display: block !important }`, and the page's own script can
    // simply remove it.
    const hide = deps.hide ?? (() => {
      const el = doc.documentElement;
      const apply = () => {
        if (el.style.getPropertyValue("display") !== "none"
            || el.style.getPropertyPriority("display") !== "important") {
          el.style.setProperty("display", "none", "important");
        }
      };
      apply();
      try {
        new globalThis.MutationObserver(apply)
          .observe(el, { attributes: true, attributeFilter: ["style", "class", "hidden"] });
      } catch { /* already hidden once; the worker still closes the tab */ }
    });
    const observe = deps.observe ?? ((cb) => {
      const o = new globalThis.MutationObserver(cb);
      o.observe(doc.documentElement, OBSERVER_OPTIONS);
      return () => o.disconnect();
    });

    let lastScored = "";     // signature of the last page the worker JUDGED
    let lastAttempted = "";  // signature of the last page we ASKED about
    let inFlight = false;
    let dirty = false;       // a change arrived while a request was in flight
    let attempts = 0;
    let retryTimer = null;
    let stopped = false;
    let stopObserving = null;
    let lastHref = loc.href;
    let staleStreak = 0;
    let policyRevision = 0;

    const stats = { evaluations: 0, requests: 0, failures: 0, verdicts: 0, stale: 0 };

    /** Collect the zones the scorer weights, cheaply. */
    function collectZones() {
      const meta = [];
      for (const sel of ['meta[name="description"]', 'meta[property^="og:"]',
                         'meta[name="keywords"]']) {
        for (const el of sampleElements(doc.querySelectorAll(sel), 40)) {
          const c = el.getAttribute("content");
          if (c) meta.push(boundedText(c, 2000));
        }
      }

      const headings = [];
      for (const el of sampleElements(doc.querySelectorAll("h1, h2"), 40)) {
        headings.push(boundedText(el.textContent, 2000));
      }

      // `innerText` rather than `textContent`: it respects rendering, so it
      // skips <script>, <style> and hidden elements. `textContent` would
      // happily score a page's inline JSON payload and its CSS.
      //
      // innerText stops at a shadow root, open or closed, so text a page
      // renders inside web components was never read; it is gathered
      // separately. And past MAX_TEXT the HEAD and the TAIL are kept: slicing
      // the head alone meant an infinite-scroll page was judged on its first
      // screens forever, and the verdict froze because the signature did too.
      const full = [doc.body?.innerText || "", ...shadowTexts()].join("\n");
      const body = boundedText(full);

      const alt = [];
      for (const el of sampleElements(doc.querySelectorAll("img[alt], [aria-label]"), 100)) {
        alt.push(boundedText(el.getAttribute("alt") || el.getAttribute("aria-label"), 2000));
      }

      return {
        url: boundedText(safeDecode(loc.pathname + loc.search), 8000),
        title: boundedText(doc.title, 2000),
        meta: boundedText(meta.join(" ")),
        heading: headings.join(" "),
        body,
        alt: boundedText(alt.join(" ")),
      };
    }

    /** Every shadow root under the document, open or closed, nested ones
     *  included, bounded so a huge DOM cannot stall the page. New roots are
     *  handed to `watchRoot` so their mutations re-arm a scan as well. */
    function shadowRoots() {
      const roots = [];
      const openOrClosed = deps.shadowRootOf ?? ((el) =>
        el.shadowRoot ?? globalThis.chrome?.dom?.openOrClosedShadowRoot?.(el) ?? null);
      if (typeof doc.createTreeWalker !== "function" || !doc.body) return roots;
      const pending = [doc.body];
      let visited = 0;
      while (pending.length && visited < MAX_SHADOW_NODES) {
        const walker = doc.createTreeWalker(pending.pop(), 1 /* SHOW_ELEMENT */);
        for (let n = walker.currentNode; n && visited < MAX_SHADOW_NODES; n = walker.nextNode()) {
          visited++;
          let root = null;
          try { root = openOrClosed(n); } catch { root = null; }
          if (root) { roots.push(root); pending.push(root); watchRoot(root); }
        }
      }
      return roots;
    }

    function shadowTexts() {
      const out = [];
      for (const root of shadowRoots()) {
        for (const child of root.children ?? []) {
          const t = child.innerText ?? child.textContent ?? "";
          if (t) out.push(t);
        }
      }
      return out;
    }

    const watchedRoots = new WeakSet();
    function watchRoot(root) {
      if (watchedRoots.has(root) || typeof globalThis.MutationObserver !== "function") return;
      watchedRoots.add(root);
      try {
        new globalThis.MutationObserver(() => onMutation())
          .observe(root, OBSERVER_OPTIONS);
      } catch { /* a root we cannot observe is still read on the next pass */ }
    }

    const scheduler = createScheduler(() => { evaluate(); }, {
      setTimeout: timers.setTimeout, clearTimeout: timers.clearTimeout, now,
    });

    function scheduleRetry() {
      if (stopped || retryTimer !== null) return;
      if (attempts >= MAX_RETRIES) return;    // give up on this content; a change re-arms
      const delay = retryDelayMs(attempts);
      attempts++;
      retryTimer = timers.setTimeout(() => { retryTimer = null; evaluate(); }, delay);
    }

    function block() {
      stopped = true;
      scheduler.stop();
      if (stopObserving) stopObserving();
      if (retryTimer !== null) { timers.clearTimeout(retryTimer); retryTimer = null; }

      // Hidden first, in every frame. A subframe must never navigate the whole
      // tab — an advert or a widget could otherwise take the page out from
      // under the user — so hiding is all it does. The top frame hides too,
      // because a page can cancel its own navigation (the Navigation API's
      // `navigate` event) and must not stay readable if it does; the worker
      // also sends the tab to the block page itself, which a page cannot
      // cancel.
      hide();
      if (!isTop) return;
      // `replace`, not `assign`: the offending URL must not be left in
      // history, where it becomes exactly the record Part 5 forbids. No
      // parameters beyond the reason, for the same reason.
      navigate(blockPageURL());
    }

    /**
     * One scoring pass.
     *
     * FAIL OPEN, on purpose, and only here — but no longer SILENTLY. If the
     * worker is asleep, mid-update or simply not answering, this returns
     * without blocking and tries again shortly. Every other fallback in this
     * codebase is the restrictive one, so the exception needs justifying: a
     * text score is a heuristic layered on top of two enforcement layers that
     * have already had their say, and treating "no answer" as "block" would
     * black out ordinary browsing every time the worker restarted. The domain
     * list and the URL rules are the ones that must fail closed, and they do.
     */
    async function evaluate() {
      if (stopped) return;
      if (inFlight) { dirty = true; return; }
      stats.evaluations++;

      let zones;
      try {
        zones = collectZones();
      } catch {
        return;                // a DOM in a strange state; the next change retries
      }
      const signature = contentSignature(zones);
      if (signature === lastScored) return;
      if (signature !== lastAttempted) {
        // New content gets a fresh retry budget, whatever happened before.
        lastAttempted = signature;
        attempts = 0;
      }

      inFlight = true;
      const hrefAtSend = loc.href;
      const revisionAtSend = policyRevision;
      let verdict = null;
      let replyTimer;
      try {
        stats.requests++;
        verdict = await Promise.race([send(zones), new Promise((resolve) => {
          replyTimer = timers.setTimeout(() => resolve(null), REPLY_TIMEOUT_MS);
        })]);
      } catch {
        verdict = null;
      } finally {
        if (replyTimer !== undefined) timers.clearTimeout(replyTimer);
        inFlight = false;
      }
      if (revisionAtSend !== policyRevision) {
        dirty = false;
        evaluate();
        return;
      }

      // A verdict for a page that is no longer here. A single-page app can
      // route away while the worker is thinking; the answer describes the OLD
      // route and must not block — or clear — the new one. Drop it and look
      // at what is on screen now.
      if (loc.href !== hrefAtSend) {
        stats.stale++;
        staleStreak++;
        // …unless the page keeps doing it. A page that changes its URL every
        // time it is asked about never receives a verdict at all otherwise;
        // after a few in a row, a block for the old route counts.
        if (verdict?.block === true && staleStreak >= MAX_STALE) { block(); return; }
        dirty = false;
        evaluate();
        return;
      }
      staleStreak = 0;

      // `undefined` is what sendMessage resolves to when nobody answered — a
      // worker that was torn down between the message and the reply. That is a
      // failure to retry, not a page judged clean.
      // So is any reply without a boolean verdict — an error reply
      // (`{ok: false, …}`) used to count as a clean page, and the page was
      // never asked about again.
      if (verdict == null || typeof verdict !== "object" || typeof verdict.block !== "boolean") {
        stats.failures++;
        scheduleRetry();
        return;
      }

      stats.verdicts++;
      attempts = 0;
      lastScored = signature;
      if (verdict.block) { block(); return; }

      if (dirty) {              // the page moved on while we were asking
        dirty = false;
        scheduler.request();
      }
    }

    function onMutation() {
      if (stopped) return;
      scheduler.request();
    }

    function recheck() {
      if (stopped) return;
      policyRevision++;
      lastScored = "";
      attempts = 0;
      if (retryTimer !== null) { timers.clearTimeout(retryTimer); retryTimer = null; }
      evaluate();
    }

    function start() {
      // Pass 1: as soon as the head is parsed. Title and meta alone decide most
      // pages, and this fires long before images and scripts finish.
      if (doc.readyState === "loading") {
        doc.addEventListener("DOMContentLoaded", () => evaluate(), { once: true });
      } else {
        evaluate();
      }

      // Pass 2: once loaded, and again when the page settles after mutating —
      // or, if it never settles, every MAX_WAIT_MS regardless.
      win.addEventListener("load", () => evaluate(), { once: true });
      stopObserving = observe(onMutation);

      // Single-page apps change location without a navigation, so neither the
      // DNR rules nor a fresh injection get another chance. Re-arm on href
      // changes. The signature includes the URL, so nothing else is needed for
      // the new page to count as new.
      timers.setInterval(() => {
        if (stopped || loc.href === lastHref) return;
        lastHref = loc.href;
        attempts = 0;
        evaluate();
      }, HREF_POLL_MS);
    }

    return {
      start, evaluate, onMutation, recheck,
      get stopped() { return stopped; },
      get stats() { return { ...stats }; },
    };
  }

  // ------------------------------------------------------------------------
  // Exports and bootstrap
  // ------------------------------------------------------------------------

  globalThis.HisnScan = {
    safeDecode, fnv1a, contentSignature, retryDelayMs, createScheduler, createScanner,
    boundedText, sampleElements,
    SETTLE_MS, MAX_WAIT_MS, RETRY_BASE_MS, MAX_RETRIES, MAX_TEXT, HREF_POLL_MS,
    REPLY_TIMEOUT_MS, OBSERVER_OPTIONS, scoringPolicyChanged,
  };

  // Only a real extension has a runtime id. A test harness that loads this
  // file gets the factory and nothing else.
  //
  // Injected twice into one frame — registered for new pages AND pushed into
  // tabs that were already open (background.js) — it starts once. A different
  // extension version (after an update, when the old script is orphaned)
  // starts afresh.
  if (globalThis.chrome?.runtime?.id) {
    const version = globalThis.chrome.runtime.getManifest?.().version ?? "";
    if (globalThis.__hisnScanVersion !== version) {
      globalThis.__hisnScanVersion = version;
      const scanner = createScanner();
      scanner.start();
      globalThis.chrome.storage?.onChanged?.addListener((changes, area) => {
        if (area === "local" && changes.state
            && scoringPolicyChanged(changes.state.oldValue, changes.state.newValue)) scanner.recheck();
      });
    }
  }
})();
