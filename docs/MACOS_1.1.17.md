# Mac 1.1.17 — MDM as a layer the app can verify

Released 6 November 2026 as a development candidate. Unsigned and unnotarized;
no Developer ID package, notarized build or physical-Mac acceptance is claimed.
This note records what changed and what the change does not cover.

## Why

Organization Mode could prove that a **forced preference** was present. It
could not tell an organization deployment from a profile the person installed
on their own, because both deliver the same `HisnManaged` key. The status layer
therefore said "Managed by …" for either one. That is the wrong claim for a
feature whose whole point is removal resistance: only an MDM keeps the profile
scoped and re-pushes it.

The profile generator had a second mismatch. It always embedded a local removal
password, which is a manual-install control. An MDM build is removed by the MDM;
shipping the password in the uploaded artifact added a secret without adding
protection.

## What changed

- **Enrollment is read separately from the profile flag.** `ManagedMode` now
  carries `MDMEnrollment` and asks `/usr/bin/profiles status -type enrollment`
  through `MDMEnrollmentProbe`. The system command reports the documented
  lines (`Enrolled via DEP`, `MDM enrollment: Yes`, `Yes (User Approved)`); it
  is a CLI, not a framework API, so an unrecognized shape becomes unknown.
  The probe is bounded to two seconds, cached for ten minutes, and runs only
  when the forced `HisnManaged` flag is already true — a personal Mac never
  pays for it.
- **An unreadable answer is unknown.** A missing command, a timeout, a
  non-zero exit, an unrecognized value, or the contradictory "DEP yes, MDM no"
  reading all become `.unknown`. None of them becomes "not managed".
- **The Organization policy layer says which deployment it is.** The detail
  distinguishes **Enrolled via DEP and MDM**, **Enrolled in MDM**, **MDM
  enrollment is present but not user-approved**, **This Mac is not enrolled in
  MDM**, and the old wording when the probe could not answer. Managed behavior
  itself is unchanged: the flag still decides what the app hides.
- **`--mdm` builds a fleet profile.** It implies `--managed`, requires a real
  `--organization-name`, omits `com.apple.profileRemovalPassword`, keeps
  `PayloadRemovalDisallowed`, and sets `PayloadOrganization` to the
  organization. `--supervised` still controls `ProhibitDisablement`; the build
  says so when it is missing. Split layers keep the managed payload with the
  network profile, where removing the browser profile cannot drop it.
- **`macos/verify_mdm.sh` is the read-only check.** It prints the enrollment
  lines, the forced `HisnManaged` value, the resolvers and the Hisn profiles
  visible to the user; root adds the device-level list. Exit 0 means a user-approved
  enrollment with the managed payload forced; 1 means a required fact is false;
  2 means the Mac could not be asked. It reports DEP, never "supervised".
- **A guided handoff in Settings.** **Settings › Device management** shows
  whether the Mac is enrolled, whether Hisn is managed, and which is missing.
  It opens Device Management or an organization-supplied `.mobileconfig` that
  the person reviews and approves in System Settings. It re-reads enrollment
  after the person returns, and it can probe even when Hisn is not managed yet,
  so “enrolled but not deployed” is visible. It cannot create an enrollment
  profile, enroll the Mac or accept for the person. A running lock disables
  the enrollment actions until it ends.
- **A deployment note.** [MDM deployment](MDM_DEPLOYMENT.md) covers Apple
  Business Manager, the common MDM consoles, app deployment, the in-app
  handoff, verification, offboarding and the limits that remain.

## Files

| File | Change |
| --- | --- |
| `macos/Hisn/ManagedMode.swift` | `MDMEnrollment`, the bounded cached probe, enrollment-aware `policyDetail` |
| `macos/Hisn/ProtectionStatus.swift` | Organization policy layer uses the enrollment-aware detail |
| `macos/Hisn/MDMEnrollmentView.swift` | Settings card and guided handoff: state, Device Management, organization-supplied profile, re-check |
| `macos/Hisn/ContentView.swift` | Settings card entry; `Card`/`PageSection` shared with the new file |
| `macos/generate_xcodeproj.py` | Registers the new file and carries 1.1.17 / build 29 |
| `macos/HisnTests/ManagedModeTests.swift` | Parser shapes, unknown answers, probe TTL, deployment wording, status layer |
| `profile/make_profile.py` | `--mdm` build: implied managed flag, no removal password, organization as `PayloadOrganization`, split-layer handling |
| `profile/test_make_profile.py` | MDM payload, supervision gating, split layers, CLI validation, verifier parser |
| `macos/verify_mdm.sh` | Read-only enrollment/managed-payload/resolver report with exit codes |
| `macos/Hisn/Localizable.xcstrings` | 27 new strings, all with Arabic |
| `docs/MDM_DEPLOYMENT.md` | The operator route |
| `docs/ORGANIZATION_MODE.md` | What the enrollment probe adds and what it does not |

## Verification

- `./test.sh macos` — **576 tests, 0 failures**.
- Localization — **747 strings, all with Arabic**; the export check sees every
  string the compiled code uses.
- `profile` suite — **107 tests, 0 failures**; 62 in the generator suite,
  including the MDM CLI, the no-password build, supervision gating and the
  verifier's parser.
- The generated example was inspected with `plutil`: no
  `com.apple.profileRemovalPassword`, `HisnManaged => 1`,
  `PayloadRemovalDisallowed => true`, and `ProhibitDisablement => 1` only with
  `--supervised`.
- The verifier was run on this unmanaged development Mac: it reported
  `Enrolled via DEP: no`, `MDM enrollment: no`, no Hisn managed payload, and
  exited 1. No MDM-enrolled Mac was available for a positive run; that is
  stated rather than inferred.
- The Settings card and sheet compile, and `MDMHandoff` covers all five states
  in unit tests. No interactive click-through, screenshot or real enrollment
  is claimed.

## Limits

- **Enrollment is not supervision.** `profiles status` reports whether the Mac
  is enrolled, not whether it is supervised. A DEP line is a signal to confirm
  in the MDM console; `--supervised` is still the operator's assertion.
- **A user-approved enrollment is not automatically weak or strong.** Its
  removal resistance depends on supervision and the MDM's profile-removal
  setting. An unprotected enrollment can be removed in System Settings; real
  resistance needs supervision, a disallowed removal setting and a standard
  daily account.
- **The layer reports management, not filtering.** An enrolled Mac with no
  Hisn profile is managed, not protected. The DNS/browser payloads and the app
  are what filter; verify effective policy separately.
- **No positive MDM run is claimed.** The parser and the layer are tested with
  the documented output shapes; the deployment route is documented, not
  executed against a real fleet here.
- **The enrollment probe is a CLI parse.** `/usr/bin/profiles` is not a
  framework API, and Apple can change its output. An unrecognized line falls
  back to unknown rather than to a false positive.
- A local administrator, Recovery, or an erase still defeats a local profile.
  Nothing in this release changes that platform limit.
- **The handoff is a handoff.** It opens the system UI or a file the person
  chooses; it does not enroll the Mac, and it cannot approve a profile. A
  management profile the person installs from an untrusted source is still
  their decision and still dangerous.
