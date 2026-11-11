# Android 1.5.4: a connected guard and an honest reconnect path

Development build in the repository, 8 November 2026, version 1.5.4,
versionCode 42. Installed on the owner's Samsung for testing; not a release or
accuracy claim. Adult detection, thresholds, permissions and stored rules are
unchanged.

## Why

The 1.5.3 dashboard could show **Apps: Needs attention** and “Hisn accessibility
is enabled but not connected” while `dumpsys accessibility` listed
`Service[label=حصن, id=app.hisn.android/.TextGuardService]` under **Bound
services**. `logcat` also reported a leaked `IntentReceiver` registered in
`TextGuardService.onCreate`. The app waited for `onServiceConnected`, which
Android does not always repeat after an update or rebind, and its one-shot rearm
broadcast could be missed while the service was being created. The person was
sent to the generic accessibility list and told to reconnect, but the direct
build's removal guard closes that very screen.

## What changed

| File | Change |
| --- | --- |
| `android/app/src/main/java/app/hisn/android/TextGuardService.java` | `onCreate` marks the live instance connected and runs the idempotent `startGuard()`; `onUnbind` returns true and a new `onRebind` reconnects; the rearm receiver is unregistered in `onDestroy` |
| `android/app/src/main/res/values/strings.xml` | Reconnect row: “Turn accessibility off and on”; attention text names Accessibility → Installed apps and says to return |
| `android/app/src/main/res/values-ar/strings.xml` | The same two strings in Arabic |
| `android/app/build.gradle` | 1.5.4 / 42 |
| `versions.json` | Android entry 1.5.4 / 42 |

No permission, threshold, list, consent sentence, capture size or stored field
changed.

## Verification

| Check | Result |
| --- | --- |
| `JAVA_HOME=<JDK 25> sh test-core.sh` | **PASS** — 68/72 adult-text blocks, 0/133 benign controls, the same four Franco-Arabic misses; strings and notices pass |
| `:app:assembleDebug` | **PASS** |
| `:app:assembleRelease -PhisnDistribution=direct` | **PASS**, signed with the release key |
| `:app:lintDebug` | **PASS**, 0 errors; warnings are the pre-existing set |
| `check-release.py --expect-distribution direct` | **PASS** — 1.5.4-direct (42), not debuggable, no test-only content, no DUMP permission |
| Install on SM-G990B2 | **PASS** — `adb install -r`, version 42, data preserved |

### Phone state after install

- The app language was consistent after setting the per-app locale to English
  (`cmd locale set-app-locales app.hisn.android --locales en`). Before that,
  layout labels were Arabic while several Java strings were English.
- DNS filtering stayed on and resolved `example.com` through the tunnel.
- A reboot was run. The service was listed under **Bound services**, the app
  was version 42, and the boot shield finished, but the dashboard still said
  "Hisn accessibility is enabled but not connected". A follow-up `onAccessibilityEvent`
  restore was built and installed; it did not clear the state either. The direct
  build's self-protection screen blocks turning the service off and on. This is
  now a service-binding problem that needs a clean re-enable (reinstall or a
  deliberate reset of the accessibility state), not another copy change.

### Signed artifacts

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `release/app-release.apk` (direct) | 31,290,865 | `4084e12d54f6b014b704c6e7590fc620f9fd01d0b287c282814d865684e76529` |
| `debug/app-debug.apk` | 32,279,803 | `38f538662b2d74186c49ca657725f3e3a13c2bf93919965d18edaaea2c76ecbc` |

Release certificate: `2F:BE:D6:57:E6:AB:C7:1C:15:CA:D0:CD:99:1C:53:2F:59:77:2D:16:95:D7:C3:7E:65:A1:14:38:5E:94:70:C5`.

## Limits

- **The reconnect fix is not yet exercised on a fresh service instance.** The
  phone still needs a reboot; until then the dashboard can keep the attention
  state from the old instance.
- **The guard blocks the recovery it recommends.** “Turn accessibility off and
  on” is not possible while the direct build's self-protection screen is armed.
  The next pass should offer a Repair action, a deliberate guard-off flow, or a
  reboot instruction instead.
- **Locale is fixed per app on this phone, not in code.** The underlying mixed
  resource resolution needs one supported locale applied to every activity.
- System dialog buttons still follow the German system language while the app is
  English.
- No phone adult-content test, real-app capture test or adult-positive accuracy
  claim is made.
