// The full frame is what the model sees. A small explicit region can be a minority of it,
// so an uncertain full-frame verdict gets one closer look on a centred crop, not a lower
// block threshold. The crop is resized by the model to its 224 input, so the region occupies
// more of that input. Pure functions: the WebView glue and a Node test share the same code.

export const NEAR_PORN = 0.45;
export const NEAR_HENTAI = 0.45;
export const NEAR_SUM = 0.60;
export const CROP_FRACTION = 0.68;

/** Scores below the engine's block threshold that are worth a closer look. */
export function nearMiss(adult) {
  const scores = adult?.scores;
  return !!scores && (scores.Porn >= NEAR_PORN || scores.Hentai >= NEAR_HENTAI
    || scores.Porn + scores.Hentai >= NEAR_SUM);
}

export const STRONG = 0.90;
export const STRONG_SUM = 0.95;

/** A full-frame block so far above the threshold that one capture is enough to act on. */
export function strongBlock(adult) {
  const scores = adult?.scores;
  return adult?.block === true && !!scores && (scores.Porn >= STRONG || scores.Hentai >= STRONG
    || scores.Porn + scores.Hentai >= STRONG_SUM);
}

/** Centred RGBA crop of a Base64 frame, or null when the frame is malformed. */
export function centreCrop(rgba, width, height) {
  if (typeof rgba !== 'string' || !Number.isInteger(width) || !Number.isInteger(height)
      || width < 1 || height < 1) return null;
  let bytes;
  try { bytes = Uint8Array.from(atob(rgba), char => char.charCodeAt(0)); }
  catch { return null; }
  if (bytes.length !== width * height * 4) return null;
  const cropWidth = Math.max(1, Math.round(width * CROP_FRACTION));
  const cropHeight = Math.max(1, Math.round(height * CROP_FRACTION));
  const left = Math.floor((width - cropWidth) / 2);
  const top = Math.floor((height - cropHeight) / 2);
  const cropped = new Uint8Array(cropWidth * cropHeight * 4);
  for (let row = 0; row < cropHeight; row++) {
    const source = ((top + row) * width + left) * 4;
    cropped.set(bytes.subarray(source, source + cropWidth * 4), row * cropWidth * 4);
  }
  bytes.fill(0);
  let binary = '';
  // Chunked conversion keeps the argument count bounded on a large frame.
  for (let index = 0; index < cropped.length; index += 0x2000) {
    binary += String.fromCharCode.apply(null, cropped.subarray(index, Math.min(index + 0x2000, cropped.length)));
  }
  cropped.fill(0);
  try { return { width: cropWidth, height: cropHeight, rgba: btoa(binary) }; }
  catch { return null; }
}
