# Android: finish-and-improve plan

Date: 12 November 2026. Status: plan. Source: three read-only audits of the
working tree (UX/UI, code reliability, release state) on the 1.6.0 / 43
candidate. Nothing here was run on a phone. It builds on
[the end-project plan](ANDROID_END_PROJECT_PLAN_2026-11-08.md) and
[the feature plan](ANDROID_FEATURE_IMPROVEMENT_PLAN_2026-11-08.md); where they
disagree, this file is newer.

## Where the app really is

- **Version:** 1.6.0 / 43, a development candidate. The docs disagree on whether
  a signed 1.6.0 direct APK was installed on the owner's Galaxy (CHANGELOG says
  yes; RELEASE_STATE, `versions.json` and the native-shell note say no).
- **Two UIs ship.** The Compose shell (`ShellActivity`, `nativeshell/`) is the
  launcher. The classic `MainActivity` (4,073 lines) still holds the real
  editors, but nothing in the shell opens it any more.
- **The launcher is not in git.** `ShellActivity.kt`, `nativeshell/`, the
  personal layer and the new icons are untracked or uncommitted (44 tracked
  files changed, about 5,400 new Kotlin lines).
- **The engine is ahead of the interface.** DNS, text, app and image layers,
  signed list updates, lock and guardian pairing work in code with ~80 tested
  core classes. The front door misreports several of them.

## Phase 0 — Make the tree safe (hours)

1. Commit the working tree in three commits: native shell, personal layer,
   icons. Until then one `git clean` removes the launcher.
2. Reconcile the docs on the signed 1.6.0 build and the phone state; fix the
   stale "Compose is debug-only" comments in `build.gradle` and
   `gradle.properties`, and the native-shell note's "rows open the classic
   editors".

## Phase 1 — An honest front door (P0)

The shell must never show something that is not true.

| # | Problem | Change |
| --- | --- | --- |
| 1 | `LockScreen` shows "started (prototype)" and starts no lock | Start the real lock through `PolicyStore`, with the classic confirmation |
| 2 | `PermissionsScreen` shows hardcoded On/Off | Read `Permissions.*` live; each row deep-links to its system screen |
| 3 | `DiagnosticsScreen` shows invented numbers | Port `showDiagnosticsDialog` data, or hide the row |
| 4 | `WordTesterScreen` matches a 5-word sample list | Call the real `AdultTextPolicy` / saved word rules |
| 5 | Info text says "this build does not filter traffic", "UI prototype" | Replace with the real disclosure strings |
| 6 | Settings rows are static ("Not active", "Notifications off", version) | Bind to `PolicyStore`, `Permissions`, `BuildConfig` |
| 7 | "Check" is a 900 ms delay then "passed" | Run a real check (DNS health + layer state) or remove |
| 8 | "Fix gaps" fires every grant at once and says "re-armed" first | Sequential checklist; each step verified before the next |
| 9 | Attention card always says "DNS stopped" | Report the actual missing layer and cause |
| 10 | Shell DNS switch skips the `dns_consent` disclosure; no accessibility disclosure | Reuse the Welcome disclosures before the system prompt |
| 11 | Every failed write toasts "Rules are locked" | Distinguish lock, validation and storage errors |
| 12 | Shell is English only; `ArabicProtectionCopy` is never selected | Move shell copy to `strings.xml` / `values-ar`; mirror chevrons |
| 13 | Unreachable from the shell: guardian pairing, uninstall protection, site check, import/export, list update, language, personal | Add rows that open the existing screens until each is ported |

## Phase 2 — A blocker that stays on (P0/P1)

1. **VPN recovery.** `DnsVpnService` returns `START_NOT_STICKY`, stops itself on
   a reader error and is never restarted. Add sticky start, bounded restart with
   backoff, a watchdog restart when `VpnService.prepare()` is null, and a
   `MY_PACKAGE_REPLACED` receiver. The manifest says the watchdog "never
   restarts a service" on purpose, so this is a decision to revisit, not a typo.
2. **Commitment clock on API 23.** The boot marker falls back to `/proc/stat`
   `btime`, which moves with the wall clock; a time change may look like a
   reboot and shorten a lock. Inferred from code, not device-tested. Persist an
   own boot marker.
3. **Battery.** The tunnel reader wakes every 250 ms and refreshes the notice
   each pass; the shell polls 12 store snapshots every 1.5 s on the main
   thread. Block on the next timer; one snapshot per tick, lifecycle-aware.
4. **IPv6 TCP DNS** is unhandled; unlisted DoH endpoints pass. Add the v6 TCP
   path and report browser Secure DNS coverage honestly.
5. **Memory.** The 236k-domain set lives on the heap behind `largeHeap`. Move
   to a packed or streamed seed before claiming Android 6 phones.
6. **After-update guard loss.** Android disables the accessibility service on
   update; show a one-tap re-enable prompt instead of a silent 0 of 4.

## Phase 3 — First run and daily use (P1)

1. Onboarding in Compose with the shell theme: "Step n of 6", required
   (accessibility, DNS) versus optional (usage, overlay, notifications,
   battery), each with one sentence of why.
2. Locked state: a read-only banner with remaining time and disabled controls,
   not a toast.
3. One UI per surface: notifications and the shield page open the shell;
   framework `AlertDialog`s become Material 3 sheets.
4. Remove duplication: Protection repeats Rules; "Start a lock" appears twice.
5. Accessibility pass: TalkBack labels (24 icons have none), 2.0× font,
   48 dp targets (ledger chips are 34 dp), a persistent status area.
6. Gray screen needs an adb grant; move it under Advanced with instructions.
7. Port the remaining editors, then delete `MainActivity` and `Ui.java`.

## Phase 4 — Features that help a person stay (P2)

Local only, no data leaves the phone.

1. **Urge moment.** The block screen offers a 60-second breathing pause, the
   saved reason, and one chosen alternative action before any override.
2. **Honest progress.** Keep "Day N · N pauses"; add a weekly local summary of
   blocks by hour so the user sees their risk times.
3. **Risk-hour mode.** One switch that tightens rules during the user's chosen
   hours (late night), built on the existing schedule.
4. **Accountability without accounts.** Finish guardian pairing in the shell; a
   lock change needs the paired phone.
5. **Rules quality.** Search, duplicate detection, import preview.

## Phase 5 — Ship (P0 for release)

1. Turn on R8 and resource shrinking with keep rules; the release APK is about
   40 MB. Drop `material-icons-extended` for the icons actually used.
2. Signed direct 1.6.0 / 43 from a clean commit; `check-release.py`; record
   hashes and certificate.
3. Phone acceptance matrix: reboot, update, force stop, Doze, Wi-Fi/cellular,
   airplane mode, captive portal, IPv4/IPv6, Private DNS, another VPN.
4. Visual benchmark with reviewed positive and hard benign sets.
5. CI: run the instrumentation runner, add a lint baseline, add tests for
   `Json`, `UdpDnsPacket`, `UdpDnsTransport`, `WordRuleList`, the VPN lifecycle
   and the shell state mapper.
6. Play decision: accessibility declaration review, direct versus Play split.

## Owner decisions needed

- Was the signed 1.6.0 build installed on the Galaxy?
- May the watchdog restart the VPN (Phase 2.1)? It was designed not to.
- Is there a disposable phone for device-owner and Android 6 acceptance?
- Direct only, Play only, or both?

## Order of work

Phase 0 → Phase 1 → Phase 2.1–2.3 → Phase 5.1–5.3 gives a releasable 1.6.0.
Phases 3 and 4 are 1.7.0.
