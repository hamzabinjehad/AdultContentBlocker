import assert from 'node:assert/strict';
import { nearMiss, centreCrop, CROP_FRACTION } from '../../android/app/src/main/assets/visual/region.js';

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
console.log('region crop tests passed');
