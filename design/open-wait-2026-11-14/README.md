# Wait before opening (Android)

Date: 14 November 2026. From one sec and ScreenZen: a few seconds of breathing before an app
the person chose comes up. Hisn already had a breath screen, but only *after* a block; this
catches the moment *before*. Nothing is blocked: "Close" is the main button and works at once,
"Open" unlocks when the count reaches zero.

## How it works

- **Rules › Wait before opening** (a tile next to Daily limit and Schedule): the apps, the wait
  (5, 10, 15 or 30 s) and **Longer each time** (each open today adds 5 s, up to a minute).
  Only apps the guard watches are listed: never Settings, launchers or Hisn itself.
- **The wait** (`01-waiting.png`, `02-ready.png`): the app's icon and name, "Take a breath
  first", a circle that breathes in and out with the count, the person's reason if saved, and
  today's counts for that app: "opened N · closed instead N". Back is the same as Close.
- **One visit, one wait**: after "Open", coming back to the app within 3 minutes does not wait
  again. After that it waits again, longer if growth is on (`03-second-open-longer.png`).
- A **protection lock** refuses making it weaker (removing an app, a shorter wait, growth off);
  adding apps or waiting longer is always allowed.
- It runs through App protection; the settings screen says so when that is off.
- With "Remove animations" on, the circle holds still.

Code: `OpenWait` (core rules, `OpenWaitTest`), `OpenWaitStore`, `OpenWaitActivity`,
`OpenWaitSettingsActivity`, `nativeshell/OpenWaitScreens.kt`, a hook in `TextGuardService`.

## Checked

- `test-core.sh`: OpenWait tests pass. `test-ui.sh`: 39 of 39 on the emulator and 39 of 39 on
  the owner's Samsung (separate test copy), including four new tests (Open unlocks only after
  the wait, Close works at once, counts and reason, the settings save).
- End to end on the emulator with App protection on: Chrome opened → the wait; Open → Chrome
  stays up; Home and back within 3 min → no wait; after 3 min → a 10 s wait; Back → Home;
  opened 1 and closed 1 saved.
- End to end on the owner's Samsung (signed direct build, App protection on), with the
  owner's own reason shown (`05-samsung-waiting.png`): Chrome → the wait; Open → Chrome stays
  up; Home and back at once → no wait. Play Store → Close → the Niagara launcher; opening it
  again waits and shows "closed instead 1" (`06-samsung-closed-count.png`); Back → Home.
- Found: ScreenZen also pauses YouTube on that phone, so both pause screens stack. Hisn does
  not try to detect other apps' pauses; pick an app in one of them, not both.
- Arabic on the emulator (`07-ar-waiting.png`, `08-ar-ready.png`, `09-ar-settings.png`):
  right to left, Arabic plural for the seconds, nothing cut off.
- TalkBack: the "Open in N seconds" button no longer announces every second. One polite line,
  "The wait is over. You choose.", is announced when "Open" unlocks.
- Not checked: TalkBack by ear on a phone; Arabic on the phone itself.
