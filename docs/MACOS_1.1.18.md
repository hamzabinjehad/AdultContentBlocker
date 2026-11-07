# Mac 1.1.18 — the MDM profile can own the filter

Released 7 November 2026 as a development candidate. Unsigned and unnotarized;
no Developer ID package, notarized build or physical-Mac acceptance is claimed.
This note records the MDM side of the release. The same build also carries the
1.1.17 adult-TLD rail and the craft/extra-site pass; those are recorded in
[the craft note](MACOS_CRAFT_2026-11-07.md) and
[the extra-sites note](MACOS_EXTRA_SITES_2026-11-07.md).

## Why

Mac 1.1.17 made MDM enrollment visible, but the MDM still delivered only DNS,
browser and organization payloads. The system filter was app-managed: Hisn
created the `NEFilterManager` configuration at launch, and the filter could be
toggled in System Settings or the extension deactivated by an administrator.
The management layer could reinstall a profile but did not own the filter
itself.

That left three gaps for a managed Mac:

1. **The filter did not start with management.** It started when Hisn ran and
   configured it. Before login, after a crash, or with the app closed, the
   filter was not a profile-owned layer.
2. **The extension was removable.** No `com.apple.system-extension-policy`
   marked it allowed or non-removable, so System Settings remained a route out.
3. **Safari private browsing was uncovered.** Apple's `webcontent-filter`
   payload has a macOS 26 key, `SafariHistoryRetentionEnabled`, that closes the
   private-browsing and history-clearing gap on Safari. It was not deployed.

## What changed

- **A signed team ID adds the management-layer payloads.** With
  `--mdm --team-id ABCDE12345`, the generator emits:
  - `com.apple.webcontent-filter`: plugin type, `PluginBundleID` = the
    containing app (`app.hisn.Hisn`),
    `FilterDataProviderBundleIdentifier` = the system extension
    (`app.hisn.Hisn.HisnFilter`), `FilterSockets=true`, `FilterPackets=false`,
    `FilterGrade=firewall`, and `SafariHistoryRetentionEnabled=true`.
  - `com.apple.system-extension-policy`: `AllowedSystemExtensions`,
    `NonRemovableSystemExtensions` and `NonRemovableFromUISystemExtensions`
    map the team ID to the filter extension. The last two require macOS 15+
    and SIP; on older systems only the allow list applies.
- **The designated requirement is generated from the team.** The default is
  the standard Developer ID requirement naming the extension and team;
  `--filter-requirement` accepts the exact `codesign -d -r-` output for a
  different signing setup and rejects a string that names neither.
- **An MDM build blocks Erase All Content and Settings.** `--mdm` now sets
  `allowEraseContentAndSettings=false` on macOS 12+. `--allow-erase` keeps the
  old behavior; a personal (`--managed`) profile is unchanged.
- **Safari private browsing is disabled on macOS 26+.** The filter payload
  sets `SafariHistoryRetentionEnabled`. `--no-safari-private-lock` sets it to
  false instead. Older systems do not apply the key; verify the oldest macOS
  in the fleet before treating Safari as covered.
- **The profile says what it contains.** The managed payload carries
  `HisnProfileVersion=2`, `HisnFilterManaged`, and a sorted
  `HisnProtectedLayers` list. They are declarations, not enforcement evidence.
- **The app leaves a profile-owned filter alone.** `FilterController` reads
  `HisnFilterManaged`; when true it never replaces the `NEFilterManager`
  configuration, refuses `disable()` with a new
  **managed by your organization** error, and skips automatic reassertion.
  The Setup filter control says the organization owns it instead of showing an
  Enable button.
- **The Device management card shows the profile layer.** It prints
  **Protection profile v2. The system filter is managed by the profile.**,
  the same version without the filter, or a note that the profile reports no
  version and should be redeployed.
- **`verify_mdm.sh` checks the layers.** It reads the managed-preferences
  plists for the profile version, managed-filter declaration, DNS payload,
  `ProhibitDisablement`, Private Relay, Erase, and Chrome/Firefox policy, then
  reads `systemextensionsctl list` for the filter extension. `--strict`
  requires all of them (and therefore a supervised macOS 15+ build) and exits
  1 when one is missing. The default exit codes stay 0/1/2.
- **New flags for the edges.** `--without-managed-filter` keeps the old
  profile; `--lock-system-extensions` sets `AllowUserOverrides=false` and can
  break unrelated extensions; `--allow-erase` and
  `--no-safari-private-lock` turn off the new defaults.
- **`--maximum` is the one-command supervised fleet preset.** It requires
  `--mdm` and `--team-id`, implies `--supervised` and
  `--lock-system-extensions`, and refuses every weakening flag. It still
  depends on the operator telling the truth about supervision.

## Files

| File | Change |
| --- | --- |
| `profile/make_profile.py` | team ID and requirement validation, `com.apple.webcontent-filter` and `com.apple.system-extension-policy` payloads, erase lock, Safari history key, profile markers, new flags |
| `profile/test_make_profile.py` | 20 new tests for the filter, extension policy, marker, erase/Safari flags, split layers, CLI validation, the maximum preset and the verifier layer reader |
| `macos/Hisn/ManagedMode.swift` | `profileVersion`, `filterManaged`, the profile summary sentence |
| `macos/Hisn/FilterController.swift` | refuses to create or disable a profile-owned filter; skips reassertion |
| `macos/Hisn/FilterSetupControl.swift` | reports organization ownership instead of an Enable button |
| `macos/Hisn/MDMEnrollmentView.swift` | shows the profile summary in the card and sheet |
| `macos/verify_mdm.sh` | profile-layer report, extension state, `--strict` |
| `macos/HisnTests/ManagedModeTests.swift` | marker parsing and summary cases |
| `macos/HisnTests/FilterActivationTests.swift` | managed refusal message |
| `macos/Hisn/Localizable.xcstrings` | 32 MDM strings, all with Arabic |
| `docs/MDM_DEPLOYMENT.md`, `ORGANIZATION_MODE.md`, `TAMPER_MODEL.md`, `NETWORK_FIRST_PROTECTION.md` | the new layer, flags, checks and limits |
| `macos/generate_xcodeproj.py`, `macos/Hisn.xcodeproj` | 1.1.18 / build 30 |

## Verification

- `./test.sh macos` — **601 tests, 0 failures**; **847 strings, all with
  Arabic**.
- `profile` suite — **127 tests, 0 failures**; 82 in the generator suite.
- The generated example was inspected with `plutil`:
  `FilterType => Plugin`, `PluginBundleID => app.hisn.Hisn`,
  `FilterDataProviderBundleIdentifier => app.hisn.Hisn.HisnFilter`,
  `FilterSockets => true`, `FilterPackets => false`,
  `SafariHistoryRetentionEnabled => true`, the three extension maps with the
  team ID, `allowEraseContentAndSettings => false`,
  `HisnProfileVersion => 2`, `HisnFilterManaged => true`, no
  `com.apple.profileRemovalPassword`, and `ProhibitDisablement` only with
  `--supervised`.
- A `--maximum` example was inspected with `plutil`: `ProhibitDisablement =>
  true`, `AllowUserOverrides => false`, `allowEraseContentAndSettings =>
  false`, `SafariHistoryRetentionEnabled => true`,
  `DeveloperToolsAvailability => 2` and `IncognitoModeAvailability => 1` on
  every emitted browser policy.
- `macos/verify_mdm.sh` ran on this unmanaged development Mac: enrollment
  `No`, no managed payload, profile version `not reported`, DNS/extension
  layers missing, and exit 1. `--strict` also exits 1 there, at the enrollment
  check; neither mode was exercised on an enrolled Mac.
- The app builds and the new state is unit-tested. No MDM-enrolled Mac, signed
  build or real fleet was available; no positive end-to-end run is claimed.

## Limits

- **A signed build is required.** The filter payload names the extension and a
  designated requirement generated from the team ID. An unsigned build or a
  wrong requirement leaves the profile looking complete while the extension
  cannot load. Verify with `systemextensionsctl` and the filter's own status.
- **No positive MDM run is claimed.** The parser, plist shapes and shell
  checks are tested; the payloads were inspected, not deployed to an enrolled
  Mac. A real pilot is still required before a deployment claim.
- **`NonRemovableSystemExtensions` needs macOS 15+ and SIP.** It stops the
  System Settings/Finder toggle; it does not stop Recovery, a DFU restore or
  an administrator who removes the MDM scope.
- **The Safari key needs macOS 26+.** It disables Safari private browsing and
  history clearing; it does not cover third-party WebKit browsers with their
  own private mode. Older systems do not apply the key; verify the oldest macOS
  in the fleet.
- **The filter is socket-level, not URL-level.** It sees hostnames and flows,
  not individual encrypted posts or videos. Apple's OS 26 URL Filter APIs are
  not implemented here.
- **A non-removable extension can complicate updates.** Replacing a
  non-removable extension may need the policy removed or an MDM-pushed update;
  test the app-update path before calling the extension permanent. The policy
  is also what keeps it non-removable, so offboarding removes it.
- **`--lock-system-extensions` can break unrelated software.** It is opt-in
  for that reason.
- **Erase is hidden, not impossible.** Recovery, a DFU restore and the MDM
  console remain.
- **The app/filter handoff is untested against a real profile-managed filter.**
  The code path is gated by `HisnFilterManaged` and unit-tested at the value
  layer; a real MDM deployment must confirm that the app observes the filter
  without replacing it.
- A local administrator, Recovery or an erase still defeats a local profile.
  Nothing in this release changes that platform limit.

## Related

- [MDM deployment](MDM_DEPLOYMENT.md) — the operator route, including the new
  payloads and `--strict`.
- [Mac 1.1.17 release note](MACOS_1.1.17.md) — enrollment as a verifiable fact.
- [Organization Mode](ORGANIZATION_MODE.md) — what the forced flag changes.
- [Threat model](TAMPER_MODEL.md) — each layer's real resistance.
