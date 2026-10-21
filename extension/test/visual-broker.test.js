// Exercise the actual broker with deterministic offscreen creation/transport.
// No models, native host, real browser profile, or external media are needed.
import { getVisualStatus, prepareVisualEngine, handleVisualMessage, disableVisualEngine, cancelVisualTab,
  VISUAL_RESPONSE_TIMEOUT_MS, VISUAL_HASH_TIMEOUT_MS, MAX_VISUAL_PENDING_REQUESTS,
  MAX_VISUAL_FRAME_PENDING_REQUESTS } from '../visual/broker.js';
let timerID = 0;
const timers = new Map();
globalThis.setTimeout = (run, delay) => { const id = ++timerID; timers.set(id, { run, delay }); return id; };
globalThis.clearTimeout = id => timers.delete(id);
const expire = delay => {
  for (const [id, timer] of [...timers]) if (timer.delay === delay) {
    timers.delete(id); timer.run();
  }
};
let checks = 0;
const check = (ok, label) => { checks++; if (!ok) throw new Error(label); };
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
// Promise-job scheduling differs between the hosted and desktop JSC versions.
// Wait for an observed transport milestone before exercising transport races;
// a fixed number of microtasks is not proof that fingerprinting has finished.
const until = async (condition, label) => {
  for (let turn = 0; turn < 200; turn++) {
    if (condition()) return;
    await Promise.resolve();
  }
  throw new Error(`Broker fixture did not reach ${label}`);
};
const enabled = { inspectVisual: true, personBlur: 'off' };
const sender = (tabId = 17, frameId = 0) => ({ id: 'hisn', tab: { id: tabId }, frameId });
const capture = (id = 'frame') => ({ type: 'inspectVisualMedia', id, width: 1, height: 1, rgba: 'AAAAAA==' });
function harness({holdPreparation=false} = {}) {
  let documentExists = false, finishCreation, failCreation;
  const calls = { create: 0, close: 0, has: 0, sent: [], pending: [], preparations: [] };
  let holdClosure = false, finishClosure;
  globalThis.chrome = {
    runtime: { id: 'hisn', getURL: path => `chrome-extension://hisn/${path}`,
      sendMessage: async message => {
        calls.sent.push(message);
        if (message.operation === 'prepare' && holdPreparation) return new Promise(resolve => calls.preparations.push(resolve));
        if (message.operation === 'inspect') return new Promise(resolve => calls.pending.push({ message, resolve }));
        return { state: ['status','prepare'].includes(message.operation) ? 'ready' : 'unknown', reason: 'cancelled' };
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
      closeDocument: async () => {
        calls.close++;
        if (holdClosure) await new Promise(resolve => { finishClosure = resolve; });
        documentExists = false;
      },
    },
  };
  return { calls, ready: () => finishCreation(), fail: () => failCreation(),
    transport: (count = 1) => until(() => calls.pending.length >= count, `${count} inspection transport(s)`),
    closeExternally: () => { documentExists = false; },
    holdClosure: () => { holdClosure = true; },
    finishClosure: () => { holdClosure = false; finishClosure(); },
    resolve: (index = 0, result = { state: 'ready', adult: { block: false } }) => calls.pending[index].resolve(result) };
}
{
  const h=harness({holdPreparation:true}), pending=prepareVisualEngine(enabled);
  await flush(); h.ready(); await until(()=>h.calls.preparations.length===1,'active model preparation');
  await disableVisualEngine();
  check((await pending).reason==='cancelled' && h.calls.close===1,
    'opt-out cancels an active model preload and destroys only its existing context');
  h.calls.preparations[0]({state:'ready'}); await flush();
  check(h.calls.create===1 && timers.size===0, 'a late preload response cannot recreate the model context or retain its watchdog');
}
{
  const h=harness(), first=prepareVisualEngine(enabled);
  await flush();
  const next=prepareVisualEngine({inspectVisual:false,personBlur:'female'});
  await flush(); h.ready();
  check((await first).reason==='cancelled' && (await next).state==='ready'
    && h.calls.sent.length===1 && h.calls.sent[0].preferences.personBlur==='female',
    'a changed pending preparation uses only the latest opted-in model preferences');
  await disableVisualEngine();
}
{
  const h = harness();
  check((await getVisualStatus()).state === 'disabled', 'default status does not opt into models');
  check((await prepareVisualEngine()).state === 'disabled', 'preparation does not opt into models without selected protection');
  check((await handleVisualMessage(capture(), sender(), {})).state === 'disabled', 'default media request does not opt into models');
  await disableVisualEngine(); await cancelVisualTab(17);
  await handleVisualMessage({ type: 'cancelVisualMedia', id: 'frame' }, sender(), {});
  check(h.calls.create === 0 && h.calls.sent.length === 0, 'disabled cleanup never wakes a model context');
}
{
  const h = harness(), first = prepareVisualEngine(enabled), duplicate = prepareVisualEngine({...enabled});
  check(first === duplicate, 'concurrent preparation under the same policy shares one promise');
  await flush(); check(h.calls.create === 1, 'opt-in preloads in exactly one private model context without a capture');
  h.ready();
  check((await first).state === 'ready' && h.calls.sent.length === 1
    && h.calls.sent[0].operation === 'prepare' && !h.calls.sent[0].capture,
    'preparation loads only opted-in models and transports no webpage pixels');
  await disableVisualEngine();
}
{
  const h = harness(), pending = prepareVisualEngine(enabled); await flush();
  const disable = disableVisualEngine(); await flush(); h.ready(); await disable;
  check((await pending).reason === 'cancelled' && h.calls.sent.length === 0 && h.calls.close === 1,
    'opt-out during preload creation prevents late model work and closes the context');
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
  // Exercise the setup barrier with actual asynchronous fingerprint work,
  // even in JSC shells without WebCrypto/TextEncoder. This stub is not a
  // SHA-256 test; the real-WebCrypto browser fixture covers that separately.
  const originalCrypto = globalThis.crypto, originalEncoder = globalThis.TextEncoder;
  let hashes = 0;
  globalThis.TextEncoder = class { encode(value) { return Uint8Array.from(value, char => char.charCodeAt(0)); } };
  globalThis.crypto = { subtle: { digest: async () => {
    hashes++;
    for (let turn = 0; turn < 24; turn++) await Promise.resolve();
    return new Uint8Array(32).buffer;
  } } };
  const h = harness(), first = handleVisualMessage(capture('first'), sender(17), enabled);
  await flush(); h.ready(); await flush();
  const second = handleVisualMessage(capture('second'), sender(18), enabled); await flush();
  await h.transport(2);
  await cancelVisualTab(17); h.resolve(0); h.resolve(1);
  check((await first).reason === 'cancelled' && (await first).captureKey === undefined,
    'late ready results cannot survive cancellation of their tab or retain a capture identity');
  check((await second).state === 'ready' && /^[a-f0-9]{64}$/.test((await second).captureKey),
    'cancelling one tab preserves an unrelated current reply and its capture identity');
  const third = handleVisualMessage(capture('third'), sender(18), enabled); await flush();
  await h.transport(3);
  await disableVisualEngine(); h.resolve(2);
  check((await third).reason === 'cancelled', 'opt-out discards late ready results already in transport');
  check(hashes === 3, 'transport race setup observes readiness after delayed fingerprinting, not a fixed microtask budget');
  globalThis.crypto = originalCrypto; globalThis.TextEncoder = originalEncoder;
}
{
  const h = harness(), inspection = handleVisualMessage(capture(), sender(), enabled); await flush();
  h.ready(); await flush();
  await h.transport();
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
  h.ready(); await h.transport(); h.resolve();
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
{
  const h = harness(), stalled = handleVisualMessage(capture('stalled'), sender(17), enabled);
  await flush(); h.ready(); await flush();
  const sibling = handleVisualMessage(capture('sibling'), sender(18), enabled); await flush();
  await h.transport(2);
  expire(VISUAL_RESPONSE_TIMEOUT_MS); await flush();
  check((await stalled).reason === 'inference-timeout', 'a stalled transport has a bounded failed verdict, never clean');
  check((await sibling).reason === 'cancelled', 'retiring a context invalidates sibling in-flight verdicts');
  check(h.calls.close === 1, 'one stalled generation closes exactly one isolated engine context');
  const fresh = handleVisualMessage(capture('fresh'), sender(19), enabled); await flush();
  check(h.calls.create === 2, 'subsequent opted-in work creates a fresh context after confirmed destruction');
  h.ready(); await h.transport(3); h.resolve(2);
  check((await fresh).state === 'ready', 'another tab can progress after engine recovery');
  h.resolve(0); h.resolve(1); await flush();
  check(timers.size === 0, 'retired and completed requests release watchdog timers');
  await disableVisualEngine();
}
{
  const h = harness(), stalled = handleVisualMessage(capture('closing'), sender(17), enabled);
  await flush(); h.ready(); await h.transport(); h.holdClosure();
  expire(VISUAL_RESPONSE_TIMEOUT_MS); await flush();
  check((await stalled).reason === 'inference-timeout', 'timeout is reported while cleanup remains pending');
  const replacement = handleVisualMessage(capture('replacement'), sender(18), enabled); await flush();
  check(h.calls.create === 1, 'recovery cannot overlap a replacement with a still-running context');
  expire(5000); await flush();
  check((await replacement).state === 'failed' && h.calls.create === 1,
    'unconfirmed context destruction remains failed, not permission to launch parallel models');
  h.finishClosure(); await flush();
  const fresh = handleVisualMessage(capture('after-close'), sender(19), enabled); await flush();
  h.ready(); await h.transport(2); h.resolve(1);
  check((await fresh).state === 'ready', 'a late confirmed cleanup permits later opted-in recovery');
  h.resolve(0); await disableVisualEngine();
}
{
  const h = harness(), stalled = handleVisualMessage(capture('retire'), sender(17), enabled);
  await flush(); h.ready(); await h.transport(); h.holdClosure();
  expire(VISUAL_RESPONSE_TIMEOUT_MS); await flush(); await stalled;
  const waiting = handleVisualMessage(capture('opted-out'), sender(18), enabled); await flush();
  const disable = disableVisualEngine(); await flush();
  h.finishClosure(); await flush(); await disable;
  check((await waiting).reason === 'cancelled' && h.calls.create === 1,
    'opt-out during engine recovery cannot wake a replacement model context');
  h.resolve(0);
}
print(`  ${checks}/${checks} visual broker race checks passed`);
