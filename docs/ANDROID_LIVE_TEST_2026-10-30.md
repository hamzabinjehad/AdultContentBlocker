# Hisn Android live protection test — 2026-10-30

Disposable **emulator only**. The physical Samsung was inspected read-only and
was **not** changed. This is not a physical-device, Play-review or
factory-reset test, and it does not claim complete device protection.

## Device and build

- Emulator `emulator-5554`: Android 16 (API 36), `sdk_gphone64_arm64`,
  userdebug, 1080 × 2400, no accounts, no other device owner.
- Hisn **0.8.2 (versionCode 22)**, Play debug build (`assembleDebug`), seed v5.
- Android test fixture APK installed for the repository's own live harness;
  normal apps were also used.
- The emulator's accessibility service was enabled with the Android secure
  setting. It was intermittently disabled by the emulator while setup screens
  were open; it was re-enabled immediately before each guard test. This is a
  test-environment observation, not a claim about an ordinary install.

## What passed

### 1. Text blocking in a normal app — PASS

- Added the harmless literal rule `hisnfixtureword`, selected **Chrome** for
  inspection, and served a local page containing that word.
- Chrome opened the page; Hisn returned it to the restriction screen:
  *“Your selected text protection detected restricted content in this app.”*
- Live counters after the intervention:
  `CONNECTED true`, `WORD_INTERVENTIONS 1`, `SCANS 24`.
- Evidence: [word-block screenshot](../design/android-live-test-2026-10-30/01-word-block-chrome.png).

The repository's packaged fixture (`test-live-guard.py --case word`) did
**not** reproduce this on the API 36 emulator: its test-only activity produced
`no-readable-leaves`. The same guard blocked the normal Chrome page above, so
the failure is recorded as a harness/test-window limitation on this emulator,
not as a pass for the fixture.

### 2. Whole-app blocking — PASS

- Selected Chrome in **Apps to block entirely** and launched it again.
- Android returned to the launcher; Hisn's live status recorded
  `OUTCOME app-intervention` and incremented `COOLDOWN_ENFORCEMENTS`.
- The shared restriction UI is shown in the
  [word-block screenshot](../design/android-live-test-2026-10-30/01-word-block-chrome.png);
  the app-block run itself returned to the launcher before a stable capture.
- Settings, dialers and launchers remain excluded by design.

### 3. DNS local TCP blocks — PASS

`test-live-tcp-local.py` passed:

> two pipelined synthetic local blocks returned over one native Android TCP connection

This used the running Hisn DNS tunnel, a synthetic blocked name and no public
resolver or adult site.

### 4. DNS route check — PASS

`test-live-network.py --confirm-example-com` passed:

> UDP and reused TCP each blocked the synthetic name and resolved
> example.com through Hisn

At the same moment the app still reported `VPN DEGRADED` because recent
requests had failed or were unsupported. The route-check line itself said
**“UDP and TCP DNS checks passed · Local blocks and resolver responses
observed.”** That difference is intentional: a point-in-time route check does
not erase recent failures.
Evidence: [route-check screenshot](../design/android-live-test-2026-10-30/06-dns-route-check.png).

The tunnel was started through the app's consent dialog and Android's VPN
confirmation. The public test query was the fixed `example.com`; no adult site
was requested.

## Time-limited uninstall lock — PASS on the emulator

This is the only part that makes the app genuinely refuse an ordinary
uninstall, and it needs device ownership.

1. Provisioned the emulator as Hisn's device owner:
   `adb shell dpm set-device-owner app.hisn.android/.CommitmentAdminReceiver`
   → **Success**, one `DeviceOwner` for user 0.
2. Selected one app (Chrome) so the commitment had a saved restriction, then
   started a **1-day commitment** through Rules → Commitment period.
3. Hisn applied Android's `setUninstallBlocked` policy.
4. Read-only managed status:

```
device owner: True  admin: True  uninstall blocked: True
keep installed: False  remaining: 86394193 ms  state: PROTECTED  may release: False
managed lockdown: False  held: []  desired: []  match: True
PASS: device owner with the uninstall guard applied and read back
```

5. Android Settings → App info → Hisn shows **Uninstall** and **Force stop**
   greyed out. Evidence:
   [Settings screenshot](../design/android-live-test-2026-10-30/03-settings-uninstall-blocked.png).
6. Hisn's Protection page says **“Uninstall protection · ON for the active
   commitment (24 hours). Android is refusing ordinary uninstall.”**
   Evidence: [app status screenshot](../design/android-live-test-2026-10-30/04-uninstall-on-commitment.png)
   and [commitment screenshot](../design/android-live-test-2026-10-30/05-commitment-active.png).

The lock ends with the 1-day commitment. The emulator remains a disposable test
device.

### What this does not stop

Device ownership blocks **ordinary uninstall** while the commitment runs. It
does not stop a factory reset, removing device ownership through ADB/another
administrator, root, or boot-key Safe Mode recovery. ADB uninstall was not run
because it would remove the test target; the project's own threat model already
records ADB/root as a bypass.

## Physical Samsung — inspected only, not changed

`RZCTA0XN60K`, SM-G990B2, Android 16:

- Hisn **0.6.0-direct** (versionCode 15) installed; accessibility service
  already enabled.
- **14 accounts** on the owner user.
- Knox Secure Folder owns a managed profile; Hisn is **not** a device owner.
- No settings, rules, app updates or commitment were applied to this phone.

A true Android uninstall block on that phone would require removing the accounts
and provisioning a fresh, account-free device as Hisn's device owner — in
practice, a factory reset. The direct build can add its accessibility
uninstall/disable-screen guard and a commitment as a deterrent, and that guard
is bypassable by Safe Mode at boot, ADB/root or a factory reset. That choice and
its duration need the owner's explicit confirmation.

After the read-only inspection, the owner chose to **leave the Samsung
unchanged**. No phone lock, commitment, app update or setting change was
applied. The one-day lock remains on the disposable emulator only.

## Not tested

- Image/video sampling, the daily schedule boundary, adult-text live corpus and
  short-video surfaces.
- Real adult domains, all-app traffic coverage, VPN handover, reboot and
  long-running DNS.
- ADB/root/factory-reset bypasses.
- Physical Samsung behavior, TalkBack, OEM Arabic rendering and maximum text
  size.
- Play policy review or any signed production build.

## Reproduction commands

```sh
cd android
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew assembleDebug assembleDebugAndroidTest
adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# The app's own read-only managed state
python3 test-live-managed-status.py --serial emulator-5554 --expect-managed

# Synthetic local DNS blocks and the route check
python3 test-live-tcp-local.py --serial emulator-5554
python3 test-live-network.py --serial emulator-5554 --confirm-example-com
```

The text/app checks used a normal Chrome page and the Rules UI, not the
test-only fixture.
