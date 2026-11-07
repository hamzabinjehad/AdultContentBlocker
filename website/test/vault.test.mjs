import assert from 'node:assert/strict';
import test from 'node:test';

import { loadTlock } from './helpers.mjs';

await loadTlock();

const vaultLib = await import('../lib/vault.js');
const tlockLib = await import('../lib/tlock.js');
const { makePartnerKeypair, fromB64u, b64u } = await import('../lib/crypto.js');

const HOUR = 3600 * 1000;

function dates(createdHrsAgo = 0, revealInHrs = 168) {
  const now = Date.now() - createdHrsAgo * HOUR;
  return { now, revealAt: now + revealInHrs * HOUR };
}

test('round math lands on the last beacon at or after the date', () => {
  const date = Date.now() + 7 * 24 * HOUR;
  const round = tlockLib.roundForDate(date);
  assert.ok(tlockLib.dateForRound(round) >= Math.floor(date / 1000) * 1000);
  assert.ok(tlockLib.dateForRound(round) - date < tlockLib.QUICKNET.period * 1000);
  assert.equal(tlockLib.roundForDate(tlockLib.dateForRound(round)), round);
  assert.equal(tlockLib.roundForDate(tlockLib.QUICKNET.genesis_time * 1000), 1);
});

test('a vault in partner mode opens with the partner key and not without it', async () => {
  const alice = await makePartnerKeypair();
  const bob = await makePartnerKeypair();
  const { now, revealAt } = dates();

  const { vault, passcode } = await vaultLib.createVault({
    now, revealAt, mode: 'partner', partnerPublicJwk: alice.publicJwk,
  });

  assert.equal(vault.partner.fingerprint, await vaultLib.partnerFingerprint(alice.publicJwk));
  assert.equal(vault.time, null);
  assert.equal(vaultLib.inspectVault(vault).ok, true);
  assert.equal(vaultLib.roundMatchesDate(vault), true);

  const opened = await vaultLib.openWithPartner(vault, alice.privateJwk);
  assert.equal(opened.code, passcode);

  await assert.rejects(() => vaultLib.openWithPartner(vault, bob.privateJwk), /another partner key/);
});

test('the code is not recoverable from the file without a path', async () => {
  const alice = await makePartnerKeypair();
  const { now, revealAt } = dates();
  const { vault, passcode } = await vaultLib.createVault({
    now, revealAt, mode: 'partner', partnerPublicJwk: alice.publicJwk,
  });

  const text = JSON.stringify(vault);
  assert.equal(text.includes(passcode), false);
  assert.equal(fromB64u(vault.payload.ct).length > 0, true);

  const tampered = { ...vault, payload: { ...vault.payload, ct: b64u(new Uint8Array(fromB64u(vault.payload.ct)).map((b) => b ^ 1)) } };
  await assert.rejects(() => vaultLib.openWithPartner(tampered, alice.privateJwk));
});

test('a vault cannot be built outside the allowed window', async () => {
  const now = Date.now();
  await assert.rejects(() => vaultLib.createVault({ now, revealAt: now - 1000, mode: 'time' }), /future/);
  await assert.rejects(() => vaultLib.createVault({ now, revealAt: now + 400 * 24 * HOUR, mode: 'time' }), /year/);
  await assert.rejects(() => vaultLib.createVault({ now, revealAt: now + HOUR, mode: 'partner' }), /partner key/);
});

test('phase follows the setup window, the date and the reveal', () => {
  const createdAt = Date.parse('2026-11-07T10:00:00Z');
  const vault = {
    createdAt: new Date(createdAt).toISOString(),
    revealAt: new Date(createdAt + 7 * 24 * HOUR).toISOString(),
    sealedAt: null,
    revealedAt: null,
  };
  assert.equal(vaultLib.phase(vault, createdAt + 60_000).state, 'setup');
  assert.equal(vaultLib.phase(vault, createdAt + vaultLib.SETUP_WINDOW_MS).state, 'sealed');
  assert.equal(vaultLib.phase(vault, createdAt + 8 * 24 * HOUR).state, 'due');
  assert.equal(vaultLib.phase({ ...vault, revealedAt: new Date(createdAt + 9 * 24 * HOUR).toISOString() }, createdAt + 10 * 24 * HOUR).state, 'revealed');
  assert.equal(vaultLib.phase({ ...vault, sealedAt: new Date(createdAt + 60_000).toISOString() }, createdAt + 120_000).state, 'sealed');
  // A clock set before creation never reopens the setup window.
  assert.equal(vaultLib.phase(vault, createdAt - 60_000).state, 'sealed');
});

test('an imported file with a round that does not match its date is refused', async () => {
  const { now, revealAt } = dates();
  const { vault } = await vaultLib.createVault({ now, revealAt, mode: 'time' });
  const shifted = { ...vault, round: vault.round - 1000 };
  const result = vaultLib.inspectVault(shifted);
  assert.equal(result.ok, false);
  assert.ok(result.problems.some((p) => /round/.test(p)));
});
