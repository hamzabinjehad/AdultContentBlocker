// Hisn Vault — primitives.
//
// Everything here runs in the tab. There is no server, no key escrow and no
// network call: the vault file is the only copy, and it is ciphertext.
//
// What each piece is for:
//   * AES-256-GCM        seals the code itself under a random 32-byte vault key
//   * P-256 ECDH + HKDF  wraps that vault key to a partner's public key
//   * tlock (lib/tlock)  wraps the same vault key to a future drand round
//
// The partner path and the time path both end at the same vault key, so either
// one opens the vault — and neither one can be undone by the person alone.

const TEXT = new TextEncoder();

export const AAD_PAYLOAD = 'hisn-vault/payload/v1';
export const AAD_PARTNER = 'hisn-vault/partner/v1';
export const HKDF_INFO = TEXT.encode('hisn-vault/partner/v1');

// ---------------------------------------------------------------- encoding

export function b64u(bytes) {
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let binary = '';
  for (let i = 0; i < view.length; i += 0x8000) {
    binary += String.fromCharCode.apply(null, view.subarray(i, i + 0x8000));
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function fromB64u(text) {
  if (typeof text !== 'string' || !/^[A-Za-z0-9_-]*$/.test(text)) {
    throw new Error('not base64url');
  }
  const padded = text.replace(/-/g, '+').replace(/_/g, '/') +
    '='.repeat((4 - (text.length % 4)) % 4);
  const binary = atob(padded);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) out[i] = binary.charCodeAt(i);
  return out;
}

export function toHex(bytes) {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

export function concat(...parts) {
  const total = parts.reduce((n, p) => n + p.length, 0);
  const out = new Uint8Array(total);
  let at = 0;
  for (const part of parts) { out.set(part, at); at += part.length; }
  return out;
}

export function randomBytes(count) {
  const out = new Uint8Array(count);
  crypto.getRandomValues(out);
  return out;
}

export async function sha256(bytes) {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', bytes));
}

// ---------------------------------------------------------------- the code

const GUESSABLE = new Set([
  '0000', '1111', '2222', '3333', '4444', '5555', '6666', '7777', '8888', '9999',
  '1234', '4321', '1212', '2121', '1122', '6969', '0420', '2580', '1010', '0101',
  '1020', '2020', '2024', '2025', '2026', '2000', '1990', '1980',
]);

/** True for codes a stranger, or the person at 2 a.m., would try first. */
export function isGuessable(code) {
  if (!/^\d{4}$/.test(code)) return true;
  if (GUESSABLE.has(code)) return true;
  const digits = code.split('').map(Number);
  const ascending = digits.every((d, i) => i === 0 || d === (digits[i - 1] + 1) % 10);
  const descending = digits.every((d, i) => i === 0 || d === (digits[i - 1] + 9) % 10);
  if (ascending || descending) return true;
  if (code[0] === code[2] && code[1] === code[3]) return true; // abab
  if (code.slice(0, 2) === code.slice(2)) return true;         // abab again
  return false;
}

/** Uniform 4-digit code, rejecting the patterns above (no modulo bias). */
export function makePasscode(attempts = 64) {
  const limit = 10000 - (10000 % 10000); // identity, kept explicit
  for (let i = 0; i < attempts; i += 1) {
    const n = crypto.getRandomValues(new Uint32Array(1))[0] % limit;
    const code = String(n).padStart(4, '0');
    if (!isGuessable(code)) return code;
  }
  throw new Error('could not draw a non-guessable code');
}

// ---------------------------------------------------------------- AES-GCM

export async function aesSeal(keyBytes, plaintext, aad) {
  const key = await crypto.subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['encrypt']);
  const iv = randomBytes(12);
  const ct = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, additionalData: TEXT.encode(aad) },
    key,
    plaintext,
  );
  return { alg: 'A256GCM', iv: b64u(iv), ct: b64u(new Uint8Array(ct)) };
}

/** Throws if the bytes, the IV or the additional data changed. */
export async function aesOpen(keyBytes, sealed, aad) {
  const key = await crypto.subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['decrypt']);
  const pt = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: fromB64u(sealed.iv), additionalData: TEXT.encode(aad) },
    key,
    fromB64u(sealed.ct),
  );
  return new Uint8Array(pt);
}

// ---------------------------------------------------------------- partner keys

export async function makePartnerKeypair() {
  const pair = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits'],
  );
  return {
    publicJwk: await crypto.subtle.exportKey('jwk', pair.publicKey),
    privateJwk: await crypto.subtle.exportKey('jwk', pair.privateKey),
  };
}

export async function publicRawFromJwk(jwk) {
  const key = await crypto.subtle.importKey(
    'jwk', { kty: 'EC', crv: 'P-256', x: jwk.x, y: jwk.y, ext: true },
    { name: 'ECDH', namedCurve: 'P-256' }, true, [],
  );
  return new Uint8Array(await crypto.subtle.exportKey('raw', key));
}

function publicJwkFromPrivate(jwk) {
  return { kty: 'EC', crv: 'P-256', x: jwk.x, y: jwk.y, ext: true };
}

async function hkdfKey(shared, salt) {
  const base = await crypto.subtle.importKey('raw', shared, 'HKDF', false, ['deriveKey']);
  return crypto.subtle.deriveKey(
    { name: 'HKDF', hash: 'SHA-256', salt, info: HKDF_INFO },
    base,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
}

async function partnerAad(epkRaw, partnerRaw) {
  return `${AAD_PARTNER}|${b64u(epkRaw)}|${b64u(partnerRaw)}`;
}

/**
 * Wrap `bytes` to a partner's public key.
 * The ephemeral private key is discarded after this call.
 */
export async function partnerSeal(partnerPublicJwk, bytes) {
  const partnerRaw = await publicRawFromJwk(partnerPublicJwk);
  const partnerKey = await crypto.subtle.importKey(
    'jwk', partnerPublicJwk, { name: 'ECDH', namedCurve: 'P-256' }, false, [],
  );
  const ephemeral = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits'],
  );
  const epkRaw = new Uint8Array(await crypto.subtle.exportKey('raw', ephemeral.publicKey));
  const shared = new Uint8Array(await crypto.subtle.deriveBits(
    { name: 'ECDH', public: partnerKey }, ephemeral.privateKey, 256,
  ));
  const salt = await sha256(concat(epkRaw, partnerRaw));
  const key = await hkdfKey(shared, salt);
  const iv = randomBytes(12);
  const ct = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, additionalData: TEXT.encode(await partnerAad(epkRaw, partnerRaw)) },
    key,
    bytes,
  );
  return {
    v: 1,
    alg: 'P256-ECDH-HKDF-SHA256-A256GCM',
    pk: b64u(partnerRaw),
    epk: b64u(epkRaw),
    iv: b64u(iv),
    ct: b64u(new Uint8Array(ct)),
  };
}

/** Open a partner envelope with the partner's private key. Throws on any change. */
export async function partnerOpen(partnerPrivateJwk, envelope) {
  if (!envelope || envelope.alg !== 'P256-ECDH-HKDF-SHA256-A256GCM') {
    throw new Error('unsupported partner envelope');
  }
  const partnerRaw = await publicRawFromJwk(publicJwkFromPrivate(partnerPrivateJwk));
  if (envelope.pk !== b64u(partnerRaw)) throw new Error('this envelope is for another partner key');
  const epkRaw = fromB64u(envelope.epk);
  const ephemeralKey = await crypto.subtle.importKey(
    'raw', epkRaw, { name: 'ECDH', namedCurve: 'P-256' }, false, [],
  );
  const privateKey = await crypto.subtle.importKey(
    'jwk', partnerPrivateJwk, { name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits'],
  );
  const shared = new Uint8Array(await crypto.subtle.deriveBits(
    { name: 'ECDH', public: ephemeralKey }, privateKey, 256,
  ));
  const salt = await sha256(concat(epkRaw, partnerRaw));
  const key = await hkdfKey(shared, salt);
  const pt = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: fromB64u(envelope.iv), additionalData: TEXT.encode(await partnerAad(epkRaw, partnerRaw)) },
    key,
    fromB64u(envelope.ct),
  );
  return new Uint8Array(pt);
}

/** Short, human-checkable name for a partner key: HISN-PK-XXXX-XXXX-XXXX-XXXX. */
export async function partnerFingerprint(publicJwkOrRaw) {
  const raw = publicJwkOrRaw instanceof Uint8Array
    ? publicJwkOrRaw
    : await publicRawFromJwk(publicJwkOrRaw);
  const digest = await sha256(raw);
  const hex = toHex(digest).slice(0, 16).toUpperCase();
  return `HISN-PK-${hex.match(/.{4}/g).join('-')}`;
}
