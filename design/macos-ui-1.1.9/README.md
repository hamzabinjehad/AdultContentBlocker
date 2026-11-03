# Mac 1.1.9 — guard and status-first overview captures

Deterministic SwiftUI renders from the macOS test host, not physical-display
screenshots. English and Arabic, dark appearance, 880×1000 for Overview and
460×320 for the guard card. They are not a user study or a screen-reader audit.

| File | State |
| --- | --- |
| `overview-en.png` | Overview with layer chips and the Layer details disclosure (expanded because a layer needs attention) |
| `overview-ar.png` | Arabic RTL equivalent |
| `guard-en.png` | Browser-guard intervention card, English, 42 seconds left |
| `guard-ar.png` | Arabic RTL equivalent; the long reason text is generated from the app bundle at runtime, so the test render shows the English bundle string |

The guard card's long reason comes from `BrowserGuard.message`, which uses
`String(localized:)`; SwiftUI's test locale does not switch the app bundle, so
the Arabic render is mixed. The real Arabic app bundle is checked separately by
`macos/check_localization.py`.
