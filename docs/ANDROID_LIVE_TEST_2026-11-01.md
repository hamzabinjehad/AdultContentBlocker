# Android managed-mode emulator run — 2026-11-01

This records what an Android **emulator** proved for the managed-device
features added in the current tree. It is not a physical-device or release
record, and it does not claim the restrictions that were not exercised.

## Environment

- macOS arm64, Android emulator `hisn_ui`, API 36 (Android 16).
- Debug build `app.hisn.android` 0.8.4, versionCode 24.
- The emulator was already provisioned as Hisn's device owner
  (`app.hisn.android/.CommitmentAdminReceiver`) with an existing commitment and
  **Keep Hisn installed** on.
- A debug-only, tighten-only harness (`ManagedTestSetupActivity`, absent from
  release builds) enabled managed lockdown, strict mode and blocked-app
  suspension, and added `com.android.chrome` and
  `com.google.android.apps.photos` to the block list.
- The read-only `test-live-managed-status.py` was the only status reader.

## Result

```text
PASS: device owner with uninstall blocked and managed lockdown held
held: disallow_config_default_apps, disallow_config_private_dns, no_add_user,
      no_config_date_time, no_config_vpn, no_control_apps, no_factory_reset,
      no_install_unknown_sources_globally, no_safe_boot
suspended apps: com.android.chrome, com.google.android.apps.photos
always-on DNS VPN: current=True desired=True match=True
managed match: True
```

The run used `--expect-managed --expect-lockdown --expect-strict`.

## Bug found and fixed

Before this run the app used the key **`no_apps_control`**. Android's documented
key is **`no_control_apps`** (`UserManager.DISALLOW_APPS_CONTROL`). The first
attempt therefore reported `ok=false reason=readback`: every other key was
held, but the all-app control restriction was silently missing. The key was
corrected in `ManagedRestrictions` and all status/tests/docs; the second run
held all nine keys and passed.

This is exactly the class of failure the pure string tests could not catch.

## Release and cleanup run

A second, freshly wiped AVD was provisioned with `dpm set-device-owner --user 0`
and driven without a commitment:

```text
PASS ok=true reason=protected lockdown=true strict=true suspend=true keep=true
held: nine keys, match=True
suspended apps: com.android.chrome, match=True
RELEASE result=null owner=false keep=false lockdown=false suspend=false
```

After release:

- `dumpsys package com.android.chrome` shows `suspended=false`.
- `dumpsys device_policy` shows `Device Owner Type: -1`.
- `settings get global always_on_vpn_app` is `null`.
- `adb shell pm uninstall app.hisn.android` returned **`Success`**.

The release path therefore resumed the suspended browser, cleared the
always-on request, cleared the restrictions, released device ownership and
allowed an ordinary uninstall. This does not cover a commitment being active
(release is intentionally refused then) or a physical phone.

## What was exercised

- Device-owner appointment and readback.
- Nine managed-lockdown restrictions, including strict-mode
  `no_install_unknown_sources_globally` on API 36.
- Blocked-app suspension: both blocked packages reported
  `suspended=true` in `dumpsys package`.
- Uninstall refusal: `adb shell pm uninstall app.hisn.android` returned
  `Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]`.
- Always-on DNS request readback (`current=True desired=True match=True`); the
  tunnel itself was not exercised.

## Settings UI refusals

The same emulator state was used to open Settings and inspect the actual screens:

| Screen | Observed |
| --- | --- |
| Chrome → App info | no **Uninstall** action; tapping **Force stop** showed **“Blocked by work policy”** |
| Date & time | **Automatic date and time — Enabled by admin** |
| VPN | **“Blocked by work policy”** when opening VPN settings |
| Users | **Add user — Controlled by admin**, **Add guest — Controlled by admin** |

Commands:

```sh
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d 'package:com.android.chrome'
adb shell input tap 873 1102   # Force stop
adb shell am start -a android.settings.DATE_SETTINGS
adb shell am start -a android.settings.VPN_SETTINGS
adb shell am start -a android.settings.USER_SETTINGS
adb shell uiautomator dump /sdcard/window.xml
```

`android.settings.PRIVATE_DNS_SETTINGS` did not resolve on this image, so the
Private DNS screen was not inspected. Safe Mode, the erase flow, default-app
settings and the power menu were not driven. Those remain readback-only.

## What was not exercised

- A physical phone.
- Safe Mode, the erase flow, Private DNS and default-app screens; the app
  controls, clock, VPN and user screens were inspected as above.
- Suspension cleanup after unblocking and release **was** driven on the fresh run;
  app-data clear was not (the device policy refused it).
- The always-on DNS tunnel, its authentication, and SafeSearch CNAME answers.
- Factory reset, recovery mode, ADB restriction bypass beyond the observed
  package-uninstall case.

## Residual confirmed

`adb shell pm uninstall --user 0 com.google.android.apps.photos` **succeeded**
even while `no_control_apps` was held. That is the documented ADB/shell residual,
not a failure hidden by the app: a user with an authorized debugging connection
can still remove an app. The device-owner app's own uninstall was refused.

## Commands used

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n app.hisn.android/.ManagedTestSetupActivity \
    --ez lockdown true --ez strict true --ez suspend true --ez keep true \
    --es block com.android.chrome,com.google.android.apps.photos
python3 android/test-live-managed-status.py --serial emulator-5554 \
    --expect-managed --expect-lockdown --expect-strict
adb shell pm uninstall app.hisn.android
```
