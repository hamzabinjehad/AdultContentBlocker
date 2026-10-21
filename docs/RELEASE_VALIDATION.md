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

## Selected-person recall: 2026-10-16 — extension 1.1.8

- Reproduced the user's screenshot locally with the exact prior bundled
  engine: all-face mode found the five unmasked faces, but both selected modes
  returned no matches. The female appearance estimates were approximately
  0.54–0.74, below the prior 0.80 selected-match cutoff. These are model
  estimates, not evidence of anyone's actual gender or content category.
- A leading-estimate-only prototype improved this sample but fluctuated on a
  borderline face during actual-page capture. It was not activated as a
  release. Globally enlarging/insetting face crops was also rejected after
  local experiments helped some faces but reversed other predictions.
- Final policy withholds matching estimates and uncertain opposite estimates
  below 0.80, without relabeling uncertain faces. Only confident opposite
  estimates stay outside the selected preference. Exact/near ties, malformed
  probabilities and failed appearance inference cannot become clean no-person
  verdicts. English/Arabic Chromium and Safari controls disclose the broader
  masking tradeoff; unintended faces may also be covered.
- Human's detector retains its 0.60 confidence and ten-face cap; explicit 0.30
  overlap suppression replaces the more aggressive inherited setting. No new
  detector passes, model weights, dependencies, age/identity features or
  image uploads were added. An offline rebuild verifies recorded local assets
  before reusing them. The resulting engine SHA-256 is
  `a89276e041687cdb054e0418a059750f36fd959545bd1d1432c3efa841f2be36`.
- Real rebuilt-model comparison on eight local screenshot tiles: PASS for all
  three modes, including selected matching/uncertain faces. Actual canonical
  scanner + model + face-only black-mask renderer: 18 checks PASS in three
  consecutive isolated Helium runs (24.2, 23.6 and 21.2 seconds). All five
  previously unmasked faces receive masks; the blank and two already-masked
  tiles receive no invented additional face masks. This is one local sample,
  not a general accuracy, gender identification or latency benchmark.
- User image and evaluation variants were kept only in a private temporary
  directory, not added to source, packages or remote services. Actual-model
  tests required no accounts, personal browser profiles or external image
  requests.
- `./test.sh extension`: PASS, including 74 visual decision, 19 canonical
  engine and 676 scanner lifecycle checks, preparation/package checks and the
  text evaluation corpus. Full isolated Helium `./test.sh browser`: PASS,
  including real model startup, cross-origin recovery, X playback/feed,
  popup/settings, live-worker scanning and network rules. Shared Safari
  staging/bridge: 12 Python tests PASS. `git diff --check`: PASS.
- Prepared a clean pinned-ID unpacked package on the Mac; ZIP integrity and
  exact source-manifest/engine comparisons pass. ZIP SHA-256:
  `18a3792d98ae776f92dde3309ec6e439ecb0299a499ea8a100428d6867ec4245`.
  Saved a copy in Downloads. Personal browser activation remains unverified
  until Hisn is reloaded and affected webpages refreshed. No native app
  reinstall, protection-setting reset, GitHub push or phone installation was
  performed in this follow-up.
- Coverage remains limited to detected visible faces and sampled readable
  media. This does not identify all women/men, detect hidden/absent faces,
  inspect every video frame or certify every website.

## Ordinary image recovery: 2026-10-16 — extension 1.1.6

- Diagnosed a real cross-origin canvas limitation: an ordinary webpage IMG
  loaded without CORS can be displayed by the browser but unreadable to the
  visual scanner, even if its image server would grant anonymous CORS access.
  The strict gate consequently kept such clean images unknown and hidden.
- On that specific capture failure, Hisn now requests an anonymous reload of
  the actual IMG, once per selected source, only when the webpage has not
  explicitly authored a `crossorigin` mode. It stays hidden until fresh pixels
  from that same DOM image receive a verified verdict. A separately fetched
  image is never evidence that an opaque original is safe. No background URL
  proxy, new host permissions, persistent image cache or model changes added.
- Model results are fenced by exact source, CORS mode, route and generation.
  Normal clean results retain the inspected image load. Opt-out, stop and
  removal restore only scanner-owned CORS attributes, never later author
  changes. Reinserted ordinary images can retry; adult-source latches remain
  blocking. Gallery preparation does not wait one network request per image.
- `./test.sh extension`: PASS, including 676 scanner lifecycle checks and 45
  Python preparation/runner checks. `CHROME=…/Helium ./test.sh browser`: PASS,
  including the existing X playback/feed, settings/popup, live-worker scanner
  and network-rule regressions.
- New real two-origin Helium fixture: 47 checks PASS, repeated three times.
  It exercises real DOM image requests, canvas taint, gate CSS and actual-node
  captures; model replies in this fixture are deterministic test responses.
  One URL deliberately returns red for an opaque request and green for CORS:
  the model boundary and the revealed actual image both contain the same
  green pixels. Denied CORS stays explicitly unknown and hidden.
- New bundled-engine CPU smoke fixture: PASS using the shipped bundle and
  local models on synthetic flat green pixels. Adult and all/female/male face
  modes return ready, non-positive results. This checks model startup and
  inference, not detection accuracy, real-photo coverage or gallery latency.
- Shared Safari staging regression suite: 12 tests PASS. This stages the same
  canonical scanner, but is not evidence of a new iPhone/iPad installation or
  a device/browser-specific Safari runtime test.
- Native `/Applications/Hisn.app` is already version 1.1.3 build 7 and its
  existing deep/strict signature verification passes. No native reinstall,
  protection-setting change, account permission change or filter removal was
  needed. The unpacked extension files are updated on disk; activation in the
  existing personal browsers still requires Reload and webpage refresh.
- Limitation: servers that deny CORS, protected video pixels and unsupported
  surfaces are still withheld. This is not an all-websites fix or permission
  to expose unknown media. Existing intentionally selected person filtering
  continues to apply, including to otherwise ordinary photos.

## Similar-extension adaptations: 2026-10-15 — extension 1.1.5

- Reviewed pinned source from NSFW Filter, HaramBlur and LeechBlock NG, with
  links and license boundaries in `EXTENSION_COMPARISON_2026-10-15.md`. No
  competitor code, model assets, packages or dependencies were copied or run.
- Exact-capture reuse uses browser SHA-256 over captured pixels, dimensions,
  media kind and normalized protection policy. Only complete, sanitized ready
  results are retained: up to 32 entries for 30 seconds, in memory only. No URL
  keys, raw frames, history or persistent verdicts are added. Unknown, failed
  and partial-clean results are not cached. Independent adult-positive results
  remain blocking, including when the optional person layer was not inspected.
- Cache policy epochs prevent A→B→A preference changes from reviving old work.
  Context retirement, disable, cancellation and hash-deadline races are covered.
  Hashing failure falls back to ordinary inference, never a presumed safe
  result. Admission before hashing is bounded to 12 requests globally and two
  per authenticated tab/frame; overflow is explicitly unknown and retryable.
- Toolbar `!` warnings and accessible English/Arabic titles reflect current
  rule/scanner receipts. Prior failure warnings survive pending/restarted
  workers and clear only after verified application. Toolbar API failures do
  not block filtering/native IPC or become cached presentation successes.
- Independent `repairProtection` is restricted to Hisn pages and rejects
  settings-shaped payloads. It reapplies the held policy without native IPC,
  resetting commitments, saving drafts or transferring app ownership. Popup
  Details and the settings overview provide separate, accessible repair feedback.
  A timeout or stale receipt cannot be presented as successful repair.
- Visual readiness now requires both a current installed-scanner receipt and
  model readiness. A warm model alone is not active coverage. Unknown worker
  state is not displayed as a deliberate opt-out, and repair controls remain
  usable when scanner installation fails.
- Completed verification: full extension unit/package/evaluation suite and
  complete isolated Helium browser regression suite passed. Focused tests
  include 89 cache checks, 65 broker-cache checks, 32 prior broker races, 22
  toolbar checks, 151 production-worker checks and 37 visual-status checks.
  Existing 655 visual-scanner and 87 X-feed checks remain green. Forty-three
  extension preparation/package tests and 12 Safari shared-resource tests
  passed. The canonical cache helper is staged into the full Safari extension;
  no physical phone or Safari execution is implied.
- The actual browser WebCrypto/broker fixture verified 50 sequential identical
  synthetic captures require one engine inspection; changed pixels, dimensions
  or policy require new work. Inference is mocked in that fixture. This is not
  a 50-unique-image speed benchmark, real-model latency measurement, increased
  classification accuracy, universal video coverage or live-account acceptance.
- Popup/settings UI regression fixtures passed for English/Arabic and narrow
  layouts, including repair duplication, missing acknowledgments, outage/draft
  recovery, native ownership, commitment visibility and 44px targets. The fresh
  full interactive UI review did not complete: the first run failed an early
  snapshot assertion, a retry's browser closed during launch, and later browser
  operations lost their live handles without completing any checks. These runs
  are not counted as passed, including for toolbar autosizing. The snapshot
  assertion now permits legitimate startup revision changes, captures one
  verified current snapshot and records diagnostic fields. The QA runner also
  explicitly fails incomplete reviews and records original errors before
  potentially interrupted browser cleanup. Final incomplete evidence is in
  `dist/qa-1.1.5-2026-10-15-r4/evidence.json`.
- Native Mac behavior, personal browser profiles, permissions, DNS, policies
  and commitment settings were not changed. No GitHub push or phone install
  occurred. Clean 1.1.5 local and store packages are prepared under `dist/`;
  `Hisn-1.1.5-unpacked.zip` retains the existing development identity, whereas
  the store-clean ZIP deliberately removes it. Reload and refresh are still
  required for existing extension instances and webpages.

## X playback repair: 2026-10-15 — extension 1.1.4

- Ordinary same-source media remains visible while periodic visual checks run;
  normal `playing` events do not cancel inference or reset unknown retry delays.
  Initial admission, source replacement, seeks and policy changes still require
  independent hidden-first checks. Positive and unknown results still withhold
  media, and known adult sources remain latched. Periodic sampling does not
  establish the safety of every intervening frame.
- X text snapshots exclude changing playback controls and engagement/analytics
  counters. Hisn's visual gate no longer makes unchanged image/video captions
  disappear from text scoring. Actual post/caption changes still trigger fresh
  verdicts. Already paused media is not paused again on every polling cycle.
- Verification: 655 scanner lifecycle and 87 granular-feed checks passed;
  extension unit/package/evaluation suite passed. The standalone feed/gate
  browser fixture passed eight checks. The combined actual feed scanner,
  visual scanner, computed CSS and decoded local MediaStream fixture passed
  22 checks with real timers in isolated Helium. Playback advanced during
  delayed periodic inference without additional text scores or pause calls.
  The complete isolated Helium browser suite also passed, including real-worker
  X/Twitter fixtures, network rules, settings and the popup.
- Verdicts, X location and buffer/seek events in that combined fixture are
  simulated. This is not logged-in X, real-model accuracy, cross-origin media
  or physical Safari validation. Twelve Safari shared-resource/staging tests
  passed; the source repair is shared with the full Safari extension, not
  installed onto a phone by this check.
  A media element's own author stylesheet `visibility:hidden` can still be
  masked by the visual gate; inline hiding and hidden/display:none ancestors
  remain excluded from text captions.
- Native Mac behavior, commitments, DNS, browser policies and user preferences
  were not changed. The installed Mac application was verified as 1.1.3/build 7;
  this is an extension-only update and requires extension reload plus refreshing
  already-open X pages. A clean unpacked 1.1.4 copy and store-clean ZIP are staged
  under `dist/`; the latter does not retain the native development extension ID.

## Local candidate: 2026-10-15 — 1.1.3 build 7

- Full Mac suite: 489 tests passed, zero failures. Release build succeeded;
  app, embedded bridge and filter share version 1.1.3/build 7. The local bundle
  is ad-hoc signed and passes deep strict signature verification. This is not
  Developer ID notarization or authorization of Hisn's network filter.
- Connection evidence no longer clears all live processes after 64 dictionary
  entries. Five-minute retention and a 4,096-key ceiling bound the ledger;
  new identities at capacity are explicitly refused without deleting existing
  fresh stamps. The bridge does not yet surface this capacity refusal in its UI.
- Setup shares the runtime's bounded profile reader, including modern
  enablement and conservative conflict handling. Managed setup still needs an
  appropriate policy installation location. Profile/process attribution and
  custom data-root coverage remain incomplete; this slice does not silently
  weaken existing runtime closure decisions.
- The browser warning panel now grows for multiple warnings and provides a
  bounded scrolling region instead of clipping all alerts into a fixed panel.
  Physical multi-warning accessibility/fullscreen acceptance remains pending.
- Extension suite, package validation, release/installer checks and the complete
  isolated Helium browser suite passed. There are 644 scanner lifecycle checks
  and 32 broker race/recovery checks. Real-browser fixtures cover computed
  hide-first CSS, a 240-image gallery, small thumbnails and ordinary local
  MediaStream video. They do not establish real-site classification accuracy.
- Actual-worker desktop/toolbar UI QA passed 71 checks in English/Arabic and
  light/dark modes. The included Safari-layout checks are mocks, not physical
  Safari execution. A readiness-fixture race was corrected to retain one verified
  snapshot instead of comparing it with a later, legitimately pending repair.
- Desired and applied browser policy are separate. Static/dynamic rule and
  scanner-registration readback failures remain pending/failed, with independent
  successfully verified layers shown separately. Native connection ownership
  is preserved if browser enforcement repair fails. UI native checks have a
  longer bounded deadline; an injected seven-second native response succeeds.
  Readback confirms configuration, not filtered traffic on every page.
- Visual admission is no longer capped by retaining the first 128 completed
  visible nodes. Weak per-node records and bounded active slots allow later
  items to progress. Inaccessible media stays unknown/withheld with retry backoff;
  it is not silently revealed or classified as harmless. Visible localized
  placeholders and new capture capabilities are still pending.
- Chromium visual requests have a 45-second transport deadline. A stalled
  isolated offscreen context is retired before a replacement is admitted;
  stale results and opt-out/recovery races are covered. A cleanup deadline
  does not grant permission to run overlapping model contexts. Slow loading or
  queueing can also trigger this deadline; this is not proof of an inference
  defect. Safari's shared background engine is not recreated automatically after
  a stall and reports restart-required instead.
- Shared Safari-resource/package tests: 27 passed. No physical phone update,
  model accuracy benchmark or real-user browser closure test was performed.
- Downloads: `dist/Hisn-1.1.3-mac.zip`, `dist/hisn-extension-1.1.3.zip`,
  and the clean unpacked folder `dist/hisn-extension-1.1.3/`. Installed personal
  browsers currently reference the canonical `extension/` folder and require
  reloading the extension to activate changed worker/modules. Their profiles,
  commitment data and management policies were not rewritten for tests.

Installation result must be checked separately against `/Applications/Hisn.app`;
building/staging the candidate alone does not prove deployment. The larger
[REA plan](REA_FUNCTIONALITY_PLAN.md) and signed-device gates above remain open.

Deployment checkpoint: macOS denied the privileged updater access to the Desktop
source path before it could start. An identical updater and verified candidate
were then staged in `/tmp/hisn-update.2Othnv/`; administrator confirmation is
pending. At this checkpoint the installed app is still 1.1.2/build 6 and its
existing process and root-owned login agent remain unchanged. Do not report the
candidate as installed until the installed bundle and relaunched process are
verified. Personal browser extension reloads are also still unconfirmed.

## Local Engineering Checks: 2026-10-09 — Verified setup milestone

- Mac setup readiness is derived from current live filter/policy evidence,
  detected browser profiles, connection freshness, installation files and the
  consented outside-lock requirement. Legacy setup visits and user-writable
  domain counts cannot certify a new session; hosts-only blocking stays partial.
- Local Mac Release build succeeded. The 439 hosted Mac tests passed, including
  readiness regression, explicit navigation and injected EN/AR setup rendering.
  Arabic catalog validation and compiler-based localization export passed.
- Python, signed-seed and release-configuration suites passed. The mobile app,
  all four Safari extensions and hosted test target compiled for generic iOS
  Simulator. A standalone Swift check passed 9,216 mobile setup-evidence
  combinations and list-integrity guards. This is not a real-device acceptance
  record, and compilation is not execution of the hosted mobile tests.
- Mobile readiness checks include nonempty bundled Safari resources with
  matching hashes/counts/versions, all four blocker states, Screen Time readback
  and matching enabled Family DNS. The verdict describes layer configuration,
  never all-content filtering or uninstall prevention.
- The laptop remains a local ad-hoc development installation. Hisn's system
  filter is not activated by this milestone. Proper signing, macOS approval,
  installed filter liveness and supported end-to-end flow/outage checks remain
  release gates. No account, DNS, router, commitment or guardian settings were
  changed by the setup-screen work.

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
