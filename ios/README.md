# Hisn for iPhone and iPad

**نسخة أولية فعلية:** تطبيق موحّد للهاتف والآيباد، مستقل عن الماك، بواجهة
عربية وإنجليزية. يحوي أربع إضافات لحجب مواقع Safari من قائمة حصن الأساسية،
ومسارًا اختياريًا لإعداد DNS عائلي. لا يعني ذلك حجب كل التطبيقات أو كل المحتوى.

## Open and run

Open `ios/HisnMobile.xcodeproj`, select the **HisnMobile** scheme, then choose an
iPhone or iPad simulator. Minimum supported OS: iOS/iPadOS 16.

Build prerequisites: Xcode with the iOS SDK, Python 3, and `cryptography>=42` in
the Python used by Xcode. Install dependencies into your normal development
environment; set `HISN_PYTHON=/absolute/path/to/python3` as an Xcode build setting
if its Python differs from the terminal's. The project itself is committed;
regenerate it after source-membership changes with:

```sh
python3 ios/generate_xcodeproj.py
./test.sh ios
```

`HISN_IOS_DESTINATION` can select a specific available simulator for the test
runner, for example `platform=iOS Simulator,name=iPhone 17 Pro`.

## What ships

- Native SwiftUI tabs for protection status, guided setup, privacy and language.
- Four Safari content blockers, each capped at 40,000 rules. The signed core
  seed is fully included, never silently truncated. The current seed has
  159,768 domains; increasing capacity requires explicit additional targets.
- Domain requests and descendant hostnames are matched with host boundaries.
  Safari applies rules without reporting visited sites to Hisn.
- Optional encrypted Cloudflare Family DNS configuration, including IPv4 and
  IPv6 resolver addresses. This uses **Cloudflare categories**, not Hisn's list.
- Actual DNS enabled-state readback and separate Safari state for each part.
  Each Safari part distinguishes unknown, disabled, enabled and last-reload
  failure. A failed reload stays visible across status refreshes until that
  part reloads successfully; enabled toggles alone cannot hide the failure.
  The overview links directly to setup, supports pull-to-refresh and shows
  the time of the last completed configuration check.
  Saving DNS does not count as enabling it; partial/unknown Safari state does
  not count as all parts enabled. These are configuration checks, not proof of
  filtering every website or every app.

The seed is verified during **every build**: Ed25519 signature, signed artifact
size/hash/count, canonical sorted unique domains, and the existing two-tier
infrastructure safety policy. Generated rules are signed as app resources;
each Safari handler also checks its bundled rules against generated SHA-256
metadata. That metadata is protected by Apple code signing, not a separately
signed update manifest. This version has no downloaded-list update path;
shipping a new seed requires rebuilding and redistributing the app.

## Activate on a real device

1. Set your Apple development team on the app and all four Safari targets.
   Keep extension bundle IDs prefixed by the app ID. If changing the app ID,
   also change `shared/apple/MobileProtectionPolicy.swift` and the generator.
2. Enable the app's **Network Extensions → DNS Settings** signing capability.
   The supplied entitlement is a request, not a grant: your provisioning profile
   must actually include `dns-settings`.
3. Run on your iPhone/iPad from Xcode. UI and WebKit rule tests can run in a
   simulator without purchasing a developer membership. The simulator does
   **not** verify real-device DNS activation.
4. In Safari settings, enable **Hisn 1**, **Hisn 2**, **Hisn 3**, **Hisn 4**.
   Reload the rules from Hisn, then return to the protection tab and check state.
5. Optional: choose **Prepare family DNS**, accept Apple's permission dialog,
   then explicitly select **Hisn Family DNS** in system DNS settings. Return to
   Hisn; a saved-but-disabled configuration remains visibly incomplete.
6. Optional: authorize Hisn's personal Screen Time adult-site filter. Hisn
   applies it only after explicit consent and reads its configured state back;
   this is not proof of blocked traffic. Test ordinary browsing and filtering
   separately on each real device before relying on protection.

Distribution through TestFlight/App Store requires your developer membership,
signing/provisioning and review. No distributable signed IPA or store upload is
produced by this development build. Do not ship the unsigned build as an
installable phone app. Existing artwork is reused for the mobile app icon.

## Coverage boundaries

For adult self-use, start with **My self-control commitment** and the separate
personal Screen Time consent action. This works without a partner. Fixed plans
last 1, 7, 30 or 90 days, persist across app relaunch, and cannot be cancelled or
shortened inside Hisn. A configured adult-content filter remains enabled after
the plan expires until the person chooses to turn it off. Guardian authorization
below is optional and only appropriate for genuine child-device use.

See [the shared self-control design](../docs/SELF_CONTROL_COMMITMENT.md) for
actual enforcement layers, clock/persistence limits and real-device tests.

Safari blocking does not read page text, scan images, filter non-Safari apps,
or shield apps. DNS can be bypassed by alternate DNS, tunnels, Private Relay or
configuration removal; enabling it is not VPN-proof enforcement. There is no
macOS system-extension installer on iOS, no hidden router setup, no phone/Mac
settings synchronization, and no promise of undeletable or 100% protection.

## Selected app restrictions

The iPhone/iPad Setup page now offers Apple's private activity picker, always-on
shields, and a combined daily usage budget (15–240 minutes) for selected apps,
categories and websites. A `HisnActivityMonitor` extension receives Apple's
threshold callbacks and shields the selection until the next daily interval
starts. This is not a whole-device lock or a meter for all network traffic.

The app and monitor must both have Family Controls and the same App Group
(`group.app.hisn.mobile`) in their signed provisioning profiles. Apple distribution
approval is needed for each appropriate target. Simulator registration/build
tests do not establish real enforcement or callback reliability.

During a commitment, including 90 days, selections can grow but cannot shrink;
daily limits can tighten but not increase, and always-block cannot become a daily
budget. On iOS 16–17.3 a running daily budget cannot be edited during commitment
because restarting its monitor may reset counted activity. On iOS 17.4+ the event
includes past activity. The always-block rule is restored on authorized relaunch.
The app does not automatically restart missing budget monitoring or pretend it
is registered. Rules remain until removed outside the commitment.

The optional app-domain exporter creates reviewed AdGuard Home domain rules only,
not an app/server discovery database or router installer. An explicit all-client
acknowledgement is required before sharing. Import must be performed and checked
on the external DNS server. It does not export a router time schedule.

macOS behavior is unchanged in this app/time-budget update, as requested.

## Guardian-authorized removal protection

The app now includes an explicit **Request guardian authorization** action using
Family Controls `.child` authorization only. A guardian in the child's Apple
Family Sharing group must consent on the child's device. Apple prevents removal
of the parental-control app and iCloud sign-out while child authorization is
active. There is no `.individual` fallback, automatic permission request,
in-app revocation shortcut, or restriction against deleting every other app.

The app entitlement includes `com.apple.developer.family-controls`. Configure
the Family Controls capability and matching provisioning in Xcode. Distribution
requires Apple's approval for this entitlement; adding the plist key does not
grant approval. Unsigned builds and Simulator cannot validate removal prevention.

Approval status does not identify authorization scope. The UI records a successful
child request only in memory and reads the system status again. Revocation clears
that evidence; after restart, approved access is shown as scope unknown, not as
proof that deletion is blocked. A successful request still requires real-device
verification. Guardians retain recovery through Apple's Screen Time settings;
device erasure and guardian revocation are not prevented.

Real-device acceptance: use a signed build with an entitled provisioning profile,
a child account and a consenting guardian in the same family. Approve the request,
check that the app-removal option is disallowed (do not uninstall), then verify
revocation updates the displayed status and reauthorization works. Relaunch must
display scope unknown, not a false verified lock. Verify Safari/DNS separately:
removal protection does not prevent changing their settings. Self-use guidance
offers a separate guardian-held Screen Time passcode; the app cannot inspect that
manual restriction. Menu names vary by OS version.

Selected-app shielding and combined daily budgets are implemented separately
from guardian removal protection. Managed-device enforcement remains a separate
deployment option, not an automatic app feature.

## Selected-rule persistence

Selected-app rules now use two versioned records in the App Group. A surviving
copy can retain rules after accidental loss of the other; readers never write
repairs over a potentially newer revision. Legacy valid rules remain readable.
Clearing rules writes a versioned tombstone, so a stale legacy copy cannot
resurrect a cleared selection. Corrupt, conflicting or unsupported records are
reported as invalid rather than silently replaced with an empty selection.

This is same-container redundancy, not Keychain protection or anti-uninstall
enforcement. Losing both records, OS permission revocation and device erasure
remain limits. Default-store readback is not a guarantee of durable disk writes.
The reached-day marker still uses the existing local-calendar storage. A full
revision-linked OS apply receipt and secure-mirror device validation remain
future work. The monitor leaves shields untouched when rule reads fail.

## Sources

Checked 2026-10-03:

- [Apple DNS Settings](https://developer.apple.com/documentation/networkextension/dns-settings)
- [Apple DNS enabled state](https://developer.apple.com/documentation/networkextension/nednssettingsmanager/isenabled)
- [Creating a Safari content blocker](https://developer.apple.com/documentation/safariservices/creating-a-content-blocker)
- [Safari content-blocker state and reload](https://developer.apple.com/documentation/safariservices/sfcontentblockermanager)
- [Cloudflare Families and encrypted DNS endpoints](https://developers.cloudflare.com/1.1.1.1/setup/)
- [Family Controls distribution entitlement](https://developer.apple.com/documentation/familycontrols/requesting-the-family-controls-entitlement)
- [Family Controls child authorization and removal restrictions](https://developer.apple.com/documentation/familycontrols)
- [Why individual authorization does not prevent deletion](https://developer.apple.com/videos/play/wwdc2022/110336/)

## Validation

Python tests cover signed-input rejection, tampering, capacity, sorted/unique
domains, infrastructure protection/CDN tenants, complete shard splitting,
authority boundary fixtures and Arabic/English key parity. Hosted iOS tests
cover actual bundled integrity/counts, DNS configuration classification, partial
Safari state, WebKit compilation and layouts at phone/tablet widths. Device
signing and real Safari/DNS behavior still require a provisioned physical device.
