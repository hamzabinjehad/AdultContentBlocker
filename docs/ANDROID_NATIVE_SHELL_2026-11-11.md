# Android: the native shell is the app

**Status: development, 1.6.0 / 43 candidate, 11 November 2026.** `ShellActivity` is now the
launcher, and it renders live protection state rather than sample content. A signed direct
APK was built with the owner's key; the phone update was **not applied**, because the phone
disconnected during the pass. No release artifact is published and no phone result is
claimed for this change.

## Debug pass, 12 November

Three problems the owner hit on the Galaxy:

- **The native package was force-stopped and the accessibility guard was off**, so the shell read
  0 of 4. The guard was re-enabled in Android's accessibility settings and the shell then read
  3 of 4 (Network, Apps, Adult tags, Images; Network stayed degraded).
- **Android disables an app's accessibility service when the app is updated.** After an update
  the guard is gone and the direct build's keep-installed cover waited three minutes for a
  service the system had turned off, blocking the phone. `BootShieldService.startIfNeeded` now
  checks `Permissions.accessibilityInSettings` first and skips the cover when the service is not
  enabled at all; the dashboard reports the missing layer instead. The cover still runs after a
  reboot when the service is enabled.
- **The DNS switch could not turn off.** A bound `VpnService` ignores `stopService`, so the
  switch did nothing. `DnsVpnService` now has `ACTION_STOP`, which clears the saved intent,
  closes the session and stops the service; the shell sends it on the switch-off path. The shell
  also resumes DNS on open (`ensureDnsRunning`, the classic activity's behaviour) and re-arms the
  guard (`ACTION_REARM`).

The remaining amber layer is not an app fault: the phone had **no active network** (Wi-Fi not
connected, no default network), so the tunnel's upstream cannot be reached and the app reports
DNS as degraded. Once a network returns the service re-checks and should return to Running.

## Ported design, 12 November

The shell now uses the sample app's layout instead of the first dashboard pass: the brand header
(shield, Hisn, tracked caps), the ring hero, the inset coverage ledger, one card of layer rows
with real switches, a rules card and the reassurance row. The layer switches act on the real
app:

- **DNS filtering** starts the VPN through the system consent and the notification permission;
  switching it off stops the service.
- **App protection** opens Android's accessibility screen; the switch reflects the grant.
- **Adult tags** and **Image checks** show the same short paragraph and full disclosure as the
  classic sheet, then write the real policy (`PolicyStore.update` / `updateVisual`). A
  protection lock refuses the write and shows the lock message.
- **Rules and Settings rows** still open the classic editors at page 1 and 2.

`ShellActivity` reads the live state every 1.5 s as before. The hero and ledger count only
layers that are chosen and observed running, so a chosen-but-unconnected layer shows its switch
on while the ledger stays Off.

Still not ported: the rules/settings editors themselves (domains, words, app picker, schedule,
limits, lock, diagnostics, import/export) and the Arabic strings for the new shell copy.

## What changed

- **The native shell is the front door.** The manifest now gives `MAIN`/`LAUNCHER` to
  `app.hisn.android.ShellActivity`. The classic Views activity (`MainActivity`) keeps the
  editors that have not been ported and is `exported="false"`; it is reachable in-app only.
- **The shell reads the same state the classic dashboard reads.** `readShellState` combines
  `DnsVpnService.state` and its live domain count, `TextGuardService.connected`, the
  accessibility grant, `PolicyStore.snapshot()` / `remainingMillis()`, the schedule, time
  limits and the local personal preferences. A layer reads **On** only when it was chosen
  and is observed running; a saved switch alone is not enough. The screen refreshes every
  1.5 s.
- **Protection, Rules and Settings carry real values.** The hero states Protected / Partly
  protected / Not protected with a 4-layer coverage ledger. Rules shows the saved domain,
  word, app and limit counts plus the schedule and lock remaining time. Settings shows the
  saved name, the theme/language mode, the layer count and the version.
- **The layers are read-only for now.** Each row, the hero action and the attention list
  open `MainActivity` at the matching page (0 Protection, 1 Rules, 2 Settings), where the
  switches, consent sheets and editors still live. The screen says so in one line.
- **Onboarding and shortcuts still work.** `ShellActivity` hands a first run to
  `WelcomeActivity`, and `finishWelcome()` now returns to the shell instead of the classic
  activity. Dynamic shortcuts ("Rules", "Settings") were retargeted at `ShellActivity` and
  carry the tab index; `ShellActivity` opens that tab, including through `onNewIntent`.
- **The `:nativeshell` module is gone.** Compose, Material 3, the icons and Kotlin are now
  ordinary `:app` dependencies, because the shell is the app.

## Cost

The signed direct APK grows from 31.76 MB to **39.77 MB** (+8.0 MB) with Compose, Material 3
and the Compose runtime in the release variant. R8 is not enabled for releases in this
tree, so nothing is shrunk. Turning minification on needs its own pass: the classifier, the
WebView JS interface and the reflection in the receipt path would all need keep rules and a
device re-check.

## Evidence, 11 November

| What | Where | Result |
| --- | --- | --- |
| API 23, fresh install | `hisn_api23` emulator | welcome → skip → shell "Not protected", 0 of 4; Rules and Settings values; rows open the classic page; shortcut intent opened the Rules tab |
| Android 16, dark | Pixel 7 emulator (API 36) | shell renders edge-to-edge, correct status/navigation bar icons, no crash |
| Signed artifact | owner key `keys/android/hisn-release.jks` | certificate SHA-256 `2fbed657…`, versionCode 43, versionName `1.6.0-direct`; installs as an in-place update over the current app |

Captures: [main, API 23](../design/android-native-2026-11-11/32-native-main.png),
[main, Android 16 dark](../design/android-native-2026-11-11/33-native-main-dark.png),
[rules](../design/android-native-2026-11-11/34-native-rules.png),
[settings](../design/android-native-2026-11-11/35-native-settings.png),
[shortcut tab](../design/android-native-2026-11-11/36-native-shortcut-rules.png).

The four layer rows are read-only, so these captures show saved state and service state,
not that a switch was toggled from the shell.

## What is still not ported

- **Every editor and consent sheet.** Adult text, image checks, app picker, domain and word
  editors, schedule, daily limits, protection lock, diagnostics, import/export, permissions
  and the guide all remain in `MainActivity`. They open from the native rows.
- **The shell's strings are English only.** The new Kotlin strings are literals; `values-ar`
  for them arrives with the editor port, together with RTL checks of the new screens.
- **Accessibility.** No TalkBack pass, no 2.0× font-scale check, no screenshot test and no
  reduced-motion path on the shell.
- **The phone has not run this build.** The signed APK exists; the update was not installed.

## Next

1. Port the layer switches and the setup actions (DNS consent, accessibility hand-off, adult
   and image consent) into the shell, keeping the existing service calls and policy writes.
2. Port the Rules and Settings editors, with the protection-lock read-only state.
3. Only then delete `MainActivity`, the `Ui` view toolkit and the old layout resources.
4. Enable R8 with keep rules and re-measure the APK; re-run the API 23 and API 36 passes,
   TalkBack, Arabic RTL and screenshot tests.
