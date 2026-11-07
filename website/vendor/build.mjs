// Rebuilds ../lib/tlock.bundle.js from the pinned dependency.
//
//     cd website/vendor && pnpm install && node build.mjs
//
// esbuild-wasm is used instead of native esbuild so the build has no
// postinstall binary to fetch or allow.
import * as esbuild from 'esbuild-wasm';
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
await esbuild.initialize({});

const result = await esbuild.build({
  entryPoints: [path.join(here, 'entry.js')],
  bundle: true,
  format: 'iife',
  globalName: 'HisnTlockBundle',
  platform: 'browser',
  target: ['chrome100', 'safari15', 'firefox100'],
  minify: true,
  legalComments: 'external',
  outfile: path.join(here, 'dist', 'tlock.bundle.js'),
  write: false,
  define: { global: 'globalThis', 'process.env.NODE_ENV': '"production"' },
});

const js = result.outputFiles.find((file) => file.path.endsWith('.js'));
const legal = result.outputFiles.find((file) => file.path.endsWith('.LEGAL.txt'));
await writeFile(path.join(here, '..', 'lib', 'tlock.bundle.js'), js.contents);
if (legal) await writeFile(path.join(here, '..', 'lib', 'tlock.bundle.js.LEGAL.txt'), legal.contents);
console.log(`wrote ${js.contents.length} bytes`);
