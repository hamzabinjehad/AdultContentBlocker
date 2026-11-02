# Mac UI/UX pass — 2 November 2026

This pass improves the existing window rather than replacing it. The product
rule stays: the interface reports what is actually enforced, separately from
whether a lock is recorded.

## What changed

### State is visible everywhere

- The sidebar now ends with a **persistent status footer**: protection headline,
  a colour dot, and lock state/remaining time. Clicking it opens Overview.
- The Lock row in the sidebar shows a lock badge while a lock runs.
- The window toolbar carries a **Manage lock / Start a lock…** action, so the
  main lock action is reachable from every page.
- The window toolbar now shows the page subtitle; the default window is
  980×700 with a 760×560 floor.

### Clearer primary action on Overview

- The protection card now ends with one **primary action**:
  - **Continue setup** when nothing is enforcing a lock,
  - **Start a lock…** when at least one layer is enforcing,
  - **Manage lock** while a lock runs.
- The duplicate Start a lock button in the lock summary card was removed; the
  lock card only offers **Manage lock** while a lock actually runs.

### Faster navigation

- `Go` menu: ⌘1 Overview, ⌘2 Protected Setup, ⌘3 Blocking Rules, ⌘4 Lock,
  ⌘5 Settings.
- `Protection` menu: ⌘R Check again, ⌘⇧L Start a lock page.
- Keyboard navigation complements the existing menu-bar quick lock and quick
  site block.

### Authority and lock honesty

- The Setup account-split stage shows read-only protection-authority evidence:
  daily account, guardian account, guardian daemon.
- The Lock page warns when the daily account is still an administrator, before
  a lock starts.
- While a lock runs, account/removal controls are disabled; the app never
  creates the guardian or stores its password.

## Verification

| Check | Result |
| --- | --- |
| `./test.sh macos` | **PASS** — 508 tests |
| Localization export | **PASS** — 561 strings, all with Arabic |
| Render captures | `design/macos-ui-2026-11-02/` (English and RTL layout) |
| `./test.sh release` | **PASS** — 33 tests |
| Full repository matrix | **PASS** — Python/seed/extension/browser/release/Mac/iOS |

One full Mac run hung transiently in an Xcode render test after the UI shell
change. The isolated test passed (0.57 s), and the full suite passed on rerun;
this is recorded rather than hidden.

## Not claimed

- No user study, screen-reader audit or measured usability result.
- No physical-Mac visual review of the new shell.
- The Arabic render captures are mixed because the test harness sets SwiftUI's
  locale, not the app bundle's language; a real in-app Arabic launch localizes
  the dynamic strings after restart.
- No change to protection policy, lock rules, filter behaviour or consent text
  in this pass.
