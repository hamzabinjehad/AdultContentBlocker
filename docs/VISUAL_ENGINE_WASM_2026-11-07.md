# Visual engine: packaged WASM fallback

Status: implemented and measured in headless Chrome and Node, 2026-11-07. This
is a latency and availability change. **It does not change a single verdict,
threshold or model**, and it is not an accuracy result.

## Why

The Android guard runs the shared TensorFlow.js engine inside an isolated,
unattached WebView. A WebView with no GPU surface cannot create the WebGL
backend, so the engine fell back to TensorFlow.js's JavaScript CPU backend. The
[0.5.0 baseline](ANDROID_VISUAL_BENCHMARK_2026-10-22.md) measured a **1,984 ms
median classifier roundtrip** on a Samsung SM-G990B2. The guard asks for a
sample every two seconds, and every one second while the selected window is
scrolling or changing — but a two-second inference cannot honour either
interval: the guard spent most of its life inside one inference, and polling
opportunities were skipped while it was busy.

The bundled `@vladmandic/human` build already imports every TensorFlow.js
backend, including WASM, so the backend code was present all along. What was
missing was (a) selecting it, and (b) shipping its compiled kernel files, which
are separate `.wasm` binaries rather than JavaScript.

## What changed

- `extension/visual/engine.js` now takes `auto` (default), `webgl`, `wasm` or
  `cpu`. `auto` walks **WebGL → WASM → CPU**; a named backend is tried first and
  then the rest of the ladder, so a missing capability degrades instead of
  disabling the layer. An unknown name is rejected instead of silently mapped.
- Before the WASM backend is selected, `setWasmPaths()` points at the packaged
  `visual/wasm/` directory — never a CDN.
- No extra warm-up was added. NSFWJS's own loader already predicts a zero
  tensor before `load()` resolves, and the measured first inspection matched
  the warm median on both WASM and CPU. A second warm-up would have duplicated
  that work.
- `build.mjs` copies three kernel files from the pinned
  `@tensorflow/tfjs-backend-wasm@4.22.0` package into `extension/visual/wasm/`
  and records their bytes and SHA-256 in `assets.json`:

  | file | bytes |
  | --- | --- |
  | `tfjs-backend-wasm.wasm` | 311,123 |
  | `tfjs-backend-wasm-simd.wasm` | 424,594 |
  | `tfjs-backend-wasm-threaded-simd.wasm` | 435,643 |

  All three ship because the backend chooses its variant at load time; a
  missing file would silently drop to CPU. The engine bundle itself changes by
  only 272 bytes (5,518,076 → 5,518,348) because the backend JavaScript was
  already linked through Human.
- `extension/manifest.json` and the Safari manifest allow
  `'wasm-unsafe-eval'` in `script-src`; Android's `android.html` additionally
  allows its intercepted origin in `connect-src`.
- Android's `VisualClassifier` serves `/visual/wasm/*.wasm` with the
  `application/wasm` media type (streaming compilation refuses anything else)
  and accepts `ready:wasm` from the load handshake.
- The Chrome unpacked staging, Safari staging and Android Gradle copy all
  verify and carry the same files.
- `extension/test/browser/visual-backends.html` measures each real backend and
  is wired into `test.sh browser`; it fails if the WASM backend is packaged but
  does not actually run.

## Measurements

Headless Chrome 156 on an arm64 Mac, real timers over loopback, 384×384
synthetic detailed captures, three real-model inspections per backend. Warm
median is the median of the second and third inspections; the first inspection
follows model load (which includes NSFWJS's own warm-up). The default check
measures WASM only; the CPU and WebGL rows are separate `?only=cpu` and
`?only=webgl` runs.

| requested | actual | model load | first inspect | warm median |
| --- | --- | --- | --- | --- |
| `webgl` | **wasm** (no GPU in headless) | 380 ms | 106 ms | 100 ms |
| `wasm` | **wasm** | 296 ms | 96 ms | 97 ms |
| `cpu` | cpu | 1,444 ms | 1,145 ms | 1,111 ms |

The same CPU path measured 1,237–1,255 ms warm on the earlier `visual-real-engine`
smoke test (64×64 captures), so the CPU number is consistent with the recorded
baseline. With `--disable-gpu` the WebGL row is a fallback test, not a WebGL
benchmark.

Node 24.21.0, same pinned packages and model, 224×224 input:

| backend | first inference | warm average |
| --- | --- | --- |
| cpu | 1,122 ms | 1,015 ms |
| wasm | 57 ms | 50 ms |

**WASM is roughly 11× faster than the CPU backend in headless Chrome and about
20× in Node.** The Android device number was not re-measured; the 22 October
harness would need to be run on hardware before any phone claim. The expected
effect is that the two-second Android cadence stops being starved by a
two-second inference, but that is a hypothesis, not a measurement.

A strict page CSP mirroring the packaged policy — `default-src 'none';
script-src 'self' 'wasm-unsafe-eval'; connect-src 'self'` — ran the real WASM
backend and returned an inspected verdict. Branded Chrome 156 refuses
`--load-extension`, so the final navigation check inside a real
`chrome-extension://` page was not available; the CSP and packaged path were
exercised over loopback instead.

## What this does not establish

- **No accuracy claim.** Thresholds, model weights and decision rules are
  unchanged. Adult recall, false positives and the benign medical/art/family
  cases remain unmeasured, exactly as the [visual plan](VISUAL_PROTECTION_PLAN.md)
  already says.
- **No Android hardware result.** WebView WASM support, device latency, memory
  and battery still need the device benchmark.
- **No Safari device result.** The Safari engine attempts WASM and falls back
  to CPU; physical Safari execution remains unverified.
- WebGL selection can hang rather than fail on a broken headless graphics
  stack (observed in this environment). The Android runtime's 60-second load
  timeout still recreates the classifier, and the policy then moves to WASM
  before CPU.
- WASM memory is allocated by the backend and is not separately capped here.

## HaramBlur and other prior art

Reviewed for approach only; **no code was copied**. [HaramBlur](https://github.com/alganzory/HaramBlur)
is AGPL-3.0, and the [plan's reuse decision](VISUAL_PROTECTION_PLAN.md) already
records why its source is not a license-free snippet library.

Its useful ideas were a model warm-up (NSFWJS's loader already supplies one) and skipping near-identical frames. Its
video loop samples at 25 fps with one positive frame to blur and three negative
frames to clear, and its strictness scale can drop `Porn`/`Sexy` thresholds to
0.1 — unsuitable here, where sampling is every two seconds and `Sexy` alone is
deliberately not an adult verdict. Near-identical-frame skipping is already
covered by the exact-capture verdict cache; a perceptual variant would need its
own false-negative review.

## Reproduce

```bash
# Rebuild the bundle and re-pin the kernels from the locked packages.
node extension/visual/build.mjs --offline

# Real-model latency: packaged WASM by default (the CI gate).
HISN_HARNESS_REALTIME=1 extension/test/browser/run.sh visual-backends.html
# Optional comparisons on a machine that can run them.
HISN_HARNESS_REALTIME=1 extension/test/browser/run.sh 'visual-backends.html?only=cpu'
HISN_HARNESS_REALTIME=1 extension/test/browser/run.sh 'visual-backends.html?only=webgl'

# The rest of the visual checks.
./test.sh extension
HISN_HARNESS_REALTIME=1 extension/test/browser/run.sh visual-real-engine.html
```

## Next candidates

Ranked by evidence needed, not by appeal:

1. Re-run `android/visual-eval` on hardware to replace the 1,984 ms baseline
   with a WASM number and confirm memory/thermal behaviour.
2. Ambiguous-region tiles or flip confirmation for small explicit regions;
   requires an appropriately obtained positive corpus before changing a verdict.
3. Perceptual frame skipping for video; requires a false-negative review.
4. WebGL hang budget in backend selection; needs a design that cannot leave a
   late-resolving backend competing with the selected one.
