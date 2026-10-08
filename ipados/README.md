# Hisn for iPadOS

The iPad app is the **same universal target** as the iPhone app in
[`../ios/HisnMobile.xcodeproj`](../ios/HisnMobile.xcodeproj), not a divergent copy.
`TARGETED_DEVICE_FAMILY = 1,2` supports iPhone and iPad. Native SwiftUI lists,
forms, navigation and tabs adapt to tablet widths; portrait and landscape are
declared, including iPad upside-down portrait.

Setup, DNS entitlements and coverage limits are documented in
[`../ios/README.md`](../ios/README.md). A signed iPad build uses the same four
Safari extensions and the same verified seed. No Mac is needed after installing.

The self-control plan (1, 7, 30 or 90 days), personal Screen Time web filter and
optional guardian flow are shared with iPhone, not separately implemented.
See [cross-platform commitment behavior](../docs/SELF_CONTROL_COMMITMENT.md).

App selection, always-block shields, the shared daily usage budget and the
Device Activity monitor use the same implementation as iPhone. The monitor
requires the same signed App Group and Family Controls entitlement on iPad.

Tablet-width rendering is covered in hosted tests. Full iPad simulator/device
activation, rotation, split-screen, VoiceOver and actual Safari/DNS enforcement
must additionally be checked on that target before release. Do not equate an
iPhone simulator pass with physical iPad enforcement.
