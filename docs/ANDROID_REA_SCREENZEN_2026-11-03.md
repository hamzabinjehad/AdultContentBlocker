# REA static comparison: ScreenZen, BlockerHero and Hisn Android 1.0.0

Research date: 3 November 2026. This is a **design-research record**, not a
security assessment, a quality ranking or a claim about another product's
runtime behaviour.

## Tool and method

[REA](https://github.com/morluto/rea) 6.5.0 ran through its CLI with the pinned
JADX headless MCP 0.7.1 engine (jar SHA-256
`6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`). The
owner-authorised Samsung SM-G990B2 was connected by USB. The installed base APKs
were pulled to a temporary directory outside the repository; no account was used
and no competitor setting, rule, permission or administrator state was changed.

| App | Package | Base APK SHA-256 | REA package evidence |
| --- | --- | --- | --- |
| ScreenZen | `com.screenzen` | `a4152aed38bddfb28a31c2394efce9200bbdb3a9feddd14419a72b311c4dbb11` | `ev_5e6e5fa305dc01f6623ef33ff4f231fa53887ea75ad25e1affc81e6aa326f3da` |
| BlockerHero 1.5.0 (190) | `com.blockerhero` | `691e1001a12a34933afd097e36c969a581876e8e9574c4092a4827787a5ce578` | `ev_d430d0603d288daaf7abdff98991a02009286cc64d8ef113b24fbdf0efc8f6e5` |

The APKs and decompiler output remain outside the repository. No competitor
code, asset, string table or model weight was copied into Hisn. The
observations below are class and field inventories, not measured enforcement,
classification accuracy or a complete product description.

## What the static models show

### ScreenZen

- `com.cleanarchitecture.domain.model.GroupDBModel` exposes a broad rule group:
  `pauseTime`, `addExtraPauseTime`, `breathingExerciseTime`, `coolDownTime`,
  `dailyOpens`, `customDailyOpens`, `dailyScreenTimeBlockTime`,
  `customDailyScreenTimeBlockTime`, `gracePeriodTime`, `isStrictBlock`,
  `addStrictBlock`, `lockSettings`, `lockSettingsTime`,
  `lockSettingsPasscode`, `pauseAlternativeType`, `customMessage` and
  `customReplacementAppPackage`.
- `AppUsageModel` and `AppUsageWeeklyModel` carry `date`, `dayOfWeek`,
  `totalTime` and a list of per-app usage.
- `StreakCountModel` carries a count, a freeze/recovery path and a local
  message.
- `GroupScheduleDBModel` carries `days`, `startHour`, `startMinute`, `endHour`,
  `endMinute` and a `scheduleType`, so its schedule is per weekday rather than
  one every-day window.
- `DailyCountModel` stores a local date and an open count.

### BlockerHero

- `FocusTime` stores `start_minutes`, `end_minutes`, `repeat`,
  `allowed_packages` (an allowlist) and a label, plus sync fields.
- `UserBlockedItem`, `Keyword`, `SupportedBrowser` and
  `BlockedAccessibilityLog` model custom blocked items, keyword rules, browser
  coverage and local block events.
- `MyAccessibilityService` references `BlockedAccessibilityLog` and a
  `SupportedBrowser`/browser-details model. The service is the enforcement
  surface; the field inventory alone does not show which paths work.

## What Hisn had before 1.0.0

Hisn already had signed domain blocking, selected-app permanent blocks, one
every-day time-window schedule (blocklist or allowlist), custom literal words,
built-in adult-text checks, short-video leaving, sampled image/video checks,
the pause screen, protection locks, guardian pairing and optional managed
uninstall protection. It deliberately did **not** have per-app daily time
budgets, per-weekday schedules, open-count limits, streaks, cloud accounts or a
physical-proximity mode.

## What 1.0.0 took from the comparison

1. **Daily app time limits.** ScreenZen's `dailyScreenTimeBlockTime` and
   BlockerHero's focus periods both treat time itself as the rule. Hisn added a
   package-to-minutes rule read from Android's own on-device usage record, with
   the separate Usage access grant. Hisn does not copy ScreenZen's
   replacement-app, passcode or custom-message fields.
2. **The same protection-lock freeze.** Limits are saved rules, not a separate
   unsealed counter, so the 0.9.0 protection lock freezes them in both
   directions. A guardian policy may add or lower a limit but never remove or
   raise one.
3. **The existing pause screen explains the limit.** No new countdown, streak
   or score was added.

## Deliberately not added

- **Open-count limits and streaks.** A count of opens or a streak is a
  motivational display, not a protection guarantee. Hisn keeps its local pause
  tally only, as the Mac 1.1.8 audit also decided.
- **Per-weekday schedules.** Hisn's 1.0 schedule remains one every-day window.
  Adding a day mask would change the sealed schedule format and its coverage
  rules; it is not required for the 1.0 release.
- **Grace periods, replacement-app launches, passcodes and custom messages.**
  Each is a new weakening or escape surface. Hisn's model is a saved rule plus
  the protection lock, not a temporary unlock flow.
- **Cloud accounts, sync dashboards, physical proximity and remote partner
  dashboards.** These conflict with the on-device, no-account promise.
- **ScreenZen's or BlockerHero's visual identity, copy or code.** Hisn's UI uses
  its own components, colours and English/Arabic strings.

## Limits of this comparison

- Class and field inventories do not prove that either competitor enforces the
  feature, on which Android versions, or with what accuracy. No runtime test of
  either app was performed in this pass.
- The APKs are split-install bases; a field or class may be exercised only by a
  configuration split that was not inspected.
- The installed ScreenZen build's exact version name was not recorded by REA;
  the base digest above identifies the bytes examined.
- This is one emulator/one-device design input, not a competitive benchmark.
  Do not cite it as evidence that ScreenZen or BlockerHero is better or worse at
  blocking.
