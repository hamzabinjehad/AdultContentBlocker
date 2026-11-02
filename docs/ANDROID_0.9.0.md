# Android 0.9.0: the protection lock

Released in the repository on 2 November 2026 as version 0.9.0, versionCode 25.
This is a development preview: the APK is unsigned and no physical phone run of
this build is claimed.

## What changed

The in-app commitment is presented as the **protection lock**, and its promise
is now stronger and simpler: while a lock runs, no saved rule can change inside
Hisn.

- All rule writers go through `PolicyStore.save`, which refuses a change when
  `ProtectionLock.locked(remainingMillis)` is true. That covers domains, words,
  blocked and inspected apps, the browser selection, the daily schedule, the
  built-in adult/visual/short-video switches, the SafeSearch rewrite and the
  gray-screen choice.
- The earlier "tighten only" rule allowed adding restrictions during a
  commitment. It was removed on purpose. The only in-app change that remains is
  starting a longer lock; a guardian-signed policy may still add rules through
  the separate `updateFromGuardian` path, which is tighten-only.
- The direct build's accessibility removal guard is armed by a running lock even
  when the keep-installed switch is off. `TextGuardService`, the boot-shield
  copy and `LayerWatchdog` use the same effective state, so the guard closes
  Hisn's uninstall, App info, disable, Safe Mode and VPN screens while the lock
  runs.
- On a device-owner install, Android's `setUninstallBlocked` was already applied
  for the lock. Managed lockdown remains a separate opt-in. The Play build
  cannot make Android refuse its removal; while a lock runs it now adds an
  explicit attention item saying so.
- The Rules page shows a "Rules are locked" card with the remaining time and
  disables every rule control. The Protection hero names the lock, and when
  rules exist without a lock it says they can still be changed and points to
  the lock action. The uninstall card distinguishes "on for the lock", "guard
  disconnected" and ordinary off states.

## Verification

Run on this machine against the 0.9.0 tree:

| Check | Result |
| --- | --- |
| `JAVA_HOME=<JBR> sh test-core.sh` | **PASS** — includes `ProtectionLockTest`, the existing 7,680 setup combinations, managed restrictions and seed tests |
| `python3 check-strings.py` | **PASS** — 440 strings, 14 plurals, English and Arabic |
| `./gradlew assembleDebug assembleDebugAndroidTest lintDebug` | **PASS** — 0 lint errors |
| `CoreDeviceTestRunner` on the API 36 emulator | **PASS** — seed, saved rules, the full-freeze rejections, adult/visual commitment, uninstall guard, damaged settings, layer watch and seed age |
| Product UI on the API 36 emulator | **PASS** — one-day lock started through Rules; the locked card, disabled switches/rows/schedule and remaining time were read back through the accessibility tree; Arabic RTL capture taken. See [design captures](../design/android-protection-lock-2026-11-02/README.md) |
| `./gradlew assembleRelease bundleRelease`, then `./gradlew assembleRelease -PhisnDistribution=direct`, with `check-release.py` | **PASS** — Play release is `app.hisn.android 0.9.0 (25)`, direct is `app.hisn.android 0.9.0-direct (25)`; both non-debuggable, no DUMP permission and no debug-only component or class |

Artifacts (unsigned, not publishable):

- Play release APK SHA-256
  `b1887534dccc300c692f50838b8733676ef4939f8866179ac6135c001a1b23fe`.
- Direct release APK SHA-256
  `5336e5eae797202cc868a04df8ef99959a29cfe71aa4434f4e0dcf748a7344f6`.
- Release AAB SHA-256
  `4647d666edd6068262659a2eab2631fc470529aa3262f157004502dc82526ab7`.

## Not proven

- No physical-device run of this build. The rules freeze and the automatic
  direct-build guard arming are code and unit-test evidence; the emulator run
  used the Play debug build and its pre-existing device-owner state, so it
  exercised the device-owner uninstall branch, not the direct guard.
- The Play build cannot prevent its own uninstall. The direct guard is friction
  and a legacy-admin confirmation step, not an Android uninstall block.
- The guard still depends on the accessibility service being on; Android offers
  no supported API that stops the person turning it off. Boot-key Safe Mode,
  ADB, root and a factory reset remain outside it.
- A lock freezes rules inside Hisn, not Android settings, app data, permissions,
  the device clock or another device.
