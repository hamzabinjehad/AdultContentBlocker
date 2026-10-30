# Hisn Android 0.8.0 — Private DNS lock and a layer-loss watchdog

> Superseded by [0.8.1](ANDROID_0.8.1.md), which adds the list-update decision,
> the bundled-list age warning and the visible watchdog status. This record
> stays as the 0.8.0 evidence.

Development candidate, 30 October 2026. Not a Play release, not a signed
production build, and not a claim that the connected owner's phone is locked.
No device-owner appointment, factory reset or layer-loss device run was made on
the owner's phone.

## What changed

### Private DNS is now part of managed lockdown on Android 10+

0.7.0 applied four device-owner restrictions. 0.8.0 adds the fifth Android key,
`disallow_config_private_dns`, on Android 10+ (API 29), where the setting exists:

| Restriction | Android key | Applies on |
| --- | --- | --- |
| Safe Mode | `no_safe_boot` | Android 8+ (minSdk 26) |
| App controls | `no_apps_control` | Android 8+ |
| Settings factory reset | `no_factory_reset` | Android 8+ |
| VPN configuration | `no_config_vpn` | Android 8+ |
| Private DNS | `disallow_config_private_dns` | Android 10+ (API 29) |

The pure policy now receives the device API level, so Android 8/9 are asked for
four keys and Android 10+ for five. The status still compares Android's own
readback with the exact expected set; one missing or extra key is
**out of sync**, never a green light. Release order is the reverse of apply
order, so Private DNS and VPN are cleared first and Safe Mode last.

The debug exporter and `test-live-managed-status.py --expect-lockdown` now
include the Private DNS key only when the device reports API 29 or later.

### A background watchdog notices a stopped layer

The app window is a control panel; the DNS VPN and the accessibility guard do
the enforcement. Either can stop while Hisn is closed. A persistent
`JobScheduler` job now checks every 15 minutes (Android's minimum):

- **Expected layers** come from the owner's saved choices: the direct build's
  accessibility guard while **Keep Hisn installed** is on, and DNS while the
  owner left it on.
- **Running layers** come from the live process state (`TextGuardService.ready`,
  `DnsVpnService.state == RUNNING/DEGRADED`).
- A lost layer raises one local notification that says which layer stopped and
  opens Hisn. The same loss repeats at most once every twelve hours; a changed
  loss alerts at once; recovery clears the notice and the saved state.
- The job is scheduled only while at least one layer is expected, survives a
  reboot, and is refreshed on app open, DNS start/stop, boot and keep-switch
  changes. It never restarts a service by itself, never reads screen text and
  never sends anything to a server.
- If no notification permission is granted on Android 13+, the app cannot show
  the notice; the in-app live status is still honest.

The pure decisions live in
[`LayerWatch`](../android/core/src/main/java/app/hisn/core/LayerWatch.java);
the Android half is
[`LayerWatchdog`](../android/app/src/main/java/app/hisn/android/LayerWatchdog.java)
and `LayerWatchdogJobService`.

Version 0.8.0, versionCode 20. Settings from 0.7.0 load unchanged.

## Verification

On 30 October 2026, with Android Studio's JBR and the local Android SDK:

- `JAVA_HOME=<JBR> sh test-core.sh`: **PASS**. Includes `LayerWatchTest`
  (expected/running sets, lost layers, stable signature, notification cadence),
  the updated `ManagedRestrictionsTest` (API-29 Private DNS, five-key order,
  exact readback) and English/Arabic string parity (275 strings).
- `./gradlew --no-daemon assembleDebug assembleDebugAndroidTest assembleRelease
  lintDebug`: **PASS**.
- `python3 test-live-managed-status.py` compiles and is API-aware; the live run
  still needs a provisioned device.

**No device acceptance is claimed.** The actual Private DNS refusal, the
watchdog notification after a real service loss, and the recovery path must be
shown on a disposable device. The connected Samsung is not Hisn's device owner.

## Limits

- The periodic interval is Google's 15-minute minimum. Doze, app-standby and
  OEM battery policy can delay a run; the notice is not an instant alarm.
- The watchdog can only report what the restarted process sees. If the process
  is killed and the job starts a fresh one, a stopped DNS VPN and an
  accessibility service that has not reconnected both read as lost. It does not
  restart either service.
- Android has no supported API that keeps an accessibility service on. The
  watchdog makes the loss visible; it does not prevent it.
- Private DNS is the fifth restriction only on Android 10+. On Android 8/9 the
  setting does not exist and only four keys are applied.
- The job is best-effort. A device that disables JobScheduler, force-stops
  Hisn, or clears its data can silence the notice. ADB, root, recovery and
  device-owner removal still bypass everything.
- A notification is not a guarantee of attention. The app's live status remains
  the source of truth when it is open.

## Disposable-device checks

On a factory-reset, account-free test phone with the debug build:

1. Turn on **Keep Hisn installed**, confirm the accessibility guard is on, then
   turn the accessibility service off. Force the job without waiting:
   `adb shell cmd jobscheduler run -f app.hisn.android 1401`. The layer-loss
   notification must appear and the app must say the guard is not running.
2. Re-enable the service and run the job again. The notification must clear.
3. Start DNS, then stop the VPN in Android settings. Run the job; the DNS
   notice must appear.
4. As device owner on Android 10+, turn on managed lockdown and read the five
   keys back with `test-live-managed-status.py --expect-lockdown`. Try to change
   Private DNS; it must be refused.
5. Repeat on an Android 8/9 device if available: only four keys are expected,
   and the script's expected set must say so.

Record the result in a dated device record. A failure is fixed or written down
as a limit, never dropped.
