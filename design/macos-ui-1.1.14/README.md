# Mac 1.1.14 — first-run and private windows

Rendered from the built app by `OnboardingTests` at 640 pt (welcome and
private-window sheet) and 760 pt (Overview, the window minimum), dark
appearance. These are layout captures, not a user study, screen-reader audit or
proof that a browser accepted a policy.

| File | State |
| --- | --- |
| `after-welcome-en.png` | the first screen: three layers, one line each, one Continue button |
| `after-welcome-ar.png` | the same screen rendered with the Arabic localization and RTL direction |
| `after-overview-min-width-en.png` | the Overview at 760 pt: short status, four layer tiles in two columns, **Prevent private browsing…** as the primary action when the gap exists, Layer details collapsed |
| `after-private-windows-sheet-en.png` | the consent sheet: Chrome and Helium open, one primary action, one **Not now**, and a disclosure naming browsers Hisn cannot control |
| `after-private-windows-sheet-ar.png` | the same sheet in Arabic, right-to-left |

The Overview capture is the real app state on this development Mac: hosts-file
blocklist ready, system filter not set up (ad-hoc build), browser extension not
connected in the test host, private windows open in Chrome and Helium. It is not
a mocked healthy machine.

Design research: ScreenZen 2.0.0's dashboard and menu bar were observed after a
reinstall from the official notarized feed. ScreenZen restored its onboarding
state from its account, so its first-run screens were not available; the
takeaway was the single-status-first layout and one decision per screen. No
ScreenZen code, assets, copy or data model was used. The implementation record
is [Mac 1.1.14](../../docs/MACOS_1.1.14.md).
