# Hisn: comprehensive improvement and performance plan

Date: 8 November 2026. Status: proposed work, not implemented.

## Scope and confidence

This is a repository-based review of the product's feature families, blocking
architecture, existing acceptance records and performance opportunities. It is
not a line-by-line audit of every file, a security audit, a fresh benchmark or
a live test of every screen. No protection settings or application code were
changed. Existing working-tree changes were left alone.

Sources include README.md, versions.json, docs/POLICY.md,
docs/BLOCKING_PIPELINE.md, docs/1.0_READINESS.md, the existing improvement
roadmaps, docs/WEB_VAULT.md and docs/VISUAL_ENGINE_WASM_2026-11-07.md;
source checks include the Mac filter/store, iOS DNS engine/index, extension
scanner and Android DNS/visual services. Follow-up tickets must inspect their
complete affected paths before implementation.

An immediate evidence problem: untracked docs/ANDROID_1.4.0.md describes
TfliteClassifier, VisualRuntime and native inference results, but this checkout's
build.gradle declares 1.2.3/build 32, VisualGuard uses VisualClassifier, and
TfliteClassifier.java is absent. Treat that document and its measurements as a
separate recorded candidate until the corresponding source and artifact are
located and verified. Do not announce native inference as shipped here.
README also contains inconsistent bundled-core counts. Generate those facts
from verified artifacts rather than copying release prose forward.

## Existing feature inventory and next improvements

| Area | Present in source or repository records | Next improvement |
| --- | --- | --- |
| Domain protection | Signed lists, bundled core, adult-TLD rail and hostname terms | Verified mobile updates, freshness, capacity and rollback tests |
| Browser network rules | DNR domain/URL/path rules, reviewed obfuscation, encoded Arabic queries, SafeSearch, restricted YouTube, strict mode | Real-browser capability tests, transactional installation and measured navigation coverage |
| Browser text | Language-aware scoring, normalization, metadata/frame collection, feed-item handling, retries | Incremental collection, bounded queues, expanded multilingual evaluation |
| Visual protection | Opt-in local image/video checks; TFJS backend ladder; Android captures selected windows | End-to-end latency, native inference experiment, positive and benign evaluation |
| Mac system filter | Socket-flow decisions, signed list store, strict mode, app restrictions, aggregate deny count | Installed signed filter acceptance, flow identification and update contention measurements |
| Mac browser controls | Extension bridge, heartbeat/profile evidence, private-window status, browser guard | Profile/private/guest matrix and installed restart/recovery acceptance |
| iPhone/iPad | DNS proxy, six Safari blockers, Safari scanner, selected-app shields, budgets, quiet hours, browser/VPN guard | Entitled physical-device proof, monitor/rule revision consistency, distribution feasibility |
| Android | DNS VPN, selected-app and word restrictions, usage budgets, schedules, accessibility scanner, visual sampling | Network transitions, OEM lifecycle resilience, scan latency and battery measurements |
| Commitment | Deadlines, freeze/extension rules, mirrored state, guardian/partner paths | Clock/corruption/reinstall acceptance and explicit authority ownership |
| Removal resistance | Mac standard/admin split and guardian daemon; profiles/MDM; Android owner/removal controls; iOS Lock Center | Demonstrate each supported level and recovery on real enrolled devices |
| Personal rules | Custom blocks/allowances, terms/corrections, named site shortcuts, browser rule import/export | Cross-platform schema, conflict previews and capability-aware transfer |
| Support tools | Site checker, readiness/layer status, DNS route checks, notices and licenses | One guided diagnostic flow with current evidence and repair action |
| Behaviour support | Breathing pauses, reminders, reflection, iOS pause grants | Clarify which actions grant access; accessible, optional support without weakening commitments |
| Interface | English/Arabic, RTL, light/dark, onboarding, overview, rule/settings editors | Task-based usability testing, keyboard/screen-reader/dynamic-type checks |
| Network/router | Signed DNS exports, discovery/guides and profile generation | Supported-model adapters with backup, owner consent, rollback and post-change probes |
| Web vault/partner | Encrypted off-device passcode document, partner and time-release paths | File-loss/recovery drills, browser compatibility, independent cryptographic review |
| Release engineering | Test runners, signing/package checks, version and readiness records | Artifact-to-source traceability and physical-device release gates |

## Blocking architecture and boundaries

Keep independent layers. A hostname decision prevents a known destination;
page text evaluates content after rendering; visual inference samples readable
media; OS controls enforce selected apps and removal restrictions. None alone
establishes complete protection.

Preserve the existing decision contract: hand-rule specificity and tie behavior,
built-in higher-priority restrictions, strict-mode plumbing boundaries, and
authority-owned locks. Do not implement a generic “allow always wins” rule:
the policy document has exceptions and more-specific blocks. iOS DNS currently
exposes custom blocks rather than the entire Mac/browser rule model; record
that capability difference instead of silently inventing parity.

Test encrypted DNS, VPN/proxy routing, IPv6, direct-IP access, existing open
connections, caches, service workers, unsupported browsers and unreadable media
as distinct coverage cases. A passed DNS probe does not prove every app uses
that route. Do not introduce TLS interception as a default solution.

## Ordered delivery backlog

Effort labels are relative: S = focused task, M = several connected changes,
L = cross-platform work or hardware acceptance. They are not calendar promises.

| ID | Priority / effort | Concrete deliverable | Acceptance gate |
| --- | --- | --- | --- |
| E1 | P0 / S | Reconcile versions, source, release notes, artifact hashes and native-ML candidate | Every claimed feature links to source revision, artifact and explicit test environment; unresolved candidates remain labelled |
| E2 | P0 / M | Platform capability/evidence ledger | Setup distinguishes configured, runtime checked, degraded, unavailable and unknown; no protection percentage |
| E3 | P0 / L | Signed installed-device acceptance | Mac filter and physical iPhone/iPad exercise actual OS callbacks; Android runs current artifact on hardware; entitlement blockers recorded |
| E4 | P0 / M | Lifecycle/recovery suite | Reboot, sleep, process death, permission loss, failed storage, expired lock and authorized recovery preserve documented behavior |
| E5 | P0 / M | Revision-bound enforcement receipts | UI reports a rule applied only when the enforcing service confirms that revision; interrupted updates retain coherent prior state |
| B1 | P1 / L | Signed mobile list update design and implementation | Atomic generation staging, signature/hash/schema validation, persistent rollback floor, last-known-good fallback and offline/corruption tests |
| B2 | P1 / M | Network bypass and transition matrix | IPv4/IPv6, UDP/TCP DNS, truncated replies, Private DNS, VPN conflict, Wi-Fi/cellular and captive portal outcomes documented |
| B3 | P1 / M | Transactional rule/list replacement stress tests | Concurrent lookups see complete old/new generations; failed install retains old enforcement; no stale clean cache after policy change |
| B4 | P1 / L | Mixed-content site adapters | Supported real DOMs, reused nodes, virtualized feeds, late captions and SPA changes validated; item blocks stay scoped where supported |
| B5 | P1 / M | Multilingual text quality evaluation | Arabic dialects/Franco-Arabic, mixed scripts, English/German and supported languages; misses and benign blocks reported separately |
| B6 | P1 / L | Visual quality evaluation | Appropriately sourced lawful adult-positive and difficult benign controls; held-out evaluation by layout/size; sample counts and confidence limits |
| B7 | P1 / M | Visual failure policy | Secure windows, timeout, runtime crash and unsupported media remain unknown/degraded; stale verdicts cannot act on a different window |
| P1 | P1 / M | Unified performance harness | Capture, conversion, queue, inference, scoring, dispatch and intervention timed separately; cold/warm p50/p95/p99 |
| P2 | P1 / M | Native Android inference experiment | Current TFJS baseline vs same-model native runtime, full preprocessing and score parity, hardware latency/thermal/memory results |
| P3 | P1 / M | Incremental browser text scanning | Changed regions only, bounded pending work and maximum wait, revision-aware cache invalidation, 10/50/100-post workloads |
| P4 | P2 / M | Domain index/storage experiment | Compare current hash and sorted-byte approaches with compact exact index; load/RSS/lookups measured on full and core tiers |
| P5 | P2 / M | Rust matching prototype | Identical fixtures and representative hardware show a material end-to-end benefit before production adoption |
| U1 | P1 / M | Guided setup/repair | One next action per failed layer, successful real route check where possible, clear partial coverage and denied-permission recovery |
| U2 | P1 / M | Rules editor consistency | Search, dedupe, validation, specificity explanation, import preview and locked-change explanations across platforms |
| U3 | P1 / M | Accessibility acceptance | Arabic RTL, long translations, large fonts, contrast, focus order, keyboard and screen readers across key flows |
| U4 | P2 / M | Portable personal-rule document | Explicit supported fields, add-only/lock-safe behavior, schema/version checks, conflict preview; no hidden policy weakening |
| U5 | P2 / M | Optional local progress | Aggregate useful trends without sites, queries, screenshots or guilt-based scores; clear deletion/retention controls |
| L1 | P1 / M | Commitment and authority matrix | Every mutation is classified as tightening/weakening/recovery, consistently enforced by actual authority; time-change tests |
| L2 | P1 / L | Managed-device acceptance | Real MDM/owner enrollment, policy readback, uninstall attempt, permission loss and authorized offboarding tested |
| L3 | P1 / M | Vault recovery/availability drills | Lost file, wrong partner key, tampering, offline use, unavailable beacon and post-date opening; no premature key release |
| Q1 | P1 / M | Private diagnostic bundle | Opt-in export of versions, capability states, timings and error codes; no browsing material or keys |
| Q2 | P1 / M | Artifact release gate | Test exact packaged bytes, verify signing/resources/generated files, platform smoke test and explicit release evidence |
| Q3 | P2 / M | Maintainability pass | Split large UI/service files at state/side-effect boundaries; keep policy logic pure and generated assets reproducible |
| N1 | P3 / L | Router adapters | Start with one verified model; explicit owner action, backup, rollback and independent resolver validation |
| N2 | P3 / L | Optional multi-device coordination | Threat model, privacy and recovery design first; authenticated signed policy transfers, offline behavior and lock-safe conflict handling |

## Rust and other performance options

Rust is a candidate for a small shared engine, not evidence of an automatic
speedup. It provides native code without a garbage collector and can integrate
through a C ABI ([Rust project explanation](https://blog.rust-lang.org/2015/04/24/Rust-Once-Run-Everywhere/)).
Swift already executes native code; Android Java can delegate inference to
native kernels; browser network rules execute in the browser. Changing their
language does not remove capture limits, OS callback delays or model cost.

| Component | Preferred first action | Rust assessment |
| --- | --- | --- |
| Android visual inference | Benchmark bundled native LiteRT/TFLite against current packaged TFJS | Runtime/model acceleration likely more useful than rewriting service glue |
| Mac/iOS domain lookup | Profile existing Swift and reduce allocations/index overhead | Useful experiment if full-list memory or p95 lookup is a demonstrated problem |
| Text normalization/scoring | Incremental input collection; shared fixtures; bounded matching | Potential shared Rust core compiled native/WASM, with Unicode parity checks |
| DNS parsing | Profile and fuzz existing parsers; enforce bounds | Potential memory-safe shared parser, but integration must justify maintenance cost |
| Browser DNR | Keep declarative rules and generator tests | Replacing DNR with per-request application calls would change the architecture adversely |
| Browser DOM collection | Reduce repeated walks and message payloads | Rust/WASM cannot remove DOM traversal and binding costs |
| Native UI and platform controls | Keep native frameworks and improve state ownership | No rewrite justified by available evidence |
| Build-time list processing | Measure build bottleneck first | Lower priority unless publication time becomes operationally significant |

Android's official guidance supports native on-device inference and hardware
acceleration ([Android ML documentation](https://developer.android.com/ai/custom)).
Evaluate a bundled/offline runtime to preserve direct-build availability; do
not add a Play-services dependency without checking that product requirement.
GPU/NPU delegates and quantization are experiments: verify supported operations,
fallback, preprocessing, score drift and decision quality before adoption.

The recorded WASM benchmark reports ~97 ms warm median versus ~1,111 ms CPU
in headless Chrome on an arm64 Mac. That is one controlled environment, not an
Android hardware claim. The separate 1.4.0 note reports native emulator results
but currently lacks corresponding source here. Reproduce both relevant paths
against identical input before using those numbers to choose an implementation.

For a Rust prototype, expose coarse batched calls through C ABI for Swift,
JNI for Android, and WASM for browser scoring. Keep platform lifecycle, OS
permissions and authority enforcement native. Define buffer ownership, error
codes, panic containment and versioned policy snapshots. Fuzz the boundary;
avoid per-token bridge calls. Maintain a single reviewed vocabulary and shared
fixtures rather than adding a fourth divergent normalization implementation.

Suggested adoption gate (a proposed decision rule, not a measured result):
zero contract-fixture regressions and either at least 20% improvement in a
material end-to-end p95 path or at least 25% reduction in relevant peak memory,
with acceptable cold start, binary size, build complexity and no protection
quality loss. A large microbenchmark gain alone is insufficient.

## Measurement contract

Measure user-visible time from navigation/content arrival to actual intervention.
Report detection sampling delay separately from inference: a fast classifier
still leaves a long exposure window when capture happens every few seconds.
The extension scanner currently has a 2-second settle and 5-second maximum wait;
those scheduling choices can dominate scorer execution time.

Use low/mid/high Android hardware and a signed Mac/iPhone/iPad set, cold/warm
loads, offline mode, sustained scrolling and a 30-minute workload. Record
CPU, whole-process-tree memory (including WebView renderer), thermal state,
battery conditions, dropped/stale samples, UI stalls and fallback frequency.
Emulator results are regression signals, not battery-life evidence.

Never report accuracy from only benign images or repeated layouts. Separate
adult recall, benign false-block rate, unknown/unreadable coverage, language,
site/layout and exposure time. Review difficult benign medical, educational,
art, family, religious and recovery contexts. Keep raw browsing content out of
persistent diagnostics; test corpora must have an appropriate provenance.

## Execution sequence and continuing improvement

1. Establish truth: E1–E2 and P1, then unblock E3 with signing/entitlements and
   physical devices. Record hard platform constraints before expanding promises.
2. Stabilize enforcement: E4–E5, B2–B3, L1 and Q2. Build B1 against those contracts.
3. Improve measurable detection: B4–B7, P2–P3, using quality and latency results
   together. Preserve unknown states and supported recovery behavior.
4. Improve daily use: U1–U4 and Q1, then managed-device/vault acceptance L2–L3.
5. Run P4–P5 only where profiles justify them; extend to N1–N2 after core release
   gates are met. Keep optional breadth from delaying basic device proof.

For every subsequent cycle, select a measured failure or a demonstrated user
problem, define the acceptance gate, ship a bounded change, verify the exact
artifact and update the evidence ledger. Re-rank quarterly using severity,
frequency, coverage gain, user friction and maintenance cost. This is how the
plan supports continued improvement without an unbounded rewrite.

## First concrete work package

- Reconcile the missing Android native-runtime candidate and source/version facts.
- Produce the capability ledger and a physical-device acceptance checklist.
- Instrument capture-to-intervention latency with ephemeral aggregate timings.
- Benchmark current Android visual inference on hardware; compare a native
  prototype if the recorded candidate source can be recovered or reproduced.
- Expand text/visual evaluation with difficult benign and adult-positive cases.
- Implement incremental browser scanning after the baseline identifies its cost.
- Decide on Rust only after these measurements identify a suitable shared core.

Planning verification: source paths and current version/class wiring were
inspected. Existing test-result records were consulted, not rerun. Application
tests are not claimed for this documentation-only change.
