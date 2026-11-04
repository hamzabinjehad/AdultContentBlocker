# Hisn Mac 1.1.13 — one action for private windows

Released 5 November 2026 as a development candidate. Unsigned and unnotarized;
no Developer ID package, notarized build, physical-Mac acceptance or VoiceOver
audit is claimed.

## Why

The extension no longer has a private-window mode: it does not offer to run in
Incognito and no longer walks anyone to the "Allow in Incognito" toggle. Private
browsing is the app's job, through the `IncognitoModeAvailability` policy.

But the app left that job in prose. Setup said "private windows in Chrome" among
the open items, and the only route was the full hardening profile — which also
changes DNS, DoH and guest windows. Installing a whole network profile to fix one
setting is a poor trade, and on macOS 26 the `profiles` command no longer
installs profiles at all, so the person has to go through System Settings by hand
either way.

## What changed

- **A card, not a paragraph.** When any installed Chromium browser still allows
  private windows, Setup names those browsers and offers one button.
- **A narrow profile.** `Create the private-windows profile…` writes a
  configuration profile that forces exactly one key —
  `IncognitoModeAvailability = 1` — for each installed Chromium browser Hisn
  knows (Chrome, Helium, Chromium, Edge, Brave, Vivaldi and the rest of
  `NativeMessagingInstaller.browsers`). It never sets DNS, DoH, guest windows or
  extension policy.
- **The system does the approval.** The profile is written to
  `~/Library/Application Support/Hisn/Hisn-Private-Windows-Disabled.mobileconfig`
  and opened, so System Settings takes over. The app says to approve it in
  **System Settings › Device Management**, then **Check again**.
- **The loop closes through existing evidence.** `Check again` re-reads the
  forced policy per browser, so the card disappears when the profile is
  installed — no new state, no guess.

## What this does not do

- **It does not install silently.** macOS 26 removed command-line profile
  installation; the person approves it once in System Settings. The app cannot
  and should not bypass that.
- **It only affects browsers it knows and that are installed.** A browser Hisn
  has never heard of keeps its own incognito until it is added to the browser
  catalogue.
- **It is not the full hardening profile.** DNS, Private Relay, guest windows
  and extension management remain the full profile's job.

## Files

| File | Change |
| --- | --- |
| `macos/Hisn/SetupChecklist.swift` | `PrivateWindowsProfile` (payload, data, write-and-open), `SetupChecklist.browsers` |
| `macos/Hisn/ContentView.swift` | the Setup card, its action and notice |
| `macos/Hisn/Localizable.xcstrings` | five new strings with Arabic |
| `macos/HisnTests/SetupChecklistTests.swift` | the profile is valid and forces only `IncognitoModeAvailability` |
| `design/macos-ui-1.1.13/` | the Setup capture |

## Verification

- `./test.sh macos` — **542 tests, 0 failures**; 644 strings, all with Arabic.
- Version 1.1.13, build 25, installed in place on the owner's Mac.
- The button was pressed on the installed build: the profile was written, passed
  `plutil -lint`, and contained `com.google.Chrome` and `net.imput.helium` with
  `IncognitoModeAvailability = 1`; System Settings opened for approval.

## Limits

- The approval step itself was not clicked through in this pass — it needs the
  person's password or Touch ID. After approving, the card should disappear on
  **Check again**; that transition was not observed on this Mac yet.
- No UI test drives the card; the payload is unit-tested, the button was
  exercised by hand.
