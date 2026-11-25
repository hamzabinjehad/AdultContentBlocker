/**
 * A frame from a video the page cannot read.
 *
 * A page may only read a video's pixels when the video's server allows it.
 * Where it does not, the scanner had no verdict at all, and a video with no
 * verdict stays hidden: measured in a real browser, such a video played with
 * sound and never appeared.
 *
 * The extension may fetch any address it has host access to. This fetches the
 * start of the same file, plays it muted inside the extension's own private
 * document (where its pixels are readable), and captures one frame from the
 * first seconds as an ordinary bounded capture.
 *
 * What this is and is not. It is a verdict on an early frame of the file at
 * that address, fetched a second time. It is not continuous monitoring: a
 * readable video is sampled every two seconds of playback, this one is judged
 * once per source. That is weaker, and it is still far better than either
 * showing an unchecked video or hiding every such video for good. Any failure
 * here is no verdict, and no verdict keeps the video hidden.
 *
 * Runs only where there is a document (the offscreen page, or a background
 * page). Captures are kept for two minutes by address so that a seek or a
 * player rebuild does not download the file again; only pixels of one frame
 * are held, never the video.
 */
import { fetchableImageURL } from './fetch-capture.js';

export const VIDEO_FETCH_BYTES = 8 * 1000 * 1000;
export const VIDEO_TIMEOUT_MS = 12000;
export const VIDEO_KEEP_MS = 2 * 60 * 1000;
export const VIDEO_KEEP_ENTRIES = 8;
const EDGE = 384;
const kept = new Map();

function encode(pixels) {
  const bytes = new Uint8Array(pixels.buffer, pixels.byteOffset, pixels.byteLength);
  if (typeof bytes.toBase64 === 'function') return bytes.toBase64();
  const chunks = [];
  for (let offset = 0; offset < bytes.length; offset += 8192) chunks.push(String.fromCharCode(...bytes.subarray(offset, offset + 8192)));
  return btoa(chunks.join(''));
}

/** Where to look: a little way in, never past what was downloaded. */
export function frameTime(duration, bufferedEnd) {
  const available = Math.min(Number.isFinite(duration) && duration > 0 ? duration : Infinity,
    Number.isFinite(bufferedEnd) && bufferedEnd > 0 ? bufferedEnd : Infinity);
  if (!Number.isFinite(available)) return 0;
  return Math.max(0, Math.min(3, available * 0.5));
}

const once = (target, names, milliseconds) => new Promise(resolve => {
  const done = event => { clearTimeout(timer); for (const name of names) target.removeEventListener(name, done); resolve(event?.type ?? 'timeout'); };
  const timer = setTimeout(done, milliseconds);
  for (const name of names) target.addEventListener(name, done, { once: true });
});

async function capture(url, doc, fetchImpl) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), VIDEO_TIMEOUT_MS);
  let objectURL, video;
  try {
    const response = await fetchImpl(url, { credentials: 'include', cache: 'force-cache', redirect: 'follow',
      headers: { Range: `bytes=0-${VIDEO_FETCH_BYTES - 1}` }, signal: controller.signal });
    if (!response?.ok) return null;
    const type = response.headers.get('content-type') || '';
    if (type && !/^(video\/|application\/octet-stream|binary\/octet-stream)/i.test(type)) return null;
    // A server that ignores the range sends the whole file: read no more than the bound.
    const reader = response.body?.getReader?.();
    if (!reader) return null;
    const parts = []; let size = 0;
    while (size < VIDEO_FETCH_BYTES) {
      const { done, value } = await reader.read();
      if (done) break;
      parts.push(value); size += value.byteLength;
    }
    reader.cancel().catch(() => {});
    if (!size) return null;
    objectURL = URL.createObjectURL(new Blob(parts, { type: /^video\//i.test(type) ? type : 'video/mp4' }));
    video = doc.createElement('video');
    video.muted = true; video.preload = 'auto'; video.playsInline = true;
    video.src = objectURL;
    if (await once(video, ['loadeddata', 'error'], 6000) !== 'loadeddata') return null;
    const end = video.buffered.length ? video.buffered.end(video.buffered.length - 1) : 0;
    const target = frameTime(video.duration, end);
    if (target > 0.05) {
      video.currentTime = target;
      if (await once(video, ['seeked', 'error'], 4000) !== 'seeked') return null;
    }
    const width = video.videoWidth, height = video.videoHeight;
    if (!(width > 0 && height > 0)) return null;
    const scale = Math.min(1, EDGE / Math.max(width, height));
    const canvas = doc.createElement('canvas');
    canvas.width = Math.max(1, Math.round(width * scale)); canvas.height = Math.max(1, Math.round(height * scale));
    const context = canvas.getContext('2d', { willReadFrequently: true });
    if (!context) return null;
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
    const result = { width: canvas.width, height: canvas.height, rgba: encode(pixels) };
    pixels.fill(0); canvas.width = canvas.height = 0;
    return result;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
    try { if (video) { video.removeAttribute('src'); video.load(); } } catch { /* released */ }
    if (objectURL) URL.revokeObjectURL(objectURL);
  }
}

/** `{ width, height, rgba }` for an early frame of the video at `url`, or null. Never throws. */
export async function captureVideoFromURL(url, { doc = globalThis.document, fetchImpl = globalThis.fetch, now = () => Date.now() } = {}) {
  if (!fetchableImageURL(url) || !doc?.createElement || typeof fetchImpl !== 'function') return null;
  const at = now();
  for (const [key, entry] of kept) if (at - entry.at >= VIDEO_KEEP_MS) kept.delete(key);
  const held = kept.get(url);
  if (held) return held.pending;
  const pending = capture(url, doc, fetchImpl);
  kept.set(url, { at, pending });
  while (kept.size > VIDEO_KEEP_ENTRIES) kept.delete(kept.keys().next().value);
  // A failure is not kept: the next request may succeed.
  pending.then(result => { if (!result && kept.get(url)?.pending === pending) kept.delete(url); });
  return pending;
}

/*
 * Following a video while it plays.
 *
 * One early frame (above) misses whatever comes later in the video, and it was
 * the only verdict such a video ever got. The extension's own page can read a
 * frame of any video it has host access to, other sites included, and can seek
 * it. So it keeps a muted, silent copy of the page's video and, for each
 * sample, shows the frame at the time the page is playing. The browser fetches
 * only the ranges it needs for those times, from its cache where it can.
 *
 * Copies are dropped after a minute without a request, and at most
 * MIRROR_ENTRIES are kept. Any failure is no verdict, which keeps the video
 * covered on the page.
 */
export const MIRROR_ENTRIES = 3;
export const MIRROR_IDLE_MS = 60 * 1000;
export const MIRROR_LOAD_MS = 8000;
export const MIRROR_SEEK_MS = 4000;
const mirrors = new Map();

function dropMirror(url) {
  const entry = mirrors.get(url);
  if (!entry) return;
  mirrors.delete(url);
  clearTimeout(entry.idle);
  try { entry.video.removeAttribute('src'); entry.video.load(); } catch { /* released */ }
}

function mirrorFor(url, doc) {
  let entry = mirrors.get(url);
  if (!entry) {
    const video = doc.createElement('video');
    video.muted = true; video.preload = 'auto'; video.playsInline = true;
    video.src = url;
    entry = { video, ready: once(video, ['loadeddata', 'error'], MIRROR_LOAD_MS), busy: Promise.resolve() };
    mirrors.set(url, entry);
    while (mirrors.size > MIRROR_ENTRIES) dropMirror(mirrors.keys().next().value);
  } else {
    // Most recently used last, so the oldest copy is the one dropped.
    mirrors.delete(url); mirrors.set(url, entry);
  }
  clearTimeout(entry.idle);
  entry.idle = setTimeout(() => dropMirror(url), MIRROR_IDLE_MS);
  return entry;
}

/** `{ width, height, rgba }` for the frame at `at` seconds of the video at `url`, or null. Never throws. */
export async function captureVideoFrameAt(url, at, { doc = globalThis.document } = {}) {
  if (!fetchableImageURL(url) || !doc?.createElement || !Number.isFinite(at) || at < 0) return null;
  const entry = mirrorFor(url, doc);
  // One seek at a time per copy: requests for the same video wait their turn.
  const turn = entry.busy.then(async () => {
    try {
      if (await entry.ready !== 'loadeddata') { dropMirror(url); return null; }
      const video = entry.video;
      const target = Number.isFinite(video.duration) && video.duration > 0 ? Math.min(at, Math.max(0, video.duration - 0.05)) : at;
      if (Math.abs(video.currentTime - target) > 0.04) {
        video.currentTime = target;
        if (await once(video, ['seeked', 'error'], MIRROR_SEEK_MS) !== 'seeked') return null;
      }
      const width = video.videoWidth, height = video.videoHeight;
      if (!(width > 0 && height > 0)) return null;
      const scale = Math.min(1, EDGE / Math.max(width, height));
      const canvas = doc.createElement('canvas');
      canvas.width = Math.max(1, Math.round(width * scale)); canvas.height = Math.max(1, Math.round(height * scale));
      try {
        const context = canvas.getContext('2d', { willReadFrequently: true });
        if (!context) return null;
        context.drawImage(video, 0, 0, canvas.width, canvas.height);
        const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
        const result = { width: canvas.width, height: canvas.height, rgba: encode(pixels) };
        pixels.fill(0);
        return result;
      } finally { canvas.width = canvas.height = 0; }
    } catch { return null; }
  });
  entry.busy = turn.catch(() => null);
  return turn;
}
