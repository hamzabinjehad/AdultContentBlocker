# Android 1.1.0 UI/UX captures — 2026-11-03

Build: debug `app.hisn.android` 1.1.0 (versionCode 27) on the API 36 emulator
`hisn_ui`. The emulator was provisioned as Hisn's device owner from earlier
tests; the captures show that state only where relevant. No owner rules were
changed.

## Captures

- `01-welcome-en.png` — first-run welcome, page 1, English.
- `02-welcome-en-3.png` — first-run welcome, page 3 (Set up protection).
- `03-welcome-ar.png` — first-run welcome, page 1, Arabic RTL.
- `04-protection-pause.png` — Protection with the **Take a pause** row near the
  top and the next setup step.

The daily-limit editor is intentionally left out: it holds app names and usage
totals, and the editor marks the screen secure (`FLAG_SECURE`), so `screencap`
returns black. Its usage row was read back through the accessibility tree as
`Chrome / Used 4 min of 15 min today`.

## Verified interactively

- Welcome pages advance with **Next**, **Skip for now** opens Protection, and
  **Set up protection** opens Rules.
- Back moves one page back; back from page 1 skips the welcome.
- Arabic renders RTL with the same structure.
- **Take a pause** opens the voluntary pause; **Back to Hisn** and system Back
  both return to the dashboard.
- The welcome appears only when no rules are configured.

## Not verified here

- TalkBack, large font scaling, landscape/tablet layout, other OEM skins or a
  physical-phone UI audit.
- The daily-limit editor's visual layout in a screenshot; it is `FLAG_SECURE`.
- ScreenZen's UI captures are not part of this design record; the observations
  are recorded in `docs/ANDROID_1.1.0.md` and the REA audits.
