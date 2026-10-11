// Decisions consume actual model outputs. No text, skin-colour, or identity heuristic.
export const PERSON_MODES = Object.freeze(['off', 'all', 'female', 'male']);
export const MAX_EDGE = 384;
export const MAX_RGBA_BYTES = MAX_EDGE * MAX_EDGE * 4;
const CLASSES = ['Porn', 'Hentai', 'Sexy', 'Neutral', 'Drawing'];

export function visualPreferences(state = {}) {
  return { inspectVisual: state.inspectVisual === true,
    personBlur: PERSON_MODES.includes(state.personBlur) ? state.personBlur : 'off' };
}

export function enabled(state) {
  const prefs = visualPreferences(state);
  return prefs.inspectVisual || prefs.personBlur !== 'off';
}

export function validateCapture(message) {
  if (!message || typeof message !== 'object' || typeof message.id !== 'string'
      || !/^[a-zA-Z0-9_-]{1,80}$/.test(message.id)) return false;
  const { width, height, rgba } = message;
  if (!Number.isInteger(width) || !Number.isInteger(height)
      || width < 1 || height < 1 || width > MAX_EDGE || height > MAX_EDGE) return false;
  const expected = 4 * Math.ceil(width * height * 4 / 3);
  return typeof rgba === 'string' && rgba.length === expected
    && rgba.length <= 4 * Math.ceil(MAX_RGBA_BYTES / 3)
    && /^[A-Za-z0-9+/]*={0,2}$/.test(rgba);
}

export function adultDecision(predictions) {
  const scores = Object.fromEntries(CLASSES.map(name => [name, 0]));
  if (!Array.isArray(predictions) || predictions.length !== CLASSES.length) return null;
  const seen = new Set();
  for (const entry of predictions) {
    if (!CLASSES.includes(entry?.className) || seen.has(entry.className)
        || !Number.isFinite(entry.probability) || entry.probability < 0 || entry.probability > 1) return null;
    seen.add(entry.className);
    scores[entry.className] = entry.probability;
  }
  if (Math.abs(Object.values(scores).reduce((a, b) => a + b, 0) - 1) > 0.02) return null;
  // Conservative initial thresholds, not a claimed accuracy guarantee. Sexy
  // alone is deliberately insufficient: ordinary sport/medical photos matter.
  return { block: scores.Porn >= 0.70 || scores.Hentai >= 0.70
      || scores.Porn + scores.Hentai >= 0.85, scores };
}

// Human's faceScore measures its optional mesh, not the BlazeFace detector.
// Mesh is intentionally disabled here, so its faceScore is always zero.
export function faceDetections(result) {
  if (result?.error || !Array.isArray(result?.face)) return null;
  return result.face.map(face => ({ faceScore: face.boxScore ?? face.score, box: face.box }));
}

export function personDecision(faces, bodies, mode, width, height) {
  const regions = [];
  if (!PERSON_MODES.includes(mode) || mode === 'off') return { blur: false, mode: 'off', regions, estimated: true };
  for (const face of Array.isArray(faces) ? faces : []) {
    if (!Number.isFinite(face.faceScore) || face.faceScore < 0.60) continue;
    if (mode !== 'all' && (face.gender !== mode || !Number.isFinite(face.genderScore) || face.genderScore < 0.80)) continue;
    const region = normalizedRegion(face.box, width, height);
    if (region) regions.push(region);
  }
  if (mode === 'all') {
    for (const body of Array.isArray(bodies) ? bodies : []) {
      if (!Number.isFinite(body.score) || body.score < 0.50) continue;
      const region = normalizedRegion(body.box, width, height);
      if (region) regions.push(region);
    }
  }
  return { blur: regions.length > 0, mode, regions: regions.slice(0, 10), estimated: true };
}

function normalizedRegion(box, width, height) {
  if (!Array.isArray(box) || box.length !== 4 || !box.every(Number.isFinite) || width < 1 || height < 1) return null;
  const x = Math.max(0, Math.min(1, box[0] / width));
  const y = Math.max(0, Math.min(1, box[1] / height));
  const w = Math.max(0, Math.min(1 - x, box[2] / width));
  const h = Math.max(0, Math.min(1 - y, box[3] / height));
  return w > 0 && h > 0 ? { x, y, width: w, height: h } : null;
}
