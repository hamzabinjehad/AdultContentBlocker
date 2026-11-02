# Publish readiness — 2 November 2026

This is the repository-side answer to “can we publish?”. It records what is
built, tested and packaged, and the owner actions that no repository change can
complete. It does **not** claim that a signed store build, a store listing or a
physical-device acceptance run exists.

## Versions in this tree

| Surface | Version | Build | State |
| --- | --- | --- | --- |
| Android | 0.9.0 | 25 | unsigned Play and direct releases checked; direct build installed on the Samsung |
| Mac | 1.1.7 | 11 | unsigned package smoke passes; system filter still needs the Apple team |
| iPhone & iPad | 1.1.4 | 5 | simulator suite passes; no team, entitlement or device |
| Browser extension | 1.2.2 | — | reproducible store zip passes the package check; no listing |
| Seed | v5, 2026-10-30 | — | signed; no published `lists` branch |

## Verification run on 2 November 2026

| Check | Result |
| --- | --- |
| `./test.sh python seed extension release macos ios` | **PASS** |
| Python suites | **PASS** — blocklist, profile, network publication, extension staging, Safari rules |
| Extension JavaScript suites | **PASS** — 397 web-protection, 46 worker-layout plus the rest |
| `./test.sh browser` | **PASS** — DNR 42/42, live scanner and six block pages in Helium/Chromium |
| `./test.sh macos` | **PASS** — 508 tests, localization 558 strings with Arabic |
| `./test.sh release` | **PASS** — 33 installer/package tests |
| `./test.sh ios` | **PASS** — 183 hosted tests on iPhone 17 / iOS 26.5 |
| `android/test-core.sh` | **PASS** — seed, DNS, rules, managed restrictions, protection lock |
| Android `assembleDebug assembleDebugAndroidTest lintDebug assembleRelease bundleRelease` | **PASS** — 0 lint errors; Play and direct APKs pass `check-release.py` |
| `CoreDeviceTestRunner` on API 36 emulator | **PASS** — including full-freeze assertions |
| Extension package reproducibility | **PASS** — two builds produce the same SHA-256 |

## Artifacts (unsigned unless stated)

- Android Play APK SHA-256
  `b1887534dccc300c692f50838b8733676ef4939f8866179ac6135c001a1b23fe`
- Android direct APK SHA-256
  `5336e5eae797202cc868a04df8ef99959a29cfe71aa4434f4e0dcf748a7344f6`
- Android AAB SHA-256
  `4647d666edd6068262659a2eab2631fc470529aa3262f157004502dc82526ab7`
- Extension zip SHA-256
  `fd254f2eeb5a3a14ee017281675d12a11ca4a6d355ba075c97bf68a54f04157b`
  (5.9 MB, 90 files, reproducible)
- Mac package smoke artifact `Hisn-UNSIGNED-TEST.pkg` SHA-256
  `a081102f8cea0d9d8ec3658758c280d47f3f4132c6048d408d118ce0e6ca0547`
  — structure-only, no signature, not for distribution
- iOS: no archive; the simulator test build is the only artifact

## What is genuinely ready in the repository

- **Android:** protection lock freezes every saved rule; direct removal guard
  arms from the lock; device-owner uninstall block; managed lockdown; DNS,
  app, text, visual and schedule layers; setup and rules localized in English
  and Arabic. Direct and Play releases are separately enforced.
- **Mac:** content filter, browser guard, lock, guardian split scripts, root
  daemon, hardening profile, partner key, protection-authority evidence and
  lock-gated account controls. The Mac app does not yet have the Apple team
  required to activate its system filter.
- **iOS/iPadOS:** Screen Time filter, six Safari blockers, passcode vault,
  supervised profile and privacy manifests; simulator-tested only.
- **Extension:** blocking, SafeSearch, text and optional local visual checks,
  short-video rules, managed-policy support and a store-ready package.
- **Privacy/support:** [PRIVACY.md](../PRIVACY.md) and
  [SUPPORT.md](../SUPPORT.md) are written; list sources and licences are
  recorded in-app and in the notices pipeline.
- **Release engineering:** version matrix, store-release verifier, release
  scripts, deterministic extension packaging and per-platform release records.

## What still needs the owner

### Android

1. Create the production keystore offline with two backups.
2. Build with the four `HISN_ANDROID_*` variables, then run
   `android/check-release.py`, `apksigner verify --print-certs` and publish the
   APK hash and certificate fingerprint.
3. Provision a disposable, factory-reset, account-free phone as device owner and
   record the real refusals: uninstall, Safe Mode, app controls, factory reset,
   VPN/Private DNS, new user, default apps, strict install, release.
4. Run the layer-loss, VPN handover, accessibility-revoke and reboot tests.
5. Create the Play listing for the Play build. Never upload the direct build.

### Apple / Mac / iPhone / iPad

1. Join the Apple Developer Program.
2. Request the Family Controls distribution entitlement for iOS and the
   activity monitor.
3. Obtain a Developer ID Application and Installer certificate; sign and
   notarize the Mac `.pkg`; run `macos/release/release.sh --build` and
   `--notarize`.
4. Run the standard-account guardian split, `launchctl bootout`, reboot and
   admin-recovery tests on a physical Mac.
5. Create a disposable iPhone/iPad; run the vault, Screen Time, six Safari
   blockers, supervised profile and removal tests.
6. Build/upload TestFlight and complete App Review.

### Chrome Web Store

1. Open the developer account and upload the extension zip unlisted.
2. Read the assigned store ID and wire it into
   `NativeMessagingInstaller.swift`, `profile/make_profile.py` and the Windows
   policy.
3. Complete the listing/privacy answers from
   [EXTENSION_STORE.md](EXTENSION_STORE.md).
4. Test managed force-install on a standard user.

### Lists

1. Configure the `list-signing` environment with the signing key and a
   `main`-only policy.
2. Publish the `lists` branch and verify the manifest is HTTP 200.
3. Prove a client installs a newer signed generation and rejects rollback.

## The honest end-goal status

The code now covers every layer the project set out to build: domain, text,
visual, short-video, search, app scheduling, commitments/locks, guardian
authority, removal resistance report and per-platform health. What is missing is
**evidence and distribution**, not a missing product layer:

- No signed Android APK with a production key and no physical device-owner run.
- No signed/notarized Mac package, no active Mac system filter without the Apple
  team, and no standard-account Mac test.
- No Apple team, entitlement, TestFlight or physical iPhone/iPad run.
- No Chrome Web Store listing or real store ID.
- No published `lists` branch; every client is on seed v5.
- No measured adult-recall/visual benchmark with a licensed corpus.

Until those are done, the correct label is **release candidate**, not 1.0.

## Suggested publication order

1. **Android direct build** — sign, install on a disposable device, run the
   device-owner matrix. This is the strongest free removal lock.
2. **Mac** — Developer ID, notarized package, standard-account test.
3. **Extension** — unlisted Chrome Web Store beta, wire the real ID.
4. **iOS** — entitlement, TestFlight, disposable device.
5. **Lists** — publish signed daily generations.
