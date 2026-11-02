# Hisn Android: end goal and plan to 1.0

Written 2026-10-24 against 0.5.1 (version code 13). This replaces the open-ended
"next acceptance gates" list in [the status record](ANDROID_IMPLEMENTATION_STATUS.md)
with a fixed finish line. Per-version evidence stays in the `ANDROID_0.x.md` records.

> **0.9.0 update (2026-11-02):** the in-app commitment is now the **protection
> lock**. While it runs, every saved rule is frozen in both directions, not only
> against weakening. The direct build's removal guard arms itself from the lock
> even when its switch is off, and a device-owner install keeps Android's
> uninstall block. The Play build states that it cannot block its own removal.
> Code, core tests, string parity, debug/test builds and lint pass; no physical
> device run of 0.9.0 is claimed. See [0.9.0](ANDROID_0.9.0.md).

## End goal

Hisn for Android 1.0 is a **signed, sideloadable personal self-control app**. The
owner turns on the layers they choose and gets accurate status for each one:

1. **DNS blocking**: the signed domain list over an authenticated encrypted resolver.
2. **Selected-app restrictions**: permanent blocks and daily schedules.
3. **Word and adult-text checks** in the apps the owner chooses.
4. **Opt-in image/video sampling** in the apps the owner chooses.
5. **Protection locks** that freeze every layer above, in both directions, until
   they expire.
6. **Optional managed uninstall protection** on a phone the owner has
   provisioned as device owner. This is an extra, not a requirement for the
   ordinary sideloaded build.

Every layer states its scope and shows when it has stopped working, for example
after a permission is lost, a service is killed or the network changes. It never
reports protection it does not have.

Explicitly **not** part of 1.0, and never claimed:

- Full-traffic or always-on lockdown VPN, and coverage of app-encrypted DNS or direct-IP traffic.
- Uninstall or disable prevention for an ordinary install. Device-owner
  uninstall blocking and managed lockdown are optional 0.8.1 previews with their own provisioning and
  recovery requirements; resistance to a device administrator, ADB/root or a
  factory reset remains out of scope ([tamper scope](ANDROID_TAMPER_RESISTANCE.md)).
- Pre-display blurring or detection of all adult content.
- Google Play distribution. Play's Accessibility policy needs a separate review first.

## Network decision (1.0)

Android 1.0 keeps the DNS-only VPN owner-started and start-on-boot; it does **not**
turn on always-on VPN or lockdown. A device owner's managed lockdown blocks VPN and
Private DNS configuration changes, but the tunnel itself remains the user-approved
Hisn service. `setGlobalPrivateDns` (the 1.0 plan's Option A) is deferred: it would
enforce the resolver's categories, not Hisn's signed list, and needs its own setup,
readback and release path. A full tunnel is the 2.0 project.

The consequence is honest: if the VPN stops, DNS protection is lost until it
restarts. The 0.8.x watchdog makes that visible with a local notification, and
`no_config_vpn` stops an ordinary user replacing the tunnel. The app never claims
always-on coverage.

Usage limits (time budgets) are deferred to 1.1. Daily schedules and permanent blocks
already cover the self-control case, and budgets add a counter-recovery surface that
needs its own lifecycle testing.

## Where the preview stands (0.8.1)

| Area | State | Remaining to reach 1.0 |
| --- | --- | --- |
| DNS layer (UDP + TCP53, DoT upstream, route check) | Implemented; core and Samsung route checks pass | Real Wi-Fi to mobile handover, screen lock, another VPN, resolver loss (M2) |
| App blocks, schedules, words, adult text | Implemented; fixture and synthetic-corpus evidence | Real-app coverage pass (M3) |
| Visual sampling | Implemented; benign baseline 0/12 false blocks; WebGL 121 ms median on Samsung | Adult recall, battery, live capture on 0.5.1 (M4) |
| Protection locks | Implemented; clock, boot, full-freeze and isolated-preference tests pass. Device-owner mode can additionally block ordinary uninstall while one runs, and managed lockdown can hold the API-appropriate restrictions (five on Android 8, six on Android 9, seven on Android 10–13, eight on Android 14+). | Disposable short protection lock end to end on device (M3) |
| Managed uninstall protection (0.8.1) | Device-owner only; pure state matrix, persistence guard, readback/release controller, UI and boot reconciliation are present. Optional lockdown adds `no_safe_boot`, `no_control_apps`, `no_factory_reset`, `no_add_user`, `no_config_vpn`, on Android 9+ `no_config_date_time`, on Android 10+ `disallow_config_private_dns` and, on Android 14+, `disallow_config_default_apps`; an optional strict switch adds `no_install_unknown_sources_globally` on Android 10+; a separate switch suspends blocked apps in Android while the lockdown holds. The DNS layer also has an opt-in SafeSearch CNAME rewrite. It also asks Android to keep the DNS-only VPN always on while DNS filtering is on. Saved with the uninstall block and released in reverse order. | Actual blocked uninstall and restriction refusals on a disposable provisioned device; Play/DPC policy review; an API 36 emulator device-owner run passed on 2026-11-01; no physical phone or release was provisioned |
| Layer health / honest status | Implemented model with 1,280 combinations tested. 0.8.0 adds a persistent 15-minute JobScheduler watchdog that notices a stopped DNS VPN or accessibility guard, posts one local notice and clears it on recovery. 0.8.1 shows the last run, a lost layer or notifications being off, and requests the notification permission when Keep is enabled. | Service-kill, Doze delay, notification permission and permission-revoke recovery on device (M2) |
| Release signing | **Added in 0.5.1**: optional `HISN_ANDROID_*` environment signing | Owner creates and backs up the production key (M5) |
| Signed list updates | **Decision: APK-only for 1.0.** The signed seed is bundled and pinned at build time; `SeedFreshness` warns after 90 days and the UI states a new build is the update. No runtime list download. | Runtime generation deferred to 1.1 (needs a reviewed Ed25519 verifier and a published `lists` branch); stale warning on a device |

## Milestones

Each milestone ends with a dated `ANDROID_0.x.md` record (APK hashes, what passed,
what did not) and a pushed commit. A failed acceptance step is fixed or recorded
as a limit. It is never dropped silently.

### M1: 0.5.x code completion (no device needed)

- [x] Visual runtime: WebGL first, one fresh CPU retry on load failure, failed frames
      never replayed, and stale runtime replies rejected (`VisualExecutionPolicy`).
- [x] Optional production signing config, with keystores git-ignored.
- [x] Decision on list updates: ship list changes only through signed APK releases
      for 1.0. Android has no runtime list download; the UI states it and warns
      after 90 days from the signed seed's build date. Runtime downloads wait for
      a reviewed Ed25519 verifier, a rollback floor and a published `lists` branch
      (1.1). Recorded in [0.8.1](ANDROID_0.8.1.md).
- [x] `PolicyStore` static-context lint warning: already holds the application context; suppressed with reason.
- [ ] Remaining lint warnings: plural-string candidates and two attributes that need a higher API than minSdk 26.
      0.5.3 turned the "about N hours" strings into plurals and marks `<application>` with
      `tools:targetApi="33"`. The 0.5.4 text and privacy work made the last two plural candidates
      plurals; a local `lintDebug` then reported 0 errors and 13 warnings: the two
      `text_guard_service.xml` attributes (`canTakeScreenshot`, `isAccessibilityTool`, ignored
      below API 30/31) and 11 in debug-only visual test screens (`SetTextI18n`, `ApplySharedPref`).

- [x] 0.5.2 blocking hardening: DoH canary and provider names, strict Private DNS warning,
      degraded recovery, resume after reboot, separated spellings, Arabic name false
      positives, image checks on feeds, 60-second hold, commitment reboot clock fix.
      See [0.5.2](ANDROID_0.5.2.md).
- [x] 0.5.3 honest status: setup guide ready for DNS only, app layer only or both; incomplete-scan
      and image-check failures clear only after a later success; real DNS notification state and
      channel name; real refusal reasons; no sampling with the screen off and idle model release;
      remaining-time plurals; Arabic disclosure parity and a resource parity check; App languages;
      bundled list version; Licenses and sources screen; Android CI job.
      See [0.5.3](ANDROID_0.5.3.md).
- [x] 0.5.4 reliability (code and core tests; phone checks pending): second Cloudflare Families
      address with bounded failover, connections reset only by the network in use, damaged
      settings never end a commitment, and `check-release.py` for release APKs.
      See [0.5.4 part A](ANDROID_0.5.4_RELIABILITY.md).

### 0.6.0–0.8.0 managed preview (source complete; device acceptance pending)

- Pure `UninstallProtection` state matrix, including the readback mismatch and
  the rule that a commitment blocks disabling the keep switch.
- `PolicyStore` persists the keep switch and refuses weakening it during a
  commitment.
- `UninstallProtectionController` applies/clears only when the app is device
  owner, reads the flag back, and releases ownership only after both the
  commitment and switch are off.
- Status and owner instructions are in Protection and the Guide. Boot and
  foreground entry reconcile a commitment that expired while the app was not
  running.
- 0.7.0 adds the second opt-in: while a device owner, the lockdown switch and a
  commitment or keep lock all hold, `DevicePolicyManager.addUserRestriction`
  applies `no_safe_boot`, `no_control_apps`, `no_factory_reset`,
  `no_config_vpn` and, on Android 10+, `disallow_config_private_dns`;
  `UserManager` readback drives an out-of-sync state rather than a green light.
- 0.8.0 adds the persistent 15-minute `JobScheduler` layer watchdog. It compares
  the expected accessibility/DNS layers with the live state, posts one local
  notice per loss (repeat at most every 12 hours) and clears it on recovery. It
  never restarts a service.
- Core tests (including `ManagedRestrictionsTest`), `assembleDebug`,
  `assembleDebugAndroidTest` and lint pass. The connected test phone is not
  device owner, so it must show "Not managed"; the actual refusals and release
  must be verified on a disposable provisioned device. Do not provision the
  owner's daily phone.

### M2: device lifecycle acceptance (needs the owner's phone and consent)

Run on a fresh 0.5.x debug install, using `test-live-*.py` where possible:

- [ ] VPN: Wi-Fi to mobile and back, airplane mode, screen lock overnight, reboot,
      another VPN taking over, resolver unreachable. Status must become DEGRADED or
      OFF, never stay green.
- [ ] Accessibility: force-stop, revoke, and re-grant. Health must report the loss
      and recover after re-grant.
- [ ] `CoreDeviceTestRunner` passes on the release-candidate APK.
- [ ] 0.5.3 status checks on device: DNS notification states, image-check failure persistence,
      no captures with the screen off, model release after idle minutes
      ([0.5.3 list](ANDROID_0.5.3.md#still-needs-a-phone)).

### M3: real-app coverage (needs the owner's phone)

- [ ] Browsers: Chrome, Brave, Samsung Internet, Firefox. Record DNS block, text check
      and visual check per browser, as pass / fail / not supported.
- [ ] Two feed apps chosen by the owner, using public content only, never private messages.
- [ ] Disposable 1-day protection lock (option added in 0.5.2): restart, rejected rule additions and rejected weakening.
- [ ] Optional admin step: activation and deactivation UI observed.
- [ ] Managed uninstall preview on a disposable device owner: blocked uninstall,
      readback, commitment expiry reconciliation, release and removal.

### M4: visual and performance numbers

- [x] `visual-eval/run-device.py --backend webgl` and `--backend cpu` on the same corpus
      (2026-10-24, Samsung): WebGL 121 ms median vs CPU 1,989 ms, identical verdicts.
      Default stays `auto` (WebGL first). See [0.5.1](ANDROID_0.5.1.md).
- [ ] Adult-recall corpus, reviewed and legally sourced, kept out of the repo,
      reported with denominators.
- [ ] One hour of battery and CPU with each layer on, compared with all layers off.

### M5: 1.0 release (owner actions)

- [ ] Owner creates the production keystore offline and keeps two backups. Losing it
      means existing installs can never be updated.
- [ ] Build with the `HISN_ANDROID_*` variables set, then run `check-release.py` and
      `apksigner verify --print-certs`, and publish the certificate SHA-256 next to the APK hash.
- [ ] Set `versionName '1.0.0'` and update the strings, README and `ANDROID_1.0.md` release record.
- [ ] GitHub release with the signed APK, hash, certificate fingerprint and the
      "not part of 1.0" list above, copied verbatim.

## Remaining before calling it 1.0

The 0.8.1 code covers the 1.0 feature scope. What is left is evidence and
distribution, not another layer:

| Item | State | Owner action |
| --- | --- | --- |
| Signed APK | Unsigned release artifact checked (`check-release.py`, SHA-256 recorded in [0.8.1](ANDROID_0.8.1.md)) | Create the production keystore offline, build with `HISN_ANDROID_*`, record the certificate fingerprint and APK hash |
| Device lifecycle (M2) | Pure tests only | Disposable phone: VPN handover, accessibility revoke, reboot, service kill, watch the watchdog |
| Real-app coverage (M3) | Fixtures only | Disposable phone and public content: browsers and two feed apps |
| Visual benchmark (M4) | Benign baseline and WebKit/CPU timings | Licensed adult/benign corpus with denominators; battery run |
| Managed device proof | Code, readback and tests only | Factory-reset, account-free device owner: uninstall, Safe Mode, app controls, factory reset, VPN and Private DNS refusals; release restores |
| Version 1.0.0 | 0.9.0 (25) | Bump to 1.0.0, write `ANDROID_1.0.md`, copy the known limits into the release notes |
| QR provisioning | Deferred | Needs a hosted APK URL and a reviewed QR payload; the ADB route is the supported 1.0 setup |
| Always-on / full tunnel | Explicitly out of 1.0 | Revisit with a full-tunnel forwarder in 2.0 |
| Play vs direct | Separate builds exist (`-PhisnDistribution=direct`) and `check-release.py --expect-distribution play` or `direct` refuses a mismatched artifact | Never upload the direct build to Play; keep the accessibility guard out of Play |

Until the signed artifact and the device records exist, the honest label is
**0.8.1 development preview**, not Android 1.0.

## Building a signed release

```sh
export HISN_ANDROID_KEYSTORE=/secure/path/hisn-release.jks
export HISN_ANDROID_KEYSTORE_PASSWORD=...
export HISN_ANDROID_KEY_ALIAS=hisn
export HISN_ANDROID_KEY_PASSWORD=...
./gradlew --no-daemon assembleRelease
python3 check-release.py app/build/outputs/apk/release/app-release.apk
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

`check-release.py` fails when the APK is debuggable or test-only, uses the DUMP permission,
contains a debug-only component or class, or has a package or version that differs from
`app/build.gradle`. Run it on `app-release-unsigned.apk` for unsigned builds.

Set all four variables or none of them. A partial set fails the build. With none set,
`assembleRelease` produces `app-release-unsigned.apk` as before.
