# Physical direct-build guard run — 2026-11-02

Device: Samsung **SM-G990B2**, Android 16 (API 36), German Settings, Hisn app
language Arabic. Installed build: **0.8.4-direct** (debug-signed, versionCode
24). This is a daily-use phone: it has 14 accounts and a Secure Folder user, so
it is **not** device owner and was not provisioned.

## State before the test

```text
device owner: False  admin: True  uninstall blocked: False
keep installed: True  remaining: 15,255,561 ms  state: ADMIN_ONLY
accessibility services: ... app.hisn.android/app.hisn.android.TextGuardService
```

The direct guard was therefore active through **Keep Hisn installed** and the
enabled accessibility service.

## App Info attempt

```sh
adb shell 'am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:app.hisn.android'
```

The Settings page was replaced by `app.hisn.android/.ShieldActivity` showing the
Arabic protection page:

> الحماية أوقفت هذه المحاولة
> أغلق حصن شاشة إلغاء التثبيت. إبقاء حصن مثبتًا جزء من الحماية التي فعّلتها.

The Home button returned to the launcher normally.

## ADB uninstall attempt

```sh
adb shell pm uninstall app.hisn.android
# Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]
```

Hisn remained installed. The refusal came while the **legacy device admin was
active**; this device has no device owner. It is not proof that a user cannot
remove the app after deactivating the admin, using Safe Mode, root, ADB with
sufficient privilege, or a factory reset.

## What this proves

- The direct App-info/removal guard works on this physical Samsung build, not
  just in unit tests.
- An ordinary `adb uninstall` was refused while the active admin was present.
- No device-owner claim is made: the protection is still friction plus the
  legacy admin, not Android's device-owner uninstall block.

## Not tested here

- The new VPN-settings shield on a screen that actually names Hisn's VPN.
- The unrestricted-battery request and its effect.
- Deactivating the legacy admin and then uninstalling.
- Safe Mode, factory reset, root, or an OEM-specific Settings surface.
- Any device-owner path.
## Follow-up: shield latency

After the first run, the shield start delay was reduced from 250 ms to 100 ms
and the 1.5-second repeat cooldown no longer suppresses the shield. On the same
phone, a debug-only launcher opened the real App-info intent and the
`ShieldActivity` appeared reliably. Starting the shield immediately (0 ms) was
covered by the Home transition and is not used. The final build adds a
`TYPE_ACCESSIBILITY_OVERLAY` touch blocker before Back/Home; on the same phone
the App-info attempt still reached `ShieldActivity`. The overlay's own
tap-blocking was not measured independently.
