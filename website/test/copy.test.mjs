import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';

import { DICT } from '../lib/copy.js';

test('English and Arabic carry the same keys', () => {
  assert.deepEqual(Object.keys(DICT.ar).sort(), Object.keys(DICT.en).sort());
});

test('every string the pages ask for exists in both languages', () => {
  for (const file of ['app.js', 'partner-app.js']) {
    const source = readFileSync(new URL(`../lib/${file}`, import.meta.url), 'utf8');
    for (const match of source.matchAll(/\bt\('([^']+)'/g)) {
      const key = match[1];
      assert.ok(key in DICT.en, `${file}: missing English ${key}`);
      assert.ok(key in DICT.ar, `${file}: missing Arabic ${key}`);
    }
  }
});
