# End-goal plan: no adult content, and no removal until the chosen date

Written 2026-10-24. This plan ties together the existing plans
([roadmap](HISN_IMPROVEMENT_ROADMAP.md), [visual](VISUAL_PROTECTION_PLAN.md),
[tamper resistance](TAMPER_RESISTANCE_NEXT.md), [Android 1.0](ANDROID_1.0_PLAN.md))
around one goal. It does not mark any of their release gates complete.

## The goal, stated so it can be tested

> On every device the person uses, adult content does not reach the screen —
> and, if they choose, neither do images of women (or men, or anyone). The
> person cannot remove or weaken that protection on their own before a date
> they chose in advance. After that date they can.

Two words in that sentence carry the honesty: **on their own** and **in
advance**. Whoever holds a device's administrator, recovery, Apple Account or
supervision credentials can always undo software on it; no product changes
that ([threat model](THREAT_MODEL.md), Part 2). What Hisn can do is make sure
that nobody holds those credentials *alone, in the moment* — a second person
holds them, or a time lock does.

Person hiding remains a separate, optional viewing preference. A predicted
woman or man is an estimate from a face model, not an identity and not an
adult-content verdict ([person presentation](PERSON_PRESENTATION.md)).

## How we know we are getting closer

Each workstream below ends in a measurable gate. The scoreboard:

| Measure | Today | Target for "done" |
|---|---|---|
| Adult text corpus: misses / benign blocks | 0/18 and 0/23 on a 41-case synthetic set | ≥ 500 labelled real-world cases per language (ar/en/de), ≤ 2 % misses, ≤ 1 % benign blocks |
| Adult images: recall / false hides | Android benign set 0/12 false hides; adult recall unmeasured | Licensed adult + benign benchmark, ≥ 95 % recall, ≤ 3 % false hides, per platform |
| Person hiding (faces) | Whole-media blur; faces only | Face *and* body regions; measured on a consented public set |
| Time to hide | Content can appear before a verdict | Hide-first: nothing unverified is shown on supported surfaces |
| Devices covered | Mac, Chromium, Safari (iOS/Mac), Android preview | Every device the person uses passes the coverage checklist (M-C below) |
| Removal before the date | Possible for an admin / unsupervised owner | Requires another person or a time lock on every covered device |

## Workstream A — sites and network (blocking)

Already: signed 160k-domain seed and 982k published list, strict allowlist
mode, SafeSearch, custom sites on every platform (this release), Mac socket
filter, family DNS profiles, router guidance.

1. **Same custom list everywhere.** One signed, user-owned list of extra sites
   and words synced between Mac, extension and phone through a file the person
   exports/imports (no account, no server). Gate: an entry added on one device
   is enforced on the others after import; removal still obeys each device's lock.
2. **New-domain coverage.** Ship the published list's daily delta to the iOS
   Safari blockers through an app update path with signature checks (today the
   phone list only changes with a new app build). Gate: rollback rejected,
   unsigned delta rejected, four parts stay under capacity.
3. **Direct-IP and encrypted-DNS gaps.** On Mac strict mode already drops
   unnamed flows; add an opt-in "unknown destinations need a name" rule outside
   strict mode with an exception list for system services. Gate: no break of
   App Store, iCloud, updates, FaceTime in a 7-day dogfood.
4. **Apple URL Filter (OS 26).** Prototype full-URL filtering on iPhone/Mac
   ([network-first plan](NETWORK_FIRST_PROTECTION.md#mac-enforcement-and-stronger-url-coverage)).
   Gate: signed build blocks an adult path on an allowed host in Safari and one
   third-party app.

## Workstream B — words (text)

Already: shared scorer for Chromium/Safari, 5.5k Arabic dialect terms, evasion
aliases, X/Twitter per-post hiding, Android accessibility word guard.

1. **Corpus first.** Build the labelled corpus above from public pages only
   (never private messages), with medical, religious, recovery and news
   negatives. Every scorer change runs against it in CI; a regression fails.
2. **Context and evasion** — rows 1–3 of the [roadmap](HISN_IMPROVEMENT_ROADMAP.md):
   mixed scripts, spaced letters, hashtags, split nodes; local context so an
   explicit post is not washed out by benign padding.
3. **More mixed-content sites.** Per-post adapters for Reddit, Instagram web,
   Telegram web, YouTube comments — each only after testing its real DOM.
4. **Mac native apps.** A Mac equivalent of Android's word guard: the
   Accessibility API reads visible text in apps the person selects (Telegram,
   Discord, Mail) and covers the window when the scorer flags it. Opt-in,
   on-device, password/secure fields skipped. Gate: fixtures like Android's
   2026-10-19 live-guard record.

## Workstream C — visual (images, video, person hiding)

Already: opt-in local NSFW + face models in Chromium/Safari, hide-first for
pending media, face-only or whole-media masks, Android 14+ selected-window
sampling with WebGL backend (121 ms median).

1. **Hide-first everywhere it can run.** Unverified media stays covered until a
   verdict on Chromium, Safari and Android selected apps; a failed or slow
   verdict stays covered. Gate: 0 adult frames visible before verdict in the
   benchmark's video set.
2. **Body-level person hiding.** Add a person/pose detector so the chosen
   preference covers bodies, not just faces; when a body has no visible face,
   "everyone" hides it and "women/men" says it cannot estimate. Gate: measured
   on a consented, licensed public set; limits shown in settings.
3. **Video.** Sample faster when a video is playing in view; pause and cover on
   an adult verdict; never auto-reveal.
4. **Mac all-apps visual layer.** ScreenCaptureKit sampling of selected app
   windows with the same local models and an overlay that covers flagged
   regions — the Mac counterpart of Android's visual guard. Screen Recording
   permission, on-device only, nothing stored. Gate: benchmark parity with the
   browser engine; CPU budget under 10 % on Apple silicon.
5. **Benchmark.** A licensed adult/benign image and video set, held outside
   the repository, scored per release per platform. No accuracy claim without it.

## Workstream D — removal: "only after a time"

Already: Mac guardian account split, root-owned filter authority, fixed-horizon
locks, 48-hour delayed self-release, partner approval, removal-password
profiles; iPhone commitment, Screen Time filter, child authorization,
supervised-device profile (this release); Android commitment.

The missing piece is the solo person with no second human. **A time lock can
stand in for that person**:

1. **Passcode vault (iPhone/iPad, then Mac) — next to build.** Hisn generates
   a random Screen Time passcode, shows it once while the person enters it in
   Settings › Screen Time, then seals it until the commitment's end date. The
   guide sets *Content & Privacy › App Store Purchases › Deleting Apps: Don't
   Allow* and *Lock Screen Time Settings*. Result: Hisn and every other app
   cannot be deleted, and Screen Time cannot be changed, until the date —
   on an ordinary unsupervised iPhone. Limits to state in the app: an adult
   who set the passcode can reset it with their Apple Account password
   (Family Sharing moves that to the organizer); erasing the phone removes it.
   Gate: unit tests for "never revealed before the deadline, always after",
   storage damage never reveals early, clock changes never reveal early.
2. **Time-locked removal passwords (Mac, supervised iPhone).** Profile removal
   passwords and the guardian admin password generated by Hisn and encrypted
   to a future time with drand timelock encryption (`tlock`): no one, Hisn
   included, can decrypt until the public beacon reaches that round. Gate:
   offline encryption with pinned chain parameters, decryption only after the
   round, interoperable with the reference `tle` tool.
3. **Android Device Owner edition.** A separate build provisioned on a reset
   phone that blocks its own uninstall, enforces always-on VPN lockdown and
   blocks safe boot/factory reset from Settings until the commitment date
   ([Android tamper resistance](ANDROID_TAMPER_RESISTANCE.md)). Gate: physical
   provisioning, release and recovery on a disposable phone.
4. **Emergency exit stays.** Every lock keeps a slow, social route (48-hour
   release, partner approval, or Apple/Google account recovery) and a cap of
   90 days per step, extendable. A lock with no exit is the lock people refuse
   to install ([threat model](THREAT_MODEL.md), Part 4).

## Workstream E — every device

Row 15 of the threat model ("use another device") defeats everything above.

- **M-C coverage checklist** in each app: lists the person's other devices and
  marks each as protected only after its own app reports a verified state.
- **Router DNS** for devices Hisn cannot run on (TVs, consoles, guests), with
  verified per-model guides ([router compatibility](ROUTER_COMPATIBILITY.md)).
- **One commitment date across devices** through the same export/import file as
  Workstream A, so a lock set on the phone also covers the Mac.

## Order of work

| Step | Deliverable | Platform | Can be verified in CI? |
|---|---|---|---|
| 1 | Passcode vault + guide (D1) | iPhone/iPad | Logic yes; on-device steps no |
| 2 | Labelled text corpus + CI gate (B1) | All | Yes |
| 3 | Hide-first audit and video sampling (C1, C3) | Chromium, Safari | Yes (browser harness) |
| 4 | Time-locked removal passwords (D2) | Mac, supervised iPhone | Yes (crypto round-trip) |
| 5 | Custom list export/import (A1, E) | All | Yes |
| 6 | Body-level person hiding (C2) | Chromium, Safari | Partly; needs licensed set |
| 7 | Mac word guard (B4) and visual layer (C4) | Mac | Build yes; behaviour needs a Mac |
| 8 | Signed iOS list deltas (A2) | iPhone/iPad | Yes |
| 9 | Android Device Owner edition (D3) | Android | Needs SDK and a disposable phone |
| 10 | Real-device acceptance matrix | All | No — physical devices |

## Rules that do not change

- Everything stays on the device: no browsing logs, no uploaded images.
- No hidden admin accounts, exploits, or disabling OS security to resist removal.
- No claim of 100 % blocking or permanent undeletability.
- Weakening waits for the lock; tightening is always allowed.
