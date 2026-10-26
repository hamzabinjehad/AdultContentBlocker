# Hisn documentation

Every document in this folder, grouped by topic. *Historical* records describe a
past version; *superseded* documents point to their replacement. Start with the
[project README](../README.md) for the overview.

## Start here

- [Setting it up, in order](SETUP.md): The ordered setup steps: second person, network, Mac app, extension, profile, Screen Time, phone and account split.
- [Threat model](THREAT_MODEL.md): Treats the user as the adversary and covers coverage gaps, tamper resistance, failure modes and what not to build.
- [Why Hisn beside Screen Time](POSITIONING.md): Explains why Hisn adds coverage (Arabic terms, page text, non-Safari Mac browsers) and leaves the lock to Screen Time.
- [Development notes](DEVELOPMENT.md): Building, signing, the seed bundle, the extension, the Mac app and native messaging.

## Using Hisn

- [Using Hisn on a Mac](USER_GUIDE.md): Installing the Mac app, trying a lock, changing rules, connecting a browser, and fixing common problems.

## Mac

- [Mac filter health contract](HEALTH.md): The status keys the Mac filter, updater and bridge write, and how the app should report them.

## Browser extension

- [Browser management and guard limits](CHROME_ENFORCEMENT.md): What the Mac browser guard and consented managed browser policies add, supported browsers, and remaining removal limits.
- [The browser blocking pipeline](BLOCKING_PIPELINE.md): Code map of every browser layer, which generated ruleset owns it, and the invariants a change must keep.
- [Browser-first protection for mixed sites](BROWSER_FIRST_PROTECTION.md): Hiding single explicit posts on mixed-content sites like X instead of blocking the whole site, plus setup and checks.
- [Person area, effect and media choices](PERSON_PRESENTATION.md): Face-only versus whole-media masking, blur versus opaque, image/video scope, and appearance-estimate rules through 1.1.9.
- [Settings inside the popup](POPUP_SETTINGS.md): Protection settings opened inside the toolbar popup, the minimal popup redesign, and one-tap current-site blocking.
- [Using Hisn in a browser](EXTENSION_USAGE.md): Loading the extension, standard versus strict mode, settings, and how behavior changes when the Mac app connects.

## iPhone and iPad

- [Apple platform structure](APPLE_PLATFORMS.md): How macOS, iOS and iPadOS code is laid out, what each protects, and cross-platform development rules.
- [iPhone and iPad end-goal plan](IOS_IMPROVEMENT_PLAN.md): The lock ladder on iPhone (vault, guardian, supervised, time-locked), current gaps per layer, and milestones M0-M6.
- [Supervised iPhone or iPad setup](IPHONE_SUPERVISED.md): Step-by-step guardian setup using Apple Configurator so Hisn cannot be removed from a supervised device, plus limits.

## Android

- [Android implementation status](ANDROID_IMPLEMENTATION_STATUS.md): Table of each Android protection layer, what code exists and how far it was verified, as of 0.5.0.
- [Android tamper resistance scope](ANDROID_TAMPER_RESISTANCE.md): What Android personal mode can and cannot do to resist removal, Play policy limits, and managed-device mode.

## Network and routers

- [Network-first protection plan](NETWORK_FIRST_PROTECTION.md): Assessment and plan for blocking at the router first, with Mac and phone layers that work independently.
- [Router identification and guides](ROUTER_COMPATIBILITY.md): How the app identifies a router and which manufacturer setup routes it offers for family DNS protection.

## Security, tamper model and commitments

- [Blocking decision contract](POLICY.md): The rules the Mac filter and browser extension must both follow for lists, allowlist, locks and strict mode.
- [Self-control commitment](SELF_CONTROL_COMMITMENT.md): Rules for voluntary time-locked commitments on Mac, iPhone and iPad: what is implemented, what it does not promise, and remaining gaps.
- [Tamper model](TAMPER_MODEL.md): An audit of every store enforcement reads, who can write it, and what each kind of user can undo.

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
- [BlockerHero inspection and resulting changes](REA_BLOCKERHERO_2026-10-19.md): REA static inspection of BlockerHero 1.5.0 and the independently written Hisn Android fixes it prompted. *Historical.*
- [Similar extensions source comparison](EXTENSION_COMPARISON_2026-10-15.md): Patterns studied in NSFW Filter, HaramBlur and LeechBlock, what Hisn adopted, and license boundaries. *Historical.*
- [Visual hide-first research](VISUAL_HIDE_FIRST_RESEARCH.md): Research on hiding media until checked, and the resulting pre-display gate and X playback repair. *Historical.*

## Release records

Per-version change and verification notes, newest first.

### Android

- [Android 0.5.2 release notes](ANDROID_0.5.2.md): Latest Android build: DNS bypass hardening, separated-spelling and Arabic name fixes, image checks on feeds, 60-second hold and the commitment reboot fix.
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

- [Extension 1.1.12 release notes](EXTENSION_1.1.12.md): Glued phrase matching, Persian/Urdu normalisation, generated obfuscation and encoded-query rulesets, and an organised worker.
- [Extension 1.1.11 release notes](EXTENSION_1.1.11.md): Text scanner hardening: phrase tree, disguised-word detection, French form fix and corpus checks at three sensitivities. *Historical.*
- [Extension 1.1.10 release notes](EXTENSION_1.1.10.md): Faster model preparation and a larger verdict cache that removes repeat image checks, with measured results. *Historical.*
- [Extension 1.1.9 release notes](EXTENSION_1.1.9.md): Mirrored-crop confirmation before excluding a face and stable face masks across identical rechecks, with test evidence. *Historical.*
- [Extension 1.1.8 release notes](EXTENSION_1.1.8.md): Safer women/men face preferences, pixel-verified reveals, verdict cache, toolbar warnings, and how to reload the update. *Historical.*

### Cross-platform updates

- [Hisn 1.1.2 update notes](UPDATE_1_1_2.md): New popup and visual-check interface, detection changes and limits, and how to update Mac, browser and phone. *Historical.*
- [Hisn 1.1.1 update notes](UPDATE_1_1_1.md): Installing the 1.1.1 Mac build, reloading browser extensions, and the signed-list publishing key prerequisite. *Historical.*

Raw device evidence for the Android records is kept next to them as JSON:
[visual acceptance](ANDROID_VISUAL_ACCEPTANCE_2026-10-21.json),
[visual benchmark](ANDROID_VISUAL_BENCHMARK_2026-10-22.json) and
[WebGL/CPU comparison](ANDROID_VISUAL_BACKEND_2026-10-24.json).
