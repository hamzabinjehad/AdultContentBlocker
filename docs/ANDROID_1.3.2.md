# Android 1.3.2: reuse the verdict on an unchanged frame

Development build in the repository, 7 November 2026, version 1.3.2,
versionCode 35. Signed Play and direct artifacts were built and verified. No
1.3.2 phone run is claimed. The model, thresholds and capture size are
unchanged.

## Why

The guard takes a capture every one to two seconds on a selected still screen.
Those frames often have the same coarse signature, but the guard was running
the model on every one: about **123 ms** median on the API 36 emulator in the
1.3.1 benchmark. A still screen should not pay that twice for the same pixels.

## What changed

- **A bounded last-verdict cache.** `VisualVerdictCache` keeps one
  `(signature, allow/block)` per selected window for **five seconds**. It stores
  only the coarse 16×16 signature and one word, never pixels, a URL or text.
  The cache is limited to the four selected windows and evicts the oldest.
- **`VisualGuard` checks it after the capture.** A cache hit still goes through
  the frame gate, the live-window/typing check and the same test hook as a real
  verdict; it then updates the cadence and health and acts on a cached block.
  It does not increment `samples` (real model samples); the debug status now
  reports `REUSED`.
- **The cache is cleared when it could be stale:** a window change, a scroll or
  content activity event, an engine reset or close, or a window leaving the
  rotation. A different signature is never reused.

## Files

| File | Change |
| --- | --- |
| `android/core/src/main/java/app/hisn/core/VisualVerdictCache.java` | bounded, TTL'd per-window signature/verdict cache |
| `android/core/src/test/java/app/hisn/core/VisualVerdictCacheTest.java` | signature, window, TTL, unknown, bound, retain and clear cases |
| `android/app/src/main/java/app/hisn/android/VisualGuard.java` | cache lookup before inference; clear on change; `reused` counter |
| `android/app/src/debug/java/app/hisn/android/VisualCaptureTestActivity.java` | `REUSED` diagnostic |

No string, permission, rule, threshold, stored field or capture size changed.

## Verification

| Check | Result |
| --- | --- |
| `JAVA_HOME=<JBR> sh test-core.sh` | **PASS**, including `VisualVerdictCacheTest` |
| `check-strings.py`, `check-notices.py` | **PASS** — 563 strings, 16 plurals, 554 references |
| `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` | **PASS**, 0 lint errors |
| `CoreDeviceTestRunner` on the API 36 emulator | **PASS** on the 1.3.2 build |
| Signed Play APK/AAB and direct APK | **PASS** `check-release.py` and `apksigner` |

The 1.3.1 real-model benchmark of **123 ms median** (WASM) is the inference a
cache hit avoids. There is **no device measurement of cache-hit rate or
battery** in this pass; the debug `REUSED` counter is the field to read on a
phone.

### Signed artifacts

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `direct/hisn-1.3.2-direct.apk` | 5,734,595 | `968b612045f1d00f60283e6f730fc7c355c4c6f9c3ac1507bdc9c153282f0def` |
| `play/hisn-1.3.2-play.apk` | 5,733,823 | `528031aa271829fd13b68e5b46a1f7135369a8d92e13b37ea1870475c9962d6a` |
| `play/hisn-1.3.2-play.aab` | 5,620,638 | `747ee811a20bd04a36e64ddc4fb19b581d1f941a80557a846d2b93b57aa4d959` |

The direct APK is the installable artifact. Never upload it to Play. The Play
APK/AAB are signed but not uploaded.

## Limits

- **The cache is an exact coarse signature, not a perceptual hash.** A subtle
  change that keeps the 16×16 sample identical can reuse an allow for up to five
  seconds. Scroll/content events and a window change clear it, so the normal
  movement path does not use it.
- **The capture still happens.** Only the model inference is skipped; screenshot
  and scaling cost remain.
- **No device cache-hit measurement.** The benefit depends on how often a
  selected app sits on an unchanged screen. A still reader or settings page
  benefits; a playing video does not.
- **No accuracy change.** The model, thresholds and centred crop are unchanged;
  the cache replays a verdict for the same coarse frame. Adult recall remains
  unmeasured and no adult-positive corpus was used.
- **The big remaining visual win is native TFLite/GPU.** The cache removes
  repeated work; it does not make a first inference faster. Rust would not
  change that by itself.

## Reproduce

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd android && sh test-core.sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```
