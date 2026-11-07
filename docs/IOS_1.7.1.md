# Hisn iPhone and iPad 1.7.1 — weekdays, session control and an honest handoff

Released 6 November 2026 as a development candidate. Simulator-tested only; no
Apple team, Family Controls entitlement, signed build or physical-device
acceptance is claimed.

## Why

1.7.0 rebuilt the app and added the pause rule. Three gaps were left, and none
of them was a surface change:

1. **The quiet-hours window ran every day.** The Android app has had chosen
   weekdays since 1.1.1, and "block during the workday" or "only on school
   nights" is a promise an every-day window cannot keep. A person who wanted
   that had to switch the rule off on Friday and remember to switch it back.
2. **A running pause session was only a badge.** The person could not see on
   Today that a session was open, and had no way to end it early — the one
   control that only ever tightens and therefore never needs to be frozen.
3. **The shield handoff was silent when it could not work.** Before iOS 26.5 the
   shield's **Pause first** reaches Hisn through a notification. With
   notifications off, the shield closed and nothing explained why.

## What changed

### Chosen weekdays (blocking)

- **A Monday-first day mask on the quiet-hours rule**, stored as seven bits: bit
  0 is Monday, bit 6 is Sunday. Rules saved before this release decode with
  every day, so an existing window keeps running exactly as it did.
- **The window belongs to the day it starts on.** A 22:00–07:00 window chosen
  for weekdays holds through Saturday morning, and Friday night is a weekday
  night; Saturday night is not. The same rule is used by the app's refresh, the
  monitor's both-clock clear and the shield's re-application.
- **The monitor checks its leg.** The evening (or same-day) leg uses today; the
  morning leg of a crossing window uses yesterday. A day that was not chosen
  clears the window store instead of shielding.
- **The editor gains day chips and presets** (every day, weekdays, weekends) in
  the UI language's own initials, a "choose at least one day" state, and a rule
  row that says *weekdays*, *weekends* or *N days*.
- A weekday change is frozen during a commitment like every other rule field.

### Session and lock on Today

- **A running session is visible** on Today and under the rule in Apps, with
  **End now**. Ending re-shields immediately; because the action only
  tightens, a commitment does not refuse it.
- **An active commitment is visible** on Today with its deadline, instead of
  living only inside More.

### The handoff, honestly

- Before iOS 26.5 the pause editor says that **Pause first** reaches Hisn
  through a notification, and offers **Allow notifications** — or **Open
  Settings** when they were declined. iOS 26.5 and later keeps the direct
  handoff and shows nothing.

## Files

| File | Change |
| --- | --- |
| `ios/Shared/AppUsageConfiguration.swift` | `days` field, Monday-first helpers, start-day window logic, leg helper, validity, freeze |
| `ios/HisnActivityMonitor/ActivityMonitor.swift` | each window leg checked against its start day |
| `ios/Hisn/AppsView.swift` | day chips and presets, End now, notification guidance |
| `ios/Hisn/TodayView.swift` | session strip with End now, commitment deadline strip, day phrase |
| `ios/Hisn/AppUsageController.swift` | `endPauseSessionNow()` |
| `ios/HisnTests/QuietHoursTests.swift` | nine weekday tests: indexing, crossing start day, legs, clocks, validity, freeze, legacy decode |
| `ios/HisnTests/UIRenderTests.swift` | a render of the weekday editor |

## Verification

- `./test.sh ios` — **40 Python and 252 simulator tests, 0 failures** on the
  iPhone 17 simulator (iOS 26.5); nine new weekday tests.
- The weekday cases are pure and pinned: Monday-first bit order, a crossing
  window's start day, the morning leg's yesterday, one clock on a chosen start
  day, an empty mask rejected, a weekday change refused during a commitment,
  and a rule saved before weekdays decoding as every day.
- Renders of the weekday editor, on an iPhone and an iPad, are in
  [the 1.7.1 capture record](../design/ios-ui-1.7.1/README.md); the iPad render
  caught duplicate "Presets" labels and an unconstrained sheet, both fixed.
- No entitlement, signed build or device acceptance is claimed. DeviceActivity
  callbacks are not exercised here; the monitor is thin and its decision is the
  tested helper.

## Limits

- **A callback is not a clock.** Weekday selection is applied when Apple
  delivers the window leg; the app reconciles on foreground. A missed callback
  cannot shield an unchosen day, but it can leave a chosen day's clear a little
  late.
- **End now only tightens.** It stops the session and re-applies the shield; it
  cannot open anything or shorten a commitment.
- **The day mask is device-local** and stored with the rule, so it is frozen by
  a commitment and erased by removing the rule.
- Real shielding still needs the Family Controls entitlement on a signed
  device.
