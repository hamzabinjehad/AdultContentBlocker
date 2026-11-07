import assert from 'node:assert/strict';
import test from 'node:test';

import { loadTlock } from './helpers.mjs';

await loadTlock();

const { b64u, fromB64u, isGuessable, makePasscode, partnerSeal, partnerOpen, makePartnerKeypair, partnerFingerprint } =
  await import('../lib/crypto.js');

test('base64url round-trips arbitrary bytes', () => {
  for (const size of [0, 1, 31, 32, 65, 1000]) {
    const bytes = crypto.getRandomValues(new Uint8Array(size));
    assert.deepEqual(fromB64u(b64u(bytes)), bytes);
  }
  assert.throws(() => fromB64u('not base64!'));
});

test('a drawn code avoids the obvious patterns', () => {
  for (let i = 0; i < 200; i += 1) {
    const code = makePasscode();
    assert.match(code, /^\d{4}$/);
    assert.equal(isGuessable(code), false, code);
  }
  for (const bad of ['0000', '1234', '4321', '1111', '1212', '2026', '9876']) {
    assert.equal(isGuessable(bad), true, bad);
  }
  assert.equal(isGuessable('4071'), false);
  assert.equal(isGuessable('12'), true);
});

test('partner envelope opens only with the partner key', async () => {
  const alice = await makePartnerKeypair();
  const bob = await makePartnerKeypair();
  const secret = crypto.getRandomValues(new Uint8Array(32));

  const envelope = await partnerSeal(alice.publicJwk, secret);
  assert.ok(envelope.pk);
  assert.ok(envelope.epk);

  assert.deepEqual(await partnerOpen(alice.privateJwk, envelope), secret);
  await assert.rejects(() => partnerOpen(bob.privateJwk, envelope), /another partner key/);

  const tampered = { ...envelope, ct: b64u(new Uint8Array(fromB64u(envelope.ct)).map((b) => b ^ 1)) };
  await assert.rejects(() => partnerOpen(alice.privateJwk, tampered));
});

test('a partner fingerprint is stable and readable', async () => {
  const pair = await makePartnerKeypair();
  const one = await partnerFingerprint(pair.publicJwk);
  const two = await partnerFingerprint(pair.publicJwk);
  assert.equal(one, two);
  assert.match(one, /^HISN-PK(-[0-9A-F]{4}){4}$/);
});
