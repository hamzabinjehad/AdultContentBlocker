# Hisn Android 0.8.1 — honest list age, visible watchdog and the update decision

Development candidate, 30 October 2026. Not a Play release, not a signed
production build, and not a device-acceptance claim.

## What changed

### The list-update decision is recorded and shown

Android 1.0 does **not** download a block list at runtime. The signed list is
compiled into the APK, and a new list ships in a new signed Hisn build. The app
already said "Updates arrive only with a new Hisn version"; 0.8.1 makes the
decision explicit and visible:

- `SeedFreshness` reads the signed seed's `built_at` and computes the whole-day
  age. After **90 days** the Protection page adds a warning with the age and
  says that a newer signed build is the only update path.
- A plain date and an ISO offset timestamp are both accepted; an unparsable
  timestamp shows no warning rather than a wrong one. A future timestamp is
  clamped to zero.
- The decision avoids a second list trust path on Android. Ed25519
  verification on minSdk 26 would need a reviewed pure-Java verifier or a new
  crypto dependency, a rollback floor, a staging swap and its own failure
  matrix. One signed APK is the simpler 1.0 model. A runtime generation can be
  revisited in 1.1 with that verifier and a published `lists` branch.
- The warning is informational. The bundled list still enforces; the UI does
  not claim it is fresh or complete.

### The 1.0 network decision is recorded

Android 1.0 keeps the DNS-only VPN owner-started and start-on-boot; it does not
enable always-on or lockdown. A device owner's managed lockdown blocks VPN and
Private DNS configuration changes, but the tunnel stays the user-approved Hisn
service. `setGlobalPrivateDns` (Option A in the 1.0 plan) is deferred because it
would enforce the resolver's categories, not Hisn's signed list, and needs its
own setup/readback/release path. A full tunnel is the 2.0 project. A stopped VPN
is therefore a leak; the 0.8.x watchdog makes it visible and `no_config_vpn`
stops an ordinary user replacing it. The app never claims always-on coverage.

### The watchdog is visible, and its notification is requested

- `LayerWatchdog` stores `last_checked` on every run and exposes the last lost
  signature. Protection now shows **Background check · last ran …**, or
  **a layer is not running**, or **notifications are off**.
- Enabling **Keep Hisn installed** on Android 13+ requests the notification
  permission, because a local notification is the only closed-app signal. A
  denial is shown in the watchdog line; it is never treated as protection.
- Clearing a healthy check no longer wipes the "last ran" time, so the status
  does not look like the check never ran.
- The in-app Guide now mentions the optional managed lockdown, the 15-minute
  background check, the lack of always-on/lockdown VPN, and the APK-only list
  update path.

Version 0.8.1, versionCode 21. Settings from 0.8.0 load unchanged.

## Verification

On 30 October 2026, with Android Studio's JBR and the local Android SDK:

- `JAVA_HOME=<JBR> sh test-core.sh`: **PASS**, including `SeedFreshnessTest`
  (whole-day age, date forms, clamping, 90-day boundary), `LayerWatchTest`, the
  API-aware `ManagedRestrictionsTest` and English/Arabic string parity
  (280 strings).
- `./gradlew --no-daemon assembleDebug assembleDebugAndroidTest
  assembleRelease lintDebug`: **PASS**. `assembleDebug assembleRelease
  -PhisnDistribution=direct` also passes, so the direct build's self-protection
  code still compiles. The device runner now also checks the watchdog
  expectations and the bundled-list age read-only; it was compiled, not run.
- `python3 check-release.py --expect-distribution play
  app/build/outputs/apk/release/app-release-unsigned.apk`: **PASS**, app
  `app.hisn.android` 0.8.1 (21), not debuggable or test-only, no DUMP
  permission, no debug-only component or class.
- `assembleRelease -PhisnDistribution=direct` then
  `check-release.py --expect-distribution direct`: **PASS**, versionName
  `0.8.1-direct`. The checker now refuses a `-direct` artifact checked as Play,
  and the standard artifact was rebuilt afterwards.

**No device run is claimed.** The stale-list warning, the watchdog status line,
the permission request and a real layer loss still need a disposable or the
owner's phone.

## Release artifact (unsigned)

| Field | Value |
| --- | --- |
| Version / build | 0.8.1 / 21 |
| File | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Size | 4.6 MB |
| SHA-256 | `1e00bf2eb870f693a8e49d097835b516ca20780cea343436b1023624452813c4` |
| Debug APK SHA-256 | `2a160a593649e1e07e27697f1047bf2982f874f914ed2c857fad096f5e1ce947` |
| Direct release APK SHA-256 | `16878b73c66d0501a5735980a1dad96b58b9a2bef71dfb832fc2a172c41485b0` (versionName `0.8.1-direct`; never upload to Play) |
| Signing | **None** — this is the unsigned release check artifact, not distributable. |

The production keystore is still an owner action; without it this APK cannot
update an installed build. The release check confirms the artifact's package,
version, permissions and debug-component absence, not a device behavior.

## Limits

- The 90-day threshold is a product choice, not a freshness guarantee. A list
  can be older than its usefulness long before 90 days, and a new build can
  still be built from an old seed.
- The warning does not update anything. A person who never installs a newer
  build stays on the bundled list indefinitely; the UI says so.
- The watchdog is best-effort: 15-minute minimum, Doze/battery delay, and a
  force-stopped app stops its jobs. It never restarts a service.
- Notification permission can be denied or revoked; the status line says so,
  but no notice can be shown.
- Android 8/9 have no Private DNS restriction; the managed set stays at four
  keys there.

## Device checks

1. Install the debug build, confirm the Protection page shows the bundled list
   version/date and **Background check · not run yet**.
2. Turn on **Keep Hisn installed**; grant the notification permission. Force
   the job with `adb shell cmd jobscheduler run -f app.hisn.android 1401`; the
   line must show a recent last-run time.
3. Turn the accessibility service off and run the job again; the line must say
   a layer is not running and the notification must appear.
4. Re-enable the service and run the job; the line and notification must clear.
5. Deny the notification permission and confirm the status line says
   notifications are off.
6. Set the device date forward more than 90 days (or use a test build with an
   old `built_at`) and confirm the stale-list warning appears with the age.
