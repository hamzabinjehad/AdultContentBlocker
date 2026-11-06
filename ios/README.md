# Hisn for iPhone and iPad

**نسخة أولية فعلية:** تطبيق موحّد للهاتف والآيباد، مستقل عن الماك، بواجهة
عربية وإنجليزية. يحوي ست إضافات لحجب مواقع Safari من قائمة حصن الأساسية،
ومسارًا اختياريًا لإعداد DNS عائلي. لا يعني ذلك حجب كل التطبيقات أو كل المحتوى.

The plan to finish the iPhone/iPad app, including which Apple-supported lock
level stops deletion and what each one cannot stop, is in
[the iOS end-goal plan](../docs/IOS_IMPROVEMENT_PLAN.md).

## 1.7.0 the pause rule

A fourth app-rule mode, chosen rather than imposed: the shield asks **Is this
important?** instead of only closing.

- **Pause first.** The shield's secondary button hands a request to Hisn —
  directly on iOS 26.5 and later, through a local notification before that. The
  shield never grants anything itself; a request older than ten minutes is
  ignored.
- **A ten-second breath.** The app presents a ring that must finish before
  **Open for N minutes** becomes available. Closing the screen spends nothing
  and the app stays shielded.
- **A bounded session.** One open buys the chosen minutes (1–30), and the rule
  allows 1–10 opens per day. The DeviceActivity monitor ends the session at its
  threshold; a session from another day, or older than any possible reading of
  its minutes, is re-shielded by the monitor's reconciliation.
- **Its own store and a mirrored day record.** A damaged budget record reads as
  unreadable and closes the day rather than handing out a fresh budget.
- The editor offers the mode with opens and session minutes, in English and
  Arabic; every other mode keeps the one-button shield.

Version 1.7.0, build 11. `./test.sh ios` passes **40 Python and 242 simulator
tests**; the new `PauseRuleTests` cover the budget, session bounds, handoff
freshness and damaged records. Real shielding and the shield's pause action
need a signed, entitled device; see [the 1.7.0 record](../docs/IOS_1.7.0.md).

## 1.6.0 the bypass guard and the Lock Center

Two changes; no Safari list, commitment, Screen Time, DNS, pause or shield
behaviour changed.

- **A browser and VPN guard.** A second, independent always-block app rule: pick
  the browsers and VPN apps that route around Safari blocking, and they stay
  shielded beside whatever mode the main rule uses for other apps — always
  block, a daily budget or quiet hours. The guard has its own mirrored record
  and its own `ManagedSettingsStore`; a running commitment freezes it like
  every saved rule; the DeviceActivity monitor's layer-loss check expects it
  and observes its own store; the overview rules card shows its count or
  **Not set**. It adds no new permission or entitlement. Apple combines the
  shields of Hisn's named stores on the device; Hisn reading its own records
  back is configuration evidence, not a blocking test, and revoking Screen
  Time access in Settings removes the guard with every other rule.
- **A Lock Center for removal protection.** Setup step 3's flat removal
  section becomes the ladder as its own object: the current level with its
  evidence (verified this session, confirmed by you, or no lock) and any
  stale-confirmation reason, then one row per rung — passcode vault, family
  guardian, supervised — each stating what it stops and the exit it keeps,
  with the guardian request and its consent sheet, the supervised-setup
  guidance, and the person's own confirmations with the optional deletion
  self-test. The footer states what no level stops: an Apple Account password
  can reset a Screen Time passcode, erasing the device removes every lock, and
  another device is never covered. **No app can make itself undeletable on an
  unsupervised iPhone; the ladder is the honest answer, and the Lock Center
  says so on the screen.**

Design research re-ran REA 6.8.0 `inspect-artifact` on the built 1.6.0 app
(evidence
`ev_33f65972e29c1415eeeb05ff3265387c087bb9caef117ca19f02ab494da6cf9e`); it
remains a static inventory with no native Swift analysis, and the design
decisions come from the recorded 1.5.0 audit's principles. Captures are in
[design/ios-ui-1.6.0](../design/ios-ui-1.6.0/README.md).

Version 1.6.0, build 10. `./test.sh ios` passes **39 Python and 221 simulator
tests**; no Apple team, Family Controls entitlement, signed build or
physical-device acceptance is claimed, and the guard's real shielding needs a
signed, entitled device. See [the 1.6.0 record](../docs/IOS_1.6.0.md).

## 1.5.0 a calmer overview and a guided setup

This is a UI/UX pass, not a rules change. The overview is now a card stack:
status, pause, one protection-plan card with `0 of 3` progress, a lock card and
a rules card. **Set a lock / Manage lock**, **Blocked sites** and **App limits**
open their editors directly, so changing a saved rule no longer means finding
the right Setup step first. The full commitment form moved off the status page.

Setup is three step cards with one progress card — turn on a blocking layer,
start the commitment, seal a passcode — with the detailed controls and the four
checks one level down. The app-limit editor shows always-block, daily budget and
quiet hours as three visible rows instead of a menu. Settings is grouped cards
for language, the Safari extension, gray screen, privacy and about.

The same pass reduced what a person has to read before acting: the Safari, Screen
Time, DNS, family, router, Safari-extension, app-limit, blocked-site, vault and
removal sections lead with a short line and keep the full scope one disclosure
down; Setup issues collapse behind their count; the overview plan uses short
states. The stale "Hisn 1–4" guide text is corrected to **Hisn 1–6** in both
languages.

The design research is in
[the REA audit](../docs/REA_AUDIT_IOS_2026-11-04.md), and the before/after
captures are in [design/ios-ui-1.5.0](../design/ios-ui-1.5.0/README.md).
No rule, lock, commitment, Screen Time, DNS, Safari, pause or shield behaviour
changed. The 1.4.0 shield, 1.3.0 pause and quiet hours, and the 1.2.0 saved-rule
freeze are retained.

Version 1.5.0, build 9. `./test.sh ios` passes **39 Python and 216 simulator
tests**; no Apple team, Family Controls entitlement, signed build or
physical-device acceptance is claimed. The renders cover every tab at phone and
iPad sizes in English and Arabic, phone light and dark, and the three Setup
drill-ins. See [the 1.5.0 record](../docs/IOS_1.5.0.md).

## 1.4.0 a shield that explains itself

The 1.3.0 shields were Apple's default screen: they did not say which Hisn rule
was holding the app, and they did not follow Hisn's language. This release adds
two Screen Time extensions:

- **HisnShieldConfig** returns the system-rendered shield for every app,
  category, site and web domain Hisn shields. It names the saved rule — always
  block, daily budget reached, or quiet hours — in the language chosen in Hisn.
  The copy is a small fixed English/Arabic table because the extension runs
  outside the app; the app writes its language choice into the App Group.
- **HisnShieldAction** answers the shield's one button with `.close`. It cannot
  unlock anything: the ManagedSettings shield remains applied until the rule
  ends or the person changes it in Hisn (outside a commitment). There is no
  quick-unlock action to copy from ScreenZen.
- A missing or unreadable rule, a budget shield without its reached-day marker,
  or an outside-window quiet-hours shield gets a generic honest message rather
  than a false claim about which rule applies.
- Both extensions carry the Family Controls entitlement; the configuration
  extension also carries the shared App Group and a privacy manifest. The
  privacy manifest is now included in eleven bundles (app, six blockers,
  monitor, scanner, and both shield extensions). Device builds embed both
  appexes; simulator test builds remove them before installation because some
  simulator runtimes reject the ManagedSettingsUI extension point.

Version 1.4.0, build 8. `./test.sh ios` passes **39 Python and 213 simulator
tests**; no Apple team, Family Controls entitlement, signed build or
physical-device acceptance is claimed. The system-rendered shield itself still
needs a signed build on a physical iPhone. See
[the 1.4.0 record](../docs/IOS_1.4.0.md).

## 1.3.0 pause, quiet hours and a calmer overview

Inspired by ScreenZen and comparable apps, and recorded in
[the iOS REA audit](../docs/REA_AUDIT_IOS_2026-11-03.md):

- **A private on-demand pause.** A 30-second breath (4 seconds in, 6 out, three
  times), five rotating reminders, four needs with offscreen tips, and a local
  tally of today's pauses (a day, a count and a time; a 30-second merge; a 9,999
  cap; no host, reason, history or network). It is reachable from the overview
  and a wind button on every tab, keeps working during a commitment, and changes
  no rule or permission.
- **Daily quiet hours for selected apps.** A third app-rule mode beside always
  block and the daily budget: one daily window with presets and custom times.
  A window that crosses midnight is registered as two same-day
  `DeviceActivitySchedule`s (evening → 23:59 and 00:00 → morning), so the
  undocumented overnight interval is not relied on. The app clears the shield
  only when both the device clock and, once read, Apple's time are outside the
  window. The rule is frozen during a commitment, like every other saved app
  rule. Health separates registered from stopped, and the copy says this is
  configuration evidence, not a blocking test.
- **One status, one action.** The overview hero shows a plain status, three
  layer chips and one primary action; the detailed setup issues sit behind a
  count and a disclosure. Setup keeps the full diagnostic view.
- The long browser/coverage explanations in the app-limit section moved behind
  **Browser and coverage limits**.

Version 1.3.0, build 7. `./test.sh ios` passes **38 Python and 206 simulator
tests**; no Apple team, entitlement, signed build or physical-device acceptance
is claimed. The quiet-hours window, DeviceActivity callbacks and real Screen
Time shields still need a signed build on a physical iPhone. See
[the 1.3.0 record](../docs/IOS_1.3.0.md).

## 1.2.0 protection lock

Inspired by the Android 0.9.0 protection lock, a running commitment now freezes
the saved rules Hisn owns:

- **My blocked sites:** the entry field and Add button are disabled, swipe-to-
  delete is unavailable, and the controller refuses a direct add. No parent-
  domain consolidation either.
- **App limits:** choosing apps, changing the mode or daily minutes, applying,
  strengthening always-block, and clearing are all disabled. The controller and
  the pure `permits` rule require full equality while committed.
- An active **or unreadable** commitment counts as locked; extending the
  commitment is the one rule action that remains.
- **Layer recovery is still available.** The Screen Time filter cannot be
  turned off inside Hisn, but it can be enabled or restored after iOS revokes
  access. DNS setup and the Safari rule reload remain available because they add
  or repair layers rather than change the frozen lists. iOS Settings switches
  remain outside the app's control.
- The commitment section says **Protection lock running** and names the frozen
  rules in English and Arabic.

This is an app-level rule lock, not Android's device-owner lock. The passcode
vault and supervised profile remain the removal-protection paths. Version 1.2.0,
build 6. `./test.sh ios` passes 185 simulator tests; no Apple team, entitlement,
signed build or physical-device acceptance is claimed. See
[the 1.2.0 record](../docs/IOS_1.2.0.md).

## 1.1.4 coverage pass

- **Six Safari blockers:** the four 40,000-rule parts became six, raising the
  bundled capacity from 160,000 to 240,000 domains. Seed v5 carries 239,885,
  including every high-confidence adult domain that two permissively licensed
  lists agree on. Each part is still 40,000 rules; no per-blocker limit was
  raised without a device measurement. See the
  [coverage pass record](../docs/COVERAGE_PASS_2026-10-30.md).
- **Privacy manifest:** `Hisn/PrivacyInfo.xcprivacy` is included in the app,
  all six Safari blockers, `HisnActivityMonitor` and `HisnText`. It declares no
  tracking and no collected data, with required reasons for app-only and
  app-group `UserDefaults` and for the monotonic boot clock. `Info.plist`
  declares `ITSAppUsesNonExemptEncryption` false.
- **Signed list age:** `prepare_rules.py` copies the signed seed's `built_at`
  into `rules-metadata.json`; the list section shows **Built** and warns after
  90 days that a newer signed build is the only Safari list update. There is no
  runtime list download.
- **Settings:** app version/build, plus privacy-policy and support links.
- Version 1.1.4, build 5. Simulator-tested only; no Apple team, entitlement or
  physical-device acceptance is claimed. See the
  [coverage pass record](../docs/COVERAGE_PASS_2026-10-30.md) and
  [the 1.1.3 history](../docs/IOS_1.1.3.md).

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
  The overview leads with one status, three layer chips and one next action; the
  detailed checks are one disclosure below.
- An on-demand **pause**: a 30-second guided breath, five rotating reminders,
  four needs with offscreen tips, and a local day/count/time tally. It changes
  no rule, works during a commitment and never sends the tally anywhere.
- Selected-app rules can **always block**, share a **daily usage budget**, or
  apply **daily quiet hours** with presets and custom times. Quiet hours crossing
  midnight are two same-day DeviceActivity schedules; the shield clears only
  when both the device clock and, once read, Apple's time are outside the
  window. Rules are frozen during a commitment. This is configuration evidence,
  not a blocking test.
- A custom system **shield** (`HisnShieldConfig` and `HisnShieldAction`) explains
  which saved rule is holding the app or site, in English or Arabic, and has no
  unlock action. It needs the Family Controls entitlement on both extensions and
  a signed build to test.
- Six Safari content blockers, each capped at 40,000 rules. The signed core
  seed is fully included, never silently truncated. The current seed has
  239,885 domains; increasing capacity requires explicit additional targets.
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

1. Set your Apple development team on the app, all six Safari domain targets,
   `HisnActivityMonitor`, `HisnText`, `HisnShieldConfig` and `HisnShieldAction`.
   Keep extension bundle IDs prefixed by the app ID. If changing the app ID,
   also change `shared/apple/MobileProtectionPolicy.swift` and the generator.
2. Enable the app's **Network Extensions → DNS Settings** signing capability.
   The supplied entitlement is a request, not a grant: your provisioning profile
   must actually include `dns-settings`.
3. Enable **Family Controls** on the app, `HisnActivityMonitor`,
   `HisnShieldConfig` and `HisnShieldAction`. Each provisioning profile must
   include `com.apple.developer.family-controls`; the shield configuration
   extension also needs the shared App Group. Distribution requires Apple's
   approval for the entitlement; adding the plist key is not approval.
4. Run on your iPhone/iPad from Xcode. UI and WebKit rule tests can run in a
   simulator without purchasing a developer membership. The simulator does
   **not** verify real-device DNS activation.
5. In Safari settings, enable **Hisn 1**, **Hisn 2**, **Hisn 3**, **Hisn 4**.
   Reload the rules from Hisn, then return to the protection tab and check state.
   To add text checking, separately enable **Hisn Text**, grant access to each
   wanted website, reload existing tabs and test profiles/private browsing.
   The existing three-layer setup assessment does not certify these permissions.
   Open Hisn Text's Safari toolbar panel or extension options to opt into visual
   checking. These settings are stored in Safari, not synchronized with or
   locked by the native app commitment.
6. Optional: choose **Prepare family DNS**, accept Apple's permission dialog,
   then explicitly select **Hisn Family DNS** in system DNS settings. Return to
   Hisn; a saved-but-disabled configuration remains visibly incomplete.
7. Optional: authorize Hisn's personal Screen Time adult-site filter. Hisn
   applies it only after explicit consent and reads its configured state back;
   this is not proof of blocked traffic. Test ordinary browsing and filtering
   separately on each real device before relying on protection.
8. Verify the custom shield: apply an always-block or quiet-hours rule to a
   disposable app, open it, and check Hisn's title/subtitle in the language
   chosen in Hisn. Tap the single button; the app must remain shielded. Then
   revoke Family Controls access and confirm iOS removes the shields. None of
   this can be verified in the Simulator.

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

The six Safari domain blockers do not read page text. Hisn Text separately
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

The iPhone/iPad Setup page offers Apple's private activity picker, always-on
shields, a combined daily usage budget (15–240 minutes), and daily quiet hours
for selected apps, categories and websites. A `HisnActivityMonitor` extension
receives Apple's threshold and interval callbacks and applies the shield. This
is not a whole-device lock or a meter for all network traffic.

Quiet hours are one daily window, with presets (late night, evening, work
hours) or custom From/Until times. A window that crosses midnight is registered
as two same-day `DeviceActivitySchedule`s: evening start to 23:59, and 00:00 to
the morning end. The evening end deliberately keeps the shield; the morning end
clears it, so the midnight boundary has no gap. The app also reconciles the
shield with the device clock and, once read, Apple's server time, clearing only
when both are outside the window. Apple rejects intervals under 15 minutes, so
each split leg must meet that minimum; the editor explains an invalid window
instead of rounding it. Health says whether the window is registered, not
whether blocking was observed.

Hisn's own system shield (`HisnShieldConfig` and `HisnShieldAction`) names the
saved rule — always block, budget reached or quiet hours — in the language
chosen in Hisn, and shows one button that only closes the shield view. It cannot
unlock anything. A missing or unreadable rule, a budget shield without its
reached-day marker, or an outside-window quiet-hours shield shows a generic
message rather than a false claim. Both extensions need the Family Controls
entitlement; the configuration extension also needs the shared App Group. The
Simulator cannot render or verify the system shield.

The app and monitor must both have Family Controls and the same App Group
(`group.app.hisn.mobile`) in their signed provisioning profiles. Apple distribution
approval is needed for each appropriate target. Simulator registration/build
tests do not establish real enforcement or callback reliability.

For a category-based checklist of apps to consider, including what each block
costs and what must stay accessible, see
[apps to consider blocking](../docs/APPS_TO_CONSIDER.md). Apple's picker returns
opaque tokens, so the app cannot preselect or name the apps from a bundled list.

During a commitment, including 90 days, the saved app rule is fully frozen:
selections, mode, daily minutes and the quiet-hours window cannot change.
Always-block cannot become a daily budget, and a budget cannot become quiet
hours. On iOS 16–17.3 a running daily budget cannot be edited during a
commitment because restarting its monitor may reset counted activity. On iOS
17.4+ the event includes past activity. The always-block and quiet-hours shields
are restored on authorized relaunch. The app does not automatically restart
missing budget monitoring or pretend a quiet-hours window is registered. Rules
remain until removed outside the commitment.

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

The 39 Python tests cover signed-input rejection, tampering, capacity,
sorted/unique domains, infrastructure protection/CDN tenants, complete shard
splitting, authority boundary fixtures, the shield extension point and
privacy-manifest membership, and Arabic/English key parity. The 213 hosted iOS
tests cover bundled integrity/counts, DNS configuration classification, partial
Safari state, WebKit compilation, layouts at phone/tablet widths, the pause and
quiet-hours rules, and the shield language/reason policy. Device signing and
real Safari/DNS/Screen Time shield behavior still require a provisioned physical
device.
# Current protection setup checks

Overview and Setup now show a live, non-persisted configuration assessment.
It requires nonempty bundled Safari parts with matching hashes, counts and
versions, all six Safari blockers enabled, Hisn Screen Time readback, and
matching enabled Family DNS before reporting all three layer configurations
verified. A saved-but-disabled DNS configuration, revoked permission, failed
reload, empty rules or unreadable commitment/history remains incomplete.
These checks do not activate settings silently or establish device-wide
blocking, all-content coverage, VPN resistance or app-removal prevention.
