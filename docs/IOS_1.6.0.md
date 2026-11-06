# Hisn iPhone and iPad 1.6.0 — the bypass guard and the Lock Center

Released 6 November 2026 as a development candidate. Simulator-tested only; no
Apple team, Family Controls entitlement, signed build or physical-device
acceptance is claimed.

## Why

Two gaps kept coming back in the plan and the audits:

1. **The single app rule could not cover the main bypass route.** Safari
   blocking, the Screen Time filter and family DNS all stop at another browser
   or a VPN app. The one saved app rule already had three modes, so a person
   who budgeted one app could not also hold the browsers always-blocked — the
   improvement plan's remaining M2.5 item ("Other browsers and VPN apps preset
   for commitments, using existing app shields with a `FamilyActivitySelection`
   the person picks").
2. **The removal ladder was a paragraph, not a place.** The lock levels
   (self only → passcode vault → family guardian → supervised), their evidence
   and their exits existed, but they lived in a flat section a person had to
   read. The REA audits' common thread — status first, rows as objects, one
   decision per screen — was applied to the overview in 1.5.0 and never to the
   question people actually ask: *can I make this app undeletable?* On an
   unsupervised iPhone the honest answer is "no app can, but here is every
   supported level and the exit each one keeps," and that answer deserved its
   own object.

## What changed

### Browser and VPN guard (blocking)

- **A second, independent always-block rule.** Setup step 2 and the overview's
  App limits sheet gain a **Browser and VPN guard** section: pick the apps
  (browsers, VPN apps, anything) that are always shielded, regardless of what
  mode the main rule uses for other apps. It is applied through the existing
  FamilyControls picker and ManagedSettings shields — no new permission, no
  new entitlement, no new enforcement claim.
- **Its own record and store.** The guard is saved in its own mirrored record
  (`appUsageGuardConfiguration`) and applied through its own
  `ManagedSettingsStore` (`hisn.apps.guard`), so it never overwrites or is
  overwritten by the main rule. Damaged guard records read as no guard and are
  never silently replaced; a cleared guard writes a tombstone so a stale copy
  cannot resurrect it.
- **Frozen during a commitment.** `applyGuard`/`clearGuard` take the same
  `rulesLocked` the main rule takes; a running or unreadable commitment refuses
  every guard change. The frozen-rules copy on the commitment section now
  names the guard in both languages.
- **Watched by the monitor.** The DeviceActivity monitor's layer-loss check now
  expects app shields when a guard is saved and observes the guard's own store,
  so a guard whose shield disappeared is reported like any other lost layer.
  The watch sees the union of the main rule's and the guard's shields; it
  cannot tell which of the two went missing.
- **Visible on the overview.** The rules card carries a third row — Browser and
  VPN guard — showing the always-blocked count or **Not set**, opening the
  app-limits sheet where the guard is edited.

Honest limits, shown in the app: Apple combines the shields of Hisn's named
rule stores on the device; Hisn reads its own records back, which is
configuration evidence, not proof every chosen app was shielded. Revoking Screen
Time access in Settings removes the guard with every other rule. Real
enforcement still needs the Family Controls entitlement on a signed device.

### Lock Center (removal protection, "undeletable, honestly")

- **The ladder is now the screen.** Setup step 3 shows the current level with
  its evidence pill (verified this session / confirmed by you / no lock) and any
  stale-confirmation reason, then one row per rung: what the level stops, the
  exit it keeps, and one action — the passcode vault (the section above), the
  guardian request (with its consent sheet), and supervised setup (the
  Apple Configurator route and guide).
- **No level is overstated.** The footer states what no level stops: an Apple
  Account password can reset a Screen Time passcode, erasing the device removes
  every lock, and another device is never covered. Hisn still shows a level as
  verified only when Apple confirms child authorization during the session.
- **The person's own confirmations stay.** `LockConfirmationRows` (guardian and
  supervised claims, with the optional deletion self-test and the
  device-marker binding) moved into the Lock Center unchanged. The vault claim
  is still confirmed beside the vault itself.
- The old flat `RemovalProtectionSection` is gone; nothing it said was deleted
  — the guardian status, the consent alert, the manual-setup guidance, the
  recovery copy and the Apple guide link all live in the Lock Center.

## What did not change

No commitment, Screen Time, DNS, Safari-blocker, pause, quiet-hours or shield
behavior changed. The six Safari blockers still carry the signed seed v5
(239,885 domains); this release adds no list capacity and claims none. The
guard adds no new layer to the readiness assessment — it is part of the
existing Screen Time app-limits layer.

## Design research (REA)

- REA 6.8.0 was installed from npm into a temporary directory from a portable
  Node 22.23.4 runtime, installation scripts disabled
  (`npm install --ignore-scripts rea-agents@latest`). No MCP server was
  registered, no editor or agent configuration was changed, and no inspected
  application code was executed.
- The provider-free `inspect-artifact` command ran on the built 1.6.0
  `HisnMobile.app` (the `build-for-testing` product, with the two shield
  appexes removed as `./test.sh ios` requires before installation). Evidence
  `ev_33f65972e29c1415eeeb05ff3265387c087bb9caef117ca19f02ab494da6cf9e`,
  subject digest SHA-256
  `cea9c8031764eeb542b727757fd0f6fe5907393d64c6371aee7f1685b608a253`; the
  inventory lists 29 Mach-O binaries, 14 plists and the bundle resources.
- REA reported no native Swift/SwiftUI analysis provider, so this is an
  inventory, not decompilation. It establishes nothing about runtime behaviour;
  the applied design decisions come from the recorded 1.5.0 audit's
  principles (status first, rows as objects, one decision per screen) — the
  Lock Center and the guard section are the two screens this pass builds with
  them.
- No ScreenZen or other third-party code, assets, visual identity or copy was
  reused. The previous audits' limits still stand: no user study, no
  screen-reader audit, no competitive benchmark.

## Files

| File | Change |
| --- | --- |
| `ios/Shared/AppUsageConfiguration.swift` | Guard record key/store, `isGuardValid`, `loadGuard`/`readGuard`, `appShieldWatch` |
| `ios/Hisn/AppUsageController.swift` | `guardConfiguration`, `applyGuard`/`clearGuard`, guard re-shield on refresh |
| `ios/Hisn/AppUsageSection.swift` | New `GuardSection` |
| `ios/Hisn/SetupViews.swift` | `RemovalProtectionSection` replaced by `LockCenterView`; guard wired into step 2 |
| `ios/Hisn/MobileRootView.swift` | Guard section in the App limits sheet |
| `ios/Hisn/OverviewCards.swift` | Guard row in the rules card |
| `ios/HisnActivityMonitor/ActivityMonitor.swift` | Layer-loss watch includes the guard store |
| `ios/Hisn/en.lproj/Localizable.strings`, `ar.lproj` | Guard and Lock Center copy in both languages; the frozen-rules line names the guard |
| `ios/HisnTests/MobileProtectionTests.swift` | New `GuardRuleTests`: validity, freeze, mirrored tombstone, watch inputs, controller refusals |
| `ios/generate_xcodeproj.py`, `ios/HisnMobile.xcodeproj` | Version 1.6.0, build 10 |
| `design/ios-ui-1.6.0/` | Render-harness captures and this record's README |

## Verification

- `./test.sh ios` — **39 Python and 221 simulator tests, 0 failures** on the
  booted iPhone 17 simulator (iOS 26.5). The 1.5.0 release had 216 hosted
  tests; the five additions are the guard rule tests.
- New renders cover every tab at phone and iPad sizes in English and Arabic,
  light and dark, and the three Setup drill-ins; the vault drill-in now shows
  the Lock Center and the app-limits drill-in shows the guard section.
  Captures are in `design/ios-ui-1.6.0/`.
- Localization parity passes for every new key in both languages, including
  the interpolated counts and the guard consent copy.

## Limits

- Simulator only. The guard's real ManagedSettings shielding, the combination
  of shields across Hisn's named stores on a real device, the monitor's
  guard-aware watch and the system shield all need a signed, entitled build on
  a physical iPhone. None of that is claimed here.
- "Undeletable" remains an honest ladder, not a property: without supervision,
  a device owner with the Screen Time passcode (or an Apple Account password
  reset, or device erasure) can still remove Hisn. The Lock Center states
  this on the screen itself.
- The layer-loss watch sees app shields as one layer: it reports that app
  shields were lost, not whether the main rule or the guard was the one that
  went missing.
- The lock ladder's supervised rung still describes Apple Configurator
  supervision; installing the profile is a manual step outside the app
  (guideline 5.5 keeps the generator a command-line tool).

## Next owner actions

Unchanged from [the iOS improvement plan](IOS_IMPROVEMENT_PLAN.md): the Apple
Developer Program, the Family Controls distribution entitlement for the app
and its extensions, a disposable test iPhone and TestFlight. M2.5 is now done
in code; device verification (M2.3, M2.6, M3.1, M5) is still the owner's to
run.
