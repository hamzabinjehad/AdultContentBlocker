# Hisn Android 0.6.0 — optional managed uninstall protection

Development candidate, 26 October 2026. Not a Play release, not a signed
production build, and not a claim that the connected owner's phone is
undeletable. No device-owner appointment, factory reset, account removal or
administrative policy was applied to that phone.

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
  `3f93edc050734c1255c93af0d0ddbf36f20bb6409f3ace1aef9ebde672cea017`.
- `./gradlew --no-daemon lintDebug`: **PASS**, 0 errors and 18 warnings. The
  warnings are the existing debug-activity/preference, API-level attribute,
  plural-heuristic and debug `setText` findings; the new hour string adds one
  plural-heuristic warning and follows the existing hours-string pattern.
- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
  - versionName `0.6.0`, versionCode `15`
  - SHA-256 `fde8f10e1ab786a1e33696793a72d42d3776a5342ed924ee3debb8ad8d7f47f4`
  - Test APK SHA-256
    `6358d600b2b67391b4686cf34b5017bd240b04dc19830859f14264ed63b0c608`
  - debug certificate SHA-256
    `ca819da2f204fdaecb5fde95fd3f6e22b05f80843d625a99439f7f8eee8f6c61`
  - This is a development certificate, not a production release key.

A debug-only, read-only `ManagedStatusActivity` exports the same controller state
as JSON to private storage. `android/test-live-managed-status.py --serial SERIAL`
checks it without applying, clearing or releasing anything; use
`--expect-managed` only on a disposable provisioned device.

Connected device check: the Samsung SM-G990B2 (Android 16) was detected over
ADB. `dpm list-owners` showed Samsung Secure Folder as a profile owner in user
150, not Hisn; the app was not a device owner. Installing the 0.6.0 debug APK
and running `CoreDeviceTestRunner` was attempted, but the device disconnected
from ADB before the install. **No 0.6.0 device or blocked-uninstall result is
claimed yet.**

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
