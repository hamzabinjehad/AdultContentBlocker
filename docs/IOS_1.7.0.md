# Hisn iPhone and iPad 1.7.0 — the pause rule

Released 6 November 2026 as a development candidate. Simulator-tested only; no
Apple team, Family Controls entitlement, signed build or physical-device
acceptance is claimed.

## Why

1.6.0 closed the bypass and removal gaps: the browser/VPN guard and the Lock
Center. The ScreenZen-inspired thread the audits kept returning to was the
opposite shape of protection: a shield that asks a question instead of only
closing the door, where a deliberate, uncomfortable pause buys a short session.
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
| `ios/Hisn/AppUsageSection.swift` | the pause mode in the editor with opens and session minutes |
| `ios/Hisn/PauseRuleGrantView.swift` | the breath and grant screen |
| `ios/Hisn/MobileRootView.swift` | consumes a handoff on foreground and presents the screen |
| `ios/Hisn/en.lproj/Localizable.strings`, `ar.lproj` | pause-rule copy, both languages |
| `ios/HisnTests/PauseRuleTests.swift` | day rollover, budget, session bounds, handoff freshness, damaged records |

## Verification

- `./test.sh ios` — **40 Python and 242 simulator tests, 0 failures** on the
  iPhone 17 simulator (iOS 26.5); 21 new pause-rule tests.
- The engine tests pin the facts that matter without Apple's ScreenTime
  service: an exhausted day reads closed, a damaged record is not replaced, a
  session from yesterday does not unshield today, and a stale handoff is
  ignored.
- No entitlement, signed build or physical-device acceptance is claimed. The
  pause shield rendering, the secondary button and `openParentalControlsApp`
  need a signed, entitled device; only the code paths are covered here.

## Limits

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
