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
  off show "DNS filtering is off"; words, apps, daily limit and schedule show "Protection
  is off" or "Not connected". The chip is a Material `AssistChip` and tapping it turns the
  layer on. See `rules-issue.png`, `rules-light-apps.png` and `rules-ar.png`.
- **Home › Your rules is a stat row** (`StatStrip`): Blocked domains, Apps to block and,
  while DNS runs, Lookups blocked. Each cell opens its screen. See `home-stats.png`.

## Left out on purpose

- Opal's Score and "years reclaimed": projections, not measurements (see the earlier
  pass, "Guilt numbers"). The stat row shows only counts Hisn holds.
- Gems and milestones: they reward streaks, which Hisn does not count.
- The Timer screen: Hisn's daily-limit editor was not changed in this pass.

## Second pass (14 November)

- **App picker suggests the most-used apps** (`apps-suggested-light.png`). With usage access,
  "Most used today" lists up to five apps by today's screen time, each with its switch, above
  the full list. Without it, one row offers to turn usage access on. This is Opal's suggested
  block list, but nothing is pre-selected and the record never leaves the phone.
- **Shorter chip labels.** Looking at light theme and Arabic showed "App protection is off"
  cut to "App protection…" in a half-width tile, so the labels are now "Protection is off"
  and "Not connected".

## Third pass (14 November)

- **Bug: a domain or word could be added twice.** `addDomain` and `addWord` appended the
  entry after the write had already reloaded it from the store, so the list held it twice
  and the editor crashed on the duplicate key. Found by the new Done-key test; fixed in
  `HisnState.kt`.
- **The keyboard's Done key adds the entry** in Blocked domains and Blocked words, the same
  as the Add button.
- **The three rule editors are in Arabic.** Blocked domains, Blocked words and Apps to block
  showed English to an Arabic reader; about 50 strings now go through `tr`
  (`domains-ar.png`).
- **The warning chip is a full 48 dp control.** It was drawn 32 dp tall with an invisible
  touch margin, which the shell's tap-size check rejects once a rule is saved.

## Checked

`bash android/test-ui.sh emulator-5554 direct`: 25 of 25 pass (labels, 48 dp targets,
2x text, RTL, the chip's action and size, the usage-access row, the Done key). The
accessibility tests also pass with a saved rule present, so the chip is measured. Light
theme and Arabic were looked at on the emulator for Home, Rules, the app picker and the
domain editor. Not checked: a real phone, and the other detail screens (schedule, daily
limit, lock, diagnostics) are still English in Arabic.
