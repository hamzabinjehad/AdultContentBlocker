# Android 1.3.1: a faster visual pass and a closer look

Development build in the repository, 7 November 2026, version 1.3.1,
versionCode 34. Signed Play and direct artifacts were built and verified. No
1.3.1 phone run is claimed. The model and the adult block thresholds are
unchanged.

## Why

The visual layer can be the slowest part of the guard. The engine's `auto`
backend tries WebGL first; on the unattached Android WebView that attempt can
add seconds to the first load, or pick a slower path. A full frame is also what
the model sees: a small explicit region can be a minority of the 224 px capture.

## What changed

- **The guard starts on the packaged WASM backend.** `VisualClassifier` now
  requests `wasm` instead of `auto`; the engine still falls back to WebGL and
  then CPU if WASM is unavailable. On the API 36 emulator the `auto` ladder
  chose WebGL, loaded it in 3.3 s and measured **188 ms median**; the WASM path
  loaded in **3.0 s** and measured **123 ms median** on the same benign corpus.
  An earlier `auto` run on the same emulator spent **12.1 s** loading WebGL and
  measured 207 ms median, which is why the backend is no longer tried first.
- **An uncertain full frame gets one closer look.** When the full-frame
  Porn/Hentai scores are at least **0.45** (or their sum is at least **0.60**)
  but below the engine's block threshold, the guard crops the centred **68%**
  of the frame, sends that crop through the same local model once, and blocks
  only if the same **0.70 / 0.85** thresholds are met. This is not a lower
  block threshold and not a new model; it gives a small region more of the
  model's 224 px input. A failed second look keeps the first `allow` verdict.
- **The crop is a pure module with a Node test.** `region.js` holds `nearMiss`
  and `centreCrop`; `region.test.mjs` checks the thresholds, dimensions, pixel
  offset, malformed input and Base64 output. The WebView glue imports it, and
  `VisualClassifier` serves `/visual/region.js` from the packaged assets.
- **Capture edge stays 224 and the still/fast cadence is unchanged.** The
  improvement is the backend choice and the bounded second look, not a larger
  capture or a faster polling interval.

## Files

| File | Change |
| --- | --- |
| `android/app/src/main/java/app/hisn/android/VisualClassifier.java` | WASM first; serve `region.js` |
| `android/app/src/main/assets/visual/android.js` | import the crop helper; one closer look on near-miss |
| `android/app/src/main/assets/visual/region.js` | pure near-miss and centred-crop logic |
| `android/visual-eval/region.test.mjs` | Node checks for thresholds, crop geometry and malformed input |
| `docs/ANDROID_VISUAL_1.3.1_2026-11-07.json` | raw benchmark reports |

No string, permission, rule, threshold, stored field or capture size changed.

## Verification

| Check | Result |
| --- | --- |
| `node region.test.mjs` | **PASS** — near-miss boundaries, 10×10 crop to 7×7 at (1,1), malformed/false-length input |
| `JAVA_HOME=<JBR> sh test-core.sh` | **PASS** |
| `check-strings.py`, `check-notices.py` | **PASS** — 563 strings, 16 plurals, 554 references |
| `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` | **PASS**, 0 lint errors |
| `CoreDeviceTestRunner` on the API 36 emulator | **PASS** on the 1.3.1 build |
| Real packaged model, 12 benign views × 3 | **PASS** — 0 false blocks in every run |
| `check-release.py`, `apksigner` | **PASS** for both APKs; certificate `2F:BE:D6:…:70:C5` |

### Measurements

The same 12 benign views (4 pinned public-domain/CC0 sources, image/feed/tile
layouts), 3 repeats, 2 s interval, real NSFWJS MobileNetV2 in the packaged
WebView on the API 36 `hisn_ui` emulator. Roundtrip includes capture encode, the
JavaScript bridge and inference. Raw reports:
[benchmark JSON](ANDROID_VISUAL_1.3.1_2026-11-07.json).

| Run | Actual backend | Model load | Median | p95 |
| --- | --- | ---: | ---: | ---: |
| 1.3.1, WASM first | wasm | 2,978 ms | **123 ms** | 291 ms |
| 1.3.1, `auto` | webgl | 3,312 ms | 188 ms | 361 ms |
| 1.3.0, WASM baseline | wasm | 3,260 ms | 116 ms | 209 ms |

The WASM-first path is about **35 % faster** than the `auto`/WebGL path for
inference on this emulator. The 1.3.1 WASM run is about 6 ms slower at the
median than the 1.3.0 baseline, which is within run-to-run noise and includes
the near-miss crop when it triggers; the p95 rise is the cost of an occasional
second inference. No phone number is claimed.

### Signed artifacts

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `direct/hisn-1.3.1-direct.apk` | 5,733,563 | `cb543f0ef9c7422bd73b00d619c9d52be2afee390eb2c7a88ed8907d8899ad98` |
| `play/hisn-1.3.1-play.apk` | 5,732,435 | `07461f8a5a390e696c1711cfe7dd9ab1f5ebf5f46e56041a0171d23326a4b679` |
| `play/hisn-1.3.1-play.aab` | 5,619,236 | `f573f13dbb6e3a6160fea391adad65b58998902dcba8cf8b0ad39e7e865961f6` |

The direct APK is the installable artifact. Never upload it to Play. The Play
APK/AAB are signed but not uploaded.

## Limits

- **No adult-positive corpus and no accuracy claim.** The 12 benign controls
  stayed at 0 false blocks; adult recall and precision remain unmeasured. The
  closer look can only help when the full-frame score is already near the
  threshold; a region the full frame scores very low can still be missed.
- **The crop is centred and 68 %.** An explicit region near an edge, or a very
  small region, may not benefit. A tile sweep over the whole frame would cover
  more, at four extra inferences per uncertain frame; it is not implemented.
- **Emulator and benign images only.** The speed numbers are from the API 36
  emulator, WebView 133, not from the owner's Samsung. A real GPU may make
  WebGL faster than WASM; the engine still falls back, and a phone measurement
  is still needed. Model load remains about three seconds; the warm model and
  the 224 px capture are unchanged.
- **No device battery, thermal or memory measurement.** The second look adds
  an inference only on uncertain frames; its cost on a phone is unmeasured.
- **No new detection model or threshold.** The block thresholds, `Sexy`
  exclusion and local-only processing are unchanged. No adult content, private
  image or network request was used.

## Reproduce

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
node android/visual-eval/region.test.mjs

python3 android/visual-eval/prepare-benign.py --out /tmp/hisn-benign
./gradlew :app:assembleDebug -PhisnIsolatedVisualCheck=true
adb install -r -t android/app/build/outputs/apk/debug/app-debug.apk
python3 android/visual-eval/run-device.py --serial <emulator> --corpus /tmp/hisn-benign \
    --report /tmp/hisn-visual.json --backend wasm

cd android && sh test-core.sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```
