# Opal main-app pass: rule tiles and a stat row

Date: 13 November 2026. Source: Opal's public site (opalapp.com) and the app screens it
shows there — Home, Rules and Timer. The earlier pass
([competitors-2026-11-13](../competitors-2026-11-13/README.md)) stopped at Opal's paywall,
so it covered onboarding only. Captures here are from the `hisn_ui` emulator, debug build.

## What Opal's main screens do

| Screen | Pattern |
| --- | --- |
| Home | One focal object, then **three numbers in a row** right under it (Score, Screen time, Culprits): small label above, large figure below. |
| Home | "My Apps ›" card: "2 active rules" and the blocked apps' icons, on the first screen. |
| Rules | **Each rule is a tile** in a two-column grid: a glyph, a status chip ("7 left", "in 12h"), the rule's name, the affected app icons. |
| Rules | **"Issue Detected" chip on the tile that has the problem**, not on a separate status page. |
| Timer | One large value, − / + beside it, one Start button. |

## What changed in Hisn

- **Rules › Custom rules are tiles** (`RuleTile`, `TileGrid` in `Components.kt`). Label
  above, value below, so TalkBack reads "Blocked domains, 1". The icon badge is tinted
  only when the rule holds something. The Apps tile shows up to three blocked-app icons.
- **A warning chip on a rule that is saved but not enforced.** Domains with DNS filtering
  off show "DNS filtering is off"; words, apps, daily limit and schedule show "App
  protection is off" or "App protection not connected". See `rules-issue.png`.
- **Home › Your rules is a stat row** (`StatStrip`): Blocked domains, Apps to block and,
  while DNS runs, Lookups blocked. Each cell opens its screen. See `home-stats.png`.

## Left out on purpose

- Opal's Score and "years reclaimed": projections, not measurements (see the earlier
  pass, "Guilt numbers"). The stat row shows only counts Hisn holds.
- Gems and milestones: they reward streaks, which Hisn does not count.
- The Timer screen: Hisn's daily-limit editor was not changed in this pass.

## Checked

`bash android/test-ui.sh emulator-5554 direct`: 20 of 20 pass (labels, 48 dp targets,
2x text, RTL). Not checked: a real phone, light theme and Arabic by eye, and the tiles
with apps selected (the icon strip).
