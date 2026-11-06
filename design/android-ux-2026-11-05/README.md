# Android UI/UX refresh — 2026-11-05

This is a **design record**, not a release record. It changes presentation and
the opt-in path to the existing uninstall guard. No enforcement layer,
permission, full disclosure, persistence value or commitment rule was added,
removed or weakened. The Android version stays 1.1.0 (versionCode 27).

## Device and build

- Owner's Samsung SM-G990B2, Android 16, 1080 × 2340 (420 dpi override), Arabic
  and dark. Release-signed direct build (`-PhisnDistribution=direct`), same
  certificate and version as the installed app, updated in place with
  `adb install -r`. App settings and the running protection lock were preserved.
- English captures: `hisn_ui` API 36 emulator, wiped before the run, direct
  debug build. The English build is a debug build; the onboarding lock page is
  present because the build is direct.
- The owner's phone had the direct-build uninstall guard already on. The
  screenshots show that state; the onboarding page below shows the opt-in for a
  fresh install.

## What was observed

ScreenZen 2.9.4 (`com.screenzen`) was opened on the owner's phone. The owner
granted its onboarding permissions so its main list screens could be reached.
After that the observation was read-only: no setting, rule, account, purchase or
block group was changed, and no competitor screenshot is redistributed here.

The pass borrows the *structure*, not the product: one idea per onboarding
screen with one full-width action, one permission per card with a short reason
and a state check, standalone rounded settings rows carrying an icon, a label,
a value and a chevron, and almost no body copy on list screens. The full
disclosures remain in Hisn's own words, under **Full disclosure**. No ScreenZen
code, asset, string, colour or data model was copied.

## What changed

### First run

- Three value screens: what Hisn is, why it interrupts autopilot, and what a
  protection lock does. One sentence each.
- A permission checklist: App protection, Website filtering, Daily time limits,
  Pause screen, Notifications and Keep running. Each card has an icon, a
  one-line reason and one full-width action. Returning to the page re-reads the
  platform state; a granted card shows a check and **On**.
- A final direct-build page, **Keep Hisn in place**, offers the existing
  uninstall guard in one tap. It says that App protection is required. Finishing
  without the guard stays available. Existing installs are not changed
  silently.
- The DNS card now shows a short consent summary; the previous full text is one
  **Full disclosure** tap away. The same short-plus-disclosure pattern is used
  for App protection, Adult text, Images and video, and Short videos.

### Protection (home)

- The header carries the product name and a plain-language tagline instead of
  the version string. The version moved to Settings → About.
- The shield, verdict, short explanation and three layer tiles stay.
- **What needs attention** now opens expanded, so the concrete reasons are
  visible without another tap. Leaving DNS filtering on while it is not running
  is now part of the warning state, not only the list.
- The duplicate Pause card and the long app/uninstall cards are gone. A single
  **Protection rules** list carries Apps to block, Adult text, Images and video,
  Daily schedule, Daily time limits, Protection lock and Uninstall protection.
  Each row shows a state word and opens its editor. Pause is a standalone row.

### Rules

- The intro paragraph and the top **About these rules** panel are gone.
- Built-in checks are four single-line switches. Their descriptions moved to the
  consent sheet that opens when they are turned on.
- The App protection button hides once the service is connected. The status line
  is one short phrase.
- Daily time limits, Daily schedule and Protection lock are standalone rows with
  their state, replacing three tall cards with a status line and a button.
- Uninstall protection and Private screens moved to Settings. The long
  "how to uninstall" guide was removed from the app.

### Settings (replaces the Guide page)

- The page is now a list. **Protection**: Uninstall protection, Permissions,
  Keep protection running, Guardian pairing. **Information**: Show introduction,
  How Hisn works, What can bypass Hisn, Privacy, Diagnostics, Word tester,
  Licenses and sources, About.
- Long explanations open in dialogs. The word tester is still a secure dialog;
  diagnostics and the copy action still work. The version line moved here.
- **Show introduction** replays the first-run flow without clearing data.

### Uninstall guard

- The direct-build guard is unchanged. The change is the path to it: a first-run
  page, a one-tap **Turn on uninstall protection** action in the dialog when it
  is off, and a warning-coloured **Off** state on the Home and Settings rows.
- The Play build still cannot block uninstall; its dialog keeps the honest
  device-owner explanation and setup command. No new claim is made.

### Pause screen

- The title is now **Pause** and the card heading is **Breathe**. The reason
  sentences and the daily-limit line are shorter. The breathing logic, tally,
  need choices, immediate Home action and privacy flags are unchanged.

## What did not change

- Every consent, coverage, private-screen and commitment disclosure remains,
  word for word, under its **Full disclosure** control. The new short paragraphs
  are summaries, not replacements.
- No new permission, intent filter, exporter, network call or stored field.
- The protection lock still freezes rules in both directions, the DNS consent
  still names Cloudflare Families, and the direct-build guard still depends on
  the accessibility service.
- The guard is not switched on silently for an existing install. The first-run
  page and the dialog are explicit opt-ins.

## Files

| File | State |
| --- | --- |
| `01-protection-ar-dark.png` | Home, Arabic, dark, DNS on, one background-check notice |
| `02-protection-list-ar-dark.png` | Home rules list, Arabic, dark |
| `03-rules-ar-dark.png` | Rules, Arabic, dark, single-line switches |
| `04-rules-list-ar-dark.png` | Standalone limit/schedule/lock rows |
| `05-settings-ar-dark.png` | Settings list, Arabic, dark |
| `06-dns-consent-ar-dark.png` | Short DNS consent with Full disclosure |
| `07-permissions-en-light.png` | Permission checklist, English, light |
| `08-uninstall-guard-en-light.png` | First-run uninstall guard page |
| `09-protection-en-light.png` | Home, English, light |
| `10-rules-en-light.png` | Rules list, English, light |
| `11-settings-en-light.png` | Settings, English, light |
| `12-diagnostics-ar-dark.png` | Diagnostics with the encrypted-DNS resolver counter |

## Verification

- `./gradlew :app:compileDebugJavaWithJavac`, `:app:assembleDebug`,
  `:app:assembleRelease -PhisnDistribution=direct` and `:app:lintDebug`: pass,
  0 errors.
- `python3 check-strings.py`: pass, 548 strings and 15 plurals, English and
  Arabic with matching placeholders.
- `bash android/test-core.sh`: pass, including the 239,880-domain seed, 68/72
  adult-text blocks with 0/133 benign controls, and the uninstall-protection
  and guardian tests.
- The release direct APK kept the owner's signing certificate and was installed
  with `adb install -r`; the app's saved rules and protection lock survived.
- The screenshots are phone captures (Arabic) and emulator captures (English).
  They are not evidence of enforcement, VPN routing, accessibility interception,
  TalkBack order or a Play review.

## Still open

- TalkBack and Switch Access over the new permission cards, disclosure controls
  and standalone rows.
- Largest font scale, landscape, split screen and a hardware keyboard.
- English on the physical Samsung (the device locale is Arabic); the English
  captures are from the emulator.
- A fresh-install physical-device run of the guard opt-in and the one-tap
  **Turn on uninstall protection** action.
