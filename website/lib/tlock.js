// drand quicknet (the league of randomness beacon tlock-js is built around).
// These are public, fixed parameters: sealing a vault works offline because
// only the public key is needed. Opening needs the beacon for the round, which
// the threshold network cannot produce before that round's time.
export const QUICKNET = Object.freeze({
  hash: '52db9ba70e0cc0f6eaf7803dd07447a1f5477735fd3f661792ba94600c84e971',
  public_key: '83cf0f2896adee7eb8b5f01fcad3912212c437e0073e911fb90022d3e760183c8c4b450b6a0a6c3ac6a5776a2d1064510d1fec758c921cc22b0e17e63aaf4bcb5ed66304de9cf809bd274ca73bab4af5a6e9c76a4bc09e76eae8991ef5ece45a',
  period: 3,
  genesis_time: 1692803367,
  schemeID: 'bls-unchained-g1-rfc9380',
  groupHash: 'f477d5c89f21a17c863a7f937c6a6d15859414d2be09cd448d4279af331c5d3e',
});
export const DRAND_API = 'https://api.drand.sh';
const USER_AGENT = 'hisn-vault/1';

function lib() {
  const api = globalThis.HisnTlock;
  if (!api) throw new Error('the tlock bundle is not loaded');
  return api;
}

export function isTimeLockAvailable() {
  return Boolean(globalThis.HisnTlock);
}

/** The smallest round whose emission time is not before `dateMs`. */
export function roundForDate(dateMs) {
  const seconds = Math.floor(dateMs / 1000);
  const offset = (seconds - QUICKNET.genesis_time) / QUICKNET.period;
  return Math.max(1, Math.ceil(offset) + 1);
}

export function dateForRound(round) {
  return (QUICKNET.genesis_time + (round - 1) * QUICKNET.period) * 1000;
}

// A chain client that answers info() from the pinned parameters and refuses
// every fetch. Timelock *encryption* never needs the network, so a vault can be
// sealed on a plane; only opening fetches the beacon.
function offlineClient() {
  const info = { ...QUICKNET };
  return {
    chain: () => ({ info: async () => info }),
    get: async () => { throw new Error('sealing does not read beacons'); },
    latest: async () => { throw new Error('sealing does not read beacons'); },
  };
}

function beaconClient() {
  const T = lib();
  const options = {
    chainVerificationParams: { chainHash: QUICKNET.hash, publicKey: QUICKNET.public_key },
  };
  const chain = new T.HttpCachingChain(`${DRAND_API}/${QUICKNET.hash}`, options, { userAgent: USER_AGENT });
  return new T.HttpChainClient(chain, options, { userAgent: USER_AGENT });
}

export async function sealToFuture(bytes, revealAtMs) {
  const T = lib();
  const round = roundForDate(revealAtMs);
  const armor = await T.timelockEncrypt(round, T.Buffer.from(bytes), offlineClient());
  return { round, armor };
}

export async function openFromFuture(armor) {
  const T = lib();
  const opened = await T.timelockDecrypt(armor, beaconClient());
  return new Uint8Array(opened);
}
