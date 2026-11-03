# Hisn iPhone and iPad 1.3.0 — a pause, quiet hours and a calmer overview

Released 3 November 2026 as a development candidate. Simulator-tested only; no
Apple team, Family Controls entitlement, signed build or physical-device
acceptance is claimed.

## Why

The 1.2.0 app was honest but heavy: the first screen met a new person with a
checking state, four paragraphs about missing layers and a long plan section.
The only saved app rules were **always block** or a single **daily budget**, so
there was no way to say “keep these apps away at night” without blocking them
all day. And there was no on-demand way to meet an urge before it became an
address bar or an app tap.

This release borrows three ideas from ScreenZen and comparable apps — a calm
closeable pause, one status/one action, and first-class time windows — without
copying their code, assets, copy, accounts or cloud state. The design research
is recorded in [the iOS REA audit](REA_AUDIT_IOS_2026-11-03.md).

## What changed

### 1. A private on-demand pause

The Mac 1.1.8 pause is now available on iPhone and iPad:

- A 30-second guided breath: inhale 4 seconds, exhale 6 seconds, three cycles.
- Five rotating reminders, so the same sentence does not meet every pause.
- Four needs — Rest, Company, Calm, Something to do — each with one offscreen
  tip.
- A local tally of today’s pauses: one calendar day, a count and the time of
  the last pause. A pause within 30 seconds of the last one is the same moment
  and does not count twice; the count is capped at 9,999. No host, reason,
  history, account or network request is stored.
- Reachable from the overview card and a wind button in the navigation bar on
  every tab. It changes no rule or permission, so it remains available during
  a commitment.
- Closing is always immediate. Nothing on the screen waits for the breath to
  finish; the close control is present in both the breathing and completion
  stages. The passcode-style app-switcher concern does not apply: the tally is
  not sensitive, and the screen contains no browsing information.

### 2. Daily quiet hours for selected apps

A third rule mode for Apple’s Screen Time app picker:

- **Always block**, **daily combined usage budget**, or **daily quiet hours**.
- One daily window with presets (late night 22:00–07:00, evening 20:00–23:00,
  work hours 09:00–17:00) and custom From/Until times.
- The selected apps, categories and websites are shielded every day during the
  window. The rule is saved in the same versioned, mirrored app-group records
  as the other modes and is frozen while a commitment runs.
- A window that crosses midnight is registered as **two same-day
  `DeviceActivitySchedule`s** — evening start to 23:59 and 00:00 to the morning
  end — rather than relying on an overnight interval whose behaviour Apple does
  not document. The evening end deliberately does not clear the shield; the
  morning end does. A one-minute gap at midnight therefore cannot open.
- Apple rejects schedules shorter than 15 minutes. A crossing window is split
  at midnight, so each half must also meet that minimum; the editor validates
  the window and explains why an invalid one cannot be applied.
- The app and the monitor reconcile the saved quiet-hours shield with **both the
  device clock and, once read, Apple’s server time**. They clear only when both
  are outside the window, so moving the device clock forward cannot end quiet
  hours early. The monitor’s interval end is driven by the device clock; if the
  saved Apple-time reading still puts the device inside the window, the shield
  remains and the app clears it once both clocks agree. A quiet-hours rule now
  also asks the app to refresh its shared Apple-time reading while it runs.
  Without a reading, the device clock is the only clock; the app says so in the
  scope copy.
- `AppUsageController` restores the shield after an authorized relaunch and
  clears it outside the window, using the app’s reading or the shared monitor
  anchor. The monitor re-checks the saved rule at each interval callback and
  leaves the shield untouched if the rule cannot be read.
- Health separates **registered for monitoring** from **saved but not
  registered**. The copy says this is configuration evidence, not a blocking
  test, and that real-device verification is still required.
- The long browser/coverage explanations moved behind **Browser and coverage
  limits**, so the rule controls come first.

### 3. One status, one action

The overview now leads with a hero instead of the full diagnostic list:

- One plain status: **Protection is on**, **Protection needs attention**,
  **Protection has not been checked**, or **Checking protection**.
- Three compact layer chips (Safari, DNS, Screen Time), each on or off.
- One primary action: **Finish setup** or **Check again**.
- The detailed setup issues are one disclosure below, with a count, instead of
  four paragraphs meeting the person at launch.
- A short scope line remains always visible: *Configuration checks, not proof of
  complete blocking.*

The full readiness rows, commitment section, blocked-site count and layer
details remain below. Setup still shows the complete diagnostic view.

## Files

| File | Change |
| --- | --- |
| `ios/Hisn/Pause.swift` | Pause tally, breath timeline, reminders, needs, card and full-screen view |
| `ios/Hisn/ProtectionHero.swift` | Action-first overview hero and layer chips |
| `ios/Hisn/MobileRootView.swift` | Hero and pause card on the overview; pause toolbar on every tab; full-screen pause |
| `ios/Hisn/AppUsageSection.swift` | Quiet-hours editor, presets, validation, browser limits disclosure |
| `ios/Hisn/AppUsageController.swift` | Quiet-hours registration, restore, both-clock reconciliation, health |
| `ios/HisnActivityMonitor/ActivityMonitor.swift` | Window callbacks, midnight split, layer-alert expectation |
| `ios/Shared/AppUsageConfiguration.swift` | `quietHours` mode, validation, plan, activity, both-clock active check, Codable compatibility |
| `ios/Hisn/MobileCommitmentStore.swift` | Shared-state reading for quiet hours |
| `ios/Hisn/ProtectionController.swift` | Quiet hours ask for a shared Apple-time reading while configured |
| `ios/Hisn/en.lproj`, `ios/Hisn/ar.lproj` | English and Arabic pause, hero and quiet-hours copy |
| `ios/HisnTests/PauseTests.swift` | Tally, merge, day boundary, breath, reminders, store, renders |
| `ios/HisnTests/QuietHoursTests.swift` | Window validation, midnight split, both-clock checks, Codable compatibility, freeze, health, renders |
| `ios/generate_xcodeproj.py`, project | Version 1.3.0, build 7; new source and test membership |
| `ios/README.md` | 1.3.0 section and updated verification count |

## Verification

- `./test.sh ios` — **38 Python tests and 206 hosted tests, 0 failures** on the
  iPhone 17 simulator, iOS 26.5. The hosted count was 185 before this release;
  the 21 new tests are 9 pause and 12 quiet-hours tests.
- `python3 -m unittest ios.test_localization` — pass; English/Arabic keys and
  placeholders match, and every key the SwiftUI code looks up exists in both.
- The app builds for the simulator with `CODE_SIGNING_ALLOWED=NO`; the built
  `Info.plist` reads 1.3.0 (7).
- Render tests cover the pause at breathing and completion stages in English
  and Arabic, the pause at the largest accessibility text size, and the
  quiet-hours editor in both languages.
- REA 6.4.0 (`inspect-artifact`, provider-free) inventoried a clean simulator
  build of this tree:
  evidence `ev_9ce139c0da4ed5cc318c2ac33c0767ca0d7917b8d7881cbf12eb74dfe2da0dec`,
  SHA-256 `fc1becf3befc6bf7e68869be68947a8f1a163966f1eeb23ff7eddac14f259090`.
  This is a static artifact inventory, not runtime proof. See the
  [iOS REA audit](REA_AUDIT_IOS_2026-11-03.md).
- No physical device, Family Controls entitlement or signed IPA was available.
  The Screen Time app-limit behavior, quiet-hours callback timing, shield
  enforcement, device-clock reconciliation and pause behavior on a real iPhone
  remain unproven.

## Limits

- Quiet hours are an **app-level Screen Time rule**, not a device-wide network
  filter or a VPN. They cover the apps, categories and websites chosen through
  Apple’s picker; Safari’s domain blockers and Hisn Text are separate layers.
- The Screen Time API gives no “blocking actually happened” signal. Health and
  the registered/stopped rows are configuration evidence.
- The window follows the device clock. When Hisn has a reading of Apple’s time,
  it also requires that clock to be outside the window before clearing; without
  a reading, the device clock is the only one.
- A window cannot end exactly at midnight: an end time of 00:00 and a crossing
  window’s lower half are rejected unless each split leg is at least 15
  minutes. The editor explains this instead of rounding silently.
- The pause is a private habit tool, not a protection layer. It records only a
  day, a count and a time on this device, and it does not change any rule.
- During a commitment, the quiet-hours rule is frozen like every other saved
  app rule. On an unsupervised phone the person can still revoke Screen Time
  access or delete Hisn; the passcode vault and supervised profile remain the
  removal-protection paths.
- No custom ShieldConfiguration/ShieldAction extension is included in this
  release. The system shield remains Apple’s; a future release can add Hisn’s
  own shield text and a deliberate pause before an unlock.
- Simulator tests prove the policy, the registration plan, the controllers and
  the rendered views. They do not prove DeviceActivity callback reliability,
  real Screen Time enforcement, battery behavior, or VoiceOver output on a
  physical device.

## Device acceptance still needed

1. Start quiet hours on an entitled device; verify the shield starts and ends
   on both sides of midnight, across a reboot, and with the device clock moved
   forward and back.
2. Verify a running commitment freezes the window, and that an unreadable
   commitment still counts as locked.
3. Revoke Screen Time access in Settings during a quiet-hours rule; verify the
   stopped state and recovery path do not falsely claim enforcement.
4. Verify the pause’s tally, rotation and close control with VoiceOver and the
   largest Dynamic Type size.
5. Run the App Store review disclosure for the pause and quiet-hours features
   with the Family Controls distribution entitlement.

## Next owner actions

The Apple Developer Program, Family Controls distribution entitlement, a
disposable iPhone and TestFlight remain the owner actions in
[the iOS improvement plan](IOS_IMPROVEMENT_PLAN.md) and
[1.0 readiness](1.0_READINESS.md). This release does not change those.
