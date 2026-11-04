# Android UI/UX pass — 2026-11-04

This is a **design record**, not a release record. It changes presentation only:
no enforcement layer, permission, consent, persistence or commitment rule was
added, removed or weakened. The Android version stays 1.1.0 (versionCode 27);
the screenshots come from a release-signed `1.1.0-direct` build installed over
the owner's existing app, so the build and the saved settings were preserved.

## Device and build

- Owner's Samsung SM-G990B2, Android 16, 1080 × 2340 (420 dpi override).
- Release-signed direct build (`-PhisnDistribution=direct`), same certificate
  and version as the installed 1.1.0, updated in place with `adb install -r`.
- Arabic dark and English light. The app locale was changed with
  `cmd locale set-app-locales app.hisn.android` and restored to Arabic
  afterwards; the system night mode was restored to dark.
- Capture 07 is from the `hisn_ui` emulator because the phone's welcome has
  already been seen. It uses the debug build; the welcome screen shows no
  version, and the layout is the same.
- These are phone screenshots, not a TalkBack, large-text, tablet or
  maximum-font-size acceptance.

## What changed

### One calm answer at the top

The Protection page now leads with a drawn shield ring, a 27 sp verdict and one
short sentence. The three layer words stay directly below it, then a single
**Next step** action. The old separate **Recommended setup** card and its
duplicate button are gone; the DNS button becomes the primary action only on
the DNS step.

### Less repetition, more scan value

- Layer tiles carry a state dot and one state word instead of a sentence.
- The Protection app card now shows the selected-app line again. That line was
  being overwritten by the Rules page's copy of the same field, so it stayed
  blank on Protection (a real UI bug, now fixed).
- The gray-screen status is now the short **Gray screen · Off**; the longer
  explanation stays in the switch summary.
- Rules no longer opens with a five-line paragraph. It opens with one line and
  an **About these rules** disclosure.
- The Guide's floating **How Hisn works** disclosure is now a quiet panel.

### Typography, colour and shape

- Day/night palettes were retuned for calmer surfaces and stronger hierarchy.
- Cards are 24 dp; buttons are 52 dp pill controls; icon tiles are rounded.
- Page titles are 30 sp, section headers 19 sp, body 15 sp, captions 13 sp.
- New surface and state contrast stays above 4.5:1 for body text.
- The header is the product name plus the version line, not the product name
  twice.

### Guide steps now work in Arabic

The guide parser split on blank lines and only recognised ASCII digits. It now
accepts inline numbering and Arabic-Indic numerals, converts the badge number
safely, and leaves room for wrapped lines so the second line is not clipped.
This fixes the single Arabic paragraph that used to appear with one badge.

### Welcome and pause

- Welcome uses the full-screen wash, a larger illustration, 30 sp title,
  progress bars and a pill button in the same visual language as the dashboard.
- The pause screen keeps the same breathing logic and tally, with a larger
  title, ring and card.

## Design input

ScreenZen 2.9.4 (`com.screenzen`) was observed on the same phone through its
onboarding screens; the only interaction was advancing the onboarding. No
permission was granted and no setting, rule, account or purchase was changed.
REA 6.5.0 was used through its adb provider to list the devices, read the
display and package identity, and capture frames as digest-bound Evidence. The
design borrows the *ideas* of a calm full-screen introduction, one message per
screen, a single accent, generous whitespace, large type and one clear next
action. It does not copy ScreenZen's code, assets, copy, colours or data model,
and no competitor screenshot is redistributed here.

## Files

| File | State |
| --- | --- |
| `01-protection-ar-dark.png` | Protection, Arabic, dark |
| `02-rules-ar-dark.png` | Rules, Arabic, dark |
| `03-guide-ar-dark.png` | Guide, Arabic, dark, numbered steps |
| `04-protection-en-light.png` | Protection, English, light |
| `05-rules-en-light.png` | Rules, English, light |
| `06-guide-en-light.png` | Guide, English, light |
| `07-welcome-en-light.png` | Welcome, English, light (emulator) |
| `08-pause-ar-dark.png` | Voluntary pause, Arabic, dark |

## Verification

- `./gradlew :app:assembleDebug`, `:app:assembleRelease -PhisnDistribution=direct`,
  `:app:assembleDebugAndroidTest` and `:app:lintDebug` pass.
- `bash android/test-core.sh` passes.
- The release APK keeps the owner's signing certificate
  (`2F:BE:D6:…:70:C5`), and `adb install -r` preserved the app's
  `firstInstallTime` and settings.
- The screenshots show the real app on the owner's phone; they are not evidence
  of enforcement, VPN routing, accessibility interception, TalkBack order or a
  Play review.
