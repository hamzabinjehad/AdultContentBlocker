# Android implementation and remaining release gates

Updated 2026-11-03. This describes the development work in `android/`, not a
production release or proof that the connected owner's phone is protected.
Mac, iOS/iPadOS and browser code were not changed in this Android implementation.

## 1.1.0 UI/UX pass (2026-11-03)

Version 1.1.0 (versionCode 27) adds a first-run welcome, a voluntary **Take a
pause** action on Protection and today's usage in the daily-limit editor. It
adds no enforcement layer and relaxes no rule.

- `WelcomeActivity` shows three skippable pages only when the app has no
  configured rules and the welcome has not been seen. Finishing opens Rules;
  skipping opens Protection. English and Arabic RTL.
- `RestrictionActivity` now has a voluntary mode reached from Protection. It
  uses the same pause card and tally, changes no rule, and **Back to Hisn**
  returns immediately. The service-launched block path is unchanged.
- The daily-limit editor shows each selected app's Android usage today with a
  progress bar, or an explicit Usage-access message. `AppUsage` is the same
  source used by enforcement.
- ScreenZen's Android onboarding was observed read-only as design input; no
  competitor code, assets or copy was used.

Verified on 3 November 2026 on the API 36 emulator: `test-core.sh` (including
`AppTimeLimitTest`), English/Arabic string parity (480 strings, 15 plurals),
`assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`, `CoreDeviceTestRunner`,
the welcome in both languages with Skip/Set up and page-by-page back, the
voluntary pause and the usage row. The owner's Samsung updated from 1.0.0 to
1.1.0 in place (`firstInstallTime` unchanged) and rendered the Arabic welcome.
The signed Play/direct APKs and AAB pass `check-release.py` and `apksigner`; the
direct APK is the published artifact. No full physical-phone enforcement,
TalkBack/large-font audit or Play approval is claimed.

## 1.0.0 signed release boundary (2026-11-03)

Hisn Android 1.0.0 (versionCode 26) is the first signed direct-sideload release.
It adds opt-in daily app time limits on top of the 0.9.0 protection lock.

- `AppTimeLimit` stores a package-to-minutes rule (5–1,440 minutes, at most 200
  apps) inside the sealed settings file. It is frozen by a running protection
  lock in both directions; the guardian tighten-only path preserves saved
  limits and cannot remove or raise one.
- `AppUsage` reads Android's on-device `UsageStatsManager` foreground total for
  the current local day. The separate Android **Usage access** special
  permission is required. Without it nothing is enforced, and the Rules card and
  Protection attention list say so.
- `TextGuardService` checks a limited app when it comes on screen, while it stays
  on screen, and in the once-a-minute sweep. The same pause screen names the
  daily-limit reason. Settings, dialers, launchers, installers and Hisn remain
  excluded.
- The Play and direct signed candidates pass `check-release.py`; `apksigner`
  verifies both with the new release certificate. The direct APK is published on
  GitHub Releases. The Play AAB is built but not uploaded; Play policy review is
  still pending.

Verified on 3 November 2026: `test-core.sh` (including `AppTimeLimitTest` and
the sealed `SavedSettings` round trip), English/Arabic string parity (460
strings, 15 plurals), `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`,
the signed release builds, `check-release.py` for both distributions,
`apksigner verify`, and `CoreDeviceTestRunner` on the API 36 emulator including
time-limit persistence and protection-lock refusal. The Rules editor, Usage
access state, app picker and summary were read back through the accessibility
tree in English and in Arabic (RTL); with the Usage access grant revoked the
summary read "Usage access needed" and the Apps tile read "Needs attention".
A live 5-minute enforcement pass closed the fixture app and showed the
daily-limit pause screen.

**No physical-device run of 1.0.0 is claimed.** OEM Usage-access screens, real
clock/time-zone changes, Doze/battery behaviour and physical enforcement
latency remain device gates. The 0.9.0 and earlier verification boundaries below
still apply.

## 0.9.0 protection-lock verification boundary (2026-11-02)

0.9.0 turns the in-app commitment into the **protection lock** and freezes every
saved rule while it runs. `ProtectionLock` holds the pure decision; `PolicyStore`
refuses additions, removals and edits from every in-app caller, not only
weakening changes. Starting a longer lock is allowed. The guardian policy path
(`updateFromGuardian`) can still add restrictions and is tighten-only.

The direct build's accessibility guard now arms itself from a running lock even
when the keep-installed switch is off. `TextGuardService`, the boot-shield copy
and `LayerWatchdog` use the same effective state. A device-owner install applies
`setUninstallBlocked` for the lock as before. The Play build cannot block its own
removal and now adds an explicit attention item while a lock runs. The Rules page
shows the remaining lock time and disables every editor, switch and the schedule
button.

Verified on 2 November 2026: `ProtectionLockTest`, the full Android core suite,
English/Arabic string parity (440 strings, 14 plurals), `assembleDebug`,
`assembleDebugAndroidTest` and `lintDebug` pass. The release/direct builds and
`check-release.py` are recorded in [0.9.0](ANDROID_0.9.0.md).

**No device run is claimed for 0.9.0.** The freeze, automatic guard arming, boot
shield and uninstall states are code and unit-test evidence only. The guard still
depends on accessibility being on; boot-key Safe Mode, ADB, root and a factory
reset remain outside it, and the Play build remains removable by design.

## End-state objective

The fixed 1.0 finish line and milestone checklist are in
[ANDROID_1.0_PLAN.md](ANDROID_1.0_PLAN.md). That plan takes precedence over
the open-ended gates listed below.

Offer voluntary layered protection: verified domain blocking, selected-app and
word restrictions, appropriate visual filtering, focus commitments and accurate
status. Each layer must state its scope and recover from service/permission
loss without displaying false protection. Absolute detection or resistance to
a device owner's reset/recovery controls is not an achievable release claim.

## Implemented in the development preview (latest: 0.9.0)

| Layer | Code now present | Verification boundary |
| --- | --- | --- |
| List authenticity | Signature verification at build; APK-pinned hashes checked at load; 159,762 domain rules plus bounded adult-term assets | Actual domain/term seeds loaded in core tests and Samsung instrumentation |
| List updates | Bundled only; the signed APK is the update path. The UI says so and `SeedFreshness` warns after 90 days with the signed build date. There is no runtime list download. | `SeedFreshnessTest` covers age/date/clamping; no network path exists. The 90-day warning on a device is pending. |
| Network | IPv4 UDP plus bounded TCP53 at the virtual DNS endpoint, shared bounded workers, authenticated TLS upstream, local blocks and alias checks | Core UDP/TLS/TCP tests and Samsung UDP/TCP route checks pass; large UDP-to-TCP fallback is core-only evidence; physical transition reliability remains pending |
| Route verification | Explicit owner-confirmed virtual-DNS check: blocked `.invalid` then `example.com` over UDP and reused TCP; four queries on success; session-bound results | Loopback validation/cancellation and complete Samsung checks pass; this does not verify all traffic |
| Browser diagnostic (debug only) | Fixed `www.example.org` additive deny, memory-only 120-second lease; separate release no-op; no saved-rule changes | [0.4.3 core/release-hook tests](ANDROID_0.4.3.md) pass; Samsung policy count rose during private navigation and owner confirmed DNS error in Brave; this verifies only that tested path |
| Network changes | Underlying-network callbacks invalidate pooled/in-flight TLS and old route results; old-network errors are fenced; no automatic public probe or VPN restart | Core generation/buffered-response tests and Samsung simulated-change/recheck pass; real Wi-Fi/mobile handover pending |
| Packet diagnostics | Unsupported/malformed/old-network counts, accepted TCP queries, prepared replies and UDP TC signals; no payload retention | UDP/TCP parser and mutation tests pass; counts are diagnostics, not proof that all apps use the virtual endpoint |
| Saved rules | Domain and literal word lists, selected blocked/scanned apps and separate adult-text opt-in; private persistence | Samsung persistence, Unicode rejection/reload and opt-in checks pass |
| App/word intervention | Consent-based accessibility service restricted to chosen apps; home/restriction screen; input/password skips | Existing ordinary/editable/password/long-text/wide-tree/rapid-reopen fixtures and 2026-10-20 normal-content fixtures pass; real-app coverage pending |
| Built-in adult-text checks | Opt-in English/Arabic policy; 4,462 canonical rules including reviewed local protective/recovery context; bounded compact-spelling recovery, complete explicit media labels, shared Arabic/Persian/Urdu folds and no-space phrase aliases; custom rules independent | Core actual-seed tests pass, including Persian/Urdu, `nudephotos`, `p0rnhub` and the `سكسونيا` benign boundary; 52 synthetic examples: 23/25 intended blocks, 0/27 benign false blocks, two known challenge misses; not a general accuracy estimate. See 0.4.6 release evidence and the 0.6.0 record |
| Setup guidance | Shared next-step message/action model separates rules, permission, binding, policy loading/error, limited scans and DNS/check states | All 1,280 enum combinations tested; READY requires every selected setup prerequisite, not universal protection |
| Commitment | Backend rejects every rule change during the lock (0.9.0), not only weaker settings; elapsed/boot clock; new empty commitments rejected | Pure clock and protection-lock tests, isolated Samsung preference tests from earlier versions pass; the 0.9.0 freeze is core-test and build evidence only |
| Daily selected-app schedule | Optional every-day local-time window, separate app set, overnight support, static-screen periodic enforcement and commitment-preserving coverage | Exhaustive/randomized core tests, isolated Samsung persistence/commitment checks and real local-time boundary test pass; clock/timezone/accessibility bypasses remain |
| Removal friction | Optional legacy admin receiver with no administrative policies | Compiles; latest activation/deactivation test pending |
| Managed uninstall protection | Device-owner only. Pure state matrix, `PolicyStore` keep switch with commitment guard, `DevicePolicyManager.setUninstallBlocked` readback/repair, in-app setup/release UI and boot reconciliation. 0.7.0 adds an opt-in managed lockdown (`no_safe_boot`, `no_control_apps`, `no_factory_reset`, `no_config_vpn`), saved in the same write, read back from `UserManager` and released in reverse order. 0.8.0 adds `disallow_config_private_dns` on API 29+. | Core `UninstallProtection`/`ManagedRestrictions`/`SavedSettings` tests, debug/test builds and lint pass. The Samsung is not device owner, so it reports "Not managed"; an actual blocked uninstall and the restrictions, expiry reconciliation and release still need a disposable provisioned device. |
| Background layer watchdog | A persistent 15-minute `JobScheduler` job compares expected layers (direct-build accessibility while keep is on, DNS while left on) with the live state; a lost layer gets one local notice, repeated at most every 12 hours, cleared on recovery. It never restarts a service. Protection shows the last run, a lost layer or notifications being off; enabling Keep on Android 13+ requests the notification permission. | Pure `LayerWatch` tests cover expected/running sets, lost detection, signature and notification cadence; the app builds and lints. A real service loss, Doze delay and notification permission are device checks. |
| Visual filtering | Opt-in Android 14+ selected-window image/video sampling using the bundled local classifier; generic Home/restriction intervention, explicit capture consent and separate health | Actual local model, policy and stale-frame tests plus nine Samsung live fixture checks pass. A small benign baseline had 0/12 false-blocked views over 36 calls, with 1,984 ms median / 2,290 ms p95 classifier roundtrip. Sources/layouts are limited and correlated; this is not population accuracy. Adult recall, real-app/video and battery acceptance remain pending; no pre-display claim. See [0.5.0](ANDROID_0.5.0.md) and [benchmark](ANDROID_VISUAL_BENCHMARK_2026-10-22.md) |

The 0.3.0 debug APK was installed and opened on the Samsung on 2026-10-19.
The final device runner passed seed loading, Arabic/boundary matching, local DNS
blocking, Unicode persistence and rejection, bounded long-text sampling and
commitment persistence/restriction checks. Earlier runs exposed and led to
fixes for instrumentation context/storage handling. Tests use a generated
preference file separate from `hisn_policy` and clear only that test file.
They do not enable VPN/accessibility/admin or start a real user commitment.
Complete end-to-end VPN routing was still unverified at that stage. After owner accessibility activation,
controlled live intervention fixtures passed; see `ANDROID_LIVE_GUARD_2026-10-19.md`.
The external driver avoids the process restart caused by instrumentation and
restores its temporary fixture additions without changing owner restrictions.

REA inspection of BlockerHero produced independent long-text, traversal-budget,
Unicode persistence and dashboard improvements. See
`REA_BLOCKERHERO_2026-10-19.md` for evidence and limitations.

Follow-up 0.3.1 was installed on the Samsung and passed the full external live
runner, including periodic selected-app checking and stopping after leaving that
app. It adds a tested layer-health model, scroll events and a selected-root probe
on binding. See `ANDROID_0.3.1.md`. That report's empty-rules/DNS-off state is a
historical observation, not the current 0.4.0 test state. Permission loss and
service-stop recovery still need broader tests and can require owner action.

## 0.4.0 verification boundary (2026-10-20)

Core checks and Samsung instrumentation passed. Controlled normal-content,
English/Arabic adult-promotion and benign medical-context fixtures also passed.
The instrumentation uses isolated preferences and does not start a real owner
commitment. The optional adult mode is off by default when upgrading; its
verified loader can fail visibly without deleting or suppressing custom rules.
Built-in negatives apply within local leaf context, never as a whole-screen
exemption, and do not override the user's chosen literal rules.

Initial live VPN route probes passed the synthetic local NXDOMAIN block but
failed the external-resolver step for `example.com`. Investigation
found that socket protection ran before native socket allocation. The transport
now binds locally before protection and connection, with a regression test.
The phone then reported no active underlying internet network. After connectivity
returned, one check failed with resolver EOF, then the next complete check passed
both local NXDOMAIN and the authenticated `example.com` response. Repeat checks
exposed stale pooled TLS connections: the latest build retries one reused
EOF/reset against the same authenticated endpoint, but not fresh errors,
timeouts, certificate failures or bad frames. Regression tests pass; three
consecutive live two-step checks passed on the final installed APK. The session
retains its historical DEGRADED state: a pass does not erase other failures.
Transition reliability and normal browsing still need acceptance tests. This is
route verification, not a complete-protection claim. The check is deliberately explicit
and uses the virtual route rather than a protected diagnostic socket. It does
not prove all apps use Hisn DNS even when it passes.

A small separate read-only text corpus yielded 9/16 adult-text hits and 1/18 benign
false positives. These are fixture-specific counts, not an accuracy estimate or
a representative benchmark. The misses/false positive require further work.
No full-traffic or always-on VPN, and no undeletable installation for an
ordinary install, is implemented. Device-owner uninstall blocking and the
managed lockdown are opt-in 0.8.1 previews; they do not survive a factory
reset, device-owner removal, ADB/root or an administrator. The current
deliverable remains a tested development preview, not the requested universal
end-state protection.

## 0.4.2 verification boundary (2026-10-20)

The installed version 0.4.2 adds a bounded IPv4 TCP53 DNS endpoint, not a general
TCP or full-traffic tunnel. It supports 16 flows, 4,096-byte DNS frames, split
and pipelined requests, reused connections, MSS/window-limited responses and
bounded retry/expiry. Oversized-for-UDP validated replies produce a TC response.
Core packet/engine/fallback tests, debug/test builds, lint and release-manifest
checks pass. Earlier instrumentation is historical; no fresh instrumentation
run was made because it restarts the active accessibility process.

Samsung native checks passed UDP and reused-TCP local blocks plus authenticated
`example.com` replies, simulated network invalidation/recheck and old-error
fencing. Two pipelined local TCP blocks also passed without internet access.
Controlled normal/editable/password, custom-word/app and English/Arabic adult
fixtures passed; fixture changes were restored without removing owner rules.

Brave displayed an ordinary page, search results and an IETF documentation page
with Hisn running. This short smoke test does not prove all browser requests use
Hisn or evaluate adult-content accuracy. A stale result observed afterward was
explicitly rechecked: UDP and TCP passed again. Historical errors/unsupported
packets correctly leave the session DEGRADED; a route pass does not erase them.
Physical Wi-Fi/mobile handover and large native UDP-to-TCP fallback remain
unverified. See [the complete record](ANDROID_0.4.2.md) for the APK identity.

## 0.6.0 managed-uninstall verification boundary (2026-10-28)

The 0.6.0 source adds device-owner-only uninstall blocking. On 2026-10-28 the
current `0.6.0-direct` debug build (version code 15, from the `android-0.6.0`
branch before the 0.5.5 merge; release/1.0 builds 0.6.0 as version code 18) was
installed on the Samsung SM-G990B2 (Android 16); the pulled device APK matched
the build output SHA-256
`f674d7b375ef34b448382226f424b361b4da0934400234d998166416553552b7`.
The test APK `62de24512cdd81c42d907565ff041c0d004d7b91e7f052a5a9866ff4d5f23021`
was installed and `CoreDeviceTestRunner` passed, including the uninstall-guard
checks. The read-only `test-live-managed-status.py` also passed and reported
`deviceOwner=false`, `uninstallBlocked=false`, keep switch on, state
`UNAVAILABLE`: the ordinary install is honestly not managed.

Hisn is still **not** this phone's device owner; Samsung Secure Folder owns a
managed profile for user 150. No factory reset, account removal, device-owner
appointment or administrative policy was applied. An actual blocked uninstall,
commitment-expiry reconciliation and release path still require a disposable,
freshly provisioned device owner. Do not provision the owner's daily phone and
do not describe the ordinary install as undeletable. See
[0.6.0](ANDROID_0.6.0.md) and [tamper scope](ANDROID_TAMPER_RESISTANCE.md).

## 0.7.0 managed-lockdown verification boundary (2026-10-29) *Historical*

0.7.0 adds the second opt-in: `no_safe_boot`, `no_control_apps`,
`no_factory_reset` and `no_config_vpn`, applied only while a device owner, the
lockdown switch and a commitment or keep lock all hold. The code builds, lints
and its pure matrix passes:

- `ManagedRestrictionsTest` checks the opt-in matrix, exact set equality,
  missing/extra detection, apply and reverse-release order, the four Android
  keys, and the combined uninstall state (a missing or extra restriction is
  `OUT_OF_SYNC`, never `PROTECTED`/`READY`).
- `SavedSettingsTest` checks the `managed_lockdown` round trip and that settings
  saved before 0.7.0 load with it off.
- `PolicyStore.updateManagedChoice` saves both choices in one write and refuses
  to loosen either during a commitment.
- `JAVA_HOME=<JBR> sh test-core.sh`, `assembleDebug`,
  `assembleDebugAndroidTest` and `lintDebug` pass.

No managed phone was provisioned. The four real refusals, the readback on a
device-owner build, the expiry repair and the release path are **not** claimed.
`test-live-managed-status.py --expect-lockdown` is the read-only check to run on
a disposable provisioned device; it does not apply or clear policy. See
[0.7.0](ANDROID_0.7.0.md).

## 0.8.1 list age and visible watchdog verification boundary (2026-10-30)

0.8.1 records the Android 1.0 list-update decision: no runtime list download,
the signed APK is the update. `SeedFreshness` reads the signed seed's
`built_at`, computes a clamped whole-day age and drives a warning after 90 days.
The UI already stated that updates arrive only with a new version; the warning
makes a stale build visible rather than implied.

The watchdog now stores and shows `last_checked`, the last lost signature and
whether notifications are enabled. Enabling **Keep Hisn installed** on Android
13+ requests `POST_NOTIFICATIONS`; a denial is shown in the watchdog line, not
treated as protection. The in-app Guide was updated for the managed lockdown,
the watchdog, the lack of always-on/lockdown VPN and the APK-only list path.

Verified on 30 October 2026: `SeedFreshnessTest`, `LayerWatchTest`, the
API-aware `ManagedRestrictionsTest`, English/Arabic string parity (280 strings),
`assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` and `lintDebug`
pass.

**No device run is claimed.** The stale-list warning, the watchdog status line,
the permission request and a real layer loss need a phone. See
[0.8.1](ANDROID_0.8.1.md).

## 0.8.0 Private DNS and watchdog verification boundary (2026-10-30)

0.8.0 extends the managed lockdown on Android 10+ with the separate
`disallow_config_private_dns` key. The pure policy takes the device API level,
so Android 8/9 expect four keys and Android 10+ expect five; the debug exporter
and `test-live-managed-status.py --expect-lockdown` use the same rule. The
existing exact-readback, out-of-sync and reverse-release rules apply unchanged.

The layer watchdog is a persistent periodic `JobScheduler` job (15-minute
minimum). It reports a stopped DNS VPN or a disconnected direct-build
accessibility guard with one local notification, repeats at most every twelve
hours, and clears when the layer is back. It never restarts a service.

Verified on 30 October 2026: `LayerWatchTest`, the updated
`ManagedRestrictionsTest`, English/Arabic string parity (275 strings),
`assembleDebug`, `assembleDebugAndroidTest` and `lintDebug` pass.

**No device run is claimed.** The real Private DNS refusal, a genuine service
loss and recovery, Doze/battery delay and Android 13+ notification permission
need a disposable device. See [0.8.0](ANDROID_0.8.0.md).

## Next acceptance gates

The [0.4.1 follow-up](ANDROID_0.4.1.md) adds monitored connection invalidation;
[0.4.2](ANDROID_0.4.2.md) adds bounded TCP DNS. Neither completes real handover,
all-protocol coverage, visual filtering, or device-owner provisioning and
uninstall-block acceptance on a disposable managed device.

1. Completed: install both development APKs and run `CoreDeviceTestRunner`.
   Repeat these checks for future builds; keep test preferences isolated from
   real user settings and commitments.
2. Completed: diagnose socket allocation/protection and reused-EOF failures;
   obtain three consecutive complete route checks on 2026-10-20. This does not
   clear other session errors or complete the broader network gate. Check the
   previous VPN state before activation. After owner consent, expand blocked and
   benign DNS fixtures and actual tunnel counters; verify ordinary browsing,
   another VPN, resolver failure, service loss, screen lock, Wi-Fi/mobile
   transitions and IPv6 connectivity. Do not call the limited route full VPN
   protection or enable lockdown for it.
3. After accessibility consent, use a harmless fixture app for blocked-app and
   custom-word tests. Test password/editable descendants, duplicated labels,
   dynamic feeds, split nodes, secure surfaces, unavailable roots, process death
   and normal allowed browsing. Extend the small adult-text corpus, measure
   misses/false positives and test per-leaf context/custom-rule independence in
   real supported layouts. Do not use private messages as a corpus.
4. Exercise a disposable short commitment in the test APK, including restart,
   stronger-rule updates and rejected removals. Never start a real long
   commitment on the owner's phone as a routine test.
5. If the owner activates the optional admin step, observe its normal uninstall
   and deactivation UI without disabling an existing unrelated blocker. Report
   deactivation as available; do not present legacy admin as device-owner mode.
6. Expand the bounded DNS transport/record handling and verify native large-reply
   fallback. Implement an explicit signed list-update strategy. Address
   app-encrypted DNS and direct-IP coverage before
   considering a full traffic/always-on design. Measure CPU, memory, queue drops,
   end-to-end latency, resolver reachability and battery on the phone.
7. Add a separately consented visual layer with reviewed models, bounded capture
   and inference, clear capture-loss status, privacy and model evaluation.
   Android secure surfaces and capture-session stop/restart behavior remain
   coverage limits. Benchmark misses and false positives with denominators.
8. Expand the tested daily app schedule to separately scoped usage limits and
   additional scheduling options; add an explicit distribution/signing/release
   process. Managed-device enrollment is a separate product mode, not an
   automatic upgrade or a reason to reset the owner's phone.

The [0.4.4 record](ANDROID_0.4.4.md) covers the daily schedule and its real static
screen boundary test. Its initial live DNS check failed while Android reported
no active default network. After owner reconnection, the same 0.4.4 APK passed
both UDP and reused-TCP local-block/allowed-query checks; historical session
degradation remains visible. This is not a full-traffic or radio-handover test.
The added commitment reminder before existing in-app VPN
settings links is dismissible and is not system-settings interception. See
[Android tamper-resistance scope](ANDROID_TAMPER_RESISTANCE.md) for personal-mode
limits and the separate managed-device prerequisite. No management enrollment
or settings trap was applied to the Samsung.

References and build instructions are in `android/README.md`; earlier static
competitor observations remain in `ANDROID_RESEARCH_PLAN.md` and are not copied
competitor implementations or evidence of Hisn's runtime quality.
