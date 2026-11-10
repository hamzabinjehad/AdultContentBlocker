# Android package 2: current-policy proof and failure recovery

Status: implementation brief for DeepSeek, 8 November 2026.

## Outcome

Finish the readiness work: Hisn must not show a layer as working because an
earlier policy, network or service instance passed a check. Show when evidence
was obtained, and prove that lost services and interrupted updates recover
without silently weakening a commitment.

## Starting facts

- Main checkout is `mdm-finish` with uncommitted package-1 work. Preserve it.
- `ProtectionReadiness` exists; the dashboard still passes unknown revisions.
- `PolicyStore.Snapshot` does not yet supply applied-policy receipts.
- DNS, inspection and visual health now record completion times.
- `InspectionHealth.COMPLETE` also includes an app/schedule decision or nothing
  needing inspection. It is not necessarily evidence of completed text scanning.
- The separate `android-1-2-3-responsive` worktree is now at Android 1.5.3,
  build 41 (`9270044` at this inspection). Native inference already exists on
  that line. Do not rebuild it or disturb that worktree/benchmark.
- The first package compiled and passed recorded local checks; visual/device
  acceptance remains outstanding. Do not describe those old checks as newly run.

## Task 1 — Real policy identities and applied receipts

Work in `PolicyStore`, `ProtectionReadiness`, `DnsVpnService`,
`TextGuardService`, `InspectionHealth`, `VisualGuard` and `VisualHealth`.

1. Give each immutable enforcement snapshot a validated policy identity.
   Prefer a persisted revision committed with the settings it identifies;
   handle legacy records and mirrored-copy recovery explicitly. Do not use
   wall-clock time, object identity or a counter that silently resets on launch.
2. Advance it only after a successful effective policy change. Ordinary reads,
   countdown checkpoints and saving identical rules must not create new policy
   versions. Failed persistence must not publish a supposedly saved revision.
3. Include all relevant inputs: DNS also needs SafeSearch/list generation;
   text needs vocabulary generation; visuals need model/config identity.
   Scope identities to affected layers where practical, so unrelated changes
   do not needlessly invalidate every layer.
4. Distinguish adopting a snapshot from completing a check using it. A receipt
   identifies the policy, service session, relevant network/window generation,
   check kind, result and monotonic completion time. No hosts or screen text.
5. Capture identity at work submission and validate it at completion and before
   intervention. Discard an old asynchronous result; it cannot certify or act
   for a newer policy/window/network. Queue bounded work for current state.
6. Wire actual saved/applied identities into the dashboard. Unknown applied
   state must not certify a known saved revision. Reuse the existing states and
   repair actions rather than introducing contradictory status logic.

Acceptance: save A, start work, save B, complete A. A neither makes B green nor
acts on B's screen. B becomes working only after the appropriate current check.
Test identical saves, failed writes, copy recovery, restart and a changed list.

## Task 2 — Honest evidence age and lifecycle

1. Render localized “Checked just now”, “Checked %1$d minutes ago” and an
   appropriate stale state, using plurals and existing resource conventions.
2. Keep evidence timestamps ephemeral and scoped to the service session. Never
   accept a prior boot's monotonic timestamp as evidence in the new process.
3. Use the same clock for each producer/consumer. Audit the existing DNS
   elapsed-time and visual uptime clocks; define how deep sleep affects freshness
   rather than subtracting times from different clock domains.
4. Reset/invalidate relevant evidence on service recreation, permission loss,
   policy change and DNS network change. Preserve failure precedence even if a
   prior success is recent. Choose freshness limits by layer and explain them.
5. Distinguish an app-rule decision from a text inspection and a completed visual
   sample. “Checked” must name what was actually checked; it never means all
   current content or every app has been examined.

Acceptance: future/unknown times, expiry, sleep/wake and service reconnect never
produce misleading ages or stale working states. No per-second UI polling.

## Task 3 — DNS transition and lifecycle regression cases

Extend existing `UnderlyingNetwork`, `ResolverFailover`, DNS health/probe and
live-network tests; avoid replacing proven transport code without a failing case.

- Wi-Fi → mobile → Wi-Fi; network lost/restored; resolver timeout and failover.
- Late reply and late route-check success from a previous network generation.
- Local block and allowed resolution over supported UDP/TCP paths.
- Service restart, reconnect, revoked VPN consent and offline launch.
- Explicit unsupported encrypted-DNS/IPv6 cases and ordinary browsing checks.

Acceptance: stale callbacks cannot restore healthy evidence; queues remain
bounded; supported connectivity recovers; unknown coverage stays unknown.
Keep the DNS-only architecture and do not enable lockdown/default-route changes
as part of this package.

## Task 4 — Commitment mutation regression cases

Audit and test every settings mutation in `PolicyStore`: domains, words,
selected apps, visual/text switches, short-video rules, schedules, time limits,
SafeSearch and managed/guardian choices. Reuse the existing commitment contract.

Acceptance: weakening is refused at the mutation boundary during a commitment;
documented tightening and authorized recovery behave correctly; rejected/failed
changes preserve settings and revision. Test clock changes, reboot and damaged
state. Do not add new permissions or change the commitment/recovery contract.

## Task 5 — Validate and prepare integration

- Use Android Studio's bundled JDK via `JAVA_HOME`; default Java lacks `javac`.
- Run focused new regression tests, `sh android/test-core.sh`, string/notice
  checks, Java compilation, `assembleDebug` and `lintDebug`.
- For runtime proof use an available disposable emulator explicitly by serial.
  Do not replace the owner's phone app, the 1.4.0 emulator installation or the
  active benchmark. If no emulator is available, report device checks pending.
- Check dashboard English/Arabic, RTL, large text and TalkBack. Add new resource
  keys to every complete locale present when implementation starts; coordinate
  with the separate 15-language work instead of overwriting its files.
- Inspect the current 1.5.3 line read-only and prepare an integration file map.
  Record its actual commit again before handoff. Do not automatically merge,
  rebase, cherry-pick, commit or change another worktree in this package.

Completion report: files changed, new behavior, exact checks passed, remaining
device gaps and expected integration conflicts. Keep the report short. No raw
browsing diagnostics, speculative native-engine rewrite or unrelated UI redesign.
