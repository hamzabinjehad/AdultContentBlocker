// Short-lived, in-memory reuse of an identical decoded capture. Entries contain
// a SHA-256 digest and bounded verdict fields, never source URLs or frame bytes.
const MAX_ENTRIES = 128;
const MAX_AGE_MS = 30000;

async function sha256(value) {
  if (!globalThis.crypto?.subtle?.digest || typeof TextEncoder !== 'function') return null;
  const bytes = new TextEncoder().encode(value);
  try {
    const digest = await crypto.subtle.digest('SHA-256', bytes);
    return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  } catch { return null; }
  finally { bytes.fill(0); }
}

function copyVerdict(result, preferences) {
  if (result?.state !== 'ready') return null;
  const adultWanted = preferences?.inspectVisual === true;
  const mode = preferences?.personBlur ?? 'off';
  if (!['off', 'all', 'female', 'male'].includes(mode)) return null;
  if (adultWanted && (typeof result.adult?.block !== 'boolean' || result.adult.inspected !== true)) return null;
  const adult = adultWanted ? { block: result.adult.block, inspected: true, scores: null }
    : { block: false, inspected: false, scores: null };
  if (adultWanted && result.adult.scores && typeof result.adult.scores === 'object') {
    const scores = {};
    for (const name of ['Porn', 'Hentai', 'Sexy', 'Neutral', 'Drawing']) {
      const value = result.adult.scores[name];
      if (!Number.isFinite(value) || value < 0 || value > 1) return null;
      scores[name] = value;
    }
    adult.scores = scores;
  }
  // An independent positive adult verdict is sufficient to keep media hidden.
  // A clean adult result must never stand in for a missing selected face check.
  if (adult.block) return { state: 'ready', adult,
    persons: { blur: false, mode, regions: [], estimated: true, state: 'not-inspected' } };
  const persons = { blur: false, mode, regions: [], estimated: true };
  if (mode !== 'off') {
    if (result.persons?.mode !== mode || typeof result.persons.blur !== 'boolean'
        || !Array.isArray(result.persons.regions) || result.persons.regions.length > 10
        || result.persons.blur !== (result.persons.regions.length > 0)) return null;
    for (const region of result.persons.regions) {
      if (!region || ![region.x, region.y, region.width, region.height].every(Number.isFinite)
          || region.x < 0 || region.y < 0 || region.width <= 0 || region.height <= 0
          || region.x + region.width > 1.001 || region.y + region.height > 1.001) return null;
      persons.regions.push({ x: region.x, y: region.y, width: region.width, height: region.height });
    }
    persons.blur = result.persons.blur;
  }
  return { state: 'ready', adult, persons };
}

export class VisualVerdictCache {
  constructor({ limit = MAX_ENTRIES, ttlMs = MAX_AGE_MS, now = () => Date.now(), digest = sha256 } = {}) {
    this.limit = Number.isInteger(limit) ? Math.max(0, Math.min(MAX_ENTRIES, limit)) : MAX_ENTRIES;
    this.ttlMs = Number.isFinite(ttlMs) ? Math.max(0, Math.min(MAX_AGE_MS, ttlMs)) : MAX_AGE_MS;
    this.now = now; this.digest = digest;
    this.entries = new Map(); this.epoch = 0; this.policyEpoch = 0; this.policy = null;
  }
  begin(policy) {
    const normalized = JSON.stringify(policy);
    if (normalized !== this.policy) { this.policyEpoch++; this.clear(); this.policy = normalized; }
    return { epoch: this.epoch, policyEpoch: this.policyEpoch, policy: this.policy };
  }
  current(ticket) { return !!ticket && ticket.epoch === this.epoch && ticket.policyEpoch === this.policyEpoch && ticket.policy === this.policy; }
  refresh(ticket) {
    // Context creation may clear the cache while retaining the same policy.
    // Callers must independently reject canceled work before refreshing.
    return ticket?.policy === this.policy && ticket.policyEpoch === this.policyEpoch
      ? { epoch: this.epoch, policyEpoch: this.policyEpoch, policy: this.policy } : null;
  }
  clear() { this.epoch++; this.entries.clear(); }
  async fingerprint(capture, ticket, preferences, mediaKind) {
    if (!this.current(ticket)) return null;
    let key;
    try {
      key = await this.digest(JSON.stringify([1, ticket.policy, preferences, mediaKind ?? null,
        capture.width, capture.height, capture.rgba]));
    } catch { return null; }
    return this.current(ticket) && typeof key === 'string' && /^[a-f0-9]{64}$/.test(key) ? key : null;
  }
  get(ticket, key) {
    if (!this.current(ticket) || !key) return null;
    const entry = this.entries.get(key), now = this.now();
    if (!entry) return null;
    if (!Number.isFinite(now) || now < entry.created || now - entry.created >= this.ttlMs) {
      this.entries.delete(key); return null;
    }
    this.entries.delete(key); this.entries.set(key, entry);
    return copyVerdict(entry.result, entry.preferences);
  }
  put(ticket, key, result, preferences) {
    if (!this.current(ticket) || typeof key !== 'string' || !/^[a-f0-9]{64}$/.test(key) || !this.limit || !this.ttlMs) return false;
    const normalized = copyVerdict(result, preferences), now = this.now();
    if (!normalized || !Number.isFinite(now)) return false;
    this.entries.delete(key);
    this.entries.set(key, { result: normalized,
      preferences: { inspectVisual: preferences?.inspectVisual === true, personBlur: preferences?.personBlur ?? 'off' }, created: now });
    while (this.entries.size > this.limit) this.entries.delete(this.entries.keys().next().value);
    return true;
  }
}
