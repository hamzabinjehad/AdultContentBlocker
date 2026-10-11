// Deterministic granular-feed scanner regressions, no browser dependencies.
load("../content/feed.js");
load("../content/scan.js");
const F = globalThis.HisnFeed;
let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL ${label}`); } }
// Allow an entire bounded batch's promise chain to settle. Eight turns only
// settled the first few replies and made large fake-clock queues look stalled.
const flush = async () => { for (let i = 0; i < 64; i++) await Promise.resolve(); };
function clock() {
  let time = 10000, id = 0;
  const pending = new Map();
  const timers = {
    setTimeout(fn, ms) { pending.set(++id, { at: time + ms, fn }); return id; },
    clearTimeout(id) { pending.delete(id); },
    setInterval(fn, ms) { pending.set(++id, { at: time + ms, fn, interval: ms }); return id; },
    clearInterval(id) { pending.delete(id); },
  };
  return { timers, now: () => time, async advance(ms) {
    const target = time + ms;
    for (;;) {
      let next;
      for (const [id, timer] of pending) if (timer.at <= target && (!next || timer.at < next.timer.at)) next = { id, timer };
      if (!next) break;
      time = next.timer.at;
      if (next.timer.interval) next.timer.at += next.timer.interval; else pending.delete(next.id);
      next.timer.fn(); await flush();
    }
    time = target; await flush();
  } };
}
function style() {
  const values = new Map();
  return { getPropertyValue(k) { return values.get(k)?.value || ""; },
    getPropertyPriority(k) { return values.get(k)?.priority || ""; },
    setProperty(k, value, priority = "") { values.set(k, { value, priority }); },
    removeProperty(k) { values.delete(k); } };
}
function element(attrs = {}) {
  return { attrs: { ...attrs }, style: style(), isConnected: true,
    getAttribute(k) { return this.attrs[k] ?? null; },
    setAttribute(k, value) { this.attrs[k] = value; },
    removeAttribute(k) { delete this.attrs[k]; },
    remove() { this.isConnected = false; if (this.after?.previousSibling === this) this.after.previousSibling = null; },
    textContent: "", before(notice) { notice.after = this; notice.isConnected = true; this.previousSibling = notice; } };
}
function item(body = "Gardening notes", alt = []) {
  const article = element({ "data-testid": "tweet" });
  article.innerText = body;
  article.captions = alt.map((text) => Object.assign(element({ alt: text }), { parentElement: article }));
  article.link = element({ href: "/person/status/1" });
  article.media = { paused: false, pauses: 0, pause() { this.paused = true; this.pauses++; }, closest() { return article; } };
  article.querySelectorAll = (selector) => selector === "video, audio" ? [article.media] : article.captions;
  article.querySelector = () => article.link;
  return article;
}
function harness(items, verdict = (zones) => ({ block: zones.body.includes("fixtureadult") || zones.alt.includes("fixtureadult"), enabled: true })) {
  const c = clock(), messages = [], listeners = new Map();
  let mutation = () => {};
  const doc = { readyState: "complete", title: "Never scored as an item", items, documentElement: { lang: "en" },
    querySelectorAll() { return this.items; }, createElement() { return element(); },
    addEventListener(type, cb) { listeners.set(type, cb); }, removeEventListener(type) { listeners.delete(type); } };
  const win = { getComputedStyle(el) { return { display: el.display || "block", visibility: el.visibility || "visible" }; }, navigator: { language: "en" } };
  const loc = { protocol: "https:", hostname: "x.com", href: "https://x.com/home" };
  const deps = { doc, win, loc, timers: c.timers, now: c.now, isTop: true,
    observe(cb) { mutation = cb; return () => { mutation = () => {}; }; },
    send: async (zones) => { messages.push(zones); return verdict(zones, messages.length); } };
  const scanner = globalThis.HisnScan.createScanner(deps);
  return { scanner, doc, loc, messages, clock: c, mutate() { mutation(); }, play(media) { listeners.get("play")?.({ target: media }); } };
}
function hidden(article) { return article.style.getPropertyValue("opacity") === "0"; }

for (const hostname of ["x.com", "www.x.com", "mobile.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com", "X.COM."]) {
  check(F.isSupportedLocation({ protocol: "https:", hostname }), `${hostname} supports narrow tweet-article scanning`);
}
for (const hostname of ["x.com.evil.test", "notx.com", "cdn.x.com", "example.test", ""]) {
  check(!F.isSupportedLocation({ protocol: "https:", hostname }), `${hostname} does not claim feed coverage`);
}
check(!F.isSupportedLocation({ protocol: "file:", hostname: "x.com" }), "non-web scheme not supported");
check(F.signature({ body: "hello" }) !== F.signature({ body: "world" }), "equal-length post changes invalidate equality cache");
check(F.signature({ body: "same" }, "1") !== F.signature({ body: "same" }, "2"), "recycled identity changes invalidate cache");
check(F.boundedText({ secret: "do not coerce" }) === "", "non-string payload is not coerced");
const long = "h".repeat(F.MAX_ITEM_TEXT) + "tail";
check(F.boundedText(long).endsWith("tail") && F.boundedText(long).length === F.MAX_ITEM_TEXT + 1,
  "bounded samples retain both ends of long post text");

await (async () => {
  const safe = item(), bad = item("fixtureadult"), h = harness([safe, bad]);
  h.scanner.start();
  check(hidden(safe) && hidden(bad), "unjudged articles withheld synchronously before worker response");
  await flush();
  check(!hidden(safe) && hidden(bad), "one bad post hidden while ordinary sibling stays usable");
  check(bad.media.paused && bad.getAttribute("aria-hidden") === "true", "blocked media paused and inaccessible");
  check(bad.inert === true && safe.inert === false, "withheld article cannot receive keyboard focus; clean item inert restored");
  check(safe.getAttribute("aria-hidden") === null, "safe original accessibility restored");
  check(bad.previousSibling?.textContent.includes("Hisn hid this post"), "generic explanation has no reveal button or matched text");
  check(bad.style.getPropertyValue("max-height") === "0px", "blocked item collapses to notice instead of video-sized blank gap");
  check(h.messages.every((zones) => Object.keys(zones).sort().join(",") === "alt,body"), "only per-article body/caption zones leave scanner");
  check(h.messages.length === 2, "one message per changed item, not aggregate page");
  h.mutate(); await h.clock.advance(1000);
  check(h.messages.length === 2, "unchanged benign and blocked items reuse current verdict");
  bad.style.removeProperty("opacity"); h.mutate();
  check(hidden(bad), "page style removal cannot silently reveal blocked article");
  bad.inert = false; h.mutate();
  check(bad.inert === true, "page cannot restore keyboard access to withheld item");
  bad.media.paused = false; h.play(bad.media);
  check(bad.media.paused, "play event inside hidden article pauses media again");
  safe.innerText = "fixtureadult"; h.mutate();
  check(hidden(safe), "reused safe node withheld immediately on changed text");
  await flush(); check(hidden(safe), "changed item blocked without hiding unrelated website");
  bad.innerText = "New gardening note"; bad.link.attrs.href = "/person/status/2"; h.mutate(); await flush();
  check(!hidden(bad) && bad.previousSibling === null, "clean recycled article is released, obsolete notice removed");
  check(bad.style.getPropertyValue("max-height") === "", "recycled clean article restores original layout");
  h.scanner.stop(); check(!hidden(safe), "explicit scanner stop restores only owned styles");
})();

await (async () => {
  const posts = Array.from({ length: 120 }, (_, i) => item(`Ordinary item ${i}`));
  const h = harness(posts); h.scanner.start(); await flush();
  check(h.messages.length <= F.MAX_SCAN_ITEMS, "first batch bounds text extraction and worker queue on a large feed");
  await h.clock.advance(1000);
  check(h.messages.length === posts.length && posts.every((post) => !hidden(post)),
    "batched initial scan eventually reaches middle and tail posts without omitting content");
  h.scanner.stop();
})();

await (async () => {
  const posts = Array.from({ length: 700 }, (_, i) => item(`Ordinary item ${i}`));
  const h = harness(posts); h.scanner.start(); await h.clock.advance(2500);
  check(new Set(h.messages.map((zones) => zones.body)).size === posts.length,
    `large feed eventually checks all items including middle, not only sampled ends (${new Set(h.messages.map((zones) => zones.body)).size}/${posts.length})`);
  check(h.scanner.stats.cachedItems <= F.MAX_CACHED_ITEMS && h.scanner.stats.queuedText <= F.MAX_QUEUED_TEXT,
    "large feed bounds retained signatures and queued text independently of DOM length");
  h.scanner.stop();
})();

await (async () => {
  const answers = [], posts = Array.from({ length: 90 }, () => item());
  const h = harness(posts, (_, n) => n <= F.MAX_PARALLEL
    ? new Promise((resolve) => answers.push(resolve)) : { block: true, enabled: true });
  h.scanner.start(); await h.clock.advance(200);
  check(h.scanner.stats.queuedText <= F.MAX_QUEUED_TEXT, "hung worker cannot retain unlimited queued text");
  h.scanner.recheck();
  for (const answer of answers) answer({ block: false, enabled: true });
  await flush(); await h.clock.advance(1500);
  check(posts.every(hidden) && h.messages.length >= posts.length,
    "policy changes with a full queue replace stale payloads without deadlock or clean old verdicts");
  h.scanner.stop();
})();

await (async () => {
  const post = item("fixtureadult"); post.parentElement = { display: "none" };
  const h = harness([post]); h.scanner.start(); await flush();
  check(h.messages.length === 0 && !hidden(post), "ancestor-hidden article is not scored as rendered text");
  post.parentElement.display = "block"; h.mutate(); await flush();
  check(hidden(post), "article is judged once its author-hidden ancestor becomes visible");
})();

await (async () => {
  const post = item("Weather", Array.from({ length: 10000 }, (_, i) => i === 9999 ? "fixtureadult" : "ordinary"));
  const zones = F.collectItemZones(post, { getComputedStyle() { return { display: "block", visibility: "visible" }; } });
  check(zones.alt.split("\n").length === F.MAX_CAPTION_NODES && zones.alt.endsWith("fixtureadult"),
    "huge caption sets bounded before join and retain both ends");
})();

await (async () => {
  const caption = item("Weather", ["fixtureadult"]), hiddenCaption = item("Weather", ["fixtureadult"]);
  hiddenCaption.captions[0].hidden = true;
  const h = harness([caption, hiddenCaption]); h.scanner.start(); await flush();
  check(hidden(caption) && !hidden(hiddenCaption), "visible alt/aria content scored; author-hidden payload excluded");
  hiddenCaption.captions[0].hidden = false; h.mutate(); await flush();
  check(hidden(hiddenCaption), "caption visibility/attribute mutation triggers fresh item verdict");
})();

await (async () => {
  const caption = item("Today's weather"), safe = item("Gardening notes");
  caption.setAttribute("aria-label", "fixtureadult");
  safe.setAttribute("aria-label", "Video: planting tomatoes");
  const h = harness([caption, safe]); h.scanner.start(); await flush();
  check(hidden(caption) && !hidden(safe), "root article description is checked without blocking ordinary sibling video");
  check(caption.media.paused && caption.inert === true, "root-description verdict withholds and pauses this article's media");
  safe.media.paused = false; h.play(safe.media);
  check(!safe.media.paused, "ordinary root-labelled video can be played manually after clean verdict");
  caption.media.paused = false; h.play(caption.media);
  check(caption.media.paused, "play event in root-description-blocked article is paused again");
  check(h.messages[0].alt === "fixtureadult", "root description uses existing caption zone, not page or new payload fields");
  const requests = h.messages.length;
  caption.setAttribute("aria-label", "Video: weather forecast"); h.mutate(); await flush();
  check(!hidden(caption) && h.messages.length === requests + 1, "changing only root description invalidates blocked verdict");
  safe.setAttribute("aria-label", "fixtureadult"); h.mutate(); await flush();
  check(hidden(safe), "root-description mutation invalidates clean verdict while body is unchanged");
  h.scanner.stop();
})();

await (async () => {
  const post = item("Weather", Array.from({ length: 10000 }, (_, i) => i === 9999 ? "tail caption" : "ordinary"));
  post.setAttribute("aria-label", "root description");
  const win = { getComputedStyle() { return { display: "block", visibility: "visible" }; } };
  let zones = F.collectItemZones(post, win);
  check(zones.alt.split("\n").length === F.MAX_CAPTION_NODES
    && zones.alt.startsWith("root description\n") && zones.alt.endsWith("tail caption"),
    "root caption participates in bounded node budget without losing descendant tail sample");
  post.captions = [];
  post.setAttribute("aria-label", "head" + "x".repeat(10000) + "tail");
  zones = F.collectItemZones(post, win);
  check(zones.alt.length === 2001 && zones.alt.startsWith("head") && zones.alt.endsWith("tail"),
    "root description has the same bounded head/tail sample as other captions");
  post.attrs["aria-label"] = { toString() { throw new Error("must not coerce attributes"); } };
  check(F.collectItemZones(post, win).alt === "", "non-string root description is neither coerced nor transmitted");
})();

await (async () => {
  const post = item("Weather"); post.setAttribute("aria-label", "fixtureadult");
  post.parentElement = { display: "none" };
  const h = harness([post]); h.scanner.start(); await flush();
  check(h.messages.length === 0, "root description in an author-hidden ancestor is not scanned");
  post.parentElement.display = "block"; h.mutate(); await flush();
  check(hidden(post), "root description is judged when its article becomes visible");
  h.scanner.stop();
})();

await (async () => {
  let answer;
  const post = item("Weather"), h = harness([post], (_, n) => n === 1
    ? new Promise((resolve) => { answer = resolve; }) : { block: true, enabled: true });
  h.scanner.start(); await flush();
  post.setAttribute("aria-label", "fixtureadult"); answer({ block: false, enabled: true }); await flush();
  check(hidden(post) && h.messages.length === 2 && h.scanner.stats.stale === 1,
    "late clean verdict cannot release a changed root description before observer callback");
  h.scanner.stop();
})();

await (async () => {
  let answer;
  const post = item(), h = harness([post], (_, n) => n === 1 ? new Promise((resolve) => { answer = resolve; }) : { block: true, enabled: true });
  h.scanner.start(); await flush(); h.loc.href = "https://x.com/person/status/2";
  post.innerText = "fixtureadult"; answer({ block: false, enabled: true }); await flush();
  check(hidden(post) && h.messages.length === 2, "old-route clean verdict cannot reveal current bad article");
})();

await (async () => {
  const answers = [];
  const h = harness(Array.from({ length: 12 }, () => item()), () => new Promise((resolve) => answers.push(resolve)));
  h.scanner.start(); await flush();
  check(h.messages.length === F.MAX_PARALLEL, "large feed caps simultaneous worker requests");
  answers[0]({ block: false, enabled: true }); await flush();
  check(h.messages.length === F.MAX_PARALLEL + 1, "bounded queue advances remaining items as replies arrive");
  h.scanner.stop();
})();

await (async () => {
  let answer;
  const recycled = item("fixtureadult"), h = harness([recycled], (_, n) => n === 1 ? new Promise((resolve) => { answer = resolve; }) : { block: false, enabled: true });
  h.scanner.start(); await flush();
  recycled.innerText = "Now a safe post"; recycled.link.attrs.href = "/person/status/2";
  answer({ block: true, enabled: true }); await flush();
  check(!hidden(recycled) && h.messages.length === 2 && h.scanner.stats.stale === 1,
    "late blocked verdict never applies to recycled clean node even before observer callback");
})();

await (async () => {
  let answer;
  const old = item("fixtureadult"), fresh = item(), h = harness([old], (_, n) => n === 1 ? new Promise((resolve) => { answer = resolve; }) : { block: false, enabled: true });
  h.scanner.start(); await flush(); old.isConnected = false; h.doc.items = [fresh]; h.mutate();
  answer({ block: true, enabled: true }); await flush();
  check(!hidden(fresh) && !hidden(old), "detached node answer neither blocks replacement nor leaves owned styles on old node");
})();

await (async () => {
  let block = false;
  const post = item(), h = harness([post], () => ({ block, enabled: true }));
  h.scanner.start(); await flush(); block = true; h.scanner.recheck(); await flush();
  check(hidden(post) && h.messages.length === 2, "policy revision rescans identical formerly safe item");
  block = false; h.scanner.recheck(); await flush();
  check(!hidden(post), "permitted policy change clears old blocked item cache");
})();

await (async () => {
  let answer;
  const post = item(), h = harness([post], (_, n) => n === 1 ? new Promise((resolve) => { answer = resolve; }) : { block: true, enabled: true });
  h.scanner.start(); await flush(); h.scanner.recheck(); answer({ block: false, enabled: true }); await flush();
  check(hidden(post) && h.messages.length === 2, "old clean reply cannot release item after stricter policy change");
})();

await (async () => {
  const post = item(), h = harness([post], () => ({ block: false, enabled: false }));
  post.style.setProperty("opacity", ".8", "important"); post.attrs["aria-hidden"] = "false";
  h.scanner.start(); await flush();
  check(post.style.getPropertyValue("opacity") === ".8" && post.getAttribute("aria-hidden") === "false",
    "disabled/exempt scanning restores original styles and accessibility");
})();

await (async () => {
  const post = item(), h = harness([post], () => undefined);
  h.scanner.start(); await flush(); await h.clock.advance(25000);
  check(hidden(post) && h.messages.length === F.RETRY_LIMIT, "unavailable worker retries a bounded burst and withholds this item only");
  check(post.previousSibling?.textContent.includes("reconnects"), "exhausted retry status explains hidden item");
  await h.clock.advance(F.RECONNECT_MS);
  check(h.messages.length <= F.RETRY_LIMIT + 1, "persistent failure switches to slow bounded reconnect instead of busy-looping");
  post.innerText = "changed"; h.mutate(); await flush();
  check(h.messages.length === F.RETRY_LIMIT + 2, "fresh content re-arms exhausted retry budget");
})();

await (async () => {
  const post = item(), h = harness([post], (_, n) => n === 1 ? new Promise(() => {}) : { block: false, enabled: true });
  h.scanner.start(); await h.clock.advance(F.REPLY_TIMEOUT_MS + 1000);
  check(!hidden(post) && h.messages.length === 2, "hung reply times out and retries without freezing rest of feed");
})();

await (async () => {
  const h = harness([]); h.scanner.start(); await flush();
  check(h.messages.length === 0, "empty X shell does not fall back to whole-page scoring");
  for (let i = 0; i < 20; i++) { h.doc.items.push(item(i === 10 ? "fixtureadult" : `safe ${i}`)); h.mutate(); await h.clock.advance(100); }
  check(h.messages.length === 20 && hidden(h.doc.items[10]) && !hidden(h.doc.items[19]),
    "continuous infinite-scroll additions individually checked without debounce starvation");
})();

print(`${checks - failures}/${checks} granular feed scanner checks passed`);
if (failures) throw new Error(`${failures} granular feed checks failed`);
