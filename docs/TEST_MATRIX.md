# Test matrix — what is demonstrated, what is inferred

Passing unit tests do not prove bypass resistance. This is the ledger of what
each claim rests on. **Demonstrated** means a repeatable check exists and ran;
**inferred** means read from the code; **manual** means a procedure exists but
no automation, and it must be run on a real Mac by a person.

## Automated (run by `./test.sh` and CI)

| Claim | Evidence | Suite |
|---|---|---|
| Both clients decide every fixture case identically (35 cases) | `policy_cases.json` asserted against the store (Swift) and against the exact DNR rules (JS) | `PolicyContractTests`, `policy.test.js` |
| Filter is never looser than the browser | browser-only cases asserted blocked by the store | `PolicyContractTests` |
| A settings message cannot touch internal state | schema refusals | `policy.test.js` |
| Concurrent writers cannot lose a restriction | lock ordering | `serial.test.js` |
| Lock ends at the effective deadline in the filter | matured release → `strictModeActive` false | `SelfReleaseTests` |
| Rollback protection survives a filter restart | `versionFloor` | `BlocklistLoadTests` |
| Scanner: equal-length replacement, non-settling feed, malformed URL, worker restart, stale verdict, subframe, SPA route | fake DOM + fake clock | `scan.test.js` |
| Concatenated spellings of reviewed phrases match, share the cap, and disappear when ignored | compiled alias table + score assertions | `compound.test.js` |
| Persian/Urdu letter forms normalise identically in Python, JavaScript and Swift | shared `normalize_cases.json` fixture | `test_terms.py`, `normalize.test.js`, `KeywordLayerTests` |
| Reviewed evasion vocabulary does not restore an ignored/weak term and fits its bitmask | alias/verdict assertions | `text-evasion.test.js` |
| Every static network regex compiles, and obfuscated/encoded-query rules match only where intended | real Chromium `isRegexSupported` + server request log | `test/browser/dnr.sh` |
| Generated evasion/encoded-query rules are the output of the reviewed tables and signed vocabulary | `--check` generators | `evasion.py --check`, `encoded_queries.py --check`, `test_evasion.py`, `test_encoded_queries.py` |
| The service worker stays split into focused modules without import cycles | source-shape assertions | `worker-layout.test.js` |
| Same scanner scenarios in a real Chromium DOM | harness page, headless Chrome in CI | `test/browser/run.sh` |
| Extension scanner, signature page, settings, popup, block page, DNR/SafeSearch and live-page evasion in a real Chromium | Helium 0.20.2.1 (Chromium 156) on 2026-10-29, every harness PASS | `./test.sh browser` |
| Extension store metadata, footer wording and permission set are present and localized | i18n key/placeholder parity, options footer assertions, packaged-manifest check | `extension/test/run.sh`, `check_package.py` |
| Portable rules: export shape, schema/version refusal, invalid-entry counting, add-only merge, a lock withholding allowances, the 200-word ceiling and app-managed refusal | pure model plus a real file through the settings harness | `transfer.test.js`, `test/browser/settings.html` |
| A generation is installed whole or not at all (browser) | planner refusals | `generation.test.js` |
| Every shipped artifact matches the signed seed manifest and the sources | `seed.py verify` | `test_seed.py`, CI |
| Clients download from the same place; requested artifacts are built | config pins | `test_build.py` |
| Known adult domains blocked, critical infrastructure not blocked, in a real build | built `dist/` | `test_build.py` (CI builds first) |
| Every test class is discovered | `unittest discover` in `test.sh` | `test.sh python` |
| The default suite runs end to end on this Mac | `./test.sh` (python, seed, extension, release, macOS, iOS) on 2026-10-29, exit 0 | `./test.sh` |
| The pinned version set matches every platform manifest, and the coverage page matches the signed seed/ruleset | `versions.py --check`, `coverage.py --check` | `test_versions.py`, `test_coverage.py`, `test.sh python` |
| iOS/iPadOS app, Safari blockers and activity monitor build and run their hosted tests | iOS 26.5 simulator (iPhone 17), Xcode 27.1, `** TEST SUCCEEDED **` on 2026-11-03, 206 tests | `./test.sh ios` |
| iOS privacy manifest and export declaration match the code and every bundle | plist checks + generator/project membership | `ios/test_privacy.py`, `test.sh python` |
| Apple apps bundle complete, current third-party notices | generated files + Swift bundle reads | `script/test_notices.py`, `testBundledThirdPartyNoticesArePresent` |
| iOS signed Safari list age and 90-day warning | signed `built_at` in metadata + Swift age/clamp/boundary tests | `ios/test_rules.py`, `testSeedFreshness…` |
| Supervised iOS profile optional hardening keys are opt-in and written only when asked | profile generator tests for default absence and explicit values | `profile/test_make_ios_profile.py`, `test.sh python` |
| iOS guardian pack: profile, manifest hash, password only in the profile, maximum keys, refusals and file modes | generated pack + plist inspection | `profile/test_make_guardian_pack.py`, `test.sh python` |
| macOS app, filter and strings | Xcode test + Arabic export on 2026-11-03 | `./test.sh release macos` (521 tests, 589 strings) |
| Published list v6: signed manifest and artifacts reachable, exact bytes, degraded build refused | `curl` + `cmp` + `keys.py verify` + `publish_guard.py` + 43 real-artifact checks | [LIST_PUBLICATION_2026-11-03.md](LIST_PUBLICATION_2026-11-03.md) |
| Mac pause: tally merge/day boundary/cap, 4/6-second breath, reminder rotation, stored shape, both-language breathing and needs renders | pure model + `NSHostingView` renders | `PauseTests`, `./test.sh macos` |
| Android daily time limits: 5–1,440-minute validation, one limit per app, saved-rule freeze, guardian tighten-only, Usage-access health and enforcement decision | pure model + sealed settings + API 36 emulator | `AppTimeLimitTest`, `SavedSettingsTest`, `CoreDeviceTestRunner`; one 5-minute test-only fixture-app pass |
| Android managed-uninstall state, commitment guard, readback mismatch and release guard | pure matrix + isolated preference fixture | `android/test-core.sh`, `CoreDeviceTestRunner` on the 0.6.0-direct Samsung build |
| Android managed lockdown: opt-in matrix, API-gated add-user/date-time/Private DNS/default-app and strict install, exact held set, missing/extra detection, apply/release order and combined state | pure `ManagedRestrictions` + `UninstallProtection` + settings round trip | `android/test-core.sh` (`ManagedRestrictionsTest`, `UninstallProtectionTest`, `SavedSettingsTest`) |
| Android always-on DNS VPN: opt-in matrix, own-package detection, another app’s always-on left alone and a stale Hisn request detected | pure `AlwaysOnVpn` decisions | `android/test-core.sh` (`AlwaysOnVpnTest`) |
| Android blocked-app suspension: opt-in matrix, installed intersection, two-way diff and exact readback | pure `BlockedAppSuspension` decisions | `android/test-core.sh` (`BlockedAppSuspensionTest`) |
| Android browser allowlist: keep-only selects every other installed browser; unknown/single-browser and exactly-one-kept cases | pure `BrowserAllowlist` decisions | `android/test-core.sh` (`BrowserAllowlistTest`) |
| Android signed domain list hash: canonical sorted/newline SHA-256 matches Python and rejects a mismatch | pure `DomainListHash` and Java applier | `android/test-core.sh` (`DomainListHashTest`), emulator `test-live-pairing.py --apply-policy` |
| Android product pairing screen: paste offer, build response, show SAS/device ID, copy response | manual UI; debug trampoline and clipboard reader for automation | emulator 2026-11-01: Python offer pasted, SAS displayed, copied response verified and SAS matched |
| Android product signed-policy import: paste policy, verify signature/binding/counter, apply tighten-only rules | manual UI; debug harness covers the same applier | emulator 2026-11-01: pasted Python policy for the persisted device ID, Apply policy reported “already-protected” |
| Android SafeSearch: exact Google/Bing/DuckDuckGo/YouTube mapping, country/suffix/API names left alone, local CNAME A/AAAA answer and no upstream call | pure mapping + DNS response bytes | `android/test-core.sh` (`SafeSearchTest`, `DnsPolicyRewriteTest`) |
| Laptop Android device-owner provisioning: account/user/owner/setup refusals, exact install→owner→launch order, no destructive command and owner readback | pure facts + a fake adb runner | `script/test_guardian_provision.py`, `test.sh python` |
| Guardian pairing and signed policy: offer parsing, SAS equality and MITM mismatch, response proof, device binding, replay, expiry and canonical JSON | P-256 reference implementation + fixed clock | `script/test_guardian_protocol.py`, `test.sh python` |
| Guardian laptop console: key file mode, encrypted key, offer, response SAS, policy sign/verify, wrong device/replay and rules validation | file-level CLI + P-256 | `script/test_guardian_console.py`, `test.sh python` |
| Android phone-side pairing: Python fixture verification, Java-built response, SAS equality, tampered/nonce/extra-field rejection, generated key | P-256 Java fixture + Python reference | `android/test-core.sh` (`GuardianPairingTest`) |
| Android signed policy: Python v2 fixture, rules bytes, replay, expiry, future issue, tampered rules/signature, unknown fields and non-canonical integers | P-256 Java verifier + fixed clock | `android/test-core.sh` (`GuardianPolicyTest`) |
| Android runtime pairing and policy: Python offer, Java response/SAS, Python verification, v2 policy signed by Python and verified by the activity; identity persists across runs in an EC Keystore key on API 31+, tighten-only managed rules applied and read back, including a blocked domain | API 36 emulator + debug activity | `android/test-live-pairing.py --apply-policy` (emulator PASS 2026-11-01, `reason=protected`, Chrome suspended) |
| Android layer watchdog: expected/running sets, lost layers, stable signature and 12-hour notification cadence | pure `LayerWatch` decisions | `android/test-core.sh` (`LayerWatchTest`) |
| Android bundled-list age: whole days, date forms, clamping and the 90-day warning | pure `SeedFreshness` | `android/test-core.sh` (`SeedFreshnessTest`) |
| Android Arabic/Persian/Urdu folds, custom-rule parity, glued phrase aliases and evasion recovery | shared fold table, actual-seed fixtures and boundary samples | `android/test-core.sh` |

## Manual (procedures exist; must be run on hardware)

| Scenario | Procedure | Status |
|---|---|---|
| **App-group sharing between filter (root) and app** | `docs/TAMPER_MODEL.md` → "Assumption to verify first" | **not yet run — blocks every claim below** |
| Profile, incognito, DoH, Private Relay, native host presence, admin status | `macos/verify_enforcement.sh` (read-only) | exists |
| Filter enforces a lock after restart | start lock → reboot → `defaults read group.app.hisn filterStrictActive`; browse a listed site | manual |
| Sleep/wake | lock → sleep 10 min → wake → heartbeat within 30 s | manual |
| Private browsing | with the profile: incognito unavailable; without: `verify_enforcement.sh` reports the gap | manual |
| Alternative browsers | the filter blocks in Safari/Firefox (socket level); `verify_enforcement.sh` lists native-host coverage per Chromium browser | manual |
| VPN / proxy | socket filter sees flows before the tunnel: test with a system VPN on, listed site blocked | manual |
| Direct IP | **known open in blocklist mode** (`FilterDataProvider.handleNewFlow`: no hostname → allow); strict mode drops it | inferred, documented |
| Clock changes | `trustedNow` high-water mark: set clock back 1 y → countdown frozen | `LockStore` unit tests for the mark; end-to-end manual |
| Interrupted update | kill the app mid-download → previous generation intact (staging dir swap) | inferred from `writeToContainer`; manual |
| Missing components | delete user native-host manifest → extension strict after 5 min; quit app → filter keeps enforcing | inferred; manual |
| Connections open before a lock starts | browser: open tabs are navigated to the block page on rule change (`tabsToBlock`); **Mac: existing sockets are not closed** — a stream already playing continues until it reconnects | browser demonstrated in unit tests; Mac limitation documented |
| Android device-owner uninstall block | disposable device: provision device owner, enable Keep Hisn installed, attempt uninstall; then test commitment-expiry reconciliation, release and removal | **emulator PASS 2026-11-01**: API 36 device owner, `pm uninstall app.hisn.android` refused while managed, and returned `Success` after release; a physical phone remains **not run** |
| Android layer watchdog and status | disposable device: turn on Keep, grant notifications, run `adb shell cmd jobscheduler run -f app.hisn.android 1401`; check the last-run line; disable accessibility and run again; deny notifications and check the warning | **not yet run** — pure tests and build only |
| Android stale bundled-list warning | set the device date forward past 90 days after the signed seed build, reopen Protection | **not yet run** — `SeedFreshnessTest` covers the decision only |
| Android managed lockdown | disposable device owner: enable Keep + Managed lockdown; try uninstall, Safe Mode, Settings erase, a new user, a manual date change (Android 9+), a default-app change (Android 14+), a VPN change and (Android 10+) a Private DNS change; read back with `test-live-managed-status.py --expect-lockdown`; then release and verify the expected restrictions are gone | **emulator PARTIAL 2026-11-01**: nine-key readback, own-app uninstall refusal, release cleanup and Settings UI refusals for app controls, clock, VPN and users passed; Safe Mode, erase, Private DNS, default apps and a physical phone remain **not run** |
| Direct-build guard: uninstall, App info, storage, admin, accessibility, VPN settings, Safe Mode and install screens classified; ordinary Settings left usable | pure `SelfProtectionScreens` matrix | `android/test-core.sh` (`SelfProtectionScreensTest`) |
| Direct-build guard on a phone; battery exemption request | physical direct build with accessibility and keep switch on | **physical PARTIAL 2026-11-02**: SM-G990B2 App Info → shield; `adb uninstall` refused with legacy admin active. VPN-settings shield, battery request and Safe Mode remain not run |
| Android always-on DNS VPN | disposable device owner: enable DNS filtering and Keep + Managed lockdown; read back the always-on package and `alwaysOnMatch`; stop DNS and verify the request is cleared; try another VPN and verify it is not claimed as Hisn’s | **not yet run** — code, lint and pure tests only; no provisioned device |
| Android blocked-app suspension | disposable device owner: enable Keep + Managed lockdown + suspension; block two apps, verify both are suspended and gone from the launcher; unblock one and verify it resumes; clear app data and verify the other still resumes; release managed mode | **emulator PARTIAL 2026-11-01**: two blocked packages read `suspended=true`; release resumed Chrome; launcher, unblock and data-clear paths remain **not run** |
| Android browser allowlist | disposable device owner: open Rules → Browsers, tap **Keep only this** on one browser, save, enable suspension; verify the others are blocked/suspended and the kept one opens; install another browser and check the documented allowlist limit | **not yet run** — no device; the selection logic is pure-tested only |
| Android SafeSearch rewrite | disposable phone: enable DNS filtering and the SafeSearch switch; search Google, Bing, DuckDuckGo and YouTube; verify restricted results and that sign-in/playback still work; use a country Google domain to confirm it is unchanged | **not yet run** — no device; enabled only by an explicit switch |
| Android strict managed lockdown | disposable device owner: enable Keep + Managed lockdown + Strict; try a sideloaded APK install; read back `managedStrict` and the nine/eight/six/five-key set; turn Strict off and verify only the basic set remains | **not yet run** — code, lint and pure tests only; no provisioned device |
| iPhone/iPad lock levels | disposable device: vault seal/reveal, Screen Time readback, supervised profile removal password, app deletion refusal | **not yet run** — simulator tests only; no physical device or entitlement |
| Mac daemon on a standard account | standard user boots out `gui/<uid>/app.hisn.agent`; daemon restores it; log out/in; reboot; admin bootout recovery | **not yet run** — unit tests only |
| Second-person onboarding | a non-developer follows `docs/SETUP.md` on a clean Mac and iPhone/Android | **not yet run** |

## Isolated environment for disruptive checks

None of the manual rows may be run on a developer's own account: they change
accounts, install profiles and start real locks. Use a separate macOS user or
a VM with a throwaway Apple developer team, and `setup_guardian.sh --dry-run`
before any real run.
