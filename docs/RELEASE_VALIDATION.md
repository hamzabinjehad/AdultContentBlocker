# Release Validation Record

Copy this record into a dated release note for each candidate. Mark a check
passed only after it was actually performed.

| Field | Value |
| --- | --- |
| Version / build | Pending |
| Commit | Pending |
| Package SHA-256 | Pending |
| Apple team | Pending |
| Notarization submission / result | Pending |
| macOS version / hardware | Pending |
| Browser version / store extension ID | Pending |
| Tester | Pending |

| Check | Result | Evidence / Issue |
| --- | --- | --- |
| Automated tests | Pending | |
| Intel and Apple silicon package build | Pending | |
| App, filter, and bridge signature validation | Pending | |
| Notarization, stapling, and Gatekeeper | Pending | |
| Clean install into Applications | Pending | |
| Root-owned app, agent, and browser manifests | Pending | |
| First visible launch opens Setup | Pending | |
| System approval and restart flow | Pending | |
| Filter authority reachable and persistent | Pending | |
| Store extension connects in every profile | Pending | |
| Five-minute trial expires normally | Pending | |
| Essential allowed sites reachable | Pending | |
| Partner release and delayed release | Pending | |
| Restart, sleep/wake, and VPN cases | Pending | |
| Signed list update without GitHub authentication | Pending | |
| Update preserves lock and protected account | Pending | |
| Setup completed by a non-developer | Pending | |

No browsing history or private keys should be included in this record.

## Local Engineering Checks: 2026-10-08

### Repeated standard-profile removal evidence and safe closure

- Mac runtime reads only supported Chromium browsers' standard `Default` and
  `Profile *` preference files, with a twenty-megabyte per-browser read budget.
  Two explicit-loss readings at least ten seconds apart can override another
  profile's healthy bundle-level heartbeat. Malformed, conflicting, oversized
  and unreadable settings remain unconfirmed, not removal evidence.
- Confirmed profile loss cannot renew launch grace through another profile's
  connection. Relaunches require a current-instance read; profile-only warning
  expiry requires a read started at or after the original deadline. Pending
  reads retain the original countdown. The force-close fallback independently
  rereads still-confirmed folders and rechecks consent, process identity and
  heartbeat recovery. Per-task ownership prevents a cancelled reader from
  discarding a replacement force task.
- Mac English/Arabic diagnostics separate confirmed loss from unverifiable
  profiles. iPhone/iPad guidance explicitly asks users to select every browser
  app they want shielded with Screen Time; no Chromium profile reader or
  arbitrary browser termination is claimed for mobile.
- Mac suite: 408 passed, zero failures, including 21 profile-evidence and
  closure-decision tests. Localization export: 448 strings, all with Arabic.
  Selected iOS Simulator suite: 57 passed, zero failures. Full bundled Safari
  compiler test explicitly excluded locally; default/CI still includes it.
- Unsigned generic iOS device build, extension unit/production-worker mocked
  transport checks, package validation, benign evaluation-corpus gate and 15
  release checks passed. `git diff --check` passed. No live browser harness or
  installed-app closing/recovery acceptance run was performed in this round.
- Evidence: `/tmp/hisn-mac-plan-build/Logs/Test/Test-Hisn-2026.10.08_7-35-13-+0200.xcresult`
  and `/tmp/hisn-ios-build/Logs/Test/Test-HisnMobile-2026.10.08_7-25-51-+0200.xcresult`.
- No personal browser preferences were changed, running browser terminated,
  commitment altered, installed app relaunched, extension reloaded, managed
  policy installed or outside-lock guarding silently enabled by this round.
  Standard preference evidence is user-editable and does not authenticate page
  scanning. Custom paths, guest/private windows, unreadable profiles, exemptions
  and stopping the guard remain limitations. Signed installed-Mac and physical
  iPhone/iPad acceptance, managed-policy verification and release gates remain
  pending; these tests do not prove undeletability or complete bypass prevention.

## Local Engineering Checks: 2026-10-07

### Browser-removal guard and legacy Strict-rule recovery

- Added an explicitly confirmed, default-off Mac outside-lock browser guard.
  Active commitments continue to require guarding independently of that option.
- Browser-level grace and warning evidence survive unverified process restarts.
  A previously healthy browser earns one new startup grace; grace without a
  verified connection cannot earn another. Pure tests cover both paths.
- Authoritative check-in records are exclusive when the authority answers;
  editable local fallback is used only when authority status is unavailable.
- Delayed force-closure tasks are cancelled on enforcement stop or recovery,
  deduplicated per process, and recheck current coverage/connection before force.
  Decision tests cover revocation of outside-lock consent, healthy connection,
  exempt/allowed apps and genuinely disconnected/uncovered browsers.
- Mac native confirmation/recovery copy and iPhone/iPad Screen Time browser
  selection guidance ship in English and Arabic. Safari exemption, personal
  authorization revocation, profile/private-window and process-stop limitations
  remain visible; no setting or mobile shield was silently enabled.
- Extension dynamic policy installation now explicitly disables the inherited
  static Strict catch-all only after replacement succeeds. Production-worker
  regression checks cover Standard recovery, failed replacement and intentional
  Strict blocking. This addresses the reported YouTube false-block cause in
  source; the user's installed extension was not reloaded for a live retest.
- Final macOS suite: 378 passed, zero failures. Localization export: 438 strings,
  all with Arabic. Selected iOS Simulator suite: 57 passed, zero failures,
  including the new bundled English/Arabic guidance test. Full bundled Safari
  compiler test explicitly excluded; default/CI runs still include it.
- Unsigned generic iOS device build passed. Extension units (including 44 real
  worker-entry-point checks with mocked Chrome transport), package validation
  and benign evaluation-corpus gate passed. No real-browser harness or native
  browser-closing acceptance run in this round.
- Python: 5 runner, 88 blocklist, 29 profile, 45 network and 9 mobile-rule tests
  passed. Seed signature/five artifacts verified; 14 release checks passed.
  First sandboxed Python attempt could not write through `/dev/stderr`; the
  reviewed retry passed. `git diff --check` passed.
- Evidence: `/tmp/hisn-mac-plan-build/Logs/Test/Test-Hisn-2026.10.07_11-10-36-+0200.xcresult`
  and `/tmp/hisn-ios-build/Logs/Test/Test-HisnMobile-2026.10.07_11-09-35-+0200.xcresult`.
- No running user browser was terminated, commitment changed, installed app
  relaunched, managed profile installed or installed protection preference
  enabled by this round. Signed installed-Mac and physical-phone/iPad acceptance
  remain pending. Bundle-level heartbeats still do not close the multi-profile
  gap; managed force-install deployment and effective-policy verification remain
  release gates, not features proven by these unit tests.

## Local Engineering Checks: 2026-10-04

### Update and commitment reliability round

- Refuse malformed positive/negative vocabulary and exemption-domain fields
  before installing an extension generation; 51 generation checks passed.
- Preserve native Xcode exit status in the main test runner; include `ios` in
  its default suites. Five isolated runner regression tests passed, including
  a success banner followed by Xcode failure (exit 65).
- Mobile commitment load no longer treats a wrong-type defaults value as an
  absent commitment. Shared restoration tests verify corrupt/absent data and
  latest valid mirror selection. Defaults saves now verify immediate readback.
- Native macOS: 365 passed, zero failures; all 423 exported strings have Arabic.
- Selected iOS Simulator: 56 passed, zero failures. Full bundled Safari compiler
  test excluded locally; it remains included in default/CI `ios` runs.
- Unsigned generic iOS build passed. Extension units, package validation and
  benign evaluation-corpus gate passed. No real-browser UI run in this round.
- Python: 5 runner, 88 blocklist, 29 profile, 45 network and 9 mobile-rule tests
  passed. Signed seed (five artifacts) verified. Fourteen release checks passed.
- Evidence: `/tmp/hisn-mac-plan-build/Logs/Test/Test-Hisn-2026.10.04_9-38-30-+0200.xcresult`
  and `/tmp/hisn-ios-build/Logs/Test/Test-HisnMobile-2026.10.04_9-38-30-+0200.xcresult`.
- Signed physical-device activation, clock/reboot/removal acceptance and public
  release gates remain pending. Mac enforcement/recovery behavior is unchanged.

### Consent-gated filter recovery round

- Mobile reasserts its automatic Screen Time filter during foreground readback
  only if enabled in the current commitment session, the commitment is active,
  storage/history are readable and OS authorization remains approved.
- Historical readiness alone never enables a layer; revocation is not bypassed.
  Mac enforcement and recovery behavior are unchanged.
- Shared decision test checks all 32 combinations of the five required conditions.
- 362 macOS tests and 53 selected iOS Simulator tests passed, zero failures.
  Full bundled Safari compiler test excluded. Unsigned generic iOS build passed.
- Results: `/tmp/hisn-mac-plan-build/Logs/Test/Test-Hisn-2026.10.04_6-50-56-+0200.xcresult`
  and `/tmp/hisn-ios-build/Logs/Test/Test-HisnMobile-2026.10.04_6-51-43-+0200.xcresult`.
- Physical-device permission/recovery checks remain pending. This is not deletion
  prevention or an always-running watchdog; adult OS revocation remains possible.

### Session clock diagnostics implementation round

- Added shared session-local wall/monotonic clock diagnostics and English/Arabic
  warnings on Mac and the universal iPhone/iPad app. Saved deadlines and Mac
  recovery policy are unchanged. Mobile elapsed time now includes device sleep.
- macOS native suite: 361 passed, zero failures.
- Selected iOS Simulator suite: 52 passed, zero failures; the expensive full
  bundled Safari compilation test was excluded from this run.
- Unsigned generic iOS device build passed; `git diff --check` passed.
- Evidence: `/tmp/hisn-mac-plan-build/Logs/Test/Test-Hisn-2026.10.04_6-36-35-+0200.xcresult`
  and `/tmp/hisn-ios-build/Logs/Test/Test-HisnMobile-2026.10.04_6-36-30-+0200.xcresult`.
- Sleep behavior tested with injected elapsed samples, not physical sleep/wake.
  No system clock was changed. Secure time across relaunch remains unimplemented;
  signed device activation and release checks remain pending.

### Readiness continuity implementation round

- Mac, iPhone and iPad now retain a bounded, versioned two-copy baseline of
  previously ready layers. History survives supported relaunches; current
  readiness is still read independently. No browsing history is recorded.
- Mac layer identity is independent of translated names. Unknown/unreadable
  history has its own English/Arabic warning and is not silently overwritten.
- Shared tests cover fresh history, relaunch, separate-window merging, failed
  writes/retry, corrupt records, identifier bounds and avoiding repeated writes.
  Native tests cover restored mobile history without false current protection
  and stable Mac identities.
- Full Mac suite: 354 passed. Selected mobile simulator suite: 44 passed, with
  full-bundled Safari compilation explicitly excluded. Generic-device unsigned
  iOS build, 9 mobile-rule and 14 release tests, release configuration, 422-string
  Arabic catalog coverage and whitespace checks passed.
- Visual accessibility/usability and signed physical-device acceptance remain
  pending. This baseline is neither an authenticated audit trail nor a deletion
  lock, and can be lost if all local copies are removed.

### Rule persistence implementation round

- Versioned two-copy mobile selected-rule storage implemented, including legacy
  reads, explicit deletion tombstones and refusal of corrupt/conflicting/newer
  unsupported schema records. The monitor reads the same store without repair
  writes; failed reads do not clear existing shields.
- Thirteen shared persistence tests run on Mac and mobile cover missing copies,
  interrupted writes, rollback revisions, stale legacy data, corruption,
  revision conflicts/overflow, unsupported schema and isolated defaults.
- Mobile controller tests distinguish unavailable App Group storage and damaged
  saved rules from an unconfigured installation.
- Full Mac regression suite: 347 passed. Selected mobile simulator suite:
  37 passed; full-bundled Safari compilation remains explicitly excluded.
- Generic-device unsigned iOS build, 88 blocklist / 29 profile / 45 network /
  9 mobile-rule / 14 release tests, seed verification, release configuration,
  catalog Arabic coverage and whitespace checks passed.
- First sandboxed Xcode attempts could not access test/simulator services; a
  reviewed retry ran the tests. A generated shared-test group-path error was
  corrected before the successful Mac tests.
- This is same-container redundancy, not secure Keychain storage or an OS
  transaction. Both-copy deletion and physical-device/permission limits remain.
  Full OS apply receipts, trusted-time design and signed-device acceptance are
  not completed by this round. Existing Mac authority/enforcement is retained.

These results describe a dirty development workspace, not a signed release:

- `bash test.sh python seed extension release`: passed. Python suites ran 88
  blocklist, 29 profile, 45 network and 9 mobile rule tests; 14 release tests
  passed. Signed seed verification, JavaScript suites, extension packaging and
  benign-page evaluation passed.
- `bash test.sh browser`: passed all seven real-browser harnesses, including
  scanning, signed updates, English/Arabic settings and popup, block page,
  network rules and evasive-page scanning.
- macOS native suite and selected iOS simulator suite passed. The full-bundled
  Safari compiler test was explicitly excluded from this simulator run; do not
  interpret this as a new full Safari compilation acceptance result.
- Generic-device iOS build passed with `CODE_SIGNING_ALLOWED=NO`.
- Localization catalog check: 421 strings, all with Arabic. `git diff --check`
  passed.
- Read-only signing inspection found an Apple Development identity, but no
  Developer ID distribution identity. iOS development-team fields are blank;
  browser-store URLs remain unconfigured.
- No installer was executed, router configured, system filter activated, app
  uploaded, or physical-phone protection tested by this run. Signing,
  notarization, physical iPhone/iPad and installed-Mac acceptance, public update
  delivery, licensing and distribution remain release gates.

The app and Device Activity extension now restore a recorded same-day mobile
budget shield without restarting monitoring. Unit tests cover day-marker
decisions; actual restoration and callbacks remain physical-device checks.

## Local Engineering Checks: 2026-10-01

These results are for the current workspace, not a signed release candidate:

- Standard macOS suite: 251 tests passed, no failures.
- Release validation suite: 14 tests passed, no failures.
- Python suites: 79 blocklist tests and 29 profile tests passed.
- Seed signature/artifact checks, extension unit/package/evaluation checks, and
  all real-browser harnesses passed.
- Localization export: all 296 app strings have Arabic translations.
- Latest unsigned universal package build and expanded-bundle validation passed.
  Test artifact: `/tmp/hisn-release-final/Hisn-UNSIGNED-TEST.pkg`. It was not installed.
- Setup rendering was exercised at the minimum detail-pane width. The optional
  native screenshot attempt failed with "could not create image from window";
  a complete visual usability review remains pending.
- Developer ID signing, notarization, actual installer execution, live filter
  activation, and non-developer setup remain pending.
