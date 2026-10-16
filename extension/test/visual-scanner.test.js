// Canonical content script with deterministic DOM/transport lifecycle fixtures.
let checks = 0;
const check = (ok, label) => { checks++; if (!ok) throw new Error(label); };
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
function harness() {
  const values = new Map(), events = new Map(), documentEvents = new Map(), timers = new Map(), delays = new Map(), nodeEvents = new Map();
  const originalNow = Date.now;
  let now = 1000;
  Date.now = () => now;
  let inspect = [], stored = { inspectVisual: true, personBlur: 'off' }, change, tick, discover, id = 0, capture;
  const node = { tagName: 'VIDEO', currentSrc: 'https://fixture.example/a.webm', currentTime: 0, attributes: new Map(), attributeWrites: 0,
    getAttribute(name) { return this.attributes.get(name) ?? null; },
    setAttribute(name, value) { this.attributes.set(name, value); this.attributeWrites++; },
    removeAttribute(name) { this.attributes.delete(name); this.attributeWrites++; },
    videoWidth: 224, videoHeight: 224, isConnected: true, dataset: {}, paused: false,
    style: { getPropertyValue: key => values.get(key)?.[0] ?? '', getPropertyPriority: key => values.get(key)?.[1] ?? '',
      setProperty: (key, value, priority) => values.set(key, [value, priority]), removeProperty: key => values.delete(key) },
    getBoundingClientRect: () => ({ width: node.size ?? 224, height: node.size ?? 224, top: node.offscreen ? -400 : 0,
      bottom: node.offscreen ? -176 : node.size ?? 224, left: 0, right: node.size ?? 224 }),
    addEventListener: (event, fn) => events.set(event, fn), removeEventListener: event => events.delete(event),
    pause() { this.paused = true; },
  };
  nodeEvents.set(node, events);
  const nodes = [node];
  globalThis.location = { href: 'https://fixture.example/' };
  globalThis.innerHeight = globalThis.innerWidth = 800;
  globalThis.crypto = { randomUUID: () => String(++id) };
  globalThis.document = { hidden: false, documentElement: {}, querySelectorAll: () => nodes.filter(item => item.isConnected),
    addEventListener: (event, fn) => documentEvents.set(event, fn) };
  globalThis.addEventListener = (event, fn) => documentEvents.set(event, fn);
  globalThis.MutationObserver = class { constructor(fn) { discover = fn; } observe() {} disconnect() {} };
  globalThis.setInterval = fn => { tick = fn; return 1; };
  globalThis.clearInterval = () => {};
  globalThis.setTimeout = (fn, delay) => { timers.set(++id, fn); delays.set(id, delay); return id; };
  globalThis.clearTimeout = key => { timers.delete(key); delays.delete(key); };
  globalThis.HisnVisualCapture = { capture: item => typeof capture === 'function' ? capture(item) : capture ?? ({ width: 1, height: 1, rgba: 'AAAAAA==' }) };
  globalThis.chrome = { runtime: { sendMessage: async message => {
    if (message.type === 'getVisualSettings') return stored;
    if (message.type === 'cancelVisualMedia') return { state: 'unknown' };
    return new Promise((resolve, reject) => inspect.push({ message, resolve, reject }));
  } }, storage: { onChanged: { addListener: fn => { change = fn; } } } };
  delete globalThis.__hisnVisualScanner;
  load('../content/visual.js');
  return { node, events, documentEvents, inspect, values, nodes, discover: mutations => discover(mutations),
    addNode() {
      const properties = new Map(), listeners = new Map();
      const added = { ...node, dataset: {}, attributes: new Map(), attributeWrites: 0, currentSrc: `https://fixture.example/${nodes.length}.webm`,
        offscreen: false, paused: false,
        style: { getPropertyValue: key => properties.get(key)?.[0] ?? '', getPropertyPriority: key => properties.get(key)?.[1] ?? '',
          setProperty: (key, value, priority) => properties.set(key, [value, priority]), removeProperty: key => properties.delete(key) },
        getBoundingClientRect: () => ({ width: added.size ?? 224, height: added.size ?? 224, top: added.offscreen ? -400 : 0,
          bottom: added.offscreen ? -176 : added.size ?? 224, left: 0, right: added.size ?? 224 }),
        addEventListener: (event, fn) => listeners.set(event, fn), removeEventListener: event => listeners.delete(event),
      };
      nodes.push(added);
      nodeEvents.set(added, listeners);
      return { node: added, values: properties, events: listeners };
    },
    emit(event, item = node) { (nodeEvents.get(item)?.get(event) || documentEvents.get(event))?.({ type: event, target: item }); },
    advance(ms) { now += ms; },
    tick: async () => { tick(); await flush(); },
    pump: async () => {
      const key = [...timers.keys()].find(key => delays.get(key) === 0);
      if (key === undefined) return false;
      const fn = timers.get(key); timers.delete(key); delays.delete(key); fn(); await flush(); return true;
    },
    timeout: async () => {
      const key = [...timers.keys()].find(key => delays.get(key) === 20000);
      check(key !== undefined, 'an outstanding inspection has a bounded deadline');
      const fn = timers.get(key); timers.delete(key); delays.delete(key); fn(); await flush();
    },
    capture(next) { capture = next; },
    set(next) { stored = { ...stored, ...next }; change({ state: { newValue: stored } }, 'local'); },
    async resolve(index, adult = false) { inspect[index].resolve({ state: 'ready', adult: { block: adult }, persons: { blur: false } }); await flush(); },
    async reply(index, result) { inspect[index].resolve(result); await flush(); },
    async reject(index) { inspect[index].reject(new Error('fixture transport failure')); await flush(); },
    stop() { globalThis.__hisnVisualScanner.stop(); Date.now = originalNow; },
  };
}
{
  const h = harness(); await flush();
  globalThis.crypto = {getRandomValues: values => values.fill(7)};
  await h.tick();
  check(/^v_[a-f0-9]{32}$/.test(h.inspect[0].message.id), 'HTTP contexts without randomUUID can issue valid visual requests');
  await h.resolve(0); h.stop();
}
{
  const h = harness(); await flush(); await h.tick(); await h.resolve(0);
  h.node.currentTime = 2; await h.tick();
  check(h.inspect.length === 2 && h.node.dataset.hisnVisual === 'checked', 'periodic monitoring does not blank a previously checked same-source video');
  h.emit('playing'); await h.tick();
  check(h.inspect.length === 2 && h.node.dataset.hisnVisual === 'checked', 'buffering recovery neither cancels an in-flight recheck nor revokes a clean source');
  await h.resolve(1); h.emit('playing'); await h.tick();
  check(h.inspect.length === 2 && h.node.dataset.hisnVisual === 'checked' && !h.node.paused,
    'ordinary resumed playback remains available without redundant inference');
  h.node.currentTime = 4; await h.tick(); await h.resolve(2, true);
  check(h.node.dataset.hisnVisual === 'adult-hidden' && h.node.paused, 'a positive periodic sample still hides and pauses the source');
  h.node.paused = false; h.emit('playing');
  check(h.node.paused && h.node.dataset.hisnVisual === 'adult-hidden', 'playing cannot clear a later adult latch');
  h.stop();
}
{
  const h = harness(); await flush(); await h.tick();
  h.emit('playing'); await h.tick();
  check(h.inspect.length === 1 && h.node.dataset.hisnVisual === 'pending', 'playing cannot reveal or restart a pending first check');
  await h.resolve(0); h.emit('seeking');
  check(h.node.dataset.hisnVisual === 'pending', 'seeking still revokes a previous clean sample');
  await h.tick(); await h.resolve(1);
  h.node.currentSrc = 'https://fixture.example/new-playing-source.webm'; h.emit('playing');
  check(h.node.dataset.hisnVisual === 'pending', 'playing with a changed source requires a new hidden-first check');
  await h.tick(); await h.resolve(2);
  h.set({ personBlur: 'all' });
  check(h.node.dataset.hisnVisual === 'pending', 'changed visual policy requires an independent hidden-first check');
  await h.tick(); await h.resolve(3);
  h.stop();
}
{
  const h = harness(); await flush(); await h.tick(); await h.resolve(0);
  h.node.currentTime = 2; await h.tick(); await h.reply(1, {state: 'unknown', reason: 'inaccessible-pixels'});
  check(h.node.dataset.hisnVisual === 'unknown', 'an unknown periodic verdict revokes earlier clean permission');
  for (let i = 0; i < 5; i++) { h.emit('playing'); await h.tick(); }
  check(h.inspect.length === 2 && h.node.dataset.hisnVisual === 'unknown', 'playing cannot reveal unknown media or reset its retry backoff');
  h.advance(1000); await h.tick(); await h.resolve(2);
  check(h.node.dataset.hisnVisual === 'checked', 'a verified retry can recover ordinary playback after an unknown verdict');
  h.stop();
}
{
  const h = harness(); await flush();
  for (let i = 1; i < 50; i++) h.addNode();
  h.discover();
  for (let i = 0; i < 50; i++) {
    check(await h.pump(), 'ready queue schedules a browser-yielding immediate pass');
    check(h.inspect.length === i + 1, 'next image starts without a one-second interval tick');
    await h.resolve(i);
  }
  await h.pump();
  check(h.inspect.length === 50, 'draining 50 images does not rescan unchanged sources');
  h.stop();
  check(!await h.pump(), 'stop clears the scheduled queue pump');
}
{
  const h = harness(); await flush(); await h.tick(); await h.resolve(0, true);
  for (let i = 1; i < 128; i++) h.addNode();
  h.discover(); // Fill the active scan cache before a virtualized-feed eviction.
  h.node.offscreen = true;
  for (const node of h.nodes.slice(1)) node.offscreen = true;
  const next = h.addNode(); h.discover(); await h.tick();
  check(h.values.get('visibility')?.[0] === 'hidden', 'cache eviction never reveals offscreen known adult media');
  h.node.paused = false; h.emit('playing');
  check(h.node.paused, 'evicted known adult video still cannot start playing');
  check(h.inspect.length === 2 && next.events.size === 7, 'bounded feed cache admits newly visible media');
  await h.resolve(1);
  h.node.offscreen = false; h.discover(); await h.tick();
  check(h.inspect.length === 2 && h.values.get('visibility')?.[0] === 'hidden', 'returning flagged source reuses its latch without a clean-frame unhide');
  h.node.offscreen = true;
  h.set({ inspectVisual: false, personBlur: 'off' });
  check(!h.values.has('visibility') && h.events.size === 0, 'explicit opt-out restores a known source without retaining node listeners');
  h.stop();
}
{
  const h = harness(); h.values.set('visibility', ['collapse', 'important']);
  await flush(); await h.tick(); await h.resolve(0, true);
  for (let i = 1; i < 128; i++) h.addNode();
  h.discover(); h.node.offscreen = true; h.addNode(); h.discover();
  h.set({ inspectVisual: false, personBlur: 'off' });
  check(h.values.get('visibility')?.[0] === 'collapse' && h.values.get('visibility')?.[1] === 'important', 'opt-out enumerates offscreen suspended nodes and restores original visibility');
  check(h.events.size === 0, 'opt-out releases suspended node listeners');
  h.stop();
}
{
  const h = harness(); await flush(); await h.tick(); await h.resolve(0, true);
  for (let i = 1; i < 128; i++) h.addNode();
  h.discover(); h.node.offscreen = true; h.addNode(); h.discover();
  h.node.currentSrc = 'https://fixture.example/replacement.webm'; h.emit('loadeddata');
  check(!h.values.has('visibility') && h.events.size === 0, 'source replacement releases a suspended known-adult verdict and its listeners');
  h.stop();
}
{
  const h = harness(); await flush(); await h.tick(); await h.resolve(0, true);
  for (let i = 1; i < 128; i++) h.addNode();
  h.discover(); h.node.offscreen = true; h.addNode(); h.discover();
  h.node.isConnected = false; h.discover();
  h.set({ inspectVisual: false, personBlur: 'off' });
  h.node.isConnected = true; h.discover();
  check(!h.values.has('visibility') && h.events.size === 0, 'reinserted suspended node respects an opt-out made while it was detached');
  h.stop();
}
{
  const h = harness(); await flush();
  const extra = [];
  for (let i = 1; i < 140; i++) extra.push(h.addNode());
  h.discover();
  check(h.events.size + extra.reduce((count, item) => count + item.events.size, 0) === 128 * 7,
    'active scan cache installs listeners for at most 128 nodes');
  check(h.nodes.slice(128).every(node => node.dataset.hisnVisual === 'pending') && h.nodes[128].style.getPropertyValue('visibility') === '', 'later gated posts have an explicit pending state while awaiting an active slot');
  h.node.isConnected = false; h.discover();
  check(h.events.size === 0, 'detached active nodes release all node-owned listeners');
  h.stop();
}
{
  const h = harness(); await flush();
  for (let i = 1; i < 128; i++) h.addNode();
  h.discover();
  for (let i = 0; i < 128; i++) { await h.tick(); await h.resolve(i, true); }
  for (const node of h.nodes) node.offscreen = true;
  const safe = h.addNode(); h.discover(); await h.tick(); await h.resolve(128);
  check(h.inspect.length === 129 && safe.node.dataset.hisnVisual === 'checked',
    '128 offscreen adult verdicts do not starve a newly visible safe post');
  check(h.nodes.slice(0, 128).every(node => node.style.getPropertyValue('visibility') === 'hidden'),
    'all suspended adult verdicts remain withheld after active-cache replacement');
  h.set({ inspectVisual: false, personBlur: 'off' });
  check(h.nodes.slice(0, 128).every(node => node.style.getPropertyValue('visibility') === ''),
    'explicit opt-out restores every connected suspended verdict');
  h.stop();
}
{
  const h = harness(); await flush(); await h.tick();
  h.stop(); await h.resolve(0, true);
  h.set({ inspectVisual: true, personBlur: 'all' }); h.discover(); await h.tick();
  check(h.inspect.length === 1 && h.events.size === 0 && !h.values.has('visibility'), 'stop prevents late verdicts, settings callbacks, and queued discovery from reviving the scanner');
}
{
  const h = harness(); await flush(); await h.tick();
  check(h.inspect.length === 1, 'visible video sampled once');
  h.node.currentTime = 8;
  await h.resolve(0, true);
  check(h.values.get('visibility')?.[0] === 'hidden' && h.node.paused, 'slow inference still withholds flagged source');
  h.emit('seeking');
  check(h.values.get('visibility')?.[0] === 'hidden', 'seek never reveals known adult source');
  h.set({ personBlur: 'all' });
  check(h.values.get('visibility')?.[0] === 'hidden', 'stronger person preference never reveals adult source');
  await h.tick();
  check(h.inspect.length === 1, 'known adult source remains withheld without clean-frame unhide');
  h.node.currentSrc = 'https://fixture.example/b.webm'; h.emit('loadeddata');
  await h.tick(); await h.resolve(1);
  check(!h.values.has('visibility'), 'new source can receive independent clean verdict');
  h.set({ inspectVisual: false, personBlur: 'off' });
  check(!h.values.has('visibility'), 'explicit allowed opt-out restores owned styles');
  h.stop();
}
{
  const h = harness(); h.node.tagName = 'IMG'; h.node.naturalWidth = h.node.naturalHeight = 224;
  await flush(); await h.tick();
  document.hidden = true; h.documentEvents.get('visibilitychange')();
  await h.resolve(0);
  check(h.node.dataset.hisnVisual !== 'checked', 'cancelled hidden-tab result not applied');
  document.hidden = false; await h.tick();
  check(h.inspect.length === 2, 'focus resume rescans same pending image');
  await h.resolve(1, true);
  h.set({ lastHeartbeat: 1234 });
  check(h.values.get('visibility')?.[0] === 'hidden', 'unrelated heartbeat never reveals media');
  h.stop();
}
{
  const h = harness(); await flush();
  h.capture({ state: 'unknown', reason: 'media-not-ready' }); await h.tick();
  check(h.inspect.length === 0 && h.node.dataset.hisnVisual === 'unknown', 'unloaded media is unknown, not clean or blocked');
  h.capture(null); h.advance(1000); await h.tick(); await h.resolve(0);
  check(h.inspect.length === 1 && h.node.dataset.hisnVisual === 'checked', 'paused video retries readiness without needing playback or a load event');
  h.stop();
}
{
  const h = harness(); h.node.srcObject = {}; await flush(); await h.tick(); await h.resolve(0);
  h.node.srcObject = {}; await h.tick();
  check(h.inspect.length === 2, 'distinct MediaStream identities cannot reuse an unchanged string signature');
  await h.resolve(1); h.stop();
}
{
  const h = harness(); h.node.tagName = 'IMG'; h.node.naturalWidth = h.node.naturalHeight = 224;
  await flush(); await h.tick();
  h.node.currentSrc = 'https://fixture.example/picture-2x.png'; await h.resolve(0, true);
  check(!h.values.has('visibility'), 'late verdict for a replaced picture/srcset candidate is discarded');
  await h.tick(); await h.resolve(1);
  check(h.inspect.length === 2 && h.node.dataset.hisnVisual === 'checked', 'picture/srcset change is rechecked without a DOM mutation event');
  h.stop();
}
{
  const h = harness(); await flush(); await h.tick();
  h.emit('resize'); await h.resolve(0, true);
  check(!h.values.has('visibility'), 'late verdict invalidated after decoded video dimensions change');
  await h.tick(); await h.resolve(1);
  check(h.inspect.length === 2 && !h.node.paused, 'resized harmless video is available after an independent recheck');
  h.stop();
}
{
  const h = harness(); h.node.tagName = 'IMG'; h.node.naturalWidth = h.node.naturalHeight = 224;
  await flush(); await h.tick(); await h.resolve(0);
  await h.tick(); check(h.inspect.length === 1, 'unchanged images do not create an inference flood');
  h.advance(10000); await h.tick();
  check(h.node.dataset.hisnVisual === 'checked', 'periodic image monitoring also retains a checked same-source presentation while awaiting inference');
  await h.resolve(1, true);
  check(h.inspect.length === 2 && h.values.get('visibility')?.[0] === 'hidden',
    'unchanged-URL animation or refreshed image receives a bounded later sample');
  h.stop();
}
{
  const h = harness(); await flush(); h.capture({ state: 'unknown', reason: 'inaccessible-pixels' });
  await h.tick(); await h.tick();
  check(h.inspect.length === 0 && !h.values.has('visibility') && !h.node.paused,
    'unreadable protected or cross-origin video remains explicitly unknown without a false clean result or inference flood');
  h.stop();
}
{
  const h = harness(); await flush();
  const extra = [];
  for (let i = 1; i < 240; i++) extra.push(h.addNode());
  const listenerCount = () => h.events.size + extra.reduce((count, item) => count + item.events.size, 0);
  h.discover();
  check(listenerCount() === 128 * 7, 'a 240-item simultaneous gallery bounds active node listeners');
  for (let i = 0; i < 240; i++) {
    check(await h.pump() && h.inspect.length === i + 1, 'every simultaneously visible gallery item reaches inference');
    await h.resolve(i, i % 17 === 0);
    check(listenerCount() <= 128 * 7, 'settled gallery records release active listeners');
  }
  await h.pump();
  check(h.nodes.every((node, i) => node.dataset.hisnVisual === (i % 17 === 0 ? 'adult-hidden' : 'checked')),
    'all 240 visible items reach a verified checked or blocked state beyond the first 128');
  check(listenerCount() === 0 && h.inspect.length === 240, 'weak settled verdicts do not consume active slots or rescan unchanged samples');
  h.node.isConnected = false; h.discover();
  h.set({ personBlur: 'all' });
  h.node.isConnected = true; h.discover();
  check(h.node.dataset.hisnVisual === 'adult-hidden' && h.values.get('visibility')?.[0] === 'hidden',
    'a retired adult verdict survives detachment, a stronger policy and reinsertion');
  h.node.currentSrc = 'https://fixture.example/reinserted-new-source.webm';
  h.discover([{ type: 'attributes', attributeName: 'src', target: h.node }]);
  check(h.node.dataset.hisnVisual === 'pending' && !h.values.has('visibility'),
    'source replacement revokes a retained adult latch and requires an independent check');
  h.stop();
}
{
  const h = harness(); h.node.size = 16; await flush();
  const medium = h.addNode(); medium.node.size = 32;
  const large = h.addNode(); large.node.size = 48;
  const empty = h.addNode(); empty.node.size = 0;
  h.discover();
  for (let i = 0; i < 3; i++) { await h.pump(); await h.resolve(i, i === 1); }
  await h.pump();
  check(h.node.dataset.hisnVisual === 'checked' && medium.node.dataset.hisnVisual === 'adult-hidden'
    && large.node.dataset.hisnVisual === 'checked', '16/32/48px media is checked without a dimensions-based safety exemption');
  check(empty.node.dataset.hisnVisual === 'unknown' && empty.node.dataset.hisnVisualReason === 'zero-area'
    && h.inspect.length === 3, 'zero-area media has an explicit withheld unknown state');
  empty.node.size = 16; h.discover(); await h.pump(); await h.resolve(3);
  check(empty.node.dataset.hisnVisual === 'checked', 'zero-area media is admitted when it acquires a visible layout');
  h.stop();
}
{
  const h = harness(); h.node.tagName = 'IMG'; h.node.naturalWidth = h.node.naturalHeight = 224;
  await flush();
  for (let i = 1; i < 240; i++) h.addNode();
  h.discover();
  for (let i = 0; i < 240; i++) {
    await h.pump(); h.advance(11000); await h.resolve(i);
  }
  check(h.nodes.every(node => node.dataset.hisnVisual === 'checked'),
    'slow inference still completes every first check before due periodic resamples');
  await h.pump(); await h.resolve(240);
  check(h.inspect.length === 241, 'periodic resampling resumes after gallery first checks complete');
  h.stop();
}
{
  const h = harness(); await flush();
  for (let i = 1; i < 240; i++) h.addNode();
  let captures = 0;
  h.capture(() => { captures++; throw new Error('fixture canvas failure'); });
  h.discover(); await h.pump(); await h.pump(); await h.pump();
  check(captures === 240 && h.inspect.length === 0 && h.nodes.every(node => node.dataset.hisnVisual === 'unknown'
    && node.dataset.hisnVisualReason === 'capture-failed'), 'capture exceptions do not starve later items or produce a clean verdict');
  await h.tick(); check(captures === 240, 'failed capture retries wait for a bounded backoff rather than spinning the pump');
  h.capture(null); h.advance(1000); await h.tick(); await h.resolve(0);
  check(h.node.dataset.hisnVisual === 'checked', 'a transient capture failure can recover after its backoff');
  h.stop();
}
{
  const h = harness(); await flush(); const next = h.addNode(); h.discover(); await h.pump();
  await h.timeout();
  check(h.node.dataset.hisnVisual === 'unknown' && h.node.dataset.hisnVisualReason === 'timeout',
    'a content request timeout remains explicitly unknown and withheld');
  await h.pump(); await h.resolve(1);
  check(next.node.dataset.hisnVisual === 'checked', 'a timed-out item yields admission to the next supported item');
  await h.resolve(0);
  check(h.node.dataset.hisnVisual === 'unknown', 'a late reply cannot release timed-out media');
  h.advance(1000); await h.tick();
  await h.reply(2, { state: 'failed', reason: 'inference-timeout' });
  check(h.node.dataset.hisnVisualReason === 'inference-timeout', 'broker recovery failures retain a meaningful unknown reason');
  h.advance(1000); await h.tick();
  check(h.inspect.length === 3, 'repeated unknown results increase the retry delay');
  h.advance(1000); await h.tick(); await h.resolve(3);
  check(h.node.dataset.hisnVisual === 'checked', 'a broker recovery failure is retryable after the bounded delay');
  h.stop();
}
{
  const h = harness(); await flush(); await h.tick();
  h.set({ inspectVisual: false, personBlur: 'off' });
  await h.reject(0);
  check(!h.node.dataset.hisnVisual && !h.values.has('visibility'), 'a stale rejected request cannot reinstate withholding after an explicit opt-out');
  h.stop();
}
{
  const h = harness(); await flush(); await h.tick(); await h.resolve(0);
  delete h.node.dataset.hisnVisual; h.discover();
  check(h.node.dataset.hisnVisual === 'checked' && h.inspect.length === 1,
    'a valid weak verdict repairs a removed DOM marker without another inference');
  h.node.currentTime = 2; h.capture({ state: 'unknown', reason: 'inaccessible-pixels' }); await h.tick();
  h.node.dataset.hisnVisual = 'checked'; delete h.node.dataset.hisnVisualReason; h.discover();
  check(h.node.dataset.hisnVisual === 'unknown' && h.node.dataset.hisnVisualReason === 'inaccessible-pixels',
    'DOM presentation markers cannot overwrite a retained unknown verdict');
  h.stop();
}
{
  const h = harness(); h.node.tagName = 'IMG'; h.node.naturalWidth = h.node.naturalHeight = 224;
  h.capture({state:'unknown',reason:'inaccessible-pixels'});
  await flush(); await h.tick();
  check(h.node.getAttribute('crossorigin') === 'anonymous' && h.inspect.length === 0
    && h.node.dataset.hisnVisual !== 'checked', 'tainted image requests an actual hidden-first CORS reload, not a separate pixel verdict');
  h.discover([{type:'attributes',attributeName:'crossorigin',target:h.node}]); await h.tick();
  h.advance(1000); await h.tick(); await h.pump();
  check(h.node.attributeWrites === 1 && h.inspect.length === 0, 'own mutation, polling and backoff cannot repeatedly reload a tainted source');
  h.capture({width:1,height:1,rgba:'REAL'}); h.emit('load'); await h.tick();
  check(h.inspect.length === 1 && h.inspect[0].message.rgba === 'REAL', 'only a fresh actual-node capture reaches the model');
  await h.resolve(0);
  check(h.node.dataset.hisnVisual === 'checked' && h.node.getAttribute('crossorigin') === 'anonymous',
    'a verified clean result keeps the inspected CORS load, rather than reloading uninspected opaque pixels');
  h.stop();
  check(h.node.getAttribute('crossorigin') === null, 'stop restores the scanner-owned original image mode');
}
{
  const h = harness(); h.node.tagName = 'IMG'; h.node.naturalWidth = h.node.naturalHeight = 224;
  h.capture({state:'unknown',reason:'inaccessible-pixels'}); await flush(); await h.tick();
  h.set({inspectVisual:false,personBlur:'off'});
  check(h.node.getAttribute('crossorigin') === null && !h.node.dataset.hisnVisual,
    'explicit visual opt-out during reload restores only the owned mode and removes withholding');
  h.emit('load'); await h.tick();
  check(h.inspect.length === 0, 'late load after opt-out cannot revive inspection');
  h.stop();
}
{
  const h = harness(); h.node.tagName = 'IMG'; h.node.setAttribute('crossorigin','use-credentials');
  h.capture({state:'unknown',reason:'inaccessible-pixels'}); await flush(); await h.tick();
  check(h.node.getAttribute('crossorigin') === 'use-credentials' && h.node.attributeWrites === 1 && h.inspect.length === 0,
    'unreadable explicitly authored credential modes are never downgraded');
  h.stop(); check(h.node.getAttribute('crossorigin') === 'use-credentials', 'stop preserves author credential mode');
}
{
  const h = harness(); h.node.tagName = 'IMG';
  h.capture({state:'unknown',reason:'inaccessible-pixels'}); await flush(); await h.tick();
  h.discover([{type:'attributes',attributeName:'crossorigin',target:h.node}]);
  h.capture(null); h.emit('load'); await h.tick();
  h.node.setAttribute('crossorigin','use-credentials');
  await h.resolve(0);
  check(h.node.dataset.hisnVisual !== 'checked', 'exact CORS mode fence discards late verdict even before the author mutation observer runs');
  h.discover([{type:'attributes',attributeName:'crossorigin',target:h.node}]);
  h.stop(); check(h.node.getAttribute('crossorigin') === 'use-credentials', 'release never overwrites a later page-authored mode');
}
{
  const h = harness(); h.node.tagName = 'IMG';
  h.capture({state:'unknown',reason:'inaccessible-pixels'}); await flush(); await h.tick();
  h.discover([{type:'attributes',attributeName:'crossorigin',target:h.node}]);
  h.node.setAttribute('crossorigin','anonymous');
  h.discover([{type:'attributes',attributeName:'crossorigin',target:h.node}]);
  h.stop(); check(h.node.getAttribute('crossorigin') === 'anonymous', 'a later author write of the same attribute value also relinquishes scanner ownership');
}
{
  const h = harness(); h.node.tagName = 'IMG';
  h.capture({state:'unknown',reason:'inaccessible-pixels'}); await flush(); await h.tick();
  h.capture(null); h.emit('load'); await h.tick(); await h.resolve(0);
  h.node.isConnected = false;
  h.discover([{type:'childList',removedNodes:[h.node]}]);
  check(h.node.getAttribute('crossorigin') === null && h.node.dataset.hisnVisual !== 'checked',
    'detaching a settled image restores its authored mode and revokes its reveal');
  h.node.isConnected = true; h.capture({state:'unknown',reason:'inaccessible-pixels'});
  h.discover(); await h.tick();
  check(h.node.getAttribute('crossorigin') === 'anonymous' && h.inspect.length === 1,
    'reinsertion of the same source receives one fresh actual-image CORS reload');
  h.capture(null); h.emit('load'); await h.tick(); await h.resolve(1);
  check(h.node.dataset.hisnVisual === 'checked', 'virtualized ordinary image can recover again after reinsertion');
  h.stop();
}
{
  const h = harness(); h.node.tagName = 'IMG';
  h.capture({state:'unknown',reason:'inaccessible-pixels'}); await flush(); await h.tick();
  h.capture(null); h.emit('load'); await h.tick(); await h.resolve(0, true);
  h.node.isConnected = false; h.discover([{type:'childList',removedNodes:[h.node]}]);
  h.node.isConnected = true; h.discover(); await h.tick();
  check(h.node.dataset.hisnVisual === 'adult-hidden' && h.values.get('visibility')?.[0] === 'hidden'
    && h.inspect.length === 1, 'detaching and restoring CORS does not release a known adult-source latch');
  h.stop();
}
{
  const h = harness(); h.node.tagName = 'IMG'; await flush(); await h.tick();
  h.node.currentSrc = 'https://fixture.example/replaced-photo.png';
  await h.resolve(0);
  check(h.node.dataset.hisnVisual !== 'checked', 'source identity fence rejects a late clean image result');
  h.emit('load'); await h.tick(); await h.resolve(1, true);
  check(h.node.dataset.hisnVisual === 'adult-hidden', 'replacement source can independently receive an adult-positive verdict');
  h.stop();
}
{
  const h = harness(); h.node.tagName = 'IMG';
  h.capture({state:'unknown',reason:'inaccessible-pixels'}); await flush(); await h.tick();
  h.discover([{type:'attributes',attributeName:'crossorigin',target:h.node}]);
  h.node.removeAttribute('crossorigin');
  h.discover([{type:'attributes',attributeName:'crossorigin',target:h.node}]); await h.tick();
  check(h.node.getAttribute('crossorigin') === null && h.node.attributeWrites === 2 && h.inspect.length === 0,
    'a page removing the owned mode cannot cause repeated reload loops or a false clean verdict');
  h.stop();
}
{
  const h = harness(); h.node.tagName = 'IMG';
  for (let i=1;i<50;i++) h.addNode();
  h.capture({state:'unknown',reason:'inaccessible-pixels'}); await flush(); await h.tick();
  check(h.nodes.every(node=>node.getAttribute('crossorigin')==='anonymous') && h.inspect.length===0,
    'CORS preparation of 50 gallery images does not serialize one network wait per image');
  h.stop();
}
{
  const h = harness(); h.capture({state:'unknown',reason:'inaccessible-pixels'}); await flush(); await h.tick();
  check(h.node.attributeWrites===0 && h.node.dataset.hisnVisual==='unknown' && h.inspect.length===0,
    'unreadable videos are not mutated or falsely certified by the image retry');
  h.stop();
}
print(`  ${checks}/${checks} visual scanner lifecycle checks passed`);
