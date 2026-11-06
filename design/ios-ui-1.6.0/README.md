# iOS 1.6.0 — capture record

Layout evidence for the iPhone/iPad 1.6.0 pass, which adds the browser and
VPN guard and the removal-protection Lock Center. These are renders from the
hosted test harness (`UIRenderTests`, one point per pixel), not a user study,
a screen-reader audit or a pixel snapshot test.

This simulator has no Family Controls entitlement and no configured layers,
so the app honestly shows the layer checks as unavailable, the plan at 0 of 3
and no guard saved. Compare the layout, not the verdicts.

| File | State |
| --- | --- |
| `after-overview.png` | 1.6.0 iPhone, English, light: the rules card now carries a third row for the browser and VPN guard |
| `after-overview-ar.png` | The same tab in RTL Arabic |
| `after-overview-dark.png` | Dark mode |
| `after-overview-ipad.png` | iPad render of the same tab |
| `after-setup-rules-guard.png` | The app-limits drill-in (Setup step 2): the guard section between the app rule and the commitment — status, Choose browsers and VPN apps, the frozen-rule note and the merge disclosure |
| `after-setup-vault-lock-center.png` | The passcode-vault drill-in (Setup step 3): the vault section above the new Lock Center — current level with its evidence, one row per ladder rung with what it stops and the exit it keeps, the guardian request and the person's own confirmations |

Design research for this pass re-ran REA 6.8.0 `inspect-artifact` on the built
1.6.0 app; the record is [the 1.6.0 release note](../../docs/IOS_1.6.0.md).
Third-party captures stay in temporary storage and are not redistributed.
