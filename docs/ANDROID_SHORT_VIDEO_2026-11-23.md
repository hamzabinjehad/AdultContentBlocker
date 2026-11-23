# Android short-video block, 23 November 2026

What the short-video layer (Shorts, Reels, Spotlight, TikTok) now covers, how each part
was checked, and what it still misses. Rules and readings live in
`android/core/src/main/java/app/hisn/core/ShortVideoSurfaces.java`; the guard's side is
`showsShortVideo` and `showsLabelledPlayer` in `TextGuardService.java`.

## Changes

- **More all-feed apps close as a whole:** Likee, Kwai, Snack Video, Kuaishou, Moj, Josh,
  Chingari and Triller join TikTok, TikTok Lite and Douyin. These are package names. A name
  that is not installed matches nothing.
- **Patched YouTube builds:** ReVanced, ReVanced Extended and Vanced install beside YouTube
  under their own package name. Their Shorts player is searched under both their own and
  YouTube's resource name, because these builds rename the package but not the resource
  table. Not yet read from a device.
- **Facebook Reels, by label and shape:** Facebook reports every view ID as "(name removed)",
  so the player is found by a short breadth-first walk (at most 400 views): a view labelled
  "Reel" or "Reel by <name>", as wide as the window and at least 1.65 times as tall as it is
  wide. Setup still treats Facebook as a whole app, so "close Facebook" in onboarding is unchanged.
- **More browsers' address bars:** Samsung Internet (read from a device), plus Edge, Vivaldi,
  Kiwi, Firefox, Opera and DuckDuckGo (common IDs, not yet read from a device). A stale ID
  finds no node, so it can only miss.
- **Invisible characters ignored in addresses:** Samsung Internet puts a left-to-right mark
  before the host, which made even `tiktok.com` fail the link check. Format characters
  (`\p{Cf}`) are removed before matching.
- **YouTube:** `reel_watch_fragment_root` added as a fourth Shorts marker.

## Readings on a Galaxy S21 FE (owner's phone, read only)

| App | Version | Finding |
|---|---|---|
| Facebook | 588.0.0.42.55, English | Reels tab player "Reel" 1080x2054 (1.90); reel opened from the feed "Reel by …" 1080x1920 (1.78); Home feed reel cards "Reel", at most 1080x1619 (1.50). Back from both players lands on Home. |
| YouTube | 21.45.82 | `reel_recycler`, `reel_player_page_container`, `reel_watch_player`, `reel_watch_fragment_root` on Shorts; Home and its feed show only `reel_time_bar`. Back leaves Shorts. |
| Snapchat | 14.32.0.54 | `spotlight_container` on Spotlight only (absent from Camera, Stories, Chat, Map). Back leaves Spotlight for the camera. |
| Samsung Internet | 31.0.0.81 | Address bar `location_bar_edit_text` shows the host only, after an invisible U+200E: "‎tiktok.com". |

Eleven Facebook screens captured from the phone were replayed through the real
`isLabelledPlayer` rule, walked breadth first as the guard walks them: both player screens
were caught, and all eight Home feed screens (five with a reel card) were allowed. Each
screen needed 46 to 124 views.

## Verification

- `android/test-core.sh` passed, including the new short-video cases (all-feed apps,
  patched YouTube, browser bars, invisible marks, Facebook player versus feed card).
- Hisn and Unrot debug builds compile.
- Not run live: the emulator was unstable under heavy host load (system server restarts,
  storage service not up), and no new build was installed on the phone. The live case
  "Reels in Chrome left" in `test-live-checks.py` is still to run on this build.

## Remaining limits

- Facebook Reels is recognised only with Facebook in English; other languages label the
  player differently. Facebook Lite is not covered.
- Samsung Internet shows only the host, so there it closes TikTok but not
  `youtube.com/shorts`, Instagram Reels or Spotlight pages.
- Instagram, Instagram Lite and the patched YouTube builds were not on a device to read.
- The added whole-feed apps and the unread browser IDs come from their public package names
  and common blocker IDs, not from a device.
