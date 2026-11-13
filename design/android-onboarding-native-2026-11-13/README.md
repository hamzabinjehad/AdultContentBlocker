# Onboarding on native Material components

Date: 13 November 2026. Follows [android-onboarding-2026-11-13](../android-onboarding-2026-11-13/README.md).
Same pages, strings and disclosures; the hand-built parts are replaced by the platform's own.
Captures are from the `hisn_ui` emulator, debug build, dark theme.

## What changed

- **Swipe between pages.** The pages sit in a `HorizontalPager`, so a swipe moves forward and
  back and follows right-to-left layout by itself. Tap, the buttons and system Back still work.
- **A back arrow** beside the step label from the second page on (`02-swiped-back-arrow.png`).
  Before, going back was the system gesture only.
- **Progress is a `LinearProgressIndicator`** instead of nine drawn segments.
- **Permission rows are Material `ListItem`s** with the same switch behaviour: the switch shows
  what Android reports and flipping it opens Android's screen (`04-permissions.png`).
- **"0 of 2 on"** beside "Needed for blocking", read live from Android.
- **Swiping off the personal page saves it**, the same as pressing Continue, and closes the
  keyboard.
- **Only the page in view is read by TalkBack.** A neighbouring page kept ready for the swipe
  is hidden from the accessibility tree.
- **Rules tab:** the warning chip on a rule tile is a Material `AssistChip` and is now the fix.
  "DNS filtering is off" turns DNS filtering on; the App protection chips open Android's
  accessibility settings.

## Checked

`bash android/test-ui.sh emulator-5554 direct`: 22 of 22 pass, including two new tests
(swipe and back arrow; swiping off the personal page saves it). Not checked: a real phone,
light theme and Arabic by eye, and tapping the rule chip (no test covers it).
