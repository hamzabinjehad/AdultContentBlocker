# Android 1.5.0: an opt-in people cover

Development build in the repository, 8 November 2026, version 1.5.0,
versionCode 38. Signed Play and direct artifacts were built and verified. No
1.5.0 phone run is claimed. The adult classifier, 0.70/0.85 thresholds, capture
cadence, after-block pause and text scan are unchanged.

## Why

HaramBlur-style Android protection can blur faces, including an optional
women/men appearance setting. Hisn Android had no people feature at all: its
adult classifier only flagged explicit images, and a flagged screen closed the
app. The request was to add the people feature **stronger**, not to reproduce a
small face blur.

## What changed

- **A new Rules › Built-in checks › Blur people row.** Off (default), **All
  faces**, **Women (estimated)** and **Men (estimated)**. The value is a
  separate comfort preference, not a sealed rule; turning it off never disables
  the adult classifier.
- **Face detection uses the already-pinned visual engine.** Enabling a people
  mode switches the guard from the adult-only native TFLite runtime to the
  packaged WebView engine, which loads the pinned Human **BlazeFace** face
  detector and, for women/men, the **gender** model. The adult classifier runs
  in the same pass; an adult verdict still takes precedence and closes the app.
- **A 384 px frame for people mode.** The adult-only path stays at 224 px. The
  larger frame gives a small face more pixels before the face detector's own
  resize.
- **The response is a whole-window cover, not a subtle face blur.** When a face
  matches, an accessibility overlay covers the selected window with a **10 px
  abstract snapshot** on an opaque scrim. The full screen is hidden, the
  snapshot does not preserve a readable face or clothing, and the overlay
  consumes touches. It is stronger than a small blurred rectangle; it is also
  more disruptive.
- **The cover is bounded.** It clears on a later sample with no matching face,
  when the selected app leaves the foreground, on screen off, when the visual
  layer is disabled, or when the people mode changes. In people mode a frame the
  face runtime cannot answer shows the cover if one is already up and then
  retries; an adult block always hides the cover first and closes the app.
- **Women/men is deliberately recall-biased.** It is an appearance estimate,
  not identity. A face whose estimate is uncertain is covered rather than
  revealed, so the men setting can also cover a woman when the model is
  uncertain. All-faces mode avoids the estimate entirely.
- The face model files were already in the pinned visual inventory
  (`assets.json`) and their MIT/Apache notices were already in the app. The
  Android build now copies `models/blazeface.*` and `models/gender.*` and the
  isolated WebView serves only those files from `hisn.invalid`.

## Behavior

| Setting | What matches | Response |
| --- | --- | --- |
| Off | Adult content only | Adult verdict closes the app; no face cover |
| All faces | Any detected face | Whole-window cover until a clean frame or leaving |
| Women (estimated) | Faces estimated female, plus uncertain faces | Same cover; may hide unintended faces |
| Men (estimated) | Faces estimated male, plus uncertain faces | Same cover; may hide unintended faces |

The cover is an accessibility overlay above the app and below the status bar. It
is touch-blocking; the way out is Home or switching apps. The app content below
is covered, but the status-bar strip can still show edge-to-edge app pixels.

## Measurements

API 36 `hisn_ui` emulator, isolated `app.hisn.android.visualcheck` debug package,
synthetic fixture / the pinned public-domain NASA astronaut portrait from the
visual-eval benign corpus. No adult-positive image, private content, real app,
phone, GPU, battery or thermal measurement.

| Check | Result |
| --- | --- |
| Face-model smoke, all faces | `PASS: wasm verdict=allow face=true` |
| Face-model smoke, women | `PASS: wasm verdict=allow face=true` |
| Face-model smoke, men | `PASS: wasm verdict=allow face=true` (uncertainty rule; not proof the astronaut is male) |
| Live all-faces guard | `PEOPLE all`, `FRAME_EDGE 384`, `BACKEND wasm`, `OVERLAYS 1` |
| Live all-faces latency | capture 12 ms, inference 109–135 ms, sample 145–190 ms |
| Live all-faces capture failures | 0; 1 floor rejection (`RATE_LIMITED 1`) |
| People-off live suite | 10/10 checks pass, `BACKEND tflite-cpu`, `FRAME_EDGE 224` |
| Adult inference in people off | 23–32 ms; capture 27–32 ms; sample 94–98 ms |

Raw status counts and limits:
[ANDROID_VISUAL_1.5.0_2026-11-08.json](ANDROID_VISUAL_1.5.0_2026-11-08.json).
Captures: [design/android-people-2026-11-08](../design/android-people-2026-11-08/README.md).

## Verification

| Check | Result |
| --- | --- |
| Face-model smoke, all / women / men | **PASS** on the pinned astronaut portrait |
| Live all-faces guard | **PASS** — cover shown, `OVERLAYS 1`, no capture failures |
| Live people-off regression, 10 checks | **PASS** — still native TFLite, 224 px, adult close, input/secure/hold/opt-out |
| `android/test-core.sh` | **PASS** |
| `check-strings.py`, `check-notices.py` | **PASS** — 574 strings, 16 plurals, 566 references; 5 list licences |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | **PASS**, 0 errors |
| `check-release.py`, `apksigner` | **PASS** for both APKs; certificate `2F:BE:D6:…:70:C5` |
| Physical SM-G990B2 face check | **PASS** — after re-binding Hisn's accessibility service following the update, a public-domain portrait opened in Chrome was covered. No adult image was used. |

The update exposed a stale accessibility binding: Android still listed Hisn in
**Enabled services** and bound the service, but the app's status read
**Hisn accessibility is enabled but the service is disconnected**, so neither
the adult nor the people pass sampled. Re-registering only Hisn's accessibility
component (leaving ScreenZen, the launcher and HaramBlur untouched) restored
**Apps On**, **Images On** and **App protection Connected**. If an update leaves
the same stale read, toggle Hisn in Android's accessibility settings once; this
is the same class of post-update binding problem recorded for 1.2.1.

### Signed artifacts

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `direct/hisn-1.5.0-direct.apk` | 31,288,521 | `39b801cc23cca8a4c2ce32f7697a0e1c46c48e2e4102765829d743a4314f1c07` |
| `play/hisn-1.5.0-play.apk` | 31,288,513 | `4397f61391c55441916adc9126539f31fbe6fad1a87bb52cabd4a895e780f285` |
| `play/hisn-1.5.0-play.aab` | 16,891,134 | `11481189b57a38bf19a2575cd541b96ec66483a4117683b9982aa1dcc6ba0d3a` |

The direct APK is the installable artifact. Never upload it to Play. The Play
APK/AAB are signed but not uploaded. The face models add about 0.8 MB; the
WebView engine and fallback remain.

## Limits

- **Faces only, not full-body.** A body-only image, a face turned away, a tiny
  face in a feed, a covered face or an unrecognised face can be missed. The
  feature is not a person detector and not a guarantee about a photograph.
- **Women/men is a model estimate, not identity.** It can hide unintended faces
  (including men in the women setting or women in the men setting when the
  estimate is uncertain) and can miss faces it should hide. All-faces mode is the
  reliable option; women/men is the preference.
- **The cover blocks interaction.** It is intended to make the person leave, not
  to let them scroll around a face. It covers the selected window; the status bar
  strip can remain visible. Home and app switching still work.
- **No MediaProjection video rate.** This uses Android 14 accessibility window
  screenshots, about three per second; a short flash between samples can be
  missed. It is not HaramBlur's screen-capture session and does not claim its
  video-rate behaviour.
- **One face image is not an accuracy corpus.** The smoke test proves that the
  packaged models load, the bridge returns a face, and the cover appears; it does
  not estimate recall or false positives on women, men, children, skin tones,
  occlusions or small faces. No adult-positive corpus was used.
- **No phone, GPU, battery or thermal result.** The people path runs the WebView
  WASM engine; the adult path remains the faster native TFLite runtime. No
  comparison with HaramBlur's accuracy or speed is established.
- **No claim of being "better than HaramBlur".** The response is stronger
  (whole-screen, touch-blocking, fail-closed while covered); coverage and
  accuracy are not measured against it.

## Reproduce

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
sh test-core.sh
python3 check-strings.py
python3 check-notices.py
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

# Isolated debug package and face fixture (accessibility is enabled by hand).
./gradlew :app:clean :app:assembleDebug -PhisnIsolatedVisualCheck=true
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put secure enabled_accessibility_services \
    app.hisn.android.visualcheck/app.hisn.android.TextGuardService
adb shell settings put secure accessibility_enabled 1
# In the isolated controller: action=people, mode=all; then the visual-face fixture.
```

The picker and modes are in `MainActivity.editPersonBlur`; the runtime selection
and cover are in `VisualGuard`; the overlay is `VisualOverlay`; the WebView bridge
is `android.js` and `VisualClassifier`.
