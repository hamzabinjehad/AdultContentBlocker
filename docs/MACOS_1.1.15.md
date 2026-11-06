# Mac 1.1.15 — Organization Mode

Released 5 November 2026 as a development candidate. Unsigned and unnotarized;
no MDM deployment, supervised device or removal-resistance acceptance is
claimed. This note records what changed and what the change does not cover.

## Why

Hisn is often installed by one person for another — a parent, a school, an
employer. OS-level removal resistance belongs to a configuration profile and,
for real enforcement, an MDM. What the app can do is stop offering the local
user the exits it controls once a profile says the Mac is managed, and say who
manages it instead of pretending to be unmanaged.

The evidence has to be a **forced** preference. A value the person can write
with `defaults write` is not management; it must arrive through the profile or
MDM layer that `CFPreferences` marks as forced.

## What changed

- **`ManagedMode` reads the forced layer.** `HisnManaged` in the app's own
  domain (`app.hisn.Hisn`) turns managed mode on; `OrganizationName` supplies
  the name. `CFPreferencesAppValueIsForced` is the gate, and tests inject the
  reader rather than writing real preferences. Absent or false means unmanaged.
- **A visible managed state.** Overview, Setup, Rules, Settings and the menu bar
  show **Managed by [organization]**. The Protection status also gains an
  **Organization policy** layer, so "managed" is part of the status answer, not
  a footnote.
- **Tightening only.** While managed, the app hides the Device Management
  removal route and the private-window sheet's **Not now**; Rules still accepts
  new blocks and removal of allowances but refuses the reverse; the partner key,
  inspection settings and gray-screen switch become tightening-only; the
  account-split confirmation is disabled.
- **The profile generator can emit the flag.**
  `profile/make_profile.py --managed --organization-name "…"` adds a forced
  `app.hisn.Hisn` payload (`HisnManaged = true`, `OrganizationName`) beside the
  existing DNS and browser payloads. The profile already sets
  `PayloadRemovalDisallowed` and a removal password; when the Mac is managed,
  the narrow private-windows profile the app creates also sets
  `PayloadRemovalDisallowed`.

Deployment steps, what the payload does and its limits are in the
[organization deployment note](ORGANIZATION_MODE.md).

## Files

| File | Change |
| --- | --- |
| `macos/Hisn/ManagedMode.swift` | forced-preference reader, display name |
| `macos/Hisn/ContentView.swift` | managed banner and tightening-only controls |
| `macos/Hisn/ProtectionStatus.swift` | organization-policy layer |
| `macos/Hisn/StatusMenu.swift` | banner and gray-screen gating |
| `macos/Hisn/PrivateBrowsing.swift`, `PrivateBrowsingView.swift` | managed banner, removal disallowed, no "Not now" |
| `macos/Hisn/WelcomeView.swift` | managed first-run wording |
| `profile/make_profile.py` | `--managed`, `--organization-name`, forced app payload |
| `macos/HisnTests/ManagedModeTests.swift` | forced-only reading, display name, profile payload, gating |

## Verification

- `./test.sh macos` — **559 tests, 0 failures**; 718 strings, all with Arabic.
- The `profile` suite covers the managed payload, the empty-name fallback and
  the removal-disallowed narrow profile.
- The generated example profile was inspected with `plutil`: it contains the
  forced `HisnManaged`/`OrganizationName` payload and
  `PayloadRemovalDisallowed => true`.

## Limits

- **A local administrator, Recovery or an erase can remove any profile.** This
  is the platform boundary; MDM, supervision and a standard daily account are
  what make removal resistance real. See the
  [organization deployment note](ORGANIZATION_MODE.md).
- **Managed mode is UI policy, not an OS enforcement layer.** It removes the
  exits inside Hisn; it does not add a system restriction.
- **Safari has no supported private-browsing profile key.** The managed
  profile's DNS and browser policies do not change that. Use the
  organization's Safari restriction, Screen Time, or block Safari.
- **No loss of protection on upgrade.** An unmanaged install is unchanged; a
  managed one begins in tightening-only mode as soon as the forced preference
  is readable.
