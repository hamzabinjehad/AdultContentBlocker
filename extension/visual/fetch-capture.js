/**
 * A picture the page cannot read, fetched by the extension instead.
 *
 * A page script may only read an image's pixels when the image's server
 * allows it. Most picture hosts do not, and the scanner's first answer to
 * that (reloading the image in anonymous mode) fails on exactly those hosts,
 * which left the picture hidden for good. Measured in a real browser, every
 * other-site picture on a page stayed hidden.
 *
 * The extension itself may fetch any address it has host access to. This
 * fetches the same address the image shows, decodes it off the page's thread
 * and hands back the same bounded capture the canvas path produces, so the
 * rest of the pipeline cannot tell the two apart.
 *
 * It is the second choice on purpose. The canvas path inspects the very
 * pixels on screen; this inspects a second response for the same address. A
 * server that answers the two differently could show one picture and have
 * another judged. `force-cache` asks for the stored response where there is
 * one, and credentials are sent as the page's own request would send them,
 * to keep the two as close as a second request can be. Any failure here is
 * no verdict, and no verdict keeps the picture hidden.
 */
export const FETCH_EDGE = 384;
export const FETCH_TIMEOUT_MS = 8000;
export const FETCH_MAX_BYTES = 20 * 1000 * 1000;
export const FETCH_MAX_URL = 4096;

export function fetchableImageURL(url) {
  return typeof url === 'string' && url.length <= FETCH_MAX_URL && /^https?:\/\/[^\s]+$/i.test(url);
}

function encode(pixels) {
  const bytes = new Uint8Array(pixels.buffer, pixels.byteOffset, pixels.byteLength);
  if (typeof bytes.toBase64 === 'function') return bytes.toBase64();
  const chunks = [];
  for (let offset = 0; offset < bytes.length; offset += 8192) chunks.push(String.fromCharCode(...bytes.subarray(offset, offset + 8192)));
  return btoa(chunks.join(''));
}

/** `{ width, height, rgba }` for the picture at `url`, or null. Never throws. */
export async function captureFromURL(url, { fetchImpl = globalThis.fetch, decode = globalThis.createImageBitmap,
  Canvas = globalThis.OffscreenCanvas, edge = FETCH_EDGE, timeoutMs = FETCH_TIMEOUT_MS, maxBytes = FETCH_MAX_BYTES } = {}) {
  if (!fetchableImageURL(url) || typeof fetchImpl !== 'function' || typeof decode !== 'function' || typeof Canvas !== 'function') return null;
  const controller = typeof AbortController === 'function' ? new AbortController() : null;
  const timer = setTimeout(() => controller?.abort(), timeoutMs);
  let bitmap;
  try {
    const response = await fetchImpl(url, { credentials: 'include', cache: 'force-cache', redirect: 'follow', signal: controller?.signal });
    if (!response?.ok) return null;
    const type = response.headers?.get?.('content-type') || '';
    // A server that names a type must name an image. One that names none is
    // left to the decoder, which accepts only real images.
    if (type && !/^image\//i.test(type)) return null;
    const declared = Number(response.headers?.get?.('content-length'));
    if (Number.isFinite(declared) && declared > maxBytes) return null;
    const blob = await response.blob();
    if (!blob?.size || blob.size > maxBytes) return null;
    bitmap = await decode(blob);
    if (!(bitmap?.width > 0 && bitmap.height > 0)) return null;
    const scale = Math.min(1, edge / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(1, Math.round(bitmap.width * scale)), height = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = new Canvas(width, height);
    const context = canvas.getContext('2d', { willReadFrequently: true });
    if (!context) return null;
    context.drawImage(bitmap, 0, 0, width, height);
    const pixels = context.getImageData(0, 0, width, height).data;
    const rgba = encode(pixels);
    pixels.fill(0);
    return { width, height, rgba };
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
    try { bitmap?.close?.(); } catch { /* already released */ }
  }
}
