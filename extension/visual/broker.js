import { enabled, visualPreferences, validateCapture } from './decisions.js';
let creation;
let localEngine;
const requests = new Set();
const cancelled = () => ({ state: 'unknown', reason: 'cancelled' });
function cancelRequests(predicate = () => true) {
  for (const request of requests) if (predicate(request)) request.cancelled = true;
}
const disabled = () => ({ state: 'disabled', available: supported(), supportedPersonModes: ['off', 'all', 'female', 'male'] });
function api() { return globalThis.browser ?? globalThis.chrome; }
function supported() { return typeof document !== 'undefined' || !!api()?.offscreen?.createDocument; }
async function engine() {
  if (!localEngine) localEngine = import('./engine.bundle.js').then(module => module.createVisualEngine({ baseURL: api().runtime.getURL('visual/') }));
  return localEngine;
}
async function offscreen() {
  const chrome = api();
  if (!creation) creation = (async () => {
    if (await chrome.offscreen.hasDocument()) return;
    await chrome.offscreen.createDocument({ url: 'visual/offscreen.html', reasons: ['WORKERS'],
      justification: 'Run opted-in on-device visual models without uploading webpage media' });
  })().finally(() => { creation = undefined; });
  return creation;
}
async function relay(operation, payload = {}) {
  const request = { key: payload.key, cancelled: false };
  requests.add(request);
  try {
    let result;
    if (typeof document !== 'undefined') {
      const instance = await engine();
      if (request.cancelled) return cancelled();
      result = await (operation === 'inspect'
        ? instance.inspect(payload.key, payload.capture, payload.preferences)
        : instance.status(payload.preferences));
    } else {
      await offscreen();
      if (request.cancelled) return cancelled();
      result = await api().runtime.sendMessage({ target: 'hisn-visual-engine', operation, ...payload });
    }
    return request.cancelled ? cancelled() : result;
  } finally {
    requests.delete(request);
  }
}
// Waiting for an already-started creation is not consent to start a new one.
// Cleanup may arrive while hasDocument is still false, or after another
// cleanup closes it. Never use the context-creating relay for cancellations.
async function existingOffscreen() {
  if (creation) {
    try { await creation; } catch { /* Still check for a partially created document. */ }
  }
  return !!api()?.offscreen?.hasDocument && await api().offscreen.hasDocument();
}
// Disable without waking/creating a model context. Chromium closes the private
// document and its frames; Safari cancels queued work in the existing page.
export async function disableVisualEngine() {
  cancelRequests();
  if (typeof document !== 'undefined') {
    if (localEngine) (await localEngine).disable();
  } else if (await existingOffscreen()) {
    await api().offscreen.closeDocument();
  }
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
    if (!enabled(state)) return disabled();
    if (!supported()) return { state: 'unavailable', available: false, reason: 'unsupported-runtime' };
    if (!validateCapture(message)) return { state: 'unknown', reason: 'invalid-capture' };
    return await relay('inspect', { key,
      capture: { id: message.id, width: message.width, height: message.height, rgba: message.rgba },
      preferences: visualPreferences(state) });
  } catch { return { state: 'failed', reason: 'engine-unreachable' }; }
}
