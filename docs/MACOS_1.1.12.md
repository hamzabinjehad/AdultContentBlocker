# Hisn Mac 1.1.12 — browser protection has no off switch

Released 4 November 2026 as a development candidate. Unsigned and unnotarized;
no Developer ID package, notarized build, physical-Mac acceptance or VoiceOver
audit is claimed.

## Why

Browser protection used to be conditional. The guard watched browsers only
while a lock ran, or when the person opted in with **Keep browser protection on**
in Settings → Browser protection. Outside those, removing the extension left the
browser open and nothing happened: the layer that makes the browser safe was the
one layer the person could switch off.

Every other layer keeps applying without being asked. Browser protection now
does too.

## What changed

- **The guard is continuous.** It is active whenever Hisn runs. A browser whose
  extension stops answering shows the guard card with a one-minute countdown and
  is then closed; a relaunch without a repair gets a shorter warning. Unsupported
  browsers, including Safari, close on the same rule.
- **The opt-in is gone, not hidden.** `Keep browser protection on`, its
  confirmation sheet, the stored `guardRequireOutsideLock` preference, the
  `setRequireOutsideLock` setter and the readiness issue that asked for the
  opt-in are all removed. Settings states the rule instead: *"Browser protection
  is always on while Hisn runs."*
- **A lock no longer decides whether the guard runs.** It only changes what the
  person may do to the app while it runs.

## What this does not do

- **It is not tamper-proof.** Quitting Hisn outside a lock, an administrator, or
  removing the login agent still stops it. Only the separately installed login
  agent brings the app back after a crash or force-quit.
- **Closing loses unsaved work.** The countdown is the warning. There is no
  per-window exception, no snooze and no "ignore once".
- **Detection is periodic**, not instant, so a browser can be used briefly before
  the next check.
- **Not every profile or window is verified:** standard `Default`/`Profile`
  folders are checked; custom paths, guest and private windows are not. An
  explicitly trusted unknown app still bypasses extension checks.
- **The build is still unsigned**, so the system filter does not load. Browser
  protection does not depend on the filter.

## Files

| File | Change |
| --- | --- |
| `macos/Hisn/BrowserGuard.swift` | continuous `isActive()`; opt-in key, property and setter removed |
| `macos/Hisn/ContentView.swift` | Settings statement replaces the toggle, its alert and its setter; readiness call sites |
| `macos/Hisn/ProtectedSetup.swift` | `requireOutsideLock` parameter and `.browserGuard` issue removed |
| `macos/HisnTests/` | `isActive` test rewritten; readiness tests and fixtures updated |
| `macos/Hisn/Localizable.xcstrings` | two new statements with Arabic |

## Verification

- `./test.sh macos` — **541 tests, 0 failures**; all strings used by the code
  have Arabic; `python3 macos/check_localization.py` passes.
- Version 1.1.12, build 24. Installed in place on the owner's Mac.
- The close path itself is unchanged: it is the same one that ran under a lock,
  and its policy tests pass.

## Limits

- Not exercised end to end against a really removed extension in this pass. The
  owner should remove Hisn from one browser once, confirm the countdown closes
  it, and record the result in `RELEASE_VALIDATION.md`.
- The Settings statement is new copy; the Browser protection card has no fresh
  screenshot for this build.

## Note on the working tree

This change was made while another session had uncommitted iOS and extension UI
work in the same checkout. Only the macOS files for this change were staged and
committed; the other work was left untouched.
