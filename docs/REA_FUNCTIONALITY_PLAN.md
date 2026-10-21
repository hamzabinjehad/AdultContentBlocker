# Hisn: REA-informed functionality plan

Date: 2026-10-13. Status: research and implementation plan, not a shipped update.

Implementation update, 2026-10-15: the 1.1.3 local candidate implements bounded
heartbeat retention, shared bounded Setup/runtime profile parsing, scrollable
multi-browser countdowns, extension applied-policy receipts and readback,
connection-check timing, visual gallery admission/fairness, and Chromium
offscreen transport recovery. See [the validation record](RELEASE_VALIDATION.md).
This is a subset of the backlog below, not completion of every release gate.
Per-instance profile attribution, visible localized unknown-media placeholders,
new cross-origin/DRM capture capabilities, native protocol compatibility
diagnostics and public managed deployment remain unfinished.

Goal: improve the extension and Mac app's real protection, responsiveness and
reliability. Connected browsers should remain usable; verified loss of required
protection should produce a clear recovery warning. Normal supported videos
should remain available, with targeted filtering rather than a platform-wide ban.

## What was actually done with REA

[REA](https://github.com/morluto/rea) 6.4.0 was run locally as a temporary CLI,
without registering an MCP server, changing agent configuration, installing a
native disassembler, or executing an inspected application's code. Dependencies
were installed into a temporary directory with installation scripts disabled.

- Hisn extension: a 37-file snapshot of selected first-party runtime sources was
  checked byte-for-byte against the current working tree. REA parsed 29
  JavaScript files and reconstructed 188 graph nodes and 333 relationships.
  Its application coverage is **partial** and semantic coverage is **unknown**.
  Generated engine code, vendor libraries, weights, rulesets, tests and some UI
  entrypoints were excluded. Static observations are not a count of bugs.
- Installed Mac app: REA inventoried `/Applications/Hisn.app`, version 1.1.2,
  build 6, with 34 inventory nodes. This identifies the installed bytes; it does
  not establish signature validity, active filtering, or current source parity.
  Native functionality was reviewed directly in Hisn's available source.
- HaramBlur: REA statically analyzed selected public source from commit
  `f47000ef21d2c067e0c0290e96c14968b56d459c`, parsing 14 JavaScript files.
  Application graph coverage is complete only for the selected artifact;
  semantic coverage remains unknown. Dependencies and model assets were excluded.
- SelfControl: REA imported the sparse public source at commit
  `34774c413013f903dce00c4f5cb17bc8d3a3c273`: 109 source entries, partial
  inventory, and no recovered source relationships. It was not compiled,
  decompiled, installed or run. Architectural conclusions also required direct
  source review; the inventory alone does not prove daemon behavior.

An earlier Hisn pass including `engine.bundle.js` hit REA's analyzer heap limit.
The focused first-party pass completed without that limitation. This is a tool
analysis limitation, not evidence that Hisn's runtime model exhausts memory.

Compact receipts are generated in
`dist/rea-audit-2026-10-13/hisn-audit-summary.json` and `extension-view.json`.
Full local evidence remains in `/tmp/hisn-rea-audit.vQAfVs/reports/`; these are
temporary engineering artifacts, not part of the app or a public release.

Repository baseline: `7c68695718cac1a62e3805a89a758e8171414f70`, branch
`codex/visual-ui-1.1.2`, with existing uncommitted work. The audit concerns that
working tree, not just the named commit. No application functionality was changed
or installed during this research pass.

## Lessons from comparable apps

| Reference | Pattern worth studying | Hisn implementation direction |
| --- | --- | --- |
| [HaramBlur queues](https://github.com/alganzory/HaramBlur/blob/f47000ef21d2c067e0c0290e96c14968b56d459c/src/modules/queues.js) | Separate media preparation from inference | Benchmark preparation separately, prioritize visible work, and keep each stage bounded. Do not inherit its queue sizes or claim its speed is proven. |
| [HaramBlur background](https://github.com/alganzory/HaramBlur/blob/f47000ef21d2c067e0c0290e96c14968b56d459c/src/background.js) and [offscreen logic](https://github.com/alganzory/HaramBlur/blob/f47000ef21d2c067e0c0290e96c14968b56d459c/src/offscreen.js) | Model-context recovery and video request correlation | Build an independent watchdog, engine generations and stale-frame rejection; preserve ordinary video playback. |
| [SelfControl daemon](https://github.com/SelfControlApp/selfcontrol/blob/34774c413013f903dce00c4f5cb17bc8d3a3c273/Daemon/SCDaemon.m) | Enforcement lifetime distinct from a window's lifetime | Validate Hisn's existing provider/authority independently from its window and browser guard, across crashes, reboot and updates. |
| [SelfControl block management](https://github.com/SelfControlApp/selfcontrol/blob/34774c413013f903dce00c4f5cb17bc8d3a3c273/Daemon/SCDaemonBlockMethods.m) | Authority-side updates and inspection of applied rules | Keep Hisn's existing refusal invariants; distinguish saved policy from acknowledged enforcement and verified traffic. |

[HaramBlur's license](https://github.com/alganzory/HaramBlur/blob/f47000ef21d2c067e0c0290e96c14968b56d459c/LICENSE)
is AGPL-3.0; [SelfControl's COPYING](https://github.com/SelfControlApp/selfcontrol/blob/34774c413013f903dce00c4f5cb17bc8d3a3c273/COPYING)
is GPL-3.0, with selected source headers specifying version 3 or later. Public
inspection is not a decision to incorporate their implementation or assets.
Use independently written Hisn implementations; review licensing separately
before any reuse. Do not import hover-to-reveal, quick-disable or kill controls.
Reference source and product claims are not proof of flawless enforcement.

## Ordered implementation backlog

### 1. P0 — Preserve healthy browser connections

Evidence: `macos/Hisn/PolicyAuthority.swift` clears the entire check-in map after
it exceeds 64 entries. Browser launch churn can therefore discard fresh evidence
alongside obsolete process records. `ExtensionPresence.swift` also assumes the
native host's immediate parent is the UI browser process; this still needs
installed-browser verification.

Changes:

- Replace whole-map clearing with bounded retention/pruning that preserves
  current eligible process evidence. Report true capacity overflow explicitly.
- Verify browser identity against OS process lifetime and native-host ancestry;
  do not trust an extension-supplied PID or borrow another process's heartbeat.
- Make profile evidence attributable to process/data root where supported.
  Current bundle-wide profile loss and fixed default roots can penalize unrelated
  healthy instances while missing custom roots. Do not simply ignore confirmed
  loss: distinguish active attributable loss, dormant profiles and unknown scope.
- Explain the shared-process case: some profiles share one browser process, so
  terminating it also closes healthy profiles. Do not promise per-profile OS
  closure where the browser offers none.
- Use one bounded profile interpretation for Setup and runtime. Setup currently
  requires legacy `state == 1`, unlike the newer runtime reader, and reads
  preferences without the runtime's size budget.
- Keep all simultaneous warnings readable in a bounded, scrollable panel.

Acceptance:

- More than 64 historical PID entries do not erase valid current connections.
- Healthy Chrome/Helium instance A stays open while unprotected B counts down.
- Recovery before the deadline cancels both termination and delayed force-close.
- PID reuse, helper parents, custom roots, dormant/shared profiles, conflicting
  preferences and unreadable files produce correct or explicitly unknown states.
- Preserve the initial visible 60-second warning and existing repeated-relaunch
  behavior; no silent reduction, extension or new consent to close browsers.
- Menu tracking, sleep/wake, authority restart and clock adjustments are tested.
  The remaining wall-clock warning-ledger gap needs a separate monotonic-timing
  design; do not claim this audit solved it.

### 2. P1 — Show applied protection, not just requested settings

Evidence: `extension/background.js` stores requested state and heartbeat before
all enforcement operations finish. `extension/popup.js` can report domain
protection from stored state alone. The UI request helper times out after five
seconds while native polling permits ten seconds.

Changes:

- Track desired revision and applied revision separately, with ruleset readback,
  scanner-registration evidence and typed repair failures.
- Add a versioned bridge capability/health contract. A live reply establishes
  connectivity, not proof of classification or every protected profile.
- Align connection-check deadlines and show progress for delayed valid replies.
- Diagnose missing host, stale executable path, disallowed origin, incompatible
  build, malformed response and authority outage with specific repair actions.
- Keep standalone and app-managed ownership clear. Loss of one layer must not
  silently disable independent rules or transfer policy authority.
- Show commitment transitions as Applying, Confirmed or Recovering until the
  relevant authority acknowledgement arrives. Preserve stricter existing rules
  if applying the desired revision fails.

Acceptance:

- A seven-second native reply succeeds without an earlier false failure.
- Rejected ruleset/scanner updates never appear fully applied in the UI.
- Recovery converges to the desired revision without loosening a locked policy.
- Old bridge fields remain compatible and missing fields do not delete rules.
- Administrator-owned manifests are diagnosed, not silently overwritten.

### 3. P1 — Make hide-first visual checking complete within its supported scope

Evidence: `visual-gate.css` gates all unchecked images/videos, while `visual.js`
only admits sufficiently large visible media and stops at 128 retained records.
Completed visible records can prevent subsequent gallery items from being
processed. Current canvas capture cannot read every cross-origin/protected video.

Changes:

- Make gate eligibility and scheduler admission agree. Every withheld item
  needs a meaningful checked, pending, blocked or uninspectable state.
- Separate lightweight verdict retention from active inference capacity so
  large visible galleries make progress without unbounded listeners or memory.
- Preserve withholding and stale-result checks across source changes,
  reinsertion, SPA navigation, frames and queue eviction.
- Give unreadable media a clear local placeholder and reason, with bounded retry
  for transient failures. Do not silently mark it safe or reveal it on timeout.
- Investigate browser-supported, narrowly permissioned capture alternatives for
  ordinary remote media. DRM/protected media remains honestly unsupported unless
  an actual supported capability is demonstrated.
- Define small-media and unresolved-media policies explicitly before changing
  them. Dimensions alone are not evidence of harmless content; changes must
  respect the user's selected policy and commitment.

Acceptance:

- All eligible items in a 200-item visible gallery progress to an explicit state.
- 16/32/48-pixel fixtures follow the documented policy rather than remaining in
  an unexplained perpetual gate.
- Tests inspect computed CSS/rendered output, not merely inline visibility.
- Same-origin, CORS-enabled, restricted-origin, embedded and protected media are
  tested separately. Supported normal videos remain usable; unreadable media
  never receives a false clean verdict.
- Adult-positive media stays withheld; optional person/face preferences remain
  separate from adult-content classification.

### 4. P2 — Recover stalled detection and improve measured performance

Evidence: `extension/visual/queue.js` bounds waiting work but awaits an active
inference without a completion deadline. Marking an active request cancelled
does not end that inference. A stalled engine can obstruct work from every tab.

Changes:

- Add a bounded watchdog coordinated with broker/offscreen lifecycle, explicit
  engine generations, stale-reply rejection and controlled context replacement.
- Keep preparation, inference and video scheduling separate; avoid launching
  overlapping model contexts merely because one caller timed out.
- Prioritize visible foreground work with fairness for other tabs. Reuse only
  valid, memory-bounded local results without keeping browsing-history caches.
- Improve face-mask stability and video sampling only after measuring playback
  interruption, missed content and resource use. Do not claim every frame was
  classified or a person's identity was determined.
- Add a local benign preview for existing face/full-media, effect and image/video
  options; independently evaluate optional strength controls without duplicate
  settings or a reveal shortcut.

Acceptance:

- A never-resolving inference triggers bounded recovery; another tab eventually
  progresses, and obsolete results cannot reveal replacement media.
- Model-context count, frame retention, queue size and listeners remain bounded.
- Measure cold/warm single-image latency, 50/200-image gallery completion,
  scrolling, video interruptions, CPU/GPU, memory and battery on named hardware.
- Set performance budgets from that baseline and gate regressions. Fast CSS
  withholding is distinct from model inference; no universal millisecond or
  zero-exposure promise is justified by a static analysis report.
- Validate misses and false positives on an appropriately obtained benchmark,
  including benign medical, educational and ordinary-video content. No private
  browsing captures are uploaded or persisted for testing.

### 5. P2 — Verify independent layers and supported removal resistance

Changes:

- Test the existing Mac provider/authority separately from window closure,
  app-process failure and extension-only operation. A LaunchAgent alone does not
  prove the network filter is active or browser guarding cannot be interrupted.
- Publish a compatible store extension ID/update URL before public managed
  deployment. Development/unpacked copies are not a force-install guarantee.
- Verify effective policies in each supported browser; sharing Chromium ancestry
  does not establish that every fork respects the same management settings.
- Keep browser rules, signed device filtering and explicitly chosen network/DNS
  layers independent, with separate health evidence and essential-service tests.
- Provide an opt-in management setup with a precise explanation of scope and
  administrator powers. No hidden browser policies, router changes or account
  demotion; no new kill switch, and existing documented recovery remains intact.

Acceptance:

- Record exact app, bridge, authority, browser and extension versions.
- Use disposable accounts/profiles for extension disable/removal, browser update,
  multiple processes, private/guest windows, reboot, sleep and authority outages.
- Verify real allowed/blocked traffic as well as configured policy.
- Keep coverage and removal-resistance claims separate. An administrator or
  device owner still has recovery powers; standalone extension deletion removes
  that extension's enforcement. Do not advertise universal undeletability.

## First implementation slice and release gate

Start with stage 1's check-in retention regression, then shared profile evidence
and per-instance attribution. Next implement stage 2's applied-health contract;
visual scheduling and watchdog improvements depend on that truthful status.

Each slice should have a failing regression fixture, an independent Hisn
implementation, targeted tests, existing-suite regression checks and a separate
installed-browser acceptance record. Rerun the relevant suites against the
current dirty tree; earlier test results are not a new release certification.

Do not change personal browser profiles or installed protection while testing.
Install, change management settings, publish or merge only in a separately
authorized deployment step. iPhone/iPad are outside this Mac/Chromium plan;
none of these native process-closure claims should appear in their UI.
