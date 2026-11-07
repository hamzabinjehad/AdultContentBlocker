# Hisn iPhone and iPad 1.7.0 — the pause rule

Released 6 November 2026 as a development candidate. Simulator-tested only; no
Apple team, Family Controls entitlement, signed build or physical-device
acceptance is claimed.

## Why

1.6.0 closed the bypass and removal gaps: the browser/VPN guard and the Lock
Center. The gap the audits kept returning to was the opposite shape of
protection: a shield that asks a question instead of only closing the door,
where a deliberate, uncomfortable pause buys a short session.
That is not a weakening of the hard rules — it is a fourth app-rule mode the
person chooses, with its own daily budget, so the app can be "not now" rather
than "never" for the things that are not worth a permanent block.

## What changed

### The rule

- **A fourth app-rule mode, `pause`.** The editor offers **Ask first, with a
  pause** beside always-block, daily budget and quiet hours. It stores opens
  per day (1–10) and minutes per open (1–30); both are validated on save.
- **Its own store and records.** A pause rule shields through its own
  `ManagedSettingsStore` (`hisn.apps.pause`) and keeps a mirrored day record of
  opens used, a session record and the shield's handoff request. A damaged
  budget record reads as *unreadable*, and the day stays closed — Hisn does not
  guess a fresh budget from a record it could not verify.
- **A session is bounded twice.** Apple's usage threshold ends it when the
  chosen minutes are consumed; the monitor also re-shields a session from
  another day or one older than any possible reading of its minutes. The safe
  direction is always back to shielded.
- **One activity per pause rule.** The rule registers one daily DeviceActivity
  with a threshold event for the session, so a session that starts at 23:50 is
  not rejected for being shorter than Apple's 15-minute schedule minimum.

### The shield

- **The pause shield asks.** In pause mode the shield reads **Is this
  important?** with the remaining opens in the subtitle, **Close** as the
  primary action and **Pause first** as the secondary. Every other mode keeps
  the existing one-button hard shield; the secondary button is only offered
  when the budget allows an open.
- **Pause first never opens the app.** It hands a request to Hisn —
  `.openParentalControlsApp` on iOS 26.5 and later, or a local notification
  before that — and the request is ignored if it is older than ten minutes.
  The completion handler is called exactly once even when the notification
  path races the timeout.
- **The app grants, not the shield.** The shield cannot write a session; only
  the app can, after the breath.

### The app

- **A breath screen.** The app consumes the handoff at most once per
  foreground and presents a ten-second ring; **Open for N minutes** is disabled
  until it finishes. Closing the screen spends nothing and the shield stays.
- **The grant order is failure-safe.** The session monitor starts first, the
  session record is written second, one open is consumed third, and the shield
  is cleared last. Any failure before the last step leaves the app shielded and
  the session stopped.
- **A date-bounded record.** The day key is the same Gregorian local day the
  rest of the app uses, so a session from yesterday never unshields this
  morning.
- English and Arabic copy for the mode, the editor, the shield handoff and
  every grant result.


### The screens

The 1.6.0 app was a status dashboard: the first screen answered "is it
configured?" six times before "what do I do now?". This pass rebuilds the tabs
into four places that answer the second question first and move the evidence
one tap away.

- **Today.** One deep card carries the state (protected, one step, not checked,
  checking), the three layer chips and the one action. Under it: the week's
  practice and its streak, the app rules in one sentence each, and a practice
  card. A warning gets its own card only when there is something to say.
  Everything else sits behind the shield button in the header or in More.
- **Apps.** A status pill (opens left today, or blocking on), one row per rule
  with the app icons the system draws from the FamilyControls tokens, the rule
  in a sentence, the opens as dots, and the browser/VPN guard as its own row.
  The editor is a sheet: the selection, one of four modes as a row with an
  honest one-line description, and only the numbers that mode uses — opens per
  day and minutes per open for a pause, minutes for a budget, a window for
  quiet hours. Saving is one filled capsule; removal is a text action.
- **Practice.** The pause as a habit: the streak and its seven dots, the breath
  screen (now in the shield's navy, with the same mark), three honest lines
  about what a pause does and does not do, and the privacy line.
- **More.** The evidence: the layer checks and plan, the Lock Center, the
  commitment, the passcode vault, allowed sites, language, the gray-screen
  guidance, privacy, support and the notices. Nothing is deleted; the setup
  screens keep their exact words.
- **The pause flow.** The handoff is full-screen navy: the mark, "Is this
  important?", the app name when the shield could read it, the opens as dots
  and a ten-second ring. **Open for N minutes** stays disabled until the ring
  finishes; **Keep it closed** is always available. The result state names the
  session or the reason it did not start.

### The design language

- **Paper and ink.** A warm paper canvas in light mode and near-black in dark;
  one deep navy for the card that matters and for the shield, so the system
  screen and the app screen read as one voice.
- **Type.** Rounded display type for titles, numbers and streaks, body text in
  the system face; one large title and one filled action per screen.
- **One accent.** Indigo for actions, calm green for "held", ember for
  attention. Every state also carries an icon and words, never colour alone.
- **Accessibility.** Every tab renders at the largest accessibility sizes and
  in Arabic RTL; body text is at least 4.5:1 on its surface; rings and dots are
  decoration beside a written number; the tab bar keeps four written labels;
  no screen waits for a timer to let the person leave, except the deliberate
  pause itself.
- **Removed.** `AppUsageSection` and `GuardSection` are gone: the rules live in
  Apps, with the same controller calls and validation. The old overview cards
  stay only where the evidence is shown in More.

## Files

| File | Change |
| --- | --- |
| `ios/Shared/PauseRule.swift` | the pure engine and the app-group store (day record, session, handoff request, practice days) |
| `ios/Shared/AppUsageConfiguration.swift` | `pause` mode, `opens`, pause store/activity identifiers, validity |
| `ios/Shared/ShieldTextPolicy.swift` | pause reasons and copy, secondary button, handoff notification text |
| `ios/HisnShieldConfig/ShieldConfigurationExtension.swift` | renders the pause shield's secondary button |
| `ios/HisnShieldAction/ShieldActionExtension.swift` | the Pause first handoff, direct open or notification, exactly-once completion |
| `ios/HisnActivityMonitor/ActivityMonitor.swift` | ends a session at its threshold and watches the pause layer |
| `ios/Hisn/AppUsageController.swift` | grant order, budget/session reconciliation, request consumption |
| `ios/Hisn/HisnTheme.swift` | the 1.7.0 palette, type and components (cards, buttons, rows, ring, week dots, steppers, app icons) |
| `ios/Hisn/TodayView.swift` | the Today tab and the rule sentences shared by Today and Apps |
| `ios/Hisn/AppsView.swift` | the Apps tab and the rule editor sheet (removed `AppUsageSection.swift`) |
| `ios/Hisn/PracticeView.swift` | the Practice tab and the shared streak card |
| `ios/Hisn/MoreView.swift` | More, its protection detail (checks and plan) and settings |
| `ios/Hisn/PauseRuleGrantView.swift` | the full-screen breath and grant screen |
| `ios/Hisn/Pause.swift` | the practice pause in the same navy, recording the practice day |
| `ios/Hisn/MobileRootView.swift` | four tabs, the handoff consumption, and the device-marker and notices plumbing it still owns |
| `ios/Hisn/en.lproj/Localizable.strings`, `ar.lproj` | pause-rule copy, both languages |
| `ios/HisnTests/PauseRuleTests.swift` | day rollover, budget, session bounds, handoff freshness, damaged records |

## Verification

- `./test.sh ios` — **40 Python and 243 simulator tests, 0 failures** on the
  iPhone 17 simulator (iOS 26.5); 21 new pause-rule tests.
- The engine tests pin the facts that matter without Apple's ScreenTime
  service: an exhausted day reads closed, a damaged record is not replaced, a
  session from yesterday does not unshield today, and a stale handoff is
  ignored.
- Renders of all four tabs (English, Arabic, dark mode, iPad), the pause
  handoff and the rule editor are in
  [the 1.7.0 capture record](../design/ios-ui-1.7.0/README.md). They are layout
  evidence from the hosted test harness, not a user study.
- No entitlement, signed build or physical-device acceptance is claimed. The
  pause shield rendering, the secondary button and `openParentalControlsApp`
  need a signed, entitled device; only the code paths are covered here.

## Limits

- **The captures are layout evidence, not a claim about usability.** The
  simulator shows 0 of 3 layers and an unsaved rule because it has no Family
  Controls entitlement; the screens are honest about that state, not
  configured examples.
- **Nothing in this note runs on a real iPhone without the Family Controls
  entitlement.** The simulator has no Screen Time shielding to observe.
- **A session can overrun its minutes.** Apple's threshold callback is not a
  hard clock; the monitor re-shields when it next reconciles. The app says the
  session's length, not a promise about the exact second it ends.
- **The notification handoff needs notification permission**, and the direct
  open needs iOS 26.5. If neither is available, the shield closes and the
  person can still open the pause screen from Hisn.
- **Pause is a chosen mode, not a default.** Existing rules keep their mode;
  a commitment freezes a rule change like any other.
- **The day record is device-local.** Reinstalling or clearing the app group
  resets the budget; the mirror protects against a half-written update, not
  against deletion.
