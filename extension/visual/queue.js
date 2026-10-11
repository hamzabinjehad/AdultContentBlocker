// A single inference owns at most one frame; waiting work and cancelled bytes
// are released immediately. No browsing URLs or model results are persisted.
export class LocalInferenceQueue {
  constructor(infer, { limit = 12, maxAgeMs = 12000, now = () => Date.now() } = {}) {
    this.infer = infer;
    this.limit = limit;
    this.maxAgeMs = maxAgeMs;
    this.now = now;
    this.pending = [];
    this.active = null;
  }

  submit(key, payload) {
    if (this.pending.length + (this.active ? 1 : 0) >= this.limit) {
      return Promise.resolve({ state: 'unknown', reason: 'queue-full' });
    }
    return new Promise(resolve => {
      this.pending.push({ key, payload, resolve, created: this.now(), cancelled: false });
      this.drain();
    });
  }

  cancel(key) {
    this.pending = this.pending.filter(job => {
      if (job.key !== key) return true;
      job.payload = null;
      job.resolve({ state: 'unknown', reason: 'cancelled' });
      return false;
    });
    if (this.active?.key === key) this.active.cancelled = true;
  }

  cancelPrefix(prefix) {
    for (const job of this.pending.slice()) if (job.key.startsWith(prefix)) this.cancel(job.key);
    if (this.active?.key.startsWith(prefix)) this.active.cancelled = true;
  }

  cancelAll() {
    for (const job of this.pending) {
      job.payload = null;
      job.resolve({ state: 'unknown', reason: 'cancelled' });
    }
    this.pending = [];
    if (this.active) this.active.cancelled = true;
  }

  async drain() {
    if (this.active) return;
    const job = this.pending.shift();
    if (!job) return;
    this.active = job;
    let result;
    try {
      result = this.now() - job.created > this.maxAgeMs
        ? { state: 'unknown', reason: 'expired' }
        : await this.infer(job.payload);
    } catch {
      result = { state: 'failed', reason: 'inference-failed' };
    }
    job.resolve(job.cancelled ? { state: 'unknown', reason: 'cancelled' } : result);
    job.payload = null;
    this.active = null;
    this.drain();
  }
}
