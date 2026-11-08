# Android 1.5.1: cover protected and incognito windows

Development build in the repository, 8 November 2026, version 1.5.1,
versionCode 39. Signed Play and direct artifacts were built and verified. No
1.5.1 phone adult test is claimed. The adult thresholds, capture cadence,
people modes and after-block pause are unchanged.

## Why

Chrome Incognito and every other `FLAG_SECURE` window cannot be screenshotted.
Android's accessibility screenshot API returns
`ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` (6) ([AOSP constant](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/accessibilityservice/AccessibilityService.java#L778));
Chrome Incognito also produces black pixels in capture tools
([Chromium issue](https://issues.chromium.org/issues/559237438)). A MediaProjection
session does not bypass this. **No ordinary app can inspect those pixels**;
HaramBlur cannot either. Before 1.5.1, Hisn reported the window as unreadable and
left the page visible, which looked like the visual layer had simply failed.

## What changed

- **A dedicated `PROTECTED` state.** A secure-window screenshot failure is no
  longer treated as a generic unreadable/backoff state. The guard reports
  `PROTECTED` and the dashboard says the protected window is covered, not
  inspected.
- **A fail-closed cover.** The selected window is covered by the same
  touch-blocking accessibility overlay, with a 2 s retry while the window stays
  protected. The cover says **Hisn can't check this window / This private or
  protected window blocks screenshots. Close it or switch apps.** It clears on
  leaving the window, screen off, a later inspected sample, or turning the
  layer off.
- **No pixel bypass.** No root, Xposed, accessibility privilege escalation,
  MediaProjection capture, secure-flag removal or screenshot of protected
  pixels is added. This is deliberately a cover, not a claimed inspection.
- **Text and DNS remain independent.** Chrome Incognito text, where Chrome
  exposes it to accessibility, is still available to the text layer; the DNS
  VPN filters domains regardless of incognito. The visual layer cannot see the
  protected page's images.

## How this relates to HaramBlur

HaramBlur's Android app uses a screen-capture session and overlay. A
`FLAG_SECURE` window is excluded from those captures by Android, so the platform
limit applies to it as well. Hisn's response is stronger in the sense that it
does not leave a protected window visible while claiming the visual layer is
healthy: it covers it and reports the state honestly. This does not establish
superior accuracy or coverage, and no HaramBlur code or assets were copied.

## Verification

| Check | Result |
| --- | --- |
| API 36 FLAG_SECURE fixture | `STATE PROTECTED`, `CAPTURE_ERROR 6`, `OVERLAYS 1`, 0 samples from the protected window |
| Protected cover render | **PASS** — *Hisn can't check this window* covers the window; the status bar remains system UI |
| People-off live suite | **PASS** — ten checks, native TFLite adult path and 350 ms cadence unchanged |
| `android/test-core.sh` | **PASS**, including the new `PROTECTED` health state |
| `check-strings.py`, `check-notices.py` | **PASS** — 578 strings, 16 plurals, 570 references |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | **PASS**, 0 errors |
| `check-release.py`, `apksigner` | **PASS** for both APKs; certificate `2F:BE:D6:…:70:C5` |
| Physical SM-G990B2 1.5.1 install | **PASS** — version 39, data preserved, accessibility re-bound. The Incognito cover was not reached because the phone locked before the check; it remains a device check after unlock. |

The phone update also exposed two separate settings that were off: DNS filtering
was left on but not running, and the built-in adult-text layer was off. Both were
enabled on the phone and are independent of this code change. They are recorded
here because they explain why a normal adult page could have appeared open
before the cover existed. Hisn 1.5.1 was installed and the accessibility service
was re-bound; the phone locked before the Incognito check, so that specific
device check is still pending.

### Signed artifacts

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `direct/hisn-1.5.1-direct.apk` | 31,289,677 | `f0ddaab90a63b6c23418049fbae20052eb70b462e187db422f9ff0dd8cc0d513` |
| `play/hisn-1.5.1-play.apk` | 31,289,673 | `3e2b0ba32445d611f8fda16e7e5419b0243ed2d24348bbf8c66331d5bc5a0192` |
| `play/hisn-1.5.1-play.aab` | 16,891,997 | `25ab2b55aaa46c037373a8073cb404fac9acb55c9ef849dc966eeafbda5e412d` |

## Limits

- **Protected pixels are not inspected.** The cover hides the whole selected
  window; it cannot tell whether the page is adult, benign or a banking page.
  Selected apps that use `FLAG_SECURE` for legitimate reasons will be covered
  too, and the person must leave or disable image checks.
- **The cover is touch-blocking.** Home and app switching remain available; the
  page cannot be tapped or scrolled underneath. The status-bar strip can remain
  visible.
- **Text/DNS are not a substitute for image checking.** They depend on what the
  app exposes and on the domain/text rules; they do not inspect protected
  pixels.
- **No bypass and no accuracy claim.** No root, Xposed, system privilege,
  MediaProjection or secure-flag removal is used. No adult-positive corpus,
  phone adult detection, battery or thermal measurement, and no comparison with
  HaramBlur's accuracy is established.
- **One emulator protected-window test; phone Incognito still pending.** The
  secure fixture is not a reliability study across OEMs, Chrome versions or
  secure windows. The phone update succeeded and the service was re-bound, but
  the phone locked before the Incognito cover check.

## Reproduce

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
sh test-core.sh
python3 check-strings.py
python3 check-notices.py
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

# FLAG_SECURE fixture (isolated debug package; accessibility enabled by hand).
./gradlew :app:clean :app:assembleDebug -PhisnIsolatedVisualCheck=true
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put secure enabled_accessibility_services \
    app.hisn.android.visualcheck/app.hisn.android.TextGuardService
adb shell settings put secure accessibility_enabled 1
adb shell am start --activity-clear-task -n \
    app.hisn.android.visualcheck.test/app.hisn.android.GuardFixtureActivity --es mode visual-secure
```

`ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` is handled in `VisualGuard`; the protected
overlay is `VisualOverlay.showProtected`; the dashboard state is the core
`VisualHealth.PROTECTED` state.
