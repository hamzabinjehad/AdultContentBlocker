# Android policy receipts: 1.5.3 integration map

Prepared 8 November 2026. Read-only inspection of the separate
`android-1-2-3-responsive` worktree. No merge, rebase, cherry-pick or commit
was performed.

## Target

- Worktree: `AdultContentBlocker-android-1.5.3`
- Commit: `9270044729e19e661ed99fc75a3cdede323b2a7e`
- Version: Android 1.5.3, build 41
- Subject: “Android 1.5.3: go back on a matched person”

## Source of this package

The readiness and receipt work is uncommitted on the `mdm-finish` checkout
(base `8400320`). It adds persisted policy versions, layer identities,
receipts, evidence age and focused tests. The 1.5.3 line does not contain it.

## Port order

1. Core classes and tests: `PolicyVersion`, `PolicyIdentity`, `PolicyReceipt`,
   `DnsCheck`, `EvidenceAge`, plus the `InspectionHealth`/`VisualHealth`
   receipt storage and their tests.
2. `SavedSettings` revision field and `withRevision`, `SettingsIntegrity`
   seal compatibility, `SavedSettingsTest`/`SettingsIntegrityTest` updates.
3. `PolicyStore`: persisted revision, `Snapshot.revision`, revision only on an
   effective rule change, copy/legacy handling, instrumentation revision test.
4. `PolicyEvidence` and the `VISUAL_ASSET_REVISION` build field.
5. `DnsVpnService`: service session, policy capture at route-check start,
   `DnsCheck` decision, `ConnectionCheck.receipt`.
6. `TextGuardService`: service session, scan policy/session capture,
   completion validation, app-decision vs text-scan receipt kinds.
7. `VisualGuard`: pending policy/session, discard on change, receipt on every
   sampled path.
8. `MainActivity`, `Ui`, `Durations` and strings: layer identities, tile
   detail line, layer-specific age limits.

## File map

| File | 1.5.3 state | Expected integration |
| --- | --- | --- |
| `android/core/.../PolicyVersion.java` | absent | add cleanly |
| `android/core/.../PolicyIdentity.java` | absent | add cleanly |
| `android/core/.../PolicyReceipt.java` | absent | add cleanly |
| `android/core/.../DnsCheck.java` | absent | add cleanly |
| `android/core/.../EvidenceAge.java` | absent | add cleanly |
| `android/core/.../SavedSettings.java` | unchanged | add revision field/helper |
| `android/core/.../SettingsIntegrity.java` | unchanged | no change; seal already covers new key |
| `android/core/.../InspectionHealth.java` | unchanged | add receipt storage |
| `android/core/.../VisualHealth.java` | adds `PROTECTED` failure state | merge: keep `PROTECTED` in `failure()` and add receipt fields; both SAMPLED paths need receipts |
| `android/app/.../PolicyStore.java` | unchanged | add revision/snapshot changes cleanly |
| `android/app/.../PolicyEvidence.java` | absent | add cleanly |
| `android/app/.../DnsVpnService.java` | unchanged | add session/receipt/DnsCheck cleanly |
| `android/app/.../TextGuardService.java` | major scanner/person changes around scan submission and `scanFinished` | manual port: new parameters and session/identity capture; 1.5.3 shifts these regions by roughly 40 lines |
| `android/app/.../VisualGuard.java` | major cover/person changes; two `health.report(State.SAMPLED)` paths | manual port: capture pending policy/session at frame start; validate and stamp a receipt at both sampled paths |
| `android/app/.../MainActivity.java` | person-response rows and edited settings/status hooks | manual port: add readiness helpers before `refreshStatus`, compute statuses before the hero, add tile detail; keep 1.5.3 person UI |
| `android/app/.../Ui.java` | unchanged at this commit | add `LayerTile.detail` cleanly |
| `android/app/.../Durations.java` | unchanged | add evidence formatter |
| `android/app/build.gradle` | version 41 and visual asset changes | add `VISUAL_ASSET_REVISION` near seed fields; resolve version block |
| `android/app/src/main/res/values/strings.xml` | 30 lines of 1.5.3 additions | add the new evidence keys; no key collision expected |
| `android/app/src/main/res/values-ar/strings.xml` | 30 lines of 1.5.3 additions | same, with Arabic plural categories |
| `android/app/src/androidTest/.../CoreDeviceTestRunner.java` | base version | add revision and commitment-mutation checks |

## Known semantic differences

- 1.5.3 `VisualHealth` treats `PROTECTED` as a failure; the receipt port must
  not clear that state or move it ahead of failure precedence.
- 1.5.3 has a second visual decision path for the people cover. Every path
  that reports `SAMPLED` needs a receipt, not only the adult classifier path.
- 1.5.3’s `TextGuardService` has extra scan branches. The scan identity/session
  must be captured where the worker is submitted and checked in
  `scanFinished` before either recording evidence or intervening.
- 1.5.3 has no `ProtectionReadiness` or `PolicyEvidence`, so its dashboard
  keeps the older “running means on” tile logic until this package is ported.

## Verification after port

- `sh android/test-core.sh`
- `:app:compileDebugJavaWithJavac`, `:app:assembleDebug`,
  `:app:assembleDebugAndroidTest`, `:app:lintDebug`
- Instrumentation on a disposable emulator (not the owner’s phone, the 1.4.0
  emulator installation or the active benchmark)
- Arabic/RTL, large text and TalkBack pass on the dashboard
