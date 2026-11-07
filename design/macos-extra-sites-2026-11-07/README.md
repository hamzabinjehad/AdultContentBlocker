> These captures show the earlier card surface. The current surface is the
> [Mac craft pass](../macos-craft-2026-11-07/README.md).

# Mac extra sites and rules — 7 November 2026

Captures for the optional distraction-site chips and the one-card Blocking
Rules editor. The engineering record is
[Mac extra sites and rules](../../docs/MACOS_EXTRA_SITES_2026-11-07.md).

The pass keeps the 7 November neutral surfaces and system-blue accent. It adds
one optional first-run decision and folds the two site editors into one
segmented card. It does not add a daily time budget, a timed disable, a streak
or a score.

## Captures

| File | State |
| --- | --- |
| `extra-sites-en.png` | first run, step 2, no sites chosen, English |
| `extra-sites-ar.png` | the same step in Arabic, RTL |
| `extra-sites-picker-en.png` | chip picker with selected, unselected and a parent-covered site (`ycombinator.com` over `news.ycombinator.com`) |
| `extra-sites-picker-ar.png` | the same picker in Arabic, RTL |
| `rules-light.png` | Blocking Rules, block list, light; empty saved list |
| `rules-dark.png` | the same page in dark |
| `arabic-rules.png` | Blocking Rules in Arabic, RTL; empty saved list |

These are layout captures from `HisnTests/MacUIRefreshTests`, not pixel
assertions. The Rules captures show an empty saved list because the render host
does not seed the app-group list; the picker captures carry the selected and
parent-covered states.

## What to look at

- The first-run sheet has three segments of progress, one decision on screen,
  and a chip row that wraps at a 600-point width.
- Every chip names a site. There is no category label that hides the domains.
- Selected chips use the accent fill and a check; an unselected chip only
  strengthens its hairline on hover.
- The parent-covered chip is visibly disabled; the help and accessibility hint
  name the wider rule.
- The Rules card keeps the segmented list names, the block/allow count and one
  Save/Revert pair. The extra-site row sits above the same list it edits, so a
  chip and a row can never disagree.
- In Arabic the sidebar stays on the right, the chips wrap from the right, and
  each chip's icon is on the leading (right) side. The custom wrapping layout
  relies on SwiftUI mirroring the coordinate space; it does not mirror twice.

## Verification

- `HisnTests/ExtraSitesTests` — 6 data tests.
- `HisnTests/MacUIRefreshTests/testExtraSitesRenderForReview` — these captures.
- `HisnTests/MacUIRefreshTests/testEveryPageRendersForVisualReview` — the Rules
  page in light and dark.
- `HisnTests/MacUIRefreshTests/testOverviewAndLockRenderInArabic` — the Rules
  page in Arabic.
- `python3 macos/check_localization.py` — every key has Arabic.
- Full Mac suite — 587 tests, 0 failures.

## Limits

- No user study, VoiceOver audit or physical-Mac install.
- The Arabic render host keeps runtime `String(localized:)` values in English;
  translation completeness is the XLIFF check's job, not this capture's.
- The Rules page's `Save` path is not exercised by the render test. The
  underlying `SiteLists.save` guard is covered by the existing suite.
