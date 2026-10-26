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

## Audit by layer, with code references

A second read of `ios/` and `shared/apple/` on 2026-10-26, line numbers as of
`main` at that date. "Works" means the code does it and unit tests cover it, not
that it was seen working on a phone.

| Layer | Works (code) | Missing or wrong |
| --- | --- | --- |
| Screen Time app limits | Always-block shields and one shared daily budget (`ios/Hisn/AppUsageController.swift:60-116`, `ios/Shared/AppUsageConfiguration.swift:127-132`); the monitor shields at the threshold and keeps a reached budget for the day (`ios/HisnActivityMonitor/ActivityMonitor.swift:7-28`); during a commitment only tightening is accepted (`AppUsageConfiguration.swift:116-123`); health separates missing permission from stopped monitoring (`AppUsageConfiguration.swift:24-32`) | The budget day is the wall-clock date (`AppUsageConfiguration.swift:47-50`), so moving the date starts a new day; on iOS 16–17.3 a running budget cannot be edited (`AppUsageController.swift:77-79`); no daily time window like Android's schedule; no notice when access is revoked in the background |
| Safari content blockers | Four blockers × 40,000 rules (`shared/apple/MobileProtectionPolicy.swift:6`, `:41-47`); each handler refuses a rule file whose SHA-256 or count does not match instead of loading an empty list (`ios/HisnBlocker/ContentBlockerRequestHandler.swift:16-23`); the app re-verifies all four parts once per launch (`ios/Hisn/ProtectionController.swift:413-438`) and reads each switch (`:354-363`) and reload result (`:395-411`) | The list changes only with an app build; about 160k of the published list's domains fit; no proof a page was blocked, only configuration |
| Safari text and visual extension | The shared on-device scorer replaces a blocked top-frame page with `blocked.html` (`ios/HisnWebExtension/background.js:130-170`); it never receives native messages (`ios/HisnWebExtension/SafariWebExtensionHandler.swift:5-8`) | The app cannot read whether Hisn Text is on, allowed on sites or in Private Browsing, so the guide says so (`ios/Hisn/BrowserFirstSection.swift:10`) |
| Family DNS | Saves a Cloudflare Family DoH configuration (`ProtectionController.swift:368-393`) and reports absent, saved-but-off, enabled or different (`MobileProtectionPolicy.swift:114-125`, `ProtectionController.swift:345-352`) | Can be switched off in Settings on any unsupervised phone; a supervised profile's DNS is invisible to `NEDNSSettingsManager`, so the row reads "not configured" there |
| Commitment | 1/7/30/90 days, extend only (`ProtectionController.swift:95-119`); Keychain plus mirror, damaged storage counts as running (`ios/Hisn/MobileCommitmentStore.swift:22-52`); a forward clock jump cannot end a running session (`ProtectionController.swift:82-88`) | After a relaunch the deadline trusts the wall clock; Screen Time is re-applied during a commitment only if chosen in this process (`ProtectionController.swift:150-161`) |
| Passcode vault | Shown for 15 minutes, then sealed (`ios/Hisn/PasscodeVault.swift:36-43`); reveal needs the date on the device and on Apple's server clock (`:49-54`, `:160-178`); device-only Keychain (`:104-136`) | **Before this pass:** `vault.body` said apps "cannot be deleted" without the condition that the person actually entered the code and turned off deletion; the passcode ignored Dynamic Type (`ios/Hisn/PasscodeVaultSection.swift:83`) and stayed visible in the app-switcher snapshot |
| My blocked sites | Up to 49 sites in Hisn's Screen Time filter, canonicalised like the Mac and extension (`shared/apple/CustomSitePolicy.swift:13-60`); adding works during a commitment, removing waits (`ProtectionController.swift:193-217`); damaged storage never replaces the filter's list (`:159-167`) | **Before this pass:** with Screen Time access turned off, the row said only "enable Screen Time", as if it had never been set up |
| Status, language, layout | One verified-setup assessment (`MobileProtectionPolicy.swift:49-70`); 251 English and Arabic keys; iPad split view with an 860 pt readable column (`ios/Hisn/MobileRootView.swift:21-34`, `:97`) | **Before this pass:** a revoked Screen Time permission and a never-requested one read the same; orange and teal status text was about 2:1 on white (`MobileRootView.swift:150`); the setup-issue arrow pointed right in Arabic (`:159`); the step badges were fixed at 28 pt (`BrowserFirstSection.swift:33`); Settings ignored the readable column on iPad (`MobileRootView.swift:279`); no automated check that every looked-up key exists in both languages |

## Android features and what iOS can do with public APIs

Android 0.5.x is the feature reference. iOS gives apps far less reach into other
apps, so several Android layers have no iOS equivalent at all.

| Android feature | iOS with public APIs | State on iOS |
| --- | --- | --- |
| DNS filtering with Hisn's own list and custom domains (local VPN) | Partly. `NEDNSSettingsManager` only points the system at a DNS server; it cannot filter with Hisn's list on the device. A local `NEPacketTunnelProvider` could, at the cost of occupying the one VPN slot | Cloudflare Family DNS only; the domain list runs in Safari's blockers |
| Block chosen apps entirely | Yes: FamilyControls picker + ManagedSettings shields | Built (always-block mode) |
| Daily time window for chosen apps | Yes: `DeviceActivitySchedule` with an interval, shields set in `intervalDidStart` and cleared in `intervalDidEnd` | Not built; iOS has a daily usage budget instead |
| Word rules and adult-text checks in other apps (accessibility) | **No.** No API reads another app's screen text | Safari only, through Hisn Text |
| Image and video sampling in chosen apps (accessibility screenshots) | **No** for other apps. ReplayKit broadcast capture shows a recording indicator, is memory-capped and is not meant for this | Safari only, through Hisn Text's opt-in visual checks |
| Commitment that refuses weaker rules | Yes, inside the app | Built |
| Uninstall friction (device admin) | Yes, stronger than Android: Screen Time "Deleting Apps: Don't Allow" under a passcode Hisn keeps (vault), `.child` authorization, or supervision | Vault and guardian mode built; device acceptance pending |
| Honest per-layer health and setup guide | Yes | Built; this pass separates revoked from never-requested Screen Time |
| Alert when protection stops (DNS revoked, VPN replaced) | Partly: no callback when DNS or Screen Time is turned off in Settings; the app or the DeviceActivity monitor can re-check and post a local notification | Not built (M2.4) |
| DNS route self-check | Partly: a lookup through `URLSession` can show the configured resolver answers, not which apps use it | Not built |
| Encrypted-DNS canary, Private DNS warning | Not applicable: iOS has one system resolver setting, which Hisn reads | — |
| App languages (English and Arabic) | Yes: per-app language in Settings comes from the bundle's localizations | Built (`en.lproj`, `ar.lproj`); this pass adds a parity check |
| Licenses and sources screen | Yes | Not built for the app; the web extension already carries the visual runtime's notices (`ios/prepare_web_extension.py:50-54`) |

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

**Done on `ios/improvements` (2026-10-26)**, all without a device or an Apple team:

- Item 1: `vault.body` now says the lock holds only if the person set the passcode
  and set Deleting Apps to Don't Allow, and that Hisn cannot check it.
- Item 8: the passcode scales with Dynamic Type and shows dots whenever the scene
  is not active, so the app-switcher snapshot does not keep it; warning and
  success text uses `HisnPalette` colours with at least 4.5:1 on every grouped
  list background in light and dark mode (tested); the setup-issue arrow is
  `arrow.forward.circle`, which points the reading direction in Arabic.
- New, honest status when Screen Time access is lost: `ScreenTimeState`
  separates access turned off in Settings (Hisn's history knows the filter was
  set) from never requested, declined and filter off. The readiness row, the
  commitment section, My blocked sites and the setup issues say what iOS removed
  and that only the person can turn access back on.
- New, VoiceOver and Dynamic Type: the Safari readiness detail reads "3 of 4 parts
  on" instead of "3 / 4"; readiness rows and the blocked-site field stack at
  accessibility sizes; the Safari setup step badges scale.
- New, iPad: Settings uses the same 860 pt readable column as the other tabs; a
  render test covers all three tabs on iPhone and both iPad orientations, at
  the largest accessibility sizes, in English and Arabic.
- New, `ios/test_localization.py` (runs in `./test.sh python`, no Xcode): same
  keys and placeholders in English and Arabic, Arabic text in every Arabic value,
  and every key the SwiftUI code looks up present in both.

Not done here, and why: items 2, 5 and 6 change the setup flow and want the
owner's choice of default lock level (M0 decision); item 3 needs the extension to
report through its native handler, which today accepts no messages by design;
item 4 needs consent recorded with the commitment, or Hisn could re-enable a
filter the person turned off before committing; item 7 is not possible: iOS does
not show an app another configuration's DNS; item 9 is in `extension/`, which
another change owns.

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

## Non-goals

- Any claim that Hisn is undeletable, or 100% blocking.
- Workarounds of Apple's controls, hidden features, or VPN tricks.
- Content outside WebKit and Safari (non-WebKit browsers only take part if they
  choose to support the URL filter).
- Accounts, servers or logs that see what the person browses. A URL filter server,
  if approved, must use Apple's private retrieval design and keep no logs.
