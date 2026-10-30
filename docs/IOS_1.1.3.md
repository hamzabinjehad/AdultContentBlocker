# Hisn iPhone and iPad 1.1.3 — App Store privacy readiness and list age

Development candidate, 30 October 2026. This is a simulator-tested build, not an
App Store or TestFlight release. There is still no Apple Developer team, no
Family Controls distribution entitlement and no physical-device acceptance.

## What changed

### A privacy manifest for every bundle

`ios/Hisn/PrivacyInfo.xcprivacy` is now a resource of the app, all four Safari
blockers, `HisnActivityMonitor` and `HisnText`:

- `NSPrivacyTracking` is false, with no tracking domains.
- `NSPrivacyCollectedDataTypes` is empty: Hisn collects no browsing history,
  matched text, screenshots or identifiers.
- `NSPrivacyAccessedAPIType` declares the required reasons Hisn actually uses:
  - `NSPrivacyAccessedAPICategoryUserDefaults` with `CA92.1` (app-only defaults)
    and `1C8F.1` (app-group defaults shared with Hisn's own extensions);
  - `NSPrivacyAccessedAPICategorySystemBootTime` with `35F9.1`, because
    `TrustedTime` uses the monotonic boot clock to keep commitment and budget
    dates from being moved by a wall-clock change.

`Info.plist` now declares `ITSAppUsesNonExemptEncryption` false: the app uses
Apple's HTTPS stack and no custom or non-exempt cryptography of its own.

### The signed Safari list says how old it is

`prepare_rules.py` copies the signed manifest's `built_at` into
`rules-metadata.json`. `SeedFreshness` parses the ISO offset timestamp (or a
plain date), clamps future dates to zero and computes whole days. The list
section shows **Built** and, after 90 days, says that a newer Hisn build is the
only list update for Safari. The warning is informational; the bundled list
still enforces.

There is still no runtime list download on iOS. A Safari rule change ships in a
new signed app build; the app says so and never claims a fresher list than the
metadata.

### Optional supervised-profile hardening

`profile/make_ios_profile.py` now offers six explicit, supervised-only keys:
account changes, passcode changes, app installation, App Clips, enabling
restrictions and automatic date/time. Each is off unless its flag is passed,
because each can remove a recovery path. The existing keys (`allowAppRemoval`,
device erase, profile installation, VPN creation, Private Relay, marketplace and
web-distribution apps, Safari private browsing, explicit content) are unchanged.
`profile/test_make_ios_profile.py` covers both the default absence and the
explicit values. The guide documents every flag and its tradeoff.

### Settings links and version

Settings now shows the app version and build, plus links to the repository
[privacy policy](../../PRIVACY.md) and [support page](../../SUPPORT.md). Those
same answers are the App Store privacy label: **no data collected, no tracking**.

Version 1.1.3, build 4. The app, four blockers and `HisnActivityMonitor` share
the version set.

## Verification

On 30 October 2026:

- `./test.sh ios`: 32 Python rule/extension/privacy/localization tests plus the
  hosted app suite on the iPhone 17 / iOS 26.5 simulator, **182 tests, TEST
  SUCCEEDED**.
- New Swift test `testSeedFreshnessReadsTheSignedBuildDateAndWarnsAfterNinetyDays`
  covers the timestamp forms, whole-day age, future clamping and the 90-day
  boundary.
- `ios/test_rules.py` covers the signed manifest, and the metadata now carries
  the signed `built_at`.
- `ios/test_localization.py` passes with the new English/Arabic keys, and
  `ios/test_privacy.py` checks the manifest, export declaration, reasons and
  membership in all seven bundles.
- The built simulator app and all six extension bundles contain
  `PrivacyInfo.xcprivacy`; the app's list section carries the signed build date.

**No physical-device result is claimed.** The privacy manifest and export
declaration are review inputs, not a guarantee of App Store acceptance. The App
Store privacy answers, entitlement request, TestFlight build and device
acceptance remain owner/device work.

## Limits

- The 90-day threshold is a product choice, not a freshness guarantee. A build
  can be cut from an old seed; the warning only reports the signed date.
- The manifest declares API use; it does not change what the app does. A new
  framework or API that needs a reason must update the manifest and its test.
- The policy/support links point at the repository. A store listing should use
  a hosted page with the same text.
- No Apple team, Family Controls entitlement, TestFlight build, supervised
  device or physical iPhone test exists yet.
- The Safari blockers still cover the core tier (up to 4 × 40,000 rules), not
  the full list. The URL-filter option remains deferred.

## App Store readiness checklist still open

- [ ] Apple Developer Program and Family Controls distribution entitlement for
      the app and `HisnActivityMonitor`.
- [ ] Signing team set in `ios/generate_xcodeproj.py`; archive and TestFlight.
- [ ] Privacy policy/support hosted at public URLs and entered in App Store
      Connect; confirm the privacy label says no data collected.
- [ ] TestFlight review notes disclose every lock and its exit.
- [ ] Physical device: vault seal/reveal, Screen Time filter readback, app
      shields and budget, Safari blocker activation, supervised profile,
      revocation and reboot behavior.
- [ ] Decide the default lock level (vault, guardian, supervised) and the URL
      filter (deferred).
