# Screen Zen feature audit: what Hisn can take — 3 November 2026

A walkthrough of the other Mac app on this machine, recorded as design input for
Hisn 1.1.10. It is **not** a security assessment, a benchmark, or a claim about
how Screen Zen behaves under enforcement.

## Method

- Screen Zen 2.0.0 (build 63) was opened read-only and every destination was
  visited: **Websites**, a websites detail, **Halo**, **Daily goal** (the two
  onboarding steps it was in), the **More** menu, and the **Settings** window
  (General, Browsers, Advanced, About) including its "Settings locked — wait out
  45 s" gate.
- No Screen Zen setting, rule, account or stored data was changed. The
  screenshots stay in temporary storage and are not redistributed; only the
  observations below are recorded.
- The Hisn side is 1.1.10 (build 19) at the time of this note, 526 tests.

## What Screen Zen exposes

| Surface | What it shows | Pattern worth noting |
| --- | --- | --- |
| Sidebar | Websites, Halo, Daily goal; a **Rules** group with a chip and "0 left" | Grouping by job, not by settings category |
| Websites | Two tiles (**Disable blocking · 2 mins**, **Lock settings · On/Timer**), a dismissible rules banner with domain chips and one summary sentence | State first, one line of consequence, details behind a drill-in |
| Websites detail | Segmented Block/Allow, add field, rows with per-row state and delete, action chips (Advanced rule, Adult websites, Test), then Apps / Daily limit / Zen screen / Limit rows | Rows are objects; each has one action and one chevron |
| Halo | Empty state: headline, two-line explanation, one primary "See how halo works", one secondary "Pair halo" | Empty states carry the next action, not a manual |
| Daily goal | Progress bar, a large −/4h/+ stepper, a live line ("Nothing gets blocked"), bottom-pinned **Continue**; step 2 is an app grid with icons, a dashed **+ Add app** tile and website chips | One decision per screen; the consequence is stated before the commitment |
| More menu | Test a website, Report a bug, Share feedback, Check browser permissions | Utility actions kept out of the main flow |
| Settings | Its own window with General/Browsers/Advanced/About; a modal **Settings locked — Wait out 45 s** before any change | A cool-down between the impulse and the change |

## Take, adapt, reject

### Taken in 1.1.10

| Idea | How it landed |
| --- | --- |
| Rows as objects, one action each | Site lists are per-domain rows with Remove, an inline add field and a count |
| A chip strip summarising the rules | The blocked-sites strip above the lists, read from the same edited text |
| State on the right, label on the left | The three Settings toggles are trailing switches, matching the switches already used on Rules |
| Grouped cards instead of one long form | Settings is one card per group |

### Candidate next

| Idea | Why it fits | Cost |
| --- | --- | --- |
| **Bottom-pinned primary action with a consequence line** on the Lock page | The Lock page's button sits after a long column; Screen Zen pins it and states what the choice does before you commit. Hisn could say "This would enforce 2 of 3 layers" / "Nothing is enforcing this yet" under the button | Small: move the button into a safe-area inset and derive one line from `ProtectionStatus` |
| **"Would this be blocked?"** check | Screen Zen's *Test a website*; Hisn has no in-app way to ask. On the Mac it can run the same `SiteLists` + blocklist lookups the app already has and answer, with the reason (listed, custom block, allowed, strict mode) | Medium: a small sheet, no new data |
| **Empty states with the next action** | Hisn's Setup with 0 steps done is a long card of prose; a first-run state could be headline + one primary + one secondary | Small, but needs design copy in English and Arabic |
| **Cool-down before loosening settings** | Matches Hisn's "weakening waits" doctrine. It would apply only to a change that loosens, while unlocked — a lock already refuses those outright | Medium: needs a product decision and a timer the person can see |

### Rejected

| Idea | Reason |
| --- | --- |
| **Disable blocking · 2 mins** | A one-click pause contradicts the commitment model and the Lock page's promise. Hisn's pause changes no rule; the block stays |
| **Halo proximity** | Needs Bluetooth pairing, an account and cloud state; conflicts with on-device-only |
| **Daily screen-time goal and "0 left" counters** | Streaks and scores are deliberately not Hisn concepts; Android's per-app limits are a different feature |
| **Account, sync, paywall** | No account by design |

## Test coverage today, and the honest gaps

`./test.sh macos` runs 526 tests across 20 files; the heaviest are
`BrowserGuardTests` (79), `BlocklistStoreTests` (65), `PolicyAuthorityTests`
(44), `SetupChecklistTests` (41) and `ProtectionStatusTests` (31). Guarded
behaviour, lock policy, lists, setup state, DNS probes, the pause tally and both
languages are covered.

What has **no** automated test:

- **Layout and RTL.** No snapshot or accessibility-tree assertion. Every UI pass
  so far was judged from screenshots — including this one.
- **Light mode and wide windows.** Captures are dark, 760 pt.
- **VoiceOver order** after the header removal and the new rows.
- **The chip strip and row UI as views.** Their add/remove logic is tested
  (`EditorSummaryTests`); the rendering is not.
- **End-to-end enforcement**: no test drives a real browser, the filter, or the
  extension together on a signed build.
- **Performance** of the filter and the page checks.

The cheapest way to close the first four is a small UI-test target that opens
each page at two widths, in both appearances, in English and Arabic, and asserts
the accessibility tree — not pixels. That would make future UI passes verifiable
rather than photographed.

## Recommended order

1. Lock page: bottom action bar and a live consequence line.
2. "Would this be blocked?" check.
3. UI-test target for the five pages (two widths, two appearances, two languages).
4. Settings cool-down, if the product decision is yes.
