# Android 1.0.0: signed self-control release

Released in the repository on 3 November 2026 as version 1.0.0, versionCode 26.
This is the first signed direct-sideload APK. The Play build exists and is
checked, but it is not uploaded and Play approval is not claimed.

## Artifacts

| Artifact | SHA-256 |
| --- | --- |
| `hisn-1.0.0-direct.apk` | `c42591ec8223e3d24d8159f0536d3212ee0f351b5c3edc1fc4d03c0d30afaac0` |
| `hisn-1.0.0-play.apk` | `bded282fd19fb524d77daf42d0a60a0660bd6db5c06acbba316e168743fe5517` |
| `hisn-1.0.0-play.aab` | `0794e0d509ca939cc012e2a01e82353211611c0c54174cefe7e4e40ec07cfad7` |

Package: `app.hisn.android`. Play version name `1.0.0`; direct version name
`1.0.0-direct`. Both use versionCode 26.

Signing certificate (RSA 4096, alias `hisn`):

```
CN=Hisn Android, OU=Hisn, O=Hisn, C=PS
SHA-256 2F:BE:D6:57:E6:AB:C7:1C:15:CA:D0:CD:99:1C:53:2F:59:77:2D:16:95:D7:C3:7E:65:A1:14:38:5E:94:70:C5
SHA-1   3F:DA:5C:DA:88:FD:71:E9:54:56:3F:6E:19:CE:F6:1C:05:79:53:09
```

The keystore and password were generated into the gitignored `keys/android/`
folder on the owner's machine and are **not** part of this repository. **The
owner must back both files up offline in two separate places.** Losing them
means existing installs can never be updated. The debug key that signed the
0.9.0 preview is different, so updating from 0.9.0 requires uninstalling it
first, which deletes its local rules and lock.

## What changed since 0.9.0

- **Daily app time limits.** Rules gains a separate card. The owner chooses apps
  and one daily foreground-time limit from 5 minutes to 24 hours. When Android's
  own on-device usage record reaches the limit, Hisn closes the app and shows the
  pause screen with a time-limit reason until local midnight.
- The limit is a saved rule: a protection lock freezes it in both directions,
  and the guardian tighten-only path may add or lower a limit but never remove
  or raise one.
- Enforcement requires both Hisn accessibility and Android's **Usage access**
  special permission. The Rules card distinguishes "Granted" from "Not granted",
  and Protection shows an attention item when limits are saved without the
  grant. Hisn never reports a limit as enforced without it.
- Hisn reads `UsageStatsManager` foreground totals for the current local day and
  caches one query per app for 15 seconds. It checks a limited app when it comes
  on screen, while it stays on screen, and in the once-a-minute sweep.
- Version metadata, the app subtitle and the DNS notification now say 1.0.0
  rather than preview.
- The 0.9.0 protection lock, direct-build removal guard, managed-device
  previews, DNS layer, text checks, visual sampling, schedules and pause screen
  are unchanged.

Feature inspiration came from installed ScreenZen and BlockerHero copies
(static observation only; no competitor code, assets or model weights were
copied). Both center on daily limits and schedules; ScreenZen's group model also
shows per-app pause, open-count and streak features that Hisn does **not**
implement. BlockerHero's focus-time and keyword models map to Hisn's schedule
and custom word rules. The only new 1.0.0 layer is the daily time limit.

## Verification

Run on this machine and the API 36 emulator (`hisn_ui`) against the 1.0.0 tree:

| Check | Result |
| --- | --- |
| `JAVA_HOME=<JBR> sh test-core.sh` | **PASS** — includes `AppTimeLimitTest`, `SavedSettings` sealed round-trip, `ProtectionLockTest`, DNS, text, visual, guardian and seed suites |
| `python3 check-strings.py` | **PASS** — 460 strings, 15 plurals, 485 source references, English and Arabic |
| `./gradlew assembleDebug assembleDebugAndroidTest lintDebug` | **PASS** — 0 lint errors |
| `CoreDeviceTestRunner` on the API 36 emulator | **PASS** — seed, saved rules, Unicode, daily time-limit persistence and protection-lock freeze, damaged settings, layer watch and seed age |
| Rules UI on the API 36 emulator | **PASS** — the time-limit card, Usage access "Granted", app picker, 5-minute limit, and the "1 app with a daily limit · 5 min per day" summary were read back through the accessibility tree; the Arabic RTL summary ("تطبيق واحد له حد يومي · 5 د يوميًا") was read back separately. With the grant revoked, the summary read "… · Usage access needed" and the Apps tile read "Needs attention". |
| Live enforcement on the API 36 emulator | **PASS** — see the enforcement note below |
| `./gradlew assembleRelease bundleRelease` with `HISN_ANDROID_*`, then `check-release.py --expect-distribution play` | **PASS** — signed Play APK and AAB, `app.hisn.android 1.0.0 (26)`, non-debuggable, no DUMP permission, no debug-only component or class |
| `./gradlew assembleRelease -PhisnDistribution=direct`, then `check-release.py --expect-distribution direct` | **PASS** — signed direct APK, `app.hisn.android 1.0.0-direct (26)` |
| `apksigner verify --print-certs` | **PASS** — both APKs verify with the certificate above |

### Live enforcement note

On the API 36 emulator, the test-only `app.hisn.android.test/GuardFixtureActivity`
was given a 5-minute daily limit through the product's saved rule (the same
sealed `PolicyStore` path the UI uses). Android Usage access was granted and Hisn
accessibility was enabled. Android's on-device record reached 5 minutes 23
seconds of foreground time; Hisn then brought the fixture task to the front as
`app.hisn.android.RestrictionActivity`, and the accessibility tree read:
"You reached the daily time limit you set for this app. It resets at local
midnight." The same screen showed the normal pause card.

This is one emulator pass with one fixture app. It is **not** a physical-phone
test, an OEM Usage-access test, or a measurement of enforcement latency under
Doze. The fixture is part of the test APK only and is absent from both release
artifacts (checked by `check-release.py`).

## Known limits

- Usage access is a separate special permission. Without it the feature is
  visible but not enforced, and the app says so.
- Android's foreground-time record resets at local midnight. Changing the device
  clock, time zone or date, or clearing Android's usage data, can change or
  reset it. Hisn does not claim a trusted clock or tamper-proof counting.
- A limit can be enforced a little late: the service checks on window events and
  about once a minute while the app stays on screen.
- The limit does not cover Android Settings, launchers, dialers, installers or
  Hisn itself; it is not a device-wide budget, an open-count limit or a parental
  usage report.
- Turning accessibility off, force-stopping Hisn, boot-key Safe Mode, ADB, root
  or a factory reset still bypasses app-level limits. The protection lock does
  not cover Android settings, app data, the device clock or another device.
- The Play build cannot block its own uninstall. The direct build's removal
  guard is friction, not an Android uninstall block.
- No physical-phone run of 1.0.0 is claimed. Visual recall, browser coverage,
  VPN handover, OEM behaviour and battery measurements remain device gates from
  [the 1.0 plan](ANDROID_1.0_PLAN.md).

## Publication

The signed direct APK is prepared as the asset for the GitHub release
`android-1.0.0`, to be published with its SHA-256 and the certificate
fingerprint above. The Play AAB is not uploaded. The direct build is the
recommended first distribution because its removal guard is outside Google
Play's Accessibility policy; never upload the direct build to Play.

## Updating from the preview

The 0.9.0 preview was debug-signed. Android refuses to install 1.0.0 over it
because the signing certificate changed. Export anything you need from the
preview, uninstall it, and install 1.0.0; its rules and protection lock start
empty. There is no in-place migration and no way to keep the debug install
signed as the release key.
