# Mac 1.1.14 — first-run order and the private-window gap

Released 5 November 2026 as a development candidate. Unsigned and unnotarized;
no Developer ID package, notarized build, physical-Mac acceptance or VoiceOver
audit is claimed. This note records what changed and what the change does not
cover.

## Why

Two separate problems met on the same screen:

1. The app opened on a twelve-step diagnostics page before the person knew what
   the app was. Every launch began with a scan, a progress ring and a long list;
   the status that mattered was buried below it.
2. A green **Browser extension** row meant the extension was checking in from
   the browser process, not that it was running in a private window. Chrome's
   "Allow in Incognito" switch is the person's, and when it is off the extension
   is absent from exactly the window a person would use to get around it.

1.1.13 had the narrow private-windows profile, but it lived in Setup as a card
under the long checklist. It also covered Chromium only; Firefox has a separate
`DisablePrivateBrowsing` policy. Neither fact was part of the status.

## What changed

- **Overview is the front door.** `AppNavigation.firstPage` returns
  `.overview`; the detailed Setup page is one click away and the Overview's
  next-step card leads there.
- **A three-screen welcome.** It explains the three layers in one line each,
  asks once whether Hisn may disable private browsing, and never blocks the
  app: **Skip** is always present. Settings can open it again.
- **Private windows are a `ProtectionStatus` layer.** When a controllable
  browser is open, the layer is a problem, the headline becomes **Private
  windows are open**, and the primary action is **Prevent private browsing…**.
  A green other layer can no longer be read as full coverage.
- **One consent sheet lists every installed browser.** Chromium cores and
  Firefox channels that a profile can control are listed with their state.
  Safari, DuckDuckGo, Tor, Mullvad, LibreWolf and Waterfox are listed as not
  controllable, with the reason: no supported private-mode policy, while the
  system filter and the browser guard still apply.
- **The profile is broader but still narrow.** Chromium domains get
  `IncognitoModeAvailability = 1`; Firefox domains get
  `EnterprisePoliciesEnabled = true` and `DisablePrivateBrowsing = true`. The
  payload does not set DNS, DoH, guest windows or extension policy.
- **The extension can report its own answer.** 1.5.2's heartbeat carries
  `incognitoAllowed` from `chrome.extension.isAllowedIncognitoAccess`. The app
  records it per browser and distinguishes "checked by the extension" from
  "disabled by policy"; an absent answer is not treated as "not allowed".
- **Less text.** The Setup top card shows one next step and a count; the
  Overview's Layer details starts collapsed; the duplicate private-window card
  was removed (the tile and the primary action already carry it); the layer
  tiles use two columns so labels no longer truncate at the 760-point minimum.
- **Quieter launch.** The list update runs thirty seconds after launch. The
  previous signed list stays enforced; the only change is that the first frame
  is not competing with a background download.
- **Lock gating.** Adding the private-window block is a tightening and remains
  available during a lock. The removal route (System Settings › Device
  Management) is disabled with an explanation while a lock runs. Hisn cannot
  intercept a profile removal performed in System Settings by an administrator;
  the app says that rather than promising a block it cannot enforce.

## ScreenZen research

ScreenZen 2.0.0 (build 63) was already installed at `/Applications/ScreenZen.app`.
The official Sparkle feed (`https://updates.screenzen.co/mac/update.xml`) was
downloaded; the zip was notarized by ScreenZen LLC and its binary was
byte-identical to the installed app. It was quit, replaced with that official
copy, and its onboarding flags were reset with a backup of its group-container
preferences. ScreenZen restored `onboardingCompleted = true` from its account
state on launch, so its first-run screens could not be observed without a new
account. The UI that was observed was its calm dashboard and menu bar: one
status ring, one daily goal, compact rule chips, and a settings-lock countdown.
The design vocabulary taken was the single-status-first layout, one decision
per screen, and the short empty state; no ScreenZen code, assets, copy or data
model was used.

## Files

| File | Change |
| --- | --- |
| `macos/Hisn/PrivateBrowsing.swift` | browser catalogue, policy readback, status model, narrow profile |
| `macos/Hisn/PrivateBrowsingView.swift` | consent/status sheet |
| `macos/Hisn/WelcomeView.swift` | three-screen first-run explanation |
| `macos/Hisn/ProtectionStatus.swift` | private-windows layer, headline, short hero summary |
| `macos/Hisn/ExtensionPresence.swift` | per-browser incognito answer |
| `macos/HisnBridge/main.swift` | records the heartbeat's incognito answer |
| `extension/worker/native.js` | sends `incognitoAllowed` with the heartbeat |
| `macos/Hisn/ContentView.swift` | Overview-first, collapsed details, two-column tiles, shorter Setup |
| `macos/Hisn/HisnApp.swift` | delayed list check |
| `macos/HisnTests/PrivateBrowsingTests.swift` | catalogue, policy, profile and status tests |
| `macos/HisnTests/OnboardingTests.swift` | first page, welcome/private-sheet renders, minimum width |
| `macos/Hisn/Localizable.xcstrings` | 707 strings, all with Arabic |

## Verification

- `./test.sh macos` — **554 tests, 0 failures**; 707 strings, all with Arabic.
- `extension/test/run.sh` — all suites pass, including the heartbeat field.
- The welcome, private-window sheet and Overview at 760 points were rendered to
  `/tmp` by the UI render tests and reviewed. The Arabic welcome was rendered
  with an Arabic locale but the English test language; the catalog itself was
  checked by the XLIFF export.

## Limits

- **No install or physical-Mac acceptance is claimed for 1.1.14.** The installed
  `/Applications/Hisn.app` remains 1.1.13 until someone installs the new build.
- **Safari private browsing is not disableable by this profile.** The app names
  that limit. Screen Time restrictions and server-side supervision are separate
  controls, not part of this pass.
- **A policy readback is not proof the browser accepted it.** `CFPreferences`
  forced values are evidence of the profile, not of a live private-window
  attempt. Verify in each browser before describing a deployment as enforced.
- **The extension answer is per browser, not per profile.** A profile without
  "Allow in Incognito" is visible only if that profile's process is the one that
  checked in; a multi-profile browser can still have a less-covered profile.
- **The lock cannot stop an administrator removing the profile in System
  Settings.** Hisn disables its own removal route and says so; this is a
  platform boundary, not a promise of an unremovable profile.
- **A previously approved 1.1.13 narrow profile has the same identifier.** If
  macOS refuses the new mixed profile as a duplicate, remove the older
  “Hisn private windows disabled” profile in Device Management and approve the
  new one. The app can read the old forced values, so the status remains honest
  either way; only Firefox needs the new payload.
