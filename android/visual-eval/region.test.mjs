import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { nearMiss, centreCrop, strongBlock, padSquare, agrees, CROP_FRACTION } from '../../android/app/src/main/assets/visual/region.js';

// nearMiss is a bounded closer look, not a lower block threshold.
assert.equal(nearMiss(null), false);
assert.equal(nearMiss({ scores: { Porn: 0.10, Hentai: 0.05 } }), false);
assert.equal(nearMiss({ scores: { Porn: 0.45, Hentai: 0.05 } }), true);
assert.equal(nearMiss({ scores: { Porn: 0.10, Hentai: 0.45 } }), true);
assert.equal(nearMiss({ scores: { Porn: 0.30, Hentai: 0.30 } }), true);
assert.equal(nearMiss({ scores: { Porn: 0.29, Hentai: 0.29 } }), false);

// A 10 x 10 frame with a known pixel pattern; 68% rounds to 7 x 7 from (1, 1).
const width = 10, height = 10;
const source = Buffer.alloc(width * height * 4);
for (let i = 0; i < source.length; i++) source[i] = i & 255;
const crop = centreCrop(source.toString('base64'), width, height);
assert.equal(CROP_FRACTION, 0.68);
assert.equal(crop.width, 7);
assert.equal(crop.height, 7);
const decoded = Buffer.from(crop.rgba, 'base64');
assert.equal(decoded.length, 7 * 7 * 4);
const sourceOffset = (1 * width + 1) * 4;
for (let i = 0; i < 4; i++) assert.equal(decoded[i], source[sourceOffset + i]);
assert.deepEqual([crop.width, crop.height], [7, 7]);
assert.equal(centreCrop('not-base64', 3, 3), null);
assert.equal(centreCrop(source.toString('base64'), 10, 9), null);
assert.equal(centreCrop(source.toString('base64'), 0, 10), null);
// A strong block skips the guard's second capture; a near-threshold block does not.
assert.equal(strongBlock(null), false);
assert.equal(strongBlock({ block: false, scores: { Porn: 0.95, Hentai: 0 } }), false);
assert.equal(strongBlock({ block: true, scores: { Porn: 0.72, Hentai: 0.05 } }), false);
assert.equal(strongBlock({ block: true, scores: { Porn: 0.90, Hentai: 0.02 } }), true);
assert.equal(strongBlock({ block: true, scores: { Porn: 0.50, Hentai: 0.45 } }), true);
// A tall frame is padded to a square on mid-grey with its pixels unchanged and centred.
const tall = Buffer.alloc(2 * 4 * 4);
for (let i = 0; i < tall.length; i++) tall[i] = (i * 7 + 1) & 255;
const square = padSquare(tall.toString('base64'), 2, 4);
assert.deepEqual([square.width, square.height], [4, 4]);
const pixels = Buffer.from(square.rgba, 'base64');
assert.equal(pixels.length, 4 * 4 * 4);
assert.deepEqual([...pixels.subarray(0, 4)], [128, 128, 128, 255]);
assert.deepEqual([...pixels.subarray(12, 16)], [128, 128, 128, 255]);
for (let row = 0; row < 4; row++) {
  assert.deepEqual([...pixels.subarray((row * 4 + 1) * 4, (row * 4 + 3) * 4)],
    [...tall.subarray(row * 8, row * 8 + 8)]);
}
const wide = padSquare(Buffer.alloc(4 * 2 * 4, 9).toString('base64'), 4, 2);
assert.deepEqual([wide.width, wide.height], [4, 4]);
assert.equal(Buffer.from(wide.rgba, 'base64')[4 * 4], 9);
assert.equal(padSquare(tall.toString('base64'), 4, 4), null);
assert.equal(padSquare('not-base64', 2, 4), null);
assert.equal(padSquare(tall.toString('base64'), 3, 4), null);
// A clean shape-keeping view cancels a block; a missing one never does.
assert.equal(agrees({ scores: { Porn: 0.02, Hentai: 0.01 } }), false);
assert.equal(agrees({ scores: { Porn: 0.25, Hentai: 0.05 } }), true);
assert.equal(agrees({ scores: { Porn: 0.80, Hentai: 0.00 } }), true);
assert.equal(agrees(null), true);

// Exercise the actual Android bridge with predictable model scores. The bundled
// engine skips faces after its first adult hit; the bridge may later clear that hit.
const bridgeURL = new URL('../app/src/main/assets/visual/android.js', import.meta.url);
const stubEngine = 'data:text/javascript,export const createVisualEngine = () => globalThis.testEngine';
const bridge = (await readFile(bridgeURL, 'utf8'))
  .replace("'./engine.bundle.js'", JSON.stringify(stubEngine))
  .replace("'./region.js'", JSON.stringify(new URL('region.js', bridgeURL).href));
const calls = [], answers = [];
let modelAnswers;
globalThis.window = {};
globalThis.location = { href: 'https://hisn.invalid/visual/android.html?person=all' };
globalThis.HisnResult = { result: (id, answer) => answers.push([id, answer]) };
globalThis.testEngine = {
  async prepare() {},
  status: () => ({ state: 'ready', backend: 'wasm' }),
  async inspect(id, capture, preferences) {
    assert.equal(id, capture.id);
    calls.push(preferences);
    return modelAnswers.shift();
  },
};
await import('data:text/javascript;base64,' + Buffer.from(bridge).toString('base64'));
assert.deepEqual(answers.shift(), ['load', 'ready:wasm']);

function modelAnswer(block, porn, persons) {
  return { state: 'ready', adult: { inspected: true, block, scores: { Porn: porn, Hentai: 0 } }, persons };
}

for (const [shape, face, expected] of [
  [modelAnswer(false, 0.05), { state: 'ready', persons: { blur: true } }, { v: 'allow', p: true, s: false }],
  [modelAnswer(false, 0.05), { state: 'unknown' }, 'unknown'],
  [modelAnswer(true, 0.93), null, { v: 'block', p: false, s: true }],
]) {
  modelAnswers = [modelAnswer(true, 0.94, { state: 'not-inspected' }), shape];
  if (face) modelAnswers.push(face);
  calls.length = 0;
  await window.hisnInspect('frame', 10, 20, Buffer.alloc(10 * 20 * 4).toString('base64'));
  const [id, answer] = answers.shift();
  assert.equal(id, 'frame');
  assert.deepEqual(answer === 'unknown' ? answer : JSON.parse(answer), expected);
  assert.equal(calls.length, face ? 3 : 2);
  if (face) assert.deepEqual([calls[2].inspectVisual, calls[2].personBlur], [false, 'all']);
}
console.log('region crop and Android visual bridge tests passed');
