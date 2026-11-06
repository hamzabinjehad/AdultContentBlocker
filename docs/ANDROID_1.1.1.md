# Android 1.1.1: weekday schedules

Development build in the repository, 6 November 2026, version 1.1.1,
versionCode 28. No signed APK is published and no device run is claimed. The
change adds one scheduling dimension and relaxes no rule, permission, consent
text or stored field.

## Why

The app schedule could say *one window, every day*. A household usually needs
the opposite: a late-night window on school nights, a different one at the
weekend, or no restriction on a day off. The only way to express that before
this version was to edit the schedule every day — which is exactly the moment a
person is least likely to do it.

## What changed

- **A Monday-first day mask on the saved schedule.** `DailyAppSchedule` carries
  `days`, a seven-bit mask (bit 0 = Monday). A mask of zero days is rejected: a
  schedule that never runs is not a rule. A disabled schedule keeps the days it
  will use when re-enabled.
- **An overnight window belongs to the day it starts on.** A 22:00–07:00
  window selected for Monday covers Monday evening and Tuesday morning; the
  ten-minute arming window before the start follows the same rule. The same
  per-day rule is applied to a daytime window, so no interval special case
  leaks into `activeAt`, `blocks` or `selects`.
- **Preservation is checked over the whole week.** Adding a schedule to a
  protection lock still compares coverage minute by minute, now across all
  seven days, so an overnight portion cannot be dropped by changing the day
  mask while the lock runs.
- **A day picker in the schedule editor.** A row titled **Days this window
  repeats on** opens seven checkboxes, localised with the device locale's day
  names, and refuses an empty selection. The row summary reads **Every day** or
  a count such as **3 days a week**.
- **Backward-compatible saved format.** A schedule saved before this version
  has no `schedule_days` value and reads as every day, which is what its single
  window already did.
- The schedule summary on Protection and Rules names the repetition, and the
  existing Arabic strings cover every new label and plural.

## Files

| File | Change |
| --- | --- |
| `android/core/src/main/java/app/hisn/core/DailyAppSchedule.java` | day mask, per-day coverage, overnight ownership, seven-day preservation |
| `android/core/src/main/java/app/hisn/core/SavedSettings.java` | `schedule_days`, defaulting to every day for old files |
| `android/app/src/main/java/app/hisn/android/MainActivity.java` | day row, checkbox picker, save with the chosen mask, summaries |
| `android/app/src/main/java/app/hisn/android/TextGuardService.java` | passes the local day with every schedule query |
| `android/app/src/main/res/values/strings.xml`, `values-ar/strings.xml` | day title, help, error and plurals, English and Arabic |
| `android/core/src/test/java/app/hisn/core/DailyAppScheduleTest.java` | day-mask boundaries, overnight anchoring, seven-day preservation, exhaustive minute grid |

## Verification

| Check | Result |
| --- | --- |
| `JAVA_HOME=<JBR> sh test-core.sh` | **PASS** — all core suites, including `DailyAppScheduleTest`, strings, notices and the release split |
| `python3 check-strings.py` | **PASS** — 552 strings, 16 plurals, 544 source references, English and Arabic |
| `./gradlew :app:compileDebugJavaWithJavac` | **PASS** |
| `./gradlew :app:assembleDebug :app:lintDebug` | **PASS** — 0 lint errors |
| Schedule editor on a device or emulator | **Not run for 1.1.1.** The picker follows the existing dialog and setting-row components; a device check of night, weekday and empty-selection cases remains open |

## Limits

- **The device clock and time zone decide.** Changing either can bypass the
  timing, as with every schedule. This is not a background scheduler.
- **One window per schedule.** Different windows on different days still need
  the every-day window or permanent app blocking; multiple per-day windows are
  not part of this version.
- **Accessibility must be enabled and connected.** The schedule restricts
  selected apps; it is not a phone-wide or internet-wide block.
- **Existing installs stay every day.** Nothing changes silently; a person who
  wants a day mask must open the editor and choose days.
- **Weekday selection is not commitment-protected by itself.** During a lock,
  a change is accepted only if it preserves every previously covered day and
  minute; adding days is allowed because it tightens.
