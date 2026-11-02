# Protection-lock UI captures — 2026-11-02

Build: debug `app.hisn.android` 0.9.0 on the API 36 emulator `hisn_ui`.
The emulator had previously been provisioned as Hisn's device owner, so the
uninstall card shows the device-owner state. No physical phone was used and no
owner rules were changed.

A one-day protection lock was started through the product UI:

1. Rules → **Start or extend protection lock** → **1 day** → **Start this
   protection lock**.
2. The notification permission was allowed (the watchdog notice needs it).
3. The Rules page was returned to the top; the switches and rows were checked
   through the accessibility tree as `enabled="false"`.
4. Arabic was applied with `cmd locale set-app-locales app.hisn.android
   --locales ar`.

## Captures

- `01-rules-locked.png` — Rules page with the **Rules are locked** card and
  every built-in switch, rule row and the schedule button disabled.
- `02-protection-locked.png` — Protection hero with the remaining lock time.
- `03-rules-locked-arabic.png` — the same Rules state in Arabic, RTL.

## Verified interactively

- The lock card shows the remaining time and names the guardian exception.
- All four built-in switches, the custom-rule rows and **Edit schedule** report
  `enabled="false"`; **Start or extend protection lock** stays enabled.
- The Protection hero says "Saved rules are locked for about 24 hours".
- The device-owner uninstall card says Android is refusing an ordinary
  uninstall for the lock.

## Not verified here

- TalkBack, font scaling, other OEM skins or a physical device.
- The direct build's automatic guard arming (this emulator ran the Play debug
  build; the device-owner branch was shown instead).
- Arabic on a real device. The Arabic layout was checked from the emulator
  capture only.
