# Mac craft pass — 7 November 2026

Captures for the ink-and-paper redesign of the Mac window. The engineering
record is [Mac craft pass](../../docs/MACOS_CRAFT_2026-11-07.md).

The previous captures in `macos-extra-sites-2026-11-07` document the extra-site
feature on the earlier card surface. These are the current surface.

## Captures

| File | State |
| --- | --- |
| `overview-light.png` | Overview, light, 1120 points |
| `overview-dark.png` | Overview, dark |
| `overview-min.png` | Overview at the 760-point minimum, single column |
| `lock-light.png` | Lock, light |
| `lock-dark.png` | Lock, dark |
| `settings-light.png` | Settings, light |
| `settings-dark.png` | Settings, dark |
| `rules-light.png` | Blocking Rules, light |
| `rules-dark.png` | Blocking Rules, dark |
| `setup-evidence.png` | Setup with injected checklist evidence, network setup collapsed |
| `welcome-en.png` | first run, step 1, English |
| `welcome-ar.png` | first run, step 1, Arabic RTL |
| `pause-en.png` | pause window, breathing |
| `private-en.png` | private-windows sheet |
| `arabic-overview.png` | Overview, Arabic RTL |
| `arabic-lock.png` | Lock, Arabic RTL |
| `arabic-rules.png` | Blocking Rules, Arabic RTL |

These are layout captures from `HisnTests/MacUIRefreshTests`, not pixel
assertions. The render host does not carry the app bundle's chosen language, so
runtime `String(localized:)` values stay English in the Arabic captures; the
Arabic layout, mirroring and the catalog/XLIFF check are what these verify.

## What to look at

- **One statement per page.** The serif line is the page's answer, not a
  decorative heading. The tracked label above it is the page name.
- **One surface per group.** Rows share one inset card; there is no card inside
  a card and no coloured icon tile next to every heading.
- **Hairlines over boxes.** Layer rows, settings groups, the words/apps group
  and the sidebar footer are separated by rules, not by more rounded rectangles.
- **One filled action.** Overview has its next step; Lock has its start button;
  Rules has its Save action. The rest are quiet or text actions.
- **Explain is an icon.** No row says "Explain"; the info icon opens the same
  text in a popover.
- **Density.** 28 pt controls, 11–13 pt body and 10.5 pt tracked labels replace
  the previous large, evenly weighted headings and full-width bands.
- **RTL.** Sidebar labels, page headers, layer rows, chips, the lock composer
  and the action bar all mirror. The two-column layout falls back to one column
  at 760 points.

## Verification

- Full Mac suite: **587 tests, 0 failures**.
- `python3 macos/check_localization.py --xliff …`: **815 strings, all with
  Arabic**.
- Captures reviewed in light, dark, English, Arabic RTL and at 760 points.

## Limits

- No physical-Mac install, VoiceOver audit or user study.
- The captures are content-view bitmaps, not the macOS titlebar or toolbar.
- Arabic runtime strings in the host remain English; this is recorded, not
  hidden.
