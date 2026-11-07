// Hisn Vault — the record and the two ways out of it.
//
// A vault holds one code (today, a Screen Time passcode) under a random vault
// key. The vault key is wrapped twice:
//
//   partner  P-256 ECDH to a key that lives on someone else's device
//   time     tlock to the drand round for the chosen date
//
// Opening needs one of the two. The person holding the vault file has neither,
// which is the whole point: deleting an app, clearing storage or reading the
// file does not reveal the code.

import {
  AAD_PAYLOAD, aesOpen, aesSeal, b64u, fromB64u, makePasscode, partnerFingerprint,
  partnerOpen, partnerSeal, randomBytes, sha256,
} from './crypto.js';
import { dateForRound, openFromFuture, roundForDate, sealToFuture } from './tlock.js';

export const VAULT_VERSION = 1;
export const VAULT_KIND = 'hisn-vault';
export const SETUP_WINDOW_MS = 15 * 60 * 1000;
export const DURATIONS = [1, 7, 30, 90];
export const MAX_SEAL_MS = 366 * 86400 * 1000;

const LABEL_SCREEN_TIME = 'Screen Time';

export const MODES = ['both', 'partner', 'time'];

function payloadAad(vault) {
  return `${AAD_PAYLOAD}|${vault.id}|${vault.revealAt}`;
}

function iso(ms) {
  return new Date(ms).toISOString();
}

function checkDate(name, value) {
  const ms = typeof value === 'number' ? value : Date.parse(value);
  if (!Number.isFinite(ms)) throw new Error(`${name} is not a date`);
  return ms;
}

/**
 * Build a vault. Returns the record and the code, which exists in memory only
 * during the setup window (and inside the ciphertext afterwards).
 *
 * mode: 'both' | 'partner' | 'time'
 */
export async function createVault({
  now = Date.now(),
  revealAt,
  label = LABEL_SCREEN_TIME,
  note = '',
  mode = 'time',
  partnerPublicJwk = null,
  partnerFingerprintText = null,
} = {}) {
  const createdAt = checkDate('now', now);
  const reveal = checkDate('revealAt', revealAt);
  if (reveal <= createdAt) throw new Error('the date must be in the future');
  if (reveal - createdAt > MAX_SEAL_MS) throw new Error('the date is more than a year away');
  if (!MODES.includes(mode)) throw new Error('unknown mode');
  if (mode !== 'time' && !partnerPublicJwk) throw new Error('this mode needs a partner key');
  if (typeof label !== 'string' || label.length > 64) throw new Error('label is too long');

  const passcode = makePasscode();
  const vaultKey = randomBytes(32);
  const id = b64u(randomBytes(16));

  const vault = {
    v: VAULT_VERSION,
    kind: VAULT_KIND,
    id,
    createdAt: iso(createdAt),
    revealAt: iso(reveal),
    label,
    note: typeof note === 'string' ? note.slice(0, 280) : '',
    round: roundForDate(reveal),
    payload: null,
    partner: null,
    time: null,
    sealedAt: null,
    revealedAt: null,
  };

  vault.payload = await aesSeal(vaultKey, new TextEncoder().encode(passcode), payloadAad(vault));

  if (mode !== 'time') {
    const fingerprint = partnerFingerprintText || await partnerFingerprint(partnerPublicJwk);
    vault.partner = { fingerprint, envelope: await partnerSeal(partnerPublicJwk, vaultKey) };
  }
  if (mode !== 'partner') {
    vault.time = await sealToFuture(vaultKey, reveal);
  }

  return { vault, passcode };
}

/** Which screen the vault should show. The clock here is only UI; the crypto is the guard. */
export function phase(vault, now = Date.now()) {
  if (vault.revealedAt) {
    return { state: 'revealed', at: Date.parse(vault.revealedAt) };
  }
  const createdAt = Date.parse(vault.createdAt);
  const revealAt = Date.parse(vault.revealAt);
  if (!vault.sealedAt && now >= createdAt && now < createdAt + SETUP_WINDOW_MS) {
    return { state: 'setup', hidesAt: createdAt + SETUP_WINDOW_MS };
  }
  if (now < revealAt) return { state: 'sealed', until: revealAt };
  return { state: 'due', since: revealAt };
}

export function setupWindowLeft(vault, now = Date.now()) {
  const createdAt = Date.parse(vault.createdAt);
  return Math.max(0, createdAt + SETUP_WINDOW_MS - now);
}

export function sealVault(vault, now = Date.now()) {
  if (vault.sealedAt) return vault;
  return { ...vault, sealedAt: iso(checkDate('now', now)) };
}

export function markRevealed(vault, now = Date.now()) {
  return { ...vault, revealedAt: iso(checkDate('now', now)) };
}

/** Both paths end here: turn an opened vault key into the code. */
export async function readCode(vault, vaultKey) {
  if (!(vaultKey instanceof Uint8Array) || vaultKey.length !== 32) throw new Error('bad vault key');
  const bytes = await aesOpen(vaultKey, vault.payload, payloadAad(vault));
  return new TextDecoder().decode(bytes);
}

/** Partner path: works before the date, but only with the partner's private key. */
export async function openWithPartner(vault, partnerPrivateJwk) {
  if (!vault.partner) throw new Error('this vault has no partner path');
  const vaultKey = await partnerOpen(partnerPrivateJwk, vault.partner.envelope);
  if (vaultKey.length !== 32) throw new Error('bad partner envelope');
  return { vaultKey, code: await readCode(vault, vaultKey) };
}

/** Time path: needs the drand beacon for the round; before it, decryption is impossible. */
export async function openWithTime(vault) {
  if (!vault.time) throw new Error('this vault has no time path');
  const vaultKey = await openFromFuture(vault.time.armor);
  return { vaultKey, code: await readCode(vault, vaultKey) };
}

/** Compile-time-ish sanity: the round must be the one this date produces. */
export function roundMatchesDate(vault) {
  return vault.round === roundForDate(Date.parse(vault.revealAt));
}

export function timeLockDate(vault) {
  return dateForRound(vault.round);
}

/** Structural check for an imported file. Does not prove the ciphertext is genuine. */
export function inspectVault(candidate) {
  const problems = [];
  if (!candidate || typeof candidate !== 'object') return { ok: false, problems: ['not an object'] };
  if (candidate.v !== VAULT_VERSION) problems.push('unknown version');
  if (candidate.kind !== VAULT_KIND) problems.push('not a Hisn vault');
  if (typeof candidate.id !== 'string' || candidate.id.length < 16) problems.push('bad id');
  const created = Date.parse(candidate.createdAt);
  const reveal = Date.parse(candidate.revealAt);
  if (!Number.isFinite(created) || !Number.isFinite(reveal)) problems.push('bad dates');
  if (reveal <= created) problems.push('reveal is not after creation');
  if (!candidate.payload || candidate.payload.alg !== 'A256GCM') problems.push('bad payload');
  if (!candidate.partner && !candidate.time) problems.push('no way to open it');
  if (candidate.partner && (!candidate.partner.envelope || !candidate.partner.fingerprint)) {
    problems.push('bad partner path');
  }
  if (candidate.time && (typeof candidate.time.armor !== 'string' || !candidate.time.armor.includes('AGE ENCRYPTED'))) {
    problems.push('bad time path');
  }
  if (!Number.isInteger(candidate.round) || candidate.round < 1) problems.push('bad round');
  else if (Number.isFinite(reveal) && candidate.round !== roundForDate(reveal)) problems.push('round does not match the date');
  return { ok: problems.length === 0, problems };
}

/** A short, stable hash of the whole record, shown so two devices can compare files. */
export async function vaultDigest(vault) {
  const copy = { ...vault, revealedAt: null, sealedAt: null };
  const bytes = new TextEncoder().encode(JSON.stringify(copy));
  return b64u(await sha256(bytes)).slice(0, 16);
}

export function daysFromNow(days, now = Date.now()) {
  return now + days * 86400 * 1000;
}

export { b64u, fromB64u, partnerFingerprint };
