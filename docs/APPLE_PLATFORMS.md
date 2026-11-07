# Apple platform structure

| Platform | Location | Implemented protection | State |
|---|---|---|---|
| macOS | `macos/` | Existing socket filter, browser integration, network setup | Existing app; its prior signing/deployment path is unchanged |
| iOS | `ios/` | Native app, six Safari domain blockers, a device-wide DNS filter Network Extension, Screen Time content filtering and selected-app restrictions | Development version; physical signing/activation still required |
| iPadOS | `ipados/` + the universal `ios/` target | Same layers and seed as iPhone | Shared target, not duplicate source |
| Shared Apple code | `shared/apple/` | Platform-neutral mobile configuration/status policy | No macOS daemon/XPC dependencies |
| Shared content | `seed/`, `blocklist/` | Signed core domain seed and infrastructure safety policy | One source of truth; no independently edited mobile list |

Mobile UI and state follow native SwiftUI patterns. A portable policy file can
be shared without pretending all Apple platforms have the same privileges.
Existing Mac filtering is not moved or weakened by the mobile app.

The mobile app works alone. It does not yet sync settings with the Mac. Its
separately enabled Hisn Text Safari Web Extension carries the shared JavaScript
text scanner; website permission and actual phone behavior need separate checks.
It does not inspect native-app content. Selected-app shielding uses Apple's private
picker and requires Family Controls authorization. The DNS filter is a Network
Extension DNS proxy that answers Hisn's signed core list, adult TLDs, hostname
terms and saved sites on the device and forwards allowed names to Cloudflare
Families; it covers system DNS only, and Safari carries the same list as
content-blocker rules. It is not advertised as an all-app content filter, a
full tunnel or non-removable.

Mobile now offers explicit Family Controls child authorization for guardian-led
removal protection. This is conditional on Apple's entitlement, a real child
account and guardian approval; it does not lock Safari/DNS settings. Individual
approval is not treated as removal protection. See the mobile README for
recovery, provisioning and physical-device acceptance checks.

The 1.8.0 direct build packages the tested app as an IPA outside the App
Store; see [IOS_DIRECT.md](IOS_DIRECT.md). Apple's entitlement checks
(Network Extensions, Family Controls, App Groups) still apply.

iOS 1.2.0 adds a protection lock in the same spirit as Android 0.9.0: a
commitment freezes the saved blocked-site list and app-limit rules in both
directions, while protection layers may still be added or restored and the
commitment may be extended. It is not a device-owner lock; vault and supervised
profile remain the deletion-resistance paths.

For building, signing and testing, start with [`../ios/README.md`](../ios/README.md).

## Coordinated development

Improve macOS, iOS and iPadOS together without removing existing Mac features.
Share the product goal and portable policy where appropriate, but use each
platform's supported enforcement mechanisms. Do not claim identical privileges
or automatic cross-device synchronization. If a feature cannot be implemented
on a platform, document the limitation and its supported alternative.

The current protection-plan improvement adds a readiness summary and explicit
acknowledgment of commitment limits on both Mac and mobile. Their counts are
platform-specific configuration/status checks, not comparable protection scores
or proof of complete blocking. A failed Safari rule reload is not a ready mobile
layer. Changing the duration or lock mode requires acknowledgment again. No
router settings are changed by this flow.

Adult self-control is now the primary mobile flow: a fixed commitment and an
optional personal Screen Time web filter. Mac locks now offer a fixed commitment
horizon too. See [the cross-platform behavior and limits](SELF_CONTROL_COMMITMENT.md).
