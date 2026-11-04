# Changelog

Newest first. Versions are per platform; the pinned set is checked in
[versions.json](versions.json) and rendered in [docs/VERSIONS.md](docs/VERSIONS.md).
Per-release evidence lives in `docs/` and is linked from each entry. A version
bump without an entry here is an unfinished release.

## iPhone & iPad 1.5.0 — 2026-11-04

### Changed

- **The overview is a calm card stack.** The status hero, the pause card, one
  protection-plan card with `0 of 3` progress and four state rows, a protection
  lock card and a rules card replace the old list plus full readiness and
  commitment sections. The duplicate `0/3` and the repeated plan evidence are
  gone; the detailed checks stay one disclosure down.
- **Saved rules are one tap away.** **Set a lock / Manage lock**, **Blocked
  sites** and **App limits** open their editors in sheets instead of sending the
  person to find the right Setup step. The full commitment form no longer sits
  on the status page.
- **Setup is three guided steps.** One status card carries the state, issues,
  last check time and `0 of 3 steps done`; step cards lead to blocking layers,
  app limits plus commitment, and the passcode/removal page. The four checks
  follow the steps instead of preceding them.
- **App limits shows the decision.** Always block, daily combined budget and
  daily quiet hours are visible rows with icons and a checkmark instead of a
  menu picker; the selected items and count stay above the rule.
- **Settings is grouped cards.** Language, the Safari extension, gray screen,
  privacy and about each get one card; the full privacy note and coverage copy
  stay behind disclosures.
- **Accessibility.** Status headers, step cards, pause, lock and rule rows stack
  at accessibility text sizes; status pills and the progress count no longer use
  fixed widths; cards follow system light/dark and Increase Contrast; the
  4.5:1 status colours are unchanged.
- No rule, lock, commitment, Screen Time, DNS, Safari, pause or shield behaviour
  changed. The 1.4.0 shield, 1.3.0 pause and quiet hours, and the 1.2.0
  saved-rule freeze remain.

### Verification

- `./test.sh ios` — **39 Python and 216 simulator tests, 0 failures** on the
  iPhone 17 simulator (iOS 26.5). Three new tests cover the today-only pause
  count and the new phone/iPad light/dark renders including the three Setup
  drill-ins.
- Design research: REA 6.8.0 `inspect-artifact` on ScreenZen 2.0.0 (build 63)
  and the built 1.4.0 app; observation only. Captures in
  `design/ios-ui-1.5.0/`; record in
  [the 1.5.0 notes](docs/IOS_1.5.0.md) and
  [the REA audit](docs/REA_AUDIT_IOS_2026-11-04.md).
- Version 1.5.0, build 9. Simulator-tested only; no Apple team, Family Controls
  entitlement, signed build or physical-device result is claimed.

## Mac 1.1.11 — 2026-11-04

### Added

- **Check a website.** A read-only sheet, reachable from the window toolbar,
  Overview, Blocking Rules, the menu bar and ⌘K, answers whether an address
  would be blocked and names the rule that decided it. It reads the installed list,
  falls back to the signed core seed bundled with the app, and reports an
  unreadable list as unavailable rather than as “not blocked”. **Add to Always
  block** uses the existing guarded save path and still works during a lock.
- `BlocklistStore.isOnPublishedList(host:)` and `matchesKeyword(host:)`, two
  read-only queries that let the checker explain one layer at a time.
- `SiteCheckerTests` and published-list/keyword query tests.

### Changed

- **Overview is a status hero, not a stack of summaries.** One card carries the
  headline, summary and a tile per layer (name, state word, colour). The
  duplicate chip strip and expanded layer repetition are gone.
- **One state-aware primary action.** The button reads **Finish setup** when
  nothing enforces, **Review setup** when some layers run but a check is
  missing, **Start a lock…** when the setup checks pass, and **Manage lock**
  during a lock. The lock remains the secondary action when it is valid. The
  disabled “Checking…” state no longer blocks the first seconds of launch.
- **Warnings sit under the status they qualify**, before the next action.
- **Pause and the site checker share one tools card** instead of the pause
  taking a full card.
- **Setup leads with a progress ring** and a next-step line; the readiness card
  is compact, the repeated build-block paragraph is gone, and each stage shows
  `done/total` plus a **Next** badge.
- **Blocking Rules is one card per list**, with an empty state, a parser-derived
  count, app icons for blocked apps, and a Check a website row.
- **The Lock page reads the commitment as a band**: the end date is emphasised,
  the mode toggles are trailing switches, and a running lock shows elapsed
  progress between its start and end.
- **Settings card headings carry icons**, matching the rows elsewhere.
- The sidebar's lock item shows the remaining time instead of a bare lock icon.

### Verification

- `./test.sh macos` — **541 tests, 0 failures**; **645 strings, all with
  Arabic**.
- Five pages and the checker were captured from a real development build on the
  owner's Mac, dark, 760 pt, English; the 1.1.10 captures are the before state.
  The bundled-seed path was exercised because the ad-hoc build has no app-group
  container.
- No rule, lock, filter, browser-bridge or protection-layer behaviour changed.
  New strings have Arabic; the old `": "` placeholder key is gone.
- Design research: Screen Zen 2.0.0 (build 63) observed read-only; Opal, one
  sec, Jomo and Clearspace product pages and reviews read publicly. No
  competitor code, assets, visual identity or copy was reused.

## Mac 1.1.10 — 2026-11-03

### Changed

- **One page title, not three.** The large in-page header (icon, title, subtitle)
  is gone; the window title bar already names the page and its purpose. Setup
  previously said "Protected Setup" in the title bar, again as a header and a
  third time as a section heading. Every page now starts with its content.
- **Overview puts the action before the evidence.** The one primary button sits
  directly under the layer chips; with the layer details open it used to land at
  the bottom of the card, below three explanations. The commitment card moved up
  beside the protection card, and the laptop-readiness card appears only while a
  check is missing instead of repeating a passing verdict.
- **Setup is one card.** Readiness, a progress bar, "2 of 12 steps done" and
  "Next: …" now share the top card; the duplicate "Protected Setup" section and
  its second verdict are gone.
- **Lock states what the chosen mode means, once.** The fixed-commitment
  paragraph follows the toggle — before, turning the fixed commitment off left
  the paragraph on screen — and the duplicate "Your chosen commitment" card is
  removed. The action is now pinned to the bottom edge of the window with a live
  consequence line ("This lock would enforce 2 of 3 protection layers."), so the
  decision stays visible while the options scroll instead of sitting under two
  warnings.
- **Blocking Rules leads with the lists.** The editable site and app lists come
  first; the browser guide follows them instead of pushing every control below
  the fold.
- **A blocked-sites strip.** The sites list opens with chips for what this
  person has added, before the controls that change them; it reads the same
  edited text the rows do, so the two cannot disagree.
- **Settings is grouped cards.** Content checking, browser guidance, partner,
  gray screen, language and details each become one card instead of a flat
  column of headings and dividers, and the three check toggles are trailing
  switches rather than leading checkboxes.
- **Site lists are rows, not a text box.** Each always-blocked and always-allowed
  list shows one domain per row with a Remove control, an inline add field and a
  live count; bulk paste stays one disclosure down, with the one-domain-per-line
  rules next to the box. Rows and the add field write the same newline text the
  save path already parses, so there is one source of truth.

### Verification

- `./test.sh macos` — **526 tests, 0 failures**; 597 strings, all with Arabic.
- Design research: REA 6.4.0 `inspect-artifact` and
  `capture-native-ui-scenario` on the Screen Zen 2.0.0 (build 63) window,
  observation only. Captures in `design/macos-ui-1.1.10/`.
- Version 1.1.10, build 21. Unsigned and unnotarized; no physical-Mac,
  VoiceOver or signed-build result is claimed.

## Android 1.1.0 — 2026-11-03

### Added

- **A first-run welcome.** Three skippable pages explain blocking, deliberate
  opening and protection locks, with one large icon and one idea per page.
  Finishing opens Rules; **Skip for now** opens Protection. It appears only when
  no rules are configured and has not been seen. English and Arabic RTL.
- **Take a pause on Protection.** A visible row opens the existing breathing
  pause without a block. It changes no rule, grants no permission and remains
  available during a protection lock. **Back to Hisn** returns immediately.
- **Today's use in the daily-limit editor.** Each selected app shows
  **Used X of Y today** with a progress bar from Android's on-device usage
  record; without Usage access the editor says so instead of showing zeros.
- The pause row moved near the top of Protection, after the hero/attention area.
- ScreenZen's Android onboarding was observed read-only as design input; Hisn
  uses its own copy, icons, colours and components. No ScreenZen code, assets or
  copy was used. Open-count, streak, paywall and cloud features remain out of
  scope.

### Release

- Signed direct APK `1.1.0-direct`, versionCode 27, package
  `app.hisn.android`; the Play APK/AAB are built and checked but not uploaded.
  Certificate unchanged:
  `2F:BE:D6:57:E6:AB:C7:1C:15:CA:D0:CD:99:1C:53:2F:59:77:2D:16:95:D7:C3:7E:65:A1:14:38:5E:94:70:C5`.
- SHA-256: direct APK
  `4d80a3c7c50e8e0637c69e9aa8b604d55902d1ee55c9f96c7e30c5712f0d7dff`;
  Play APK `f90c43b83cac0a79383dd8e0ccaecee4f9f604993ba618c02c23c7ebb6a75a5c`;
  Play AAB `ee5bce6fd4d70bc9fcfe0a43ce6bc019dd04377ca0d7266c1eba4ba2ea78dc4f`.
- The owner's Samsung updated from 1.0.0-direct to 1.1.0-direct in place
  (`firstInstallTime` unchanged) and rendered the Arabic welcome. The 0.9.0
  preview was debug-signed; its rules and optional admin did not survive the
  earlier 1.0.0 migration on this phone, so the safe preview path remains
  uninstall first. New installs from 1.0.0 onward use the release key.

### Verification

- `JAVA_HOME=<JBR> sh test-core.sh` — **PASS**, including `AppTimeLimitTest`,
  sealed settings, protection lock, DNS, text, visual, guardian and seed suites.
- `python3 check-strings.py` — **PASS**, 480 strings, 15 plurals, 506 source
  references in English and Arabic.
- `./gradlew assembleDebug assembleDebugAndroidTest lintDebug` — **PASS**, 0 lint
  errors.
- `CoreDeviceTestRunner` on the API 36 emulator — **PASS**.
- Welcome UI in English and Arabic, Skip/Set up, page-by-page back, the
  voluntary pause and the "Used 4 min of 15 min today" row — **PASS** on the
  API 36 emulator.
- Signed Play/direct APKs and AAB pass `check-release.py` and `apksigner`.

### Limits

- No full physical-phone enforcement, TalkBack/large-font or OEM-skin audit is
  claimed; the phone run above is a launch/update and Arabic-welcome check.
- The welcome appears only when no rules are configured. Pause and usage display
  change no rule and grant no permission. Daily limits still need accessibility
  and Usage access; clock/time-zone changes, clearing usage data, force-stop,
  Safe Mode, ADB/root or a factory reset still bypass them.
- The Play AAB is not uploaded and Play approval is not claimed.

## Blocklist v6 — 2026-11-03

### Published

- **The first signed list generation is live** at
  `https://raw.githubusercontent.com/hamzabinjehad/AdultContentBlocker/lists/manifest.json`:
  **5,274,046 domains**, **239,885 core**, 5,831 terms, 480 DNR rules. The
  manifest and signature were downloaded over HTTPS, are byte-for-byte the
  signed candidate, and returned HTTP/2 200.
- **The publish guard refused a degraded build first.** The initial build timed
  out fetching two UT1 sources and produced 1,273,954 domains; its signature
  was valid, but the guard refused the drop from 5,276,448. Nothing was
  published from it. A retry fetched all ten sources and passed.
- `keys.py verify` — signature OK; `publish_guard.py` — v5 → v6 OK;
  `test_build.py` — 43 tests OK, including known-adult-blocked and
  critical-infrastructure-not-blocked checks. Published files total 34 MB;
  `domains.txt` and `domains.packed` stay off the branch because they exceed
  GitHub's 100 MB limit.

### Limits

- The bundled seed stays **v5**; it was not re-cut. iOS and Android still update
  only with an app build. No installed Chromium or Mac client is proven to have
  installed v6.
- The daily GitHub workflow still cannot publish: the main-only `list-signing`
  environment secret is not configured. This generation was published once with
  the matching local key. See
  [the publication record](docs/LIST_PUBLICATION_2026-11-03.md).

## Browser extension 1.5.0 — 2026-11-03

### Added

- **Move your rules.** Settings → Blocking Rules exports the five hand lists
  (blocked sites, allowlist, custom words, ignored words, skipped sites) as a
  versioned `hisn.rules` JSON file, and imports one in another browser. The
  file contains no blocking mode, lock deadline, list generation, pause count
  or browsing data.
- Import merges **add-only**: it never removes an entry, so a more permissive
  file cannot undo a stricter browser. A running lock withholds additions to
  the allowlist and corrections while still applying new blocks and words; the
  report says what was withheld, invalid, or beyond the 200-word limit.
- An app-managed browser exports its lists but refuses an import: the Mac app
  owns them and would overwrite the file on its next heartbeat.

### Verification

- `./test.sh extension` — **PASS**: JavaScript suites, package and evaluation
  corpus. The new `transfer.test.js` (45 checks) covers file shape,
  schema/version refusal, invalid-entry counting, add-only merge, lock
  direction, the 200-word ceiling and app-managed refusal.
- `extension/test/browser/run.sh settings.html` — **PASS**, including a real
  file reaching the guarded worker, an invalid file writing nothing, and a
  locked browser applying blocks while withholding allowances.

### Changed

- Version 1.5.0. No permission, host permission, storage field or network path
  was added. English and Arabic copy added.

## Mac 1.1.9 — 2026-11-03

### Changed

- **The browser-guard warning is now an intervention card.** A dark, calm card
  shows a shield, the browser's name, the exact reason, a large countdown in
  seconds, and one **Open browser** action. There is no dismiss or bypass:
  restoring the extension is what cancels the close. The panel is movable,
  hides its title bar, stays above other windows and resizes for several
  browsers.
- **Overview is status-first.** Compact layer chips answer which layers are
  ready at a glance. The long evidence, readiness count and per-layer details
  sit behind a **Layer details** disclosure that opens automatically when a
  layer needs attention or is still checking.
- No policy, filter, lock, browser-bridge or protection-layer behaviour
  changed. Version 1.1.9, build 13.

### Verification

- `./test.sh macos` — **526 tests, 0 failures**; 597 strings, all with Arabic.
  The new guard-card render test covers English and Arabic in the same sizes as
  the panel.
- Design captures are in
  [`design/macos-ui-1.1.9/`](design/macos-ui-1.1.9/README.md).
- The guard-card render is a deterministic SwiftUI render, not a physical
  click-through; the panel's countdown and close logic remain covered by
  `BrowserGuardTests`.

Unsigned and unnotarized. See [the 1.1.9 record](docs/MACOS_1.1.9.md).

## Mac 1.1.8 — 2026-11-03

### Added

- **A pause window.** A 30-second guided breath (4 seconds in, 6 out, three
  times), five rotating reminders, and four needs — Rest, Company, Calm,
  Something to do — each with one offscreen tip. It is reachable from Overview,
  the menu bar and **Protection › Take a pause… (⇧⌘P)**, and it is available
  during a lock. Closing is always immediate.
- **A local count only.** The pause stores a calendar day, a count and the time
  of the last pause in `UserDefaults`: a 30-second merge window, a 9,999 cap and
  a new day starting over. No host, reason, history or network request. The menu
  bar shows today's count once a pause is recorded.
- English and Arabic copy, VoiceOver labels for the breathing state and needs,
  and design captures in
  [`design/macos-pause-2026-11-03/`](design/macos-pause-2026-11-03/README.md).

### Changed

- No rule, permission, filter, browser bridge, schedule or lock behaviour
  changed. The Mac version is 1.1.8, build 12.

### Verification

- `./test.sh macos` — **521 tests, 0 failures**; 589 strings, all with Arabic,
  including the export of every string the compiled code uses.
- Eight new `PauseTests` cover the tally, merge window, day boundary, cap,
  breath timeline, reminder rotation, stored shape and render in both languages
  at both stages, plus the Overview entry in both languages.
- Design research using REA on Screen Zen and Hisn is recorded in
  [the REA audit](docs/REA_AUDIT_SCREENZEN_2026-11-03.md). No Screen Zen code,
  assets or copy was reused.

Unsigned and unnotarized. No physical-Mac, VoiceOver or signed-build result is
claimed; see [the 1.1.8 record](docs/MACOS_1.1.8.md).

## iPhone & iPad 1.4.0 — 2026-11-03

### Added

- **A shield that explains itself.** `HisnShieldConfig` returns the system
  Screen Time shield for every app, category, site and web domain Hisn shields.
  It names the saved rule — always block, daily budget reached or quiet hours —
  in the language chosen in Hisn, using a small fixed English/Arabic table and
  the App Group language choice.
- **No unlock action.** `HisnShieldAction` answers the shield's one button with
  Close. The ManagedSettings shield remains applied until the rule ends or the
  person changes it in Hisn outside a commitment. A missing or unreadable rule,
  a budget shield without its reached-day marker, or an outside-window
  quiet-hours shield gets a generic honest message rather than a false claim.
- Both extensions carry the Family Controls entitlement; the configuration
  extension also carries the shared App Group. Privacy manifests now cover
  eleven bundles: the app, six blockers, monitor, scanner and both shield
  extensions.

### Verification

- `./test.sh ios` — **39 Python tests and 213 simulator tests, 0 failures** on
  the iPhone 17 simulator, iOS 26.5. The hosted count was 206 before this
  release; the seven new tests cover shield text, language and reasons.
- Version 1.4.0, build 8.

Simulator-tested only. No Apple team, Family Controls distribution entitlement,
signed build or physical-device result is claimed. The system-rendered shield
still needs a signed build on a real iPhone.

## iPhone & iPad 1.3.0 — 2026-11-03

### Added

- **On-demand pause.** A 30-second guided breath (4 seconds in, 6 out, three
  times), five rotating reminders, four needs with offscreen tips and a local
  day/count tally. It is available during a commitment, closes immediately and
  changes no protection rule. The tally is a day, a count and the last time; no
  host, reason or history.
- **Protection hero.** The overview leads with one status, layer chips and one
  next action; the full diagnostics are one disclosure below instead of a wall
  of text.
- **Daily quiet hours.** A DeviceActivity window applies the selected apps'
  shields, splits a cross-midnight window into two same-day legs, enforces
  Apple's 15-minute minimum and checks both wall and monotonic time. The health
  row distinguishes registered from stopped; a commitment freezes the rule and
  the guardian path can tighten, never loosen it.

### Changed

- The protection lock still freezes the saved blocked-site list and app-limit
  rules while a commitment runs; adding or restoring a protection layer remains
  available, turning one off does not.
- Version 1.3.0, build 7. No new permission or network path was added.

### Verification

- `./test.sh ios` — **206 tests, 0 failures** on the iOS 26.5 simulator
  (iPhone 17), including 9 pause tests, 12 quiet-hours tests and the existing
  Safari-blocker and privacy checks.
- `python3 -m unittest ios.test_localization` and the Python matrix pass;
  `script/versions.py --check` passes.

Simulator-tested only. No Apple team, Family Controls distribution entitlement,
signed build, TestFlight or physical-device result is claimed. The Screen Time
behavior, quiet-hours registration and pause sheet still need a real device.

## Android 1.0.0 — 2026-11-03

### Added

- **Daily app time limits.** Rules gains a separate card: choose apps and one
  daily foreground-time limit from five minutes to 24 hours. Android's own
  on-device usage record is read only after the person grants the separate
  **Usage access** permission; Hisn closes a limited app at the threshold and
  shows the pause screen with a time-limit reason until local midnight.
- A limit is a saved rule: the protection lock freezes it in both directions,
  and the guardian tighten-only path preserves saved limits and cannot remove
  or raise one. Missing Usage access or accessibility is shown as attention,
  never as enforced.
- The 0.9.0 protection lock, direct-build removal guard, managed-device
  previews, DNS layer, text checks, visual sampling, schedules and pause screen
  are unchanged. Version metadata now says 1.0.0 rather than preview.

### Release

- **Signed direct-sideload APK** at 1.0.0-direct, versionCode 26, package
  `app.hisn.android`. Published at
  [the android-1.0.0 release](https://github.com/hamzabinjehad/AdultContentBlocker/releases/tag/android-1.0.0);
  the asset was re-downloaded and its SHA-256 and certificate re-verified. The
  Play APK and AAB are built and checked but not uploaded; Play approval is not
  claimed. The signing certificate is RSA 4096
  (`CN=Hisn Android`, SHA-256
  `2F:BE:D6:57:E6:AB:C7:1C:15:CA:D0:CD:99:1C:53:2F:59:77:2D:16:95:D7:C3:7E:65:A1:14:38:5E:94:70:C5`).
- Artifact SHA-256: direct APK
  `c42591ec8223e3d24d8159f0536d3212ee0f351b5c3edc1fc4d03c0d30afaac0`;
  Play APK `bded282fd19fb524d77daf42d0a60a0660bd6db5c06acbba316e168743fe5517`;
  Play AAB `0794e0d509ca939cc012e2a01e82353211611c0c54174cefe7e4e40ec07cfad7`.
- The debug-signed 0.9.0 preview cannot be updated in place; uninstall it first.
  The owner must back up the new keystore and password offline in two places.

### Verification

- `JAVA_HOME=<JBR> sh test-core.sh` — **PASS**, including
  `AppTimeLimitTest`, the sealed settings round-trip, protection-lock freeze,
  DNS, text, visual, guardian and seed suites.
- `python3 check-strings.py` — **PASS**, 460 strings, 15 plurals and 485 source
  references in English and Arabic.
- `./gradlew assembleDebug assembleDebugAndroidTest lintDebug` — **PASS**, 0
  lint errors.
- `CoreDeviceTestRunner` on the API 36 emulator — **PASS**, including time-limit
  persistence/freeze and damaged settings.
- Rules UI read back through the accessibility tree, and one live 5-minute
  time-limit pass against the test-only fixture app closed it and showed the
  daily-limit pause screen on the same emulator.
- Both release APKs verify with `apksigner`; `check-release.py` passes for the
  direct and Play distributions.

### Limits

- No physical-phone 1.0.0 run is claimed. Usage access is a separate special
  permission; Android's record resets at local midnight and changing the clock,
  time zone or usage data can change it. Enforcement can lag by up to about a
  minute while a static screen stays open. Settings, launchers, dialers,
  installers and Hisn itself are excluded. Accessibility off, force-stop, Safe
  Mode, ADB/root or a factory reset still bypasses app-level limits.

## iPhone & iPad 1.2.0 — 2026-11-03

### Changed

- **The protection lock now freezes the saved rule lists**, inspired by Android
  0.9.0. While a commitment runs, blocked sites and app-limit rules are
  read-only: no additions, removals, parent-domain consolidation, app selection
  changes, mode changes, minute changes or clearing. An unreadable commitment
  counts as locked. Extending the commitment remains available.
- **Layer recovery is still allowed.** The Screen Time filter cannot be turned
  off inside Hisn during a commitment, but it can be enabled or restored after
  iOS revokes access. DNS setup and Safari rule reload remain available because
  they add or repair layers rather than rewrite the frozen rules. iOS Settings
  switches remain outside the app's control.
- The commitment section shows **Protection lock running** and the frozen-rule
  scope in English and Arabic; site and app controls are disabled rather than
  failing after a tap.
- `AppUsageConfiguration.permits` now requires full rule equality while
  committed, and `CustomSitePolicy.permits` freezes the whole site list. Both
  controllers refuse direct calls, not only the SwiftUI controls.
- The iOS Safari scanner packaging allowlist now includes `lib/language.js`, the
  shared language-aware scorer from extension 1.3.0.

### Verification

- `./test.sh ios` — **185 tests, 0 failures** on the iPhone simulator.
- `python3 -m unittest ios.test_localization` — pass; English/Arabic parity.
- `./test.sh python` — pass.
- Version 1.2.0, build 6. Simulator-tested only; no Apple team, Family Controls
  entitlement, signed build or device acceptance is claimed.

## Browser extension 1.4.0 — 2026-11-02

### Changed

- **UI/UX redesign.** One status hero answers “am I protected?” before detail.
  Popup layers are compact rows with a one-word state (On, Off, Checking,
  Issue) and a gear; the old per-layer sentences moved to Details, the page the
  gear opens, or a tooltip. One full-width primary action replaces competing
  buttons. The lock is a compact banner and the page-language row is a single
  label/value line.
- **Settings dashboard simplified.** The duplicated “Browser protection” list
  and the four status blocks are now one state card, one chip list, a compact
  app-connection card, two quick links and a one-line block-list row. The
  longer list-update explanation is a tooltip.
- **Less copy in English and Arabic.** Removed the sidebar tagline, the
  always-on-content paragraph, the repeated on-device footer sentence, and
  several hints; shortened status, repair, connection, visual, rules and
  content copy. Lock, managed-by-app, private-window and visual-coverage
  limits remain visible where they affect a choice.
- **Calmer visual system.** New light/dark tokens with stronger contrast,
  standardised controls, 44px touch targets, neutral information notices and
  warning amber reserved for warnings. The block page uses the same surfaces.
- Removed unused locale keys. No permission, network path, storage field or
  scoring rule changed; the 1.3.0 language-aware vocabulary is retained.

### Verification

- `./test.sh extension` — **PASS**, including the evaluation corpus (0/22 adult
  cases missed, 0/28 benign pages blocked at 50/80/100).
- `./test.sh browser` — **PASS**, including 160–480px popup layouts, Arabic RTL,
  settings guards and six block pages.
- Design captures and principles:
  [`design/extension-ui-2026-11-02/`](design/extension-ui-2026-11-02/README.md).
- Package: `dist/hisn-extension-1.4.0.zip`, 91 files, 6.2 MB, SHA-256
  `2cb7d723884b633b37f295ced0deec9ac0046c95d70d6c1fc80365acb41c5552`.

Version 1.4.0. Unpacked and store-ready; no listing, assigned store ID or
managed force-install is claimed.

## Browser extension 1.3.0 — 2026-11-02

### Added

- **Language-aware page-text scoring.** Every reviewed term in the signed
  vocabulary now carries a language (`en`, `ar`, `ar-latn`, `de`, `es`, `fr`,
  `it`, `pt`). The scorer picks a vocabulary view for the language the page is
  actually written in, instead of applying all ~5,800 terms to every page. This
  removes weak cross-language homographs without weakening the layer:
  - the detected language's own terms, English (the common base), shared
    spellings and the user's typed words are always present;
  - terms at or above weight 7 — explicit labels such as an adult studio or a
    title phrase — are included in every language;
  - negative/context phrases are never filtered;
  - an undeclared, unsupported or unclear language uses the complete index.
- **Script evidence outranks the page's tag.** The detector counts Arabic and
  Latin letters in the rendered sample. Dominant Arabic text on a page that
  declares `lang="en"` still selects the Arabic vocabulary, closing the
  one-attribute bypass that language-exclusive matching would have created.
- **Feed items are judged in their own language.** A post's `lang` attribute
  and a bounded sample of its rendered text accompany the existing body/alt
  zones. A language change is part of the equality signature, so a changed post
  is re-judged rather than reusing a clean verdict.
- **The popup shows the detected page language**, with a plain explanation of
  the rule; the options page names the supported lists and the fallback. Both
  are translated into Arabic.
- The iOS Safari web extension stages the same `lib/language.js` module and
  shares the language-aware scorer; its text-check copy now says so.
- **The real-model visual smoke test now covers four synthetic images**, not
  one flat fill: flat green, flat blue, a soft gradient and a hard checkerboard,
  plus the existing person-preference shapes. Nothing is uploaded; the test
  still runs only the packaged engine on loopback.

### Unchanged

- No new extension permission, network request, durable field or browsing
  record. Language tags and the bounded text sample stay in extension memory
  and are sent only between the content script, worker and popup.
- Not language-exclusive matching, and not a claim of complete coverage:
  mixed-language pages, text hidden from rendering, images and videos without
  readable text, and other browsers remain outside this layer.

### Verification

- `./test.sh extension` passes: every JavaScript suite, package check, and the
  evaluation corpus at sensitivity 50/80/100. At 50 the corpus reports 0/22
  adult cases missed and 0/28 benign pages blocked; dilution holds through
  8,000 filler words and every adult tube body remains blocked under a benign
  title/meta.
- New `test/language.test.js` (68 checks) covers tag normalisation, script
  detection, the declared-`en` bypass, view membership, the unknown-language
  fallback and ignored-word invalidation. `feed.test.js` (94 checks) covers
  per-post language signals.
- `./test.sh browser` passes, including the scanner harness, popup and settings
  pages in English and Arabic, feed compatibility, and the expanded real-engine
  visual smoke in CPU mode.
- Package: `dist/hisn-extension-1.3.0.zip`, 91 files, 6.2 MB, SHA-256
  `a8471ab3a3e4db4f61656507c933ec51981264162f2c8d593bd63d33694abcac`.

Version 1.3.0. Unpacked and store-ready; no listing, assigned store ID or
managed force-install is claimed.

## Guardian console — unreleased

### Added

- **Phone–laptop pairing and signed policy.** A P-256 reference protocol in
  `script/guardian_protocol.py`: a `hisn://pair/1` offer, a six-digit SAS that
  both sides derive from ECDH, a device-signed response, and a policy envelope
  with device binding, a replay counter, issue/expiry windows and an ECDSA
  signature. `docs/PAIRING_PROTOCOL.md` is the wire format and threat model;
  `script/test_guardian_protocol.py` has 33 tests and
  `script/test_guardian_console.py` adds 11 file-level tests for a laptop
  console (`keygen`, `offer`, `verify-response`, `sign-policy`,
  `verify-policy`). `android/core/.../GuardianPairing.java` adds the phone-side
  offer parser, response builder/verifier and SAS, checked against a
  Python-generated fixture in both directions; a Java policy verifier checks
  the version-2 flat envelope and its replay/expiry/tamper rules. A
  `android/test-live-pairing.py` run on the API 36 emulator exchanged a Python
  offer with the debug activity, verified the Java response and SAS, and had
  the activity verify a Python-signed policy. On API 31+ the debug identity is
  an EC P-256 key inside AndroidKeyStore (sign + key agreement), persisted
  across runs; older runtimes use an AES-256-GCM-wrapped software key. The
  QR/product pairing UI and applying domain rules are still missing. With
  `--apply-policy`, the same emulator run verified a Python-signed policy and
  applied its tighten-only rules: keep installed, lockdown, strict, blocked-app
  suspension, a blocked package and a blocked domain. The sync read back
  `protected` and Chrome was suspended. Applying a signed domain list with hash
  verification and the product pairing UI remain open.
- **Laptop Android provisioning helper.** `script/guardian_provision.py` reads
  a factory-reset phone, refuses accounts, secondary users, another device
  owner and an unfinished setup wizard, then installs Hisn and appoints it
  device owner only after a typed confirmation. It never wipes, removes
  accounts or uninstalls, and it verifies the appointment by readback.

- **iOS supervised guardian pack.** `profile/make_guardian_pack.py` writes the
  `.mobileconfig` (mode 0600, password inside), a manifest with its SHA-256,
  `GUARDIAN.md` (supervision, verification and recovery steps) and
  `SHA256SUMS`. It refuses `--maximum --allow-app-removal` and never writes the
  password to the manifest or checklist. 12 tests.

## Android 0.9.0 — 2026-11-02

### The protection lock

- **Saved rules are fully frozen while the lock runs.** The in-app commitment
  is now presented as the **protection lock**. Starting one makes every rule on
  the Rules page read-only in both directions: domains, words, blocked and
  inspected apps, the daily schedule, the built-in adult/visual/short-video
  switches, SafeSearch and the gray-screen choice. The earlier rule allowed
  adding restrictions during a commitment; that path is closed because the
  product promise is now "the rules cannot change", not "they cannot weaken".
  Extending the lock is still allowed. `ProtectionLock` holds the pure decision
  and `PolicyStore` refuses the write for every caller, not only the UI.
- **The guardian path remains the one exception.** A verified, signed policy
  from the paired laptop may still add blocked domains and apps while the lock
  runs, through `PolicyStore.updateFromGuardian`. It is tighten-only; it cannot
  remove a rule, turn a switch off or loosen the schedule.
- **The lock arms removal protection on its own.** In the direct build the
  accessibility removal guard now watches Hisn's uninstall, App info, disable,
  Safe Mode and VPN screens whenever a protection lock runs, even if the
  keep-installed switch is off. The boot shield copy and the 15-minute layer
  watchdog follow the same effective state. On a device-owner install Android's
  uninstall block was already applied for the lock; managed lockdown remains
  the separate opt-in. The Play build still cannot block its own removal, and
  the app now says so in the attention list while a lock runs.
- **UI.** Rules gains a visible "Rules are locked" card with the remaining
  time; every editor, switch, row and the schedule button is disabled while it
  is true. The Protection hero names the lock and remaining time, and when
  rules exist without a lock it points to the lock action. The copy across the
  app now says "protection lock" instead of "commitment". The uninstall card
  distinguishes guard-on-for-the-lock, guard-disconnected and ordinary off
  states, and the uninstall dialog explains that the guard is on even when its
  switch reads off. Version 0.9.0, versionCode 25. The Android
  core suite, string parity, debug/test builds, lint and both release
  distribution checks pass; no physical-device run of the lock is claimed.

### Fixed
- **Faster blocking paths.** `DomainPolicy` now keeps a bounded cache of
  repeated DNS decisions, so a familiar hostname skips IDNA/validation and two
  parent lookups. The accessibility guard inspects changed content after 50 ms
  instead of 150 ms and discovers new windows after 250 ms instead of 500 ms.
  Window-state changes were already inspected immediately. This is a latency
  improvement, not a guarantee: an unlisted domain or an uninspected app still
  gets through until another layer acts.

- **`no_control_apps`, not `no_apps_control`.** The managed-lockdown app-control
  restriction used the wrong Android key, so it was silently missing from the
  device-owner readback while every other key was held. An API 36 emulator run
  caught it; the key and every status/test/doc reference were corrected.
- **Emulator evidence recorded.** The same run proved the nine-key managed
  lockdown, strict mode, blocked-app suspension readback and
  `DELETE_FAILED_DEVICE_POLICY_MANAGER` on the app's own uninstall. A fresh
  wiped run also proved release cleanup: suspension resumed, always-on cleared,
  device ownership removed and an ordinary uninstall succeeded. The Settings
  screens were then inspected: Force stop showed “Blocked by work policy”, date
  and time showed “Enabled by admin”, VPN showed the policy block, and Users
  showed Add user/guest “Controlled by admin”. Safe Mode, erase, Private DNS,
  default apps, the tunnel and a physical phone remain unproven.

### Added

- **Physical guard evidence.** On a Samsung SM-G990B2 (Android 16) with the
  direct build, accessibility on and Keep Hisn installed, opening Hisn's App
  info produced the Arabic shield page, and `adb shell pm uninstall
  app.hisn.android` returned `DELETE_FAILED_DEVICE_POLICY_MANAGER` while the
  legacy admin was active. This is physical friction/admin evidence, not a
  device-owner claim. The shield delay was then reduced from 250 ms to 100 ms;
  the physical App-info run still reached `ShieldActivity` reliably at 100 ms.
- **Immediate touch-blocking overlay.** On Android 11+, the accessibility
  service now draws a short-lived `TYPE_ACCESSIBILITY_OVERLAY` over the watched
  Settings screen before pressing Back/Home and starting the shield. It consumes
  taps during the transition, so a second tap cannot reach Uninstall or Force
  stop before the explanation appears. The overlay is removed as the shield
  starts and on service teardown; the system bars are excluded.
- **Release-shaped Android build.** Both the Play-style and `-direct` release
  APKs build and pass `android/check-release.py`; the direct one was then
  test-signed with the debug key and installed on the SM-G990B2. It is
  non-debuggable, has no DUMP permission or debug-only components, and preserves
  app data. The signing key is still a **debug** key, so this is a test install,
  not a publishable artifact.
- **Less text, clearer first screen.** Protection now shows status, three layer
  tiles, a collapsed attention row, one DNS action and app checks. Advanced DNS
  options, guide details, limits, privacy detail and uninstall explanations are
  progressive disclosures. A duplicate DNS recommendation card is hidden when
  the DNS card already owns the next action. The status paragraph and seed line
  are shortened. Principles and sources are recorded in
  [docs/UI_PRINCIPLES.md](docs/UI_PRINCIPLES.md).
- **VPN settings guard.** While **Keep Hisn installed** is on, a Settings VPN
  screen that names Hisn and offers disconnect, forget, always-on, edit or
  settings is closed and replaced by the shield page. It is text/package based
  friction, not system enforcement.
- **Unrestricted-battery setup.** Guide now has **Keep protection running**,
  which opens Android's battery-optimization exemption request and reports
  whether Hisn is exempt. The app does not force the exemption.
- **Direct-build protection page.** When the accessibility screen guard closes
  Hisn's uninstall, App info, storage, device-admin, accessibility or Safe Mode
  screen, a full-screen page now names the reason and holds *Back to Hisn* and
  *Go to home screen* for eight seconds before enabling them. It reads nothing
  itself, never returns to Settings, and the system Home button, dialler and
  emergency screens stay reachable. The previous in-app restriction notice
  remains as the fallback if the page cannot start.
- **Opt-in new-app install lock.** A separate switch, **Also stop new app
  installs (downloads)**, closes the package installer's Install confirmation
  and any Play Store page that offers Install (home, search result or app page)
  so another browser or VPN cannot be installed from those screens. It is
  separate from the keep-installed switch; app updates, Hisn's own update page
  and the update list stay usable. The switch is sealed with the other settings
  and cannot be turned off during a commitment.
- The layer watchdog counts either screen-guard switch as an armed accessibility
  layer, and the status line reports installs-only, both switches and the
  service-off state separately.
- **Managed lockdown closes three more routes.** On a device-owner phone,
  managed lockdown now also holds `no_add_user` on every supported version, on
  Android 9+ (API 28) `no_config_date_time` and, on Android 14+ (API 34),
  `disallow_config_default_apps`; the existing Safe Mode, all-app controls,
  factory reset, VPN and Android 10+ Private DNS keys remain.
  The desired set is gated by API so Android 8 is never asked for a key it does
  not know, and the read-only live status reader expects the same set. A
  device-wide install block (`no_install_apps`) is still not used because it
  would freeze Play updates too. Physical device acceptance is still pending.
- **Optional strict install block.** A separate **Strict** switch adds
  `no_install_unknown_sources_globally` on Android 10+ while managed lockdown
  holds. It is off by default because it also blocks a manual sideloaded Hisn
  update. USB debugging stays available, so ADB verification and recovery still
  work. The status, live reader, readback, release order and English/Arabic
  disclosures include it; a device-wide install block is still excluded.
  Physical device acceptance is still pending.
- **Opt-in blocked-app suspension.** A separate **Also suspend blocked apps in
  Android** switch suspends every installed app in Hisn's block list while
  managed lockdown holds. Suspended apps disappear from the launcher and cannot
  be opened; their data is kept. The status reads `FLAG_SUSPENDED` back;
  unblocking, turning the switch off or releasing managed mode resumes them.
  Cleanup depends on Android listing installed (including suspended) packages,
  and no physical device has been tested.
- **Manual guardian pairing screen.** Guide → **Guardian pairing** now opens a
  product screen where the owner pastes a `hisn://pair/1` offer, sees the
  six-digit SAS and device ID, and copies the response JSON for the laptop. It
  uses the EC-in-AndroidKeyStore identity and sends nothing over the network.
  On the API 36 emulator a pasted Python offer produced a displayed SAS and a
  copied 439-byte response that Python verified. The same screen now accepts a
  signed policy and applies tighten-only rules through a shared applier. On the
  API 36 emulator a Python-signed policy pasted into the product screen reported
  “Policy applied (already-protected)”. Policies may also carry a canonical
  `blocked_domains_sha256`; Python and Java now compute the same hash and the
  applier refuses a mismatch. A camera QR scanner and separate laptop-side
  response/policy transport are still missing.
- **Browser allowlist action.** The browser coverage screen now has **Keep only
  this** on each installed browser. It selects every other installed browser
  for blocking in one tap; saving applies the block and, with suspension on,
  suspends the others. The selection is pure-tested (`BrowserAllowlist`) and is
  explicitly limited to browsers visible at that moment.
- **Optional SafeSearch DNS rewrite.** The Network section has **Force SafeSearch
  on search engines**, off by default. While DNS filtering is on, Hisn answers
  exact Google, Bing, DuckDuckGo and YouTube hostnames with a local CNAME to the
  restricted target; country domains, YouTube APIs and other app endpoints are
  not guessed. The answer is built locally, preserves the question, is limited
  to A/AAAA and never reaches the upstream resolver. `SafeSearchTest` and
  `DnsPolicyRewriteTest` cover the mapping and response; a device test of the
  target certificates and search/playback behavior is still pending.
- **Always-on DNS VPN.** On a device-owner phone with managed lockdown and DNS
  filtering both on, Hisn now asks Android to keep the DNS-only tunnel always
  on (`setAlwaysOnVpnPackage(..., false)`), reads the package back, and clears
  it when DNS is turned off, the lockdown is released or the VPN is revoked.
  The manifest advertises always-on support. This is not a full tunnel and does
  not enable VPN lockdown; non-DNS traffic is never routed through Hisn. The
  live status reports the always-on request separately from the seven user
  restrictions. Physical device acceptance is still pending.

### Verification

- Core tests compile and pass, including new `SelfProtectionScreensTest` cases
  for the installer confirmation, a Play Store detail page, updates, Hisn's own
  reinstall, the store home and switch-independent classification, plus
  `SavedSettingsTest` round-trip and 0.5.3-upgrade checks for `install_lock`.
  String parity passes (383 strings, 14 plurals); the direct debug and
  debug-androidTest APKs build.
- Live on the API 36 emulator (`hisn_ui`) with the direct debug build: Hisn's
  App info was closed and the uninstall reason page appeared; a Play Store app
  page for Chrome was closed and the install reason page appeared; the Arabic
  page rendered RTL; the eight-second hold disabled both buttons; *Back to
  Hisn* opened the app.
- Not checked: the real package-installer confirmation, the switch dialog,
  TalkBack, an OEM skin and a physical device. No version bump, signed build or
  store submission is part of this entry.

## Android 0.8.4 — 2026-11-01

### Added

- **The daily schedule has two modes.** It can keep blocking the apps you
  choose, or block every app except a keep list you choose. The same searchable
  picker edits either list, the row in Rules names the mode, and the editor
  explains both. In allowlist mode the guard keeps the system windows it already
  excludes (settings, launchers, dialer, installers, Hisn) reachable; keeping at
  least one app available is required before the schedule can be saved.
- **Commitment coverage is preserved across a mode switch.** A blocklist may
  become an allowlist only when every app it blocked is still blocked, an
  allowlist may only become another allowlist whose keep list did not grow, and
  the window may not shrink; switching to a mode that blocks fewer apps is
  refused while a commitment runs.
- **The one-second checks stay bounded.** An allowlist is watched inside its
  window and for ten minutes before it; a once-a-minute metadata sweep covers an
  app opened earlier and left untouched, so a window start is still enforced
  without an all-day heartbeat.

### Verification

- Core tests cover allowlist blocking, the ten-minute arming rule, mode-switch
  preservation, the new saved field and the 0.5.3 upgrade default; string parity
  passes (369 strings, 14 plurals).
- Live on the Samsung SM-G990B2 with a sealed test policy for a 12:45–13:15
  window: the kept browser opened normally and a non-kept app was refused by the
  restriction screen. The owner's original schedule was restored byte for byte
  afterwards.
- Version 0.8.4, versionCode 24. No signed store build or broader device
  acceptance is claimed.

## Android 0.8.3 — 2026-11-01

### Added

- **Rules → Browsers without protection names the layer that covers each
  installed app that can open ordinary web links**, or says that none does. The
  answer comes from one pure `BrowserCoverage` rule: blocked wins, then the
  accessibility inspection while the guard is connected, then the DNS layer
  while it runs; otherwise the browser is uncovered. Browsers with no covering
  layer start checked, so **Block selected** refuses them in one tap without
  touching app blocks that are not browsers.
- The screen states its own limits where the decision is made: DNS filters
  names only, so a browser with its own VPN, Tor or private resolver can leave
  that path and belongs in the app block; blocking a browser does not prevent
  installing another one.
- New manifest package-visibility queries for `http` and `https` handlers, used
  only to enumerate what is installed. The example.com probe keeps apps that
  handle a single site out of the list.

### Verification

- `BrowserCoverageTest` passes with the other core tests; string parity holds
  (362 strings, 13 plurals); the debug APK builds and was side-loaded on the
  Samsung SM-G990B2 for a browser-list and one-tap block check.
- Version 0.8.3, versionCode 23. No signed store build or physical-device
  acceptance beyond that local debug install is claimed.

## Seed v5 — 2026-10-30

### Added

- **The bundled core grows from 159,768 to 239,885 domains (+50.1%).** The four
  hand-picked adult sources collapse to 125,309 domains; `core_fill` adds
  114,691 domains that both `blocklistproject-porn` (Unlicense) and
  `oisd-nsfw` (free for personal and commercial use) carry and that also carry
  an adult TLD, a reviewed token, or an IDN label. Selection, capacity and the
  1,261 deferred domains are recorded in the signed manifest and explained in
  [the coverage pass record](docs/COVERAGE_PASS_2026-10-30.md).
- **Safari capacity rises from four to six 40,000-rule parts**, from 160,000 to
  240,000 entries. The bundled core fits with 115 entries of headroom. The
  browser's static ruleset grows from 320 to 480 rules.
- **The mixed ad/tracker snapshot moves out of the core tier.** It stays in the
  full Mac/DNS list; the bundled slots now go to adult domains. The UT1 lists
  remain extended-only because their terms are non-commercial.
- The full list refreshes from 5,248,731 to 5,276,448 domains; the keyword
  layer stays at 5,831 terms.

### Verification

- `./test.sh python seed extension release macos ios` passes; Android core and
  the Gradle debug/release/bundle/lint builds pass. See the coverage record for
  counts and hashes.
- No signed store build, physical-device run, published `lists` generation or
  full-list Mac bundle is claimed.

## Browser extension 1.2.2 — 2026-10-30

### Changed

- Bundles seed v5's 239,885-domain core tier and 480 static DNR rules. The
  packaged store zip is `dist/hisn-extension-1.2.2.zip`, 90 files, 5.9 MB,
  SHA-256 `fd254f2eeb5a3a14ee017281675d12a11ca4a6d355ba075c97bf68a54f04157b`.
  Packaging now fixes timestamps and sorts entries, so two builds of the same
  tree are byte-identical.
- No store listing, real store ID or managed force-install is claimed.

## Mac 1.1.7 — 2026-11-02

### Added

- **Protection authority is read, not self-certified.** Setup now reads whether
  the daily account is an administrator, whether the visible `hisn_guardian`
  account exists with a secure token, and whether the root guardian daemon is
  loaded. The account-split step is complete only when all three are verified.
  A checked manual box can no longer make an administrator account look split.
- **Lock-gated authority and removal.** While a protection lock runs, the app
  disables the Users & Groups shortcut, hides the guardian setup/restore
  commands, and prevents the account-split confirmation from changing. After
  the lock, the commands return. Restoring administrator rights remains a
  guardian action; Hisn never stores the password.
- The Setup card states plainly that Hisn does not create the guardian account
  or store its password. The app cannot make itself an administrator; a hidden
  self-granted admin would be a backdoor.
- **Lock page warning.** Before a lock starts, the app warns when the daily
  account is still an administrator, so the person sees that the lock does not
  stop their own admin access. The Setup authority card is the fix.
- **UI/UX pass.** The sidebar has a persistent protection/lock status footer
  and a lock badge; the toolbar carries a lock action and page subtitle;
  Overview has one primary action; duplicate lock CTAs were removed; new Go and
  Protection menus add ⌘1–⌘5, ⌘R and ⌘⇧L. The default window grows to 980×700.
  Render captures are in `design/macos-ui-2026-11-02/`; no policy changed.

Version 1.1.7, build 11. `./test.sh macos` passes 508 tests including
`MacAuthorityTests`; localization passes 561 strings with Arabic. Unsigned and
unnotarized; no account split or standard-account run is claimed.

## Mac 1.1.6 — 2026-10-30

### Changed

- Bundles seed v5 and its 239,885-domain core fallback, plus regenerated
  third-party notices naming the two selected fill sources.
- The full 5,276,448-domain list still arrives only through a published
  generation; this build does not bundle it. Version 1.1.6, build 10. No
  signed package or device result is claimed.

## iPhone & iPad 1.1.4 — 2026-10-30

### Changed

- **Six Safari blockers instead of four**, each still capped at 40,000 rules.
  The project generator, readiness model, setup copy (English and Arabic) and
  tests all derive from the blocker count. Seed v5 fits with 115 entries of
  headroom.
- Version 1.1.4, build 5. Simulator-tested only; no Apple team, Family Controls
  entitlement, TestFlight build or physical-device acceptance is claimed.

## Android 0.8.2 — 2026-10-30

### Changed

- Bundles seed v5's 239,885-domain core tier (239,880 after the client's
  IP-entry exclusion) and names `blocklistproject-porn` and `oisd-nsfw` in the
  English and Arabic licenses/sources screen.
- Version 0.8.2, versionCode 22. Unsigned preview; no signed production build,
  Play listing or device-owner run is claimed.

## Browser extension 1.2.1 — 2026-10-30

### Added

- **Store-ready metadata.** `short_name` and `homepage_url` in the manifest;
  version 1.2.1. The packaging script still strips the development key and
  comments, so the published ID differs from the unpacked ID.
- **An honest options footer.** Settings states that matching stays on the
  device, shows the installed version, links the privacy policy and support
  page, and says that browser policy can make the extension non-removable
  without Hisn being able to detect it from the page.
- **A full store-readiness record.** [EXTENSION_STORE.md](docs/EXTENSION_STORE.md)
  has listing copy, permission justifications, privacy answers, the managed
  `ExtensionSettings` policy, reviewer steps and post-publication ID wiring.
- Removed the unused `offscreen` permission. The extension now declares only
  `declarativeNetRequest`, `storage`, `alarms`, `nativeMessaging`, `scripting`
  and `<all_urls>`.

### Verification

- `extension/test/run.sh` passes, including English/Arabic key and placeholder
  parity.
- `./test.sh extension` passes (unit suites, package check, benign corpus).
- `./test.sh browser` settings page and related harnesses pass in Helium
  0.20.2.1; `package.sh` produces a store-clean zip.
- No store listing, real ID, review result or managed force-install is claimed.

## Mac 1.1.5 — 2026-10-30

### Added

- **A root LaunchDaemon backs the login agent.** `/Library/LaunchDaemons/app.hisn.guardian.plist`
  runs a root-owned `/Library/Application Support/Hisn/hisn-guardian` loop. It
  reads the protected account from a root-owned config, checks the GUI domain and
  re-bootstraps `gui/<uid>/app.hisn.agent` when a standard user has booted it
  out. It logs only account/action/time to `guardian.log`, waits quietly while
  the account is logged out, and refuses `root`/`loginwindow`/malformed names.
- The package `postinstall` and `macos/install_update.sh` install the daemon and
  its config; the release package carries the daemon files;
  `macos/install.sh --remove-agent` removes the daemon as well as the agent.
- **Guardian status is visible.** Details shows whether the root daemon is
  installed and the last local action (agent restored, reload failed, account
  missing or config unreadable).
- **Details shows list freshness.** The last update time, a warning after seven
  days without an update, and a failed-update line; the previous signed
  generation remains enforced.
- **It does not fight a normal quit.** The login agent's `KeepAlive` handles a
  crash or force-quit; a normal quit outside a lock stays quit. The daemon does
  not duplicate the app's filter-health XPC model.
- **Licenses and sources.** Details opens the bundled `Notices.txt` with every
  list source, its licence/URL and the redistributed texts.
- Version 1.1.5, build 9. See [the 1.1.5 record](docs/MACOS_1.1.5.md).

### Verification

- `./test.sh release` passes (33 tests), including the new
  `release/test_guardian.py` (9 tests: plist shape, config parsing, refused
  accounts, logged-out quiet, missing-agent reload, failed reload, healthy agent,
  installer wiring, shell syntax).
- `./test.sh macos` passes (498 tests plus the Arabic-string check, 536 strings).
- `release.sh --smoke` builds the unsigned package; its scripts payload contains
  the daemon script, plist and helper. Nothing was installed.
- No root install, standard-account bootout, reboot or notarized-package result
  is claimed.

## iOS/iPadOS 1.1.3 — 2026-10-30

### Added

- **Privacy manifest in every bundle.** `Hisn/PrivacyInfo.xcprivacy` ships in the
  app, four Safari blockers, `HisnActivityMonitor` and `HisnText`: no tracking,
  no collected data, and required reasons for app-only/app-group `UserDefaults`
  and the monotonic boot clock. `Info.plist` declares
  `ITSAppUsesNonExemptEncryption` false.
- **Signed Safari list age.** `prepare_rules.py` copies the signed seed's
  `built_at` into `rules-metadata.json`; the list section shows **Built** and
  warns after 90 days that a newer signed build is the only Safari list update.
  `SeedFreshness` accepts ISO offset timestamps and plain dates, clamps future
  dates to zero and has Swift tests for the boundary.
- **Settings links and version.** Settings shows the app version/build and links
  to the repository privacy policy and support page.
- **Optional supervised-profile hardening.** `make_ios_profile.py` gains six
  opt-in, supervised-only keys: account changes, passcode changes, app
  installation, App Clips, enabling restrictions and automatic date/time. Each
  is off by default because it can remove a recovery path; the guide documents
  the tradeoff and the Python tests cover both states.
- `ios/test_privacy.py` checks the manifest, export declaration, required
  reasons and membership in all seven bundles.
- **Licenses and sources.** Settings opens the bundled `Notices.txt` with every
  list source, its licence/URL, the redistributed texts and the visual runtime
  notices. Generated by `script/notices.py`, tested on both Apple apps.
- Version 1.1.3, build 4. See [the 1.1.3 record](docs/IOS_1.1.3.md).

### Verification

- `./test.sh ios` passes on the iPhone 17 / iOS 26.5 simulator (183 hosted
  tests) and the 32 Python rule, privacy and localization checks pass.
- No Apple team, Family Controls entitlement, TestFlight build or physical-device
  result is claimed.

## Android 0.8.1 — 2026-10-30

### Added

- **The 1.0 list-update decision is recorded and visible.** Android receives
  list changes only with a new signed APK; there is no runtime list download.
  `SeedFreshness` reads the signed seed's build date and warns after 90 days
  with the age and the update path. A plain date or ISO offset timestamp is
  accepted, future times clamp to zero, and an unparsable time shows no warning
  rather than a wrong one.
- **The watchdog is visible and can ask for notification permission.**
  Protection shows the last check time, a lost layer, or notifications being
  off. Enabling **Keep Hisn installed** on Android 13+ requests
  `POST_NOTIFICATIONS`; a denial is shown, not treated as protection. A healthy
  check no longer erases the last-run time.
- The in-app Guide now covers the optional managed lockdown, the 15-minute
  check, the lack of always-on/lockdown VPN, and the APK-only list path.
- **The Android 1.0 network decision is recorded.** The DNS-only VPN stays
  owner-started/start-on-boot, not always-on or lockdown. `setGlobalPrivateDns`
  and a full tunnel are deferred; a stopped VPN is made visible by the watchdog
  and cannot be replaced by an ordinary user when managed lockdown holds
  `no_config_vpn`.
- The device runner compiles read-only watchdog-expectation and seed-age checks.
- Version 0.8.1, versionCode 21. See
  [the 0.8.1 record](docs/ANDROID_0.8.1.md).

### Verification

- `android/test-core.sh` passes with `SeedFreshnessTest`, `LayerWatchTest`, the
  API-aware `ManagedRestrictionsTest` and English/Arabic parity (280 strings).
- `assembleDebug assembleDebugAndroidTest assembleRelease lintDebug` passes; so does
  the direct flavor (`-PhisnDistribution=direct`).
- `check-release.py` passes on the unsigned release APK: 0.8.1 (21), no
  debuggable/test-only flags, no DUMP permission, no debug components.
  SHA-256 `1e00bf2eb870f693a8e49d097835b516ca20780cea343436b1023624452813c4`.
- `check-release.py --expect-distribution play` or `direct` makes the Play/direct
  split enforceable; both a Play and a direct artifact check pass, and the
  direct artifact is refused as Play. Its SHA-256 is
  `16878b73c66d0501a5735980a1dad96b58b9a2bef71dfb832fc2a172c41485b0`.
- `test_check_release.py` covers the suffix rule in `test-core.sh`.
- `bundleRelease` passes (unsigned AAB SHA-256
  `b45ab915c176910d4e6b49f91f94a466a7c524ca163a7ed5cc4acb404369bfcc`).
- No device run is claimed.

## Android 0.8.0 — 2026-10-30

### Added

- **Private DNS in managed lockdown.** On Android 10+ (API 29), the device-owner
  lockdown now applies and reads back `disallow_config_private_dns` as a fifth
  restriction. Android 8/9 expect four keys; the policy takes the API level, and
  the live checker builds the expected set from the device SDK. One missing or
  extra key is out of sync, never a protected state.
- **A background layer watchdog.** A persistent 15-minute `JobScheduler` job
  compares the layers the owner left armed (the direct build's accessibility
  guard while **Keep Hisn installed** is on, and DNS while left on) with the live
  state. A lost layer gets one local notification, repeats at most every twelve
  hours and clears on recovery; it never restarts a service, reads screen text
  or contacts a server. Scheduling is refreshed on app open, DNS start/stop,
  boot and keep-switch changes.
- Version 0.8.0, versionCode 20. See
  [the 0.8.0 record](docs/ANDROID_0.8.0.md).

### Verification

- `android/test-core.sh` passes with `LayerWatchTest`, the API-aware
  `ManagedRestrictionsTest` and English/Arabic string parity (275 strings).
- `assembleDebug assembleDebugAndroidTest assembleRelease lintDebug` passes.
- No device-owner or real layer-loss result is claimed; the disposable-device
  checklist is in the 0.8.0 record.

## Unreleased — `release/1.0`, 2026-10-29

### Added

- **Android 0.7.0 (versionCode 19): optional managed lockdown.** On a
  device-owner phone, a second explicit opt-in applies and reads back four
  Android restrictions: Safe Mode, app uninstall/disable/clear-data/force-stop
  for every app, the Settings factory reset and VPN configuration changes. Both
  managed choices are saved in one write; release clears restrictions in reverse
  order, then the uninstall block, then device ownership. Status shows ON, Off,
  idle or out of sync from Android's own readback. The broad app-controls
  restriction is disclosed before it is applied. No device acceptance is
  claimed. See [the 0.7.0 record](docs/ANDROID_0.7.0.md).
- **One machine-checked version set.** `versions.json` pins the extension, Mac,
  iOS/iPad and Android versions; `script/versions.py` checks each platform
  manifest and regenerates [docs/VERSIONS.md](docs/VERSIONS.md). A conflict in
  an Xcode target or a mismatch with the manifest fails `./test.sh python`.
- **Per-surface coverage page.** `script/coverage.py` derives
  [docs/COVERAGE.md](docs/COVERAGE.md) from the signed seed and the static DNR
  ruleset: 159,768 core domains, 5,248,731 full-list domains, 5,831 terms, 320
  static and 116 keyword DNR rules. It states which client carries which tier.
- **Privacy policy and support page.** [PRIVACY.md](PRIVACY.md) records the
  on-device matching, the absence of accounts/telemetry, the only routine
  network requests and every permission. [SUPPORT.md](SUPPORT.md) has setup
  links, troubleshooting, safe reporting and the consent/emergency rules.
- **Android live managed checker.** `test-live-managed-status.py
  --expect-lockdown` reads the four restrictions back without changing policy;
  the debug exporter now includes the held and desired sets.

### Changed

- The full list count in the docs is corrected to the signed artifact
  (5,248,731 domains, not 982k). The core tier is 159,768 and the keyword layer
  is 5,831 terms; see [COVERAGE.md](docs/COVERAGE.md).
- The iOS hosted suite now runs to completion on the iOS 26.5 simulator
  (181 tests, `** TEST SUCCEEDED **`), closing the M0 runner gap recorded in the
  1.0 plan. The suite is slow because the render tests are real UI passes; that
  is time, not a hang.
- A single bare `./test.sh` run passes end to end, and `./test.sh browser`
  passes every harness in Helium 0.20.2.1 (Chromium 156), including the DNR and
  SafeSearch rules and the live-page evasion checks. Android
  `assembleDebug assembleDebugAndroidTest lintDebug` passes with the final
  strings.
- The macOS suite is green at 495 tests plus the Arabic-string check; Python,
  seed, extension, release and Android core suites are green on the same tree.

### Fixed

- Android managed-mode status can no longer show PROTECTED when a requested
  restriction is missing, or READY while an old restriction remains. The
  combined state is OUT_OF_SYNC and the periodic repair runs.
- Managed-mode release now clears restrictions before the uninstall block, so a
  failed ownership release cannot leave the phone hardened but unmanaged.

## 2026-10-28 — Extension 1.2.0

- The block page explains a signed-list block (`reason=domain`) in English and
  Arabic, instead of falling through to the generic message.
- A native-messaging reply that omits `mode` can no longer reset Strict to
  Standard; `pollNative` leaves absent fields alone.
- The status shows the installed list version/date, warns after 14 days, and
  names the bundled-only state. Settings has **Update block list**, serialised
  against the automatic alarm; a failed update keeps the current generation.
- Facebook `share/reel/…` links are covered by the short-video rules and the
  in-page matcher.
- Version 1.2.0. Tests: extension unit suites, block page/settings/popup and DNR
  browser harnesses, package and evaluation corpus. See
  [EXTENSION_1.2.0.md](docs/EXTENSION_1.2.0.md).

## 2026-10-28 — Android 0.6.0

- Opt-in device-owner uninstall protection, with platform readback, an in-app
  status/release path and boot/foreground reconciliation after a commitment
  expires.
- The uninstall-screen guard and boot shield for the direct build; the pause
  tally and short-video blocking.
- Device evidence on the Samsung SM-G990B2 is recorded as far as it goes; the
  phone was not a device owner, so no blocked-uninstall result is claimed. See
  [ANDROID_0.6.0.md](docs/ANDROID_0.6.0.md).

## 2026-10-26 — iOS/iPadOS 1.1.2

- A commitment that holds: trusted Apple server time, a restored filter,
  layer-loss alerts and the passcode vault for a date-limited exit without a
  second person.
- Honest Screen Time status, readable colours, Dynamic Type and iPad layout;
  **My blocked sites** through the Screen Time adult-content filter.
- HisnActivityMonitor app shields, usage budgets and the selected-app picker.
- Development build; no Apple team, Family Controls entitlement or device
  acceptance is claimed. See [UPDATE_1_1_2.md](docs/UPDATE_1_1_2.md) and the
  [iOS plan](docs/IOS_IMPROVEMENT_PLAN.md).

## 2026-10-26 — macOS 1.1.4 (build 8)

- Ships the Persian/Urdu text folds to installed Macs, so the Mac filter and the
  extension make the same decision on the shared normalisation cases.
- Reads the lock inside `ScreenGrayscale.set` rather than as a default argument,
  fixing the grayscale state read.
- Unsigned, unnotarized development build; the guardrail and filter tests pass
  (495 tests, Arabic-string check). See [ROAD_TO_1.0.md](docs/ROAD_TO_1.0.md).

## 2026-10-01 — signed seed bundle v4

- Core tier: 159,768 high-confidence domains; full build: 5,248,731 domains;
  keyword layer: 5,831 terms (4,100 Arabic, 1,447 Franco-Arabic, 145
  English, 100 German and 39 French/Spanish/Italian/Portuguese) plus 116 host
  terms.
- Ed25519-signed manifest, rollback floor and a fail-closed client. No `lists`
  branch has been published yet, so every client is still on this seed.

Earlier per-version records (Android 0.3.1 through 0.5.5, extension 1.1.8
through 1.1.12, and the 1.1.1/1.1.2 updates) are in the [docs index](docs/README.md).
