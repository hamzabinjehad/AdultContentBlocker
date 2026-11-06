# Organization Mode — deployment note

Hisn can be deployed by an organization so that the person using the Mac sees
who manages it and cannot remove or weaken protections from inside the app.
This note covers the profile flag, what the app does with it, and the limits
that a deployment must state honestly.

The app-side change is recorded in [Mac 1.1.15](MACOS_1.1.15.md). This note is
the operator's side.

## How the app decides

The app reads `HisnManaged` and `OrganizationName` from its own preference
domain (`app.hisn.Hisn`) **through `CFPreferences`'s forced layer**:

```swift
CFPreferencesAppValueIsForced("HisnManaged" as CFString, "app.hisn.Hisn" as CFString)
```

Only a value delivered by a configuration profile (or MDM) is forced. A plain
`defaults write app.hisn.Hisn HisnManaged -bool true` is ignored, so the flag
cannot be faked by the local user. Absent, false or unreadable means unmanaged.

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
- The profile already sets `PayloadRemovalDisallowed` and a removal password.
  That password is generated on each run and stored in plain text inside the
  owner-readable output; escrow it before installation. `--print-password`
  prints it, and that output must be handled as a secret.

Deploy the generated profile the same way as the existing hardening profile:

- **BYOD / manual**: open the `.mobileconfig`, then approve it in System
  Settings › General › Device Management (or Privacy & Security › Profiles,
  depending on the OS version).
- **Managed fleet**: upload it to the MDM and scope it to the devices. MDM can
  also keep the profile from being removed locally.

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

None of this is a new OS restriction. Managed mode changes what the app offers;
it does not make the profile, the app or the Mac unremovable.

## What it does not do

- **A local administrator can still remove the profile** in System Settings,
  boot to Recovery or erase the Mac. `PayloadRemovalDisallowed` is only real
  resistance when an MDM keeps the profile scoped and the person is a standard
  user. State that limit to the person being protected.
- **It is not supervision.** Supervised-only keys (for example
  `ProhibitDisablement`) are omitted unless the profile is generated with
  `--supervised` for a supervised device.
- **It does not cover Safari private browsing.** No supported Safari policy key
  does. Use the organization's Safari restriction, Screen Time, or block Safari;
  the app names this limit on the private-window sheet.
- **It does not add filtering by itself.** The DNS and browser payloads in the
  same profile are what filter; managed mode only governs the app's own
  controls. Verify filtering separately.

## Verifying a deployment

- On the Mac, System Settings › Device Management (or
  `sudo profiles show -type configuration`) should list the Hisn profile.
- In Hisn, the **Managed by** banner and the **Organization policy** status
  layer should appear. If they do not, the payload is missing or the flag is not
  forced — check the profile identifier and the exact keys
  (`HisnManaged`, `OrganizationName`).
- Removing the manifest in a test account is the honest removal test: it shows
  the deployment's real resistance, not the app's wording.

## Related

- [Mac 1.1.15 release note](MACOS_1.1.15.md)
- [Threat model](TAMPER_MODEL.md) — what each layer does and does not stop.
- [Profile generator](../profile/make_profile.py)
