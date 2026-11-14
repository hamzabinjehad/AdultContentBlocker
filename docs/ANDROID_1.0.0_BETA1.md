# Android 1.0.0-beta1 (44)

The first public beta of Hisn for Android. The version name restarts at 1.0;
the build number continues from the 1.6.0 (43) development candidate, so a
phone with an earlier signed build updates in place and keeps its settings.

This is a beta, not a finished release. It is built and signed, and it passed
the desktop and emulator suites listed below. It has not been through the phone
acceptance checklist.

## What is in it

- Everything in the 1.6.0 candidate: the DNS filter with the signed list, the
  adult-TLD rail and hostname terms; screen-text and image checks; app blocks,
  schedules and daily limits; the protection lock; signed list updates; the
  site checker. See [the 1.6.0 note](ANDROID_1.6.0.md).
- **Phone-maker setup.** A screen that reads the phone's maker and shows the
  background and battery switches that decide whether protection survives the
  screen turning off. See [the phone-maker note](ANDROID_OEM_PHONES_2026-11-14.md).
- **Wait before opening.** An optional short wait before a chosen app opens.
- **Onboarding checks.** One question per page, ending in a checks page.
- **A faster matcher.** Screen-text scanning allocates about 33× less memory
  and an uncached DNS decision costs roughly half the CPU, with identical
  verdicts. See [the performance note](ANDROID_FILTER_PERFORMANCE_2026-11-14.md).

## How it was checked

- `android/build-signed-release.sh` gates: `lintDebug`, `testDebugUnitTest`,
  `android/test-core.sh` (strings, notices and the JDK-only core suite) and
  string parity.
- `check-release.py` and `apksigner` on both the Play and direct builds: the
  package, version, distribution flag and signing certificate
  (`2fbed657…9470c5`) are the expected ones.
- The seed's Ed25519 signature and file hashes are verified before packaging.

The APK hashes are in the release's `SHA256SUMS` and `BUILD.txt`.

## What is not verified

- **No phone acceptance run.** The checklist in
  [the 1.6.0 release checklist](ANDROID_1.6.0_RELEASE_CHECKLIST.md) was not
  walked on a physical phone for this build.
- **The instrumented UI suite was not re-run** for this build.
- **After a reboot the dashboard can read "disconnected"** until the
  accessibility service is re-enabled. This limit is carried from 1.6.0.
- **Phone makers other than Samsung** are unverified on real hardware.
- **Image checks need Android 14+.** Older phones get the DNS, text and app
  layers only.
- The matcher performance numbers come from a desktop JVM, not a phone.

## Installing

Install `hisn-1.0.0-beta1-direct.apk` and check its SHA-256 first. The direct
build can protect its own uninstall and accessibility screens during a lock;
the Play build cannot, by Google Play policy.
