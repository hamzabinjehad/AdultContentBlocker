// Exercise the canonical engine factory with model transports mocked. Numeric
// model quality is tested separately with the real bundled engine in Helium.
import { adultDecision, appearancePrediction, faceDetections, personDecision,
  squareFrame, unpadFaces, visualPreferences, PERSON_MODES, validateCapture } from '../visual/decisions.js';
import { LocalInferenceQueue } from '../visual/queue.js';
let checks = 0;
const check = (ok, label) => { checks++; if (!ok) throw new Error(label); };
const canonical = readFile('../visual/engine.js').replace(/^import[\s\S]*?;\n/gm, '')
  .replace('export function createVisualEngine', 'function createVisualEngine');
const sourceFactory = (dependencies) => Function(...Object.keys(dependencies), canonical + '\nreturn createVisualEngine;')(...Object.values(dependencies));
const classes = ['Porn', 'Hentai', 'Sexy', 'Neutral', 'Drawing'];
const capture = (width = 1, height = 1) => ({ id: 'test', width, height, rgba: btoa('\0'.repeat(width * height * 4)) });
function harness({ explicit = false, failedFaceLoad = false, malformedFaces = false, probabilities = [.92, .08] } = {}) {
  const calls = { adult: 0, face: 0, gender: 0, disposed: 0, pads: [], faceShape: null };
  class Tensor {
    constructor(shape, data = null) { this.shape = shape; this.values = data; }
    slice(_begin, size) { return new Tensor(this.shape.map((edge, index) => size[index] < 0 ? edge : size[index])); }
    toFloat() { return this; }
    expandDims() { return new Tensor([1, ...this.shape]); }
    mul() { return this; }
    sum() { return new Tensor([1, 64, 64, 1]); }
    div() { return this; }
    sub() { return this; }
    data() { return Promise.resolve(this.values); }
    dispose() { calls.disposed++; }
  }
  const tf = { setBackend: async () => true, getBackend: () => 'cpu', ready: async () => {},
    tidy: fn => fn(), tensor3d: (_bytes, shape) => new Tensor(shape), tensor1d: values => new Tensor([values.length]),
    pad: (image, pads) => { calls.pads.push(pads); return new Tensor(image.shape.map((edge, index) => edge + pads[index][0] + pads[index][1])); },
    image: { cropAndResize: () => new Tensor([1, 64, 64, 3]) },
    loadGraphModel: async () => ({ execute: () => { calls.gender++; return new Tensor([1, 2], probabilities); } }),
  };
  class Human {
    models = { stats: () => ({ modelStats: [{ name: 'blazeface', loaded: !failedFaceLoad, sizeLoadedWeights: failedFaceLoad ? 0 : 2000 }] }) };
    async load() {}
    async detect(image) {
      calls.face++; calls.faceShape = image.shape;
      return { face: malformedFaces ? [null] : [{ boxScore: .95, box: [51, 10, 40, 40] }] };
    }
  }
  const loadNSFW = async () => ({ classify: async () => {
    calls.adult++; const scores = explicit ? [.8, .02, .01, .16, .01] : [.01, .01, .01, .96, .01];
    return classes.map((className, index) => ({ className, probability: scores[index] }));
  } });
  const createVisualEngine = sourceFactory({ tf, Human, loadNSFW, MobileNetV2Model: {},
    adultDecision, appearancePrediction, faceDetections, personDecision, squareFrame, unpadFaces,
    visualPreferences, PERSON_MODES, validateCapture, LocalInferenceQueue });
  return { engine: createVisualEngine({ baseURL: 'https://local.test/', backend: 'cpu' }), calls };
}
{
  const h = harness();
  check(h.engine.status().state === 'disabled', 'engine defaults off without model work');
  check((await h.engine.inspect('disabled', capture(), {})).state === 'disabled' && h.calls.face === 0 && h.calls.adult === 0,
    'disabled preferences do not classify media');
}
{
  const h = harness({ explicit: true, malformedFaces: true });
  const result = await h.engine.inspect('adult', capture(), { inspectVisual: true, personBlur: 'all' });
  check(result.state === 'ready' && result.adult.block && h.calls.face === 0,
    'positive adult verdict cannot be replaced by an optional malformed face verdict');
  check(h.calls.disposed === 1, 'adult short-circuit releases captured tensor');
}
{
  const h = harness({ explicit: true, failedFaceLoad: true });
  const result = await h.engine.inspect('adult-face-load', capture(), { inspectVisual: true, personBlur: 'female' });
  check(result.state === 'ready' && result.adult.block && h.calls.face === 0,
    'a successfully loaded adult layer can still block when face asset loading fails');
  check(h.engine.status({ personBlur: 'female' }).state === 'failed', 'face asset failure is still disclosed as failed');
}
{
  const h = harness({ failedFaceLoad: true });
  const result = await h.engine.inspect('partial', capture(), { inspectVisual: true, personBlur: 'all' });
  check(result.state === 'unknown' && result.reason === 'model-load-failed', 'clean adult verdict plus failed person layer is unknown, not certified clean');
}
{
  const h = harness({ malformedFaces: true });
  const result = await h.engine.inspect('invalid-face', capture(), { personBlur: 'all' });
  check(result.state === 'unknown' && result.reason === 'invalid-face-output', 'canonical factory rejects malformed face output without inventing a no-face result');
  check(h.calls.disposed === 2, 'face-error path releases image and letterbox tensors');
}
{
  const h = harness(); const result = await h.engine.inspect('portrait', capture(101, 224), { personBlur: 'female' });
  check(h.calls.faceShape.join(',') === '224,224,3' && h.calls.pads[0][1].join(',') === '61,62',
    'canonical detector receives aspect-preserving letterbox including asymmetric odd padding');
  check(result.persons.blur && result.persons.regions[0].x === 0, 'selected high-confidence estimate maps back into original capture');
}
{
  const h = harness({ probabilities: [.9, .9] });
  const result = await h.engine.inspect('invalid-appearance', capture(101, 224), { personBlur: 'female' });
  check(!result.persons.blur && h.calls.gender === 1, 'invalid high-confidence appearance probabilities cannot trigger selected person blur');
}
print(`  ${checks}/${checks} canonical visual engine checks passed`);
