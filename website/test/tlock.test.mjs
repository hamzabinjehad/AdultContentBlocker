import assert from 'node:assert/strict';
import test from 'node:test';

import { hasNetwork, loadTlock } from './helpers.mjs';

await loadTlock();

const { sealToFuture, openFromFuture, roundForDate, dateForRound, QUICKNET } = await import('../lib/tlock.js');
const vaultLib = await import('../lib/vault.js');

const HOUR = 3600 * 1000;

function isNetworkError(error) {
  return /fetch failed|ConnectTimeout|connect timeout|EAI_AGAIN|ENOTFOUND|ECONNRESET|network|socket hang up/i
    .test(String(error && (error.message || error)));
}

test('sealing needs no network and the beacon opens a past round', async (t) => {
  if (!hasNetwork()) return t.skip('HISN_SKIP_NETWORK=1');

  const key = crypto.getRandomValues(new Uint8Array(32));
  const past = Date.now() - 10 * 60 * 1000;
  const { armor, round } = await sealToFuture(key, past);
  assert.equal(round, roundForDate(past));
  assert.match(armor, /BEGIN AGE ENCRYPTED FILE/);

  let opened;
  try {
    opened = await openFromFuture(armor);
  } catch (error) {
    if (isNetworkError(error)) return t.skip('drand unreachable');
    throw error;
  }
  assert.deepEqual(new Uint8Array(opened), key);
});

test('a future round cannot be opened before its beacon exists', async (t) => {
  if (!hasNetwork()) return t.skip('HISN_SKIP_NETWORK=1');

  const key = crypto.getRandomValues(new Uint8Array(32));
  const future = Date.now() + 2 * HOUR;
  const { armor, round } = await sealToFuture(key, future);
  assert.ok(dateForRound(round) >= Math.floor(future / 1000) * 1000);

  try {
    await openFromFuture(armor);
    assert.fail('a future round opened');
  } catch (error) {
    if (isNetworkError(error)) return t.skip('drand unreachable');
    assert.match(String(error.message), /too early|not found|404/i);
  }
});

test('a whole vault sealed in the past opens on the time path', async (t) => {
  if (!hasNetwork()) return t.skip('HISN_SKIP_NETWORK=1');

  const now = Date.now() - 3 * HOUR;
  const { vault, passcode } = await vaultLib.createVault({ now, revealAt: now + HOUR, mode: 'time' });
  assert.equal(vaultLib.phase(vault, Date.now()).state, 'due');

  let opened;
  try {
    opened = await vaultLib.openWithTime(vault);
  } catch (error) {
    if (isNetworkError(error)) return t.skip('drand unreachable');
    throw error;
  }
  assert.equal(opened.code, passcode);
});

test('quicknet parameters stay pinned', () => {
  assert.equal(QUICKNET.period, 3);
  assert.equal(QUICKNET.schemeID, 'bls-unchained-g1-rfc9380');
  assert.equal(QUICKNET.hash.length, 64);
  assert.equal(QUICKNET.public_key.length, 192);
});
