# Android features: implementation and improvement plan

8 November 2026 · Proposed work, not implemented.

## Goal and starting point

Make Hisn block adult destinations and supported on-screen content reliably,
keep chosen app restrictions active, and prevent ordinary weakening before a
date the person deliberately chooses. Improve everyday usability and speed
without confusing a running service with verified protection.

This plan follows the current Android source, not every claim in release prose.
The checkout declares 1.2.3/build 32. It already has DNS filtering, signed bundled
data, hostname/TLD guards, text inspection, sampled visual checks, app blocks,
daily budgets, weekday schedules, short-video restrictions, commitment locks,
guardian pairing, device-owner controls, layer-health notifications, breathing
pauses, Arabic/English and light/dark UI. Several have limited device evidence.
Improve and verify these before adding duplicates.

The untracked 1.4.0 document describes native TFLite classes absent from this
checkout. Recover and verify that candidate or implement a new prototype;
do not count it as the current engine.

Source locations below are relative to the repository. New class names are
suggestions. This is a source-based plan, not a complete runtime/security audit.

## 1. Protection dashboard and guided setup — first priority

**Improvement:** show whether DNS, text, visual checks, app rules and removal
protection are actually working, with one next action for each problem.

**How:**

1. Add a pure `ProtectionReadiness` model in `android/core`: expected state,
   observed state, reason, evidence age and applied policy revision per layer.
2. Use existing `DnsHealth`, `GuardHealth`, `InspectionHealth`, `VisualHealth`
   and `LayerWatch` inputs. Permission granted, service connected, recent scan
   completed and saved policy applied are separate facts.
3. Update `MainActivity`, `WelcomeActivity`, `Permissions` and `LayerWatchdog`
   to render the same model. Display “Not checked” for missing evidence and
   “Some checks unavailable” for partial coverage.
4. Offer a guided sequence: choose rules → enable DNS → verify DNS route →
   optionally enable screen inspection → demonstrate a harmless fixture →
   choose commitment/removal level. Each consent explains its own capability.
5. Retain the existing three-tab interface and craft styling. Improve status
   clarity before starting another visual redesign.

**Proof:** grant/revoke permissions, deny notifications, kill/rebind services,
change rules mid-check and return from Settings. No stale “working” verdict;
all repair actions open the relevant screen. Test Arabic RTL and TalkBack.

## 2. Stronger DNS protection — first priority

**Improvement:** keep blocking dependable through network changes and make the
actual bypass limits visible.

**How:**

1. Extend tests around `DnsVpnService`, `DnsPolicy`, `DnsReplyGuard`,
   `UnderlyingNetwork`, `ResolverFailover` and `DnsTcpServer` before changing
   routing. Preserve existing TLD, hostname-term, custom-rule, SafeSearch and
   DNS-answer-target checks.
2. Exercise Wi-Fi/cellular changes, airplane mode, offline recovery, captive
   portals, IPv4/IPv6, UDP truncation/TCP fallback and upstream resolver failure.
3. Bind each outstanding exchange to a network generation; discard late
   replies from a previous network. Keep work queues and retry budgets bounded.
4. Expand browser coverage checks for system DNS, built-in Secure DNS,
   encrypted-DNS providers and VPN conflicts. Blocking known resolver addresses
   cannot identify every private resolver; report that boundary.
5. Reconcile the manifest's always-on flag with the service's experimental
   DNS-only comment and selective routes. Read back actual routing/policy and
   test ordinary browsing before offering managed lockdown.
6. Decide explicitly between retaining a lightweight DNS-only product and a
   separately scoped full-traffic VPN. Full traffic requires a forwarding engine,
   IPv6/TCP/UDP handling, QUIC behavior, backpressure, per-app attribution where
   supported, energy testing and a recovery design. It is a major project, not
   adding a default route to this DNS service.

**Proof:** local canary blocks, allowed canary resolves, TCP/UDP tests pass,
ordinary apps retain connectivity and network transitions recover. Test both
managed and ordinary installations. Record gaps by browser and configuration.

**Platform reference:** Android describes always-on VPN and lockdown in its
[VPN guide](https://developer.android.com/develop/connectivity/vpn).
Do not enable lockdown on the assumption that a DNS-only tunnel carries all
ordinary traffic correctly.

## 3. Automatic signed blocklist updates — high priority

**Improvement:** new blocked destinations reach the phone without an app upgrade.

**How:**

1. Add an `AdultListUpdater` using a persistent background job and a manual
   refresh action. Keep APK-bundled data as the offline initial generation.
2. Reuse the repository's signed-manifest format and pinned trust key. Verify
   exact manifest bytes, signature, supported schema, artifact hashes, size,
   domain/term validity and coverage plausibility before installation.
3. Download into a private staging directory. Build an immutable candidate
   index off-thread; switch one active-generation pointer only after validation.
4. Persist the highest accepted generation and retain the last working data.
   Reject older offers; interrupted writes must not erase active protection.
5. Key decision caches by list and policy revision. Report installed generation,
   verification time, freshness and last update failure without storing queries.
6. Benchmark core and full-list memory before selecting a mobile tier. A
   five-million-entry list is not automatically appropriate for every phone.

**Proof:** bad signature/hash, truncated file, oversized input, stale generation,
process death during install and offline reboot retain valid enforcement.
Concurrent DNS requests see complete generations.

## 4. Faster, more accurate text inspection — high priority

**Improvement:** catch explicit content sooner while reducing benign blocks and
avoiding stalls on large feeds.

**How:**

1. Build on `TextGuardService`'s existing background scanner and generation
   handling. Measure event-to-scan, tree collection, scoring and intervention
   separately before changing limits.
2. Coalesce pending events per active window; process the newest useful work
   instead of building a backlog. Keep a maximum wait and a periodic sweep so
   constant mutations cannot starve inspection.
3. Recheck window/package/policy generation before acting. Cache only bounded
   in-memory signatures tied to those revisions; changed text must invalidate
   a previous clean verdict.
4. Improve `LabelScan`, `VisibleTextSample`, `AdultTextPolicy`, `WordMatcher`
   and `TextFolding` with shared reviewed fixtures. Preserve context for medical,
   religious, educational and recovery text; do not globally map all digits
   into letters or match arbitrary substrings.
5. Add Arabic dialect/Franco-Arabic, mixed-script and split-label fixtures.
   Target unsupported rendering honestly: accessibility cannot always read
   text embedded in pixels or inaccessible app views.

**Proof:** small/large trees, deep nodes, static screens, fast scrolling,
same-length text replacement and window changes. Report adult misses and benign
blocks separately. Increasing the node limit alone is not an accuracy result.

## 5. Faster visual checks and less exposure — high priority

**Improvement:** reduce the time from content appearing to intervention; avoid
unnecessary repeated inference and establish real detection quality.

**How:**

1. Instrument `VisualGuard` and `VisualClassifier`: capture wait, capture,
   pixel conversion, queue, model load, inference and actual intervention.
2. Recover the separately recorded native-model candidate or create a
   `VisualRuntime` interface with TFJS and native LiteRT implementations.
   Preserve a working fallback and report which backend actually ran.
3. Pin model bytes and preprocessing. Compare per-class scores and decisions
   on identical inputs before replacing the model runtime. Evaluate conversion
   parity on representative inputs including positives, not just benign controls.
4. Run conversion/inference off the main thread. Reuse bounded buffers and one
   model instance; contain timeout/crash recovery without parallel runaway work.
5. Tune `VisualCadence`, `VisualDuty`, `VisualRetry` and `VisualFrameGate` using
   full-path measurements. A faster model does not remove a long sampling wait.
6. Evaluate changed-frame caching and region crops only after measuring misses
   from small explicit regions. Exact/cropped frame matches need policy/model
   revision keys; perceptual similarity must not reuse a clean verdict across
   meaningful content changes.
7. Evaluate supported GPU/NPU acceleration and quantization as separate
   experiments. Check score drift, cold start, memory, heat and fallback.

**Proof:** real low/mid/high phones, still/moving content, split screen,
picture-in-picture, rotation, window replacement, secure windows and runtime
failure. Include appropriately obtained adult-positive and difficult benign
controls. Report unknowns separately from clean results. Never retain captures.

Android documents native inference options in its
[ML guide](https://developer.android.com/ai/custom); screenshot failures and
capabilities are described in the
[AccessibilityService reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService).
Secure/unreadable windows remain coverage gaps; do not try to bypass them.

## 6. App blocks, budgets, schedules and short-video rules — high priority

**Improvement:** keep existing restrictions consistent across restarts and make
their triggers understandable.

**How:**

1. Use one immutable `PolicyStore.Snapshot` revision for `ChosenApps`,
   `AppUsage`, `DailyAppSchedule`, `AppTimeLimit`, `ShortVideoSurfaces` and the
   enforcing accessibility service. Report when a saved revision is not applied.
2. Verify weekday/overnight behavior, midnight, daylight-saving changes,
   time-zone changes and restored usage records. Reopening Hisn must not reset
   the day's usage budget.
3. Keep distinct controls for always-blocked apps, scanned apps, daily budgets
   and schedules. Explain which rule wins when more than one applies.
4. Validate short-video adapters against real supported app versions. When an
   adapter loses confidence, report unsupported coverage instead of claiming
   the entire app's short-video area is blocked.
5. Keep the existing breathing/support flow clear: leaving the support screen
   does not grant a policy exception. Any future scheduled access sessions need
   an explicit persisted grant/budget state machine first.

**Proof:** reboot, app relaunch, background/foreground, permission loss, usage
threshold, overlapping rules and app update. Essential apps remain reachable
according to the explicitly reviewed product policy.

## 7. Commitment and removal protection — central to the end goal

**Improvement:** make chosen rules difficult to weaken until the deadline, using
real Android authority where available.

**How:**

1. Audit every `PolicyStore` mutation against `ProtectionLock`, `CommitmentClock`
   and `PolicyChangeError`: tightening, weakening, extension or authorized
   recovery. Enforce this in the mutation path, not only disabled UI controls.
2. Preserve the existing mirrored settings while testing partial writes and
   conflicting/corrupt copies. Both copies live in app data: data clearing can
   remove both. Do not call the mirror uninstall/data-clear resistance.
3. Present ordinary and device-owner setups as distinct protection levels.
   Accessibility removal guards are friction; device-owner APIs provide stronger
   OS enforcement in a correctly provisioned managed device.
4. Extend `UninstallProtectionController`, `GuardianPolicyApplier` and
   `GuardianIdentity` acceptance: apply supported uninstall/user restrictions,
   read them back and record partial failures. Existing APIs should be tested
   before expanding the restrictions list.
5. Bind guardian commands to the correct device and current policy revision;
   test signature/authentication, expiry, replay and duplicate handling through
   `GuardianPairing`/`GuardianPolicy`. Never silently change the chosen deadline.
6. Test authorized offboarding and restore managed restrictions in a documented
   order. Provisioning tests use a disposable phone; do not reset the user's
   daily device to test a plan.

**Proof:** attempts to weaken all rule types, clock rollback, reboot, damaged
storage, wrong/replayed guardian commands, uninstall and authorized expiry.
Ordinary installations remain removable; factory reset/recovery and another
device remain outside an absolute guarantee.

Use the supported authority described in
[DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)
and [managed networking guidance](https://developer.android.com/work/dpc/network-telephony).

## 8. Service reliability and battery — high priority

**Improvement:** recover within Android's permitted lifecycle and stop silently
claiming protection after a service dies.

**How:** preserve `VpnBootReceiver`, boot handlers and watchdog scheduling;
add immediate readiness reconciliation on foreground, service connection and
network change. The existing periodic watchdog is not continuous monitoring.
Separate granted accessibility permission from a bound, responsive scanner.
Exercise supported boot/foreground-service paths across target Android versions.
Show OEM battery-settings help only where evidence indicates a problem.
Do not promise that a force-stopped app can restart itself.

Tune sampling using screen state, eligible windows and measured content change.
Measure the complete process tree including the WebView renderer, not just
the app process. Test 30-minute idle, scrolling and video workloads unplugged
with recorded battery/thermal conditions and repeated baseline comparisons.

**Proof:** Samsung and Pixel hardware plus a low-memory device, screen off/on,
Doze, OS process death, force stop and reboot. State the observed behavior and
remaining gaps for each; no battery-life claim from emulator timing.

## 9. Easier rules, accessible UI and private diagnostics — next priority

**Improvement:** make setup and rule maintenance understandable without adding
more dashboard clutter.

**How:** add rule search, normalized previews, duplicate detection and explicit
locked-change explanations to `MainActivity`/`AppPicker` and pure rule helpers.
Continue using named site chips that write ordinary rules. Add a versioned,
previewed export/import of personal rules only; active commitments gate every
imported weakening. Never export browsing history, captures or secret keys.

Keep status/error messages consistent in `values` and `values-ar`; check long
Arabic text, large font, contrast, touch targets, TalkBack traversal, rotation
and process-state restoration. Use `Ui` components to fix repeated issues once.

Add opt-in diagnostic export with app/OS version, layer states, error codes and
aggregate latency. No hosts, queries, screen text, model input or partner keys.
Local progress can show voluntary pauses and aggregate interventions, with
clear retention; avoid a score claiming total protection or personal recovery.

**Proof:** first-time setup and repair tasks completed in both languages;
invalid/oversized imports rejected; locked imports cannot weaken rules; exported
diagnostics are checked for sensitive fields.

## 10. Rust decision

Keep Java/platform services for lifecycle, permissions and UI. Start with
native inference and less repeated scanning. Consider a Rust core only after
profiling identifies domain matching, normalization or parsing as material cost.

Prototype coarse batched JNI calls, preserve the shared fixtures and benchmark
against optimized Java on the same phones. Measure JNI copies, cold start,
APK size, memory and full-path p95. Adopt only with a meaningful overall gain
and a sustainable Android build/debug story. Rust cannot make inaccessible
windows readable or provide device-owner authority to an ordinary installation.

## Delivery order and release gates

| Stage | Deliverable | Exit condition |
| --- | --- | --- |
| A | Reconcile source/version evidence; dashboard model; baseline timings | One current artifact, accurate status and reproducible phone baseline |
| B | DNS transitions, rule revisions, service recovery and lock acceptance | Existing protection remains coherent through failures and restarts |
| C | Signed mobile updates and native inference comparison | Offline/corrupt-update recovery and measured performance with quality parity |
| D | Text/visual quality and app-rule improvements | Held-out misses/false blocks and user-visible latency reported by scenario |
| E | Managed-device acceptance, setup/accessibility and private diagnostics | Current signed artifact passes real managed and ordinary phone acceptance |
| F | Optional full-traffic VPN or Rust prototype | Separate design justified by measured gaps; core release stays independently usable |

For each feature: add the relevant pure-policy regression cases, run
`sh android/test-core.sh`, compile/lint the affected Android variants, run the
relevant instrumentation/live driver on a test device, and verify the exact
release artifact with `android/check-release.py` using its documented arguments.
Use `test-live-network.py`, `test-live-tcp-local.py`, `test-live-guard.py`,
`test-live-visual.py`, `test-live-schedule.py`, `test-live-pairing.py` and
`test-live-managed-status.py` where applicable, after reading their setup
requirements. Do not enable debug hooks in production packages.

A feature is done when the enforcing layer behaves correctly on the intended
device, its failure state is honest, recovery works, and the packaged bytes
match the tested source. Tests have not been rerun for this planning document.

## First implementation package

1. Resolve source/release-note mismatches, including native inference and
   always-on VPN support.
2. Implement shared readiness states and policy-revision receipts.
3. Add capture-to-intervention timing and a physical-phone baseline.
4. Fix demonstrated DNS/lifecycle failures and verify commitment mutations.
5. Compare native inference, then improve the most expensive measured scan path.

This sequence advances dependable protection and commitment enforcement before
expanding into more features or a language migration.
