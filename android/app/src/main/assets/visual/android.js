import { createVisualEngine } from './engine.bundle.js';

// Only packaged code runs in this WebView. Captures exist in memory for one request.
const preferences = { inspectVisual: true, personBlur: 'off' };
const engine = createVisualEngine({ baseURL: 'https://hisn.invalid/visual/', backend: 'cpu' });
window.hisnInspect = async (id, width, height, rgba) => {
  try {
    const result = await engine.inspect(id, { id, width, height, rgba }, preferences);
    const verdict = result?.state === 'ready' && result.adult?.inspected === true
      ? result.adult.block === true ? 'block' : 'allow' : 'unknown';
    HisnResult.result(id, verdict);
  } catch { HisnResult.result(id, 'unknown'); }
  finally { rgba = null; }
};
try {
  await engine.prepare(preferences);
  HisnResult.result('load', engine.status(preferences).state === 'ready' ? 'ready' : 'unknown');
} catch { HisnResult.result('load', 'unknown'); }
