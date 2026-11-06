# Android UI/UX refresh — 2026-11-05

Design record for the Android 1.1.0 first-run, Protection, Rules and Settings
pass. It changes presentation and the opt-in path to the existing direct-build
uninstall guard. It adds no enforcement, permission, full disclosure, stored
value or commitment rule. The version stays 1.1.0 (versionCode 27).

The captures and the per-screen record are in
[design/android-ux-2026-11-05](../design/android-ux-2026-11-05/README.md).

## Reference and boundary

ScreenZen 2.9.4 (`com.screenzen`) was opened on the owner's Samsung SM-G990B2.
The owner granted its onboarding permissions so its main list screens could be
reached; after that the observation was read-only. The pass borrows the
*structure* — one idea per onboarding screen, one permission per card with one
action, and standalone rounded setting rows with an icon, label, value and
chevron. No ScreenZen code, asset, string, colour or data model was copied, and
no competitor screenshot is redistributed. Hisn's full disclosures remain in
Hisn's own words under **Full disclosure**.

## What changed

### First run

- Three value screens, one sentence each.
- A permission checklist: App protection, Website filtering, Daily time limits,
  Pause screen, Notifications and Keep running. Each card has a short reason and
  one full-width action. The page re-reads Android's state on resume; a granted
  card shows a check and **On**.
- Direct builds add **Keep Hisn in place**, a one-tap opt-in to the existing
  uninstall guard, with a visible **Requires App protection above** note and a
  **Finish without the guard** route.
- DNS starts behind a short consent summary. The previous long disclosure is one
  tap away. The same short-plus-disclosure sheet is used for App protection,
  Adult text, Images and video, and Short videos.

### Protection (home)

- The header replaces the version string with the tagline **Adult content
  blocker**. The version moved to Settings → About.
- The attention list opens expanded, and a DNS layer that was left on but is not
  running now produces the warning headline, not only a row.
- The long app and uninstall cards and the duplicate Pause card are gone. One
  **Protection rules** list carries Apps to block, Adult text, Images and video,
  Daily schedule, Daily time limits, Protection lock and Uninstall protection.
  Pause is a standalone row.

### Rules

- The intro paragraph and top help panel are gone.
- Built-in checks are four single-line switches. Their descriptions moved into
  the consent sheet that opens when each is enabled.
- The App protection button hides when the service is connected.
- Daily time limits, Daily schedule and Protection lock are standalone rows with
  their state, replacing three tall status-plus-button cards.
- The uninstall guide and the Private screens panel moved out of Rules. The
  long "how to uninstall" text is no longer in the app.

### Settings

- The Guide page became a Settings list: Protection (Uninstall protection,
  Permissions, Keep protection running, Guardian pairing) and Information (Show
  introduction, How Hisn works, What can bypass Hisn, Privacy, Diagnostics, Word
  tester, Licenses and sources, About).
- Long explanations open in dialogs. The word tester stays a secure dialog.
  **Show introduction** replays the first-run flow without clearing data.

### Uninstall guard

- The direct-build guard is unchanged. It is now one tap from the first-run page
  and from the Home/Settings row; when it is off, the row shows a
  warning-coloured **Off** and the dialog's primary action is **Turn on
  uninstall protection**.
- The Play build still cannot block uninstall and keeps its honest device-owner
  explanation and setup command. No new claim is made.
- Existing installs are not changed silently; the guard remains an explicit
  opt-in.

### Pause screen

- The title is **Pause**, the card heading is **Breathe**, and the reason and
  daily-limit lines are shorter. Breathing, the tally, need choices, the
  immediate Home action and the privacy flags are unchanged.

## What did not change

- Every consent, coverage, private-screen and commitment disclosure remains,
  word for word, under **Full disclosure**.
- No new permission, intent filter, exporter, network call or stored field.
- The protection lock still freezes rules in both directions, and the DNS
  consent still names Cloudflare Families.
- The direct-build guard still depends on the accessibility service and still
  does not survive Safe Mode from the hardware keys, ADB, root or a factory
  reset.

## Verification

- `./gradlew :app:compileDebugJavaWithJavac`, `:app:assembleDebug`,
  `:app:assembleRelease -PhisnDistribution=direct` and `:app:lintDebug`: pass,
  0 errors.
- `python3 check-strings.py`: pass, 548 strings and 15 plurals, English and
  Arabic with matching placeholders.
- `bash android/test-core.sh`: pass, including the 239,880-domain seed, 68/72
  adult-text blocks with 0/133 benign controls, and the uninstall-protection and
  guardian tests.
- The release direct APK kept the owner's certificate and was installed with
  `adb install -r`; saved rules and the running protection lock survived.
- Arabic captures are from the owner's phone; English captures are from the
  wiped `hisn_ui` API 36 emulator. They are not enforcement, VPN, accessibility,
  TalkBack or Play-review evidence.

## Still open

- TalkBack and Switch Access over the new permission cards, disclosures and
  standalone rows.
- Largest font scale, landscape, split screen and a hardware keyboard.
- English on the physical Samsung (the device locale is Arabic).
- A fresh-install physical-device run of the guard opt-in and the one-tap
  turn-on action.
