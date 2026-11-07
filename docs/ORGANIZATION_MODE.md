# Organization Mode — deployment note

Hisn can be deployed by an organization so that the person using the Mac sees
who manages it and cannot remove or weaken protections from inside the app.
This note covers the profile flag, what the app does with it, and the limits
that a deployment must state honestly.

The app-side change is recorded in [Mac 1.1.15](MACOS_1.1.15.md), the
enrollment probe in [Mac 1.1.17](MACOS_1.1.17.md), and the managed filter in
[Mac 1.1.18](MACOS_1.1.18.md). This note is the operator's side.

## How the app decides

The app reads `HisnManaged` and `OrganizationName` from its own preference
domain (`app.hisn.Hisn`) **through `CFPreferences`'s forced layer**:

```swift
CFPreferencesAppValueIsForced("HisnManaged" as CFString, "app.hisn.Hisn" as CFString)
```

Only a value delivered by a configuration profile (or MDM) is forced. A plain
`defaults write app.hisn.Hisn HisnManaged -bool true` is ignored, so the flag
cannot be faked by the local user. Absent, false or unreadable means unmanaged.

Mac 1.1.18 profiles also carry `HisnProfileVersion` and `HisnFilterManaged`.
They are declarations the app uses to show whether the organization profile is
current and whether it owns the system filter; they are not enforcement
evidence. An older profile has neither key, and the app says so instead of
guessing.

## Generating the profile

`profile/make_profile.py` emits the managed payload with the existing DNS,
restriction, removal-password and browser payloads:

```bash
cd profile
python3 make_profile.py \
  --managed \
  --organization-name "Example Institute" \
  --out ../dist/hisn-example.mobileconfig
```

- `--managed` adds the forced `app.hisn.Hisn` payload.
- `--organization-name` is shown verbatim in the app after **Managed by**. An
  empty or whitespace-only name falls back to the neutral phrase "your
  organization". It is not a secret: any local user can read it.
- `--team-id` (with `--mdm`) adds the profile-managed content filter and the
  system-extension policy that makes the signed Hisn filter non-removable on
  macOS 15+. Without it the profile governs the app but not the filter.
- A manual (`--managed`) profile sets `PayloadRemovalDisallowed` and a removal
  password. That password is generated on each run and stored in plain text
  inside the owner-readable output; escrow it before installation.
  `--print-password` prints it, and that output must be handled as a secret.
  An `--mdm` build has no removal password; the MDM owns removal.

Deploy the generated profile the same way as the existing hardening profile:

- **BYOD / manual**: open the `.mobileconfig`, then approve it in System
  Settings › General › Device Management (or Privacy & Security › Profiles,
  depending on the OS version).
- **Managed fleet**: build with `--mdm` instead of `--managed`, then upload the
  profile to the MDM and scope it to the devices. `--mdm` requires
  `--organization-name`, omits the local removal password (the MDM owns
  removal), and still needs `--supervised` for supervision-only keys. Add
  `--team-id` to make the filter and its extension part of the management
  layer. The full route is in [MDM deployment](MDM_DEPLOYMENT.md).

## What the app can tell about the deployment

The forced flag says *the profile is installed*; it cannot say whether an
organization delivered it or the person installed it alone. The app therefore
also runs `/usr/bin/profiles status -type enrollment` (on a Mac that is already
managed) and adds the enrollment fact to the **Organization policy** layer:

- **Enrolled via DEP and MDM** — DEP fleet deployment; confirm supervision in
  the MDM console.
- **Enrolled in MDM** — user-approved enrollment without a DEP line; removal
  resistance depends on supervision and the MDM's removal setting.
- **MDM enrollment is present but not user-approved** — limited control.
- **This Mac is not enrolled in MDM** — the profile was installed locally.
- **Unknown** — no claim is made; the old wording is kept.

The probe is cached for ten minutes and never turns a failed read into "not
managed". It does not prove supervision; DEP is a signal to confirm in the MDM
console.

The same forced domain carries the profile markers. **Settings › Device
management** shows either **Protection profile v2. The system filter is managed
by the profile.**, the version without the filter, or a note that the profile
reports no version and should be redeployed. The marker is a declaration; the
verifier and the app's filter status are the evidence.

## What managed mode changes

- **It says who manages the Mac.** Overview, Setup, Rules, Settings and the menu
  bar show **Managed by [organization]**, and Protection status gains an
  **Organization policy** layer.
- **It removes the in-app exits.** The Device Management removal route and the
  private-window sheet's **Not now** are hidden; the partner key, inspection
  settings and gray-screen switch become tightening-only; the account-split
  confirmation is disabled. Rules still accepts new blocks and removal of
  allowances, but refuses the reverse.
- **The narrow private-windows profile** the app can create also sets
  `PayloadRemovalDisallowed` when the Mac is managed.
- **It leaves a profile-owned filter alone.** When `HisnFilterManaged` is true,
  the app does not create, replace or disable the filter configuration. It
  reports the filter state and says the organization owns it.

None of this is a new OS restriction. Managed mode changes what the app offers;
it does not make the profile, the app or the Mac unremovable.

## What it does not do

- **A local administrator can still remove the profile** in System Settings,
  boot to Recovery or erase the Mac. `PayloadRemovalDisallowed` is only real
  resistance when an MDM keeps the profile scoped and the person is a standard
  user. State that limit to the person being protected.
- **It is not supervision.** Supervised-only keys (for example
  `ProhibitDisablement`) are omitted unless the profile is generated with
  `--supervised` for a supervised device. The app reports enrollment, not
  supervision.
- **It does not cover Safari private browsing before macOS 26.** The managed
  filter payload sets `SafariHistoryRetentionEnabled`, which disables Safari
  private browsing and history clearing on macOS 26+. Older systems do not
  apply the key; use Screen Time, the organization's Safari restriction, or
  block Safari there.
- **It does not add filtering by itself.** The DNS and browser payloads in the
  same profile are what filter; managed mode only governs the app's own
  controls. Verify filtering separately.

## Verifying a deployment

- On the Mac, System Settings › Device Management (or
  `sudo profiles show -type configuration`) should list the Hisn profile.
- `macos/verify_mdm.sh` is the read-only check: it reports the enrollment
  lines, the forced `HisnManaged` value, the profile version, the managed-filter
  declaration, the main policy layers, the live system-extension state and the
  visible Hisn profile identifiers, and exits non-zero when the MDM layer is not
  in place. `--strict` additionally requires the DNS prohibition, Private Relay
  off, Erase off, the managed filter and the activated extension.
- In Hisn, the **Managed by** banner and the **Organization policy** status
  layer should appear. If they do not, the payload is missing or the flag is not
  forced — check the profile identifier and the exact keys
  (`HisnManaged`, `OrganizationName`). If the layer says the Mac is not enrolled
  in MDM, the profile is local rather than MDM-delivered.
- **Settings › Device management** shows the same enrollment state and opens
  the guided handoff: Device Management itself, or an organization-supplied
  profile the person approves in System Settings. It cannot enroll the Mac for
  them.
- Removing the manifest in a test account is the honest removal test: it shows
  the deployment's real resistance, not the app's wording.

## Related

- [Mac 1.1.18 release note](MACOS_1.1.18.md)
- [Mac 1.1.17 release note](MACOS_1.1.17.md)
- [MDM deployment](MDM_DEPLOYMENT.md) — fleet build, scope, verify and
  offboarding.
- [Threat model](TAMPER_MODEL.md) — what each layer does and does not stop.
- [Profile generator](../profile/make_profile.py)
