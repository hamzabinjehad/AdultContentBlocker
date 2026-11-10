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
| iPhone & iPad | 1.8.0 / 14 | Safari social stylesheet in the text extension | Signed device; DNS extension and install acceptance |
| Android | 1.2.3 / 32 | The unreleased 1.4.0-and-later candidate: native TFLite, signed list updates, receipts and readiness, rule tools, person blur, craft pass | Merge 1.5.4 forward, choose the next number, build and sign, phone run |

The Android tree is **not** the 1.5.3 end project. It is the 1.5.3 line plus
packages 2 and 3, and it does not contain the 1.5.4 reconnect fix. Do not
install it over the phone at 1.5.4. The source-level plan and the feature
inventory are in
[the end-project plan](ANDROID_END_PROJECT_PLAN_2026-11-08.md).

## Verified on this checkout

All commands ran on 10 November 2026 on macOS with Xcode 27.1, Android Studio
JBR 25 and the repository's own runners.

| Check | Result |
| --- | --- |
| `./test.sh python seed` | Pass; seed v5 verifies, 5 shipped artifacts match the signed manifest |
| `./test.sh extension` | Pass; JavaScript suites, package check and evaluation corpus |
| `./test.sh release` | Pass; 33 tests |
| `./test.sh macos` | Pass; 600 tests, 851 localized strings |
| `./test.sh ios` | Pass; 271 tests in the simulator |
| `./test.sh browser` | Pass after the settings harness waits for the painted state and the asynchronous import reply |
| `android/test-core.sh` (JDK 25) | Pass; every core class, including the previously unrun `VisualRuntimeDeadlineTest`, plus the seed and corpus checks |
| `./gradlew assembleDebug assembleDebugAndroidTest lintDebug assembleRelease` | Pass; unsigned release APK |

Not verified: a physical Android phone, an entitled iPhone/iPad, a signed or
notarized Mac build, an installed Mac filter, a Play upload, a Chrome Web Store
submission, or any adult-positive visual benchmark. No accuracy, battery or
network-transition claim is made here.

## Decisions still open

1. **Android number and merge.** Merge the 1.5.4 reconnect fix forward, then
   choose the release number. 1.6.0 / versionCode 43 is the natural next step
   after 1.5.4 / 42; 1.4.0 / 36 matches the existing preview artifacts but is
   older than the phone. Do not ship this tree as 1.4.0.
2. **Extension number.** The media/social pass and the new `offscreen`
   permission need a version bump before packaging; the manifest still says
   1.5.3.
3. **Mac number.** The filter and browser-guard changes are beyond 1.1.18 and
   need a number, a signed build and notarization before they reach anyone.
4. **iPhone/iPad number.** The Safari social stylesheet is beyond 1.8.0 and
   still needs a signed device run.
5. **Android release declaration.** `android/app/build.gradle` remains
   1.2.3 / 32 until the merge and the number are chosen. That is deliberate:
   bumping it first would produce an artifact that is older than the phone or
   claims features the tree does not have.

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
