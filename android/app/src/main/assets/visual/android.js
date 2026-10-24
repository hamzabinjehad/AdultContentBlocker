import { createVisualEngine } from './engine.bundle.js';

// Only packaged code runs in this WebView. Captures exist in memory for one request.
const preferences = { inspectVisual: true, personBlur: 'off' };
const backend = new URL(location.href).searchParams.get('backend') === 'webgl' ? 'webgl' : 'cpu';
const engine = createVisualEngine({ baseURL: 'https://hisn.invalid/visual/', backend });
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
  const status = engine.status(preferences);
  HisnResult.result('load', status.state === 'ready' ? `ready:${status.backend}` : 'unknown');
} catch { HisnResult.result('load', 'unknown'); }
