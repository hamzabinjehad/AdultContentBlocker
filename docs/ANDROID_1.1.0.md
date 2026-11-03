# Android 1.1.0: first-run and pause UI/UX pass

Prepared in the repository on 3 November 2026 as version 1.1.0, versionCode 27.
This is a signed direct-sideload update over 1.0.0; the GitHub release is
pending at the time of this record. It adds no new enforcement layer and
relaxes no rule, permission or consent text.

## What changed

- **A short first-run welcome.** Three skippable screens explain the same three
  ideas the product already has: block the paths you choose, make opening
  deliberate, and freeze the choice with a protection lock. Large type, one
  icon per page, progress dots, a single primary button, and a **Skip for now**
  action. Finishing opens Rules; skipping opens Protection. It is shown only
  when the app has no configured rules and the welcome has not been seen, so an
  upgraded install with rules goes straight to the dashboard. English and
  Arabic, RTL included.
- **Take a pause on Protection.** A visible row opens the existing breathing
  pause without a block: no rule changes, no permission, and **Back to Hisn**
  returns immediately. The pause tally still records the pause. It is available
  while a protection lock runs because it changes nothing.
- **Daily limits show today's use.** The daily-time-limit editor now lists each
  selected app with **Used X of Y today** and a progress bar, using the same
  on-device Usage access record as enforcement. Without the grant it says so
  instead of showing zeros. This makes the new 1.0.0 limit visible before it
  fires.
- The pause action moved near the top of Protection, after the hero/attention
  area, so it reads as a primary action rather than a footer.
- Version metadata and the subtitle now say 1.1.0.

## Design input

ScreenZen 2.0.0 on the owner's Mac was already inspected with REA in
[the Mac ScreenZen audit](REA_AUDIT_SCREENZEN_2026-11-03.md), and the installed
Android ScreenZen was opened read-only on the owner's Samsung for this pass.
The 1.0.0 daily-time-limit comparison is in
[the Android ScreenZen/BlockerHero record](ANDROID_REA_SCREENZEN_2026-11-03.md).
Its Android onboarding (three value screens: pause, app time limits, open
limits; large icon, one idea per screen, progress dots, one pill CTA) informed
the structure. Hisn uses its own copy, icons, colours and components. No
ScreenZen code, assets, copy or visual identity was copied.

The first-run flow deliberately has **no paywall, account, tip screen or
open-count/streak feature**. ScreenZen's open-count and streak ideas remain out
of scope, as the 1.0 comparison records.

## Verification

| Check | Result |
| --- | --- |
| `JAVA_HOME=<JBR> sh test-core.sh` | **PASS** — includes `AppTimeLimitTest`, sealed settings, protection lock, DNS, text, visual, guardian and seed suites |
| `python3 check-strings.py` | **PASS** — 480 strings, 15 plurals, 506 source references, English and Arabic |
| `./gradlew assembleDebug assembleDebugAndroidTest lintDebug` | **PASS** — 0 lint errors |
| `CoreDeviceTestRunner` on the API 36 emulator | **PASS** — seed, saved rules, Unicode, time-limit persistence/freeze, damaged settings, layer watch and seed age |
| Welcome UI on the API 36 emulator | **PASS** — English and Arabic RTL; three pages; Skip opens Protection; **Set up protection** opens Rules; back moves page-by-page |
| Voluntary pause | **PASS** — the row opens `RestrictionActivity` with "Take a pause", **Back to Hisn** returns to the dashboard, and Back does the same |
| Daily-limit usage display | **PASS** — with Usage access granted, the editor read `Chrome / Used 4 min of 15 min today`; the dialog remains FLAG_SECURE (no screenshots by design) |
| Signed Play build | **PASS** — `app.hisn.android 1.1.0 (27)`, `check-release.py --expect-distribution play`, `apksigner` |
| Signed direct build | **PASS** — `app.hisn.android 1.1.0-direct (27)`, `check-release.py --expect-distribution direct`, `apksigner` |
| Phone update | **PASS** — 1.0.0-direct updated to 1.1.0-direct in place; `firstInstallTime` unchanged (2026-11-03 19:01:04), installed APK hash matched the release asset, and the Arabic welcome rendered |

## Artifacts

| Artifact | SHA-256 |
| --- | --- |
| `hisn-1.1.0-direct.apk` | `4d80a3c7c50e8e0637c69e9aa8b604d55902d1ee55c9f96c7e30c5712f0d7dff` |
| `hisn-1.1.0-play.apk` | `f90c43b83cac0a79383dd8e0ccaecee4f9f604993ba618c02c23c7ebb6a75a5c` |
| `hisn-1.1.0-play.aab` | `ee5bce6fd4d70bc9fcfe0a43ce6bc019dd04377ca0d7266c1eba4ba2ea78dc4f` |

Package `app.hisn.android`. Play version name `1.1.0`; direct version name
`1.1.0-direct`. Both use versionCode 27. The signing certificate is unchanged:

```
CN=Hisn Android, OU=Hisn, O=Hisn, C=PS
SHA-256 2F:BE:D6:57:E6:AB:C7:1C:15:CA:D0:CD:99:1C:53:2F:59:77:2D:16:95:D7:C3:7E:65:A1:14:38:5E:94:70:C5
```

The Play AAB is built and checked but not uploaded; no Play approval is claimed.

## Upgrade notes and limits

- 1.0.0-direct updates to 1.1.0-direct in place with the release key. The
  owner's Samsung kept the package's first-install identity and rendered the
  Arabic welcome.
- The 0.9.0 preview was signed with a debug key. On the owner's Samsung, a
  signing-lineage install of 1.0.0 was still treated as a fresh install, so the
  preview's local rules and optional admin did not survive. This is one OEM
  observation, not a migration guarantee; the 1.0.0 record's instruction to
  uninstall the preview remains the safe path. New installs from 1.0.0 onward
  use the release key.
- The welcome appears only when no rules are configured. Existing installs with
  rules do not see it; the pause action and daily-limit usage display still
  apply.
- No full physical-phone acceptance of enforcement, TalkBack, large fonts,
  landscape, an OEM skin or the visual layer is claimed. The phone run above is
  a launch/update and Arabic-welcome check.
- The welcome and pause change no rule and grant no permission. Daily limits
  still need accessibility and Android Usage access; clock/time-zone changes,
  clearing usage data, force-stop, Safe Mode, ADB/root or a factory reset still
  bypass app-level limits.
- Keystore and password must be backed up offline in two places; they are not
  in this repository.

## Publication

The signed direct APK is prepared for the GitHub release `android-1.1.0` with
the SHA-256 and certificate above. The Play AAB is not uploaded.
