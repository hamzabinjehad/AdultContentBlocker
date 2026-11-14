# Android phone-maker screen on a Xiaomi-labelled emulator

Captured 14 November 2026 from a **disposable AOSP emulator with Xiaomi build
properties**, not from retail MIUI. The AVD (`hisn_xiaomi`, Android 16 / API 36,
`google_apis/arm64-v8a`) was booted with `-writable-system` and the five
`build.prop` partitions were edited so the guest reports:

```
ro.product.manufacturer=Xiaomi
ro.product.brand=Redmi
ro.product.model=23122PCD1G
```

The app therefore takes its Xiaomi branch for real: `Build.MANUFACTURER` is
read by `OemSupport`, not supplied by a test fake. The image has no
`com.miui.securitycenter` or `com.miui.powerkeeper`, so the MIUI settings
screens themselves are **not** present; what is verified is the classification,
the screen, the live Android readings, the fallback path and persistence.

## Captures

| File | What it shows |
| --- | --- |
| [xiaomi-phone-screen.png](xiaomi-phone-screen.png) | Settings → Keep protection running → This phone: "Xiaomi 23122PCD1G", Android 16 · API 36, "Xiaomi, Redmi or POCO", 3 to check, Battery Off, Background activity On, Autostart and Recents rows, Always-on VPN and Second space notes |
| [xiaomi-phone-screen-autostart-ticked.png](xiaomi-phone-screen-autostart-ticked.png) | After the AOSP battery dialog was allowed and the Autostart box ticked: "1 to check", Battery Unrestricted; the tick survived a force-stop and cold start (`checked="true"` in the UI dump) |

## What was exercised on this emulator

1. `PhoneSetupTest` — **5 of 5** on this AVD (the real-build test asserts
   `XIAOMI` from `ro.product`; the four Compose tests use the fake phone state).
2. The real app (not the test fake): Home showed **"Xiaomi may pause Hisn"**;
   Settings showed **"Xiaomi · 3 to check"**; the phone screen showed the Xiaomi
   copy and the Autostart row.
3. Battery row opened the standard AOSP
   `RequestIgnoreBatteryOptimizations` dialog; allowing it changed the row to
   **Unrestricted** and the count to **2 to check** without a restart.
4. Autostart → **Open** fell back to Android's app info
   (`com.android.settings/.applications.InstalledAppDetails`) because the MIUI
   security-centre component is absent. No crash, no toast claiming the MIUI
   screen opened.
5. Ticking Autostart changed the count to **1 to check**; after force-stop and
   cold start the box was still `checked="true"` and the count still 1.

## Reproduce

The AVD is disposable and not in the repository. On a machine with the API 36
Google APIs image:

```sh
# create a minimal AVD from an existing one's config, then:
emulator -avd hisn_xiaomi -writable-system -no-snapshot-load -no-window \
  -gpu swiftshader_indirect -no-audio
adb root && adb remount
# in each of /system/build.prop, /vendor/build.prop, /product/etc/build.prop,
# /system_ext/etc/build.prop, /vendor/odm/etc/build.prop, set
#   ro.product.<partition>.manufacturer=Xiaomi
#   ro.product.<partition>.brand=Redmi
#   ro.product.<partition>.model=23122PCD1G
adb reboot
adb shell getprop ro.product.manufacturer   # Xiaomi
```

Changing the identity after first boot confused Google Play services and the
Bluetooth stack in one run; a `-wipe-data` start with only these three fields
was stable. The emulator is **not** a substitute for a Xiaomi phone: MIUI's
powerkeeper, security centre and autostart screens are absent.
