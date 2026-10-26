# Hisn for iPhone and iPad

**نسخة أولية فعلية:** تطبيق موحّد للهاتف والآيباد، مستقل عن الماك، بواجهة
عربية وإنجليزية. يحوي أربع إضافات لحجب مواقع Safari من قائمة حصن الأساسية،
ومسارًا اختياريًا لإعداد DNS عائلي. لا يعني ذلك حجب كل التطبيقات أو كل المحتوى.

The plan to finish the iPhone/iPad app, including which Apple-supported lock
level stops deletion and what each one cannot stop, is in
[the iOS end-goal plan](../docs/IOS_IMPROVEMENT_PLAN.md).

## Open and run

For the separately labeled free-development Safari-only candidate, see
[Hisn Safari limited edition](HisnSafariOnly/README.md). Its installed domain-only
configuration has no app shields, DNS, text/visual scanner, enforced commitment
or deletion protection. It does not replace this full app's entitlements.

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
- A separate **Hisn Text** Safari Web Extension uses the shared on-device text
  scorer and scanner. It hides detected X/Twitter posts individually and uses
  page-level checking on other sites. It must be enabled and granted website
  access separately; its access is explicitly unverified in Hisn's guide.
  Its own settings add opt-in, experimental local visual checking and optional
  blurring of media with detected faces (all or estimated female/male faces).
  Normal videos stay available by default. The whole matching image/video is
  blurred; this is not whole-body recognition or an identity determination.
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

1. Set your Apple development team on the app, all four Safari domain targets,
   and the separate Hisn Text target.
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
   To add text checking, separately enable **Hisn Text**, grant access to each
   wanted website, reload existing tabs and test profiles/private browsing.
   The existing three-layer setup assessment does not certify these permissions.
   Open Hisn Text's Safari toolbar panel or extension options to opt into visual
   checking. These settings are stored in Safari, not synchronized with or
   locked by the native app commitment.
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

The four Safari domain blockers do not read page text. Hisn Text separately
checks permitted Safari pages. Its optional visual models inspect readable
images and bounded, sampled video frames locally. They cannot inspect every
frame, protected media, unreadable cross-origin pixels, other apps or every
feed layout; a missing or failed verdict never means the media was safe.
Content can appear before a verdict, and classification/face estimates can be
wrong. Models and license notices are bundled; media is not uploaded or
persisted. For optional native-app blocking while
keeping its website available, select only the app, not its domain or an
overlapping category. See [browser-first setup and tests](../docs/BROWSER_FIRST_PROTECTION.md).
DNS can be bypassed by alternate DNS, tunnels, Private Relay or
configuration removal; enabling it is not VPN-proof enforcement. There is no
macOS system-extension installer on iOS, no hidden router setup, no phone/Mac
settings synchronization, and no promise of undeletable or 100% protection.

### Safari visual adapter verification

Safari uses a Manifest V3 nonpersistent background page, with the shared local
engine in its document context; it does not request Chrome's offscreen or
native-messaging permissions. The package build verifies the signed text terms
and a fixed allowlist of hashed visual-model/runtime assets before writing any
generated resources. Visual build metadata detects resource drift; it is not a
separately signed model update or an accuracy guarantee. No executable code or
model weights are fetched from the Internet at runtime.

The Python package tests and JavaScriptCore message-contract tests cover staged
resources, sender ownership, local preference validation and unavailable states.
They do **not** demonstrate real iPhone/iPad Safari activation, visual-model
performance, website permission coverage, battery use or classification
accuracy. Those need a provisioned physical-device test before release.

**العربية:** في إعدادات إضافة «Hisn Text» يمكن تفعيل الفحص البصري التجريبي
وتمويه الوسائط التي تحتوي على وجوه مكتشفة. هذه الإعدادات مستقلة عن التزام
التطبيق ولا تمنع تعطيل الإضافة. كشف الوجوه ليس كشفًا لكامل الجسم، وقد يخطئ
التصنيف أو تقدير المظهر. الوسائط غير القابلة للقراءة لا تُعد آمنة. لم يتم
التحقق هنا من تشغيل النماذج على آيفون أو آيباد فعلي.

## Passcode vault (no second person needed)

Setup offers a **Passcode vault**: Hisn makes a random four-digit Screen Time
passcode, shows it for 15 minutes so the person can set it (Lock Screen Time
Settings, then Content & Privacy › Deleting Apps: Don't Allow), and then keeps
it hidden for 1, 7, 30 or 90 days. If the person really sets it as the Screen
Time passcode and sets Deleting Apps to Don't Allow, then until that date no app,
Hisn included, can be deleted and Screen Time cannot be changed, on an ordinary
unsupervised iPhone. Hisn cannot check that both steps were done, and the app
says so. The date can be extended but never shortened. The passcode follows
Dynamic Type, and shows dots whenever Hisn is not the active app, so the
app-switcher snapshot does not keep it.

The passcode lives only in this device's Keychain (`ThisDeviceOnly`, never in
UserDefaults, which backups include). Showing it again requires the date to
have passed on the device clock **and** on Apple's server time (the `Date`
header of an HTTPS request to apple.com), so moving the clock forward does not
open it. Damaged storage keeps it sealed and blocks making a new one over it.

Limits, shown in the app: the adult who set a Screen Time passcode can reset
it with their Apple Account password (with Family Sharing, the organizer's);
erasing the device removes everything. Unit tests cover generation, sealing,
reveal rules and storage damage; the Settings steps need a real device.

## Removal-resistant setup (supervised devices)

To stop the app and its protections being deleted or switched off, supervise
the device with Apple Configurator and install the profile from
`profile/make_ios_profile.py`. It blocks app deletion, erasing, other profiles
and VPNs, and keeps family DNS on; Apple honours these keys only on supervised
devices. Steps and limits: [docs/IPHONE_SUPERVISED.md](../docs/IPHONE_SUPERVISED.md).

## My blocked sites

Setup includes **My blocked sites**: up to 49 sites the person adds by hand,
applied through Hisn's own Screen Time adult-content filter
(`WebContentSettings.FilterPolicy.auto(_:except:)`). Apple documents up to 50
extra domains for that filter; developers report blocking silently stops past
49, so Hisn stays at 49. Each site covers its subdomains, and adding a parent
domain frees the subdomain entries it now covers. Entries use the same
canonical ASCII host rules as the Mac app and the browser extension; enter an
internationalized name in its `xn--` form.

The list is saved in mirrored app-local records. Damaged records are reported
and never replaced with an empty list. While a commitment runs, sites can be
added but not removed. An unreadable commitment counts as running. Saving a site
never turns on Screen Time by itself. The list is applied only after the person
has enabled personal Screen Time protection, and the app reads the filter back
to show **Applied**. That is configuration readback, not a blocking test. The
Simulator cannot apply Screen Time; unit tests cover the list rules, storage and
commitment behaviour only.

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

## Status, accessibility and languages

Screen Time status distinguishes access turned off in Settings from access never
requested, declined, or allowed with Hisn's filter off. When access is turned
off after Hisn's filter was confirmed, the readiness row, the commitment section,
My blocked sites and the setup issues say that iOS removed the filter, the sites
and the app limits, and that only the person can turn access back on; Hisn
requests nothing by itself. The Simulator reports this layer as uncheckable.

Warning and success text uses `HisnPalette` colours, which keep at least 4.5:1
contrast on every grouped list background in light and dark mode (the system
orange and teal are about 2:1 on white). Readiness rows and the blocked-site field
stack at accessibility text sizes, the Safari detail reads "3 of 4 parts on", and
direction arrows follow the reading direction in Arabic. On iPad every tab uses
the same 860 pt readable column.

`ios/test_localization.py` checks, without Xcode, that English and Arabic have the
same keys and placeholders, that every Arabic value is written in Arabic, and that
every key the SwiftUI code looks up exists in both files. `AccessibilityTests`
checks the contrast, the passcode masking, the Screen Time states and renders all
three tabs on iPhone and both iPad orientations at accessibility sizes in both
languages. What VoiceOver actually announces on a device is not tested.

## Validation

Python tests cover signed-input rejection, tampering, capacity, sorted/unique
domains, infrastructure protection/CDN tenants, complete shard splitting,
authority boundary fixtures and Arabic/English key parity. Hosted iOS tests
cover actual bundled integrity/counts, DNS configuration classification, partial
Safari state, WebKit compilation and layouts at phone/tablet widths. Device
signing and real Safari/DNS behavior still require a provisioned physical device.
# Current protection setup checks

Overview and Setup now show a live, non-persisted configuration assessment.
It requires nonempty bundled Safari parts with matching hashes, counts and
versions, all four Safari blockers enabled, Hisn Screen Time readback, and
matching enabled Family DNS before reporting all three layer configurations
verified. A saved-but-disabled DNS configuration, revoked permission, failed
reload, empty rules or unreadable commitment/history remains incomplete.
These checks do not activate settings silently or establish device-wide
blocking, all-content coverage, VPN resistance or app-removal prevention.
