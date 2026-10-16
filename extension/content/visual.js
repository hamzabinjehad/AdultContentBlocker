(() => {
  if (globalThis.__hisnVisualScanner) return;
  const api = globalThis.browser ?? globalThis.chrome;
  if (!api?.runtime?.sendMessage || !globalThis.HisnVisualCapture) return;
  // A fresh injection must not reuse DOM reveal markers from an old scanner.
  document.documentElement?.setAttribute?.('data-hisn-visual-gate','pending');
  document.documentElement?.setAttribute?.('data-hisn-visual-images','pending');
  document.documentElement?.setAttribute?.('data-hisn-visual-videos','pending');
  for (const node of document.querySelectorAll('img,video')) delete node.dataset.hisnVisual;
  const media = new Map();
  // Completed verdicts belong to their DOM nodes, independently of the 128
  // active preparation slots. Weak keys do not retain old gallery/feed nodes.
  const retained = new WeakMap();
  let prefs = { inspectVisual: false, personBlur: 'off' }, busy = false, stopped = false, policyRevision = 0, admissionCursor = 0;
  let pumpTimer;
  let requestSequence = 0;
  function requestId() {
    try {
      if (typeof crypto.randomUUID === 'function') return `v_${crypto.randomUUID().replaceAll('-', '')}`;
      if (typeof crypto.getRandomValues === 'function') return `v_${Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2,'0')).join('')}`;
    } catch { /* These are correlation IDs, not credentials. */ }
    return `v_${Date.now()}_${++requestSequence}`;
  }
  let preferencesReady = false, preferenceRequest = false;
  function queuePass() {
    if (stopped || pumpTimer !== undefined) return;
    // Yield to the browser, but do not add a one-second wait per ready image.
    pumpTimer = setTimeout(() => { pumpTimer = undefined; void pass(); }, 0);
  }
  const active = () => prefs.inspectVisual || prefs.personBlur !== 'off';
  const personApplies = node => prefs.personBlur !== 'off' && (prefs.personMedia === 'both'
    || prefs.personMedia === (node.tagName === 'VIDEO' ? 'videos' : 'images'));
  const applies = node => prefs.inspectVisual || personApplies(node);
  const presentation = () => JSON.stringify([prefs.personRegion,prefs.personEffect,prefs.personMedia]);
  function applyGate() {
    const root=document.documentElement;
    root?.setAttribute?.('data-hisn-visual-gate',active()?'on':'off');
    root?.setAttribute?.('data-hisn-visual-images',applies({tagName:'IMG'})?'on':'off');
    root?.setAttribute?.('data-hisn-visual-videos',applies({tagName:'VIDEO'})?'on':'off');
  }
  const source = node => node.srcObject || node.currentSrc || node.src || '';
  const corsMode = node => node.tagName === 'IMG' ? node.getAttribute?.('crossorigin') ?? null : null;
  const held = record => record.hidden && record.blockedSource === source(record.node)
    && (record.blockKind === 'person' ? personApplies(record.node) && record.blockedPreference === prefs.personBlur && record.blockedPresentation === presentation() : prefs.inspectVisual);
  const visible = node => {
    const rect = node.getBoundingClientRect();
    // Size is not evidence of safety. Every nonzero visible selected image or
    // video is admitted, including 16/32px thumbnails. Zero-area media has an
    // explicit unknown state and is reconsidered when its layout changes.
    return rect.width > 0 && rect.height > 0 && rect.bottom >= 0 && rect.top <= innerHeight
      && rect.right >= 0 && rect.left <= innerWidth;
  };
  const signature = node => node.tagName === 'VIDEO' ? Math.floor(node.currentTime / 2)
    : `${node.naturalWidth}:${node.naturalHeight}:${Math.floor(Date.now() / 10000)}`;
  function mark(record, state, reason) {
    record.state = state; record.reason = reason || null;
    if (record.node.dataset.hisnVisual !== state) record.node.dataset.hisnVisual = state;
    if (reason) {
      if (record.node.dataset.hisnVisualReason !== reason) record.node.dataset.hisnVisualReason = reason;
    }
    else delete record.node.dataset.hisnVisualReason;
  }
  function restore(record) {
    const { node } = record;
    record.effect?.dispose(); record.effect = null;
    if (node.style.getPropertyValue('filter') === 'blur(24px)') {
      if (record.filter) node.style.setProperty('filter', record.filter, record.filterPriority);
      else node.style.removeProperty('filter');
    }
    if (node.style.getPropertyValue('visibility') === 'hidden' && record.hidden) {
      if (record.visibility) node.style.setProperty('visibility', record.visibility, record.visibilityPriority);
      else node.style.removeProperty('visibility');
    }
    delete node.dataset.hisnVisual;
    delete node.dataset.hisnVisualReason;
    record.state = null; record.reason = null;
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
  function restoreImageMode(record) {
    // Only release our own anonymous load. A page's later authored mode is
    // never ours to overwrite. Normal clean verdicts must NOT undo this: doing
    // so would reload opaque pixels that were not the pixels just inspected.
    if (record.corsOwned && corsMode(record.node) === 'anonymous') {
      record.node.removeAttribute('crossorigin');
      record.corsAttemptSource = null;
    }
    record.corsOwned = false;
  }
  function retryImageMode(record) {
    const { node } = record, url = source(node);
    // Preserve explicit author modes, including use-credentials. Retry once
    // per selected source without creating a privileged URL-fetch proxy.
    if (node.tagName !== 'IMG' || corsMode(node) !== null || !/^https?:\/\//i.test(url)
        || record.corsAttemptSource === url || typeof node.setAttribute !== 'function') return false;
    record.corsAttemptSource = url;
    mark(record, 'pending', 'awaiting-image-reload');
    node.setAttribute('crossorigin', 'anonymous');
    record.corsOwned = true;
    record.corsMutationPending = true;
    record.corsMode = corsMode(node);
    return true;
  }
  function invalidate(record) {
    cancel(record);
    // A seek or stronger person preference is not permission to reveal a
    // video already classified adult. Keep that source withheld until it is
    // replaced or adult filtering is deliberately disabled while unlocked.
    if (!held(record)) restore(record);
    record.source = source(record.node);
    record.policyRevision = policyRevision;
    record.route = location.href;
    record.signature = null;
    record.retryAt = 0; record.failures = 0;
    if (!held(record) && applies(record.node)) mark(record, 'pending', 'awaiting-check');
    queuePass();
  }
  function suspend(record) {
    record.resizeObserver?.disconnect();
    record.resizeObserver = null;
    for (const [event, handler] of record.listeners) record.node.removeEventListener(event, handler);
    record.listeners = [];
    media.delete(record.node);
  }
  function release(record) {
    cancel(record); restore(record); restoreImageMode(record); suspend(record);
    retained.delete(record.node);
  }
  function detach(record) {
    cancel(record); suspend(record);
    if (record.corsOwned) {
      // Weak retained records cannot be enumerated at stop(). Release the
      // attribute on removal and revoke any old reveal before reinsertion.
      if (!held(record)) restore(record);
      restoreImageMode(record); record.signature = null;
      record.corsMode = corsMode(record.node);
      if (!held(record)) mark(record, 'pending', 'awaiting-check');
    } else if (record.effect) {
      restore(record); record.signature = null; mark(record, 'pending', 'awaiting-check');
    }
  }
  function unknown(record, reason) {
    mark(record, 'unknown', typeof reason === 'string' ? reason.slice(0, 80) : 'inspection-failed');
    record.failures = Math.min((record.failures || 0) + 1, 6);
    record.retryAt = Date.now() + Math.min(30000, 1000 * 2 ** (record.failures - 1));
    suspend(record);
  }
  function needsWork(record) {
    return !held(record) && !record.request && !(record.retryAt > Date.now())
      && (record.signature === null || record.signature !== signature(record.node) || record.state === 'unknown');
  }
  function discover() {
    if (stopped) return;
    for (const [node, record] of media) if (!node.isConnected) {
      detach(record);
    }
    const nodes = document.querySelectorAll('img,video');
    // Check weak verdicts against current source, route and policy before using
    // a reveal marker. This also covers detached nodes when they return.
    for (const node of nodes) {
      const record = retained.get(node);
      if (!record) continue;
      if (!applies(node)) { release(record); continue; }
      const mode = corsMode(node);
      if (record.corsMode !== mode) {
        record.corsOwned = false;
        record.corsMode = mode;
        invalidate(record);
      }
      if (record.source !== source(node) || record.policyRevision !== policyRevision || record.route !== location.href
          || record.effect?.valid && !record.effect.valid()) invalidate(record);
      if (record.state && (node.dataset.hisnVisual !== record.state || (node.dataset.hisnVisualReason || null) !== record.reason)) {
        mark(record, record.state, record.reason);
      }
      if (!record.request && !visible(node)) suspend(record);
    }
    if (!active() || document.hidden) return;
    for (const node of nodes) {
      if (!applies(node)) continue;
      let record = retained.get(node);
      if (!record) {
        record = { node, source: source(node), corsMode: corsMode(node), generation: 0, signature: null, request: null,
          policyRevision, route: location.href,
          filter: node.style.getPropertyValue('filter'), filterPriority: node.style.getPropertyPriority('filter'),
          visibility: node.style.getPropertyValue('visibility'), visibilityPriority: node.style.getPropertyPriority('visibility'), hidden: false, blockedSource: null, listeners: [] };
        retained.set(node, record);
        mark(record, 'pending', 'awaiting-check');
      }
      const rect = node.getBoundingClientRect();
      if (!(rect.width > 0 && rect.height > 0)) {
        if (!held(record)) mark(record, 'unknown', 'zero-area');
      }
    }
    // First checks/source replacements precede periodic resampling. Otherwise
    // a slow model can make the first gallery items due again before item 129
    // gets a slot. Repeat work rotates its admission point for the same reason.
    for (let repeat = 0; repeat < 2; repeat++) {
      const start = repeat ? admissionCursor % Math.max(1, nodes.length) : 0;
      for (let offset = 0; offset < nodes.length && media.size < 128; offset++) {
        const index = (start + offset) % nodes.length, node = nodes[index], record = retained.get(node);
        if (!record || media.has(node) || !visible(node) || !needsWork(record)) continue;
        if ((record.signature === null) !== (repeat === 0)) continue;
        media.set(node, record);
        admissionCursor = index + 1;
        if(typeof ResizeObserver==='function') {
          record.resizeObserver=new ResizeObserver(()=>{
            if(record.effect?.valid&&!record.effect.valid())invalidate(record);
          });
          record.resizeObserver.observe(node);
        }
        queuePass();
        // Only active preparation owns node listeners. Settled weak verdicts
        // use shared document handlers, so completion releases this capacity.
        for (const event of ['load', 'loadeddata', 'loadedmetadata', 'resize', 'seeking', 'emptied']) {
          const handler = () => invalidate(record);
          record.listeners.push([event, handler]); node.addEventListener(event, handler);
        }
        record.listeners.push(['playing', onMediaEvent]); node.addEventListener('playing', onMediaEvent);
      }
    }
  }
  function onMediaEvent(event) {
    const record = retained.get(event.target);
    if (!record || stopped) return;
    if (event.type === 'playing') {
      // Buffering recovery also emits playing. It does not replace decoded
      // media, and must not cancel an inspection or restart unknown backoff.
      if (record.source !== source(record.node) || record.policyRevision !== policyRevision || record.route !== location.href) invalidate(record);
      if (held(record)) record.node.pause();
      else queuePass();
      return;
    }
    invalidate(record);
  }
  async function pass() {
    if (stopped || busy || document.hidden || !active()) return;
    discover();
    const records = [...media.values()];
    for (const record of records) {
      const node = record.node;
      if (!visible(node) || !applies(node)) continue;
      if (record.retryAt > Date.now()) continue;
      // currentSrc covers <picture>/srcset choices; srcObject must be compared
      // by identity because distinct MediaStreams stringify identically.
      if (record.source !== source(node)) invalidate(record);
      const video = node.tagName === 'VIDEO';
      // Images can animate or be updated behind an unchanged URL. Periodic
      // bounded rechecks improve that coverage; they are not every-frame scans.
      const sample = signature(node);
      if (held(record)) {
        node.style.setProperty('visibility', 'hidden', 'important');
        if (video) node.pause();
        suspend(record);
        continue;
      }
      if (record.signature === sample && record.state !== 'unknown') { suspend(record); continue; }
      // New sources, seeks and policy changes already revoke their verdict.
      // Periodic monitoring of an unchanged, checked source must not blank a
      // normal player on every sample. A failed/unknown or positive result
      // still withholds it. This is sampling, not every-frame certification.
      if (record.state !== 'checked') mark(record, 'pending', 'awaiting-check');
      const route = location.href, generation = record.generation, capturedSource = source(node), capturedMode = corsMode(node);
      let capture;
      try { capture = globalThis.HisnVisualCapture.capture(node); }
      catch { capture = { state: 'unknown', reason: 'capture-failed' }; }
      record.signature = sample;
      if (!capture || capture.state) {
        const retry = capture?.reason === 'inaccessible-pixels' && retryImageMode(record);
        // Setting CORS on the actual IMG makes the browser reload it. Never
        // certify an opaque original using a separate request's pixels. Only
        // a later origin-clean capture of this very node reaches inference.
        unknown(record, retry ? 'awaiting-image-reload' : capture?.reason || 'capture-failed');
        queuePass();
        continue;
      }
      busy = true;
      let id;
      let timeout;
      const current = () => !stopped && node.isConnected && generation === record.generation
        && location.href === route && source(node) === capturedSource && corsMode(node) === capturedMode && applies(node);
      try {
        id = requestId();
        record.request = id;
        const result = await Promise.race([
          api.runtime.sendMessage({ type: 'inspectVisualMedia', id, mediaKind:video?'video':'image', ...capture }),
          new Promise(resolve => { timeout = setTimeout(() => {
            api.runtime.sendMessage({ type: 'cancelVisualMedia', id }).catch(() => {});
            resolve({ state: 'unknown', reason: 'timeout' });
          }, 20000); }),
        ]);
        if (!current()) continue;
        // Normal playback may advance during inference. Seek/source/route
        // generations invalidate results; elapsed playback alone must not
        // discard every slower first inference.
        const adultPositive = prefs.inspectVisual && result?.adult?.block === true;
        const verified = result?.state === 'ready'
          && (!prefs.inspectVisual || typeof result.adult?.block === 'boolean')
          && (adultPositive || !personApplies(node) || typeof result.persons?.blur === 'boolean');
        if (!verified) {
          unknown(record, result?.reason || 'unverified-result');
          continue;
        }
        record.retryAt = 0; record.failures = 0;
        restore(record);
        if (prefs.inspectVisual && result.adult?.block === true) {
          record.hidden = true;
          record.blockKind = 'adult';
          record.blockedSource = capturedSource;
          node.style.setProperty('visibility', 'hidden', 'important');
          mark(record, 'adult-hidden');
          if (video) node.pause();
        } else if (personApplies(node) && result.persons?.blur === true) {
          const effect=globalThis.HisnVisualEffects?.apply(node,result.persons.regions,prefs);
          if (effect) {
            record.effect=effect; mark(record, 'checked');
            continue;
          }
          record.hidden = true;
          record.blockKind = 'person';
          record.blockedPreference = prefs.personBlur;
          record.blockedPresentation = presentation();
          record.blockedSource = capturedSource;
          node.style.setProperty('visibility', 'hidden', 'important');
          mark(record, 'person-hidden');
          if (video) node.pause();
        } else mark(record, 'checked');
      } catch { if (current()) unknown(record, 'inspection-failed'); }
      finally {
        clearTimeout(timeout);
        if (capture && typeof capture === 'object') capture.rgba = null;
        record.request = null;
        suspend(record);
        if (record.state === 'pending') record.signature = null;
        busy = false;
        queuePass();
      }
      break; // one model request per tick, not a page-sized frame flood
    }
  }
  function update(next) {
    if (stopped) return;
    // Missing/error replies are not confirmation that protection is disabled.
    if (typeof next?.inspectVisual !== 'boolean' || !['off','all','female','male'].includes(next?.personBlur)) return;
    preferencesReady = true;
    const normalized = { inspectVisual: next?.inspectVisual === true,
      personBlur: ['all', 'female', 'male'].includes(next?.personBlur) ? next.personBlur : 'off',
      personRegion:['full','faces'].includes(next?.personRegion)?next.personRegion:'full',
      personEffect:['solid','blur'].includes(next?.personEffect)?next.personEffect:'solid',
      personMedia:['images','videos'].includes(next?.personMedia)?next.personMedia:'both'};
    if (JSON.stringify(normalized) === JSON.stringify(prefs)) {applyGate();return;}
    prefs = normalized;
    policyRevision++;
    applyGate();
    discover();
  }
  async function refreshPreferences() {
    if (stopped || preferencesReady || preferenceRequest) return;
    preferenceRequest = true;
    let timeout;
    try {
      const reply = await Promise.race([api.runtime.sendMessage({type:'getVisualSettings'}),
        new Promise(resolve => {timeout = setTimeout(() => resolve(undefined),3000);})]);
      update(reply);
    } catch { /* Keep the startup gate, then retry without starting models. */ }
    finally {clearTimeout(timeout);preferenceRequest = false;}
  }
  void refreshPreferences();
  api.storage?.onChanged?.addListener((changes, area) => {
    if (area === 'local' && changes.state?.newValue) update(changes.state.newValue);
  });
  const timer = setInterval(() => {void refreshPreferences();void pass();}, 1000);
  const observer = new MutationObserver(mutations => {
    for (const mutation of mutations ?? []) {
      for (const removed of mutation.removedNodes ?? []) {
        const nodes = ['IMG','VIDEO'].includes(removed.tagName) ? [removed] : [];
        nodes.push(...(removed.querySelectorAll?.('img,video') ?? []));
        for (const node of nodes) {
          const record = retained.get(node);
          if (!record || node.isConnected) continue;
          detach(record);
          // A face-effect SVG lives outside the media node. Dispose it when
          // the node leaves the document; a reinserted image is checked again.
        }
      }
      if (mutation.type === 'attributes' && !['style','class'].includes(mutation.attributeName)) {
        const record = retained.get(mutation.target);
        if (record) {
          if (mutation.attributeName === 'crossorigin') {
            // Own writes already recorded their mode synchronously. A later
            // page-authored change relinquishes ownership before invalidation.
            const mode = corsMode(record.node);
            if (!(record.corsMutationPending && mode === 'anonymous')) record.corsOwned = false;
            record.corsMutationPending = false;
            record.corsMode = mode;
          }
          invalidate(record);
        }
        if (mutation.target.tagName === 'SOURCE') for (const image of mutation.target.parentElement?.querySelectorAll?.('img') ?? []) {
          const candidate = retained.get(image);
          if (candidate) invalidate(candidate);
        }
      }
    }
    discover();
  });
  function observe() {
    if (stopped || !document.documentElement) return;
    if (preferencesReady) applyGate();
    observer.observe(document.documentElement, { childList: true, subtree: true, attributes:true, attributeFilter:['src','srcset','sizes','poster','crossorigin','style','class'] });
  }
  observe();
  for (const event of ['load', 'loadeddata', 'loadedmetadata', 'resize', 'seeking', 'emptied', 'playing']) {
    document.addEventListener(event, event => {
      // Active nodes already have their own listeners; capturing the same
      // event twice would cancel the new request immediately after a load.
      if (!media.has(event.target)) onMediaEvent(event);
    }, true);
  }
  document.addEventListener('DOMContentLoaded', observe, { once: true });
  document.addEventListener('visibilitychange', () => { if (document.hidden) for (const record of media.values()) cancel(record); });
  addEventListener('pagehide', () => { for (const record of media.values()) cancel(record); });
  addEventListener('resize', () => { discover(); queuePass(); });
  globalThis.__hisnVisualScanner = { stop() {
    if (stopped) return;
    stopped = true;
    prefs = { inspectVisual: false, personBlur: 'off' };
    document.documentElement?.setAttribute?.('data-hisn-visual-gate', 'off');
    clearInterval(timer); observer.disconnect();
    clearTimeout(pumpTimer); pumpTimer = undefined;
    for (const record of media.values()) release(record);
    for (const node of document.querySelectorAll('img,video')) {
      const record = retained.get(node);
      if (record) release(record);
    }
    media.clear();
  } };
})();
