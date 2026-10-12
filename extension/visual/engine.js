import * as tf from '@tensorflow/tfjs';
import { load as loadNSFW } from 'nsfwjs/core';
import { MobileNetV2Model } from 'nsfwjs/models/mobilenet_v2';
import Human from '@vladmandic/human';
import { adultDecision, appearancePrediction, faceDetections, personDecision, squareFrame, unpadFaces,
  visualPreferences, PERSON_MODES, validateCapture } from './decisions.js';
import { LocalInferenceQueue } from './queue.js';

export function createVisualEngine({ baseURL, backend = 'webgl' } = {}) {
  if (typeof baseURL !== 'string' || !baseURL.endsWith('/')) throw new Error('Missing packaged model base');
  let adult = null;
  let human = null;
  let gender = null;
  let backendReady = null;
  let loading = null;
  let currentState = 'loading';
  let failure = null;
  let selected = visualPreferences();

  async function prepare(preferences) {
    selected = visualPreferences(preferences);
    if (!selected.inspectVisual && selected.personBlur === 'off') return;
    if (loading) { await loading; return prepare(preferences); }
    const needAdult = selected.inspectVisual && !adult;
    const needHuman = selected.personBlur !== 'off' && !human;
    const needGender = ['female', 'male'].includes(selected.personBlur) && !gender;
    if (!needAdult && !needHuman && !needGender) { currentState = 'ready'; return; }
    currentState = 'loading';
    failure = null;
    loading = (async () => {
      if (!backendReady) backendReady = (async () => {
        let chosen = false;
        try { chosen = await tf.setBackend(backend); } catch { /* no graphics backend */ }
        if (!chosen) await tf.setBackend('cpu');
        await tf.ready();
      })();
      await backendReady;
      if (needAdult) adult = await loadNSFW('MobileNetV2', { modelDefinitions: [MobileNetV2Model] });
      if (needHuman) {
        const candidate = new Human({ backend: tf.getBackend(), modelBasePath: `${baseURL}models/`,
          debug: false, cacheModels: false, validateModels: true,
          async: false, warmup: 'none', skipAllowed: false, cacheSensitivity: 0,
          filter: { enabled: false, return: false }, gesture: { enabled: false },
          face: { enabled: true,
            detector: { modelPath: 'blazeface.json', maxDetected: 10, minConfidence: 0.60,
              square: false, rotation: false, return: false, skipFrames: 0, skipTime: 0 },
            mesh: { enabled: false }, attention: { enabled: false }, iris: { enabled: false },
            description: { enabled: false }, emotion: { enabled: false },
            antispoof: { enabled: false }, liveness: { enabled: false }, gear: { enabled: false } },
          body: { enabled: false }, hand: { enabled: false }, object: { enabled: false },
          segmentation: { enabled: false } });
        await candidate.load();
        // Human can resolve load() after a swallowed asset error. Do not
        // advertise a constructed object as a successfully loaded detector.
        const detector = candidate.models.stats().modelStats.find(model => model.name === 'blazeface');
        if (!detector?.loaded || !(detector.sizeLoadedWeights > 0)) throw new Error('face-model-load-failed');
        human = candidate;
      }
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
    const bytes = Uint8Array.from(atob(capture.rgba), char => char.charCodeAt(0));
    if (bytes.length !== capture.width * capture.height * 4) return { state: 'unknown', reason: 'invalid-capture' };
    let tensor, faceTensor;
    try {
      tensor = tf.tidy(() => tf.tensor3d(bytes, [capture.height, capture.width, 4], 'int32').slice([0, 0, 0], [-1, -1, 3]));
      let adultResult = { block: false, scores: null, inspected: false };
      let persons = { blur: false, mode: prefs.personBlur, regions: [], estimated: true };
      if (prefs.inspectVisual) {
        const decision = adultDecision(await adult.classify(tensor, 5));
        if (!decision) return { state: 'unknown', reason: 'invalid-model-output' };
        adultResult = { ...decision, inspected: true };
        // A positive adult verdict is sufficient. An optional face model error
        // must never undo this independent blocking layer.
        if (decision.block) return { state: 'ready', adult: adultResult,
          persons: { ...persons, state: 'not-inspected' } };
      }
      if (prefs.personBlur !== 'off') {
        if (!human || (['female', 'male'].includes(prefs.personBlur) && !gender)) {
          return { state: 'unknown', reason: 'model-load-failed', adult: adultResult };
        }
        const frame = squareFrame(capture.width, capture.height);
        faceTensor = tf.tidy(() => tf.pad(tensor, [[frame.y, frame.edge - capture.height - frame.y],
          [frame.x, frame.edge - capture.width - frame.x], [0, 0]]));
        // Human's default detector resizes to a square. Our own letterbox keeps
        // face proportions without depending on its square-coordinate path.
        const result = await human.detect(faceTensor);
        // The detector is deliberately face-only; no embeddings, age, race or
        // face identity models are loaded. This is not a full-body guarantee.
        const faces = faceDetections(result);
        if (!faces) return { state: 'unknown', reason: 'invalid-face-output' };
        if (gender && ['female', 'male'].includes(prefs.personBlur)) {
          for (const face of faces) {
            const prediction = await predictGender(faceTensor, face.box, frame.edge, frame.edge, gender);
            Object.assign(face, prediction);
          }
        }
        persons = personDecision(unpadFaces(faces, frame, capture.width, capture.height), [],
          prefs.personBlur, capture.width, capture.height);
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
    disable: () => { selected = visualPreferences(); queue.cancelAll(); },
    prepare,
    status: (expected = selected) => {
      const required = visualPreferences(expected);
      const missing = (required.inspectVisual && !adult) || (required.personBlur !== 'off' && !human)
        || (['female', 'male'].includes(required.personBlur) && !gender);
      return { state: !required.inspectVisual && required.personBlur === 'off' ? 'disabled'
        : missing ? failure ? 'failed' : loading ? 'loading' : 'waiting' : 'ready',
      available: true, reason: failure || undefined,
      supportedPersonModes: [...PERSON_MODES], personCoverage: 'visible-faces-only',
      backend: backendReady ? tf.getBackend() : null, localOnly: true };
    },
  };
}

async function predictGender(image, box, width, height, model) {
  if (!Array.isArray(box) || box.length !== 4 || !box.every(Number.isFinite)) return {};
  const x1 = Math.max(0, Math.min(1, box[0] / width));
  const y1 = Math.max(0, Math.min(1, box[1] / height));
  const x2 = Math.max(x1, Math.min(1, (box[0] + box[2]) / width));
  const y2 = Math.max(y1, Math.min(1, (box[1] + box[3]) / height));
  let input;
  let output;
  try {
    input = tf.tidy(() => {
      const crop = tf.image.cropAndResize(image.toFloat().expandDims(0), [[y1, x1, y2, x2]], [0], [64, 64]);
      // Same 64x64 greyscale and [-1,1] normalization as the Oarriaga model.
      const gray = crop.mul(tf.tensor1d([0.2989, 0.5870, 0.1140])).sum(3, true);
      return gray.div(127.5).sub(1);
    });
    output = model.execute(input);
    const probabilities = await output.data();
    return appearancePrediction(probabilities) ?? {};
  } finally { input?.dispose(); output?.dispose(); }
}
