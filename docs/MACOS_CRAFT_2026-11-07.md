# Mac craft pass — 7 November 2026

A visual and interaction pass over the Mac window after the
[extra-site feature](MACOS_EXTRA_SITES_2026-11-07.md). The feature work was
right; the surface still read like a generic card dashboard: one rounded box
per control, a coloured icon tile beside every heading, an "Explain" label on
almost every row, system blue everywhere, and pages that were mostly empty
space with small text. This pass changes the surface, not the policy.

The design direction is **ink and paper**: warm neutral surfaces, a deep ink
blue accent, one serif statement per page, compact Mac-sized controls,
hairlines instead of boxes, and one information affordance per section instead
of one per row. It is closer to Things 3 and a native System Settings form than
to a web dashboard.

## References

These are design references, not code or asset sources. No competitor code,
palette, font, illustration or string table was copied.

- [Designing for macOS — Apple HIG](https://developer.apple.com/design/human-interface-guidelines/designing-for-macos)
- [Things 3: Beauty and Delight in a Task Manager — MacStories](https://www.macstories.net/reviews/things-3-beauty-and-delight-in-a-task-manager/)
- [Linear design reference](https://github.com/opencoworkai/open-codesign/blob/ca833517a009bb194b95c7620005a8dc8b57bf88/apps/desktop/resources/templates/brand-refs/linear/DESIGN.md) — density, hairlines, one accent
- [AI Design Slop — SmoothUI](https://raw.githubusercontent.com/educlopez/smoothui/42896412d80f688787a4b07156e983a31e9de4a6/apps/docs/content/blog/ai-design-slop.mdx) — the interchangeable card-grid look this pass rejects
- The ScreenZen screenshots supplied with the request — the choice architecture
  (one decision per screen, named sites, visible progress), not its screen-time
  budget or timed-disable model.

## What changed

### The window

- **Palette.** Warm paper canvas (`#F4F1EB` light, `#151412` dark), white/charcoal
  surfaces, ink text, a deep ink-blue accent, and muted green/ochre/red status
  colours. No purple, gradient, glass, glow or default system blue.
- **Type.** SF Pro for controls and body; New York serif for the one statement
  at the top of each page and for the large figures. Section labels are 10.5 pt
  uppercase with 0.8 pt tracking (tracking is disabled in Arabic, where it
  breaks joins). Body is 13 pt, captions 11 pt.
- **Sidebar.** A bilingual `Hisn / حصن` lockup, three quiet group labels,
  selection as a soft accent wash plus a two-point accent rule, and a status
  footer that is a line rather than another box.
- **Toolbar.** The site checker is an icon-only button; the lock action is the
  one filled toolbar control.
- **Page header.** A tracked page name, a blue rule, a serif statement and one
  short caption. The page name is no longer a generic noun heading with a
  paragraph behind an Explain button.
- **Explain.** The text "Explain" is gone from the interface. The remaining
  help is a 12 pt info icon with a popover, so it supports the control instead
  of competing with it.

### Overview

- The old three-card stack is now a **two-column composition**: one layer sheet
  on the reading side and a narrow rail containing the next step, today's
  numbers and two tools.
- The layer table shows the layer name, the evidence line and the state word
  in one row. Colour is a 6 pt dot and a word, never the only signal.
- The page statement is the actual condition ("Partly protected"), not the
  word "Overview".
- Today shows blocked connections when the filter reports them, pauses today,
  and the next daily lock — with **Set a daily lock** when there is none.
- A privacy line closes the rail: matching stays on this Mac, with no account
  and no browsing log.

### Lock

- The five equal-width preset blocks are now compact chips; the chosen length
  is a serif figure with the wall-clock end beside it, not a full-width blue
  band.
- The two modes have one-line captions under the title; the longer consequence
  is in the info popover.
- The consent sentence is a checkbox row, not a card.
- **Every day** is collapsed to a toggle and a one-line summary. Editing the
  window expands the times, the strict switch and Save; turning the schedule on
  expands it automatically.
- The pinned bar is a slim footer with the enforcement line, the
  "cannot be shortened" line and one start button. The disabled state keeps a
  hairline and readable text instead of a pale invisible fill.

### Setup

- The page gets the editorial header, but the real change is **Network
  protection** is collapsed to an optional disclosure, so the first screen is
  the protection state and the steps, not a router form.
- **Check again** belongs to the status, not to a floating row after the stage
  list.
- Stage cards keep their existing behaviour; the page is shorter and the
  network setup no longer dominates the first screen.

### Rules and Settings

- Rules uses the same tracked section labels and one grouped surface for the
  websites card and one for words/apps. The words and apps Save row is now
  inside their group instead of floating below it.
- Settings is a native grouped form: each section is a tracked label plus one
  inset surface, not a card per control. The mislabelled "Visual checks" card
  is now **Browser extension → Visual checks in the extension**, and the help
  links sit in that group.
- Details is a grouped disclosure at the bottom rather than a visible technical
  block.

### Welcome and pause

- The welcome sheet keeps its three decisions but uses the page header, layer
  details visible on the page, a privacy line and the progress bar. The serif
  statement gives the first run the same voice as the window.
- The pause window uses a serif statement, a circular breath progress ring
  instead of a linear bar, three small cycle marks, and a footer with the day's
  count and one close action.

## What did not change

- No protection rule, lock length, consent sentence, allowance rule, partner
  path or commitment deadline changed.
- No browsing-time history, streak, score, timed disable or passcode unlock was
  added.
- No network call, account, telemetry or new store was added.
- The module boundaries and all tests still exercise the same data and policy.

## Anti-slop acceptance bar

The pass was checked against these rules, not only eyeballed:

- No page uses a coloured rounded-square icon tile beside a heading.
- No page has a card inside a card.
- No row carries a text label saying "Explain".
- Every page has at most one filled primary button in the content area.
- A page title is a sentence or a state, not a label like "Overview".
- No page-level gradients, frosted glass, neon glow or decorative shadow. The pause orb keeps a radial breath wash because it is the breathing signal, not page decoration.
- The same surface, radius, type scale and spacing tokens are used on every
  page.
- 760-point, 1120-point, light, dark and Arabic RTL layouts were rendered and
  inspected.

## Verification

- Full Mac suite: **587 tests, 0 failures**.
- XLIFF check: **815 strings, all with Arabic**.
- Captures below are layout captures from `HisnTests/MacUIRefreshTests`, not
  pixel assertions. Reviewed at 1120 and 760 points, light and dark, and in
  Arabic RTL.
- No physical-Mac install, VoiceOver audit or user study is claimed.

## Limits

- The render host does not carry the app bundle's chosen language, so runtime
  `String(localized:)` values remain English inside the Arabic window; the
  catalog and XLIFF check own translation completeness, and the Arabic
  captures check layout direction and mirroring.
- The captures show the content view, not the macOS window titlebar or toolbar.
- The redesign does not claim new macOS 26 materials or a Liquid Glass look;
  it uses flat surfaces and hairlines.
- No signed artifact is produced by this pass.

## Captures

[design/macos-craft-2026-11-07](../design/macos-craft-2026-11-07/README.md)
