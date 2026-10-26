# Hisn for iPhone and iPad: end-goal plan

Written 2026-10-26 against 1.1.2. This is the iPhone/iPad part of the
[end-goal plan](END_GOAL_PLAN.md) and builds on it. Evidence comes from a code
read of `ios/`, `shared/apple/` and `profile/`, and from Apple's documentation and
forums (links inline). Nothing here has been tested on a real iPhone yet.

## End goal

> On the person's iPhone and iPad, adult content does not reach the screen in
> Safari and other WebKit apps, chosen apps are limited, and the person cannot
> remove or weaken that protection on their own before a date they chose. After
> that date they can.

Two rules shape the whole plan:

1. **Only Apple's own, consent-based locks.** Screen Time, Family Sharing,
   supervision and configuration profiles. No exploits, no hidden behaviour, and
   every lock is disclosed in the app ([App Review 2.5.19](https://developer.apple.com/app-store/review/guidelines/)).
2. **Every lock keeps an exit.** A date, a second person or Apple's own recovery.
   A lock with no exit is one people refuse to install ([threat model](THREAT_MODEL.md)).

### What "hard to remove" means on an iPhone

On a personal, unsupervised iPhone, Apple gives the owner **no** supported way to
make an app impossible to delete. Under the `.individual` authorization Hisn
uses, the owner can revoke Screen Time access in Settings at any time, which
clears every setting Hisn made
([WWDC22](https://developer.apple.com/videos/play/wwdc2022/110336),
[forum](https://developer.apple.com/forums/thread/729717)). What exists is a ladder:

| Level | How | Stops deletion? | Exit that always remains |
| --- | --- | --- | --- |
| 0. Self only | Screen Time `.individual` authorization | No | Delete the app, or revoke in Settings |
| 1. Passcode vault (built) | Hisn seals a random Screen Time passcode; Screen Time set to *Deleting Apps: Don't Allow* and *Lock Screen Time Settings* | Yes, until the date | Reset the passcode with the Apple Account password; erase the phone |
| 2. Family guardian | Family Sharing organizer holds the passcode and Apple Account reset, or `.child` authorization for an under-18 family member | Yes | The guardian |
| 3. Supervised device (profile built) | Apple Configurator supervision plus Hisn's profile (`allowAppRemoval` false, locked DNS, built-in web filter) | Yes, including profile removal | Removal password holder; DFU restore with the Activation Lock password |
| 3+. Supervised, time-locked | Level 3 with the removal password time-locked to the date (plan step D2) | Yes | The date; DFU restore |

The app must always say which level the person is on, and must never claim a
higher one than it can show.

## Where 1.1.2 stands

| Layer | Works today | Main gap |
| --- | --- | --- |
| Screen Time web filter | Apple's automatic adult filter plus 49 custom sites in a named store; status reads it back | Not restored after relaunch if the filter was lost; revocation is silent in the background |
| App limits | Always-on shields or daily budget through DeviceActivity; commitment allows only tightening | Moving the date forward starts a new budget day |
| Safari content blockers | 4 × 40,000 rules from the signed seed, hash-checked, never truncated | 232 free slots; covers about 3% of the full list; list changes only with an app build |
| Safari text and image extension | Signed terms, per-post X/Twitter hiding, optional local visual checks | The app cannot see if it is on, allowed on sites or in Private Browsing |
| Family DNS | Cloudflare Family DoH with honest enabled/saved status | The user can turn it off in Settings (only supervision locks it) |
| Commitment | 1/7/30/90 days, extend only, Keychain plus mirror, unreadable counts as active | Trusts the wall clock after relaunch; does not lock Safari, extension or DNS layers |
| Passcode vault | Random code, 15-minute setup window, device-only Keychain, reveal checked against Apple server time | Not tested on a device; date separate from the commitment; wording overclaims |
| Guardian mode | `.child` authorization with consent and no fallback | Uses only the web filter |
| Supervised profile | DoH with `ProhibitDisablement`, built-in filter with 500 deny URLs, removal restrictions, removal password | Some Apple restriction keys unused; password not time-locked; untested on a device |
| English/Arabic, accessibility | 251 matching keys, RTL layout, 48 pt rows, VoiceOver traits | Passcode ignores Dynamic Type; some low-contrast colours; one Arabic arrow |
| Release | Tests in CI on the simulator | No Apple team, no Family Controls distribution entitlement, no device evidence |

## Milestones

Tags: `[code]` code only, verifiable in CI · `[device]` needs a real iPhone ·
`[owner]` needs the owner · `[decision]` owner choice · `[guardian]` needs a second person.

### M0. Prerequisites (start now, they take the longest)

- `[owner]` Join the Apple Developer Program and set the team in `ios/generate_xcodeproj.py`.
- `[owner]` Request the Family Controls distribution entitlement for the app **and**
  `HisnActivityMonitor` (and any later shield extension)
  ([Apple](https://developer.apple.com/documentation/familycontrols/requesting-the-family-controls-entitlement)).
  Without it nothing based on Screen Time can ship. Guideline 4.10: Screen Time
  features cannot be behind a paywall.
- `[owner]` A test iPhone on iOS 26 that can be erased, plus a second Apple Account
  for Family Sharing tests.
- `[decision]` URL filter (M3): it needs a developer-run Private Information
  Retrieval server, which conflicts with "everything stays on the device". Decide
  yes, no or later.
- `[decision]` Whether the app recommends Family Sharing with a trusted organizer
  (level 2) as the default for people who want a real lock.

Exit: team set, entitlement requested, test device ready, both decisions recorded.

### M1. Honest status and quick fixes (all `[code]`)

1. Make the vault wording conditional: "only if you completed steps 1 and 2"
   (`Localizable.strings` `vault.body`). Hisn cannot read whether the passcode was
   entered.
2. Add a **removal-protection row** to readiness. It shows the level from the table
   above, marked *attested* (the person confirmed a self-test, such as trying to
   delete an app) or *verified* (only where an API can confirm it), never more.
   Inputs go into `MobileProtectionPolicy`.
3. Add a **Safari extension row**: on, site access and Private Browsing, shown as
   unverified until the extension reports in through its native handler.
4. Restore the Screen Time filter after a relaunch when readiness history knows
   `screentime.layer` during a commitment (`ProtectionController.swift:150-161`).
5. Offer to set the vault date to the commitment end, and show both dates together
   (`PasscodeVaultSection.swift`, `CommitmentSection.swift`).
6. Guided setup order: blocking layers first, then the commitment, then the vault.
   Starting a commitment with no active layer needs an explicit second confirmation.
7. Detect the supervised profile's DNS so the DNS row does not read "not set up"
   on a supervised phone.
8. Accessibility: the passcode follows Dynamic Type, the app-switcher snapshot hides
   the passcode, warnings use colours with at least 4.5:1 contrast, and the Arabic
   menu arrow points correctly.
9. Text scanner: after its retries fail, hide the page and say why, instead of
   leaving it visible (`extension/content/scan.js`, shared with the Safari extension).

Exit: unit tests for each rule, `./test.sh ios` green, EN/AR parity check passes.

### M2. A commitment that holds

1. `[code]` Check the commitment deadline against Apple server time, reusing the
   vault's HTTPS `Date` check. A moved clock can then never end it early.
2. `[code]` The budget day boundary uses the same trusted time, so moving the date
   does not reset the daily limit (`AppUsageConfiguration.swift`, `ActivityMonitor.swift`).
3. `[device]` During a commitment, set `dateAndTime.requireAutomaticDateAndTime`
   and `application.denyAppRemoval` in Hisn's store, and read them back. Under
   `.individual` these are friction only: revoking access clears them. First test
   on iOS 26 what each does, then describe exactly that.
4. `[code]` Layer-loss alerts: the monitor extension re-checks authorization and the
   Hisn store at each interval and posts a local notification when a layer drops.
5. `[code]` "Other browsers and VPN apps" preset for commitments, using existing app
   shields with a `FamilyActivitySelection` the person picks.
6. `[device]` Revocation test: revoke access in Settings in the background and
   confirm Hisn notices on next launch and at the next monitor interval.

Exit: a moved clock and a revoked authorization are both detected on a device,
and recorded in a dated record.

### M3. Wider blocking

1. `[device]` Measure the real per-blocker rule limit and compile time on iOS 26.
   The 40,000 cap is Hisn's own assumption; Apple publishes none.
2. `[code]` Rule packing: group domains with `if-domain` for page loads where that
   is equivalent, and add blocker targets if needed. Keep "never truncate".
3. `[code]` Signed list updates for Safari between app releases (end-goal step A2):
   signature check, rollback rejected, all parts stay under capacity.
4. `[decision]` + `[code]` If approved in M0, prototype `NEURLFilterManager` (iOS 26
   URL filter). It covers every WebKit and URLSession request, including other
   WebKit browsers and in-app web views, and scales to millions of URLs
   ([Apple](https://developer.apple.com/documentation/networkextension/url-filters)).
   It is the biggest single coverage gain available to an unsupervised phone.
5. `[code]` Visual checks in Safari: match the browser extension's hide-first rules,
   so unverified media stays hidden on supported sites.
6. Keep DNS as defence in depth, not the lock: it can be turned off on any
   unsupervised phone.

Exit: on a device, a listed adult domain beyond the current 160k is blocked in
Safari, and (if built) an adult path on an allowed host is blocked by the URL filter.

### M4. Stronger locks, all Apple-supported

1. `[code]` Extend the vault guide to Apple's other Content & Privacy settings:
   Account Changes, Passcode Changes and Installing Apps. Say plainly that the code
   can be written down during the setup window.
2. `[guardian]` Guardian mode (`.child`, under-18 family members only): also set
   `denyAppRemoval` for all apps, shield other browsers, and add a
   `ShieldConfiguration` extension with English and Arabic text. Each extension
   needs its own entitlement.
3. `[code]` Supervised profile options, each opt-in with tests, from Apple's
   [restriction schema](https://github.com/apple/device-management/blob/release/mdm/profiles/com.apple.applicationaccess.yaml):
   `forceAutomaticDateAndTime`, `allowAccountModification`, `allowPasscodeModification`,
   `allowAppInstallation`, `allowAppClips` and `allowEnablingRestrictions`.
   Keep the profile generator a Mac or command-line tool, not inside the app
   (guideline 5.5).
4. `[code]` Time-locked removal password (end-goal step D2): the profile's removal
   password is encrypted to the commitment date with drand timelock encryption, so a
   person without a second human can still use supervision, with the date as the exit.
5. `[decision]` Optional content filter extension (`NEFilterDataProvider`) for
   guardian and supervised modes only. Apple allows it nowhere else
   ([TN3134](https://developer.apple.com/documentation/technotes/tn3134-network-extension-provider-deployment)).

Exit: each lock level from the table works on the test device, its exit works
too, and the app reports the right level.

### M5. Device acceptance matrix (`[device]`)

Run on a disposable iPhone on iOS 26 and record each in `docs/IOS_<version>.md`:

- Each layer: on, off in Settings, back on; status matches every time.
- Vault: setup window, seal, reveal only after the date on both clocks, Apple
  Account reset behaves as the text says.
- Commitment: relaunch, reboot, moved clock, revoked authorization.
- 49 custom sites accepted; a 50th refused with a clear message.
- Supervised profile: install, deletion blocked, profile removal needs the
  password, 30-day provisional supervision noted.
- Guardian mode with a real family child account.
- English and Arabic, VoiceOver, largest Dynamic Type, iPad split view.

### M6. Release

- `[owner]` TestFlight build with the Family Controls entitlement.
- `[code]` Privacy manifest and privacy label: no data collected; the vault's Apple
  time check sends no user data.
- `[code]` Review notes: disclose every lock and its exit (2.5.19), minimal Safari
  site access (4.4.2), easy withdrawal of consent (5.1.1), no paid Screen Time
  features (4.10).
- `[docs]` User guide for iPhone: which lock level to choose, step by step, in
  English and Arabic.

## Critical path

M0 entitlement request → M1 (in parallel, code only) → device tests for M2 → M4
locks → M5 matrix → M6 TestFlight. The entitlement and the device are the slowest
steps; start them first. M3 can run in parallel with M2 once the device exists.

## Not in this plan

- Any claim that Hisn is undeletable, or 100% blocking.
- Workarounds of Apple's controls, hidden features, or VPN tricks.
- Content outside WebKit and Safari (non-WebKit browsers only take part if they
  choose to support the URL filter).
- Accounts, servers or logs that see what the person browses. A URL filter server,
  if approved, must use Apple's private retrieval design and keep no logs.
