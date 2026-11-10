# Release state, 10 November 2026

This page records what the checkout **declares**, what it **contains**, and what
is still a decision. It exists because the working tree is a roll-up of four
unreleased passes, not a tagged release. `versions.json` and
[the generated versions page](VERSIONS.md) remain the pinned declaration; this
page explains the gap between that declaration and the code.

## Declared versus present

| Surface | Declared | In this tree | Release blocker |
| --- | --- | --- | --- |
| Browser extension | 1.5.3 | Media checks on by default, whole-post hiding on X and the main social feeds, a new `offscreen` permission | Version bump for the new permission, packaged zip, store review |
| Mac | 1.1.18 / 30 | Allocation-free filter path, a missed heartbeat no longer closes a browser | Signed, notarized build; installed-filter acceptance |
| iPhone & iPad | 1.8.0 / 14 | Safari social stylesheet in the text extension; encrypted-DNS provider names and CNAME/DNAME/SVCB alias checks in the DNS filter | Signed device; DNS extension and install acceptance |
| Android | 1.6.0 / 43 | The 1.5.3 feature set, packages 2 and 3, native TFLite, signed list updates, receipts and readiness, rule tools, person blur, and the ported 1.5.4 reconnect lifecycle | Phone run; signed 1.6.0 artifact; the post-reboot dashboard state is unverified |

The Android tree is now **1.6.0 / 43**, a development candidate: the 1.5.3
feature set plus packages 2 and 3, with the 1.5.4 reconnect lifecycle ported
rather than the whole 1.5.4 branch merged. It has no signed artifact and no
phone run, and the 1.5.4 record's post-reboot result is still the limit. Do not
install it over the phone at 1.5.4 until a signed 1.6.0 build passes the phone
acceptance. The source-level plan and the feature inventory are in
[the end-project plan](ANDROID_END_PROJECT_PLAN_2026-11-08.md) and
[the 1.6.0 note](ANDROID_1.6.0.md).

## Verified on this checkout

All commands ran on 10 November 2026 on macOS with Xcode 27.1, Android Studio
JBR 25 and the repository's own runners.

| Check | Result |
| --- | --- |
| `./test.sh python seed` | Pass; seed v5 verifies, 5 shipped artifacts match the signed manifest |
| `./test.sh extension` | Pass; JavaScript suites, package check and evaluation corpus |
| `./test.sh release` | Pass; 33 tests |
| `./test.sh macos` | Pass; 600 tests, 851 localized strings |
| `./test.sh ios` | Pass; 277 tests in the simulator |
| `./test.sh browser` | Pass after the settings harness waits for the painted state and the asynchronous import reply |
| `android/test-core.sh` (JDK 25) | Pass; every core class, including the previously unrun `VisualRuntimeDeadlineTest`, plus the seed and corpus checks |
| `./gradlew assembleDebug assembleDebugAndroidTest lintDebug assembleRelease` at 1.6.0/43 | Pass; unsigned release APK (versionCode 43 / versionName 1.6.0) |
| Clean extraction of HEAD | `sh android/test-core.sh`, `./test.sh python seed` and `:app:assembleDebug` pass with no untracked file |

Not verified: a physical Android phone, an entitled iPhone/iPad, a signed or
notarized Mac build, an installed Mac filter, a Play upload, a Chrome Web Store
submission, or any adult-positive visual benchmark. No accuracy, battery or
network-transition claim is made here.

## Decisions still open

1. **Android phone acceptance.** Build a signed 1.6.0 / 43 direct APK with the
   owner's key, run `check-release.py`, and install it on the 1.5.4 phone only
   after recording the certificate and hashes. Confirm whether the rebooted
   dashboard reads connected; if it still does not, the next pass needs a clean
   re-enable or a different binding fix.
2. **Extension number.** The media/social pass and the new `offscreen`
   permission need a version bump before packaging; the manifest still says
   1.5.3.
3. **Mac number.** The filter and browser-guard changes are beyond 1.1.18 and
   need a number, a signed build and notarization before they reach anyone.
4. **iPhone/iPad number.** The Safari social stylesheet is beyond 1.8.0 and
   still needs a signed device run.

## What this commit pass did

- Committed the previously untracked Android candidate, including the 9.8 MB
  TFLite model and its tests, so a fresh clone and CI can see the same code.
- Made `android/test-core.sh` run `VisualRuntimeDeadlineTest` and fail when a
  `*Test.java` file is not reachable from the script.
- Fixed the two harness races that made `./test.sh browser` fail on a loaded
  machine: the settings page now waits for the painted state and for the
  asynchronous import message, and the exited-browser runner test no longer
  times out after one second.
- Recorded the Mac filter/guard and extension/Safari changes in their own
  commits with the measurements and limits attached.
- Corrected the bundled-core count in the README (236,586, not 239,885) and
  the extension's visual column in the generated coverage page.
- Added `SECURITY.md`, which the README already linked, and a documentation
  test that fails when a document is missing from this index or a top-level
  local link does not resolve.
- Ported the 1.5.4 reconnect lifecycle onto the candidate, bumped Android to
  **1.6.0 / 43**, and recorded the port and its limits in
  [the 1.6.0 note](ANDROID_1.6.0.md). No signed artifact or phone run was added.
