# Mac pause — design captures, 3 November 2026

These are deterministic renders from the macOS test host, saved by
`PauseTests.testPauseRendersInBothLanguages` and
`PauseTests.testOverviewRendersThePauseEntryInBothLanguages`. They are the view
bitmaps, not physical-display screenshots, so they show layout and copy without
window chrome or desktop context.

| File | State |
| --- | --- |
| `overview-dark-en.png` | Overview with the pause row, English |
| `overview-dark-ar.png` | Overview with the pause row, Arabic RTL |
| `pause-breathing-en.png` | Breathing stage, English |
| `pause-breathing-ar.png` | Breathing stage, Arabic RTL |
| `pause-needs-en.png` | Needs stage, English |
| `pause-needs-ar.png` | Needs stage, Arabic RTL |

The captures were taken at 460×640 for the pause window and 880×1000 for
Overview on a Mac in dark appearance. They are not a user study, a screen-reader
audit, a light-mode review or a measured usability result. Arabic is rendered
through SwiftUI's locale and layout direction; the strings in the catalogue and
the compiled Arabic bundle are checked separately by
`macos/check_localization.py`.
