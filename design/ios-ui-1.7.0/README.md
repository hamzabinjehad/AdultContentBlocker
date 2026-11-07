# iOS 1.7.0 — capture record

Layout evidence for the iPhone/iPad 1.7.0 pass, which replaces the status
dashboard with four places — Today, Apps, Practice and More — and gives the
pause rule its own screen. These are renders from the hosted test harness
(`UIRenderTests`, one point per pixel), not a user study, a screen-reader audit
or a pixel snapshot test.

This simulator has no Family Controls entitlement and no configured rule, so the
app honestly shows the layer checks as unavailable, the plan at 0 of 3, no saved
app rule, and a pause flow at 0 of 3 opens. Compare the layout, not the
verdicts.

| File | State |
| --- | --- |
| `after-today.png` | Today, English, light: the protection card, the week's practice, the Apps card and the practice card |
| `after-today-dark.png` | The same tab in dark mode |
| `after-today-ar.png` | The same tab in Arabic RTL; the ring shows `0/3` and the week letters are Arabic |
| `after-today-ipad.png` | iPad (1024×1366), light |
| `after-today-ar-ipad.png` | iPad, Arabic, light |
| `after-apps.png` | Apps: the status pill, the empty rule card, the browser/VPN guard row and the two tiles |
| `after-apps-dark.png` | Apps, dark |
| `after-apps-ar.png` | Apps, Arabic RTL |
| `after-practice.png` | Practice: the breath card, the streak and the week dots |
| `after-practice-dark.png` | Practice, dark |
| `after-more.png` | More: the protection summary with named layer chips, the three setup steps, allowed sites |
| `after-more-dark.png` | More, dark |
| `after-more-ar.png` | More, Arabic RTL |
| `after-pause-flow.png` | The pause handoff after the ten-second ring: the finished breath, the opens as dots and the enabled **Open for 5 minutes** |
| `after-pause-flow-dark.png` | The same screen with the system in dark mode; it is byte-identical to the light one because the pause screen uses the shield's fixed ink colours, not the system appearance |
| `after-pause-flow-ar.png` | The same screen in Arabic RTL, to check the opens line's bidi order |
| `after-rule-editor.png` | The rule editor sheet: selection, the four modes with one honest line each, and the pause numbers |
| `after-rule-editor-dark.png` | The editor, dark |
| `after-protection-checks.png` | The protection drill-in (unchanged from 1.6.0): Safari, DNS and Screen Time checks |

The captures were re-rendered after four fixes the first set showed: the ring
now shows a ratio (`0/3`) instead of a translated sentence that reorders under
the Arabic bidi algorithm; the week letters follow the UI language; More's
layer chips carry their names instead of icon-only pills; and the editor's
leading action says **Cancel**, not **Done**.

The palette is warm paper in light mode and near-black in dark, with one deep
navy card that matches the shield. Body text is 4.5:1 or better on its surface,
and on-ink text uses the light accent, calm and ember values rather than the
light-mode ones.
