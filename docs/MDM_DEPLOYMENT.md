# MDM on a Mac — deployment note

An MDM is the layer that keeps the other layers installed. It is not a filter
by itself: it delivers the same DNS, browser and organization payloads that
`profile/make_profile.py` builds, and it owns their removal. On a supervised
Mac, the profile is scoped by the organization and the MDM can block local
removal; the person using the Mac does not hold the enrollment.

Since Mac 1.1.18 the generator can also make the **system filter itself** part
of the management layer. With a signed team ID, the profile delivers the
content-filter configuration and a system-extension policy, so the filter
starts without the app being opened and — on macOS 15 and later — cannot be
disabled or uninstalled while SIP is on.

This note is the operator's side of [Organization Mode](ORGANIZATION_MODE.md).
It assumes a fleet, not one hand-installed profile. The app's part is recorded
in [Mac 1.1.18](MACOS_1.1.18.md) and [Mac 1.1.17](MACOS_1.1.17.md).

## What MDM adds — and what it does not

**Adds**

- **A profile-owned filter.** `com.apple.webcontent-filter` names the Hisn
  system extension as the filter provider. The filter starts at boot and
  applies to every user, not only when Hisn is running. The app sees it and
  stops trying to replace it.
- **A non-removable extension.** `com.apple.system-extension-policy` allows
  the signed extension to load and, on macOS 15+, marks it
  `NonRemovableSystemExtensions` and `NonRemovableFromUISystemExtensions`:
  with SIP enabled it cannot be disabled or uninstalled, including from System
  Settings or Finder.
- **Safari private browsing on macOS 26+.** The filter payload sets
  `SafariHistoryRetentionEnabled`, which makes Safari keep history and
  therefore refuse private browsing and history clearing. This is the first
  supported profile key that closes that gap; older macOS versions do not
  apply it, so verify the oldest system in the fleet.
- **No local erase button.** An MDM build sets
  `allowEraseContentAndSettings=false` by default, so the daily account cannot
  wipe the managed Mac from System Settings. Recovery and a DFU restore are
  still possible.
- **Reinstall and scope.** A removed or damaged profile can be pushed again.
  A profile scoped to a smart group is not a file on the person's desktop.
- **Removal control.** `PayloadRemovalDisallowed` becomes real when the MDM
  keeps the profile scoped and the enrollment is protected. A user-approved
  enrollment that the MDM has not protected can still be removed by the user;
  an ADE enrollment with removal blocked cannot be removed locally without a
  wipe or disabling SIP.
- **Supervision-only keys.** `ProhibitDisablement` on the DNS payload is
  accepted only on a supervised Mac. Other supervised restrictions can be
  added by the organization.
- **A management identity the app can read.** Hisn asks
  `/usr/bin/profiles status -type enrollment` and says whether the organization
  policy is MDM-backed or merely a locally installed profile.
- **Account separation at scale.** The MDM can enforce FileVault, a standard
  daily account and other baseline settings alongside the Hisn payload.

**Does not**

- **Filter without the app installed.** The profile configures the filter
  extension, but the signed app in `/Applications` contains it. An enrolled
  Mac with no Hisn app is managed, not protected.
- **Stop a local administrator, Recovery, a DFU restore, or SIP being turned
  off.** The non-removable extension keeps a normal administrator from
  toggling it; it does not survive someone who controls Recovery and the
  organization's ABM/MDM credentials.
- **Cover Safari private browsing before macOS 26.** The history-retention key
  is macOS 26+. Use Screen Time, the organization's Safari restriction, or
  block Safari on older systems.
- **Inspect encrypted content.** DNS classifies names, not individual posts or
  videos. The socket filter and the browser layer are separate.
- **Prove effective policy.** A payload in a profile is not proof that a
  browser accepted it. Verify on a test Mac with the checks below.

## Prerequisites

1. **Apple Business Manager** with the Macs assigned to an MDM server. ADE is
   the zero-touch path and gives the MDM the strongest control over enrollment
   removal. Since macOS 11, Device Enrollment also results in supervision, but
   it requires touching each Mac.
2. **An MDM** that can deploy a custom `.mobileconfig` and the signed app.
   Jamf Pro, Kandji, Mosyle, Addigy and Intune all can; the menu names differ.
3. **A signed, notarized Hisn build and its Apple team ID.** The app is not
   sandboxed and installs a system extension. Unsigned builds cannot run the
   filter; see the [release runbook](RELEASE_RUNBOOK.md). The team ID is the
   ten-character identifier that signs the app and the extension.
4. **A standard daily account.** The person being protected must not be an
   administrator. This is the part MDM cannot fake.
5. **A recovery owner.** The organization holds the ABM/MDM credentials; the
   person using the Mac does not. Say this to them before deployment.
6. **macOS 15 or later for the non-removable extension**, and macOS 26 or
   later for the Safari private-browsing lock. The rest of the profile works
   on macOS 12+; the filter payload itself is macOS 10.15+.

## Build the profile

```bash
cd profile
python3 make_profile.py \
  --mdm \
  --organization-name "Example Institute" \
  --team-id "ABCDE12345" \
  --supervised \
  --resolver cleanbrowsing \
  --out ../dist/hisn-example-mdm.mobileconfig
```

- `--mdm` implies `--managed`, requires `--organization-name`, and omits the
  local removal password. The MDM owns removal; a password in the uploaded
  artifact would only be another secret to lose.
- `--team-id` adds the two management-layer payloads. Without it the profile
  still delivers DNS, browser and organization policy, but the filter stays
  app-managed. The generator prints which build it produced.
- `--supervised` adds `ProhibitDisablement`. Use it only for Macs that really
  are supervised; an unsupervised Mac rejects or ignores the key and the
  deployment note would be wrong.
- `--resolver` picks the filtering resolver preset. The default is Cloudflare
  Families. `cleanbrowsing` is stricter and also forces SafeSearch.
- `--separate-layers` is optional. If used, the managed app payload, the
  managed filter and the extension policy travel with the network profile, so
  removing the browser profile cannot drop them.

The filter payload's designated requirement is generated from `--team-id`. If
the release is signed differently, copy the exact requirement from the signed
build:

```bash
codesign -d -r- \
  /Applications/Hisn.app/Contents/Library/SystemExtensions/HisnFilter.systemextension
```

Pass that string with `--filter-requirement`. It must name both
`app.hisn.Hisn.HisnFilter` and the team ID; the generator rejects a string that
does not.

### What the two payloads contain

```bash
plutil -p ../dist/hisn-example-mdm.mobileconfig | \
  grep -E 'FilterType|PluginBundleID|FilterDataProvider|FilterSockets|FilterPackets|FilterGrade|SafariHistoryRetentionEnabled|AllowedSystemExtensions|NonRemovableSystemExtensions|NonRemovableFromUISystemExtensions|AllowUserOverrides'
```

- **Filter:** `FilterType=Plugin`, `PluginBundleID=app.hisn.Hisn`
  (the containing app), `FilterDataProviderBundleIdentifier=app.hisn.Hisn.HisnFilter`
  (the system extension), `FilterSockets=true`, `FilterPackets=false`,
  `FilterGrade=firewall`, and `SafariHistoryRetentionEnabled=true`.
- **Extension policy:** `AllowedSystemExtensions`,
  `NonRemovableSystemExtensions` and `NonRemovableFromUISystemExtensions` each
  map the team ID to the filter extension.
- **Erase:** `allowEraseContentAndSettings=false`.

Two flags change the defaults:

- `--lock-system-extensions` sets `AllowUserOverrides=false`, so the person
  cannot approve system extensions the profile does not allow. It can break
  unrelated software; scope it deliberately.
- `--allow-erase` leaves Erase All Content and Settings available.
- `--no-safari-private-lock` sets `SafariHistoryRetentionEnabled=false`, which
  keeps Safari private browsing and history clearing on macOS 26+.

### One-command maximum

For a supervised fleet, `--maximum` turns on every layer the generator has and
refuses to be combined with a weakening flag:

```bash
python3 make_profile.py \
  --mdm --maximum \
  --organization-name "Example Institute" \
  --team-id ABCDE12345 \
  --out ../dist/hisn-example-maximum.mobileconfig
```

It implies `--supervised` and `--lock-system-extensions`, keeps the erase and
Safari locks, and disables DevTools and private windows. It does not choose a
resolver or claim that the Macs really are supervised: that remains the
operator's assertion, and `--supervised` on an unsupervised Mac is a false
claim the platform will reject or ignore. `--maximum` is therefore a
convenience for a known fleet, not a verification of the fleet.

Inspect before uploading:

```bash
plutil -p ../dist/hisn-example-mdm.mobileconfig | \
  grep -E 'HisnManaged|HisnProfileVersion|HisnFilterManaged|OrganizationName|PayloadRemovalDisallowed|ProhibitDisablement|profileRemovalPassword'
```

Expected: `HisnManaged => 1`, `HisnProfileVersion => 2`,
`HisnFilterManaged => 1`, the organization name, removal disallowed, and —
only with `--supervised` — `ProhibitDisablement => 1`. There must be **no**
`com.apple.profileRemovalPassword` payload.

## Upload and scope

The exact menus change; the object is always a custom configuration profile.

- **Jamf Pro** — Computers › Configuration Profiles › New › Upload, then scope
  to a smart group of test Macs.
- **Kandji** — Library › Add › Custom Profile, upload the `.mobileconfig`,
  assign to a blueprint.
- **Mosyle** — Management › Custom Profiles › Add, upload, assign to a group.
- **Intune** — Devices › Configuration profiles › Create › New policy ›
  Templates › Custom, upload the `.mobileconfig`, assign to a group.
- **Addigy** — Policies › Configuration › Custom Profile.

Deploy the signed app the same way, to `/Applications/Hisn.app`. Do not use a
per-user `~/Applications` copy: the native-messaging host and the system
extension expect the system location.

The filter payload and the extension policy are separate objects inside one
profile. Scope them together on the first pilot; removing only the filter
payload leaves the extension allowed but unmanaged, and removing only the
extension policy leaves the filter configured but removable. If the
organization split network and browser profiles, both management payloads stay
in the network profile.

For a pilot, scope one to three Macs first. A bad profile in a fleet-wide scope
is a fleet-wide outage, and the DNS payload is the one that can take the
network down if the resolver is wrong.

### Payloads the organization still owns

- **Privacy Preferences Policy Control** (`com.apple.TCC.configuration-profile-policy`)
  for any Full Disk Access the deployment needs.
- **FileVault** and a **standard-account** baseline.
- **A supervised-restriction baseline** for anything beyond content protection.

These belong to the organization's baseline, not to the Hisn generator. Apple's
[device management schemas](https://github.com/apple/device-management) are the
source for each key.

## From the app

**Settings › Device management** is a guided handoff, not an enroller. It shows
whether the Mac is enrolled, whether Hisn is managed, and which of the two is
missing. It also prints the protection-profile version and whether the profile
owns the system filter:

- **Protection profile v2. The system filter is managed by the profile.**
- **Protection profile v2. The profile does not include the managed system
  filter.** — an organization profile without `--team-id`, or one built before
  the filter layer existed.
- **This organization profile does not report a version.** — an older profile;
  redeploy the current one.

The rest of the card is unchanged:

- **Set up with your organization…** explains what MDM does and what the app
  cannot do.
- **Open Device Management** opens the system pane, where account-driven
  enrollment or a pending profile can be approved.
- **Choose Enrollment Profile…** opens an organization-supplied `.mobileconfig`
  through System Settings. The person reviews what the profile can manage and
  approves it there.
- **Check again** re-reads `/usr/bin/profiles status -type enrollment`, even
  when the Hisn profile is not installed yet, so “enrolled but Hisn is not
  deployed” is visible.

The app cannot create an MDM enrollment profile, enroll the Mac, or accept the
profile for the person. That is deliberate: an app that could enroll a Mac into
its own MDM without approval would be a privilege-escalation hole. A running
lock disables the enrollment actions until it ends, so the handoff cannot be
used to install a profile that weakens protection mid-commitment. Only install
a profile from an organization you trust; a management profile can change
settings on the Mac.

When the profile declares the managed filter, the app does not create or
replace a filter configuration. It reports the filter's state and refuses to
turn it off; the profile is the removal path. The Setup page says **The system
filter is managed by your organization** instead of offering an Enable button.

## Verify

Run the read-only verifier on the Mac:

```bash
macos/verify_mdm.sh
```

It prints the enrollment lines, the managed-payload facts, the profile layers,
the live system-extension state, the resolvers in use, and the Hisn profile
identifiers visible to the current user. Root adds the device-level profile
list:

```bash
sudo macos/verify_mdm.sh
```

Exit codes:

- **0** — MDM-enrolled (user approved) and the Hisn managed payload is forced.
- **1** — not enrolled, not user-approved, Hisn is not under management, or
  `--strict` found a missing layer.
- **2** — the Mac could not be asked; no verdict.

The layer section reads the managed-preferences plists directly, not
`defaults read app.hisn.Hisn`: the latter also shows a value the person wrote
themselves, which is exactly what the app's forced-layer check rejects. It
reports:

- the protection-profile version and whether the managed filter is declared;
- the DNS payload and `ProhibitDisablement`;
- iCloud Private Relay, Erase All Content and Settings;
- Chrome and Firefox policy, when the profile delivered it;
- whether `app.hisn.Hisn.HisnFilter` is **activated and enabled** in
  `systemextensionsctl`.

Use strict mode for a deployment claim:

```bash
macos/verify_mdm.sh --strict
```

Strict mode requires version 2, the managed-filter declaration, forced DNS,
`ProhibitDisablement`, Private Relay disabled, Erase disabled, and the filter
extension activated. It deliberately fails on an unsupervised build, because
`ProhibitDisablement` is the supervision claim. It still does not prove that a
browser accepted every policy; that is the next list.

Then check the app and the effective policy:

1. **Hisn** shows **Managed by [organization]** and the **Organization policy**
   layer reads **Enrolled via DEP and MDM** (or **Enrolled in MDM** without
   DEP). If it says **not enrolled in MDM**, the profile is local, not
   MDM-delivered. The Settings card shows the same state, the profile version
   and the handoff.
2. **System Settings › General › Device Management** lists the Hisn profile and
   the MDM. On a supervised Mac with profile removal disallowed, removal is not
   offered.
3. **System Settings › General › Login Items & Extensions › Network
   Extensions** shows the Hisn filter. On macOS 15+ with the extension policy
   applied, the toggle is controlled by the profile and cannot be switched off.
4. **DNS** — `scutil --dns` shows the filtering resolvers, and a known adult
   test name is blocked on a browser that is not covered by the extension.
5. **Chrome** — `chrome://policy` shows the Hisn policies as *OK* and
   *Machine* scope. **Firefox** — `about:policies` shows the same.
6. **Safari private browsing (macOS 26+)** — with
   `SafariHistoryRetentionEnabled`, Private Browsing is unavailable and
   history cannot be cleared in Safari. Verify on the test Mac; older systems
   do not apply it.
7. **Remove test** — in a test account, try to remove the profile and the
   extension. The honest answer is what System Settings does, not what the app
   says.

## What the app now reports

`ManagedMode` reads the forced `HisnManaged` preference and the enrollment
separately. The **Organization policy** layer distinguishes:

- **Enrolled via DEP and MDM** — DEP fleet deployment; supervision is separate
  and has to be confirmed in the MDM console.
- **Enrolled in MDM** — user-approved enrollment without a DEP line; removal
  resistance depends on supervision and the MDM's profile-removal setting.
- **MDM enrollment is present but not user-approved** — the MDM cannot apply
  every control and the user can remove the enrollment.
- **This Mac is not enrolled in MDM** — the profile was installed locally.
- **Unknown** — the probe could not answer; the older wording is kept and no
  claim is made either way.

The probe runs `/usr/bin/profiles status -type enrollment`, caches the answer
for ten minutes, and only on a Mac where the forced flag is already true. A
missing or unreadable answer never becomes "not managed". The profile markers
(`HisnProfileVersion`, `HisnFilterManaged`) are read from the same forced
domain; they are declarations, not enforcement evidence, and the verifier
checks the layers separately.

## Limits to state in the deployment note

- A local administrator can still remove a local profile, boot to Recovery,
  turn SIP off or erase the Mac. Non-removable system extensions raise the
  cost; they are not a guarantee against an administrator with Recovery.
- A user-approved enrollment that is not protected by supervision and the
  MDM's profile-removal setting can be removed by the user in System Settings.
  Real removal resistance needs supervision and an MDM setting that disallows
  profile removal.
- The filter payload names a signed extension. An unsigned build or a wrong
  designated requirement leaves the profile looking complete while the filter
  cannot start. Verify `systemextensionsctl` and the filter's own status.
- `NonRemovableSystemExtensions` requires macOS 15+ and SIP; on older systems
  only the allow list applies. A non-removable extension can complicate an
  update that needs to replace it; test the update path or push the app
  through the MDM, and remove the policy during offboarding. Older systems
  ignore the non-removable keys.
- `SafariHistoryRetentionEnabled` requires macOS 26+. It closes Safari private
  browsing; it does not cover third-party WebKit browsers that keep their own
  private mode. Older systems do not apply the key; verify the oldest macOS in
  the fleet before treating it as covered.
- DNS filtering cannot classify individual encrypted posts or videos, and a
  VPN or another resolver can bypass it. The socket filter and browser policies
  are separate layers with their own gaps.
- The profile, the app and the MDM can all be correct while a browser ignores
  a policy. Verify effective policy; do not infer it from the upload.
- Apple's URL Filter APIs (OS 26) are not implemented here. They need a
  supervised configuration, entitlements and a PIR service; this profile does
  not claim that coverage.

## Offboarding

1. Remove the Mac from the profile's scope in the MDM. The MDM removes the
   profile and the system-extension policy; the extension becomes removable
   again and the app returns to unmanaged mode.
2. Remove the Mac from the ABM assignment only after the profile is gone.
3. If the Mac is being returned, erase it through the MDM. Do not hand over an
   enrolled Mac with the organization's profile still scoped.

## Related

- [Organization Mode](ORGANIZATION_MODE.md) — the forced flag and what managed
  mode changes inside the app.
- [Mac 1.1.18 release note](MACOS_1.1.18.md) — the filter and extension layer.
- [Mac 1.1.17 release note](MACOS_1.1.17.md) — enrollment as a verifiable fact.
- [Threat model](TAMPER_MODEL.md) — each layer's real resistance.
- [Profile generator](../profile/make_profile.py) and
  [verifier](../macos/verify_mdm.sh).
