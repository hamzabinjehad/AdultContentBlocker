# Hisn Mac 1.1.10 — one title, one action, one statement of the rules

Released 3 November 2026 as a development candidate. Unsigned and unnotarized;
no Developer ID package, notarized build, physical-Mac acceptance or VoiceOver
audit is claimed.

## Why

1.1.9 made the state honest and moved the evidence behind a disclosure. Walking
the five pages afterwards showed a different problem: **the same content, said
more than once.**

- Every page printed its name twice — once in the window title bar, once as a
  large in-page header with the same subtitle. Setup printed it a third time as
  the first section heading.
- Overview put its one action *below* the layer evidence, so with the details
  open (which is what a missing layer forces) "Start a lock…" sat at the bottom
  of the card, under three explanations.
- Lock stated the fixed-commitment rule twice, in two different cards. Turning
  the fixed commitment off left the paragraph on screen anyway, now describing
  a mode that was no longer selected.
- Blocking Rules opened with a three-paragraph browser guide; the lists a person
  came to edit were below the fold.

## Design research with REA

REA 6.4.0 was already installed in a temporary directory from npm with
installation scripts disabled. Its provider-free operations were used on this
machine:

| Subject | Operation | Result |
| --- | --- | --- |
| `/Applications/ScreenZen.app` 2.0.0 (build 63) | `inspect-artifact` | SHA-256 `3d71a366…f7547`, directory artifact, one completed inventory substep |
| Screen Zen window `47074` (pid 24378) | `capture-native-ui-scenario` | 48 AX nodes, one digested screenshot, one `wait` step |

The observation is design vocabulary only: **one title per pane, cards as rows
with a leading icon, a right-aligned count and one chevron, prose kept behind a
drill-in.** Hisn did not reuse Screen Zen's code, assets, visual identity or
copy, and the capture is not redistributed. The AX tree was unusually bare —
most SwiftUI controls exposed no title or value — so the screenshot carried the
layout evidence, not the accessibility tree.

## What changed

| Page | Before | After |
| --- | --- | --- |
| All pages | title bar + large header + subtitle | title bar only; content starts immediately |
| Overview | status → summary → chips → expanded evidence → action | status → summary → chips → **action** → evidence |
| Overview | commitment card below the setup card | commitment beside the protection card |
| Overview | full readiness card repeating the missing-layer message | one row: headline, summary, step count, chevron → Setup |
| Setup | readiness card + "Protected Setup" section with a second verdict | one card: readiness, progress bar, "2 of 12 steps done", next step |
| Lock | fixed-commitment paragraph twice, once stale | stated once, follows the mode toggle; duplicate card removed; one action pinned to the window's bottom edge with a live consequence line |
| Blocking Rules | guide, then lists | lists, then guide |
| Your sites | two large text boxes, domains only visible as text | per-domain rows with Remove, an inline add field, a chip strip of what is blocked, and bulk paste in a disclosure |
| Settings | a flat column of headings and dividers, checkbox toggles | one card per group, with trailing switches |

No rule, lock, filter, browser-bridge or protection-layer behaviour changed. Two
user-facing strings were added (`Add`, `Paste a list`), both with Arabic; the
catalog is 601 strings. Xcode marked five strings that only the removed blocks
used as `stale` in the catalog; their Arabic is kept. The add field's
placeholder is a verbatim `example.com` held in a String property, so it does
not enter the catalog as translatable copy.

## Files

| File | Change |
| --- | --- |
| `macos/Hisn/ContentView.swift` | page chrome, Overview order, Setup card, Lock mode text, Rules order, row-based site lists |
| `macos/Hisn.xcodeproj/project.pbxproj`, `macos/generate_xcodeproj.py` | 1.1.10, build 21 |
| `macos/Hisn/Localizable.xcstrings` | two new strings with Arabic; five marked stale |
| `design/macos-ui-1.1.10/` | before/after captures and their README |

## Verification

- `./test.sh macos` — **526 tests, 0 failures**; 597 strings, all with Arabic.
  Four new `EditorSummaryTests` cover the row add field and Remove (normalization, duplicates, invalid text); no layout snapshot test exists.
- Both builds were installed in place on the owner's Mac and the five pages were
  captured: 1.1.9 (before) and 1.1.10 (after), same window width, dark mode.
- The captures are real windows, not SwiftUI renders: they show this Mac's
  actual state (ad-hoc filter off, hosts list on, extension connected).
- The Lock page's choices now live in a `LockDraft` observable, so the form and
  the pinned bar read the same values; a field change still clears the
  acknowledgement, and the bar's button follows it.

## Limits

- Screenshots show one window width (760 pt, the minimum) in dark mode. Wide
  windows, light mode, Arabic RTL layout, VoiceOver order and keyboard traversal
  are not measured by this pass.
- Removing the large in-page header trades a visual anchor for vertical space;
  whether that reads better is a judgement from five pages, not a user study.
- The design comparison with Screen Zen is an observation of one window, not a
  competitive benchmark, and it says nothing about Screen Zen's blocking.
