# Hisn Android 0.5.0 — opt-in visual sampling

Development preview, version code 12, 2026-10-21.

## App goal

Reduce exposure to adult websites, explicit text and flagged visual content while
supporting the restrictions and commitments the owner chooses. The app separates
Protection, Rules and Guide, with English/Arabic purpose, setup, privacy and
coverage explanations. A commitment is separate from active layer health.

## Image and video checks

An independent option, off by default, enables local adult-image classification
for the same explicitly chosen apps used by text inspection. Text classification
does not need to be enabled for visual sampling. Permanent app blocks and daily
schedules remain independent.

On Android 14+, the existing accessibility service requests a screenshot of the
selected active window about every two seconds, with one capture/inference at a
time. It skips excluded apps and pauses while an identified editable or password
field has input focus. This is window capture, not display recording. Protected
windows return an unreadable state; Hisn does not change their protections.

Samples are scaled to at most 384 pixels per edge. The existing pinned NSFWJS
MobileNetV2 model runs through TensorFlow.js CPU in a dedicated WebView. That
WebView serves only three allowlisted APK resources, disallows navigation, blocks
network loads and file/content access, and uses a restrictive content security
policy. Models, code and notices are bundled; no runtime model download or
cloud classifier is used. Build-time SHA-256/size checks verify the engine and
linked legal notice against the existing visual asset inventory.

A Porn/Hentai verdict using the shared detector thresholds returns to Home and
shows a generic visual restriction notice. This restricts the whole app screen;
it does not blur an individual image or pause a particular video. Ordinary
samples remain available. Screenshots, screen text, probabilities, app history
and browsing URLs are not persisted by the visual layer. Model input and output
are ephemeral; only current state and session counters remain in memory.

Window, scroll and content-change events invalidate pending decisions. Selection,
opt-in, current package/window and an eight-second lifetime are checked again
before intervention. Capture and inference have timeouts; failures remain unknown
and trigger bounded retry. Interrupting/unbinding the service destroys its model
and rejects old callbacks. Turning the option off also closes its model.

Visual preferences persist with the existing policy. Other editors preserve them;
commitments reject disabling opted-in checks and removing selected inspection
apps. Android permission revocation, service stopping, data clearing and removal
remain outside this in-app guard. New screenshot consent is explicit in Rules,
and the accessibility disclosure describes the separate option.

## Verification and remaining gates

- Core tests pass, including stale/expired/unselected/window-change verdict gates,
  the existing signed seed, DNS, schedules and adult-text regressions.
- The actual bundled model loaded on Samsung SM-G990B2 / Android 16 and allowed a
  fixed synthetic benign color frame. This used a separate debug application ID,
  `app.hisn.android.visualcheck`; it did not capture a user screen or change the
  existing installation, permissions, VPN, rules or commitments.
- Isolated device instrumentation passed policy persistence and commitment tests,
  including visual default-off, preservation by other editors/reload, rejecting
  disable/removal, and preserving rules after rejected changes.
- Arabic Protection and Rules screens were inspected on the device. The model
  diagnostic exists only in debug builds and requires the shell DUMP permission.
- Final debug/test and unsigned release APK builds passed. Debug lint reported
  zero errors and fourteen warnings after the capture-acceptance follow-up. The release manifest excludes debug fixture
  activities and the DUMP permission. Both isolated test packages were removed
  after verification; the normal APK was built but not installed over the owner app.

Final normal-package debug APK (`app.hisn.android`, 0.5.0 / code 12) SHA-256:
`b330e2fe9b0dbf55cf7183f30b67031faf0006ee8f4c39166673128c58c91d05`.
Unsigned release APK SHA-256:
`6e86ae4cbff368ad6d9056e603bb4a2b26ed7b55b76b5fb548ece8a00acd7f53`.
The debug APK is installable for development; the unsigned release needs the
owner's production signing and release process before distribution.

The later benign-image/performance diagnostic added a debug-only benchmark
activity without changing release behavior. Its current normal-package debug
APK SHA-256 is `76bf005010546d3f6615abfae88cf4328901371e97c74210e2808f9c52c9e5f0`;
the unsigned release hash above is unchanged. See the
[2026-10-22 baseline](ANDROID_VISUAL_BENCHMARK_2026-10-22.md) for measured results
and scope. That build passes lint with zero errors and twenty warnings, including
debug diagnostic strings.

This is an implementation preview. The fixed-fixture live capture checks below pass;
real-app coverage, service-loss recovery, latency, battery and thermal performance
still need acceptance tests. The benign model check is not an
adult-content accuracy benchmark. Small images, mixed screens, rapid scrolling,
short video frames, unreadable surfaces and classification errors leave gaps.
No claim is made about filtering every image/frame or hiding content before display.
Full-traffic VPN, always-on/lockdown, uninstall prevention and a production signing/
distribution process remain separate unfinished work.

The API boundary follows [Android accessibility screenshot APIs](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshotOfWindow(int,java.util.concurrent.Executor,android.accessibilityservice.AccessibilityService.TakeScreenshotCallback))
and the local runtime follows [Android local WebView content guidance](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content).

## Reproduce without replacing the owner's app

```sh
cd android
./gradlew -PhisnIsolatedVisualCheck=true assembleDebug assembleDebugAndroidTest
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL shell am instrument --user 0 -w app.hisn.android.visualcheck.test/app.hisn.android.CoreDeviceTestRunner
adb -s SERIAL shell am start --user 0 -n app.hisn.android.visualcheck/app.hisn.android.VisualModelCheckActivity
```

The activity reports PASS only after actual model inference. It does not enable
accessibility, capture a screen or alter policy. Uninstall only those two test
packages after checking the result. Rebuild without the property for the normal
APK; verify `output-metadata.json` has application ID `app.hisn.android`.

## Live capture acceptance driver

The isolated build is labelled **Hisn Visual Test** so its accessibility service
can be distinguished from the existing owner's installation. The shell-only
`VisualCaptureTestActivity` refuses to configure the normal app. Its configuration
selects exactly `app.hisn.android.visualcheck.test` and enables the visual option
only in that test copy. It does not start a VPN or a real commitment.

```sh
python3 test-live-visual.py --serial SERIAL --configure
```

The owner then enables **Hisn Visual Test** through Android's normal accessibility
UI. The runner does not grant permissions, change the enabled-service list, bypass
restricted settings or disable another accessibility service. With the explicit
test permission in place:

```sh
python3 test-live-visual.py --serial SERIAL --confirm-synthetic-intervention --report /tmp/hisn-visual-acceptance.json
```

This checks actual screenshots and model inference on static and moving shapes,
focused-password pause, protected-window failure, stopping outside the selected
fixture and stopping on opt-out. It also tests one explicitly synthetic positive:
after the real model allows a harmless screenshot, a memory-only debug hook
converts exactly one result for that fixed isolated fixture into a block. The
test checks that the restriction screen appears and that the next ordinary
sample remains available. This is intervention plumbing, never adult-detection
accuracy evidence. The hook expires after two minutes and clears on service
teardown; unknown results are never converted. Release has a separately compiled
no-op hook with regression coverage.

The driver reads only its test diagnostic counters. It never saves screenshots or
reads the owner's policy. Cleanup disables the test visual option and clears any
synthetic lease, including on test failure. Uninstall the two isolated packages
when done. Live pass results must be recorded separately; having this driver or
passing hook tests does not establish a live capture pass.

### Samsung live result — 2026-10-21

After the owner enabled only the separate **Hisn Visual Test** service, the full
driver passed all nine checks. The [machine-readable report](ANDROID_VISUAL_ACCEPTANCE_2026-10-21.json)
records four accepted static-image samples and three accepted moving-shape samples
in their respective baseline stages. Both focused editable and password fixtures
produced no new screenshot requests. Protected windows produced Android error 6
without accepted samples. The synthetic one-shot positive opened the actual
restriction activity; later ordinary samples resumed without another intervention.
Leaving the selected fixture and turning the visual option off stopped requests.
Final diagnostic state was OFF with the option disabled.

Early attempts exposed stale diagnostic and fixture intents when Android reused
the existing activity. Those runs were rejected. Controllers now acknowledge a
unique request nonce, the driver recreates each fixture, and both test activities
handle replacement intents. The complete sequence then passed. Failure cleanup
also stops only the isolated package if its controller cannot acknowledge stop.

The synthetic positive verifies capture-to-model-to-intervention wiring, not an
adult-model true positive. Actual adult accuracy, real video playback, and broad
app coverage remain unmeasured. The test copy and fixture APK were removed after
the run; the owner's existing Hisn installation, rules and permissions were not
changed. No screenshots were saved, no private app content was used as test data,
and the driver enabled no VPN or real commitment.
