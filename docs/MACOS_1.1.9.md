# Hisn Mac 1.1.9 — a calmer guard and a status-first overview

Released 3 November 2026 as a development candidate. Unsigned and unnotarized;
no Developer ID package, notarized build or physical-Mac acceptance is claimed.

## Why

The 1.1.8 browser guard told the truth in a small orange panel: a browser name,
a countdown, and a paragraph. It worked, but it read like a system diagnostic at
the moment a person needed a clear next step. Screen Zen's block screen showed a
better shape for the same moment: a calm dark surface, one large mark, one short
headline, one number, and one obvious action. Hisn cannot copy Screen Zen's
quick-unlock model — the guard has no bypass — but it can borrow the clarity.

The Overview had the same problem in a milder form: every layer's explanation
met the person at once. Compact state chips and a disclosure make the answer
visible before the evidence.

## What changed

### Browser-guard intervention card

- A 460-point floating panel with a dark navy gradient, a blue shield tile, the
  browser name and reason, a 44-point countdown, and one full-width blue pill:
  **Open Chrome** (or the browser's name).
- **No dismiss, no unlock, no ignore.** Bringing the browser forward is the only
  button; restoring the extension remains the only way to cancel the close.
  A caption says exactly that.
- The title bar is hidden, the panel is movable, stays above other windows,
  joins all Spaces, and grows for several browsers (up to a scrollable 640 pt).
- The card is a separate `GuardAlertCard`, rendered and tested without a live
  guard. The countdown and close decisions remain in `BrowserGuard`.

### Status-first Overview

- Compact layer chips show each layer's name and state by colour and icon; the
  full evidence, the readiness count and per-layer repair rows are behind a
  **Layer details** disclosure.
- The disclosure opens automatically when a layer needs attention or is still
  checking, so a problem is never hidden. It can be opened manually when every
  layer is healthy.
- The 1.1.8 pause row, protection-authority evidence and lock controls remain.

## Files

| File | Change |
| --- | --- |
| `macos/Hisn/BrowserGuard.swift` | Panel style, `GuardAlertView`, `GuardAlertCard` |
| `macos/Hisn/ContentView.swift` | Layer chips and `Layer details` disclosure |
| `macos/Hisn/Localizable.xcstrings` | Eight new strings, all with Arabic |
| `macos/HisnTests/PauseTests.swift` | Guard-card render in English and Arabic |
| `macos/generate_xcodeproj.py`, project | 1.1.9, build 13 |
| `design/macos-ui-1.1.9/` | Guard and overview captures |

## Verification

- `./test.sh macos` — **522 tests, 0 failures**; 597 strings, all with Arabic,
  including the guard-card render at both languages.
- The guard panel's countdown, recovery and close logic remain covered by the
  existing `BrowserGuardTests`; this pass changes presentation and disclosure
  only.
- `bash macos/release/release.sh --smoke` — **PASS**: universal unsigned
  package structure validated, `Hisn-UNSIGNED-TEST.pkg`, 8.2 MB, SHA-256
  `16280ef15dc8f4272aa48cb26f64ffb28e34d68dedbec3bb99d6df8f1354b61f`. It was
  not installed.
- The design captures are deterministic SwiftUI renders, not a physical click
  test. No user study or screen-reader audit is claimed.

## Limits

- Opening the browser does not restore the extension for the person; it only
  brings the window forward. The panel says the extension must be restored, and
  the browser still closes when the countdown ends.
- The countdown is a clock reading. Sleep, wake, a clock change and a slow
  restoration are handled by the existing guard tests, not by this visual pass.
- Overview still reports readiness, not proof that every route is blocked. The
  disclosure does not hide a problem, only the evidence when everything is
  healthy.

## Next owner actions

Build, sign and notarize with a Developer ID; run the guard with a real browser
and VoiceOver in English and Arabic; record the result in
[RELEASE_VALIDATION.md](RELEASE_VALIDATION.md).
