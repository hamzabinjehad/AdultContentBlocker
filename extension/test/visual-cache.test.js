import { VisualVerdictCache } from '../visual/verdict-cache.js';

let checks = 0;
const check = (ok, label) => { checks++; if (!ok) throw new Error(label); };
const prefs = { inspectVisual: true, personBlur: 'off' };
const policy = { ...prefs, personRegion: 'full', personEffect: 'solid', personMedia: 'both', desiredRevision: 1 };
const capture = { width: 1, height: 1, rgba: 'AAAAAA==' };
const clean = { state: 'ready', adult: { block: false, inspected: true },
  persons: { blur: false, mode: 'off', regions: [], estimated: true } };
const hex = value => value.toString(16).padStart(64, '0');
function harness(options = {}) {
  let now = 1000;
  const inputs = new Map();
  const digest = async value => {
    if (!inputs.has(value)) inputs.set(value, hex(inputs.size + 1));
    return inputs.get(value);
  };
  const cache = new VisualVerdictCache({ now: () => now, digest, ...options });
  const ticket = cache.begin(policy);
  return { cache, ticket, advance: ms => { now += ms; }, at: value => { now = value; } };
}
{
  const h = harness(), key = await h.cache.fingerprint(capture, h.ticket, prefs, 'image');
  check(h.cache.get(h.ticket, key) === null, 'unseen pixels have no cached verdict');
  check(h.cache.put(h.ticket, key, clean, prefs), 'complete inspected ready result is admitted');
  const again = await h.cache.fingerprint({ ...capture }, h.ticket, { ...prefs }, 'image');
  check(again === key && h.cache.get(h.ticket, again).adult.block === false, 'identical pixels, dimensions and policy reuse one verdict');
  for (const [replacement, changedPrefs, kind] of [
    [{ ...capture, rgba: 'AQAAAA==' }, prefs, 'image'],
    [{ ...capture, width: 2 }, prefs, 'image'],
    [capture, { ...prefs, personBlur: 'all' }, 'image'],
    [capture, prefs, 'video'],
  ]) {
    const changed = await h.cache.fingerprint(replacement, h.ticket, changedPrefs, kind);
    check(changed !== key && h.cache.get(h.ticket, changed) === null, 'different pixels/dimensions/effective policy/media kind never reuse clean output');
  }
  const exposed = h.cache.get(h.ticket, key);
  exposed.adult.block = true; exposed.persons.regions.push({ x: 0, y: 0, width: 1, height: 1 });
  check(!h.cache.get(h.ticket, key).adult.block && h.cache.get(h.ticket, key).persons.regions.length === 0,
    'returned nested verdicts cannot mutate the retained result');
  h.advance(29999);
  check(h.cache.get(h.ticket, key) !== null, 'verdict remains usable before its thirty-second expiry');
  h.advance(1);
  check(h.cache.get(h.ticket, key) === null, 'verdict expires at thirty seconds');
}
{
  const h = harness({ limit: 200, ttlMs: 90000 });
  for (let i = 1; i <= 140; i++) check(h.cache.put(h.ticket, hex(i), clean, prefs), 'bounded complete verdict can be retained');
  check(h.cache.entries.size === 128 && !h.cache.get(h.ticket, hex(1)), 'caller cannot raise the 128-entry upper bound');
  h.cache.get(h.ticket, hex(13)); h.cache.put(h.ticket, hex(141), clean, prefs);
  check(h.cache.get(h.ticket, hex(13)) !== null && h.cache.get(h.ticket, hex(14)) === null, 'recently used results survive least-recently-used eviction');
  h.advance(30000);
  check(h.cache.get(h.ticket, hex(13)) === null, 'caller cannot extend the thirty-second reuse limit');
}
for (const count of [10,50,100]) {
  for (const limit of [32,128]) {
    const h = harness({limit}); let inspections = 0;
    for (let round=0;round<2;round++) for (let index=0;index<count;index++) {
      const frame = {...capture,rgba:btoa(String.fromCharCode(index,0,0,255))};
      const key = await h.cache.fingerprint(frame,h.ticket,prefs,'image');
      if (!h.cache.get(h.ticket,key)) { inspections++; h.cache.put(h.ticket,key,clean,prefs); }
    }
    check(inspections === (limit >= count ? count : count*2),
      `${count}-image repeated gallery avoids inference only when its exact-pixel verdicts fit the bounded cache`);
    check(h.cache.entries.size <= limit, 'gallery reuse never exceeds its memory limit');
  }
}
{
  const h = harness(), key = await h.cache.fingerprint(capture, h.ticket, prefs, 'image');
  for (const result of [undefined, { state: 'unknown' }, { state: 'failed' }, { state: 'disabled' },
    { state: 'ready', adult: { block: false } }, { state: 'ready', adult: { inspected: true } },
    { state: 'ready', adult: { block: false, inspected: true }, persons: { blur: false, state: 'not-inspected' } }]) {
    check(!h.cache.put(h.ticket, key, result, { ...prefs, personBlur: 'all' }), 'missing, failed, unverified and partial clean results are never cached');
  }
  check(h.cache.entries.size === 0, 'rejected results leave no retained cache data');
  const adult = { state: 'ready', adult: { block: true, inspected: true }, persons: { state: 'not-inspected' } };
  check(h.cache.put(h.ticket, key, adult, { ...prefs, personBlur: 'all' })
    && h.cache.get(h.ticket, key).adult.block, 'verified independent adult-positive shortcut may be reused without inventing a clean face verdict');
}
{
  const h = harness(), personPrefs = { inspectVisual: false, personBlur: 'female' }, key = hex(1);
  const region = { x: .1, y: .2, width: .3, height: .4 };
  const person = { state: 'ready', persons: { blur: true, mode: 'female', regions: [region] } };
  check(h.cache.put(h.ticket, key, person, personPrefs), 'complete selected person result can be retained');
  person.persons.regions[0].x = .9;
  check(h.cache.get(h.ticket, key).persons.regions[0].x === .1, 'stored regions are defensive bounded copies');
  const oversized = { state: 'ready', persons: { blur: true, mode: 'female', regions: Array(11).fill(region) } };
  check(!h.cache.put(h.ticket, hex(2), oversized, personPrefs), 'unbounded region arrays are never retained');
  for (const regions of [[{ x: -1, y: 0, width: 1, height: 1 }], [{ x: 0, y: 0, width: 2, height: 1 }],
    [{ x: NaN, y: 0, width: 1, height: 1 }]]) {
    check(!h.cache.put(h.ticket, hex(2), { state: 'ready', persons: { blur: true, mode: 'female', regions } }, personPrefs),
      'invalid face geometry does not become reusable protection evidence');
  }
  const withExtras = { ...clean, rgba: capture.rgba, url: 'https://private.invalid/', extra: 'x'.repeat(100000),
    adult: { ...clean.adult, extra: 'x'.repeat(100000) } };
  h.cache.put(h.ticket, key, withExtras, prefs);
  const stored = JSON.stringify([...h.cache.entries.values()]);
  check(stored.length < 2000 && !stored.includes('private.invalid') && !stored.includes(capture.rgba),
    'only bounded known verdict fields remain, never arbitrary URLs, frame bytes or engine extras');
}
{
  const h = harness(), key = hex(1);
  h.cache.put(h.ticket, key, clean, prefs);
  const same = h.cache.begin({ ...policy });
  check(h.cache.get(same, key) !== null, 'equivalent normalized policy retains valid short-lived results');
  for (const patch of [{ inspectVisual: false }, { personBlur: 'male' }, { personRegion: 'faces' },
    { personEffect: 'blur' }, { personMedia: 'images' }, { desiredRevision: 2 }]) {
    const changed = h.cache.begin({ ...policy, ...patch });
    check(!h.cache.current(same) && h.cache.get(changed, key) === null, 'every full visual policy or desired revision change invalidates cached results');
    check(!h.cache.put(same, key, clean, prefs), 'late work from the old policy cannot repopulate the cache');
  }
}
{
  let finish;
  const h = harness({ digest: () => new Promise(resolve => { finish = resolve; }) });
  const pending = h.cache.fingerprint(capture, h.ticket, prefs, 'image');
  h.cache.clear(); finish(hex(1));
  check(await pending === null && !h.cache.put(h.ticket, hex(1), clean, prefs), 'cache clearing fences a digest and late verdict already in flight');
}
{
  const h = harness(); h.cache.clear();
  const refreshed = h.cache.refresh(h.ticket);
  check(refreshed && h.cache.current(refreshed), 'confirmed context creation can refresh a ticket under the unchanged policy');
  h.cache.begin({ ...policy, desiredRevision: 2 }); h.cache.begin(policy);
  check(h.cache.refresh(h.ticket) === null, 'policy A to B to A cannot revive a ticket from an earlier policy epoch');
}
{
  const h = harness();
  h.cache.put(h.ticket, hex(1), clean, prefs); h.at(999);
  check(h.cache.get(h.ticket, hex(1)) === null, 'clock rollback cannot extend a verdict lifetime');
  for (const digest of [async () => null, async () => 'weak-hash', async () => { throw new Error('unavailable'); }]) {
    const fixture = harness({ digest });
    check(await fixture.cache.fingerprint(capture, fixture.ticket, prefs, 'image') === null,
      'hash unavailability is an uncached result, never a weak fallback key');
  }
}
print(`  ${checks}/${checks} visual cache checks passed`);
