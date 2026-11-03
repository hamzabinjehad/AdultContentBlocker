# Hisn documentation

Every document in this folder, grouped by topic. *Historical* records describe a
past version; *superseded* documents point to their replacement. Start with the
[project README](../README.md) for the overview.

## Start here

- [Setting it up, in order](SETUP.md): The ordered setup steps: second person, network, Mac app, extension, profile, Screen Time, phone and account split.
- [1.0 readiness](1.0_READINESS.md): One finish line across the four platforms: versions, local evidence, artifacts, unproven claims and the owner/device actions that remain.
- [Publish readiness, 2026-11-02](PUBLISH_READINESS_2026-11-02.md): The full verification run, reproducible and unsigned artifacts, what is genuinely publishable in the repository, and the owner actions still required.
- [Versions and release state](VERSIONS.md): The pinned version/build of every platform, generated and checked from the manifests.
- [Content coverage by surface](COVERAGE.md): The core, full and keyword tiers, what each client actually carries, and what the table does not claim.
- [Privacy policy](../PRIVACY.md) and [support](../SUPPORT.md): The public statements, troubleshooting and safe reporting route.
- [Threat model](THREAT_MODEL.md): Treats the user as the adversary and covers coverage gaps, tamper resistance, failure modes and what not to build.
- [Why Hisn beside Screen Time](POSITIONING.md): Explains why Hisn adds coverage (Arabic terms, page text, non-Safari Mac browsers) and leaves the lock to Screen Time.
- [Development notes](DEVELOPMENT.md): Building, signing, the seed bundle, the extension, the Mac app and native messaging.

## Using Hisn

- [Using Hisn on a Mac](USER_GUIDE.md): Installing the Mac app, trying a lock, changing rules, connecting a browser, and fixing common problems.
- [Apps to consider blocking](APPS_TO_CONSIDER.md): A category-based, consent-aware checklist for Android app blocking and iPhone/iPad app shields, including TV/catch-up, generic players and live chat/cam services, with what each block costs and what not to block.

## Mac

- [Mac UI/UX pass, 2026-11-02](MACOS_UI_UX_2026-11-02.md): Persistent sidebar status, a lock action in every window toolbar, a single Overview primary action, Go/Protection menus with keyboard shortcuts, and RTL render captures. No policy changes.
- [Mac filter health contract](HEALTH.md): The status keys the Mac filter, updater and bridge write, and how the app should report them.

## Browser extension

- [Connecting the extension to the app](EXTENSION_BRIDGE.md): native messaging, the two extension IDs, a read-only connection check, and the managed-policy path that makes removal policy-blocked.
- [Browser management and guard limits](CHROME_ENFORCEMENT.md): What the Mac browser guard and consented managed browser policies add, supported browsers, and remaining removal limits.
- [The browser blocking pipeline](BLOCKING_PIPELINE.md): Code map of every browser layer, which generated ruleset owns it, and the invariants a change must keep.
- [Browser-first protection for mixed sites](BROWSER_FIRST_PROTECTION.md): Hiding single explicit posts on mixed-content sites like X instead of blocking the whole site, plus setup and checks.
- [Person area, effect and media choices](PERSON_PRESENTATION.md): Face-only versus whole-media masking, blur versus opaque, image/video scope, and appearance-estimate rules through 1.1.9.
- [Settings inside the popup](POPUP_SETTINGS.md): Protection settings opened inside the toolbar popup, the minimal popup redesign, and one-tap current-site blocking.
- [Using Hisn in a browser](EXTENSION_USAGE.md): Loading the extension, standard versus strict mode, settings, and how behavior changes when the Mac app connects.
- [Windows: making the extension hard to remove](WINDOWS_SETUP.md): Registry policy that force-installs the extension and locks Chrome, Edge and Brave, with the account split it depends on.

## iPhone and iPad

- [Apple platform structure](APPLE_PLATFORMS.md): How macOS, iOS and iPadOS code is laid out, what each protects, and cross-platform development rules.
- [iPhone and iPad end-goal plan](IOS_IMPROVEMENT_PLAN.md): The lock ladder on iPhone (vault, guardian, supervised, time-locked), a per-layer audit with code references, which Android features iOS can match with public APIs, and milestones M0-M6 with what is done.
- [App Store readiness](IOS_STORE.md): Entitlement, listing copy, privacy label, App Review notes, screenshots, TestFlight and submission checklist.
- [Supervised iPhone or iPad setup](IPHONE_SUPERVISED.md): Step-by-step guardian setup using Apple Configurator so Hisn cannot be removed from a supervised device, plus limits.

## Android

- [Google Play readiness](ANDROID_STORE.md): Play vs direct builds, listing copy, Data safety answers, permission declarations, reviewer steps and submission checklist.
- [Android implementation status](ANDROID_IMPLEMENTATION_STATUS.md): Table of each Android protection layer, what code exists and how far it was verified, as of 0.9.0.
- [Android live protection test](ANDROID_LIVE_TEST_2026-10-30.md): Emulator-only 2026-10-30 evidence for text and whole-app blocking, the DNS local-block/route checks and a verified one-day device-owner uninstall lock. Records the test-only fixture limitation and why the physical Samsung was not touched.
- [Android UI and UX refresh](ANDROID_UI_REFRESH.md): The 2026-10-30 presentation pass: bottom navigation, a status-first Protection page with a concrete attention list, switches, searchable app lists, day/night palette, adaptive icon and splash, and the accessibility work. No policy or consent text changed.
- [Android tamper resistance scope](ANDROID_TAMPER_RESISTANCE.md): What Android personal mode can and cannot do to resist removal, Play policy limits, and managed-device mode including the 0.8.0 lockdown restrictions and layer watchdog.

## Network and routers

- [Network-first protection plan](NETWORK_FIRST_PROTECTION.md): Assessment and plan for blocking at the router first, with Mac and phone layers that work independently.
- [Router identification and guides](ROUTER_COMPATIBILITY.md): How the app identifies a router and which manufacturer setup routes it offers for family DNS protection.

## Security, tamper model and commitments

- [Blocking decision contract](POLICY.md): The rules the Mac filter and browser extension must both follow for lists, allowlist, locks and strict mode.
- [Self-control commitment](SELF_CONTROL_COMMITMENT.md): Rules for voluntary time-locked commitments on Mac, iPhone and iPad: what is implemented, what it does not promise, and remaining gaps.
- [Tamper model](TAMPER_MODEL.md): An audit of every store enforcement reads, who can write it, and what each kind of user can undo.
- [Phone–laptop pairing protocol](PAIRING_PROTOCOL.md): Wire format for the guardian offer, six-digit SAS, signed policy envelope, replay and expiry rules, threat matrix, and the reference implementation with 33 protocol tests and 11 console tests.

## Releasing and testing

- [Dogfooding protocol](DOGFOOD.md): A three-month plan for living with Hisn: what to log, which bypasses to try, and what blocks release.
- [Extension signing key](EXTENSION_KEY.md): Where the extension signing key lives, why it pins the extension ID, and how to regenerate it safely.
- [Release runbook](RELEASE_RUNBOOK.md): Step-by-step instructions for preparing, building, notarizing and checking a signed Mac release, plus the blockers still open.
- [Release validation record](RELEASE_VALIDATION.md): A validation template for each release candidate, followed by dated engineering-check and extension-candidate records.
- [Test matrix](TEST_MATRIX.md): A ledger of which claims rest on automated tests and which still need manual checks on real hardware.
- [Android live accessibility test](ANDROID_LIVE_GUARD_2026-10-19.md): October 19 Samsung test of the accessibility guard on harmless screens: fixes found and final pass results. *Historical.*
- [Android visual classifier benchmark](ANDROID_VISUAL_BENCHMARK_2026-10-22.md): Baseline for 0.5.0 image classifier on 12 benign views: zero false blocks, about two-second median on CPU. *Historical.*

## Design

- [Hisn design guide](DESIGN_GUIDE.md): Apple HIG-informed design rules for Mac, iPhone, iPad and extension, with redesign passes and a release checklist.

## Plans and roadmaps

- [Road to 1.0](ROAD_TO_1.0.md): The integrated finish plan: current verified state, the per-platform removal-lock ladder, content-coverage gates, milestones, backlog and owner actions. Start here when deciding what to build next.
- [Guardian mode plan](GUARDIAN_MODE_PLAN.md): The post-1.0 managed-device route: device owner and supervision levels, the guardian console and phone-laptop pairing, what each platform can actually enforce, the coverage matrix and the phased build.
- [Android 1.0 plan](ANDROID_1.0_PLAN.md): Fixed Android 1.0 finish line: what is in and out of scope, current state, milestones M1-M5, signing steps.
- [End-goal plan](END_GOAL_PLAN.md): Ties all plans to one testable goal: no adult content and no removal before a chosen date, with measurable gates.
- [Improvement roadmap](HISN_IMPROVEMENT_ROADMAP.md): Ordered next work on word blocking, evasion matching, context scoring and cross-platform verification, plus what each pass delivered.
- [Publishing plan](PUBLISHING_PLAN.md): Release tracks, ship-blockers and beta and launch gates for taking the repository to a public release.
- [REA-informed extension and Mac plan](REA_FUNCTIONALITY_PLAN.md): Prioritized extension and Mac backlog from REA study of Hisn, HaramBlur and SelfControl; partly done in 1.1.3.
- [Tamper resistance next steps](TAMPER_RESISTANCE_NEXT.md): Recent anti-removal recovery work on browser, Mac and mobile, and the priority order for further hardening.
- [Visual protection plan](VISUAL_PROTECTION_PLAN.md): Contract, library reuse decisions, implemented local visual engine, and remaining acceptance gates for image and video checks.
- [Android competitive testing plan](ANDROID_COMPETITIVE_TEST_PLAN.md): Early October 18 plan for safely testing HaramBlur and BlockerHero, plus a pre-app Hisn roadmap, now outdated. *Superseded by [ANDROID_1.0_PLAN.md](ANDROID_1.0_PLAN.md).*
- [From personal use to publishing](LAUNCH.md): An early checklist for publishing: Apple signing, extension store, list licences, privacy policy and pricing decisions. *Superseded by [PUBLISHING_PLAN.md](PUBLISHING_PLAN.md).*

## Research

- [Android research and execution plan](ANDROID_RESEARCH_PLAN.md): October 18 BlockerHero and HaramBlur study, Android platform limits and an early phased plan, now replaced. *Superseded by [ANDROID_1.0_PLAN.md](ANDROID_1.0_PLAN.md).*
- [REA Android design audit, 2026-11-03](ANDROID_REA_SCREENZEN_2026-11-03.md): ScreenZen/BlockerHero research and the built Android 1.0.0 inventory behind the daily time-limit work. Design research, not a runtime or security assessment.
- [REA iOS design audit, 2026-11-03](REA_AUDIT_IOS_2026-11-03.md): ScreenZen/comparable-app research, the built HisnMobile 1.3.0 inventory, and the pause, quiet-hours and protection-hero work it informed. Design research, not a security or runtime assessment.
- [REA design audit of Screen Zen and Hisn, 2026-11-03](REA_AUDIT_SCREENZEN_2026-11-03.md): Static inventory, a Screen Zen extension graph and one accessibility/screenshot observation; what Hisn adopted (an on-demand pause) and what it deliberately did not. Design research, not a security assessment.
- [BlockerHero inspection and resulting changes](REA_BLOCKERHERO_2026-10-19.md): REA static inspection of BlockerHero 1.5.0 and the independently written Hisn Android fixes it prompted. *Historical.*
- [Similar extensions source comparison](EXTENSION_COMPARISON_2026-10-15.md): Patterns studied in NSFW Filter, HaramBlur and LeechBlock, what Hisn adopted, and license boundaries. *Historical.*
- [Visual hide-first research](VISUAL_HIDE_FIRST_RESEARCH.md): Research on hiding media until checked, and the resulting pre-display gate and X playback repair. *Historical.*

## Release records

Per-version change and verification notes, newest first.

### Cross-platform

- [Blocklist v6 publication, 2026-11-03](LIST_PUBLICATION_2026-11-03.md): The first signed `lists` generation — 5,274,046 domains, 239,885 core, 5,831 terms — with artifact hashes, HTTP 200 verification, the guard refusal of a degraded first build, and the remaining scheduled-publication and client-install gaps.
- [Seed v5 coverage pass](COVERAGE_PASS_2026-10-30.md): The bundled core grows from 159,768 to 239,885 domains on the extension, six Safari blockers and the Android seed; the full list refreshes to 5,276,448. Selection rules, licensing boundary, deferred domains, hashes and every suite are recorded. No signed build or device is claimed. *(Historical: no `lists` branch existed on 2026-10-30; v6 was published 2026-11-03.)*

### Mac

- [Mac 1.1.8 release notes](MACOS_1.1.8.md): An on-demand pause window — a 30-second guided breath, rotating reminders, four needs and a local day/count tally — reachable from Overview, the menu bar and ⇧⌘P. It changes no rule or layer. Unsigned/unnotarized; no physical-Mac result claimed.
- [Mac 1.1.7 release notes](MACOS_1.1.7.md): A verified protection-authority card (standard daily account, visible guardian administrator with a secure token, loaded root daemon) and lock-gated authority/removal controls. Unsigned/unnotarized; no split or device run claimed.
- [Mac 1.1.6 release notes](COVERAGE_PASS_2026-10-30.md): Seed v5 and its 239,885-domain core fallback, with notices naming the selected fill sources. The full list still needs a published generation. Unsigned/unnotarized.
- [Mac 1.1.5 release notes](MACOS_1.1.5.md): A root LaunchDaemon restores the login agent if a standard user boots it out, with installer wiring, tamper logging, list-freshness and licenses rows, and a removal path. Unsigned/unnotarized; standard-account and reboot checks pending.

### iPhone and iPad

- [iOS 1.4.0 release notes](IOS_1.4.0.md): Custom `ShieldConfiguration`/`ShieldAction` extensions name the saved rule in English or Arabic and offer only Close, with no unlock; eleven privacy-manifest bundles and two new Family Controls targets. Simulator-tested only; no entitlement, signed build or device.
- [iOS 1.3.0 release notes](IOS_1.3.0.md): An on-demand pause, a calmer protection hero and daily quiet hours (including a two-leg cross-midnight DeviceActivity schedule and both-clock reconciliation). Simulator-tested only; no entitlement, signed build or device.
- [iOS 1.2.0 release notes](IOS_1.2.0.md): Protection lock freezes blocked-site and app-limit rules while a commitment runs, with layer recovery still available; English and Arabic lock copy. Simulator-tested only.
- [iOS 1.1.4 release notes](COVERAGE_PASS_2026-10-30.md): Six 40,000-rule Safari blockers instead of four, seed v5, and capacity/readiness checks derived from one blocker count. Simulator-tested; no device, team or entitlement yet.
- [iOS 1.1.3 release notes](IOS_1.1.3.md): A privacy manifest in the app and every extension, export-compliance declaration, the signed Safari list's build date with a 90-day warning, an offline licenses-and-sources screen, and privacy/support/version rows in Settings. Simulator-tested; no device, team or entitlement yet.
- [Hisn 1.1.2 update notes](UPDATE_1_1_2.md): New popup and visual-check interface, detection changes and limits, and how to update Mac, browser and phone. *Historical.*

### Android

- [Android 1.0.0 release notes](ANDROID_1.0.md): Signed direct-sideload APK at versionCode 26, per-app daily time limits backed by Android's usage record, Play APK/AAB built but not uploaded, and API 36 emulator evidence for the limit card and one live 5-minute enforcement pass. No physical-phone run claimed.
- [Android 0.9.0 release notes](ANDROID_0.9.0.md): The protection lock freezes every saved rule in both directions, arms the direct build's removal guard from the lock itself, and makes the Play build's uninstall limit explicit while the lock runs. API 36 emulator UI and instrumentation evidence; no physical device claimed.
- [UI principles and the 2026-11-02 pass](UI_PRINCIPLES.md): progressive disclosure, deferred mobile copy and the screens changed.

- [Physical direct-build guard run, 2026-11-02](ANDROID_LIVE_GUARD_2026-11-02.md): Samsung SM-G990B2 App Info → shield, and `adb uninstall` refused with the legacy admin active; no device owner.
- [Physical-device validation runbook](PHYSICAL_DEVICE_VALIDATION.md): disposable-phone checklist for managed mode, Settings refusals, pairing, tunnel and release.
- [Android managed-mode emulator run, 2026-11-01](ANDROID_LIVE_TEST_2026-11-01.md): API 36 emulator device-owner evidence for nine lockdown restrictions, strict mode, blocked-app suspension and the uninstall refusal; records the `no_apps_control` → `no_control_apps` key bug and the remaining physical-device gaps.
- [Android 0.8.2 release notes](COVERAGE_PASS_2026-10-30.md): Seed v5's 239,885-domain core, and the two selected fill sources named in the licenses/sources screen. Unsigned preview; device-owner run still pending.
- [Android 0.8.1 release notes](ANDROID_0.8.1.md): The APK-only list-update decision with a 90-day bundled-list age warning, a visible background-check line, and the notification permission request when Keep Hisn installed is enabled. Device acceptance pending; the ordinary build is still removable.
- [Android 0.8.0 release notes](ANDROID_0.8.0.md): Private DNS joins managed lockdown on Android 10+, and a persistent 15-minute watchdog notices a stopped accessibility guard or DNS VPN and posts a local layer-loss notice. *Historical.*
- [Android 0.7.0 release notes](ANDROID_0.7.0.md): Optional managed lockdown on a device-owner phone: Safe Mode, all-app uninstall/data controls, Settings factory reset and VPN changes, saved together with the uninstall block and read back from Android. *Historical.*
- [Android 0.6.0 release notes](ANDROID_0.6.0.md): Opt-in device-owner uninstall protection, in-app status/release, boot reconciliation and host tests. Device acceptance pending; the ordinary build is still removable. *Historical.*
- [Android 0.5.4 reliability (part A)](ANDROID_0.5.4_RELIABILITY.md): Second Cloudflare Families address with failover, network changes limited to the network in use, damaged settings that keep a commitment, and a release APK check. *In progress.*
- [Android 0.5.3 release notes](ANDROID_0.5.3.md): Setup guide ready per chosen layer, failures kept visible, real DNS notification and refusal reasons, screen-off sampling stop, Arabic parity, licenses screen and an Android CI job.
- [Android 0.5.2 release notes](ANDROID_0.5.2.md): DNS bypass hardening, separated-spelling and Arabic name fixes, image checks on feeds, 60-second hold and the commitment reboot fix. *Historical.*
- [Android 0.5.1 release notes](ANDROID_0.5.1.md): WebGL-first visual classifier with CPU fallback, optional release signing, WebGL versus CPU timings. *Historical.*
- [Android 0.5.0 release notes](ANDROID_0.5.0.md): Adds opt-in on-device image and video sampling of chosen apps on Android 14+, with live Samsung results. *Historical.*
- [Android 0.4.6 release notes](ANDROID_0.4.6.md): Detects complete two-word explicit media labels in English and Arabic; synthetic corpus reaches 23/25, zero false blocks. *Historical.*
- [Android 0.4.5 release notes](ANDROID_0.4.5.md): Better adult-text matching for punctuation-obfuscated terms; synthetic corpus improves from 7/25 to 21/25 detected. *Historical.*
- [Android 0.4.4 release notes](ANDROID_0.4.4.md): Adds an optional daily time window blocking chosen apps, commitment-safe edits and a VPN-settings reminder. *Historical.*
- [Android 0.4.3 release notes](ANDROID_0.4.3.md): Debug-only private-browser diagnostic that temporarily blocks www.example.org to confirm browsers use Hisn DNS. *Historical.*
- [Android 0.4.2 release notes](ANDROID_0.4.2.md): Adds bounded TCP port 53 DNS handling beside UDP, truncation retries and limits, with Samsung verification. *Historical.*
- [Android 0.4.1 release notes](ANDROID_0.4.1.md): Network-change recovery: drops stale TLS connections and route results when the phone's underlying network changes. *Historical.*
- [Android 0.4.0 release notes](ANDROID_0.4.0.md): Guided setup model, opt-in English/Arabic adult-text checks, DNS route check and Samsung socket fix, with verification. *Historical.*
- [Android 0.3.1 release notes](ANDROID_0.3.1.md): Adds one-second foreground rechecks for selected apps and a dashboard that reports each layer's real health. *Historical.*

### Browser extension

- [Extension 1.5.0 release notes](EXTENSION_1.5.0.md): Portable rules: export the five hand lists to a versioned JSON file and import one add-only; a lock withholds new allowances and corrections while applying new blocks and words; an app-managed browser refuses the import. No new permission or network path. Development candidate; no listing yet.
- [Extension 1.4.0 release notes](EXTENSION_1.4.0.md): UI/UX pass with one status hero, one-word layer states, less copy in English and Arabic, calmer information notices and a single action hierarchy. Design captures in `design/extension-ui-2026-11-02/`. Development candidate; no listing yet.
- [Extension 1.3.0 release notes](EXTENSION_1.3.0.md): Language-aware page-text scoring chooses the vocabulary from the page's own text, keeps strong labels across languages, falls back to the full list when unsure, and adds a detected-language row to the popup. *Previous development candidate.*
- [Extension 1.2.2 release notes](COVERAGE_PASS_2026-10-30.md): Seed v5's 239,885-domain core tier and 480 static DNR rules; the packaged zip is hashed in the record. *Historical.*
- [Extension 1.2.1 release notes](EXTENSION_1.2.1.md): Store metadata, an options footer with the on-device statement, version and privacy/support links, and a store-readiness record. Development candidate; no listing yet.
- [Chrome Web Store readiness](EXTENSION_STORE.md): Listing copy, permission justifications, privacy answers, managed force-install policy, reviewer steps and post-publication ID wiring.
- [Extension 1.2.0 release notes](EXTENSION_1.2.0.md): Signed-list block-page reason, Strict-mode preservation across a mode-less bridge reply, list age plus a manual update action, and Facebook share/reel coverage. *Historical.*
- [Extension 1.1.12 release notes](EXTENSION_1.1.12.md): Glued phrase matching, Persian/Urdu normalisation, generated obfuscation and encoded-query rulesets, and an organised worker.
- [Extension 1.1.11 release notes](EXTENSION_1.1.11.md): Text scanner hardening: phrase tree, disguised-word detection, French form fix and corpus checks at three sensitivities. *Historical.*
- [Extension 1.1.10 release notes](EXTENSION_1.1.10.md): Faster model preparation and a larger verdict cache that removes repeat image checks, with measured results. *Historical.*
- [Extension 1.1.9 release notes](EXTENSION_1.1.9.md): Mirrored-crop confirmation before excluding a face and stable face masks across identical rechecks, with test evidence. *Historical.*
- [Extension 1.1.8 release notes](EXTENSION_1.1.8.md): Safer women/men face preferences, pixel-verified reveals, verdict cache, toolbar warnings, and how to reload the update. *Historical.*

### Cross-platform updates

- [Hisn 1.1.1 update notes](UPDATE_1_1_1.md): Installing the 1.1.1 Mac build, reloading browser extensions, and the signed-list publishing key prerequisite. *Historical.*

Raw device evidence for the Android records is kept next to them as JSON:
[visual acceptance](ANDROID_VISUAL_ACCEPTANCE_2026-10-21.json),
[visual benchmark](ANDROID_VISUAL_BENCHMARK_2026-10-22.json) and
[WebGL/CPU comparison](ANDROID_VISUAL_BACKEND_2026-10-24.json).
