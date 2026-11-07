# Android UI pass — 2026-11-06

This is a **design record**, not a release record. It changes presentation only:
no enforcement layer, permission, stored value, full disclosure or commitment
rule was added, removed or weakened. Hisn Android goes from 1.1.1
(versionCode 28) to 1.2.0 (versionCode 29).

The written record is [docs/ANDROID_UI_2026-11-06.md](../../docs/ANDROID_UI_2026-11-06.md).

## Reference and boundary

ScreenZen (`com.screenzen`) was opened read-only on the owner's Samsung
SM-G990B2 and its Apps, Options, group editor, Launcher and More screens were
captured. No ScreenZen setting, rule, account, purchase or block group was
changed, and no competitor screenshot is redistributed here.

The pass borrows the *structure*, not the product: one line per row with the
answer at the end, section labels without icons, a single filled action per
card, and almost no body copy on list screens. Hisn keeps its own icons,
colours, copy, palette and data model. No ScreenZen code, asset, string or
colour was copied.

## Camera

- Owner's Samsung SM-G990B2, Android 16, 1080 × 2340, 420 dpi override.
- Release-signed direct build (`-PhisnDistribution=direct`) with the owner's
  key, installed in place over 1.1.1. Saved rules and the running protection
  lock were preserved. Android disables an app's accessibility service when the
  package is replaced, so the guard showed its "waiting for the accessibility
  service" screen after the update; the service was re-enabled in Settings →
  Accessibility → Installed apps during verification and the guard reconnected.
  DNS filtering, which stopped with the update, was started again from the app's
  own consent sheet and read back as connected.
- `01`–`03` are the previous build; `04`–`16` are 1.2.0.
- Day/night and language were switched with `cmd uimode` and the per-app locale
  (`cmd locale set-app-locales`), then restored. The phone is Arabic-first with
  German system UI; the English captures use the per-app locale override.

## Screens

| File | Screen |
| --- | --- |
| `01-before-protection-en-dark.png` | Protection, 1.1.1 — icon-tiled hero, three tiles, next step |
| `02-before-rules-en-dark.png` | Rules, 1.1.1 — page title, lock banner, icon-tiled cards |
| `03-before-settings-en-dark.png` | Settings, 1.1.1 — page title, icon-tiled section headers and rows |
| `04-after-protection-en-dark.png` | Protection — amber hero tinted by its verdict, three tiles, DNS card with a state pill |
| `05-after-rules-en-dark.png` | Rules — quiet section labels, compact built-in checks, App protection as one row |
| `06-after-settings-en-dark.png` | Settings — section labels, 22 dp line icons, trailing answers |
| `07-after-protection-ar-dark.png` | Protection in Arabic, RTL |
| `08-after-rules-ar-dark.png` | Rules in Arabic, RTL |
| `09-after-settings-ar-dark.png` | Settings in Arabic, RTL |
| `10-after-protection-en-light.png` | Protection in the light palette |
| `11-after-rules-en-light.png` | Rules in the light palette |
| `12-after-settings-en-light.png` | Settings in the light palette |
| `13-after-welcome-en-light.png` | First-run intro with the smaller mark |
| `14-after-pause-en-light.png` | Voluntary pause with the quiet exit |
| `15-after-settings-about-1.2.0.png` | Settings → **About Hisn**, reading `Version 1.2.0-direct`, the build the captures come from |
| `16-after-protection-ar-dark-running.png` | The same Protection page in Arabic after DNS filtering was started again: three green tiles and a green DNS pill instead of the amber hero |

## What the captures do not show

- Enforcement. Nothing here was re-tested for blocking; the lock, DNS tunnel and
  accessibility guard merely render. `ANDROID_IMPLEMENTATION_STATUS.md` remains
  the authority on coverage.
- Large font scales, tablets and multi-window were not captured. The rows keep a
  weighted text column and a one-line trailing answer with end ellipsis, but
  that is a code argument, not a device check.
- Every state of every row. The DNS card is captured off; its running and
  degraded states share the same layout and were seen while building the pass.
