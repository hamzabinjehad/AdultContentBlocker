# Hisn Mac 1.1.8 — the pause window

Released 3 November 2026 as a development candidate. Unsigned and unnotarized;
no Developer ID package, notarized build or physical-Mac acceptance is claimed.

## Why

The browser extension's block page already gives a person a way through the
moment of an urge: a slow breath, a reminder, a question about what they need,
and a local count of how many times they walked away. The separate Mac app had
no equivalent — the only actions at the moment of temptation were a lock or a
site rule. Screen Zen's Mac interface showed the same gap from the other side:
its pause is the centre of the product, not a settings page.

1.1.8 brings that pause to the Mac. It is deliberately **not** an enforcement
layer and it changes no rule. It is available during a lock, adds no delay to
closing, and stores one calendar day and one number.

## What changed

### A pause window

- **One slow breath.** In for four seconds, out for six, three times — thirty
  seconds. The circle follows the breath and the label switches between
  "Breathe in…" and "Breathe out slowly…". The window can be closed at any
  moment; the close button is always present.
- **A reminder.** Five reviewed reminders take turns through the day, so the
  same sentence does not meet the person every time.
- **A question.** After the breath: Rest, Company, Calm or Something to do,
  each with one concrete, offscreen tip.
- **A local count.** "Pauses today: N" is stored in UserDefaults as a day, a
  count and the time of the last pause. The value is capped at 9,999, a pause
  within thirty seconds of the counted one is the same moment, and another day
  starts over. No host, no reason, no time list, no network request.
- **Three ways in.** A row under the protection card on Overview, a menu-bar
  item, and **Protection › Take a pause… (⇧⌘P)**. The window is its own small
  window, so the menu bar can open it without bringing the whole app forward.
  Once a pause is recorded, the menu bar also shows "Pauses today: N".

### Honesty and accessibility

- The pause says what it is: a private pause, with only today's count kept. It
  does not claim to block, diagnose or measure recovery.
- VoiceOver labels cover the breathing state, the breathing progress, the
  pause row and the four needs. The needs are ordinary buttons, not a custom
  gesture surface.
- Every new string ships in English and Arabic; the dynamic reminders, needs
  and tips use SwiftUI localized keys so the test render and the real app
  choose the same language.

## Files

| File | Change |
| --- | --- |
| `macos/Hisn/Pause.swift` | Tally, breath timeline, needs, reminders, pause window and view |
| `macos/Hisn/ContentView.swift` | Pause row on Overview |
| `macos/Hisn/StatusMenu.swift` | "Take a pause…" in the menu bar |
| `macos/Hisn/HisnApp.swift` | "Take a pause…" and ⇧⌘P in the Protection menu |
| `macos/Hisn/Localizable.xcstrings` | 24 new strings, all with Arabic |
| `macos/HisnTests/PauseTests.swift` | Tally, day boundary, cap, breath, reminders, store and render coverage |
| `macos/generate_xcodeproj.py`, project | 1.1.8, build 12; new source and test files |
| `design/macos-pause-2026-11-03/` | Overview, breathing and needs renders in English and Arabic |

## Verification

- `./test.sh macos` — **PASS**: 521 tests, 0 failures; 589 strings, all with
  Arabic (including the export of every string the compiled code uses).
- `bash macos/release/release.sh --smoke` — **PASS**: universal unsigned
  package structure validated, `Hisn-UNSIGNED-TEST.pkg`, 8.2 MB, SHA-256
  `376a563e7e197b9e9db98fe720e6ba5dc15adf1768cc75021f9cd4842b7aa843`. It was
  not installed.
- The new `PauseTests` cover: first pause and the 30-second merge, a day
  boundary, a malformed/overflowing tally, the 4/6-second breath across three
  cycles, reminder rotation, the stored shape, and a 460×640 render in both
  languages at both the breathing and needs stages.
- No rule, permission, filter, browser-bridge or lock behaviour changed.

## Limits

- The design captures are renders from the test host (the view bitmap, not a
  physical display). There is no user study, screen-reader audit or measured
  effectiveness result.
- The tally is a local convenience, not evidence. Anyone with the account can
  read or edit it, a reinstall may keep the preferences file, and it is not a
  security control.
- The pause is available on demand. It is **not** tied to a blocked page on
  the Mac: Safari and other surfaces that do not run the extension still rely
  on the filter and DNS layers.
- No Screen Zen code, assets or text were copied. The implementation is Hisn's
  own; the comparison was design research. See
  [the REA audit](REA_AUDIT_SCREENZEN_2026-11-03.md).

## Next owner actions

1. Build, sign and notarize with a Developer ID, then run the pause from the
   installed app with a real keyboard, VoiceOver and the Arabic app language.
2. Run the standard-account guardian and bootout checks that remain from 1.1.7.
3. Record the physical-Mac result in [RELEASE_VALIDATION.md](RELEASE_VALIDATION.md)
   and update [1.0 readiness](1.0_READINESS.md).
