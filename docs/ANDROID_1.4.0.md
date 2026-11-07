# Android 1.4.0: native TFLite visual runtime

Development build in the repository, 7 November 2026, version 1.4.0,
versionCode 36. Signed Play and direct artifacts were built and verified. No
1.4.0 phone run is claimed. The block thresholds, capture size, after-block
pause and text scan are unchanged.

## Why

The visual engine ran the model in a WebView. On the API 36 emulator the
packaged WASM path measured **123 ms median / 291 ms p95** and loaded in 3.0 s;
the old `auto` path could pick WebGL and was slower still. Java was not the
bottleneck — the JavaScript/WASM runtime was.

## What changed

- **The same model, converted to native TFLite.** The nsfwjs 4.4.0 MobileNetV2
  model JSON and quantized weight shard were reconstructed from the pinned
  package, converted through a Keras SavedModel to a float32 TFLite model, and
  pinned by SHA-256
  `01215075f6e759bac8931310ed95665295bd662eff547f27a33982bcf2cb4d46`.
- **The same preprocessing and thresholds.** Input is 224×224 bilinear with
  `alignCorners`, RGB divided by 255; a frame blocks at Porn ≥ 0.70, Hentai ≥
  0.70 or Porn + Hentai ≥ 0.85. The converted model's scores matched TFJS to
  **3.34 × 10⁻⁶** on all 12 benign views.
- **One worker, main-thread callback.** `TfliteClassifier` loads the model and
  runs preprocessing/inference on one daemon worker; verdicts return to the
  main thread. It implements the same `VisualRuntime` interface as the WebView
  classifier.
- **WebView remains the fallback.** If the native model cannot load (missing
  asset, unsupported runtime), `VisualGuard` switches to the packaged WebView
  engine for that service instance. A failed native load does not leave the
  image layer off.
- **The license notice is in the app.** TensorFlow Lite (Apache 2.0) is listed
  under **Licenses and sources**; the NSFWJS weights notice is retained.
- 1.3.2's verdict cache, 1.3.1's centred near-miss crop, 1.3.0's after-block
  pause and the 2,048-label off-main text scan are retained.

## Measurements

Real model, 12 benign views from 4 pinned public-domain/CC0 sources, 3 repeats,
API 36 `hisn_ui` emulator. Roundtrip includes preprocessing and inference; the
native path uses one worker and the same 224 px frame. No adult-positive images.

| Runtime | Model load | Median | p95 |
| --- | ---: | ---: | ---: |
| **Native TFLite CPU** | **1.6–1.9 s** | **38–45 ms** | 203 ms / 3,186 ms* |
| WASM (1.3.1) | 3.0 s | 123 ms | 291 ms |
| WebGL via `auto` (1.3.1) | 3.3 s | 188 ms | 361 ms |

\* The second native run had one extreme emulator hitch (max 3.6 s); the first
run's p95 was 203 ms. The emulator is not a phone.

**Benign verdicts:** 0/12 false blocks in every run. **Score parity:** maximum
absolute difference 3.34 × 10⁻⁶ across all 12 × 5 scores. Raw reports and scores:
[ANDROID_VISUAL_1.4.0_2026-11-07.json](ANDROID_VISUAL_1.4.0_2026-11-07.json).

## Size

The direct APK grows from about 5.7 MB to **30,754,721 bytes (≈29 MB)**: the
9.8 MB TFLite model, TensorFlow Lite native libraries for four ABIs, and the
retained WebView fallback assets. The Play AAB is **16,353,226 bytes** because
Play splits ABIs. A future arm64-only direct build or float16 model would reduce
this; neither is in this release.

## Files

| File | Change |
| --- | --- |
| `android/app/src/main/assets/nsfw_mobilenet_v2.tflite` | converted float32 model, 9.8 MB, SHA-256 pinned |
| `android/app/src/main/java/app/hisn/android/TfliteClassifier.java` | async native runtime, preprocessing, thresholds, digest check |
| `android/app/src/main/java/app/hisn/android/VisualRuntime.java` | common runtime interface |
| `android/app/src/main/java/app/hisn/android/VisualClassifier.java` | implements the interface; remains the fallback |
| `android/app/src/main/java/app/hisn/android/VisualGuard.java` | native first, WebView fallback |
| `android/app/src/debug/java/app/hisn/android/TfliteBenchmarkActivity.java` | debug-only native benchmark |
| `android/visual-eval/nsfw-tflite.json` | model provenance, conversion versions, SHA-256 and parity |
| `android/visual-eval/convert-model.py` | reproducibility helper; reconstructs the model and refuses an unexpected digest |
| `android/visual-eval/run-tflite.py` | native benchmark driver |
| `android/app/src/main/assets/licenses/tensorflow-lite-Apache-2.0.txt` | notice |

## Verification

| Check | Result |
| --- | --- |
| TFJS vs TFLite score parity, 12 benign views × 5 scores | **PASS** — max abs 3.34e-6 |
| Native benchmark, 36 calls | **PASS** — 0/12 false blocks; 38–45 ms median |
| Native backend in the live guard | **PASS** — debug controller reported `BACKEND tflite-cpu` |
| `android/test-core.sh` | **PASS** |
| Strings/notices | **PASS** — 564 strings, 16 plurals, 555 references; 5 license files |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | **PASS**, 0 errors |
| `CoreDeviceTestRunner` on API 36 | **PASS** |
| `check-release.py`, `apksigner` | **PASS** for both APKs; certificate unchanged |

### Signed artifacts

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `direct/hisn-1.4.0-direct.apk` | 30,754,721 | `442823455e23b1ec26d6a8fffa5be2bd7ce2ad93a2dfb130baefca8ddaa65d17` |
| `play/hisn-1.4.0-play.apk` | 30,754,717 | `b86ea1451c7e897bbaac2aba41bdd58a51165ff557257fd39000f51c088aaa67` |
| `play/hisn-1.4.0-play.aab` | 16,353,226 | `7c22859ba2efa56a8402de6e021de0628b9a1440c6e9886ff36ff57f6a57cc32` |

The direct APK is the installable artifact. Never upload it to Play. The Play
APK/AAB are signed but not uploaded.

## Limits

- **No adult-positive corpus.** Adult recall and precision remain unmeasured;
  the 0/12 is benign false-block evidence, not accuracy proof. The TFLite model
  is converted from the same weights and matched TFJS scores, but only benign
  views were compared.
- **Emulator only.** No phone, GPU, battery, thermal or long-run measurement.
  Native numbers are CPU/XNNPACK on the API 36 emulator; the GPU delegate was
  unreliable there and is not used. The p95 varied with emulator load.
- **The model and APK are larger.** The 9.8 MB model and four-ABI native
  libraries take the direct APK to ~29 MB; Play's AAB splits ABIs but the APK
  from Play will still be larger than 1.3.2.
- **Fallback is code-verified, not device-forced.** The WebView fallback path
  compiles and is wired on a native load failure; a forced native failure was
  not produced on a device.
- **No threshold, capture-edge, permission, stored field or consent change.**
  The native runtime is Android-only; the extension and iOS paths are untouched.

## Reproduce

The model conversion was performed from the pinned nsfwjs 4.4.0 package:
reconstruct `model.json` + `group1-shard1of1.bin`, convert to a Keras SavedModel
with `tensorflowjs_converter`, then `TFLiteConverter.from_saved_model`. The
converted model is checked into `assets/` and its digest is verified at load.
`android/visual-eval/convert-model.py` records the same steps and refuses to
replace a model whose SHA-256 differs from the reviewed value; it needs the
pinned nsfwjs package under `extension/visual/node_modules` and a Python
environment with TensorFlow 2.16.2 and TensorFlow.js 4.20.0.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android && sh test-core.sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python3 ../android/visual-eval/run-tflite.py --serial <emulator> \
    --corpus /tmp/hisn-benign --report /tmp/hisn-tflite.json
```
