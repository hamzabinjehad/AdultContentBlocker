// Exercise the actual broker with deterministic offscreen creation/transport.
// No models, native host, real browser profile, or external media are needed.
import { getVisualStatus, handleVisualMessage, disableVisualEngine, cancelVisualTab } from '../visual/broker.js';
let checks = 0;
const check = (ok, label) => { checks++; if (!ok) throw new Error(label); };
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
const enabled = { inspectVisual: true, personBlur: 'off' };
const sender = (tabId = 17, frameId = 0) => ({ id: 'hisn', tab: { id: tabId }, frameId });
const capture = (id = 'frame') => ({ type: 'inspectVisualMedia', id, width: 1, height: 1, rgba: 'AAAAAA==' });
function harness() {
  let documentExists = false, finishCreation, failCreation;
  const calls = { create: 0, close: 0, has: 0, sent: [], pending: [] };
  globalThis.chrome = {
    runtime: { id: 'hisn', getURL: path => `chrome-extension://hisn/${path}`,
      sendMessage: async message => {
        calls.sent.push(message);
        if (message.operation === 'inspect') return new Promise(resolve => calls.pending.push({ message, resolve }));
        return { state: message.operation === 'status' ? 'ready' : 'unknown', reason: 'cancelled' };
      } },
    offscreen: {
      hasDocument: async () => { calls.has++; return documentExists; },
      createDocument: async () => {
        calls.create++;
        return new Promise((resolve, reject) => {
          finishCreation = () => { documentExists = true; resolve(); };
          failCreation = () => reject(new Error('fixture creation failure'));
        });
      },
      closeDocument: async () => { calls.close++; documentExists = false; },
    },
  };
  return { calls, ready: () => finishCreation(), fail: () => failCreation(),
    closeExternally: () => { documentExists = false; },
    resolve: (index = 0) => calls.pending[index].resolve({ state: 'ready', adult: { block: false } }) };
}
{
  const h = harness();
  check((await getVisualStatus()).state === 'disabled', 'default status does not opt into models');
  check((await handleVisualMessage(capture(), sender(), {})).state === 'disabled', 'default media request does not opt into models');
  await disableVisualEngine(); await cancelVisualTab(17);
  await handleVisualMessage({ type: 'cancelVisualMedia', id: 'frame' }, sender(), {});
  check(h.calls.create === 0 && h.calls.sent.length === 0, 'disabled cleanup never wakes a model context');
}
{
  const h = harness(), status = getVisualStatus(enabled); await flush();
  check(h.calls.create === 1, 'opted-in status begins one context creation');
  const before = h.calls.has, disable = disableVisualEngine(); await flush();
  check(h.calls.close === 0 && h.calls.has === before, 'disable waits for pending creation instead of trusting an early false hasDocument');
  h.ready(); await disable;
  check((await status).reason === 'cancelled' && h.calls.close === 1, 'opt-out closes the newly created document and invalidates waiting status');
  check(h.calls.sent.length === 0, 'an opted-out waiting request never reaches model transport');
}
{
  const h = harness(), inspection = handleVisualMessage(capture(), sender(), enabled); await flush();
  const cancellation = cancelVisualTab(17); await flush(); h.ready(); await cancellation;
  check((await inspection).reason === 'cancelled', 'tab close invalidates inspection waiting for context creation');
  check(h.calls.sent.length === 1 && h.calls.sent[0].operation === 'cancelPrefix'
    && h.calls.sent[0].prefix === '17:', 'tab cleanup reaches only the existing created worker with its authenticated prefix');
  await disableVisualEngine();
}
{
  const h = harness(), inspection = handleVisualMessage(capture(), sender(), enabled); await flush();
  const cancellation = handleVisualMessage({ type: 'cancelVisualMedia', id: 'frame' }, sender(), {});
  await flush(); h.ready(); await cancellation;
  check((await inspection).reason === 'cancelled', 'authenticated cancellation works after preferences become disabled during creation');
  check(h.calls.sent[0]?.operation === 'cancel' && h.calls.sent[0].key === '17:0:frame', 'media cancellation remains scoped to its tab, frame and identifier');
  await disableVisualEngine();
}
{
  const h = harness(), first = handleVisualMessage(capture('first'), sender(17), enabled);
  await flush(); h.ready(); await flush();
  const second = handleVisualMessage(capture('second'), sender(18), enabled); await flush();
  await cancelVisualTab(17); h.resolve(0); h.resolve(1);
  check((await first).reason === 'cancelled', 'late ready results cannot survive cancellation of their tab');
  check((await second).state === 'ready', 'cancelling one tab preserves unrelated tab requests');
  const third = handleVisualMessage(capture('third'), sender(18), enabled); await flush();
  await disableVisualEngine(); h.resolve(2);
  check((await third).reason === 'cancelled', 'opt-out discards late ready results already in transport');
}
{
  const h = harness(), inspection = handleVisualMessage(capture(), sender(), enabled); await flush();
  h.ready(); await flush();
  const before = h.calls.create;
  h.closeExternally(); await cancelVisualTab(17);
  await handleVisualMessage({ type: 'cancelVisualMedia', id: 'frame' }, sender(), {});
  check(h.calls.create === before, 'cleanup never recreates a document that disappeared');
  h.resolve(); check((await inspection).reason === 'cancelled', 'vanished-context cleanup still suppresses a late result');
}
{
  const h = harness(), inspection = handleVisualMessage(capture(), sender(), enabled); await flush();
  const cancellation = handleVisualMessage({ type: 'cancelVisualMedia', id: 'frame' }, { ...sender(), id: 'other-extension' }, {});
  check((await cancellation).reason === 'forbidden-sender', 'another extension cannot cancel authenticated media');
  h.ready(); await flush(); h.resolve();
  check((await inspection).state === 'ready', 'forbidden cancellation leaves legitimate inspection alone');
  await disableVisualEngine();
}
{
  const h = harness(), inspection = handleVisualMessage(capture(), sender(), enabled); await flush();
  const disable = disableVisualEngine(); h.fail(); await disable;
  check((await inspection).state === 'failed' && h.calls.close === 0, 'failed creation remains failure and cleanup does not start a replacement');
  check(h.calls.create === 1 && h.calls.sent.length === 0, 'failed creation has no accidental retry during opt-out');
}
{
  const h = harness();
  await cancelVisualTab(-1); await cancelVisualTab('17');
  check(h.calls.has === 0 && h.calls.sent.length === 0, 'invalid tab cleanup is a no-op');
}
print(`  ${checks}/${checks} visual broker race checks passed`);
