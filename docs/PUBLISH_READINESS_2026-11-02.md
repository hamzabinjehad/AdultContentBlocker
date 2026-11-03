# Publish readiness — 2–3 November 2026

This is the repository-side answer to “can we publish?”. It records what is
built, tested and packaged, and the owner actions that no repository change can
complete. It does **not** claim that a signed store build, a store listing or a
physical-device acceptance run exists.

## Versions in this tree

| Surface | Version | Build | State |
| --- | --- | --- | --- |
| Android | 1.0.0 | 26 | signed direct APK published on GitHub; Play AAB built but not uploaded; physical device proof still required |
| Mac | 1.1.9 | 13 | unsigned package smoke passes; system filter still needs the Apple team |
| iPhone & iPad | 1.4.0 | 8 | simulator suite passes; no team, entitlement or device |
| Browser extension | 1.5.0 | — | reproducible store zip passes the package check; no listing |
| Seed / lists | v5 seed (2026-10-30); v6 lists (2026-11-03) | — | signed; `lists` branch published and HTTP 200 |

## Verification run on 2–3 November 2026

| Check | Result |
| --- | --- |
| `./test.sh python seed extension release macos ios` | **PASS** |
| Python suites | **PASS** — blocklist, profile, network publication, extension staging, Safari rules |
| Extension JavaScript suites | **PASS** — 68 language checks, 94 feed checks, 397 web-protection, 46 worker-layout plus the rest |
| `./test.sh browser` | **PASS** — DNR 42/42, live scanner, six block pages, popup/settings redesign in English and Arabic, and the four-image real-engine visual smoke in Helium/Chromium |
| `./test.sh macos` | **PASS** — 522 tests, localization 597 strings with Arabic (includes the 1.1.9 guard panel and pause) |
| `./test.sh release` | **PASS** — 33 installer/package tests |
| `./test.sh ios` | **PASS** — 213 tests on the iPhone simulator |
| `android/test-core.sh` | **PASS** — seed, DNS, rules, managed restrictions, protection lock, `AppTimeLimitTest` |
| Android strings/UI | **PASS** — 460 strings, 15 plurals, 485 references in English/Arabic; time-limit card read back on the API 36 emulator |
| Android `assembleDebug assembleDebugAndroidTest lintDebug assembleRelease bundleRelease` | **PASS** — 0 lint errors; signed Play and direct APKs pass `check-release.py` and `apksigner` |
| Android live limit | **PASS** — a 5-minute limit on the test-only fixture app was reached, closed and explained on the API 36 emulator |
| `CoreDeviceTestRunner` on API 36 emulator | **PASS** — including full-freeze assertions |
| Extension package reproducibility | **PASS** — two builds produce the same SHA-256 |
| Signed list v6 publication | **PASS** — manifest/signature match the signed candidate over HTTPS, HTTP/2 200; publish guard and 43 real-artifact checks pass |

## Artifacts (Mac and extension unsigned; Android signed)

- Android Play APK SHA-256
  `bded282fd19fb524d77daf42d0a60a0660bd6db5c06acbba316e168743fe5517`
- Android direct APK SHA-256
  `c42591ec8223e3d24d8159f0536d3212ee0f351b5c3edc1fc4d03c0d30afaac0`
- Android AAB SHA-256
  `0794e0d509ca939cc012e2a01e82353211611c0c54174cefe7e4e40ec07cfad7`
- Extension zip SHA-256
  `96ca854f52b3a4cb406bcc735013263a6adea56aedda93c843bf95733b1d85a7`
  (5.9 MB, 92 files, reproducible)
- Mac package smoke artifact `Hisn-UNSIGNED-TEST.pkg` (1.1.9, build 13) SHA-256
  `16280ef15dc8f4272aa48cb26f64ffb28e34d68dedbec3bb99d6df8f1354b61f`
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

1. **Done 2026-11-03:** a release keystore exists in the gitignored
   `keys/android/` with its password. Back both up offline in two separate
   places; losing them means existing installs can never be updated.
2. **Done 2026-11-03:** signed Play and direct APKs plus the AAB were built and
   checked; hashes and the certificate fingerprint are in
   [ANDROID_1.0.md](ANDROID_1.0.md). The 0.9.0 preview was debug-signed, so
   updating it requires an uninstall.
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
   `main`-only policy so the daily workflow can publish.
2. **Done 2026-11-03:** the signed `lists` branch is live at v6 (5,274,046
   domains) and the manifest is HTTP 200. It was published manually with the
   matching local key; see
   [LIST_PUBLICATION_2026-11-03.md](LIST_PUBLICATION_2026-11-03.md).
3. Prove a client installs a newer signed generation and rejects rollback.

## The honest end-goal status

The code now covers every layer the project set out to build: domain, text,
visual, short-video, search, app scheduling, commitments/locks, guardian
authority, removal resistance report and per-platform health. What is missing is
**evidence and distribution**, not a missing product layer:

- Android 1.0.0 APKs are signed with a newly generated local release key; key
  backup custody, Play submission and a physical device-owner run remain.
- No signed/notarized Mac package, no active Mac system filter without the Apple
  team, and no standard-account Mac test.
- No Apple team, entitlement, TestFlight or physical iPhone/iPad run.
- No Chrome Web Store listing or real store ID.
- No **scheduled** `lists` publication (the `list-signing` environment is not
  configured) and no installed client proven to have installed v6.
- No measured adult-recall/visual benchmark with a licensed corpus.

Until those are done, the correct label is **release candidate**, not 1.0.

## Suggested publication order

1. **Android direct build** — the signed 1.0.0 APK is ready; install it on a
   disposable device and run the device-owner matrix. This is the strongest
   free removal lock.
2. **Mac** — Developer ID, notarized package, standard-account test.
3. **Extension** — unlisted Chrome Web Store beta, wire the real ID.
4. **iOS** — entitlement, TestFlight, disposable device.
5. **Lists** — configure the scheduled signing environment; v6 is already
   published, and a client install/rollback test remains.
