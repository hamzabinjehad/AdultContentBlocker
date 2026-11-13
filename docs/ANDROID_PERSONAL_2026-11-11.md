# Android: a personal layer

**Status: development change, 11 November 2026.** This is the 1.6.0 / 43
candidate tree, not a signed artifact and not a phone result. The change adds an
optional local name, a reason, a greeting, a reason card, the same reason on the
pause screen, and a local day/pause count. It changes no layer, threshold,
permission, network request or stored protection rule.

## What changed

- **First run.** The welcome flow gains one optional page after the three
  introductions: a name, five ordinary reasons (**For my faith**, **For my
  family**, **For my focus and time**, **For my sleep and rest**, **For my
  self-respect**) or the person's own words. **Skip for now** still leaves the
  whole flow at any point. The page saves when it is left, so a typed name is
  not lost to Home or Back.
- **Protection.** A saved name replaces the all-caps tagline with a greeting,
  such as **Good evening, Ahmed**. A card under the status hero holds the reason
  as a serif quotation and a short line such as **Day 4 with Hisn · 12 pauses
  taken**. With nothing saved, the same place is one tappable **Make it yours**
  row rather than a second filled action. The card sits below the attention
  list, so a coverage gap is never pushed off the first screen by it.
- **Pause screen.** A saved reason appears above the breathing circle with
  **Your reason** as its label. The four reason categories and the generic
  reminders are unchanged.
- **Settings.** A new **Personal** section at the top has **Your name**, **What
  you're protecting**, and the note **Saved only on this phone**. Both rows open
  one editor with the same fields as the welcome page, a remove action, and a
  secure window (`FLAG_SECURE`) while it is open.
- **Core.** `PersonalProfile` cleans and bounds the values, maps the local hour
  to a greeting, clamps the day number, and advances the pause count. It has no
  Android dependency and is covered by `PersonalProfileTest` in
  `android/test-core.sh`.

## What the counters mean

- **Day N with Hisn** starts on the local calendar day of the first save. It
  counts elapsed calendar days, not consecutive clean days and not a recovery
  score. A clock moved before the first save still reads as day 1; a damaged or
  missing start day shows only the pause line.
- **Pauses taken** increments only when the restriction screen records a
  genuinely new pause. A rotation (`savedInstanceState`), a re-open inside the
  existing 30-second merge window, and a paused screen that keeps the same count
  do not increment it. A new local day starts today's tally over, but the total
  continues.
- Both are local counters, not proof that a layer was running on every one of
  those days.

## Storage and privacy

The name, the reason, the start day and the pause total live in a separate
`personal` `SharedPreferences` file with `MODE_PRIVATE`. They are **not** part of
`PolicyStore`, the sealed rule file, the exported rule document, diagnostics,
notifications or any network request. `android:allowBackup="false"` and the
existing data-extraction rules keep the file out of cloud backup and
device-transfer. The editor window is secure so the recent-apps preview and
screen recording cannot show it.

The values are cleaned before they are stored: whitespace is collapsed, control
characters and bidi overrides are dropped, a name is bounded to 24 code points
and a custom reason to 80. The app does not encrypt this file beyond Android's
application sandbox; it makes no claim that a rooted or unlocked device cannot
read it. The reason is shown on the Protection screen, which is an ordinary
window; the editor is the secure one. The Android keyboard is asked for no
suggestions and no personalized learning, but its own behavior is outside the
app.

The profile is deliberately not a protection rule: the protection lock does not
freeze it, and changing it cannot weaken a rule. **Remove personal details**
deletes the name, reason, start day and pause total; it does not touch saved
rules. Clearing Android's app data or uninstalling Hisn removes the file too.

## What this does not claim

- It is not a clean-day streak, a recovery score or evidence that a layer ran
  every day. The app says **Day N with Hisn** and **pauses taken** only.
- It does not identify the person. A name is a greeting, not an account; there
  is no Hisn account, sync or guardian transfer.
- The reason is a reminder chosen by the person. It is not a medical or
  religious claim, and the app does not verify it.
- No new permission is requested. The feature uses only APIs already on the
  app's Android 6.0 floor (the existing core-library desugaring carries
  `java.time`); image checks remain Android 14+. No Android 6 device run of
  this feature is claimed.

## Verification

| Check | Result |
| --- | --- |
| `android/test-core.sh` (JDK 25) | Pass, including the new `PersonalProfile` suite, the seed, strings and notices |
| `:app:assembleDebug` | Pass |
| `:app:assembleRelease` | Pass; unsigned because no release key was supplied |
| `python3 check-strings.py` | Pass (708 strings, 19 plurals; English and Arabic) |
| Emulator, API 36, English and Arabic | Personal card, greeting, welcome page, pause reason, Settings, dark theme and 1.3× font checked |
| `:app:lintDebug` | Pass; 0 errors, no personal-layer finding |

The emulator run used a debug APK and a seeded local profile (**Ahmed**, a
custom reason, day 4, 12 pauses). The pause screen was opened through the debug
fixture. The editor's secure window was checked through the accessibility tree,
not a screenshot, because `FLAG_SECURE` intentionally blanks captures. There is
no physical-phone run, TalkBack pass, rotation/process-death pass, signed
artifact, or native-speaker review of the Arabic copy.

## Screenshots

The captures are in
[`design/android-personal-2026-11-11/`](../design/android-personal-2026-11-11/README.md).
