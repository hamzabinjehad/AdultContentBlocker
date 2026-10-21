import { enabled, visualPreferences, validateCapture, presentationPreferences } from './decisions.js';
import { VisualVerdictCache } from './verdict-cache.js';
let creation;
let localEngine;
let recovery;
let pendingRecoveryClose;
let recoveryBlocked = false;
let localStalled = false;
let preparation;
export const VISUAL_RESPONSE_TIMEOUT_MS = 45000;
export const VISUAL_HASH_TIMEOUT_MS = 1000;
export const MAX_VISUAL_PENDING_REQUESTS = 12;
export const MAX_VISUAL_FRAME_PENDING_REQUESTS = 2;
const verdictCache = new VisualVerdictCache();
function cachePolicy(state) {
  return { ...visualPreferences(state), ...presentationPreferences(state),
    desiredRevision: Number.isSafeInteger(state?.desiredRevision) ? state.desiredRevision : null };
}
const requests = new Set();
const cancelled = () => ({ state: 'unknown', reason: 'cancelled' });
function cancelRequests(predicate = () => true) {
  for (const request of requests) if (predicate(request)) {
    request.cancelled = true;
    request.resolveCancel?.(cancelled());
  }
}
const disabled = () => ({ state: 'disabled', available: supported(), supportedPersonModes: ['off', 'all', 'female', 'male'] });
function api() { return globalThis.browser ?? globalThis.chrome; }
function supported() { return typeof document !== 'undefined' || !!api()?.offscreen?.createDocument; }
async function engine() {
  if (!localEngine) localEngine = import('./engine.bundle.js').then(module => module.createVisualEngine({ baseURL: api().runtime.getURL('visual/') }));
  return localEngine;
}
async function offscreen(request) {
  const chrome = api();
  if (recovery) await recovery;
  if (request?.cancelled) return;
  if (recoveryBlocked) {
    // Never create another model context until destruction of the previous
    // one is actually confirmed. A late close may have completed meanwhile.
    if (pendingRecoveryClose || await chrome.offscreen.hasDocument()) throw new Error('engine-recovery-failed');
    recoveryBlocked = false;
  }
  if (!creation) creation = (async () => {
    if (await chrome.offscreen.hasDocument()) return;
    if (request?.cancelled) return;
    verdictCache.clear();
    await chrome.offscreen.createDocument({ url: 'visual/offscreen.html', reasons: ['WORKERS'],
      justification: 'Run opted-in on-device visual models without uploading webpage media' });
  })().finally(() => { creation = undefined; });
  return creation;
}

function bounded(promise, milliseconds, onTimeout) {
  let timer;
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      timer = setTimeout(() => {
        onTimeout?.();
        reject(new Error('inference-timeout'));
      }, milliseconds);
    }),
  ]).finally(() => clearTimeout(timer));
}

function recoverStalledEngine(origin) {
  if (recovery) return recovery;
  verdictCache.clear();
  cancelRequests(request => request !== origin);
  if (typeof document !== 'undefined') {
    // Safari shares its background document with the broker. A timeout does
    // not prove its model stopped, so do not start overlapping local engines.
    localStalled = true;
    return;
  }
  recoveryBlocked = true;
  recovery = (async () => {
    try {
      await bounded((async () => {
        if (await api().offscreen.hasDocument()) {
          pendingRecoveryClose = api().offscreen.closeDocument();
          try { await pendingRecoveryClose; }
          finally { pendingRecoveryClose = undefined; }
        }
        if (await api().offscreen.hasDocument()) throw new Error('engine-recovery-failed');
      })(), 5000);
      recoveryBlocked = false;
    } catch { /* Keep recovery blocked until absence is confirmed. */ }
  })().finally(() => { recovery = undefined; });
  return recovery;
}
async function relay(operation, payload = {}) {
  // Include fingerprinting/context setup in the same bounded request budget;
  // a burst of captures must not allocate unbounded concurrent hash buffers.
  if (operation === 'inspect') {
    const inspections = [...requests].filter(request => request.operation === 'inspect');
    if (inspections.length >= MAX_VISUAL_PENDING_REQUESTS
        || inspections.filter(request => request.scope === payload.scope).length >= MAX_VISUAL_FRAME_PENDING_REQUESTS
        || inspections.some(request => request.key === payload.key)) return { state: 'unknown', reason: 'queue-full' };
  }
  const request = { key: payload.key, scope: payload.scope, operation, cancelled: false };
  let ticket = operation === 'inspect' ? verdictCache.begin(payload.cachePolicy) : null, fingerprint;
  const cancellation = new Promise(resolve => { request.resolveCancel = resolve; });
  requests.add(request);
  function identified(result) {
    if (!result || typeof result !== 'object') return result;
    // Only the broker's bounded SHA-256 of these exact decoded pixels and
    // policy can identify a repeat. Never trust an engine-supplied identity or
    // let a canceled/stale policy ticket attach one to a late reply.
    const { captureKey: _ignored, ...reply } = result;
    if (operation === 'inspect' && result.state === 'ready' && !request.cancelled
        && verdictCache.current(ticket) && /^[a-f0-9]{64}$/.test(fingerprint ?? '')) reply.captureKey = fingerprint;
    return reply;
  }
  try {
    let result, instance;
    if (typeof document !== 'undefined') {
      if (localStalled) return { state: 'failed', reason: 'engine-restart-required' };
      instance = await engine();
    } else await offscreen(request);
    if (request.cancelled) return cancelled();
    if (operation === 'inspect') {
      ticket = verdictCache.refresh(ticket);
      fingerprint = await bounded(Promise.race([
        verdictCache.fingerprint(payload.capture, ticket, payload.preferences, payload.mediaKind),
        cancellation.then(() => null),
      ]), VISUAL_HASH_TIMEOUT_MS).catch(() => null);
      if (request.cancelled) return cancelled();
      const cached = verdictCache.get(ticket, fingerprint);
      if (cached) return identified(cached);
    }
    const work = instance ? operation === 'inspect'
      ? instance.inspect(payload.key, payload.capture, payload.preferences)
      : operation === 'prepare' ? instance.prepare(payload.preferences).then(() => instance.status(payload.preferences))
      : instance.status(payload.preferences)
      : api().runtime.sendMessage({ target: 'hisn-visual-engine', operation, ...payload });
    result = ['inspect', 'prepare'].includes(operation)
      ? await bounded(Promise.race([work, cancellation]), VISUAL_RESPONSE_TIMEOUT_MS,
        () => { if (!request.cancelled) recoverStalledEngine(request); }) : await work;
    if (!request.cancelled && operation === 'inspect') verdictCache.put(ticket, fingerprint, result, payload.preferences);
    return request.cancelled ? cancelled() : identified(result);
  } catch (error) {
    if (request.cancelled && error?.message === 'inference-timeout') return cancelled();
    throw error;
  } finally {
    requests.delete(request);
  }
}
// Waiting for an already-started creation is not consent to start a new one.
// Cleanup may arrive while hasDocument is still false, or after another
// cleanup closes it. Never use the context-creating relay for cancellations.
async function existingOffscreen() {
  if (recovery) await recovery;
  if (pendingRecoveryClose) return false;
  if (creation) {
    try { await creation; } catch { /* Still check for a partially created document. */ }
  }
  const exists = !!api()?.offscreen?.hasDocument && await api().offscreen.hasDocument();
  if (!exists) verdictCache.clear();
  return exists;
}
// Disable without waking/creating a model context. Chromium closes the private
// document and its frames; Safari cancels queued work in the existing page.
export async function disableVisualEngine() {
  preparation = undefined;
  verdictCache.clear();
  cancelRequests();
  if (typeof document !== 'undefined') {
    if (localEngine) (await localEngine).disable();
  } else if (await existingOffscreen()) {
    await api().offscreen.closeDocument();
  }
}
// Only the trusted background reconciliation calls this after opt-in. Status
// requests never call prepare: opening a popup does not opt into models.
export function prepareVisualEngine(state = {}) {
  if (!supported() || !enabled(state)) return Promise.resolve(disabled());
  const policy = JSON.stringify(cachePolicy(state));
  if (preparation?.policy === policy) return preparation.promise;
  cancelRequests(request => request.operation === 'prepare');
  const current = { policy };
  current.promise = relay('prepare', { preferences: visualPreferences(state) })
    .catch(() => ({ state: 'failed', reason: 'engine-unreachable' }))
    .finally(() => { if (preparation === current) preparation = undefined; });
  preparation = current;
  return current.promise;
}
export async function cancelVisualTab(tabId) {
  if (!Number.isInteger(tabId) || tabId < 0) return;
  const prefix = `${tabId}:`;
  cancelRequests(request => request.key?.startsWith(prefix));
  if (typeof document !== 'undefined') {
    if (localEngine) (await localEngine).cancelPrefix(prefix);
  } else if (await existingOffscreen()) {
    await api().runtime.sendMessage({ target: 'hisn-visual-engine', operation: 'cancelPrefix', prefix });
  }
}
export async function getVisualStatus(state = {}) {
  verdictCache.begin(cachePolicy(state));
  if (!supported()) return { state: 'unavailable', available: false, reason: 'unsupported-runtime', supportedPersonModes: [] };
  if (!enabled(state)) return disabled();
  try { return await relay('status', { preferences: visualPreferences(state) }); }
  catch { return { state: 'failed', available: true, reason: 'engine-unreachable' }; }
}
export async function handleVisualMessage(message, sender, state = {}) {
  if (message?.type === 'getVisualStatus') return getVisualStatus(state);
  const chrome = api();
  if (sender?.id !== chrome.runtime.id || !Number.isInteger(sender?.tab?.id) || sender.tab.id < 0
      || !Number.isInteger(sender.frameId) || sender.frameId < 0) return { state: 'unknown', reason: 'forbidden-sender' };
  if (!/^[a-zA-Z0-9_-]{1,80}$/.test(message?.id ?? '')) return { state: 'unknown', reason: 'invalid-capture' };
  const key = `${sender.tab.id}:${sender.frameId}:${message.id}`;
  try {
    if (message.type === 'cancelVisualMedia') {
      cancelRequests(request => request.key === key);
      if (typeof document !== 'undefined') {
        if (localEngine) (await localEngine).cancel(key);
      } else if (await existingOffscreen()) {
        await api().runtime.sendMessage({ target: 'hisn-visual-engine', operation: 'cancel', key });
      }
      return cancelled();
    }
    if (!enabled(state)) { verdictCache.begin(cachePolicy(state)); return disabled(); }
    if (!supported()) return { state: 'unavailable', available: false, reason: 'unsupported-runtime' };
    if (!validateCapture(message)) return { state: 'unknown', reason: 'invalid-capture' };
    const preferences = visualPreferences(state), presentation = presentationPreferences(state);
    if (message.mediaKind !== undefined && !['image','video'].includes(message.mediaKind)) return {state:'unknown',reason:'invalid-media-kind'};
    if (message.mediaKind === undefined && presentation.personMedia !== 'both') return {state:'unknown',reason:'missing-media-kind'};
    const kind = message.mediaKind === 'video' ? 'videos' : 'images';
    if (presentation.personMedia !== 'both' && presentation.personMedia !== kind) preferences.personBlur = 'off';
    if (!preferences.inspectVisual && preferences.personBlur === 'off') return {state:'disabled',reason:'media-excluded'};
    return await relay('inspect', { key,
      capture: { id: message.id, width: message.width, height: message.height, rgba: message.rgba },
      preferences, mediaKind: message.mediaKind, scope: `${sender.tab.id}:${sender.frameId}`, cachePolicy: cachePolicy(state) });
  } catch (error) { return { state: 'failed', reason: error?.message === 'inference-timeout'
    ? 'inference-timeout' : 'engine-unreachable' }; }
}
