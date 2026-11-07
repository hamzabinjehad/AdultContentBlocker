// Loads the browser tlock bundle in Node for the test suite. The bundle is an
// IIFE that publishes window.HisnTlock, so a fake window is enough.
export async function loadTlock() {
  if (!globalThis.HisnTlock) {
    globalThis.window = globalThis;
    await import('../lib/tlock.bundle.js');
  }
  if (!globalThis.HisnTlock) throw new Error('tlock bundle did not publish HisnTlock');
  return globalThis.HisnTlock;
}

export function hasNetwork() {
  return process.env.HISN_SKIP_NETWORK !== '1';
}
