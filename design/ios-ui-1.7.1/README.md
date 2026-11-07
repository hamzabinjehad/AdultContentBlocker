# iOS 1.7.1 — capture record

Layout evidence for the one screen 1.7.1 adds: the quiet-hours editor with
weekday chips. Renders are from the hosted test harness (`UIRenderTests`, one
point per pixel), not a user study or a pixel snapshot test.

This simulator has no Family Controls entitlement and no configured rule, so
the editor honestly shows **0 selected** and a disabled save. Compare the
layout, not the verdicts.

| File | State |
| --- | --- |
| `after-quiet-hours-editor.png` | The editor on an iPhone in English: four mode rows, then Days with seven chips and a Day presets menu, then the time window. The Details card continues below the fold. |
| `after-quiet-hours-editor-dark.png` | The same screen in dark mode |
| `after-quiet-hours-editor-ipad.png` | iPad (1024×1366): the whole editor in one view — day chips, the scope line, the time window and its presets, the midnight note and the save area |

The iPad capture caught two defects that the phone render hid: the time-preset
and day-preset menus were both labelled **Presets**, and the editor stretched
to the full iPad width. The menus are now **Time presets** and **Day presets**,
and the editor keeps the app's readable column.
