# Android on Samsung, Xiaomi, Nothing and the other phone makers

Date: 14 November 2026. Status: implemented in the 1.6.0 / 43 candidate; version,
versionCode and artifact state are unchanged. This note covers the phone-maker
setup layer, the Android readings behind it, and what was and was not run on
hardware.

## Why this exists

Android's own battery optimization is not the whole background story. Several
makers add a second manager:

- **Samsung** Device care sleeps apps, its power screens are guarded by a
  Samsung signature permission, and its app-info page carries both **Battery**
  and **Manage app if unused**.
- **Xiaomi / Redmi / POCO** hide an **Autostart** switch outside Android
  Settings; without it Android stops the app after it is closed.
- **Nothing OS** and stock Android only use Android's own rules.
- **ColorOS**, **OriginOS**, **EMUI/MagicOS** and **HiOS** hide the same kind of
  autostart or launch switch.
- Several of these ship a **second space / Secure Folder / dual app**: a
  separate Android profile a single installation does not cover.

The app already handled Android's battery request. On the owner's Galaxy
SM-G990B2 / Android 16 that request is not implemented, so the old **Keep
protection running** action could not open anything useful. That is the
concrete bug this pass fixes.

## What was checked on the Galaxy (SM-G990B2, Android 16 / API 36)

Read-only probes with `adb shell cmd package resolve-activity` and
`am start`:

| Probe | Result |
| --- | --- |
| `android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | **No activity found** |
| `com.samsung.android.sm.ACTION_START_APP_POWER_MANAGEMENT_SETTING` | Resolves, but starting it throws `SecurityException`: requires `android.permission.READ_SEARCH_INDEXABLES` |
| `devicecare://dc.app_power_management/...` deep link | Same signature permission; refused |
| `android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS` | Opens `com.android.settings/.Settings$AppBatteryUsageActivity`, titled "Manage battery usage", listing each app and its standby state |
| `android.settings.APPLICATION_DETAILS_SETTINGS` | Opens app info, which carries **Battery** and **Manage app if unused** |
| `android.settings.VPN_SETTINGS` | Opens the VPN list |
| Build strings | `samsung / samsung / SM-G990B2`; classified as `SAMSUNG` |

So the Device care screens are not reachable by an ordinary app, and the
reachable path is app info (or the battery-usage list). The app now opens app
info and names the two switches in the phone copy.

### What was checked on a Xiaomi-labelled AOSP emulator

There is no official MIUI system image. A disposable API 36 AVD was booted with
`-writable-system`, and the five `build.prop` partitions were set to
manufacturer **Xiaomi**, brand **Redmi**, model **23122PCD1G**. The guest then
reports those strings, and `OemSupport` reads them through `Build`; no test fake
is involved in the classification.

| Check | Result |
| --- | --- |
| `PhoneSetupTest` on this AVD | **5 of 5** |
| Full `test-ui.sh` suite on this AVD | **39 of 39** in 187 s; the tree had gained the separate Open Wait tests (4) since the 35-test run on the Google image |
| Real app Home | "Xiaomi may pause Hisn" |
| Real app Settings row | "Xiaomi · 3 to check" |
| Phone screen | "Xiaomi 23122PCD1G", family "Xiaomi, Redmi or POCO", Autostart and Recents rows |
| Battery row | AOSP's one-tap "Let app always run in background?" dialog; allowing it changed the row to Unrestricted and the count to 2 without a restart |
| Autostart → Open | Fell back to app info (`com.android.settings/.applications.InstalledAppDetails`) because the MIUI security centre is absent; no crash |
| Autostart tick | Count changed to 1; after a force-stop and cold start the box was `checked="true"` and the count stayed 1 |

The image has no MIUI powerkeeper or security centre, so MIUI's own screens,
their component names and its power policy are **not** exercised; the
classification, the screen, the live Android readings, the fallback and the
saved tick are. Captures and reproduction steps:
[the Xiaomi captures](../design/android-oem-2026-11-14/README.md).

## The model

Two pure core classes carry the logic; neither reads Android state.

### `DeviceFamily`

Nine families and the traits that decide which rows appear:
`STOCK`, `NOTHING`, `SAMSUNG`, `XIAOMI`, `OPPO` (OPPO/realme/OnePlus), `VIVO`
(vivo/iQOO), `HUAWEI` (Huawei/HONOR), `TRANSSION` (TECNO/Infinix/itel), `OTHER`.
Classification reads `Build.MANUFACTURER`, `Build.BRAND` and `Build.MODEL`,
normalises case, spaces, hyphens and dots, and returns `OTHER` for an unknown
build rather than guessing an aggressive family.

Traits: whether the maker has an autostart screen, kills apps not locked in
Recents, ships a second space, has an extra install switch, and is known to
manage background apps aggressively. `STOCK`, `NOTHING` and `OTHER` have none
of them; nothing is shown for them beyond Android's own readings.

### `OemGuide`

The ordered checklist keeps measured and unmeasurable facts apart:

| Row | Status source |
| --- | --- |
| Battery | live `PowerManager.isIgnoringBatteryOptimizations` |
| Background activity | live `ActivityManager.isBackgroundRestricted` (Android 9+; the row is omitted below) |
| Autostart | owner's tick after changing the maker's hidden screen |
| Keep in Recents | owner's tick after locking the app in Recents |

`ACTION` is a real measured block; `CONFIRM` is unverified; `DONE` is either.
A family is settled only when every applicable row is done. The copy says that
this is advice about keeping the layers alive, not proof of coverage.

Notes (not tasks): a second space is not repaired by this install, and a manual
update may cross a maker install switch.

## The app

- **`OemSupport`** classifies the build, reads the two live states, and opens
  the maker's screen with ordered fallbacks. Xiaomi tries its battery and
  autostart components; ColorOS, OriginOS and EMUI/MagicOS try their autostart
  components; every family falls through to the standard battery request,
  app info and the battery list. A launch that throws is skipped; the helper
  never reports success for an activity that did not start.
- **The phone screen** (Settings → **Keep protection running**, and the top of
  Permissions) shows the model, the Android release, the family sentence, the
  checklist, Always-on VPN, and the second-space/install notes. The existing
  Settings row now carries the live state ("Samsung · 2 to check" / "ready").
- **Battery and background restriction are real rows.** When either is wrong,
  Home shows one attention card, "Samsung may pause Hisn", linking to the
  phone screen. It appears even while every layer happens to be running,
  because it is what stops them when the screen goes off.
- **The old battery action** now goes through `OemSupport.openBattery`, so the
  Samsung dead end is gone. A failed launch still shows the existing
  "open it from Settings → Apps → Hisn" message.
- **Always-on VPN** is explained and linked: Android can reconnect the DNS
  tunnel after a restart, which no maker switch can do.
- **No new permission, stored field or network path.** The two confirmations are
  local preferences; the phone maker is read from `Build`, not from a server.

## Verification

| Check | Result |
| --- | --- |
| `DeviceFamilyTest` | 28 build strings classified (Samsung, Redmi, POCO, Nothing, Pixel, Motorola, Sony, OPPO, realme, OnePlus, vivo, iQOO, Huawei, HONOR, TECNO, Infinix, itel, unknown, null/empty); 9 families checked for consistent traits |
| `OemGuideTest` | 216 phone states; row presence, measured status, confirmations, next step, open count, notes; Samsung, Xiaomi and Nothing row by row |
| `android/test-core.sh` | Pass (core, strings, notices, seed, corpus); the new classes print their counts |
| String parity | Pass (845 strings); every new key present in English and Arabic with matching placeholders, and no unused phone key is shipped |
| `:app:lintDebug` | Pass, 0 errors; one `BatteryLife` warning for the standard request (the same API the app already used) |
| `:app:assembleDebug` / `assembleDebugAndroidTest` / `assembleRelease` | Pass (the release is unsigned: no key was supplied in this pass) |
| `android/test-ui.sh` on `hisn_ui` (API 36) | **35 of 35**, twice (305 s and 2,179 s; the emulator load varied, the result did not). Onboarding 17, PhoneSetup 5, Shell accessibility 5, Shell navigation 8 |
| `PhoneSetupTest` on the API 36 emulator | 5 of 5 (Samsung, Xiaomi, Nothing, Home attention card, real-build classification) |
| `android/test-ui.sh` on a Xiaomi-labelled AOSP emulator (API 36) | **39 of 39** in 187 s on the current tree; `PhoneSetupTest` 5 of 5, plus the real-app walkthrough below |
| `PhoneSetupTest` on the Galaxy, isolated `app.hisn.android.visualcheck` copy | **1 of 1** non-UI test (`theRealBuildIsClassifiedAndItsBackgroundSwitchReads`); the four Compose tests could not host their activity because the phone was on its lock screen. The copy and its test APK were uninstalled afterwards; the installed 1.6.0-direct Hisn was not touched |

The UI tests use `FakeShellActions` for the layout tests and the real build for
classification. One earlier full emulator run failed
`OnboardingTest.checksAskBeforeTheyTurnOnAndPickApps` on a `Chosen: 1`
visibility assertion after its app-count assertion had passed; a clean rerun
passed 35 of 35 and that class was not changed in this pass.

The test harness had built a new `HisnState` inside the composition lambda; a
phone state written during construction then re-invalidated the root and
looped. The harness now remembers one state object. That is a
test-only fix; `ShellActivity` has always created one state in `onCreate`.

## Limits

- **The Galaxy's UI tests were blocked by the lock screen.** The phone was
  awake but showing its keyguard, so the Compose test host activity was stopped
  as soon as it launched. Only the non-UI classification test ran there. The
  five Compose checks are emulator evidence, not phone evidence.
- **No retail Xiaomi, Nothing, OPPO, vivo or Huawei phone ran this build.**
  The Xiaomi run is an AOSP image with Xiaomi build properties; MIUI's own
  screens and power policy were not present. The ColorOS, OriginOS,
  EMUI/MagicOS and HiOS component names are the documented ones, and every
  failed launch falls back. Nothing OS is treated as stock because its public
  behaviour is stock; that is a family classification, not a device test.
- **Autostart cannot be read.** Hisn cannot tell whether the maker switch is on.
  The tick is the owner's word, is stored on the phone, and is never counted as
  protection.
- **A force stop still wins.** No app can restart itself after Settings' Force
  stop; the phone screen says so.
- **The second space is not covered.** A separate profile needs its own install;
  the note says that rather than claiming coverage.
- **No battery-life claim.** The readings say whether Android is likely to stop
  the app, not how much battery it uses.
- **Version and artifacts unchanged.** This is still the 1.6.0 / 43 candidate:
  no signed 1.6.0 APK was produced in this pass, and the owner's installed
  1.6.0-direct build was not replaced.

## Second pass: background pop-ups (same day)

Blocking already works on every maker: the guard presses Home first (an accessibility global
action needs no maker permission) and only then shows the explanation. Wait before opening was
different: it only started an activity. **Xiaomi** ("Display pop-up windows while running in the
background") and **vivo** ("background pop-ups") can drop that start without an error, so the
chosen app would have opened with no wait.

- **Overlay fallback.** The guard starts the wait activity and checks 2 s later that it really
  resumed. If not, it draws the same `OpenWaitScreen` as a `TYPE_ACCESSIBILITY_OVERLAY`
  (`OpenWaitOverlay`), which belongs to the accessibility service and needs no maker or overlay
  permission. Close presses Home; Open removes the overlay; leaving for another app removes it
  (after a 1.5 s grace for the launcher's own closing events); a wait activity that appears late
  removes the overlay, so there is never a double wait.
- **A fifth checklist row, "Pop-ups from the background"** (`OemGuide.Step.BACKGROUND_POPUPS`),
  for Xiaomi and vivo only. Xiaomi's value is read live (MIUI app op 10021 through
  `checkOpNoThrow`); when a build does not answer, and always on vivo, the row is a tick like
  Autostart. A live denial is never overridden by a tick. "Open" goes to MIUI's
  `APP_PERM_EDITOR` or vivo's permission page, then app info. The five-argument checklist is
  unchanged; the Settings count and the phone screen use the new one.

Checked: `OemGuideTest` (pop-up families, live/tick/denial, order, open count), `PhoneSetupTest`
6 of 6 (new: a Xiaomi denial shows the row and opens it), `OpenWaitTest` 4 of 4. On the API 36
emulator with a debug-only switch (`settings put global hisn_debug_wait_overlay 1`, ignored in
release builds) forcing the overlay: it appeared over Chrome, Close went Home and counted, Open
removed it and Chrome stayed. Without the switch the activity path still runs alone.
Not checked: a retail Xiaomi or vivo phone (no MIUI op or permission page exists on the emulator).
