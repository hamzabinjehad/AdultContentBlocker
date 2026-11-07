> The feature behaviour below is current. The visual surface was replaced
> the same day by the [Mac craft pass](MACOS_CRAFT_2026-11-07.md); current
> captures are in
> [`design/macos-craft-2026-11-07`](../design/macos-craft-2026-11-07/README.md).

# Mac extra sites and rules — 7 November 2026

A feature and UX follow-up to the [neutral-surface pass](MACOS_UI_2026-11-07.md).
The reference screenshots were ScreenZen's first run, its website picker and
its rules editor. This pass takes the **choice architecture** — one decision per
screen, named sites rather than categories, a visible count, one list surface —
and adds it to Hisn. It does not import the parts of that model that weaken a
commitment: daily time budgets, timed disabling, open counts, streaks or a
passcode unlock. Those remain out for the reasons recorded in the
[extension UI research](EXTENSION_UI_REFRESH_2026-11-04.md) and the
[Android ScreenZen comparison](ANDROID_REA_SCREENZEN_2026-11-03.md).

Nothing here changes the published adult-content list, the lock, the partner
path, the browser guard or the private-window policy.

## The gap

- First run asked two questions, then dropped the person on a twelve-step
  technical Setup page. There was no personal decision to make, and no quick
  way to add the ordinary distraction sites that bring people to a self-control
  app in the first place.
- Blocking Rules printed the entire block editor and the entire allow editor
  one above the other. The short, rarely used allow list took the same height
  and the same visual weight as the block list, and the page read as a form
  rather than two lists.
- Adding a common site meant typing it into a field. There was no named,
  recognisable starting point.
- The site checker was a separate row below the lists, far from the list it
  answers questions about.

## What changed

### First run is three decisions

`WelcomeView` now has three steps and a three-segment progress bar:

1. **How Hisn works** — the existing three layers.
2. **Block distractions too?** — twelve recognisable sites (`youtube.com`,
   `reddit.com`, `x.com`, `instagram.com`, `facebook.com`, `tiktok.com`,
   `twitch.tv`, `netflix.com`, `news.ycombinator.com`, `amazon.com`,
   `pinterest.com`, `linkedin.com`). Every chip names the site; there is no
   "social media" category hiding what will be blocked. The step is optional,
   says so, and says adult-content protection runs either way.
3. **One gap to close** — the existing private-window decision.

Each chip writes through the same guarded `SiteLists.save` the Rules editor
uses. There is no hidden first-run draft: a chip that looks selected is already
in the list the filter and the extension read. A refusal (a lock or a managed
policy) leaves the chip unselected and shows the reason.

The footer's **Not now** on the private-window step now continues to Setup
instead of closing the pass, so skipping that one decision no longer skips the
whole first run.

### Blocking Rules is one list card

`SiteListsSection` is now:

- a **Block list / Allow list** segmented control;
- **Always block these** or **Always allowed** as the selected list's heading;
- one shared **Save/Revert** pair for the pair of lists, as before;
- a **Check a website** button in the card;
- per-list empty copy: "No extra sites. The published list still applies." and
  "Nothing allowed beyond the published list.".

Both drafts are kept in memory, so switching tabs loses no edit and the Save
button still reflects the pair. The permit/refuse direction is unchanged: while
a lock or managed policy is active, adding a block and withdrawing an allow
work; the opposites do not.

In the block tab, the twelve chips sit above the list:

- plus means not in the list, check means in the list;
- a site covered by a wider hand-written rule (`ycombinator.com` over
  `news.ycombinator.com`) is shown as blocked and disabled; the chip never
  removes the parent rule and unblocks its siblings;
- the count is "added / 12";
- the chip list and the list rows are the same data, so a chip cannot stay
  selected while its domain is absent from the list below.

### Wording

The action labels now say **block list** (`Add to block list`,
`Remove from block list`) while the list heading keeps the established
**Always block these**. The site checker, the chips and the Rules page use the
same words. The `Always allowed` heading and the allow-list explanations are
unchanged.

## Deliberately not copied

- **A daily screen-time goal and limit.** Hisn tracks no browsing time, keeps no
  per-app usage record on the Mac, and has no account to sync one to. A time
  budget would need a new on-device history and a new weakening surface.
- **A timed "disable blocking" control.** The pause is a breathing exercise and
  does not unlock anything; the lock's only early exit stays partner-approved
  or 48 hours. A quick-disable timer would be the bypass the threat model is
  built to avoid.
- **Open counts, streaks and scores.** The Mac already records a local pause
  tally; turning it into a target or a streak is a motivational claim, not a
  protection guarantee.
- **ScreenZen's visual identity, code, assets or copy.** The chips, cards and
  progress bar use Hisn's existing system surfaces, system blue and Arabic
  catalog.

## Safety and policy invariants

- No protection rule, consent sentence, allowance rule, partner path or
  commitment deadline changed.
- Extra sites are stored in the existing shared app-group `customBlocks` list,
  on top of the published list. There is no new store, network call, account or
  telemetry.
- The chip's removal path is the same `SiteLists.save` loosening guard as the
  list editor. The first-run path passes the lock/managed state as well.
- A parent rule is never removed by a chip.
- The block/allow asymmetry and the "adding works during a lock" rule are
  unchanged.

## Verification

- `HisnTests/ExtraSitesTests` — six tests: unique normalised suggestions,
  add/remove, preserving other entries, ignoring an unknown domain, parent
  coverage not being removed, and the added count.
- `HisnTests/MacUIRefreshTests/testExtraSitesRenderForReview` — first-run step 2
  in English and Arabic, and the chip picker with selected, unselected and
  parent-covered chips in both directions.
- `HisnTests/MacUIRefreshTests/testEveryPageRendersForVisualReview` and
  `testOverviewAndLockRenderInArabic` — the Rules page is in the page set and
  the Arabic set.
- `python3 macos/check_localization.py --xliff …` — every string used by the
  compiled app has Arabic; the catalog has 765 strings.
- Full Mac suite — 587 tests, 0 failures.
- Captures reviewed in light, dark, English and Arabic RTL at 1120 points; the
  first-run sheet at 600 × 560.

| Check | Result |
| --- | --- |
| `xcodebuild … build` | passed |
| `HisnTests/ExtraSitesTests` | 6 passed |
| UI render tests | passed |
| XLIFF Arabic check | 765 strings, all translated |
| Full `xcodebuild test` | 587 passed, 0 failures |

## Limits

- No user study, task-time measurement or VoiceOver audit.
- The Arabic render host does not carry the bundle's chosen language, so runtime
  `String(localized:)` values stay English inside the Arabic window; the
  catalog and XLIFF check own translation completeness, and the Arabic capture
  owns layout direction.
- The Rules capture shows an empty saved list because the test host does not
  seed the app-group lists. The selected and parent-covered chip states are
  captured from the picker directly.
- The chips are a convenience over the existing custom block list. They do not
  add coverage of a site the published list does not name beyond that one
  domain, and they do not replace the browser extension or the filter.
- No signed artifact or physical-Mac install is claimed. This is a source and
  test pass.

## Files

- `macos/Hisn/ExtraSites.swift` — curated list, coverage and toggle rules.
- `macos/Hisn/ExtraSitesView.swift` — chip picker and wrapping layout.
- `macos/Hisn/WelcomeView.swift` — three-step first run.
- `macos/Hisn/ContentView.swift` — segmented Rules card and card-level site
  checker action.
- `macos/Hisn/Localizable.xcstrings` — English keys and Arabic values.
- `macos/HisnTests/ExtraSitesTests.swift`,
  `macos/HisnTests/MacUIRefreshTests.swift` — data and layout coverage.
- `design/macos-extra-sites-2026-11-07/` — captured states.
