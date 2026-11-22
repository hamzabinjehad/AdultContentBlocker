import { createVisualEngine } from './engine.bundle.js';
import { nearMiss, centreCrop, strongBlock, padSquare, agrees } from './region.js';

// Only packaged code runs in this WebView. Captures exist in memory for one request.
const query = new URL(location.href).searchParams;
const requested = query.get('backend');
const person = ['all', 'female', 'male'].includes(query.get('person')) ? query.get('person') : 'off';
// adult=off: the native TFLite runtime judges adult content and this page only looks for faces,
// so the NSFWJS model is never loaded and each frame runs the face models alone.
const facesOnly = query.get('adult') === 'off' && person !== 'off';
// appearance=faceres picks Human's FaceRes for the women/men estimate; eval=1 (accuracy runs only)
// also returns each face's raw estimates from both models. Neither changes the adult check.
const appearance = query.get('appearance') === 'faceres' ? 'faceres' : 'oarriaga';
const evaluate = query.get('eval') === '1';
// boxes=1 (with adult=off and person=all): return each detected face's normalized box so the
// native FaceRes runtime can estimate women/men; this page then decides nothing about faces.
const boxes = facesOnly && person === 'all' && query.get('boxes') === '1';
const preferences = { inspectVisual: !facesOnly, personBlur: person, appearance, evaluateAppearance: evaluate };
const facePreferences = { ...preferences, inspectVisual: false };
// The centred near-miss second look is an adult check only; do not run the face models twice.
const closerPreferences = { inspectVisual: true, personBlur: 'off' };
// 'auto' walks WebGL -> WASM -> CPU. An explicit name is tried first and still
// degrades, so an unattached WebView without a GPU finishes on WASM instead of
// the two-second JS CPU path.
const backend = ['auto', 'webgl', 'wasm', 'cpu'].includes(requested) ? requested : 'auto';
const engine = createVisualEngine({ baseURL: 'https://hisn.invalid/visual/', backend });
// Scores the same region once more with its shape kept. Null when the frame is already square
// or the look could not be completed; neither cancels a block.
async function shaped(id, width, height, rgba) {
  const padded = padSquare(rgba, width, height);
  if (!padded) return null;
  try {
    const view = await engine.inspect(id, { id, ...padded }, closerPreferences);
    return view?.state === 'ready' && view.adult?.inspected === true ? view.adult : null;
  } catch { return null; }
}
window.hisnInspect = async (id, width, height, rgba) => {
  try {
    const result = await engine.inspect(id, { id, width, height, rgba }, preferences);
    if (facesOnly) {
      // No adult verdict here; "allow" only says the face check completed.
      const done = result?.state === 'ready' && result.persons && result.persons.state !== 'not-inspected';
      HisnResult.result(id, done ? JSON.stringify({ v: 'allow', p: result.persons.blur === true, s: false,
        ...(evaluate ? { e: result.persons.evaluation ?? [] } : {}),
        ...(boxes ? { b: (result.persons.regions ?? []).map(r => [r.x, r.y, r.width, r.height]) } : {}) }) : 'unknown');
      return;
    }
    let verdict = result?.state === 'ready' && result.adult?.inspected === true
      ? result.adult.block === true ? 'block' : 'allow' : 'unknown';
    let strong = false;
    if (verdict === 'block') {
      // A stretched tall screen makes ordinary photos score high: a clean shape-keeping view
      // cancels the block, and only a block both views agree on skips the second capture.
      const view = width === height ? result.adult : await shaped(id + '-s', width, height, rgba);
      if (!agrees(view)) verdict = 'allow';
      else strong = strongBlock(result.adult) && view?.block === true;
    } else if (verdict === 'allow' && nearMiss(result.adult)) {
      const crop = centreCrop(rgba, width, height);
      if (crop) {
        try {
          const closer = await engine.inspect(id + '-c',
            { id: id + '-c', width: crop.width, height: crop.height, rgba: crop.rgba }, closerPreferences);
          if (closer?.state === 'ready' && closer.adult?.inspected === true && closer.adult.block === true
              && agrees(crop.width === crop.height ? closer.adult
                : await shaped(id + '-cs', crop.width, crop.height, crop.rgba))) {
            verdict = 'block';
          }
        } catch {
          // The first frame already allowed it; a failed closer look must not turn that into unknown.
        }
      }
    }
    let persons = result?.persons;
    if (verdict === 'allow' && person !== 'off' && (!persons || persons.state === 'not-inspected')) {
      // The shared engine can skip faces on an initial adult verdict. If the shape check
      // cleared it, finish checking faces before this frame can be shown.
      const faceId = id + '-f';
      const faceResult = await engine.inspect(faceId, { id: faceId, width, height, rgba }, facePreferences);
      persons = faceResult?.persons;
      if (faceResult?.state !== 'ready' || !persons || persons.state === 'not-inspected') {
        HisnResult.result(id, 'unknown');
        return;
      }
    }
    const personBlur = persons?.blur === true;
    HisnResult.result(id, JSON.stringify({ v: verdict, p: personBlur, s: strong,
      ...(evaluate ? { e: persons?.evaluation ?? [] } : {}) }));
  } catch { HisnResult.result(id, 'unknown'); }
  finally { rgba = null; }
};
try {
  await engine.prepare(preferences);
  const status = engine.status(preferences);
  HisnResult.result('load', status.state === 'ready' ? `ready:${status.backend}` : 'unknown');
} catch { HisnResult.result('load', 'unknown'); }
