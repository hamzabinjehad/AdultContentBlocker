// Decisions consume actual model outputs. No text, skin-colour, or identity heuristic.
export const PERSON_MODES = Object.freeze(['off', 'all', 'female', 'male']);
export function presentationPreferences(state = {}) {
  return {personRegion:['full','faces'].includes(state.personRegion) ? state.personRegion : 'full',
    personEffect:['solid','blur'].includes(state.personEffect) ? state.personEffect : 'solid',
    personMedia:['both','images','videos'].includes(state.personMedia) ? state.personMedia : 'both'};
}
export const MAX_EDGE = 384;
export const MAX_RGBA_BYTES = MAX_EDGE * MAX_EDGE * 4;
const CLASSES = ['Porn', 'Hentai', 'Sexy', 'Neutral', 'Drawing'];
const OPPOSITE_CONFIDENCE = 0.80;

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
  const faces = [];
  for (const face of result.face) {
    const score = face?.boxScore ?? face?.score;
    if (!Number.isFinite(score) || score < 0 || score > 1 || !validBox(face?.box)) return null;
    faces.push({ faceScore: score, box: face.box });
  }
  return faces;
}

// Pad, do not stretch, portrait and landscape captures for the square detector.
// Keeping this geometry outside TensorFlow makes its coordinate contract testable.
export function squareFrame(width, height) {
  if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1
      || width > MAX_EDGE || height > MAX_EDGE) return null;
  const edge = Math.max(width, height);
  return { edge, x: Math.floor((edge - width) / 2), y: Math.floor((edge - height) / 2) };
}

export function unpadFaces(faces, frame, width, height) {
  return faces.map(face => ({ ...face, box: [face.box[0] - frame.x, face.box[1] - frame.y,
    face.box[2], face.box[3]] })).filter(face => normalizedRegion(face.box, width, height));
}

export function appearancePrediction(probabilities) {
  if (!probabilities || probabilities.length !== 2) return null;
  const values = Array.from(probabilities);
  if (!values.every(value => Number.isFinite(value) && value >= 0 && value <= 1)
      || Math.abs(values[0] + values[1] - 1) > 0.02) return null;
  // A tied output does not express a selected appearance. Leave this unknown
  // rather than inventing a female estimate from the model's output order.
  if (Math.abs(values[0] - values[1]) < 0.000001) return null;
  const index = values[1] > values[0] ? 1 : 0;
  return { gender: index === 0 ? 'female' : 'male', genderScore: values[index] };
}

export function needsAppearanceConfirmation(prediction, mode) {
  return ['female', 'male'].includes(mode) && ['female', 'male'].includes(prediction?.gender)
    && prediction.gender !== mode && Number.isFinite(prediction.genderScore)
    && prediction.genderScore >= OPPOSITE_CONFIDENCE && prediction.genderScore <= 1;
}

// Appearance estimates are not a person's actual gender. Only exclude a face
// when original and mirrored crops agree confidently. Preserve the original
// estimate and explicitly record uncertainty, rather than invent a new label.
export function confirmAppearance(primary, mirrored) {
  const first = appearancePrediction(primary), second = appearancePrediction(mirrored);
  if (!first || !second) return null;
  return { ...first, appearanceUncertain: first.gender !== second.gender
    || Math.min(first.genderScore, second.genderScore) < OPPOSITE_CONFIDENCE };
}

export function personDecision(faces, bodies, mode, width, height) {
  const regions = [];
  if (!PERSON_MODES.includes(mode) || mode === 'off') return { blur: false, mode: 'off', regions, estimated: true };
  for (const face of Array.isArray(faces) ? faces : []) {
    if (!Number.isFinite(face.faceScore) || face.faceScore < 0.60) continue;
    // A weak estimate in either direction is not evidence that a face falls
    // outside the selected preference. Withhold uncertain faces too, without
    // asserting their gender. Only a confident opposite estimate is excluded.
    // This recall-biased policy can also hide unintended faces, as disclosed
    // beside the opt-in controls. It is not a full-body/identity guarantee.
    if (mode !== 'all' && (!['female', 'male'].includes(face.gender) || !Number.isFinite(face.genderScore)
        || face.genderScore < 0.50 || face.genderScore > 1
        || (face.gender !== mode && face.genderScore >= OPPOSITE_CONFIDENCE && face.appearanceUncertain !== true))) continue;
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
  if (!validBox(box) || !Number.isFinite(width) || !Number.isFinite(height) || width < 1 || height < 1) return null;
  const x = Math.max(0, Math.min(width, box[0])) / width;
  const y = Math.max(0, Math.min(height, box[1])) / height;
  const w = Math.max(0, Math.min(width, box[0] + box[2]) / width - x);
  const h = Math.max(0, Math.min(height, box[1] + box[3]) / height - y);
  return w > 0 && h > 0 ? { x, y, width: w, height: h } : null;
}

function validBox(box) {
  return Array.isArray(box) && box.length === 4 && box.every(Number.isFinite) && box[2] > 0 && box[3] > 0;
}
