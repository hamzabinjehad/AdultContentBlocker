# Android protection pass — 2026-11-06

Android goes from 1.2.0 (versionCode 29) to **1.2.1 (versionCode 30)** and then
to **1.2.2 (versionCode 31)**, which adds the last repair below on top of the
DNS hostname-keyword pass recorded in its own entry. This pass tested the
running protection on the owner's phone, including the opt-in photo/video layer,
and fixed three measured gaps. It adds no permission, rule or claim; the image
classifier, its thresholds and the DNS policy are unchanged by it.

Design record for the interface pass in the same day:
[Android UI pass](ANDROID_UI_2026-11-06.md). The captured end state and the
fixture runner's JSON are in
[design/android-protection-2026-11-06](../design/android-protection-2026-11-06/).

## What was tested

Device: owner's Samsung SM-G990B2, Android 16, 1080 × 2340, 420 dpi override,
release-signed direct build (1.2.0 during the first runs, 1.2.1 after the fixes).

### Photo and video sampling

The isolated build (`-PhisnIsolatedVisualCheck=true`, package
`app.hisn.android.visualcheck`) and its fixture app, with only that fixture
selected, were installed next to the owner's app, so no owner rule changed.

`test-live-visual.py` — **10 checks, all pass** on 1.2.1:

| Check | Result |
| --- | --- |
| Real screenshot reaches the real local model (benign fixture) | 5 samples, 0 interventions |
| Moving synthetic frames keep being sampled | 5 samples in 8 s, 0 interventions |
| Focused editable field pauses capture | state `INPUT`, request count unchanged |
| Focused password pauses capture | state `INPUT`, request count unchanged |
| A secure window stays unreadable | 1 capture failure, Android error 6, no sample |
| A detected app is sent Home with the restriction screen | intervention after a real capture and real verdict |
| A detected app stays held when reopened | not resumed; sent Home |
| The hold ends and ordinary sampling recovers | samples resume, no new intervention |
| Leaving the selected app stops capture | 0 new requests after Home |
| Opting out stops capture and frees the model | state `OFF`, 0 new requests |

The synthetic intervention is armed only after a real benign frame has been
captured and classified; the report marks it `"accuracy_evidence": false`. No
adult-positive image or video was used.

Measured cadence with the real screenshot path and the real model on this phone:

| Scene | Samples | Period |
| --- | --- | --- |
| Still fixture, 30 s | 8 | ~3.8 s (still-screen back-off) |
| Moving fixture, 30 s | 17 | ~1.8 s (one frame at a time, ~2 s inference) |

This matches the documented 2 s base interval, the 1 s fast interval while
content changes, and the still-screen back-off to 5 s. It is sampling, not
pre-display checking: a video keeps playing between samples.

### DNS

The tunnel was read through the app's own diagnostics, no new instrumentation:
loaded 236,581 domains; blocked 2; forwarded 85 → 91 in the next 60 s; errors
54 → 55 in the same window; 0 unsupported and 0 malformed packets; 1 refused
connection on port 853; 18 irrelevant packets ignored; recoveries 2 → 3. The
session state moved between running and degraded while queries kept flowing.

### Text rules

Not run live: `test-live-guard.py` drives `GuardTestSetupActivity`, which exists
only in a debug build of the owner package. Replacing the release install with a
debug build would change the signing key and delete the owner's saved rules, so
the fixture run was not attempted. The word rules and thresholds are covered by
`test-core.sh` (`WordMatcher`, `LabelScan`, `GuardWindows`, `AdultTextPolicy`
against the real seed: 5,911 submitted / 5,909 canonical rules).

### Accessibility

One in-place update dropped the guard's accessibility service from Android's
enabled list; the app showed its waiting screen and the service had to be turned
on again in Settings → Accessibility → Installed apps. A later in-place update
kept it bound.

## What was found and fixed

### 1. A granted service could read as "permission required"

After the service was re-enabled in Settings, the app still showed **Permission
required** while Settings showed the service on and `dumpsys accessibility`
listed it as enabled and bound.

Cause: `Permissions.accessibility()` and `MainActivity.guardPermissionEnabled()`
trusted only `AccessibilityManager.getEnabledAccessibilityServiceList`, which is
a cached list. A process that was alive before the service was re-enabled kept
the stale copy, so the dashboard claimed a missing permission for a running
guard — the worst kind of wrong state for a blocker, because it invites the
person to "fix" a working layer.

Fix: `app.hisn.core.AccessibilityGrant` parses Android's own
`enabled_accessibility_services` value (platform state that Settings writes,
never a switch Hisn saves) and is used as the second source, gated on
`accessibility_enabled = 1`. Both entry formats Android writes are accepted
(`pkg/.Class`, `pkg/pkg.Class`). With the service bound but the cache stale, the
app now reports **reconnect required** instead of **permission required**, and
`TextGuardService.connected` remains the second, honest signal.

### 2. A classifier failure blinded the image layer for 30 s

`VisualGuard.fail()` freed the model and refused further sampling for a flat
30 s. One killed WebView renderer, a 15 s JavaScript timeout or a frame dropped
under memory pressure therefore cost 30 s without inspection plus about 2.2 s to
reload the model.

Fix: `app.hisn.core.VisualRetry` — the first retry comes after **5 s**, each
further failure doubles it, the wait is capped at the previous **30 s**, and any
finished sample clears the escalation. The model is still freed on failure, so a
broken engine cannot loop, but a transient one costs seconds instead of half a
minute.

### 3. A bound service could stay unarmed (shipped in 1.2.2)

While probing the grant check, the accessibility service was seen listed as
enabled and bound (a live `ServiceRecord` in the app's own process) while the
app reported **reconnect required** and the guard did not act on a Settings
window it normally closes. Android had rebound the service for a new process
without the fresh `onServiceConnected` setup that arms the guard, so
`connected` stayed false and `excluded` stayed null: the layer was silent while
the system believed it was running.

Fix: `TextGuardService.startGuard()` holds the setup, `onServiceConnected` calls
it, and the first accessibility event calls it too when it has not run yet. The
setup is idempotent, the callbacks are on the same main thread, and `started`
resets on unbind and destroy. An event now proves the instance is alive instead
of trusting the bind callback alone.

### 4. The live visual driver was stale

Its *"one-shot expires; ordinary capture recovers"* check sampled 8 s after an
intervention, but a detection holds the app for `TextGuardService.BLOCK_HOLD_MILLIS`
(60 s). The driver now asserts the hold (a reopened app is sent Home), waits it
out, and asserts that ordinary sampling recovers. The adb timeout went 25 → 45 s
for slow device responses. The suite passed for the first time in this pass
without weakening a check.

## Verification

- `android/test-core.sh` — all suites pass, including the new `AccessibilityGrant`
  and `VisualRetry` tests, plus strings (553 strings, 16 plurals, English and
  Arabic) and notices.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`,
  `:app:assembleRelease -PhisnDistribution=direct` and `:app:lintDebug`
  (0 errors) pass.
- 1.2.1 (versionCode 30) installed in place over 1.2.0 with the owner's key on
  the owner's phone; saved rules and the running protection lock survived, DNS
  filtering was started again from the app's consent sheet, and the guard
  reconnected.
- 1.2.2 (versionCode 31) was built and installed the same way afterwards,
  carrying this pass's last repair plus the DNS hostname-keyword policy recorded
  in its own changelog entry. The phone ended the pass reading **Protection is
  on · Experimental coverage** with Network, Apps and Images all working, DNS
  forwarding, and the protection lock still counting down.
- The ten visual checks above were re-run against 1.2.1 with the same result.
  The service-lifecycle change in fix 3 landed after that run; it moves the same
  setup into an idempotent method and does not touch the sampling path, and the
  final build compiles and lints clean, but the fixture suite was not repeated on
  it — doing so needs another accessibility rebind on the phone, which had
  already been unstable.

## What remains unverified

- **Adult-content accuracy.** No adult-positive corpus was used, here or in the
  2026-10-22 benchmark. The layer's adult recall and precision are still
  unestablished, and the fixture suite's block is a synthetic hook, not a model
  detection.
- Real applications, real video playback, battery drain, thermal behaviour and
  long runs; the cadence numbers above come from a synthetic fixture.
- The stale-cache condition was observed on the phone but not reproduced under
  control. The new parser and its edge cases are unit-tested; the two call sites
  that use it are the only wiring.
- The rebound-without-callback condition behind fix 3 was observed (bound
  record, no enforcement, `connected` false) and repaired by a full
  disable/enable of the service, not by the new code path under control. The
  lazy setup is a repair, not a reproduced cause.
- The DNS failure rate was not attributed. The app reports it honestly; whether
  the failures are upstream timeouts on this network or blocked encrypted-DNS
  paths needs a longer capture and a second network before anything is changed.

## Open observations

- **The accessibility service was destroyed and re-created repeatedly** on this
  phone during the session, roughly every minute while the app was being driven
  from adb, and the guard's reported state followed it. Battery optimisation is
  not the cause: the app is on the device-idle whitelist and
  `RUN_ANY_IN_BACKGROUND` is allowed. The cause was not established; the guard
  reconnected after a full disable/enable and the app reported the true state
  throughout. This needs a quiet-phone capture with logcat around a drop before
  anything is changed in the service lifecycle.
- **The self-protection screen worked.** Opening Hisn's own accessibility page
  while the protection lock was active produced *Protection stopped this
  attempt*, which is the designed refusal, even though the Settings top level
  opens normally.

`DnsHealth` degrades the session on any failed query and only clears after
10 successful answers and 120 quiet seconds, so on a network with occasional
failures the Protection verdict reads **needs attention** much of the time. That
was left alone on purpose: a failed query is a real coverage gap for that
request, and softening the signal without attribution would hide it. The
counters, the state and the recoveries stay visible in Session diagnostics.
