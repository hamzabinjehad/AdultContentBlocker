import * as tf from '@tensorflow/tfjs';
import { setWasmPaths } from '@tensorflow/tfjs-backend-wasm';
import { load as loadNSFW } from 'nsfwjs/core';
import { MobileNetV2Model } from 'nsfwjs/models/mobilenet_v2';
import Human from '@vladmandic/human';
import { adultDecision, appearancePrediction, needsAppearanceConfirmation, confirmAppearance, faceDetections, personDecision, squareFrame, unpadFaces,
  visualPreferences, PERSON_MODES, validateCapture, decodeCapture, needsFaceres, needsOarriaga, faceresPrediction } from './decisions.js';
import { LocalInferenceQueue } from './queue.js';

// WebGL is the fastest browser backend when the platform exposes a usable
// context. WebAssembly is the fast fallback where it does not (an unattached
// Android WebView has no GPU surface), and the plain JS CPU backend is last.
// A requested backend is tried first, then the rest of this ladder, so a
// missing graphics or WASM capability degrades instead of disabling the layer.
const BACKENDS = Object.freeze(['webgl', 'wasm', 'cpu']);
// The adult model looks at a 224x224 square: NSFWJS stretches any other input
// to it. Handing it that square ourselves matters for speed, not only for
// size. The GPU backend compiles a program per input shape, and pictures on a
// page nearly all differ in size: measured in Helium, each new size cost 0.5
// to 3.7 seconds, against 20-35 ms for a shape already seen. One fixed shape
// is compiled once, at load.
const ADULT_EDGE = 224;

// The capture stretched to the model's square on a 2D canvas, the same
// stretch NSFWJS applies itself. Null where no canvas exists (the caller then
// passes the capture as it is, which NSFWJS resizes on its own).
function adultSquare(bytes, width, height) {
  if (width === ADULT_EDGE && height === ADULT_EDGE) return null;
  if (typeof OffscreenCanvas !== 'function' || typeof ImageData !== 'function') return null;
  const source = new OffscreenCanvas(width, height), square = new OffscreenCanvas(ADULT_EDGE, ADULT_EDGE);
  try {
    const from = source.getContext('2d'), to = square.getContext('2d', { willReadFrequently: true });
    if (!from || !to) return null;
    from.putImageData(new ImageData(new Uint8ClampedArray(bytes.buffer, bytes.byteOffset, bytes.length), width, height), 0, 0);
    to.imageSmoothingEnabled = true; to.imageSmoothingQuality = 'medium';
    to.drawImage(source, 0, 0, ADULT_EDGE, ADULT_EDGE);
    const pixels = to.getImageData(0, 0, ADULT_EDGE, ADULT_EDGE).data;
    return new Uint8Array(pixels.buffer, pixels.byteOffset, pixels.byteLength);
  } catch { return null; }
  finally { source.width = source.height = square.width = square.height = 0; }
}

// The face detector has the same cost per input shape. Each capture is padded,
// never resampled, into the smallest of these squares that holds it: the
// detector sees exactly the pixels it saw before, and compiles for at most six
// shapes instead of one per picture size. Neighbouring sizes differ by at most
// a quarter, so a picture fills at least 80% of its square. (Scaling every
// capture up to one square was tried: the second resampling lost borderline
// faces that the padded original found.)
const FACE_EDGES = Object.freeze([128, 160, 192, 240, 288, 384]);

function faceFrame(width, height) {
  const frame = squareFrame(width, height);
  if (!frame) return null;
  const edge = FACE_EDGES.find(candidate => candidate >= frame.edge) ?? frame.edge;
  return { edge, x: Math.floor((edge - width) / 2), y: Math.floor((edge - height) / 2) };
}

export function createVisualEngine({ baseURL, backend = 'auto' } = {}) {
  if (typeof baseURL !== 'string' || !baseURL.endsWith('/')) throw new Error('Missing packaged model base');
  if (!['auto', ...BACKENDS].includes(backend)) throw new Error('Unsupported visual backend');
  let adult = null;
  let human = null;
  let gender = null;
  // Whether the loaded Human instance also runs the FaceRes description model.
  let humanDescription = false;
  let backendReady = null;
  let loading = null;
  let currentState = 'loading';
  let failure = null;
  let selected = visualPreferences();
  let epoch = 0;

  async function selectBackend() {
    const order = backend === 'auto' ? BACKENDS : backend === 'cpu' ? ['cpu']
      : [backend, ...BACKENDS.filter(candidate => candidate !== backend)];
    for (const candidate of order) {
      try {
        if (candidate === 'wasm') setWasmPaths(`${baseURL}wasm/`);
        if (!(await tf.setBackend(candidate))) continue;
        await tf.ready();
        return candidate;
      } catch { /* The next backend in the ladder may still work. */ }
    }
    await tf.setBackend('cpu');
    await tf.ready();
    return 'cpu';
  }

  async function prepare(preferences, expectedEpoch = epoch) {
    if (expectedEpoch !== epoch) return;
    selected = visualPreferences(preferences);
    if (!selected.inspectVisual && selected.personBlur === 'off') return;
    if (loading) { await loading; return prepare(preferences, expectedEpoch); }
    const needAdult = selected.inspectVisual && !adult;
    const wantDescription = needsFaceres(selected);
    const needHuman = selected.personBlur !== 'off' && (!human || (wantDescription && !humanDescription));
    const needGender = needsOarriaga(selected) && !gender;
    if (!needAdult && !needHuman && !needGender) { currentState = 'ready'; return; }
    currentState = 'loading';
    failure = null;
    loading = (async () => {
      if (!backendReady) backendReady = selectBackend();
      await backendReady;
      if (expectedEpoch !== epoch) return;
      // NSFWJS's loader already runs one zero-tensor prediction as its own
      // warm-up, so the first real capture is not paying kernel startup here.
      if (needAdult) {
        const model = await loadNSFW('MobileNetV2', { modelDefinitions: [MobileNetV2Model] });
        // Compile the one input shape every capture is turned into, so the
        // first picture of a page does not pay for it.
        let warm;
        try {
          warm = tf.tidy(() => tf.zeros([ADULT_EDGE, ADULT_EDGE, 4], 'int32').slice([0, 0, 0], [-1, -1, 3]));
          await model.classify(warm, 5);
        } catch { /* warm-up only */ } finally { warm?.dispose(); }
        adult = model;
      }
      if (expectedEpoch !== epoch) return;
      if (needHuman) {
        const candidate = new Human({ backend: tf.getBackend(), modelBasePath: `${baseURL}models/`,
          debug: false, cacheModels: false, validateModels: true,
          async: false, warmup: 'none', skipAllowed: false, cacheSensitivity: 0,
          filter: { enabled: false, return: false }, gesture: { enabled: false },
          face: { enabled: true,
            // Evaluation runs see faces down to 0.30 so detection thresholds can be compared offline;
            // personDecision still ignores faces under 0.60, so the decision is unchanged.
            detector: { modelPath: 'blazeface.json', maxDetected: 10,
              minConfidence: selected.evaluateAppearance ? 0.30 : 0.60, iouThreshold: 0.30,
              square: false, rotation: false, return: false, skipFrames: 0, skipTime: 0 },
            mesh: { enabled: false }, attention: { enabled: false }, iris: { enabled: false },
            // FaceRes runs on Human's own aligned face crop, which is what it was trained on.
            description: wantDescription
              ? { enabled: true, modelPath: 'faceres.json', minConfidence: 0, skipFrames: 0, skipTime: 0 }
              : { enabled: false },
            emotion: { enabled: false },
            antispoof: { enabled: false }, liveness: { enabled: false }, gear: { enabled: false } },
          body: { enabled: false }, hand: { enabled: false }, object: { enabled: false },
          segmentation: { enabled: false } });
        await candidate.load();
        // Compile the detector for the largest square, the one most captures use.
        let warm;
        try {
          warm = tf.tidy(() => tf.zeros([FACE_EDGES.at(-1), FACE_EDGES.at(-1), 3], 'int32'));
          await candidate.detect(warm);
        } catch { /* warm-up only */ } finally { warm?.dispose(); }
        // Human can resolve load() after a swallowed asset error. Do not
        // advertise a constructed object as a successfully loaded detector.
        const detector = candidate.models.stats().modelStats.find(model => model.name === 'blazeface');
        if (!detector?.loaded || !(detector.sizeLoadedWeights > 0)) throw new Error('face-model-load-failed');
        if (wantDescription) {
          const description = candidate.models.stats().modelStats.find(model => model.name === 'faceres');
          if (!description?.loaded || !(description.sizeLoadedWeights > 0)) throw new Error('face-model-load-failed');
        }
        human = candidate;
        humanDescription = wantDescription;
      }
      if (expectedEpoch !== epoch) return;
      if (needGender) gender = await tf.loadGraphModel(`${baseURL}models/gender.json`);
      currentState = 'ready';
    })().catch(() => {
      currentState = 'failed'; failure = 'model-load-failed';
      throw new Error(failure);
    }).finally(() => { loading = null; });
    return loading;
  }

  async function infer({ capture, preferences }) {
    if (!validateCapture(capture)) return { state: 'unknown', reason: 'invalid-capture' };
    const prefs = visualPreferences(preferences);
    if (!prefs.inspectVisual && prefs.personBlur === 'off') return { state: 'disabled' };
    try { await prepare(prefs); } catch (error) {
      // Independent layers: a missing face asset must not throw away an adult
      // classifier that already loaded. A clean partial result is still unknown.
      if (!prefs.inspectVisual || !adult) throw error;
    }
    if (!selected.inspectVisual && selected.personBlur === 'off') return { state: 'disabled' };
    const bytes = decodeCapture(capture.rgba);
    if (!bytes || bytes.length !== capture.width * capture.height * 4) return { state: 'unknown', reason: 'invalid-capture' };
    let tensor, faceTensor;
    try {
      const whole = () => tensor ??= tf.tidy(() => tf.tensor3d(bytes, [capture.height, capture.width, 4], 'int32').slice([0, 0, 0], [-1, -1, 3]));
      let adultResult = { block: false, scores: null, inspected: false };
      let persons = { blur: false, mode: prefs.personBlur, regions: [], estimated: true };
      if (prefs.inspectVisual) {
        const square = adultSquare(bytes, capture.width, capture.height);
        let input;
        let predictions;
        try {
          input = square ? tf.tidy(() => tf.tensor3d(square, [ADULT_EDGE, ADULT_EDGE, 4], 'int32').slice([0, 0, 0], [-1, -1, 3])) : null;
          predictions = await adult.classify(input ?? whole(), 5);
        } finally { input?.dispose(); square?.fill(0); }
        const decision = adultDecision(predictions);
        if (!decision) return { state: 'unknown', reason: 'invalid-model-output' };
        adultResult = { ...decision, inspected: true };
        // A positive adult verdict is sufficient. An optional face model error
        // must never undo this independent blocking layer.
        if (decision.block) return { state: 'ready', adult: adultResult,
          persons: { ...persons, state: 'not-inspected' } };
      }
      if (prefs.personBlur !== 'off') {
        if (!human || (needsOarriaga(prefs) && !gender) || (needsFaceres(prefs) && !humanDescription)) {
          return { state: 'unknown', reason: 'model-load-failed', adult: adultResult };
        }
        const frame = faceFrame(capture.width, capture.height);
        faceTensor = tf.tidy(() => tf.pad(whole(), [[frame.y, frame.edge - capture.height - frame.y],
          [frame.x, frame.edge - capture.width - frame.x], [0, 0]]));
        // Human's default detector resizes to a square. Our own letterbox keeps
        // face proportions without depending on its square-coordinate path.
        const result = await human.detect(faceTensor);
        // The detector is deliberately face-only; no embeddings, age, race or
        // face identity models are loaded. This is not a full-body guarantee.
        const faces = faceDetections(result);
        if (!faces) return { state: 'unknown', reason: 'invalid-face-output' };
        const evaluation = prefs.evaluateAppearance ? [] : null;
        if (['female', 'male'].includes(prefs.personBlur)) {
          for (const face of faces) {
            if (evaluation) {
              // Raw estimates from both models, for accuracy runs; the decision below is unchanged.
              const raw = await rawOarriaga(faceTensor, face.box, frame.edge, frame.edge, gender);
              if (!raw) return { state: 'unknown', reason: 'invalid-appearance-output', adult: adultResult };
              const faceres = faceresPrediction(face.description);
              evaluation.push({ w: face.box[2], h: face.box[3], faceScore: face.faceScore,
                oarriaga: raw.primary, mirrored: raw.mirrored,
                faceresMale: faceres ? (faceres.gender === 'male' ? faceres.genderScore : 1 - faceres.genderScore) : null,
                age: face.description?.age ?? null });
            }
            if (prefs.appearance === 'faceres') {
              // No estimate is not evidence either way; personDecision covers such a face.
              const prediction = faceresPrediction(face.description);
              if (prediction) Object.assign(face, prediction);
            } else {
              const prediction = await predictGender(faceTensor, face.box, frame.edge, frame.edge, gender, prefs.personBlur);
              if (!prediction) return { state: 'unknown', reason: 'invalid-appearance-output', adult: adultResult };
              Object.assign(face, prediction);
            }
          }
        }
        persons = personDecision(unpadFaces(faces, frame, capture.width, capture.height), [],
          prefs.personBlur, capture.width, capture.height);
        if (evaluation) persons.evaluation = evaluation;
      }
      return { state: 'ready', adult: adultResult, persons };
    } finally {
      bytes.fill(0);
      faceTensor?.dispose();
      tensor?.dispose();
    }
  }

  const queue = new LocalInferenceQueue(infer);
  return {
    inspect: (key, capture, preferences) => queue.submit(key, { capture, preferences }),
    cancel: key => queue.cancel(key), cancelPrefix: prefix => queue.cancelPrefix(prefix),
    disable: () => { epoch++; selected = visualPreferences(); queue.cancelAll(); },
    prepare,
    status: (expected = selected) => {
      const required = visualPreferences(expected);
      const missing = (required.inspectVisual && !adult) || (required.personBlur !== 'off' && !human)
        || (needsOarriaga(required) && !gender) || (needsFaceres(required) && !humanDescription);
      return { state: !required.inspectVisual && required.personBlur === 'off' ? 'disabled'
        : missing ? failure ? 'failed' : loading ? 'loading' : 'waiting' : 'ready',
      available: true, reason: failure || undefined,
      supportedPersonModes: [...PERSON_MODES], personCoverage: 'visible-faces-only',
      backend: backendReady ? tf.getBackend() : null, localOnly: true };
    },
  };
}

// Both Oarriaga outputs (original and mirrored crop), unrounded, for accuracy runs only.
async function rawOarriaga(image, box, width, height, model) {
  if (!Array.isArray(box) || box.length !== 4 || !box.every(Number.isFinite)) return null;
  const x1 = Math.max(0, Math.min(1, box[0] / width));
  const y1 = Math.max(0, Math.min(1, box[1] / height));
  const x2 = Math.max(x1, Math.min(1, (box[0] + box[2]) / width));
  const y2 = Math.max(y1, Math.min(1, (box[1] + box[3]) / height));
  let input, output, mirrored, second;
  try {
    input = tf.tidy(() => {
      const crop = tf.image.cropAndResize(image.toFloat().expandDims(0), [[y1, x1, y2, x2]], [0], [64, 64]);
      const gray = crop.mul(tf.tensor1d([0.2989, 0.5870, 0.1140])).sum(3, true);
      return gray.div(127.5).sub(1);
    });
    output = model.execute(input);
    mirrored = tf.reverse(input, [2]);
    second = model.execute(mirrored);
    return { primary: Array.from(await output.data()), mirrored: Array.from(await second.data()) };
  } finally { input?.dispose(); output?.dispose(); mirrored?.dispose(); second?.dispose(); }
}

async function predictGender(image, box, width, height, model, mode) {
  if (!Array.isArray(box) || box.length !== 4 || !box.every(Number.isFinite)) return null;
  const x1 = Math.max(0, Math.min(1, box[0] / width));
  const y1 = Math.max(0, Math.min(1, box[1] / height));
  const x2 = Math.max(x1, Math.min(1, (box[0] + box[2]) / width));
  const y2 = Math.max(y1, Math.min(1, (box[1] + box[3]) / height));
  let input;
  let output;
  let mirrored;
  let confirmation;
  try {
    input = tf.tidy(() => {
      const crop = tf.image.cropAndResize(image.toFloat().expandDims(0), [[y1, x1, y2, x2]], [0], [64, 64]);
      // Same 64x64 greyscale and [-1,1] normalization as the Oarriaga model.
      const gray = crop.mul(tf.tensor1d([0.2989, 0.5870, 0.1140])).sum(3, true);
      return gray.div(127.5).sub(1);
    });
    output = model.execute(input);
    const probabilities = await output.data();
    const prediction = appearancePrediction(probabilities);
    if (!needsAppearanceConfirmation(prediction, mode)) return prediction;
    // At most one extra 64x64 classifier pass, only before excluding a face.
    // No second full-image detector, network request or frame persistence.
    // Mirroring leaves the original mask coordinates unchanged.
    mirrored = tf.reverse(input, [2]);
    confirmation = model.execute(mirrored);
    return confirmAppearance(probabilities, await confirmation.data());
  } finally { input?.dispose(); output?.dispose(); mirrored?.dispose(); confirmation?.dispose(); }
}
