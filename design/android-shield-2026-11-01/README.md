# Hisn Android protection page — 2026-11-01

Emulator captures for the direct-build screen guard described in
[Android tamper resistance](../../docs/ANDROID_TAMPER_RESISTANCE.md). These are
local API 36 emulator captures, not physical-device, TalkBack, OEM-skin or
maximum-text-size acceptance.

## Device and build

- AVD `hisn_ui`: 1080 × 2400 px, 420 dpi, Android 16
  (`system-images;android-36;google_apis;arm64-v8a`), software GPU, headless.
- Debug APK from this working tree with `-PhisnDistribution=direct`
  (versionName `0.8.4-direct`), installed over the previous debug copy.
- Accessibility service enabled with
  `adb shell settings put secure enabled_accessibility_services app.hisn.android/app.hisn.android.TextGuardService`.
- The owner switches were turned on through the debug-only
  `GuardTestSetupActivity` (`--es mode self-protection-on`), which uses the real
  `PolicyStore`.
- The Arabic capture used
  `adb shell cmd locale set-app-locales app.hisn.android --locales ar`.
- No repository setting, seed, list or signed release was changed. The
  emulator's own app data is disposable.

## Files

| File | State |
| --- | --- |
| `01-protection-page-uninstall.png` | Live trigger: `android.settings.APPLICATION_DETAILS_SETTINGS` for Hisn was closed and the uninstall reason page appeared. |
| `02-protection-page-install.png` | Live trigger: a Play Store app page (`market://details?id=com.android.chrome`) was closed and the install reason page appeared. The same matcher also blocks the store home and a search result that offers Install; only the app page was captured. |
| `03-protection-page-arabic.png` | The same page in Arabic (RTL), opened through the debug fixture with the disable reason. |

## What the captures show

- The page is full-screen, names one fixed reason, and never shows captured
  text, a hostname or a page title.
- *Back to Hisn* and *Go to home screen* stay disabled for eight seconds; the
  countdown and progress bar show the remaining hold.
- The system status bar remains visible. The system Home button, the dialler and
  emergency screens are not covered.
- After the hold, tapping *Back to Hisn* opened `MainActivity` (checked with
  `dumpsys activity activities`).

## Not covered

The real package-installer Install confirmation, the protection dialog with the
new install switch, TalkBack, dark mode, an OEM skin, a physical device and
maximum font scale were not captured. The system Home button remains an
always-available exit; a page with no exit would be a trap and is outside the
project's stated scope.
