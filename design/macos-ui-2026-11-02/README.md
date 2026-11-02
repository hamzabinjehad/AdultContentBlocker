# Mac UI/UX pass — 2026-11-02

Captures from the `OnboardingTests` render harness after the 1.1.7 UI pass.
They are layout evidence, not a pixel-perfect screen recording: native controls
are composited outside `cacheDisplay`, which is why the selected sidebar row
appears as a black rectangle in some captures.

- `01-overview.png` — the new sidebar status footer, Overview primary action and
  the lock shortcut in the window toolbar.
- `02-lock.png` — the Lock page with the authority warning slot and the
  status footer.
- `03-overview-arabic.png`, `04-lock-arabic.png` — RTL layout. Some dynamic
  `String(localized:)` strings stay in the process language because the render
  harness sets SwiftUI's locale environment, not the app bundle's language.
  A real Arabic launch after the in-app language restart localizes them.

The full Mac suite (508 tests), including these render tests and the 561-entry
Arabic catalog check, passes. No user study, screen-reader audit or physical
Mac visual review is claimed for this pass.
