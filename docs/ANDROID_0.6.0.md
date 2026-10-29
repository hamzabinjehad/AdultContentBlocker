# Hisn Android 0.6.0 — optional managed uninstall protection

Development candidate, 28 October 2026. Not a Play release, not a signed
production build, and not a claim that the connected owner's phone is
undeletable. No device-owner appointment, factory reset, account removal or
administrative policy was applied to that phone.

> **Branch note:** this record was cut from `android-0.6.0` before the 0.5.5
> merge, so its tested APK is versionCode 15. `release/1.0` builds 0.6.0 as
> versionCode 18; the merged build has not been run on a phone. 0.7.0
> (versionCode 19) adds the managed lockdown and is in
> [ANDROID_0.7.0.md](ANDROID_0.7.0.md).

## What changed

- Add a pure `UninstallProtection` state model for the device-owner uninstall
  guard: unavailable, admin-only, ready, protected and out-of-sync. The model
  treats the platform readback as authoritative and exposes a mismatch instead
  of hiding it.
- Add `PolicyStore` state for **Keep Hisn installed**. It can be enabled at any
  time and cannot be disabled while a commitment runs. The stored setting does
  not change saved rules, schedules or the commitment clock.
- Add `UninstallProtectionController`, which:
  - reads `isDeviceOwnerApp` and `isUninstallBlocked`;
  - applies `setUninstallBlocked` only when Hisn is device owner and either a
    commitment is active or the keep switch is on;
  - verifies the platform readback after every write;
  - releases device ownership only when both the commitment and the keep switch
    are off, clearing the uninstall block first;
  - never changes a non-managed install. The only UI on such a build is the
    setup explanation and the exact owner command.
- Add Protection and Guide status/actions, English and Arabic disclosures, and
  a recovery action (**Remove managed mode**). No accessibility trap, hidden
  setting, account change or silent enrollment was added.
- Reconcile on boot and on foreground entry. If a commitment expires while the
  app is not running, the block may remain until Hisn next runs; that is the
  restrictive direction and is stated in the UI.

## Arabic matching parity

The Android matchers previously used their own partial normalisation. They now
share `TextFolding`, which applies the same signed-list folds as Python,
JavaScript and Swift: hamza alef forms, alef maqsura, taa marbuta, Arabic-Indic
digits, and the Persian/Urdu spellings of the same Arabic letters (keheh, farsi
yeh, yeh barree, heh goal, teh marbuta goal, noon ghunna). Peh, tcheh, jeh and
gaf are different letters and are deliberately not folded.

`WordMatcher` now normalises custom rules and input with NFD and the same folds,
so a user rule typed as `إباحي` matches `اباحي` and `سکسی` matches `سكسي`.
`AdultTextPolicy` does the same for the signed vocabulary. Multi-word phrases
also match their no-space spelling (`nudephotos`) through a compile-time alias
that points at the canonical entry, keeps its weight and does not inflate the
rule count or override a real single-word rule. Strong brand terms such as
`pornhub`, `xvideos` and `redtube` are now eligible for compact-spelling
recovery; `onlyfans` deliberately remains contextual because ordinary budgeting
guides use it.

## Verification

Host/JDK core suite (`cd android && sh test-core.sh`) passes, including the new
`TextFoldingTest`, `WordMatcher` cross-spelling checks, `AdultTextPolicy` glued
and Persian/Urdu fixtures, and the `UninstallProtectionTest` owner/commitment
matrix, readback mismatch, switch and release guards. The actual signed seed
recognizes `شاهد سکسی`, `Watch nudephotos` and `Watch p0rnhub`, while keeping
`شاهد سكسونيا` available. The rest of the core suite (DNS, packets, TLS,
commitments, schedule, visual policy) also passes.

Builds and lint:

- `./gradlew --no-daemon assembleDebug assembleDebugAndroidTest`: **PASS**.
- `./gradlew --no-daemon assembleRelease`: **PASS** (unsigned; no production key
  was provided). The release manifest contains no `ManagedStatusActivity`, and
  its resources contain the managed-uninstall strings. Release APK SHA-256:
  `84d5a3d58cc50cb6c1df68f95a794292dbe9b741220e5d391e4bc5043c10c578`.
- `./gradlew --no-daemon lintDebug`: **PASS**, 0 errors and 19 warnings. The
  warnings are the existing debug-activity/preference, API-level attribute,
  plural-heuristic and debug `setText` findings, plus the direct-build guard;
  the new hour string follows the existing hours-string pattern.
- Debug APK used for device testing (direct sideload build):
  `android/app/build/outputs/apk/debug/app-debug.apk`
  - versionName `0.6.0-direct`, versionCode `15`
  - SHA-256 `f674d7b375ef34b448382226f424b361b4da0934400234d998166416553552b7`
  - Test APK SHA-256
    `62de24512cdd81c42d907565ff041c0d004d7b91e7f052a5a9866ff4d5f23021`
  - debug certificate SHA-256
    `ca819da2f204fdaecb5fde95fd3f6e22b05f80843d625a99439f7f8eee8f6c61`
  - This is a development certificate, not a production release key.

A debug-only, read-only `ManagedStatusActivity` exports the same controller state
as JSON to private storage. `android/test-live-managed-status.py --serial SERIAL`
checks it without applying, clearing or releasing anything; use
`--expect-managed` only on a disposable provisioned device.

Connected device check, 2026-10-28, Samsung SM-G990B2 (Android 16):

- The installed APK is the current direct build above; its bytes match the
  pulled device APK SHA-256 exactly (`f674d7…`). The test APK
  (`62de2451…`) was installed over ADB.
- `adb shell am instrument --user 0 -w
  app.hisn.android.test/app.hisn.android.CoreDeviceTestRunner` returned
  **PASS**. It loaded 159,762 domains and passed Arabic matching, boundaries,
  local DNS block, saved rules, Unicode persistence, long-text sampling, the
  actual adult-text seed, visual settings, commitment and uninstall-guard
  checks. It did not enable a VPN, accessibility, admin or device-owner policy.
- `test-live-managed-status.py` returned **PASS**:
  `deviceOwner=false`, `adminActive=false`, `uninstallBlocked=false`,
  `keepInstalled=true`, `remainingMillis=0`, state `UNAVAILABLE`. The ordinary
  install is honestly not managed; Android can still uninstall it.
- `dpm list-owners` still shows only Samsung Secure Folder's profile owner in
  user 150, not Hisn. **An actual device-owner uninstall refusal, expiry
  reconciliation and release still require a disposable provisioned device.**
  That remains a documented external limit, not a claimed phone result.

## How it is meant to be used

The ordinary install and the legacy device admin cannot make Hisn undeletable.
Only a device owner can. The owner path is deliberately outside the app:

1. Use a device that can be reset, with no accounts and no other device owner.
2. Install the app, then run:
   `adb shell dpm set-device-owner app.hisn.android/.CommitmentAdminReceiver`
3. Open Hisn, confirm the status reads device-owner ready, then enable **Keep
   Hisn installed** or start a commitment.
4. To remove it later, open Hisn when no commitment is running, turn the keep
   switch off, choose **Remove managed mode**, then uninstall through Android.

A daily-use phone with accounts or an existing device owner cannot safely be
converted in place. Device-owner removal, ADB/root, a factory reset, a different
Android user and a managed-device console are outside what this guard survives.
See [the tamper-resistance note](ANDROID_TAMPER_RESISTANCE.md).

## Remaining acceptance

- Reconnect the Samsung and run the instrumented runner on 0.6.0. It must show
  "Not managed" and must not alter platform policy.
- On a disposable, freshly provisioned device owner: verify the uninstall
  dialog refuses removal, the readback says protected, commitment expiry clears
  the block when the keep switch is off, and release plus uninstall works.
- Review Google Play/DPC policy before distributing a device-policy controller.
  This preview is for sideloaded, owner-controlled devices.
- Full disable prevention, data-clearing prevention, full-traffic VPN and
  resistance to a device administrator remain out of scope.
