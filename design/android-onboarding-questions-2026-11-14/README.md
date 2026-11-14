# Android onboarding: one question per page and a held promise

Date: 14 November 2026. Follows the Unchaind and Unrot review in
[competitors-2026-11-14](../competitors-2026-11-14/README.md). Captures from the `hisn_ui`
emulator, debug build, dark theme.

The six story pages are unchanged. Setup was three pages (you, permissions, guard); it is now
six on the direct build and five on Play:

1. **Name** (`01-name.png`): text field; the keyboard's Next key goes on.
2. **Reason** (`02-reason.png`): six numbered answer cards. Tapping one is the answer and the
   next question slides in; "In my own words" opens a text field instead.
3. **Where it starts** (`03-start.png`): browser, social apps, short videos, not sure.
4. **Promise** (`04-promise.png`, `05-promise-holding.png`): "I will guard what I see." with
   the person's reason under it. Hold the button for 1.2 s; it fills while held and drains if
   let go early. TalkBack gets one double-tap action. "Not now" goes on without it.
5. **Permissions** (`06-permissions.png`): the "where it starts" answer puts its grant first
   and says why in one line. Unchanged otherwise.
6. **Uninstall guard** (direct build only): unchanged.

Each question page says "Your answers stay on this phone." Name and reason are saved as
before (also when swiping away); the "where it starts" answer is not saved anywhere.

## Second pass (same day)

- Each opening page has its own mark (shield, pause, lock) instead of the same shield three times.
- A seventh story page, **"A slip is not a reset"**: Hisn counts days with it and pauses
  taken, never clean days, so a slip wipes nothing.
- A fourth question, **"When is it hardest?"** (morning, day, evening, late at night, it changes).
- A **"Here is your setup"** page after the permissions: the person's name and reason, which
  of Website filtering and App protection Android reports running, and one tip. Late nights
  point to Rules › Schedule (fixed at 22:00–06:00 until its time picker lands); any other
  answer points to Rules › Daily limit. On the Play build this is the last page ("Open Hisn").

Setup is now eight steps on the direct build and seven on Play. 28 of 28 UI tests pass.

## Third pass (same day), from Unchaind again

- **"Four layers, one shield"** (`07-four-layers.png`), a story page after "How it works":
  one tile per layer with what it actually does. It takes the place Unchaind gives to its
  shame slides.
- **"What got in the way last time?"**: first try, switched the blocker off, deleted it,
  private tabs or another browser, late nights. Each answer adds one tip to the setup page:
  protection lock, uninstall guard (direct build only; Play never offers it), filtering
  below the browser, or the night schedule. Tips never repeat.
- **Three promises** instead of one ("Promise 1 of 3"), each held, like Unchaind's five
  pledges. Each starts with an empty button. "Not now" puts off all the remaining promises.

Story pages are now eight; setup is 11 steps on the direct build and 10 on Play.
29 of 29 UI tests pass (two new: Play never offers the guard; the second promise starts
empty and "Not now" skips the rest).

## Checked

`bash android/test-ui.sh emulator-5554 direct`: 27 of 27 pass, including three new tests
(the start answer orders the grants; the promise by TalkBack's action; swiping off the name
page saves it). By hand on the emulator: a tap, a 0.4 s and a 0.9 s press do not promise; a
1.7 s hold does. Not checked: a real phone, light theme and Arabic by eye.

## Fourth pass (same day): "Choose what Hisn checks"

Every competitor turns protection on before onboarding ends (Opal pre-selects a block list,
BlockerX opens on its filter switch). Hisn finished with Adult tags and Image checks off and
no apps chosen, so a new page sits between the permissions and the setup summary
(`09-checks.png`):

- Two switches, **Adult words and tags** and **Images and video**, each with the shell's
  short disclosure under it. Turning one on shows the **same full disclosure dialog** as the
  switch in the main app (`10-checks-disclosure.png`); "Cancel" leaves it off. Images is
  disabled below Android 14.
- **Apps to check**: browsers first, then today's most-used (with usage access), then A–Z;
  eight shown, "Show all apps" for the rest. **"Add all browsers (N)"** ticks every installed
  browser in one tap. Nothing is ticked for the person (`11-checks-chosen.png`).
- If App protection is off, the page says in one line that these checks run through it.
- The setup summary adds a row for each check: "On · apps: N" only when App protection runs;
  otherwise "Saved, not running". Choices are written to the same rules the shell uses, so
  Home shows them straight away.

Setup is now 12 steps on the direct build and 11 on Play.

Checked: `bash android/test-ui.sh emulator-5560 direct`, 35 of 35 (a new test: the switch
asks before it saves, and ticking an app saves it and updates the count). By hand on the
emulator: the dialog, "Add all browsers", and the summary. Installed on the owner's phone
(signed direct release, data kept). Arabic in dark theme looked at by eye
(`12-checks-ar-dark.png`, `13-checks-ar-dark-apps.png`): right-to-left order, switches and
checkboxes on the leading edge, nothing cut off. Not checked: this page on the real phone.
