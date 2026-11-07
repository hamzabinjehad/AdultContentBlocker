import { createVisualEngine } from './engine.bundle.js';
import { nearMiss, centreCrop } from './region.js';

// Only packaged code runs in this WebView. Captures exist in memory for one request.
const query = new URL(location.href).searchParams;
const requested = query.get('backend');
const person = ['all', 'female', 'male'].includes(query.get('person')) ? query.get('person') : 'off';
const preferences = { inspectVisual: true, personBlur: person };
// The centred near-miss second look is an adult check only; do not run the face models twice.
const closerPreferences = { inspectVisual: true, personBlur: 'off' };
// 'auto' walks WebGL -> WASM -> CPU. An explicit name is tried first and still
// degrades, so an unattached WebView without a GPU finishes on WASM instead of
// the two-second JS CPU path.
const backend = ['auto', 'webgl', 'wasm', 'cpu'].includes(requested) ? requested : 'auto';
const engine = createVisualEngine({ baseURL: 'https://hisn.invalid/visual/', backend });
window.hisnInspect = async (id, width, height, rgba) => {
  try {
    const result = await engine.inspect(id, { id, width, height, rgba }, preferences);
    let verdict = result?.state === 'ready' && result.adult?.inspected === true
      ? result.adult.block === true ? 'block' : 'allow' : 'unknown';
    if (verdict === 'allow' && nearMiss(result.adult)) {
      const crop = centreCrop(rgba, width, height);
      if (crop) {
        try {
          const closer = await engine.inspect(id + '-c',
            { id: id + '-c', width: crop.width, height: crop.height, rgba: crop.rgba }, closerPreferences);
          if (closer?.state === 'ready' && closer.adult?.inspected === true && closer.adult.block === true) {
            verdict = 'block';
          }
        } catch {
          // The first frame already allowed it; a failed closer look must not turn that into unknown.
        }
      }
    }
    const personBlur = result?.persons?.blur === true;
    HisnResult.result(id, JSON.stringify({ v: verdict, p: personBlur }));
  } catch { HisnResult.result(id, 'unknown'); }
  finally { rgba = null; }
};
try {
  await engine.prepare(preferences);
  const status = engine.status(preferences);
  HisnResult.result('load', status.state === 'ready' ? `ready:${status.backend}` : 'unknown');
} catch { HisnResult.result('load', 'unknown'); }
