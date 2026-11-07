# Mac UI/UX — 6 November 2026

A visual and interaction pass over the whole Mac window. The old window was a
column of system-grey cards; every control had the same weight, and the status
that mattered competed with a dozen explanations. This pass gives it one calm
surface, one accent and a clear order: the answer first, the evidence one click
away, one action per screen.

These are layout captures from `HisnTests/MacUIRefreshTests`, not a user study,
a VoiceOver audit or a claim that a browser accepted a policy. The Arabic
capture is a layout check: the test host sets SwiftUI's locale, not the app
bundle's language, so runtime `String(localized:)` values stay English in that
image. The XLIFF check owns Arabic completeness.

## Design research

ScreenZen 2.0.0 (build 63) was opened from `/Applications` on this Mac and its
dashboard and block page were observed. The vocabulary taken was the single
status-first layout, observation cards with one figure and one sentence, a
single indigo accent, and a block page that offers one decision at a time. No
ScreenZen code, assets, copy, icons or data model was used, and the third-party
screen capture is not redistributed here. The implementation record is
[Mac UI/UX — 6 November 2026](../../docs/MACOS_UI_2026-11-06.md).

## What the captures show

| File | State |
| --- | --- |
| `before-overview-760.png` | the previous Overview at the 760-point minimum, dark |
| `before-setup-760.png` | the previous Setup page at 760 points |
| `before-lock-760.png` | the previous Lock page at 760 points |
| `before-rules-760.png` | the previous Blocking Rules page at 760 points |
| `before-settings-760.png` | the previous Settings page at 760 points |
| `after-overview-light.png` | the new Overview, 1120 points, light |
| `after-overview-dark.png` | the same page in dark |
| `after-overview-760.png` | the new Overview at the minimum width, dark |
| `after-overview-ar.png` | the same page with Arabic text and RTL layout |
| `after-setup.png` | Setup with an injected checklist: stage cards, expanded steps, network guide |
| `after-lock.png` | the commitment builder: length choices, end band, mode rows, pinned action |
| `after-rules.png` | the list editors and the "your words and apps" cards |
| `after-settings.png` | grouped settings, toggle rows, sensitivity badge |
| `after-welcome.png` | the first-run explanation |
| `after-pause.png` | the private pause, now one deep surface |
| `after-private-windows.png` | the private-window consent sheet |

## The visual system

- **One canvas.** Warm paper (`#F5F2EB`) in light mode, near-black (`#0A0B10`)
  in dark. Cards are white/near-black, a single hairline, and a very soft
  shadow only in light mode.
- **One accent.** Indigo `#5C5CF5`; state is never colour alone — every chip
  carries a word and a symbol, and the protection ring carries a ratio.
- **The deep card.** The Overview's answer and the whole pause use the same
  navy card with two indigo glows. It is the one place a screen raises its
  voice.
- **Rounded type.** Headings and numbers are SF Pro Rounded; body copy stays
  the system face. Numbers are monospaced so a countdown does not jitter.
- **One primary action.** The filled button is the action the state calls for.
  Everything else is a quiet button, a text link or a row.

## Interaction changes

- The sidebar is custom: indigo selection, a hover state, a lock countdown,
  and a persistent status footer. `⌘1`–`⌘5` still change page.
- Every page starts with a readable title and purpose instead of a small
  toolbar subtitle.
- The content column is capped at 880 points and centred, so a full-screen
  window does not stretch a paragraph across the display.
- Changing page resets the scroll position; the previous shell kept the old
  offset.
- The Lock page hides the toolbar's "Start a lock…" action while you are
  already on it.
- The pinned lock bar shows why it is disabled and uses a neutral disabled
  fill rather than a faded version of the enabled accent.

## Verification

- `xcodebuild … test -scheme Hisn` — **580 tests, 0 failures**; the UI render
  tests add layout captures, not pixel assertions.
- `xcodebuild -exportLocalizations` + `macos/check_localization.py` — **721
  strings, all with Arabic**.
- Captures were reviewed in light and dark, at 1120 and 760 points, and in
  Arabic RTL.

## Not claimed

- No user study, measured task time, screen-reader audit or physical-Mac
  acceptance.
- No change to protection policy, lock rules, filter behaviour, consent copy or
  the commitment model in this pass.
- The window captures come from the test host. Native controls and the window
  chrome are composited by the system, so a few system controls may look
  inactive in a capture where the test window was not key.
