# Changelog

Newest first. Versions are per platform; the pinned set is checked in
[versions.json](versions.json) and rendered in [docs/VERSIONS.md](docs/VERSIONS.md).
Per-release evidence lives in `docs/` and is linked from each entry. A version
bump without an entry here is an unfinished release.

## Android 0.8.1 — 2026-10-30

### Added

- **The 1.0 list-update decision is recorded and visible.** Android receives
  list changes only with a new signed APK; there is no runtime list download.
  `SeedFreshness` reads the signed seed's build date and warns after 90 days
  with the age and the update path. A plain date or ISO offset timestamp is
  accepted, future times clamp to zero, and an unparsable time shows no warning
  rather than a wrong one.
- **The watchdog is visible and can ask for notification permission.**
  Protection shows the last check time, a lost layer, or notifications being
  off. Enabling **Keep Hisn installed** on Android 13+ requests
  `POST_NOTIFICATIONS`; a denial is shown, not treated as protection. A healthy
  check no longer erases the last-run time.
- The in-app Guide now covers the optional managed lockdown, the 15-minute
  check, the lack of always-on/lockdown VPN, and the APK-only list path.
- **The Android 1.0 network decision is recorded.** The DNS-only VPN stays
  owner-started/start-on-boot, not always-on or lockdown. `setGlobalPrivateDns`
  and a full tunnel are deferred; a stopped VPN is made visible by the watchdog
  and cannot be replaced by an ordinary user when managed lockdown holds
  `no_config_vpn`.
- The device runner compiles read-only watchdog-expectation and seed-age checks.
- Version 0.8.1, versionCode 21. See
  [the 0.8.1 record](docs/ANDROID_0.8.1.md).

### Verification

- `android/test-core.sh` passes with `SeedFreshnessTest`, `LayerWatchTest`, the
  API-aware `ManagedRestrictionsTest` and English/Arabic parity (280 strings).
- `assembleDebug assembleDebugAndroidTest assembleRelease lintDebug` passes; so does
  the direct flavor (`-PhisnDistribution=direct`).
- `check-release.py` passes on the unsigned release APK: 0.8.1 (21), no
  debuggable/test-only flags, no DUMP permission, no debug components.
  SHA-256 `1e00bf2eb870f693a8e49d097835b516ca20780cea343436b1023624452813c4`.
- `check-release.py --expect-distribution play` or `direct` makes the Play/direct
  split enforceable; both a Play and a direct artifact check pass, and the
  direct artifact is refused as Play. Its SHA-256 is
  `16878b73c66d0501a5735980a1dad96b58b9a2bef71dfb832fc2a172c41485b0`.
- `test_check_release.py` covers the suffix rule in `test-core.sh`.
- `bundleRelease` passes (unsigned AAB SHA-256
  `b45ab915c176910d4e6b49f91f94a466a7c524ca163a7ed5cc4acb404369bfcc`).
- No device run is claimed.

## Android 0.8.0 — 2026-10-30

### Added

- **Private DNS in managed lockdown.** On Android 10+ (API 29), the device-owner
  lockdown now applies and reads back `disallow_config_private_dns` as a fifth
  restriction. Android 8/9 expect four keys; the policy takes the API level, and
  the live checker builds the expected set from the device SDK. One missing or
  extra key is out of sync, never a protected state.
- **A background layer watchdog.** A persistent 15-minute `JobScheduler` job
  compares the layers the owner left armed (the direct build's accessibility
  guard while **Keep Hisn installed** is on, and DNS while left on) with the live
  state. A lost layer gets one local notification, repeats at most every twelve
  hours and clears on recovery; it never restarts a service, reads screen text
  or contacts a server. Scheduling is refreshed on app open, DNS start/stop,
  boot and keep-switch changes.
- Version 0.8.0, versionCode 20. See
  [the 0.8.0 record](docs/ANDROID_0.8.0.md).

### Verification

- `android/test-core.sh` passes with `LayerWatchTest`, the API-aware
  `ManagedRestrictionsTest` and English/Arabic string parity (275 strings).
- `assembleDebug assembleDebugAndroidTest assembleRelease lintDebug` passes.
- No device-owner or real layer-loss result is claimed; the disposable-device
  checklist is in the 0.8.0 record.

## Unreleased — `release/1.0`, 2026-10-29

### Added

- **Android 0.7.0 (versionCode 19): optional managed lockdown.** On a
  device-owner phone, a second explicit opt-in applies and reads back four
  Android restrictions: Safe Mode, app uninstall/disable/clear-data/force-stop
  for every app, the Settings factory reset and VPN configuration changes. Both
  managed choices are saved in one write; release clears restrictions in reverse
  order, then the uninstall block, then device ownership. Status shows ON, Off,
  idle or out of sync from Android's own readback. The broad app-controls
  restriction is disclosed before it is applied. No device acceptance is
  claimed. See [the 0.7.0 record](docs/ANDROID_0.7.0.md).
- **One machine-checked version set.** `versions.json` pins the extension, Mac,
  iOS/iPad and Android versions; `script/versions.py` checks each platform
  manifest and regenerates [docs/VERSIONS.md](docs/VERSIONS.md). A conflict in
  an Xcode target or a mismatch with the manifest fails `./test.sh python`.
- **Per-surface coverage page.** `script/coverage.py` derives
  [docs/COVERAGE.md](docs/COVERAGE.md) from the signed seed and the static DNR
  ruleset: 159,768 core domains, 5,248,731 full-list domains, 5,831 terms, 320
  static and 116 keyword DNR rules. It states which client carries which tier.
- **Privacy policy and support page.** [PRIVACY.md](PRIVACY.md) records the
  on-device matching, the absence of accounts/telemetry, the only routine
  network requests and every permission. [SUPPORT.md](SUPPORT.md) has setup
  links, troubleshooting, safe reporting and the consent/emergency rules.
- **Android live managed checker.** `test-live-managed-status.py
  --expect-lockdown` reads the four restrictions back without changing policy;
  the debug exporter now includes the held and desired sets.

### Changed

- The full list count in the docs is corrected to the signed artifact
  (5,248,731 domains, not 982k). The core tier is 159,768 and the keyword layer
  is 5,831 terms; see [COVERAGE.md](docs/COVERAGE.md).
- The iOS hosted suite now runs to completion on the iOS 26.5 simulator
  (181 tests, `** TEST SUCCEEDED **`), closing the M0 runner gap recorded in the
  1.0 plan. The suite is slow because the render tests are real UI passes; that
  is time, not a hang.
- A single bare `./test.sh` run passes end to end, and `./test.sh browser`
  passes every harness in Helium 0.20.2.1 (Chromium 156), including the DNR and
  SafeSearch rules and the live-page evasion checks. Android
  `assembleDebug assembleDebugAndroidTest lintDebug` passes with the final
  strings.
- The macOS suite is green at 495 tests plus the Arabic-string check; Python,
  seed, extension, release and Android core suites are green on the same tree.

### Fixed

- Android managed-mode status can no longer show PROTECTED when a requested
  restriction is missing, or READY while an old restriction remains. The
  combined state is OUT_OF_SYNC and the periodic repair runs.
- Managed-mode release now clears restrictions before the uninstall block, so a
  failed ownership release cannot leave the phone hardened but unmanaged.

## 2026-10-28 — Extension 1.2.0

- The block page explains a signed-list block (`reason=domain`) in English and
  Arabic, instead of falling through to the generic message.
- A native-messaging reply that omits `mode` can no longer reset Strict to
  Standard; `pollNative` leaves absent fields alone.
- The status shows the installed list version/date, warns after 14 days, and
  names the bundled-only state. Settings has **Update block list**, serialised
  against the automatic alarm; a failed update keeps the current generation.
- Facebook `share/reel/…` links are covered by the short-video rules and the
  in-page matcher.
- Version 1.2.0. Tests: extension unit suites, block page/settings/popup and DNR
  browser harnesses, package and evaluation corpus. See
  [EXTENSION_1.2.0.md](docs/EXTENSION_1.2.0.md).

## 2026-10-28 — Android 0.6.0

- Opt-in device-owner uninstall protection, with platform readback, an in-app
  status/release path and boot/foreground reconciliation after a commitment
  expires.
- The uninstall-screen guard and boot shield for the direct build; the pause
  tally and short-video blocking.
- Device evidence on the Samsung SM-G990B2 is recorded as far as it goes; the
  phone was not a device owner, so no blocked-uninstall result is claimed. See
  [ANDROID_0.6.0.md](docs/ANDROID_0.6.0.md).

## 2026-10-26 — iOS/iPadOS 1.1.2

- A commitment that holds: trusted Apple server time, a restored filter,
  layer-loss alerts and the passcode vault for a date-limited exit without a
  second person.
- Honest Screen Time status, readable colours, Dynamic Type and iPad layout;
  **My blocked sites** through the Screen Time adult-content filter.
- HisnActivityMonitor app shields, usage budgets and the selected-app picker.
- Development build; no Apple team, Family Controls entitlement or device
  acceptance is claimed. See [UPDATE_1_1_2.md](docs/UPDATE_1_1_2.md) and the
  [iOS plan](docs/IOS_IMPROVEMENT_PLAN.md).

## 2026-10-26 — macOS 1.1.4 (build 8)

- Ships the Persian/Urdu text folds to installed Macs, so the Mac filter and the
  extension make the same decision on the shared normalisation cases.
- Reads the lock inside `ScreenGrayscale.set` rather than as a default argument,
  fixing the grayscale state read.
- Unsigned, unnotarized development build; the guardrail and filter tests pass
  (495 tests, Arabic-string check). See [ROAD_TO_1.0.md](docs/ROAD_TO_1.0.md).

## 2026-10-01 — signed seed bundle v4

- Core tier: 159,768 high-confidence domains; full build: 5,248,731 domains;
  keyword layer: 5,831 terms (4,100 Arabic, 1,447 Franco-Arabic, 145
  English, 100 German and 39 French/Spanish/Italian/Portuguese) plus 116 host
  terms.
- Ed25519-signed manifest, rollback floor and a fail-closed client. No `lists`
  branch has been published yet, so every client is still on this seed.

Earlier per-version records (Android 0.3.1 through 0.5.5, extension 1.1.8
through 1.1.12, and the 1.1.1/1.1.2 updates) are in the [docs index](docs/README.md).
