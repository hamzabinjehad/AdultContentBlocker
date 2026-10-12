(() => {
  if (globalThis.__hisnVisualScanner) return;
  const api = globalThis.browser ?? globalThis.chrome;
  if (!api?.runtime?.sendMessage || !globalThis.HisnVisualCapture) return;
  const media = new Map();
  // Offscreen adult verdicts belong to their DOM nodes, not the bounded active
  // scan cache. Weak keys and node-owned listeners do not retain removed posts.
  const withheld = new WeakMap();
  let prefs = { inspectVisual: false, personBlur: 'off' }, busy = false, cursor = 0, stopped = false;
  let lastRoute = location.href;
  const active = () => prefs.inspectVisual || prefs.personBlur !== 'off';
  const source = node => node.srcObject || node.currentSrc || node.src || '';
  const visible = node => {
    const rect = node.getBoundingClientRect();
    return rect.width >= 48 && rect.height >= 48 && rect.bottom >= 0 && rect.top <= innerHeight
      && rect.right >= 0 && rect.left <= innerWidth;
  };
  function restore(record) {
    const { node } = record;
    if (node.style.getPropertyValue('filter') === 'blur(24px)') {
      if (record.filter) node.style.setProperty('filter', record.filter, record.filterPriority);
      else node.style.removeProperty('filter');
    }
    if (node.style.getPropertyValue('visibility') === 'hidden' && record.hidden) {
      if (record.visibility) node.style.setProperty('visibility', record.visibility, record.visibilityPriority);
      else node.style.removeProperty('visibility');
    }
    delete node.dataset.hisnVisual;
    record.hidden = false;
  }
  function cancel(record) {
    record.generation++;
    if (record.request) {
      record.signature = null;
      api.runtime.sendMessage({ type: 'cancelVisualMedia', id: record.request }).catch(() => {});
    }
    record.request = null;
  }
  function invalidate(record) {
    cancel(record);
    // A seek or stronger person preference is not permission to reveal a
    // video already classified adult. Keep that source withheld until it is
    // replaced or adult filtering is deliberately disabled while unlocked.
    if (!(record.hidden && prefs.inspectVisual && record.blockedSource === source(record.node))) restore(record);
    record.source = source(record.node);
    record.signature = null;
    if (withheld.has(record.node) && !record.hidden) release(record);
  }
  function release(record) {
    cancel(record); restore(record);
    for (const [event, handler] of record.listeners) record.node.removeEventListener(event, handler);
    media.delete(record.node);
    withheld.delete(record.node);
  }
  function discover() {
    if (stopped) return;
    for (const [node, record] of media) if (!node.isConnected) {
      release(record);
    }
    const nodes = document.querySelectorAll('img,video');
    // WeakMap entries cannot be enumerated. Looking up the connected DOM nodes
    // restores suspended verdicts on explicit opt-out or source replacement,
    // including posts that are still offscreen or have just been reinserted.
    for (const node of nodes) {
      const record = withheld.get(node);
      if (record && (!prefs.inspectVisual || record.blockedSource !== source(node))) release(record);
    }
    if (!active() || document.hidden) return;
    // A long, virtualized feed must not fill the bounded cache with media the
    // user has scrolled past and starve newly visible posts.
    if (media.size >= 128) for (const record of media.values()) {
      if (!record.request && !visible(record.node)) {
        if (record.hidden && prefs.inspectVisual && record.blockedSource === source(record.node)) {
          media.delete(record.node);
          withheld.set(record.node, record);
        } else release(record);
      }
    }
    // Bound retained nodes. No URL cache or durable per-media results.
    for (const node of nodes) {
      if (media.size >= 128) break;
      if (media.has(node) || !visible(node)) continue;
      const suspended = withheld.get(node);
      if (suspended) {
        withheld.delete(node);
        media.set(node, suspended);
        continue;
      }
      const record = { node, source: source(node), generation: 0, signature: null, request: null,
        filter: node.style.getPropertyValue('filter'), filterPriority: node.style.getPropertyPriority('filter'),
        visibility: node.style.getPropertyValue('visibility'), visibilityPriority: node.style.getPropertyPriority('visibility'), hidden: false, blockedSource: null, listeners: [] };
      media.set(node, record);
      // Existing listener references die with the DOM node. Route/source/seek
      // changes invalidate late results rather than applying an old verdict.
      for (const event of ['load', 'loadeddata', 'loadedmetadata', 'resize', 'seeking', 'emptied']) {
        const handler = () => invalidate(record);
        record.listeners.push([event, handler]); node.addEventListener(event, handler);
      }
      const playing = () => {
        if (!record.hidden) return;
        if (prefs.inspectVisual && record.blockedSource === source(node)) node.pause();
        else invalidate(record);
      };
      record.listeners.push(['playing', playing]); node.addEventListener('playing', playing);
    }
  }
  async function pass() {
    if (stopped || busy || document.hidden || !active()) return;
    if (location.href !== lastRoute) {
      lastRoute = location.href;
      for (const record of media.values()) invalidate(record);
    }
    discover();
    const records = [...media.values()];
    for (let count = 0; count < records.length; count++) {
      const record = records[(cursor++) % records.length], node = record.node;
      if (!visible(node)) continue;
      // currentSrc covers <picture>/srcset choices; srcObject must be compared
      // by identity because distinct MediaStreams stringify identically.
      if (record.source !== source(node)) invalidate(record);
      const video = node.tagName === 'VIDEO';
      // Images can animate or be updated behind an unchanged URL. Periodic
      // bounded rechecks improve that coverage; they are not every-frame scans.
      const signature = `${source(node)}:${video ? Math.floor(node.currentTime / 2)
        : `${node.naturalWidth}:${node.naturalHeight}:${Math.floor(Date.now() / 10000)}`}`;
      if (record.hidden && prefs.inspectVisual && record.blockedSource === source(node)) {
        node.style.setProperty('visibility', 'hidden', 'important');
        if (video) node.pause();
        continue;
      }
      if (record.signature === signature) continue;
      const route = location.href, generation = record.generation, capturedSource = source(node);
      const capture = globalThis.HisnVisualCapture.capture(node);
      record.signature = signature;
      if (capture.state) {
        node.dataset.hisnVisual = 'unknown';
        // A player can become readable without moving to a new sample bucket
        // (paused videos and lazy images in particular). Retry transient load.
        if (capture.reason === 'media-not-ready') record.signature = null;
        continue;
      }
      busy = true;
      const id = `v_${crypto.randomUUID().replaceAll('-', '')}`;
      record.request = id;
      let timeout;
      try {
        const result = await Promise.race([
          api.runtime.sendMessage({ type: 'inspectVisualMedia', id, ...capture }),
          new Promise(resolve => { timeout = setTimeout(() => {
            api.runtime.sendMessage({ type: 'cancelVisualMedia', id }).catch(() => {});
            resolve({ state: 'unknown', reason: 'timeout' });
          }, 20000); }),
        ]);
        if (!node.isConnected || generation !== record.generation || location.href !== route
            || source(node) !== capturedSource || !active()) continue;
        // Normal playback may advance during inference. Seek/source/route
        // generations invalidate results; elapsed playback alone must not
        // discard every slower first inference.
        if (result?.state !== 'ready') {
          node.dataset.hisnVisual = 'unknown';
          record.signature = null; // retry on a later bounded pass
          continue;
        }
        restore(record);
        if (prefs.inspectVisual && result.adult?.block === true) {
          record.hidden = true;
          record.blockedSource = capturedSource;
          node.style.setProperty('visibility', 'hidden', 'important');
          node.dataset.hisnVisual = 'adult-hidden';
          if (video) node.pause();
        } else if (prefs.personBlur !== 'off' && result.persons?.blur === true) {
          node.style.setProperty('filter', 'blur(24px)', 'important');
          node.dataset.hisnVisual = 'person-blurred';
        } else node.dataset.hisnVisual = 'checked';
      } catch { record.signature = null; node.dataset.hisnVisual = 'unknown'; }
      finally {
        clearTimeout(timeout);
        capture.rgba = null;
        record.request = null;
        busy = false;
      }
      break; // one model request per tick, not a page-sized frame flood
    }
  }
  function update(next) {
    if (stopped) return;
    const normalized = { inspectVisual: next?.inspectVisual === true,
      personBlur: ['all', 'female', 'male'].includes(next?.personBlur) ? next.personBlur : 'off' };
    if (normalized.inspectVisual === prefs.inspectVisual && normalized.personBlur === prefs.personBlur) return;
    prefs = normalized;
    for (const record of media.values()) invalidate(record);
    discover();
  }
  api.runtime.sendMessage({ type: 'getVisualSettings' }).then(update).catch(() => {});
  api.storage?.onChanged?.addListener((changes, area) => {
    if (area === 'local' && changes.state?.newValue) update(changes.state.newValue);
  });
  const timer = setInterval(pass, 1000);
  const observer = new MutationObserver(discover);
  function observe() { if (!stopped && document.documentElement) observer.observe(document.documentElement, { childList: true, subtree: true }); }
  observe();
  document.addEventListener('DOMContentLoaded', observe, { once: true });
  document.addEventListener('visibilitychange', () => { if (document.hidden) for (const record of media.values()) cancel(record); });
  addEventListener('pagehide', () => { for (const record of media.values()) cancel(record); });
  globalThis.__hisnVisualScanner = { stop() {
    if (stopped) return;
    stopped = true;
    prefs = { inspectVisual: false, personBlur: 'off' };
    clearInterval(timer); observer.disconnect();
    for (const record of media.values()) release(record);
    for (const node of document.querySelectorAll('img,video')) {
      const record = withheld.get(node);
      if (record) release(record);
    }
    media.clear();
  } };
})();
