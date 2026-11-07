# Mac UI — 7 November 2026

A native-surface revision of the 6 November Mac refresh.

The 6 November pass borrowed ScreenZen's layout vocabulary and, with it, its
warm-paper, indigo and navy palette. This pass keeps the principles — one
status first, one decision per screen, quiet cards, short visible copy — and
drops the borrowed colours.

- Neutral Mac surfaces, system-blue accent, system status colours.
- System text throughout; no rounded display face.
- Explanations are not printed on the page. Every page, section, mode, list and
  setup step that has a reason has an **Explain** button with a popover.
- The Overview status card is no longer a deep navy hero.
- The sidebar, buttons, fields, notices and the browser-guard panel follow the
  same surface.

The implementation record is
[Mac UI pass — 7 November 2026](../../docs/MACOS_UI_2026-11-07.md).

## Captures

| File | State |
| --- | --- |
| `before-overview.png` | the 6 November deep-card Overview |
| `overview-light.png` | the new Overview, light, 1120 points |
| `overview-dark.png` | the same page in dark |
| `overview-760.png` | the new Overview at the 760-point minimum |
| `overview-ar.png` | the new Overview with Arabic and RTL layout |
| `setup.png` | Setup with injected checklist evidence |
| `lock.png` | the Lock page, light |
| `lock-dark.png` | the Lock page, dark |
| `rules.png` | Blocking Rules |
| `settings.png` | Settings |
| `welcome.png` | first run |
| `pause.png` | the pause |
| `private-windows.png` | the private-window sheet |

These are layout captures from `HisnTests/MacUIRefreshTests`, not pixel
assertions. The Arabic capture is a layout check; the app bundle's language
is not changed by the test host.

## Verification

`./test.sh macos` ran **580 tests, 0 failures**, and the XLIFF check reported
**741 strings, all with Arabic**. Captures were reviewed in light, dark, Arabic
RTL and at the 760-point minimum.

The optional distraction-site chips and the one-card Rules editor from the same day are captured in [macos-extra-sites-2026-11-07](../macos-extra-sites-2026-11-07/README.md).
