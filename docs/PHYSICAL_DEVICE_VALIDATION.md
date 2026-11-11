# Physical-device validation runbook

Use this only on a **disposable phone you own**, ideally factory-reset with no
accounts. Device-owner provisioning cannot be undone with a normal uninstall;
the reliable reset is a factory reset. Never provision a daily phone.

## 1. Prepare

- Android 6.0+ (API 23+); Android 7.0+ for always-on DNS and package
  suspension; Android 10+ for strict mode and Private DNS; Android 14+ for
  default-app restrictions and image checks.
- USB debugging on, cable connected, RSA prompt accepted.
- No accounts, no secondary users, setup wizard finished.
- Back up anything you care about; the test ends with a factory reset.

```sh
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell dumpsys account | grep -c "Accounts: 0"
```

## 2. Install and provision

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
python3 script/guardian_provision.py --serial SERIAL --apk \
    android/app/build/outputs/apk/debug/app-debug.apk --apply --yes
adb shell dumpsys device_policy | grep -E "Device Owner|Device Owner Type"
```

Record the model, API level, build fingerprint and whether provisioning passed.

## 3. Enable managed mode

In Hisn: **Rules → Keep Hisn installed**, **Protection → Managed lockdown**
and **Strict mode**, then **Rules → Also suspend blocked apps**. Block at least
one harmless app and one browser.

```sh
python3 android/test-live-managed-status.py --serial SERIAL \
    --expect-managed --expect-lockdown --expect-strict
```

Expected: device owner, uninstall blocked, all nine restrictions held,
suspended set matches, no `reason=` failure.

## 4. Refusals

| Test | Expected |
| --- | --- |
| `adb shell pm uninstall app.hisn.android` | `DELETE_FAILED_DEVICE_POLICY_MANAGER` |
| App info → Force stop for a blocked app | “Blocked by work policy” |
| Date & time | “Enabled by admin” |
| VPN settings | “Blocked by work policy” |
| Users | Add user/guest “Controlled by admin” |
| Power menu → Safe Mode | Blocked or absent |
| Settings → Reset/erase | Blocked |
| Private DNS | Blocked or fixed |
| Default apps | Blocked |
| New browser install | Strict mode refuses unknown sources; Play route documented |

Also test the **Keep only this** browser action, the **SafeSearch** switch with
a real DNS query, and the always-on DNS tunnel after reboot.

## 5. Pairing and policy

In **Guide → Guardian pairing**, paste a laptop offer, compare the SAS, copy the
response back, and apply a signed policy. Record the device ID and SAS. Use
`script/guardian_console.py` or `script/guardian_protocol.py`.

## 6. Release and cleanup

Turn off **Keep Hisn installed**, **Managed lockdown**, **Strict mode** and
suspension; use the release action. Verify:

```sh
adb shell dumpsys device_policy | grep "Device Owner Type"
adb shell settings get global always_on_vpn_app
adb shell dumpsys package PACKAGE | grep "suspended="
adb shell pm uninstall app.hisn.android
```

Expected: no device owner, `null` VPN, `suspended=false`, ordinary uninstall
succeeds. Then factory-reset the phone.

## Evidence to record

- Date, phone model, Android build, APK version and signing key.
- Each expected result above with pass/fail and any error text.
- Reboot and always-on behavior.
- Pairing SAS match and policy readback.
- Any OEM-specific Settings wording.
- What could not be tested.

If a step fails, stop and keep the log; do not claim the physical gate passed.
