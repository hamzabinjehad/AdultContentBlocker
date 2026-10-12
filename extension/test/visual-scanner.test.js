// Canonical content script with deterministic DOM/transport lifecycle fixtures.
let checks = 0;
const check = (ok, label) => { checks++; if (!ok) throw new Error(label); };
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
function harness() {
  const values = new Map(), events = new Map(), documentEvents = new Map(), timers = new Map();
  let inspect = [], stored = { inspectVisual: true, personBlur: 'off' }, change, tick, discover, id = 0, capture;
  const node = { tagName: 'VIDEO', currentSrc: 'https://fixture.example/a.webm', currentTime: 0,
    videoWidth: 224, videoHeight: 224, isConnected: true, dataset: {}, paused: false,
    style: { getPropertyValue: key => values.get(key)?.[0] ?? '', getPropertyPriority: key => values.get(key)?.[1] ?? '',
      setProperty: (key, value, priority) => values.set(key, [value, priority]), removeProperty: key => values.delete(key) },
    getBoundingClientRect: () => ({ width: 224, height: 224, top: node.offscreen ? -400 : 0,
      bottom: node.offscreen ? -176 : 224, left: 0, right: 224 }),
    addEventListener: (event, fn) => events.set(event, fn), removeEventListener: event => events.delete(event),
    pause() { this.paused = true; },
  };
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
  globalThis.setTimeout = fn => { timers.set(++id, fn); return id; };
  globalThis.clearTimeout = key => timers.delete(key);
  globalThis.HisnVisualCapture = { capture: () => capture ?? ({ width: 1, height: 1, rgba: 'AAAAAA==' }) };
  globalThis.chrome = { runtime: { sendMessage: async message => {
    if (message.type === 'getVisualSettings') return stored;
    if (message.type === 'cancelVisualMedia') return { state: 'unknown' };
    return new Promise(resolve => inspect.push({ message, resolve }));
  } }, storage: { onChanged: { addListener: fn => { change = fn; } } } };
  delete globalThis.__hisnVisualScanner;
  load('../content/visual.js');
  return { node, events, documentEvents, inspect, values, nodes, discover: () => discover(),
    addNode() {
      const properties = new Map(), listeners = new Map();
      const added = { ...node, dataset: {}, currentSrc: `https://fixture.example/${nodes.length}.webm`,
        offscreen: false, paused: false,
        style: { getPropertyValue: key => properties.get(key)?.[0] ?? '', getPropertyPriority: key => properties.get(key)?.[1] ?? '',
          setProperty: (key, value, priority) => properties.set(key, [value, priority]), removeProperty: key => properties.delete(key) },
        getBoundingClientRect: () => ({ width: 224, height: 224, top: added.offscreen ? -400 : 0,
          bottom: added.offscreen ? -176 : 224, left: 0, right: 224 }),
        addEventListener: (event, fn) => listeners.set(event, fn), removeEventListener: event => listeners.delete(event),
      };
      nodes.push(added);
      return { node: added, values: properties, events: listeners };
    },
    tick: async () => { tick(); await flush(); },
    capture(next) { capture = next; },
    set(next) { stored = { ...stored, ...next }; change({ state: { newValue: stored } }, 'local'); },
    async resolve(index, adult = false) { inspect[index].resolve({ state: 'ready', adult: { block: adult }, persons: { blur: false } }); await flush(); },
    stop() { globalThis.__hisnVisualScanner.stop(); },
  };
}
{
  const h = harness(); await flush(); await h.tick(); await h.resolve(0, true);
  for (let i = 1; i < 128; i++) h.addNode();
  h.discover(); // Fill the active scan cache before a virtualized-feed eviction.
  h.node.offscreen = true;
  for (const node of h.nodes.slice(1)) node.offscreen = true;
  const next = h.addNode(); h.discover(); await h.tick();
  check(h.values.get('visibility')?.[0] === 'hidden', 'cache eviction never reveals offscreen known adult media');
  h.node.paused = false; h.events.get('playing')();
  check(h.node.paused, 'evicted known adult video still cannot start playing');
  check(h.inspect.length === 2 && next.events.size === 7, 'bounded feed cache admits newly visible media');
  await h.resolve(1);
  h.node.offscreen = false; h.discover(); await h.tick();
  check(h.inspect.length === 2 && h.values.get('visibility')?.[0] === 'hidden', 'returning flagged source reuses its latch without a clean-frame unhide');
  h.node.offscreen = true;
  h.set({ inspectVisual: false, personBlur: 'off' });
  check(!h.values.has('visibility') && h.events.size === 7, 'explicit opt-out restores a known source still held in active cache');
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
  h.node.currentSrc = 'https://fixture.example/replacement.webm'; h.events.get('loadeddata')();
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
  check(h.nodes.slice(128).every(node => !node.dataset.hisnVisual) && h.nodes[128].style.getPropertyValue('visibility') === '', 'cache limit never hides unclassified later posts');
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
  h.events.get('seeking')();
  check(h.values.get('visibility')?.[0] === 'hidden', 'seek never reveals known adult source');
  h.set({ personBlur: 'all' });
  check(h.values.get('visibility')?.[0] === 'hidden', 'stronger person preference never reveals adult source');
  await h.tick();
  check(h.inspect.length === 1, 'known adult source remains withheld without clean-frame unhide');
  h.node.currentSrc = 'https://fixture.example/b.webm'; h.events.get('loadeddata')();
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
  h.capture(null); await h.tick(); await h.resolve(0);
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
  h.events.get('resize')(); await h.resolve(0, true);
  check(!h.values.has('visibility'), 'late verdict invalidated after decoded video dimensions change');
  await h.tick(); await h.resolve(1);
  check(h.inspect.length === 2 && !h.node.paused, 'resized harmless video is available after an independent recheck');
  h.stop();
}
{
  const originalNow = Date.now;
  let now = 1000; Date.now = () => now;
  try {
    const h = harness(); h.node.tagName = 'IMG'; h.node.naturalWidth = h.node.naturalHeight = 224;
    await flush(); await h.tick(); await h.resolve(0);
    await h.tick(); check(h.inspect.length === 1, 'unchanged images do not create an inference flood');
    now = 11000; await h.tick(); await h.resolve(1, true);
    check(h.inspect.length === 2 && h.values.get('visibility')?.[0] === 'hidden',
      'unchanged-URL animation or refreshed image receives a bounded later sample');
    h.stop();
  } finally { Date.now = originalNow; }
}
{
  const h = harness(); await flush(); h.capture({ state: 'unknown', reason: 'inaccessible-pixels' });
  await h.tick(); await h.tick();
  check(h.inspect.length === 0 && !h.values.has('visibility') && !h.node.paused,
    'unreadable protected or cross-origin video remains unknown without blanket hiding or inference flood');
  h.stop();
}
print(`  ${checks}/${checks} visual scanner lifecycle checks passed`);
