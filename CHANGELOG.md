# Changelog

Newest first. Versions are per platform; the pinned set is checked in
[versions.json](versions.json) and rendered in [docs/VERSIONS.md](docs/VERSIONS.md).
Per-release evidence lives in `docs/` and is linked from each entry. A version
bump without an entry here is an unfinished release.

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
