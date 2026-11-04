# Hisn iPhone and iPad 1.5.0 — a calmer overview and a guided setup

Released 4 November 2026 as a development candidate. Simulator-tested only; no
Apple team, Family Controls entitlement, signed build or physical-device
acceptance is claimed.

## Why

1.4.0 made the shield honest, but the app around it still read like a
diagnostic form. The overview repeated the protection plan: a hero, a separate
plan section, a full commitment form and a sites section all asked for space.
Setup stacked the status, the plan, the setup order and every advanced layer in
one long list, so the next action sat above a wall of evidence. Settings was a
flat form where the privacy note, visual-check instructions and category copy
came before the controls. Nothing was wrong with the rules; the surface made
them harder to scan than they needed to be.

This pass borrows the vocabulary the 1.3.0 audit recorded from ScreenZen and
comparable apps — status first, rows as objects, one action per card, details
behind a drill-in — without copying their quick-unlock, streak, account or
visual identity.

## What changed

### Overview

- **One card stack instead of one long list.** The hero answers "is protection
  on?", the pause card offers the one positive action, the plan card shows
  `0 of 3` with a progress bar and compact layer rows, the lock card shows
  whether saved rules are frozen, and the rules card shows the two lists Hisn
  owns. The duplicate `0/3` and the full readiness section are gone.
- **Direct editing.** **Set a lock** / **Manage lock**, **Blocked sites** and
  **App limits** now open their editors in sheets. The overview no longer makes
  a person find the right step on Setup to change a saved rule.
- **Pause stays one tap away.** The pause card shows today's count only when the
  stored tally belongs to today; the toolbar wind button remains on every tab.
- **The full commitment form moved to Setup.** A duration picker and an
  acknowledgement no longer sit in the middle of the status page, where they
  could be changed by accident.

### Setup

- **Three steps with one progress card.** `0 of 3 steps done`, the live setup
  state, the issues and the last check time share one card. Each step is a card
  with a status pill: turn on a blocking layer, start the commitment, seal a
  passcode.
- **Drill-ins keep the controls.** Blocking layers, app limits plus commitment,
  and the passcode/removal sections each open on their own page. The main Setup
  page stays a short plan, not a form with every switch at once.
- **The checks card** keeps the four configuration rows and the scope caveat
  after the steps, so evidence never sits above the next action.
- **App limits reads as a decision.** The three modes — always block, daily
  combined budget and daily quiet hours — are visible rows with icons and a
  checkmark instead of a menu picker; the chosen items and their count stay
  above the rule.
- The DNS, family, router and Safari-extension sections keep their existing
  instructions and honest limits inside the blocking-layers page.

### Settings

- **Grouped cards.** Language, the Safari extension, gray screen, privacy and
  about each become one card with a leading icon, a short summary and a trailing
  control or link. The full privacy note and coverage copy remain one disclosure
  down. The gray-screen instructions keep their numbered Apple steps.
- **Privacy links stay visible.** The privacy policy, support page and offline
  licenses/sources are rows inside the privacy card.

### Accessibility and layout

- Cards use `secondarySystemGroupedBackground` and system separators, so light
  mode, dark mode and Increase Contrast follow the system. The tinted status
  wash stays under 12% and the existing 4.5:1 status colours are unchanged.
- At accessibility text sizes the status header, step cards, pause card, lock
  card and rule rows stack the icon above the text; the plan and check rows
  stack their status pill below the title. The setup progress count moves below
  its bar. No new fixed-width labels were added.
- Status rows keep a combined VoiceOver reading. The card icon is hidden from
  VoiceOver; the adjacent text carries the meaning. Tap targets are at least
  44 pt.

## Design research

The full record is [the REA audit](REA_AUDIT_IOS_2026-11-04.md). In short:

- REA 6.8.0 ran provider-free `inspect-artifact` on the installed ScreenZen
  2.0.0 (build 63) and on the built 1.4.0 `HisnMobile.app`. The ScreenZen
  artifact hash matches the previous audits, so this is the same observed
  build.
- The observation is design vocabulary only. No ScreenZen code, assets, visual
  identity or copy was reused. REA cannot establish Swift/SwiftUI semantics,
  ScreenZen's enforcement behaviour or any iOS-specific ScreenZen behaviour.
- Web research covered ScreenZen's App Store listing, independent reviews and
  comparisons with Opal, one sec and Jomo. Those pages describe product
  behaviour, not this build's runtime.

### Taken, adapted, rejected

| Idea | Hisn implementation |
| --- | --- |
| Status first, one primary action | Hero card with layer chips and one action; setup progress card leads with the state |
| Rows as objects with one action | Plan rows, rule rows and step rows each carry one state and one destination |
| Drill-in instead of a wall of form | Three Setup step pages and three overview editor sheets |
| Grouped settings cards | Language, Safari extension, gray screen, privacy and about cards |
| A calm pause surface | The 1.3.0 pause is retained; its count is shown on the overview only for today |

Deliberately not copied: quick unlock or time-boxed disabling, daily opens as a
protection claim or streak, location/Halo unlocking, a cloud account or
cross-device state, and ScreenZen's visual identity. Hisn's commitment still
freezes saved rules and its pause changes no rule.

## Files

| File | Change |
| --- | --- |
| `ios/Hisn/HisnUI.swift` | New shared card, icon badge, status pill, progress bar, adaptive row and surface tokens |
| `ios/Hisn/OverviewCards.swift` | New plan, warning, lock and rules cards |
| `ios/Hisn/SetupViews.swift` | New setup status card, three step cards, drill-in pages, Safari parts and removal section |
| `ios/Hisn/MobileRootView.swift` | Overview/Setup/Settings rebuilt around cards; direct editor sheets; setup step copy |
| `ios/Hisn/ProtectionHero.swift` | Card layout, adaptive header, checked-at line |
| `ios/Hisn/Pause.swift` | Today-only count helper and card layout |
| `ios/Hisn/AppUsageSection.swift` | Visible mode selector and action-first order; scope behind the footer disclosure |
| `ios/Hisn/en.lproj/Localizable.strings`, `ar.lproj` | New overview, setup and settings copy in both languages |
| `ios/HisnTests/UIRenderTests.swift` | New phone/iPad light/dark renders and the three Setup drill-ins |
| `ios/HisnTests/PauseTests.swift` | Today-only tally test |
| `ios/generate_xcodeproj.py`, `ios/HisnMobile.xcodeproj` | New sources, version 1.5.0, build 9 |
| `design/ios-ui-1.5.0/` | Before/after captures and their README |

No rule, lock, commitment, Screen Time, DNS, Safari, pause or shield behaviour
changed. The Setup drill-in navigation titles were shortened and the new copy
was added in both languages; the localization parity test still passes for every
key the SwiftUI code looks up.

## Verification

- `./test.sh ios` — **39 Python and 216 simulator tests, 0 failures** on the
  iPhone 17 simulator (iOS 26.5). The previous release had 213 hosted tests; the
  three additions are the two UI render tests and the today-only pause tally.
- New renders cover every tab at 390 × 844 and 1024 × 1366 in English and
  Arabic, phone light and dark, plus the three Setup drill-ins. The existing
  test covers the largest accessibility text sizes.
- REA evidence: ScreenZen `ev_d2543bc4839da8fb59d8ae289793cad987daad652e2c13c7ad817e44f1b23402`;
  HisnMobile 1.4.0 `ev_1d3e32a14ad19bb2c08c2c24fabc7ad51b7cfc9f5b3e86781bbc5fb81cb5d531`.
  Both are static artifact inventories, not runtime proof.
- Captures are in `design/ios-ui-1.5.0/`. The iPhone before/after images are
  real simulator screenshots; the iPad pairs are the existing 1.4.0 render
  harness and the new 1.5.0 render harness at the same size.

## Limits

- Simulator only. No signed build, Family Controls entitlement or physical
  iPhone was available, so the system shield, DeviceActivity callbacks, quiet
  hours, budget counters and real Screen Time enforcement remain unverified on a
  device. This pass changes no part of those paths.
- No user study, screen-reader audit or pixel snapshot test. The render tests
  prove each page draws something, not that every control is reachable in the
  expected VoiceOver order.
- The 1.4.0 device-acceptance steps still apply: apply an always-block rule and
  verify the shield copy, tap its single button, test budget and quiet-hours
  shields on both sides of midnight, switch language, revoke Family Controls,
  and record the results. The new UI does not substitute for any of them.
- The overview can only be as complete as the checks it reads. A missing or
  revoked layer still shows as attention, not as protection.

## Next owner actions

The Apple Developer Program, Family Controls distribution entitlement for the
app and three extensions, a disposable iPhone and TestFlight remain the owner
actions in [the iOS improvement plan](IOS_IMPROVEMENT_PLAN.md) and
[1.0 readiness](1.0_READINESS.md). This release does not change those.
