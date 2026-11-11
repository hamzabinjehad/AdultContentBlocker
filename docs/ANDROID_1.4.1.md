# Android 1.4.1: sample moving screens at the platform floor

Development build in the repository, 7 November 2026, version 1.4.1,
versionCode 37. Signed Play and direct artifacts were built and verified. No
1.4.1 phone run is claimed. The model, thresholds, capture edge and after-block
pause are unchanged from 1.4.0.

## Why

The visual guard sampled a moving screen every **250 ms**. Android's
accessibility screenshot API refuses a request for the same window sooner than
**333 ms** and returns `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`. The guard
treated that rejection as a capture failure, counted it, and backed off for one
to four seconds. A fast native inference made this worse: the guard reached the
250 ms cadence, was rejected, and then spent the next second backing off.

On the API 36 emulator's live guard, an 8 s moving fixture produced **11 real
samples and 6 capture failures** before the change. The moving cadence was not
the intended 250 ms; it was roughly one sample per second after the backoff.

## What changed

- **Moving cadence is 350 ms.** `VisualCadence.FAST_MILLIS` is above the 333 ms
  platform floor with a small margin. The still-screen 1 s base, 500 ms
  back-off steps and 2 s still maximum are unchanged.
- **A floor rejection is a retry, not an unreadable window.**
  `VisualGuard` now catches `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`
  separately: it records `RATE_LIMITED`, schedules the next request at the
  floor, and leaves `captureFailures`, `captureFailureStreak`,
  `captureBackoffUntil` and the `UNREADABLE` health state alone. The debug
  controller reports `RATE_LIMITED`.
- **The native model is mapped, not copied.** `TfliteClassifier` opens the
  uncompressed `nsfw_mobilenet_v2.tflite` through `AssetFileDescriptor` and
  `FileChannel.map`, verifies its SHA-256 over the mapped bytes, and hands the
  mapped buffer to the TFLite interpreter. It reuses its 224×224 input buffer
  and zeroes the input and source pixels after each frame. The APK stores the
  model uncompressed so the mapping works.
- No detection threshold, capture size, permission, stored field, consent text
  or after-block rule changed. The WebView fallback, verdict cache, centred
  near-miss crop and native-first selection are retained.

## Measurements

Live debug guard on the API 36 `hisn_ui` emulator (1080×2400), the isolated
`app.hisn.android.visualcheck` package and its synthetic fixture. The driver
starts the fixture, waits 8 s and reads the debug counters. `SAMPLES` counts
real model verdicts; `REUSED` counts the verdict cache. No adult-positive image
or real app was used.

| Moving fixture, 8 s | 1.4.0 | 1.4.1 |
| --- | ---: | ---: |
| Real samples (`SAMPLES`) | 11 | **23** |
| Capture requests (`REQUESTS`) | 25 | 36 |
| Capture failures (`CAPTURE_FAILURES`) | 6 | **0** |
| Floor rejections (`RATE_LIMITED`) | not reported | 1 |
| Last inference | 30 ms | 24 ms |
| Last capture | 12 ms | 24 ms |
| Last sample (capture → verdict) | 65 ms | 68 ms |
| Backend | `tflite-cpu` | `tflite-cpu` |
| Capture edge | 224 px | 224 px |

The 1.4.0 row is from an earlier run of the same driver on the same emulator;
the sample counters are not a controlled side-by-side benchmark. The 1.4.1 run
also sampled a still fixture (3 real samples, 9 cache hits) and passed every
input, secure-window, intervention, hold and opt-out check. The raw report is
[ANDROID_VISUAL_1.4.1_2026-11-07.json](ANDROID_VISUAL_1.4.1_2026-11-07.json).

The native benchmark from 1.4.0 remains the timing reference for the model
itself: 36 calls, median 38–46 ms on the emulator, 0/12 benign false blocks.
The live inference here is 24–30 ms. Emulator load, the screenshot path and the
frame gate are not separated in the live `LAST_SAMPLE_MS`.

## Verification

| Check | Result |
| --- | --- |
| Live visual suite, 10 checks | **PASS** — `BACKEND tflite-cpu`, 23 moving samples, 0 capture failures, input and password pause, secure window, synthetic intervention, hold recovery, opt-out |
| `android/test-core.sh` | **PASS** — `VisualCadenceTest` updated to the 350 ms floor |
| `check-strings.py`, `check-notices.py` | **PASS** — 564 strings, 16 plurals, 555 references; 5 license files |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | **PASS**, 0 errors |
| `check-release.py` and `apksigner` | **PASS** for both APKs; certificate `2F:BE:D6:…:70:C5` |

### Signed artifacts

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `direct/hisn-1.4.1-direct.apk` | 30,754,721 | `61dea0d0bd984eab7bc00a4a31b45874d560f82ace92dfb61370bf43e1cb49ba` |
| `play/hisn-1.4.1-play.apk` | 30,754,717 | `160ad8b847c87d3767ce64bd6fcd3c0d704ebed9dd79d92fd7a3b69e71b039e9` |
| `play/hisn-1.4.1-play.aab` | 16,353,560 | `4d8727ca07cf248f737bbbcd9809849ff806c1acab4d2354d4523321360778d5` |

The direct APK is the installable artifact. Never upload it to Play. The Play
APK/AAB are signed but not uploaded. The APK size is essentially unchanged from
1.4.0 because the same model and native libraries are present.

## Limits

- **No phone run.** The live numbers are from the API 36 emulator, on a
  synthetic fixture. A phone's screenshot latency, thermal state and CPU will
  differ.
- **No adult-positive corpus.** Benign parity and 0/12 false blocks from 1.4.0
  remain the only model evidence. This change does not measure recall.
- **23 samples in 8 s is not video-rate detection.** Android caps accessibility
  screenshots at three per second per window; this path uses the full screen
  capture API, not a MediaProjection video stream. A short flash between
  samples can still be missed.
- **The one rate-limited request at a window edge is expected.** It is retried
  without a failure backoff; it is not a claim that every request succeeds.
- **One later repeat of the live driver did not complete.** The same synthetic
  moving fixture recorded no new capture and `STATE ERROR` after the 5 s capture
  timeout; the recorded 10/10 pass is one run, not a reliability claim. The
  fixture is an emulator target, not a real app.
- **The WebView fallback was not forced on a device.** It compiles and is wired
  on a native load failure; the live run used the native backend.
- No battery, thermal, memory or long-run measurement; no GPU delegate.

## Reproduce

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
sh test-core.sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

# Isolated visual guard, then the live driver (accessibility must be enabled
# for app.hisn.android.visualcheck; the driver never enables it itself).
./gradlew :app:clean :app:assembleDebug -PhisnIsolatedVisualCheck=true
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
python3 ../android/test-live-visual.py --serial <emulator> --configure
python3 ../android/test-live-visual.py --serial <emulator> --confirm-synthetic-intervention
```

The cadence is tested by `android/core/src/test/java/app/hisn/core/VisualCadenceTest.java`;
the live driver is `android/test-live-visual.py`.
