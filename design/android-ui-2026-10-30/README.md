# Hisn Android UI captures — 2026-10-30

Captured on a local Android 16 (API 36) emulator for the UI/UX pass described
in [docs/ANDROID_UI_REFRESH.md](../../docs/ANDROID_UI_REFRESH.md). These are
emulator captures, not physical-device, TalkBack or maximum-text-size
acceptance.

## Device and build

- AVD `hisn_ui`: Pixel 7 profile, 1080 × 2400 px, 420 dpi, Android 16
  (`system-images;android-36;google_apis;arm64-v8a`), software GPU, headless.
- Debug APK from this working tree: version 0.8.2, versionCode 22, seed v5
  (239,885-domain core tier). Captures 18 and 19 use the same debug build with
  `-PhisnDistribution=direct` (versionName `0.8.2-direct`).
- Per-app locale was set with
  `adb shell cmd locale set-app-locales app.hisn.android --locales ar` for the
  Arabic captures; light/dark used `adb shell cmd uimode night no|yes`.
- Large text is the system font scale at 1.30. Landscape is rotation 1 at the
  same resolution. Default captures use scale 1.0 and portrait. Capture 20 uses
  the system keyboard. The build for 18–20 is the direct debug variant.

## Files

| File | State |
| --- | --- |
| `01-protection-light.png` | Protection, clean install, light, English |
| `02-rules-light.png` | Rules, light, English |
| `03-guide-light.png` | Guide, light, English |
| `04-protection-arabic.png` | Protection, light, Arabic (RTL) |
| `05-rules-arabic.png` | Rules, light, Arabic (RTL) |
| `06-protection-dark.png` | Protection, dark, English |
| `07-rules-dark.png` | Rules, dark, English |
| `08-rules-short-video-on.png` | Rules after enabling short-video blocking |
| `09-protection-attention.png` | Protection with the attention list and an inaccessible app layer |
| `10-restriction-pause.png` | Restriction screen with the breathing pause |
| `11-restriction-need.png` | Restriction screen after choosing “Rest” |
| `12-protection-details.png` | Expanded “What this means” disclosure |
| `13-uninstall-dialog.png` | Uninstall protection dialog, Play debug build |
| `14-commitment-dialog.png` | Commitment period chooser |
| `15-commitment-confirm.png` | Commitment disclosure before starting |
| `16-protection-large-text.png` | Protection at 130% font scale |
| `17-protection-landscape.png` | Protection in landscape, capped reading width |
| `18-protection-direct.png` | Protection, direct build (`0.8.2-direct`) |
| `19-uninstall-direct.png` | Uninstall dialog, direct build with the accessibility screen guard |
| `20-guide-tester-keyboard.png` | Guide tester with the keyboard open; the bottom bar hides and the focused field stays above it |

## Secure windows

The app chooser, domain/word editor and schedule editor are `FLAG_SECURE`
windows by design, so system screenshots are black. They were checked instead
with `adb shell uiautomator dump`:

- App-picker rows expose `CheckBox`, `checkable=true` and the correct `checked`
  value; a UI-tree pass found that the checked state was lost after a rebind,
  and the row delegate is now reapplied on every bind.
- The domain editor exposes the private-screen note, the input, the
  `0 lines · 1000 maximum` counter, Cancel and Save rules.
- The schedule editor keeps the same fields and validation as before.

## Reproduction

```sh
# Build and install the Play debug build
cd android
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :app:assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk

# Launch a page directly (0 Protection, 1 Rules, 2 Guide)
adb shell am start -W -S -n app.hisn.android/.MainActivity --ei page 1
adb exec-out screencap -p > screen.png

# Restriction screen through the debug-only shell fixture
adb shell am start -W -n app.hisn.android/.GuardTestSetupActivity \
  --es mode restriction-screen --ez word true
```

Screenshots are not evidence of enforcement, VPN routing, accessibility
interception, physical-device rendering, TalkBack order or a real app review.
