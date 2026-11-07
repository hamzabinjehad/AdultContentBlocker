# Android UI pass — 2026-11-06

Android goes from 1.1.1 (versionCode 28) to **1.2.0 (versionCode 29)**. This is a
presentation pass: no enforcement layer, permission, stored value, full
disclosure or commitment rule was added, removed or weakened, and no new claim
is made about what any layer blocks. The captures and the per-screen record are
in [design/android-ui-2026-11-06](../design/android-ui-2026-11-06/README.md).

## Why this pass

The 2026-11-05 pass gave Hisn the right *structure* — a permission checklist, a
protection-rules list and a settings list — but the three tabs still read as
walls of blue circles. Every row carried a 38 dp filled icon tile over a 16 sp
title and a 13 sp second line, every card opened with another icon-tiled
section header, and the app bar repeated the product name above a page title
that repeated the selected tab. Measured on the owner's phone, the seven
Protection rule rows needed about 1,250 px of a 2,340 px screen and the state of
each rule sat in its second line, so the eye had to read every row to compare
them.

ScreenZen was opened read-only on the same phone for the vocabulary only: one
line per row with the answer at the end, section labels without icons, a single
filled action per card. Nothing from it was copied — not code, assets, strings,
colours or the data model.

## What changed

### The row system

- A row is now a 22 dp line icon, a 15.5 sp title, an optional one-line summary
  at 12.5 sp and an optional trailing answer, 54 dp minimum height, with a
  18 dp chevron. The filled icon tile is gone, so the text column is wider and
  the list reads as text, not as bubbles.
- The trailing answer is a real part of the row: `Ui.SettingRow.setValue` writes
  it with the state colour (`On` green, `Off` muted, `Needs attention` amber)
  and keeps the row's spoken label in step (`title · answer`). Rows that carry a
  sentence instead of a word keep the second line.
- The Protection rule rows use it: **Apps to block entirely**, **Adult text**,
  **Images and video**, **App schedule**, **Daily time limits**,
  **Protection lock** and **Uninstall protection** are one line each, and the
  seven fit above the fold.
- A frozen row (protection lock) now shows a lock where the chevron was and
  keeps its text at 0.72 alpha; disabled controls sit at 0.58 instead of 0.45,
  which was failing contrast on the secondary line.

### Sections and the bar

- Cards no longer open with an icon-tiled 19 sp heading. A 12.5 sp label with
  slight letter spacing sits above the card (`Ui.sectionLabel`), so only items
  carry icons and the page has one heading level less.
- The app bar names the page: **Hisn** with its tagline on Protection, **Rules**
  and **Settings** on the other two. The separate page titles are gone, which
  gives back a full block of vertical space and removes the duplicate heading.
- The bar is 56 dp high instead of 68 dp.

### Protection

- The hero follows its own verdict: an amber tint and border when something
  needs attention, the blue accent otherwise. The mark is 92 dp instead of
  116 dp.
- The DNS card has a real header: **DNS filtering** with a state pill, then one
  honest sentence. When the layer is off, the body says what that means —
  *Not running. No domain is filtered until you turn this on.* (`dns_off_body`,
  English and Arabic) — instead of repeating `DNS filtering · Off` under a
  header that already says it.
- The dead **DNS filtering is on** button is gone. A start button appears only
  when there is something to start; a running layer shows its status, not a
  disabled button.
- **App protection** is a status row on Rules, not a card holding a lone
  "Connected" line: icon, title, answer, chevron, and the same disclosure the
  button opened.

### Rules and Settings

- The lock banner keeps its full commitment wording and gains a tighter
  layout; the frozen rows beneath it keep their labels readable.
- The three standalone rule rows no longer repeat their own titles in their
  summaries (`Daily time limits · Off` → `Off`, `Protection lock · About 8 hours
  remaining` → `About 8 hours remaining`, and the same for the schedule and its
  "all apps except" variant), in English and Arabic.
- Settings uses the same labels, icons and trailing answers. The
  **Licenses and sources** row no longer describes itself in a second line.

### Smaller screens

- The voluntary pause's exit is a quiet button, not the loudest control on the
  screen; an enforced pause keeps the way out primary.
- The first-run mark is smaller (124 dp; the lock page 116 dp), and the pause
  row uses a real pause icon instead of the diagnostics bars.

## What was not changed

- Every disclosure, consent sheet, refusal message and commitment rule is the
  same text or stronger; the lock still freezes rules and the app still refuses a
  change before opening an editor.
- No permission is requested differently, no stored field moves, and the DNS,
  accessibility, uninstall and managed-lockdown paths are untouched.
- The enforcement code paths (`TextGuardService`, `DnsVpnService`,
  `PolicyStore`, `ProtectionLock`, `UninstallProtectionController`) are not
  edited; only their presentation in `MainActivity`, `Ui`,
  `WelcomeActivity` and `RestrictionActivity` is.

## Verification

- `android/check-strings.py` — 553 strings, 16 plurals and 543 source references
  in English and Arabic, all matched.
- `android/check-notices.py` — passes.
- `android/test-core.sh` — every core suite passes (the same 68/72 corpus row
  and the pre-existing `MISS` diagnostics as before this pass).
- `:app:assembleDebug`, `:app:lintDebug` (0 errors) and a release-signed direct
  build (`-PhisnDistribution=direct`) all pass.
- The release APK was installed in place over 1.1.1 on the owner's Samsung
  SM-G990B2 with the owner's key: saved rules and the running protection lock
  were preserved. Android disables an app's accessibility service when the
  package is replaced, so the guard showed its waiting screen after the update;
  the service was re-enabled in Settings → Accessibility → Installed apps and
  the guard reconnected. DNS filtering stopped with the update and was started
  again from the app's own consent sheet, reading back as connected. Captures:
  English light, English dark and Arabic dark, in
  [design/android-ui-2026-11-06](../design/android-ui-2026-11-06/).
- Not verified: large font scales, tablets, landscape and every row state. No
  signed APK is published; no Play upload is claimed.

## Limits

- This is a pass over the three tabs, the first-run mark and the pause exit. The
  rule editors still open as framework dialogs; they inherit the new row style
  but were not redesigned.
- ScreenZen was observed read-only for structure. Its behaviour, enforcement and
  numbers were not measured and are not claimed here.
- The trailing answer is one line with an end ellipsis. A locale or font scale
  that makes an answer much longer would truncate it rather than wrap the row.
