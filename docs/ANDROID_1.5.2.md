# Android 1.5.2: make the blocking layers survive an update

Development build in the repository, 8 November 2026, version 1.5.2,
versionCode 40. Signed Play and direct artifacts were built and verified. No
1.5.2 phone adult test is claimed. The thresholds, capture cadence, people modes
and after-block pause are unchanged.

## Why

Updating the app produced two repeated failures on the phone:

- **DNS filtering was left on but not running.** Android stopped the VPN when
  the package was replaced; the saved intent and VPN approval survived, but the
  foreground tunnel did not. The dashboard showed *Protection needs attention*
  and porn sites were reachable until the owner tapped **Set up DNS filtering**
  again.
- **Accessibility could read as disconnected after the app was replaced.** The
  system kept the service bound but did not repeat `onServiceConnected`; the
  app's status stayed false and the visual/text layer could look unarmed until
  the accessibility toggle was cycled.

These are update-recovery gaps, not missing block rules.

## What changed

- **DNS self-starts on foreground.** `MainActivity.onResume` now checks three
  facts: DNS was left on (`resumeRequested`), Android still holds the VPN
  approval (`VpnService.prepare` returns null), and the service is not
  `RUNNING`, `STARTING` or `DEGRADED`. Only then does it start the foreground
  service. If the approval is gone it does nothing and the setup card remains.
- **A live accessibility service can be re-armed.** `MainActivity` sends an
  explicit package-scoped `REARM_GUARD` broadcast when accessibility is enabled
  but `TextGuardService.connected` is false. A live service receives it, sets
  connected, reruns its idempotent setup and creates the visual guard if needed.
- **No privilege escalation.** The app does not and cannot change its own
  accessibility grant or Android's VPN approval. If the service is not alive,
  the existing setup card and Android accessibility settings are still the only
  repair. No root, Xposed, secure-flag bypass or policy spoofing is added.
- 1.5.1's protected/Incognito cover, 1.5.0 people cover, native adult TFLite
  path, 350 ms moving cadence, verdict cache and after-block pause are retained.

## Verification

| Check | Result |
| --- | --- |
| Core suites | **PASS** |
| Strings / notices | **PASS** — 578 strings, 16 plurals, 570 references |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | **PASS**, 0 errors |
| Phone 1.5.2 update | **PASS** — version 40 installed, data preserved |
| DNS auto-start | **PASS** — after the update, `DnsVpnService` was foreground without another tap |
| Guard re-arm | Service bound after the update; the broadcast path compiles. The phone was dozing before the visual check, so the re-arm handshake is a code/device check, not a completed smoke test. |
| `check-release.py`, `apksigner` | _to be recorded_ |

### Signed artifacts

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `direct/hisn-1.5.2-direct.apk` | 31,289,677 | `23cbf320d0181111c18c69de3a973b123193439964997bcc75c367ee9cef32b9` |
| `play/hisn-1.5.2-play.apk` | 31,289,673 | `9789a4e76fd20eeeefe1594f7550b4e176de2ce3f7343e4d0661411fb3d5b107` |
| `play/hisn-1.5.2-play.aab` | 16,892,074 | `ebcde8a208a703567cc538a69d79059789eb55a930872546a8fddb492fddab63` |

## Limits

- **The DNS restart needs the VPN approval.** If the owner revoked it in Android
  settings, or another VPN took over, the app cannot start the tunnel; the
  dashboard still shows the gap and the setup card asks for a tap.
- **The accessibility re-arm needs a live service.** A service that Android
  killed or unbound cannot receive the broadcast; only a user toggle or a system
  rebind restores it.
- **This does not make Incognito inspectable or delete Chrome's Incognito menu.**
  The 1.5.1 protected cover remains the ordinary-device response; actual removal
  needs a managed Chrome policy or removing/blocking Chrome.
- No adult-positive corpus, phone adult detection, battery, thermal or
  long-run reliability measurement. No comparison with HaramBlur.
- **This is the first hardening step, not an audit of every feature.** App
  selection, DNS bypass paths, text matching, schedules, limits, locks, the
  block page and visual accuracy remain separate reviews.

## Reproduce

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
sh test-core.sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

The DNS self-start is `MainActivity.ensureDnsRunning`; the guard re-arm is
`MainActivity.rearmGuardIfNeeded` with `TextGuardService.ACTION_REARM`.
