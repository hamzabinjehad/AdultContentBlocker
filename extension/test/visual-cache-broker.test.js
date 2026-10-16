// Deterministic broker admission and fingerprint lifecycle. Browser fixtures
// separately exercise actual WebCrypto; this digest stub is not SHA-256 proof.
import { handleVisualMessage, disableVisualEngine, cancelVisualTab,
  MAX_VISUAL_PENDING_REQUESTS, MAX_VISUAL_FRAME_PENDING_REQUESTS, VISUAL_HASH_TIMEOUT_MS } from '../visual/broker.js';

let checks = 0;
const check = (ok, label) => { checks++; if (!ok) throw Error(label); };
const flush = async () => { for (let index = 0; index < 60; index++) await Promise.resolve(); };
const policy = { inspectVisual: true, personBlur: 'off', desiredRevision: 1 };
const sender = (tab = 1, frame = 0) => ({ id: 'hisn', tab: { id: tab }, frameId: frame });
const capture = (id, rgba = 'AAAAAA==') => ({ type: 'inspectVisualMedia', id, rgba,
  width: 1, height: 1, mediaKind: 'image' });
const timers = new Map(); let timerID = 0;
globalThis.setTimeout = (run, delay) => { timers.set(++timerID, { run, delay }); return timerID; };
globalThis.clearTimeout = id => timers.delete(id);
function expire(delay) {
  for (const [id, timer] of [...timers]) if (timer.delay === delay) { timers.delete(id); timer.run(); }
}

function harness({ holdHashes = false } = {}) {
  let exists = false;
  const calls = { inspect: 0, hashes: 0, create: 0, hashJobs: [] }, keys = new Map();
  // All input strings in this fixture are ASCII. Production uses TextEncoder.
  globalThis.TextEncoder = class { encode(text) { return Uint8Array.from(text, character => character.charCodeAt(0)); } };
  globalThis.crypto = { subtle: { digest: async (_algorithm, bytes) => {
    calls.hashes++;
    const input = Array.from(bytes).join(',');
    if (!keys.has(input)) keys.set(input, keys.size + 1);
    const result = new Uint8Array(32); result[31] = keys.get(input);
    if (holdHashes) return new Promise(resolve => calls.hashJobs.push(() => resolve(result.buffer)));
    return result.buffer;
  } } };
  globalThis.chrome = {
    runtime: { id: 'hisn', getURL: path => `chrome-extension://hisn/${path}`,
      sendMessage: async message => {
        if (message.operation !== 'inspect') return { state: 'unknown', reason: 'cancelled' };
        calls.inspect++;
        return { state: 'ready', adult: { block: false, inspected: true },
          persons: { blur: false, mode: message.preferences.personBlur, regions: [], estimated: true } };
      } },
    offscreen: { hasDocument: async () => exists,
      createDocument: async () => { calls.create++; exists = true; },
      closeDocument: async () => { exists = false; } },
  };
  return { calls, vanish: () => { exists = false; },
    finishHashes: async () => { for (const finish of calls.hashJobs.splice(0)) finish(); await flush(); } };
}

{
  const h = harness();
  for (let index = 0; index < 50; index++) {
    const result = await handleVisualMessage(capture(`duplicate_${index}`), sender(), policy);
    check(result.state === 'ready' && result.adult.block === false, 'duplicate capture receives a complete result');
  }
  check(h.calls.inspect === 1 && h.calls.hashes === 50, 'broker hashes each duplicate but avoids forty-nine redundant inference calls');
  await handleVisualMessage(capture('changed_pixel', 'AAAAAQ=='), sender(), policy);
  check(h.calls.inspect === 2, 'a changed pixel requires independent inference');
  await handleVisualMessage(capture('changed_policy'), sender(), { ...policy, desiredRevision: 2 });
  check(h.calls.inspect === 3, 'a changed policy revision cannot reuse the old result');
  h.vanish();
  await handleVisualMessage(capture('recreated_engine'), sender(), { ...policy, desiredRevision: 2 });
  check(h.calls.inspect === 4 && h.calls.create === 2, 'new context creation invalidates cached results from a vanished engine');
  await disableVisualEngine();
}
{
  const h = harness({ holdHashes: true });
  const first = handleVisualMessage(capture('first'), sender(), policy); await flush();
  const duplicate = await handleVisualMessage(capture('first'), sender(), policy);
  check(duplicate.reason === 'queue-full' && h.calls.hashes === 1, 'an authenticated duplicate request key does not allocate another hash');
  const second = handleVisualMessage(capture('second'), sender(), policy); await flush();
  const excess = await handleVisualMessage(capture('third'), sender(), policy);
  check(excess.state === 'unknown' && excess.reason === 'queue-full'
    && h.calls.hashes === MAX_VISUAL_FRAME_PENDING_REQUESTS, 'per-frame admission includes captures still awaiting a fingerprint');
  await cancelVisualTab(1);
  check((await first).reason === 'cancelled' && (await second).reason === 'cancelled', 'tab cancellation promptly retires fingerprinting requests');
  await h.finishHashes();
  check(h.calls.inspect === 0, 'late digests for canceled requests cannot trigger inference');
  const next = handleVisualMessage(capture('next'), sender(), policy); await flush(); await h.finishHashes();
  check((await next).state === 'ready' && h.calls.inspect === 1, 'retired requests release admission capacity for healthy work');
  await disableVisualEngine();
}
{
  const h = harness({ holdHashes: true }), pending = [];
  for (let index = 0; index < MAX_VISUAL_PENDING_REQUESTS; index++) {
    pending.push(handleVisualMessage(capture(`global_${index}`), sender(index + 1), policy));
  }
  await flush();
  const excess = await handleVisualMessage(capture('global_excess'), sender(99), policy);
  check(excess.reason === 'queue-full' && h.calls.hashes === MAX_VISUAL_PENDING_REQUESTS,
    'global admission bounds hash buffers across distinct tabs and frames');
  await disableVisualEngine();
  const results = await Promise.all(pending);
  check(results.every(result => result.reason === 'cancelled'), 'opt-out cancels all admitted hashing jobs without granting clean results');
  await h.finishHashes();
  check(h.calls.inspect === 0 && timers.size === 0, 'late opted-out hashes cannot revive inference or leave deadlines behind');
}
{
  const h = harness({ holdHashes: true });
  const slow = handleVisualMessage(capture('slow_hash'), sender(), policy); await flush();
  expire(VISUAL_HASH_TIMEOUT_MS); await flush();
  check((await slow).state === 'ready' && h.calls.inspect === 1, 'hash deadline falls back to uncached inference, not an unknown or safe guess');
  await h.finishHashes();
  const again = handleVisualMessage(capture('after_slow_hash'), sender(), policy); await flush();
  await h.finishHashes();
  check((await again).state === 'ready' && h.calls.inspect === 2, 'a digest completing after its deadline cannot cache the prior fallback result');
  await disableVisualEngine();
}

check(timers.size === 0, 'cache broker checks release every bounded deadline');
print(`  ${checks}/${checks} visual cache broker checks passed`);
