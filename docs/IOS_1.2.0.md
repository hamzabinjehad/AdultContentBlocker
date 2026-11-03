# Hisn iPhone and iPad 1.2.0 — the protection lock

Released 3 November 2026 as a development candidate. Simulator-tested only; no
Apple team, Family Controls entitlement, signed build or physical-device
acceptance is claimed.

## Why

Android 0.9.0 introduced the protection lock: while a commitment runs, the app
does not allow any saved rule to change, not merely the changes that weaken
protection. iOS still allowed two exceptions during a commitment — adding a
blocked site and strengthening an app limit. 1.2.0 closes that gap for the
rules the app owns.

The freeze is deliberately limited to **blocked sites** and **app-limit rules**.
A protection layer that is missing or was removed by iOS can still be added or
restored, because that only increases coverage; it is recovery, not a rule
change. Turning a layer off remains unavailable.

## What changed

### Rules freeze

- `CommitmentPolicy.rulesLocked(active:commitmentReadable:)` is the shared
  decision. An active commitment or unreadable commitment storage counts as
  locked.
- **My blocked sites:** adding, removing and parent-domain consolidation all
  wait until the commitment ends. The entry field and Add button are disabled;
  the footer says the list is frozen. The controller refuses a direct add with
  `sites.locked`, so a SwiftUI control is not the only guard.
- **App limits:** choosing apps, changing the mode or daily minutes, applying a
  rule, strengthening always-block, and clearing the rule are all disabled.
  `AppUsageController` refuses any change with `apps.locked`; an identical save
  is still a no-op. `AppUsageConfiguration.permits` now requires full rule
  equality while committed.
- **Extending** the commitment remains available, as on Android.

### Layer recovery stays available

- The Screen Time filter cannot be turned off inside Hisn during a commitment.
  It can still be enabled or restored after iOS revokes access, because that
  only restores protection.
- DNS setup and the Safari rule reload remain available: they add or repair
  layers and do not rewrite the frozen rule lists. iOS system settings remain
  outside the app's control, and the app says so.
- The commitment section now shows **Protection lock running** and **Saved
  rules are frozen: blocked sites and app limits**, in English and Arabic.

### Copy and tests

- Shortened English and Arabic copy explains the rule freeze, not a general
  “nothing can change” promise. The Android-style lock is a rule lock, not an
  OS device-owner lock.
- New unit coverage: shared lock decision, full site-list freeze, full app-rule
  freeze, controller refusal for both, and the unchanged no-op save path.
- The iOS Safari scanner packaging allowlist now includes `lib/language.js`, the
  shared language-aware scorer introduced in extension 1.3.0.

## Files

| File | Change |
| --- | --- |
| `shared/apple/CommitmentPolicy.swift` | `rulesLocked` / `mayChangeRules` |
| `shared/apple/CustomSitePolicy.swift` | Full list freeze when committed |
| `ios/Shared/AppUsageConfiguration.swift` | Full app-rule equality when committed |
| `ios/Hisn/ProtectionController.swift` | `rulesLocked`; add/refuse site and layer gates |
| `ios/Hisn/AppUsageController.swift` | Refuses apply/clear when rules are locked |
| `ios/Hisn/CustomSitesSection.swift` | Disabled field/button while frozen |
| `ios/Hisn/AppUsageSection.swift` | Disabled picker/mode/minutes/apply/clear |
| `ios/Hisn/CommitmentSection.swift` | Lock row, frozen-rules row, Screen Time copy |
| `ios/Hisn/en.lproj`, `ios/Hisn/ar.lproj` | Freeze copy in both languages |
| `ios/HisnTests/...` | Freeze tests; Safari allowlist includes `language.js` |
| `ios/generate_xcodeproj.py`, project | Version 1.2.0, build 6 |

## Verification

- `./test.sh ios` — **185 tests, 0 failures** on the iPhone simulator after the
  two failures found in the first run were fixed. The failures were the new
  Screen Time recovery path and the pre-existing Safari scanner allowlist.
- `python3 -m unittest ios.test_localization` — pass; English/Arabic keys and
  placeholders match, and every looked-up key exists.
- `./test.sh python` — pass (includes the iOS localization and Safari packaging
  checks).
- `python3 script/versions.py --check` — pass.
- No physical device, Family Controls entitlement or signed IPA was available.
  The Screen Time app-limit behavior and the Screen Time access-recovery path
  still need a real device.

## Limits

- This is not Android's device-owner lock. On an unsupervised iPhone the person
  can still revoke Screen Time access or delete Hisn. The passcode vault and the
  supervised profile remain the removal-protection paths; 1.2.0 does not change
  them.
- Safari extension switches and DNS are controlled by iOS Settings; the app can
  freeze its own rule lists but cannot freeze those system switches.
- The freeze stops rule changes inside Hisn; it does not stop a person from
  removing the app, revoking permission or erasing the phone.
- Simulator tests prove the policy and control state, not real Screen Time
  enforcement, app shielding or deletion resistance.

## Next owner actions

1. Apple Developer Program and Family Controls distribution entitlement for the
   app and `HisnActivityMonitor`.
2. A disposable iPhone: verify the disabled site/app controls, Screen Time
   revocation recovery, passcode-vault steps, and the frozen rules across a
   relaunch.
3. A supervised device: install the profile and verify deletion and profile
   removal are blocked.
4. TestFlight/App Store submission; record results in
   [RELEASE_VALIDATION.md](RELEASE_VALIDATION.md) and
   [IOS_STORE.md](IOS_STORE.md).
