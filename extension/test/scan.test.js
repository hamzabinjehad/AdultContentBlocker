/**
 * The page scanner, driven with a fake DOM and a fake clock.
 *
 * `content/scan.js` is a content script and cannot be imported, so it puts its
 * factory on `globalThis.HisnScan` and this file `load()`s it. Every scenario
 * here is one of the ways the scanner used to miss a page (see the header of
 * scan.js): an equal-length replacement, a feed that never stops mutating, a
 * malformed URL, a worker that does not answer, a subframe. `test/browser/`
 * runs the same scenarios against a real DOM; this file is the deterministic,
 * millisecond-exact version that runs in CI on any machine with `jsc`.
 */

load("../content/scan.js");
const S = globalThis.HisnScan;

let failures = 0, checks = 0;
function check(ok, label) {
  checks++;
  if (!ok) { failures++; print(`  FAIL  ${label}`); }
}

// ── fake clock ─────────────────────────────────────────────────────────────
function fakeClock() {
  let now = 1_000_000;
  let nextId = 1;
  const pending = new Map();   // id -> {at, fn, every}
  const timers = {
    setTimeout(fn, ms) { const id = nextId++; pending.set(id, { at: now + ms, fn }); return id; },
    clearTimeout(id) { pending.delete(id); },
    setInterval(fn, ms) { const id = nextId++; pending.set(id, { at: now + ms, fn, every: ms }); return id; },
  };
  const yieldMacrotask = () => new Promise((r) => setTimeout(r, 0));
  return {
    timers,
    now: () => now,
    /** Advance virtual time, running due timers in order and letting promise
     *  chains settle between them, so an `await send()` completes before the
     *  next timer fires — exactly as a browser would interleave them. */
    async advance(ms) {
      const target = now + ms;
      for (;;) {
        let next = null;
        for (const [id, t] of pending) if (t.at <= target && (!next || t.at < next.t.at)) next = { id, t };
        if (!next) break;
        now = Math.max(now, next.t.at);
        if (next.t.every) next.t.at = now + next.t.every; else pending.delete(next.id);
        next.t.fn();
        await yieldMacrotask();
        await yieldMacrotask();
      }
      now = target;
      await yieldMacrotask();
    },
    get pendingCount() { return pending.size; },
  };
}

// ── fake DOM: only what collectZones touches ───────────────────────────────
function fakeDoc({ title = "", body = "", meta = [], headings = [], alt = [] } = {}) {
  const el = (attrs, text = "") => ({
    getAttribute: (k) => attrs[k] ?? null, textContent: text,
  });
  const doc = {
    title, readyState: "complete",
    body: { innerText: body },
    documentElement: { style: {} },
    addEventListener() {},
    querySelectorAll(sel) {
      if (sel.startsWith("meta")) return meta.map((c) => el({ content: c }));
      if (sel === "h1, h2") return headings.map((h) => el({}, h));
      return alt.map((a) => el({ alt: a }));
    },
  };
  return doc;
}

/** jsc has no URL class; split a URL the way `location` exposes it. */
function splitHref(href) {
  const m = /^[a-z]+:\/\/[^/]+(\/[^?#]*)?(\?[^#]*)?/.exec(href);
  return { href, pathname: m?.[1] ?? "/", search: m?.[2] ?? "" };
}

function harness({ doc, verdicts, href = "https://example.test/page", isTop = true } = {}) {
  const clock = fakeClock();
  const log = { sent: [], navigated: [], hidden: 0 };
  let mutate = () => {};
  const scanner = S.createScanner({
    doc, win: { addEventListener() {} }, loc: splitHref(href),
    isTop, timers: clock.timers, now: clock.now,
    send: async (zones) => { log.sent.push(zones); return verdicts(zones, log.sent.length); },
    navigate: (url) => log.navigated.push(url),
    hide: () => { log.hidden++; },
    blockPageURL: () => "chrome-extension://x/blocked.html?reason=terms",
    observe: (cb) => { mutate = cb; return () => { mutate = () => {}; }; },
  });
  return { clock, log, scanner, mutate: () => mutate(), doc };
}

const clean = () => ({ block: false });
const dirtyIf = (word) => (zones) => ({ block: zones.body.includes(word) });

// ── pure helpers ───────────────────────────────────────────────────────────
{
  check(S.safeDecode("%E0%A4%A") === "%E0%A4%A", "a malformed escape is returned as typed, not thrown");
  check(S.safeDecode("/caf%C3%A9") === "/café", "a valid escape still decodes");
  const a = S.contentSignature({ title: "t", body: "hello world" });
  const b = S.contentSignature({ title: "t", body: "world hello" });
  check(a !== b, "equal-length, different content gives a different signature");
  check(S.contentSignature({ title: "t", body: "x" }) === S.contentSignature({ title: "t", body: "x" }),
        "the signature is stable");
  check(S.contentSignature({ url: "/a", body: "x" }) !== S.contentSignature({ url: "/b", body: "x" }),
        "the URL is part of the signature, so an SPA route change is a new page");
  check(S.retryDelayMs(0) === 500 && S.retryDelayMs(1) === 1000 && S.retryDelayMs(4) === 8000
        && S.retryDelayMs(99) === 8000, "backoff doubles from 0.5s and caps at 8s");
}

// ── scheduler: debounce with a ceiling ─────────────────────────────────────
await (async () => {
  const clock = fakeClock();
  let fired = 0;
  const sch = S.createScheduler(() => fired++, { setTimeout: clock.timers.setTimeout,
                                                 clearTimeout: clock.timers.clearTimeout, now: clock.now });
  sch.request();
  await clock.advance(S.SETTLE_MS - 1);
  check(fired === 0, "not before the settle window");
  await clock.advance(1);
  check(fired === 1, "fires when the page settles");

  // A page that never settles: request every 500ms for 20s.
  fired = 0;
  for (let t = 0; t < 20_000; t += 500) { sch.request(); await clock.advance(500); }
  check(fired >= 3 && fired <= 5,
        `THE regression: a page mutating every 500ms is still scanned about every MAX_WAIT (fired ${fired} times in 20s)`);
})();

// ── 1. equal-length replacement ────────────────────────────────────────────
await (async () => {
  const doc = fakeDoc({ title: "Feed", body: "a perfectly ordinary post about gardening." });
  const h = harness({ doc, verdicts: dirtyIf("xxxpornxxx") });
  h.scanner.start();
  await h.clock.advance(10);
  check(h.log.sent.length === 1 && h.log.navigated.length === 0, "first pass scores the clean page");

  // Same length, different content — exactly what `title + body.length` missed.
  const before = doc.body.innerText.length;
  doc.body.innerText = "a perfectly ordinary post about xxxpornxxx";
  check(doc.body.innerText.length === before, "precondition: same length");
  h.mutate();
  await h.clock.advance(S.SETTLE_MS + 10);
  check(h.log.sent.length === 2, "THE regression: an equal-length replacement is re-scored");
  check(h.log.navigated.length === 1 && h.log.navigated[0].includes("reason=terms"),
        "and blocked, by navigating to the block page");
  check(h.scanner.stopped, "the scanner stops after blocking");
  h.mutate();
  await h.clock.advance(S.MAX_WAIT_MS + 10);
  check(h.log.sent.length === 2, "nothing is sent after a block");
})();

// ── 2. infinite-scroll feed ────────────────────────────────────────────────
await (async () => {
  const doc = fakeDoc({ title: "Feed", body: "post 0" });
  let n = 0;
  const h = harness({ doc, verdicts: dirtyIf("xxxpornxxx") });
  h.scanner.start();
  await h.clock.advance(10);
  // Append a post every 300ms — the settle window never opens.
  for (let t = 0; t < 12_000; t += 300) {
    doc.body.innerText += `\npost ${++n}`;
    h.mutate();
    await h.clock.advance(300);
  }
  check(h.log.sent.length >= 3,
        `THE regression: a feed that never settles is still scanned every MAX_WAIT (${h.log.sent.length} scans in 12s)`);
  check(h.log.sent.length <= 5, `and not on every batch (${h.log.sent.length} scans in 12s)`);

  // The explicit post lands mid-stream; the next ceiling scan must catch it.
  doc.body.innerText += "\nxxxpornxxx";
  h.mutate();
  for (let t = 0; t < S.MAX_WAIT_MS + 300 && !h.scanner.stopped; t += 300) {
    doc.body.innerText += `\npost ${++n}`;
    h.mutate();
    await h.clock.advance(300);
  }
  check(h.log.navigated.length === 1, "a bad post in a live feed is blocked within MAX_WAIT");
})();

// ── 3. malformed URL ───────────────────────────────────────────────────────
await (async () => {
  const doc = fakeDoc({ title: "t", body: "xxxpornxxx" });
  const h = harness({ doc, verdicts: dirtyIf("xxxpornxxx"),
                      href: "https://example.test/watch?q=%E0%A4%A" });
  h.scanner.start();
  await h.clock.advance(10);
  check(h.log.sent.length === 1, "THE regression: a malformed escape in the URL no longer aborts the scan");
  check(h.log.sent[0].url === "/watch?q=%E0%A4%A", "the raw URL is scored instead");
  check(h.log.navigated.length === 1, "and the page is blocked");
})();

// ── 4. worker restarts ─────────────────────────────────────────────────────
await (async () => {
  const doc = fakeDoc({ title: "t", body: "xxxpornxxx" });
  let calls = 0;
  const h = harness({ doc, verdicts: () => {
    calls++;
    if (calls === 1) throw new Error("Could not establish connection");   // worker asleep
    if (calls === 2) return undefined;                                     // torn down mid-reply
    return { block: true };
  } });
  h.scanner.start();
  await h.clock.advance(10);
  check(h.log.sent.length === 1 && h.log.navigated.length === 0, "first ask fails; nothing blocked yet");
  await h.clock.advance(S.retryDelayMs(0) + 10);
  check(h.log.sent.length === 2, "THE regression: a failed ask is retried, not recorded as scanned");
  check(h.log.navigated.length === 0, "an undefined reply is a failure, not a clean verdict");
  await h.clock.advance(S.retryDelayMs(1) + 10);
  check(h.log.sent.length === 3 && h.log.navigated.length === 1, "third ask answers, page blocked");
  const st = h.scanner.stats;
  check(st.failures === 2 && st.verdicts === 1, `stats: ${JSON.stringify(st)}`);
})();

// ── 4b. a permanently dead worker gives up on THIS content, retries on new ─
await (async () => {
  const doc = fakeDoc({ title: "t", body: "some page" });
  let calls = 0;
  const h = harness({ doc, verdicts: () => { calls++; throw new Error("dead"); } });
  h.scanner.start();
  await h.clock.advance(60_000);
  check(calls === S.MAX_RETRIES + 1, `bounded: ${calls} asks for unchanging content, then stops (max ${S.MAX_RETRIES + 1})`);
  doc.body.innerText = "some other page";
  h.mutate();
  await h.clock.advance(S.MAX_WAIT_MS + 10);
  check(calls >= S.MAX_RETRIES + 2, `new content gets a fresh ask and a fresh retry budget (${calls} asks)`);
})();

// ── 4c. a change that lands while a request is in flight is not lost ───────
await (async () => {
  const doc = fakeDoc({ title: "t", body: "clean" });
  let release;
  const h = harness({ doc, verdicts: (zones, n) =>
    n === 1 ? new Promise((r) => { release = () => r({ block: false }); })
            : { block: zones.body.includes("xxxpornxxx") } });
  h.scanner.start();
  await h.clock.advance(10);
  doc.body.innerText = "xxxpornxxx";
  h.mutate();                       // arrives while ask #1 is still pending
  await h.clock.advance(S.MAX_WAIT_MS + 10);
  check(h.log.sent.length === 1, "no second ask while the first is in flight");
  release();
  await new Promise((r) => setTimeout(r, 0));   // let the reply land
  await h.clock.advance(S.SETTLE_MS + 10);
  check(h.log.sent.length === 2 && h.log.navigated.length === 1,
        "the change that arrived mid-flight is scored once the reply lands");
})();

// ── 5. iframe content ──────────────────────────────────────────────────────
await (async () => {
  const doc = fakeDoc({ title: "player", body: "xxxpornxxx" });
  const h = harness({ doc, verdicts: dirtyIf("xxxpornxxx"), isTop: false });
  h.scanner.start();
  await h.clock.advance(10);
  check(h.log.hidden === 1, "a blocked subframe hides itself");
  check(h.log.navigated.length === 0, "and never navigates the tab");
})();

// ── SPA route change ───────────────────────────────────────────────────────
await (async () => {
  const doc = fakeDoc({ title: "app", body: "xxxpornxxx" });
  const href = "https://example.test/home";
  const h = harness({ doc, verdicts: (z) => ({ block: z.url.includes("/naughty") }), href });
  const loc = { href, pathname: "/home", search: "" };
  // Rebuild with a mutable location the test can move.
  const clock = fakeClock(); const log = { sent: [], navigated: [] };
  const sc = S.createScanner({
    doc, win: { addEventListener() {} }, loc, isTop: true, timers: clock.timers, now: clock.now,
    send: async (z) => { log.sent.push(z); return { block: z.url.includes("/naughty") }; },
    navigate: (u) => log.navigated.push(u), hide() {}, blockPageURL: () => "b",
    observe: () => () => {},
  });
  sc.start();
  await clock.advance(10);
  check(log.sent.length === 1 && log.navigated.length === 0, "home route scored clean");
  loc.href = "https://example.test/naughty"; loc.pathname = "/naughty";
  await clock.advance(S.HREF_POLL_MS + 10);
  check(log.sent.length === 2 && log.navigated.length === 1, "a route change with unchanged DOM is a new page");
  void h;
})();

// ── stale verdict after an SPA navigation ──────────────────────────────────
await (async () => {
  const doc = fakeDoc({ title: "app", body: "xxxpornxxx" });
  const loc = { href: "https://example.test/bad", pathname: "/bad", search: "" };
  const clock = fakeClock(); const log = { sent: [], navigated: [] };
  let release;
  const sc = S.createScanner({
    doc, win: { addEventListener() {} }, loc, isTop: true, timers: clock.timers, now: clock.now,
    send: (z) => { log.sent.push(z);
                   return log.sent.length === 1
                     ? new Promise((r) => { release = () => r({ block: true }); })  // slow verdict for /bad
                     : Promise.resolve({ block: z.body.includes("xxxpornxxx") }); },
    navigate: (u) => log.navigated.push(u), hide() {}, blockPageURL: () => "b",
    observe: () => () => {},
  });
  sc.start();
  await clock.advance(10);
  // The app routes to a clean page while the verdict for /bad is in flight.
  doc.body.innerText = "a clean page";
  loc.href = "https://example.test/clean"; loc.pathname = "/clean";
  release();
  await new Promise((r) => setTimeout(r, 0));
  await clock.advance(10);
  check(log.navigated.length === 0,
        "THE regression: a verdict for the previous route does not block the new one");
  check(log.sent.length === 2 && log.sent[1].url === "/clean",
        "the new route is scored on its own text");
  check(sc.stats.stale === 1, `the stale verdict was counted (${JSON.stringify(sc.stats)})`);
})();

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} scanner checks failed`);
