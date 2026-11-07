# Android 1.2.3: the screen is read off the main thread

Development build in the repository, 7 November 2026, version 1.2.3,
versionCode 32. Signed Play and direct artifacts were built and verified; no
1.2.3 phone run and no Play upload is claimed. This pass changes when and where
the guard reads a screen, not what it treats as adult content or a saved rule.

## Why

The text guard walked the accessibility tree on the accessibility service's
main thread. Every `getChild()` crosses a Binder, so a large feed could stall
the service — and the app being read — for about a second. The walk also
stopped at 512 nodes, which is less than a long card list holds before the
label in use. A selected app's content changes queued the same walk over and
over, and a bare video frame re-read the whole tree.

The image layer had the opposite problem. A 384 px capture was encoded and then
resized to the model's 224 px input, the model was freed after three idle
minutes and on every screen-off, a frame got 15 s to answer, and a window
Android refuses to capture (a protected or already-gone window) was asked
again on every poll. On the owner's phone the real model took about two seconds
per inference, so the guard spent most of a moving session inside one sample.

## What changed

### Text

- **The tree walk runs on one daemon scanner thread.** The main thread still
  fetches the selected windows' roots, because Android resolves that through
  the service connection; the walk over those roots, the word matcher and the
  adult policy run off the UI thread. The verdict returns to the main thread,
  where Home, the restriction screen and every policy write happen.
- **A generation token and a watchdog keep a stale walk from deciding.** A
  window-state change, an intervention or an interrupt increments the
  generation, so a walk that started on the old screen is discarded. A walk
  that has not reported in 20 s is treated as a stuck Binder walk: the guard
  records an incomplete inspection, replaces the scanner executor and retries.
- **A selected app's own event is matched first.** The event's text and source
  description are inspected transiently, never stored or logged. A promotion
  label ("Watch free porn") blocks immediately; a bare explicit label ("Porn")
  still waits for the full pass, which knows whether another line on the screen
  names protective context. Only the selected window in use may act before the
  walk; an event from a window the person already left falls back to the walk.
  If the event carries no text, the tree walk is unchanged.
- **The walk reads 2,048 nodes, checks a partial match every 64 visited nodes
  and can stop early.** A matching label no longer waits for the rest of a long
  feed. A 1,500-label regression case proves a label at position 1,400 is read
  at 2,048 and cut by the old 1,024 budget. The shared budget across split-screen
  windows is unchanged, and a selected app is still re-read at least every three
  seconds, so text that appears without a text-bearing event is not missed.
- **A clean screen no longer re-walks on every content change.** Only an event
  that names text, a window-state change or a scroll marks the screen dirty;
  video frames and animations do not. While the signed adult vocabulary is
  still loading, the pass retries after a quarter second instead of waiting out
  the heartbeat.

### Image

- **The WebView and model stay warm while the screen is on.** A two-minute
  screen-off grace keeps them for an immediate screen-on; after the grace they
  are freed as before. Switching back to a selected app no longer pays the
  cold model load.
- **The capture's long edge is 224 px, not 384.** The model resizes its input
  to 224, so this removes an encode of detail the model discards. It is a cost
  change rather than an accuracy comparison; see Limits.
- **A frame gets 5 s, not 15 s.** A slower answer is treated as a failed
  runtime and retried through the existing bounded `VisualRetry` ladder.
- **A window Android refuses backs off.** One failed capture now waits 1 s,
  the next 2 s, then 4 s, instead of asking again on every poll. A window
  change clears the backoff and the sampling interval.
- **The test status reports the real engine.** The debug diagnostics now carry
  the selected backend (`webgl`/`wasm`/`cpu`), the frame edge and the last
  capture, inference and sample times.

### Added

`LabelScan.Check`, `LabelScan.CHECK_INTERVAL` and the checkpoint overload;
`VisibleTextSample.isEmpty()`; the scanner executor and watchdog; the `stress`
fixture (700 labels with the word at index 650) and its live case; and the
visual status fields above. No permission, activity, service, string or stored
field was added.

## Files

| File | Change |
| --- | --- |
| `android/app/src/main/java/app/hisn/android/TextGuardService.java` | off-main tree walk, event fast path, dirty-screen gating, generation and watchdog, adult-load retry |
| `android/app/src/main/java/app/hisn/android/VisualGuard.java` | warm-model duty, 224 px edge, capture backoff, window-change cadence, timing diagnostics |
| `android/app/src/main/java/app/hisn/android/VisualClassifier.java` | 5 s frame budget |
| `android/core/src/main/java/app/hisn/core/LabelScan.java` | 2,048-node budget, checkpoint interface and early stop |
| `android/core/src/main/java/app/hisn/core/VisibleTextSample.java` | `isEmpty()` |
| `android/core/src/main/java/app/hisn/core/VisualDuty.java` | keep warm while the screen is on; two-minute screen-off grace |
| `android/core/src/main/java/app/hisn/core/VisualHealth.java` | poll cadence wording |
| `android/core/src/test/java/app/hisn/core/LabelScanTest.java` | 700-label budget case and checkpoint case |
| `android/core/src/test/java/app/hisn/core/VisualDutyTest.java` | screen-off grace and warm-model states |
| `android/app/src/debug/java/app/hisn/android/GuardTestSetupActivity.java`, `VisualCaptureTestActivity.java` | diagnostics |
| `android/app/src/androidTest/java/app/hisn/android/GuardFixtureActivity.java`, `android/test-live-guard.py`, `android/test-live-visual.py` | `stress` fixture and case; backend/edge assertions |

## Verification

| Check | Result |
| --- | --- |
| `JAVA_HOME=<JBR> sh test-core.sh` | **PASS** — every core suite, including the 700-label 2,048/512 case, the 1,500-label 2,048/1,024 case and the updated `VisualDuty` grace |
| `python3 check-strings.py` | **PASS** — 555 strings, 16 plurals and 545 source references, English and Arabic |
| `python3 check-notices.py` | **PASS** — list and model notices |
| `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` | **PASS** — 0 lint errors |
| `CoreDeviceTestRunner` on the API 36 `hisn_ui` emulator | **PASS** — seed, Arabic matching, local DNS block, saved rules, adult terms, visual settings, daily limits, commitment, uninstall guard, layer-watch and seed-age checks; no VPN, accessibility service or device-owner policy was changed by the runner |
| Live `stress` fixture on the same emulator | **PASS** — 700 labels with the word at index 650 were blocked after the accessibility service was bound; the status recorded `FAST_TEXT 0`, `SCANS 7`, `LAST_SCAN_MS 21`, so the tree walk read past the old 512-node point |
| Live `word` fixture | **PASS** in an earlier emulator run. After repeated package reinstalls and a System UI ANR, later emulator runs were intermittent, so the full live suite is **not** claimed for 1.2.3 |
| `check-release.py --expect-distribution play` | **PASS** — 1.2.3 (32), not debuggable/test-only, no DUMP permission, no debug components |
| `check-release.py --expect-distribution direct` | **PASS** — 1.2.3-direct (32), same checks |
| `apksigner verify --print-certs` | **PASS** for both APKs; certificate `2F:BE:D6:…:70:C5`, unchanged since 1.0.0 |

### Signed artifacts

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `direct/hisn-1.2.3-direct.apk` | 5,727,074 | `632c3987ab900a48464ee26b90fa89723e592e7d166697b99e5650fd1e84b5b4` |
| `play/hisn-1.2.3-play.apk` | 5,726,282 | `141c424400ef09ae44bcc6c02a7a6d997bc3d1b9896368377881783605220322` |
| `play/hisn-1.2.3-play.aab` | 5,613,788 | `c1232512ef684fc152fff4d23fb2f8408071390c39717b8b9574c4ab29128135` |

The direct APK is the installable artifact. Never upload it to Play. The Play
APK/AAB are signed but not uploaded, and the AAB is not an installable file.

## Limits

- **No 1.2.3 phone run.** 1.2.2 was installed on the owner's Samsung
  SM-G990B2; this build was not. The emulator run above verifies the packaged
  seed and core checks, not the new scheduling on real hardware.
- **The 2,048-node budget is not device-measured.** The live `stress` fixture is
  700 labels, so it proves only the 512-node gap; the 1,500-label case is
  unit-tested. The extra Binder work is off-main and bounded by the three-second
  pass, but no frame-time, memory or battery number was taken for it.
- **No device latency or power benchmark.** The gain from moving the walk and
  keeping the model warm is a design consequence. The only measured image
  numbers remain the [WASM engine note](VISUAL_ENGINE_WASM_2026-11-07.md) on a
  Mac and in Node; no Android WASM, memory, thermal or battery number was
  taken here.
- **The 224 px edge is a cost choice, not an accuracy result.** There is no
  comparison of adult recall between 224 and 384, and no adult-positive corpus
  was used anywhere in this pass. The layer's adult recall and precision remain
  unestablished.
- **Keeping the model warm trades memory and battery for a faster next
  sample.** A long screen-on session without a selected app now keeps the
  WebView and model resident. The two-minute grace was chosen without a device
  power measurement.
- **The event fast path is not live-verified here.** The shared matcher and
  `blocksPromotion` behaviour are unit-tested, but the manual content-change
  event built for the emulator did not reach the service as a text-bearing
  event, so the tree-scan path decided instead. A hardware fixture with a real
  feed item remains the right test. The fast path has the full walk as its
  fallback.
- **The watchdog and executor replacement are code and compile evidence.** A
  stuck Binder walk was not produced on a device. The 20 s bound is longer than
  any expected 2,048-node read; a genuinely slow device could still time out
  and retry.
- **No full live suite.** The ten-check live driver was verified against 1.2.1
  on the owner's phone; 1.2.3 changes the sampling path and was not re-run on
  that phone. The `stress` case above is the only new live evidence.

## Reproduce

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
sh test-core.sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

# Signed artifacts (owner's key only; never commit it).
export HISN_ANDROID_KEYSTORE="$PWD/../keys/android/hisn-release.jks"
export HISN_ANDROID_KEYSTORE_PASSWORD="$(cat ../keys/android/keystore-password.txt)"
export HISN_ANDROID_KEY_ALIAS=hisn
export HISN_ANDROID_KEY_PASSWORD="$HISN_ANDROID_KEYSTORE_PASSWORD"
./gradlew :app:assembleRelease :app:bundleRelease
python3 check-release.py --expect-distribution play app/build/outputs/apk/release/app-release.apk
./gradlew :app:assembleRelease -PhisnDistribution=direct
python3 check-release.py --expect-distribution direct app/build/outputs/apk/release/app-release.apk

# Emulator device checks (the runner restarts the app; accessibility must be
# rebound afterwards if a live fixture is next).
adb shell am instrument -w app.hisn.android.test/app.hisn.android.CoreDeviceTestRunner
python3 test-live-guard.py --serial emulator-5554 --case stress
```

Do not start a real long commitment or replace the owner's install as a
routine test. The live fixture changes only its own additions and restores the
saved rules; the device runner uses isolated preference files.
