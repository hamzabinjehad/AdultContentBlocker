# iOS 1.5.0 — before/after captures

Layout evidence for the iPhone/iPad 1.5.0 UI pass. These are not a user study,
a screen-reader audit or a pixel snapshot test.

The **iPhone before/after** images are real simulator screenshots from the
booted iPhone 17 (iOS 26.5) with the 1.4.0 and 1.5.0 development builds. The
**iPad pairs** are renders from the hosted test harness: the before images come
from the 1.4.0 `AccessibilityTests` run at 1024 × 1366 with the default text
size, and the after images come from the new `UIRenderTests` run at the same
size. The two harnesses use the same `UIHostingController` and
`drawHierarchy` path. iPad images are downscaled to 1800 px on the long side to
keep the repository small.

This simulator has no Family Controls entitlement and no configured layers, so
the app honestly shows the layer checks as unavailable and the plan at 0 of 3.
The before iPhone capture was taken while the first refresh was still running;
the after capture is after it settled. Compare the layout, not the verdict.

| File | State |
| --- | --- |
| `before-overview.png` | 1.4.0 iPhone: status hero, pause card, plan section, commitment and sites below |
| `after-overview.png` | 1.5.0 iPhone: status card, pause card, one plan card with progress, lock and rules cards below |
| `after-overview-dark.png` | 1.5.0 iPhone, dark mode |
| `before-overview-ipad.png` | 1.4.0 iPad render: hero, pause, plan section |
| `after-overview-ipad.png` | 1.5.0 iPad render: card stack with the plan, lock and rules visible |
| `before-setup-ipad.png` | 1.4.0 Setup: status card, plan, setup order, then every layer section in one list |
| `after-setup-ipad.png` | 1.5.0 Setup: status card with `0 of 3` progress, three step cards, checks card |
| `before-settings-ipad.png` | 1.4.0 Settings: flat form with long privacy and visual-check copy |
| `after-settings-ipad.png` | 1.5.0 Settings: language, Safari extension, gray screen, privacy and about cards |
| `after-overview-ar-ipad.png`, `after-setup-ar-ipad.png`, `after-settings-ar-ipad.png` | 1.5.0 RTL Arabic at the same size |
| `after-blocking-layers.png` | The blocking-layers drill-in: Safari, Screen Time, custom sites, DNS, family, router |
| `after-app-limits.png` | The app-limits drill-in: selected items, three visible modes and the commitment section below |
| `after-passcode-removal.png` | The passcode/removal drill-in |

The iPhone before image shows the actual app state on this Mac's simulator: the
layer checks are unavailable, the commitment is not running and no sites are
added. Nothing else on the desktop is included.

Design research for this pass used REA 6.8.0 `inspect-artifact` on ScreenZen
2.0.0 (build 63) and on the built 1.4.0 app; the full record is
[the REA audit](../../docs/REA_AUDIT_IOS_2026-11-04.md). Third-party captures
stay in temporary storage and are not redistributed.
