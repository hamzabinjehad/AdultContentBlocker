# Hisn Android 0.7.0 — optional managed lockdown

Development candidate, 29 October 2026. Not a Play release, not a signed
production build, and not a claim that the connected owner's phone is locked.
No device-owner appointment, factory reset, account removal or managed
restriction was applied to that phone. The ordinary install remains removable.

## What changed

0.6.0 added a device-owner uninstall block behind an explicit keep switch or an
active commitment. 0.7.0 adds a second, separate opt-in on top of it: **managed
lockdown**.

- While Hisn is this Android user's device owner, managed lockdown is on, and a
  commitment or **Keep Hisn installed** is holding the app, Hisn applies and
  reads back four Android user restrictions:

  | Restriction | Android key | What it blocks |
  | --- | --- | --- |
  | Safe Mode | `no_safe_boot` | Safe Mode from the power menu and the hardware-key prompt |
  | App controls | `no_apps_control` | Uninstall, disable, clear-data and force-stop for **every** app |
  | Factory reset | `no_factory_reset` | The Settings erase flow (recovery mode still works) |
  | VPN configuration | `no_config_vpn` | Adding, removing or changing VPN and private-DNS configuration |

- **App controls is deliberately broad.** It changes what the person can do to
  every app, not only Hisn. The switch is off by default, appears only for a
  device owner, and the dialog states the breadth before applying it.
- Both managed choices are saved in **one write** (`updateManagedChoice`), so a
  failed save cannot leave the uninstall block and the restrictions describing
  different states. Tightening is allowed at any time; loosening either choice
  waits for the commitment to end.
- The status screen reads the platform's own `UserManager.getUserRestrictions()`
  and shows **Managed lockdown · ON / Off / idle / out of sync**. A missing or
  extra restriction is out of sync and the periodic repair runs, never a green
  light over a partial set.
- Release order is the reverse of apply order (VPN, factory reset, app
  controls, Safe Mode), then the uninstall block, then device ownership. The
  app offers **Remove managed mode** only when no commitment and no keep lock
  remain.
- The debug-only `ManagedStatusActivity` now exports `managedLockdown`,
  `restrictions`, `desiredRestrictions` and `restrictionsMatch` for the
  read-only live checker.
- Version 0.7.0, versionCode 19. Settings saved by 0.6.0 load with managed
  lockdown off.

## Verification

Run on 29 October 2026 with Android Studio's JBR (Java 21) and the local
Android SDK:

- `JAVA_HOME=<JBR> sh test-core.sh`: **PASS**, including the new
  `ManagedRestrictionsTest` (opt-in matrix, exact readback, apply/release
  order, combined protection state) and the updated settings and uninstall
  tests. English/Arabic string parity and list notices pass.
- `./gradlew --no-daemon assembleDebug assembleDebugAndroidTest lintDebug`:
  **PASS**. No lint error fails the build.
- `python3 test-live-managed-status.py --help` and the script compile; the
  live run still needs a provisioned device.

**No device acceptance is claimed.** The connected Samsung is not Hisn's device
owner. The actual refusal of Safe Mode, app uninstall, the Settings erase and
VPN changes, the readback and the release path must be proven on a disposable,
factory-reset, account-free phone. Do not provision the owner's daily phone.

## Limits and what this does not do

- It is available only while Hisn is the device owner. An ordinary install, a
  legacy device admin and a profile owner cannot apply these restrictions.
- `no_apps_control` stops the person from managing **all** apps, including
  clearing a malfunctioning app's data. That is the price of the strongest
  supported setting; it is not a default.
- It does not set always-on VPN or global Private DNS. The DNS-only `VpnService`
  is not made always-on; the network decision in the 1.0 plan is still open.
- `no_config_vpn` does not cover every OEM's renamed network screen, a
  per-app VPN that was already configured, or an OS-level private-DNS path that
  the platform does not route through the restriction.
- Recovery-mode factory reset, ADB, root, a platform vulnerability, removing
  device ownership from a management console, or a different device still
  removes or bypasses everything. The app says so on the same screen.
- The accessibility guard remains a separate layer and is not made
  unremovable by these restrictions. Android has no supported API that stops
  the user turning off an accessibility service.
- No Play distribution. Play's Accessibility policy does not allow the direct
  build's uninstall-screen guard, and the direct and managed builds stay out of
  Play.

## Disposable-device acceptance checklist

On a factory-reset, account-free test phone only:

1. Install the debug APK and run
   `adb shell dpm set-device-owner app.hisn.android/.CommitmentAdminReceiver`.
2. Save a rule, start a 1-day commitment, turn on **Keep Hisn installed** and
   **Managed lockdown**, then apply.
3. `python3 test-live-managed-status.py --serial SERIAL --expect-lockdown`
   must show all four restrictions held and `restrictionsMatch: true`.
4. Try uninstall, Safe Mode, Settings erase and a VPN change; each must be
   refused. Record the exact screens.
5. Let the commitment expire (or use the 1-day plan), turn both switches off,
   choose **Remove managed mode**, then verify uninstall works and the
   restrictions are gone.
6. Record the result in a dated device record. A failure is fixed or written
   down as a limit, never dropped.

The exact owner command and warnings are in the app and in
[the Android README](../android/README.md).
