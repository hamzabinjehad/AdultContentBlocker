// Build-time only. Runtime never downloads executable code or model weights.
import { build } from 'esbuild';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = path.dirname(fileURLToPath(import.meta.url));
const revision = 'bc66dc53bac03c96d35a7e6daaf717e72f3985f5';
const base = `https://raw.githubusercontent.com/vladmandic/human-models/${revision}/`;
await mkdir(path.join(root, 'models'), { recursive: true });
await mkdir(path.join(root, 'notices'), { recursive: true });
const artifacts = {};
async function obtain(name, url) {
  const result = await fetch(url, { signal: AbortSignal.timeout(30000) });
  if (!result.ok) throw new Error(`Required asset ${name}: HTTP ${result.status}`);
  const data = Buffer.from(await result.arrayBuffer());
  if (!data.length || data.length > 15000000) throw new Error(`Invalid asset size ${name}`);
  await writeFile(path.join(root, name), data);
  artifacts[name] = { url, bytes: data.length, sha256: createHash('sha256').update(data).digest('hex') };
  return data;
}
for (const model of ['blazeface', 'gender']) {
  const json = await obtain(`models/${model}.json`, `${base}models/${model}.json`);
  for (const shard of JSON.parse(json).weightsManifest.flatMap(group => group.paths)) {
    if (!/^[a-zA-Z0-9_.-]+\.bin$/.test(shard)) throw new Error('Unsafe weight path');
    await obtain(`models/${shard}`, `${base}models/${shard}`);
  }
}
await obtain('notices/human-models-MIT.txt', `${base}LICENSE`);
await obtain('notices/face-classification-MIT.txt', 'https://raw.githubusercontent.com/oarriaga/face_classification/master/LICENSE');
await obtain('notices/mediapipe-Apache-2.0.txt', 'https://raw.githubusercontent.com/google-ai-edge/mediapipe/v0.10.21/LICENSE');
await obtain('notices/tensorflowjs-Apache-2.0.txt', 'https://raw.githubusercontent.com/tensorflow/tfjs/tfjs-v4.22.0/LICENSE');
for (const [pkg, file] of [['nsfwjs', 'LICENSE'], ['@vladmandic/human', 'LICENSE'], ['buffer', 'LICENSE']]) {
  const data = await readFile(path.join(root, 'node_modules', pkg, file));
  const name = 'notices/' + pkg.replaceAll('/', '-') + '-LICENSE.txt';
  await writeFile(path.join(root, name), data);
  artifacts[name] = { bytes: data.length, sha256: createHash('sha256').update(data).digest('hex'), source: `${pkg} package license` };
}
await build({ entryPoints: [path.join(root, 'engine.js')], outfile: path.join(root, 'engine.bundle.js'),
  bundle: true, format: 'esm', platform: 'browser', target: ['chrome137', 'safari18'],
  alias: { '@vladmandic/human': path.join(root, 'node_modules/@vladmandic/human/dist/human.esm-nobundle.js') },
  minify: true, legalComments: 'linked', sourcemap: false });
const bundle = await readFile(path.join(root, 'engine.bundle.js'));
artifacts['engine.bundle.js'] = { bytes: bundle.length, sha256: createHash('sha256').update(bundle).digest('hex'),
  source: 'engine.js; pinned pnpm-lock.yaml; NSFWJS MobileNetV2 weights embedded from nsfwjs@4.4.0' };
const legal = await readFile(path.join(root, 'engine.bundle.js.LEGAL.txt'));
artifacts['engine.bundle.js.LEGAL.txt'] = { bytes: legal.length, sha256: createHash('sha256').update(legal).digest('hex'), source: 'esbuild linked dependency notices' };
await writeFile(path.join(root, 'assets.json'), JSON.stringify({ schema: 1, revision,
  dependencies: JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8')).dependencies,
  artifacts }, null, 2) + '\n');
console.log(`Built local visual engine: ${bundle.length} bytes; ${Object.keys(artifacts).length} pinned assets`);
