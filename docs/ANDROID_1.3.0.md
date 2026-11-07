# Android 1.3.0: close the page, rest the app

Development build in the repository, 7 November 2026, version 1.3.0,
versionCode 33. Signed Play and direct artifacts were built and verified. No
1.3.0 phone run is claimed. The detection rules, thresholds and the image model
are unchanged.

## Why

When the word or image layer detected content, the guard sent the browser Home.
The browser kept the page, so opening it again restored the same page and the
package hold closed it again. From the outside the browser looked broken: there
was no visible time limit, no way to close the page first and no choice to rest
longer or lock the screen.

## What changed

- **The page is left before the window.** For an app that can open web links,
  the guard now performs Android's Back action first, then Home. The browser's
  history moves off the detected page, so reopening it does not restore the same
  screen. Other apps only receive Home. This uses the accessibility actions the
  guard already had; it does not close another app's process.
- **A per-app quiet period replaces the fixed one minute.** The app whose screen
  was detected stays closed for **1, 5, 15, 30 or 60 minutes**, default **5**.
  Only that app is held: a detection in one browser or video app does not close
  another selected app. Reopening inside the period returns Home and shows the
  pause screen with the remaining time. The period ends by the elapsed clock;
  it is not a background job.
- **A Settings row chooses the period.** **Settings › Protection › After a
  block** reads *Keep this app closed for 5 min* and opens a 1/5/15/30/60-minute
  choice. The value is stored separately from the sealed rule file because it is
  a comfort setting: changing it never lets a matched word or image pass, and a
  detection always closes the app.
- **The pause screen offers rest and a screen lock.** After a word or image
  detection it shows *Hisn closed this app for about 5 min. It will open again
  after that.* and **Rest 5 more minutes**, **Rest 15 more minutes**, **Rest 1
  hour** and **Lock the screen**. A rest extends the hold for that app; the lock
  uses Android's accessibility lock action (API 28+). Both return Home.
- **The decision is core-tested.** `RestPeriod` fixes the allowed choices,
  rejects arbitrary or negative values, converts minutes to milliseconds and
  rounds remaining time up. The live test fixtures select one minute so their
  62-second recovery checks still mean something; the product default is five.

## Files

| File | Change |
| --- | --- |
| `android/core/src/main/java/app/hisn/core/RestPeriod.java` | allowed choices, sanitizing, milliseconds and rounded remaining minutes |
| `android/core/src/test/java/app/hisn/core/RestPeriodTest.java` | defaults, invalid values, conversions and rounding |
| `android/app/src/main/java/app/hisn/android/AfterBlock.java` | the chosen quiet period, stored outside the sealed rules |
| `android/app/src/main/java/app/hisn/android/TextGuardService.java` | page Back before Home, per-app hold, remaining time, rest/lock actions |
| `android/app/src/main/java/app/hisn/android/RestrictionActivity.java` | closed-time card, Rest 5/15/60 and Lock screen |
| `android/app/src/main/java/app/hisn/android/MainActivity.java` | Settings › Protection › After a block |
| `android/app/src/main/res/values/strings.xml`, `values-ar/strings.xml` | English and Arabic labels and choices |
| `android/app/src/debug/java/app/hisn/android/*.java` | one-minute fixture periods and diagnostics |
| `design/android-after-block-2026-11-07/` | Settings and pause-screen captures |

## Verification

| Check | Result |
| --- | --- |
| `JAVA_HOME=<JBR> sh test-core.sh` | **PASS** — every core suite, including `RestPeriodTest` |
| `python3 check-strings.py` | **PASS** — 563 strings, 16 plurals and 554 source references, English and Arabic |
| `python3 check-notices.py` | **PASS** |
| `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` | **PASS** — 0 lint errors |
| `CoreDeviceTestRunner` on the API 36 emulator | **PASS** on the 1.3.0 build |
| Settings UI on the API 36 emulator | **PASS** — the row reads *After a block / Keep this app closed for 5 min*; the picker lists 1, 5, 15, 30 and 60 min |
| Pause screen on the API 36 emulator | **PASS** — *Hisn closed this app for about 5 min*, Rest 5/15/60 and Lock the screen render; Back to home follows the rest card |
| `check-release.py` and `apksigner` | **PASS** for the Play APK/AAB and the direct APK; certificate `2F:BE:D6:…:70:C5`, unchanged since 1.0.0 |

Captures: [Settings and pause screen](../design/android-after-block-2026-11-07/README.md).

### Signed artifacts

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `direct/hisn-1.3.0-direct.apk` | 5,732,146 | `64fdab016c65d3786b127791fa7cf3ccc709fd910d107d42ff49b23e31ed3d74` |
| `play/hisn-1.3.0-play.apk` | 5,731,206 | `f66359efc221eb71957ed16ab70c7c54fab9cd5867d8f83e1864b4750c70dbaf` |
| `play/hisn-1.3.0-play.aab` | 5,617,902 | `b7aac397716b6e4521342d92a3eaf5e37e0393ccad89cec1f8f63180d4914078` |

The direct APK is the installable artifact. Never upload it to Play. The Play
APK/AAB are signed but not uploaded, and the AAB is not installable by itself.

## Limits

- **Android cannot force-stop another app or power off the phone.** “Close the
  app” is the platform-safe equivalent: Back leaves the page, Home closes the
  window, and the quiet period keeps that package closed. The app remains in
  Recents, and a browser may keep its tabs. Back depends on the browser's
  history: on a page with no earlier entry it will usually leave the browser,
  and on a long history it goes one step back.
- **Lock the screen is API 28+ and is a screen lock, not a phone shutdown.** It
  turns the display off and requires the device's normal unlock; it does not
  change the PIN, block unlocking or power off the phone. It is hidden on
  Android 8/8.1.
- **The quiet period is not sealed with the protection lock.** It is stored
  apart from the rule file because it never changes what is detected. Someone
  can shorten it in Settings during a lock; the app still closes on every
  detection, and the lock's rule freeze is unchanged. The pause screen's Rest
  action only lengthens it.
- **No real detection drove the new close path in this pass.** The Settings and
  pause screens were opened through the debug restriction fixture, not by
  matching adult content, and no adult-positive corpus or real app was used.
  The browser Back step, the per-app hold and the rest/lock broadcasts compile,
  are lint-clean and are wired to the existing intervention, but a live
  detection in a real browser is still a device check.
- **No 1.3.0 phone run.** 1.2.2 was installed on the owner's Samsung; this build
  was not. The emulator's System UI produced an ANR during capture; it was
  dismissed and the captures above are from the running app.
- **Arabic is string-parity checked, not captured.** The Arabic labels were
  added and `check-strings.py` passes; no Arabic screenshot was taken.
- **No new permission, activity, service or stored policy field.** The quiet
  period is a private preference; the rule seal, commitment and DNS/visual
  policies are untouched.

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

# Emulator UI (debug build; no accessibility service needed for the screens).
adb shell am start -n app.hisn.android/.GuardTestSetupActivity --es mode restriction-screen \
    --ez word true --es app com.android.chrome --ei minutes 5 --ez canrest true
```
