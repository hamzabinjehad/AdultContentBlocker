# REA design audit: ScreenZen, comparable iOS apps and Hisn, 3 November 2026

This is a **design-research record**, not a security assessment, a bug count or a
claim about another product’s behaviour. It records what REA could observe
statically, what web research showed, what was implemented in Hisn iPhone/iPad
1.3.0, and what remains unknown.

## Tool and method

- [REA](https://github.com/morluto/rea) 6.4.0 was already installed in a
  temporary directory from npm with installation scripts disabled
  (`npm install --ignore-scripts`). No MCP server was registered, no editor or
  agent configuration was changed, no native disassembler was installed, and no
  inspected application code was executed.
- The provider-free commands used for this pass were `inspect-artifact` on the
  installed `/Applications/ScreenZen.app` and on the built
  `HisnMobile.app` from the tested simulator build.
- The existing Mac audit’s `capture-native-ui-scenario`,
  `analyze-javascript-application` and screenshot evidence were read for design
  vocabulary only; they are not re-presented here as new observations.
- Web research covered ScreenZen’s App Store listing and independent reviews,
  plus comparisons of Opal, one sec and Jomo. Those pages describe product
  behaviour; they are not runtime evidence of any particular build.
- Third-party UI captures and strings remain temporary engineering artifacts
  and are not redistributed. No ScreenZen code, assets, visual identity or copy
  was reused.

## Evidence

| Subject | REA operation | Evidence | Observed digest |
| --- | --- | --- | --- |
| `/Applications/ScreenZen.app` (2.0.0, build 63) | `inspect-artifact` | `ev_914842e780b424b338ac5ad746902664b5cd4e488bd8d696f4aae8e63e053cab` | SHA-256 `3d71a3666b43ba400dc05e62d772c7a5f85eea06234e750b42c7d698fe8c7547` |
| Built `HisnMobile.app` 1.3.0 (7) | `inspect-artifact` | `ev_2d8091880a7c8bb83c2a3e93971034c5063c036ba9876ac9aec8a5507de8ce1c` | SHA-256 `b80c20ad1ab78bb1d2aba38021182cc36cb45e421c5643017f4da1eb327eb516` |
| ScreenZen Firefox extension | `analyze-javascript-application` (Mac audit) | `ev_ac7da350505820ff98ea6c11c30620f8e56b893c52fb4b060184dbb5bea240ff` | 5 source files, 14 graph nodes, 21 relationships |
| ScreenZen window | `capture-native-ui-scenario` (Mac audit) | `ev_7e5597cf9f1d78b8b5ee4b52d5af7f40b6c771d42a40abb5ebac3ffd7372a29d` | 48 accessibility nodes plus screenshot |
| ScreenZen user-facing strings | Static string inventory (Mac audit) | `/tmp/hisn-work/screenzen-strings.txt` | product vocabulary, not runtime proof |

The ScreenZen digest matches the Mac audit’s installed artifact, so this is the
same observed build rather than a newer copy.

### What REA could not establish

- Native Swift/SwiftUI semantics, data flow, or enforcement behaviour. There was
  no native analysis provider; this was an inventory and a static string/UI
  read, not decompilation.
- Whether ScreenZen’s blocking, pause, time-window or unlock paths work as
  designed, on which OS versions, or under which account.
- Any iOS-specific ScreenZen behaviour. The observed window and strings are from
  the Mac app; the iOS app is a separate product with different APIs.
- A complete product model. One window and a string inventory do not prove the
  absence or presence of a feature.

## What the research shows

The observations below are specific to ScreenZen 2.0.0 build 63 on this Mac and
to the public descriptions of comparable apps:

- **Pause is the product, not a settings sub-page.** The observed vocabulary
  includes a breathing exercise, intentional reason, daily opens goal, pause
  groups, interventions and a local pause counter. Independent reviews describe
  the pause as the core mechanism, with strict blocks layered on top.
- **Time windows are first-class.** “Add a Time Window”, “Block window”, “Cannot
  be opened until the window ends” and separate Limit/Full-block windows appear
  in the inventory. This is the clearest functional gap in Hisn’s app limits.
- **The dashboard is calm and card-based.** A status card leads; rules are
  compact rows; advanced detail sits behind the main flow. This matches Hisn’s
  own 1.4.0 extension UI direction.
- **Settings can be locked and progress is local.** ScreenZen has a settings
  passcode, daily opens and streaks; other apps (Opal, one sec, Jomo) use
  schedules, friction, strict modes and local progress. Hisn already has a
  commitment lock and deliberately does not copy daily opens as a protection
  claim.

Sources consulted:

- [ScreenZen on the App Store](https://apps.apple.com/us/app/screenzen-screen-time-control/id1541027222)
- [Screen Zen: a free screen-time control app (MakeUseOf)](https://www.makeuseof.com/app-to-stop-phone-addiction-screenzen/)
- [Screen Time Index: app blocker comparison](https://screentimeindex.com/posts/screen-time-app-comparison/)
- [Screen Time Index: Jomo alternatives](https://screentimeindex.com/posts/jomo-app-alternatives/)
- [one sec / Opal / Jomo comparison](https://unstar.app/th/blog/opal-forest-freedom-one-sec-jomo-screen-time-apps-ranked-2026)
- [Apple: DeviceActivitySchedule](https://developer.apple.com/documentation/deviceactivity/deviceactivityschedule)
- [Apple: DeviceActivityMonitor](https://developer.apple.com/documentation/deviceactivity/deviceactivitymonitor)
- [Apple: ManagedSettings shield](https://developer.apple.com/documentation/managedsettings/shieldsettings)
- [Reported midnight/overnight scheduling pitfalls (DEV)](https://dev.to/ayumu_kitabayashi_8e0de96/5-timing-bugs-i-hit-building-timed-blocks-with-screen-times-deviceactivity-ios-5dnc)
- [Apple Developer Forums: DeviceActivitySchedule intervals](https://developer.apple.com/forums/thread/729841)

## What was implemented in Hisn 1.3.0

| Borrowed idea | Hisn implementation | What changed |
| --- | --- | --- |
| A calm, closeable pause at the moment of an urge | A full-screen 30-second breath (4 in / 6 out × 3), five rotating reminders, four needs with offscreen tips, and a local day/count/time tally with a 30-second merge and a 9,999 cap | `ios/Hisn/Pause.swift`, overview card, navigation-bar wind button, full-screen presentation; available during a commitment; no rule, permission or network change |
| Time windows as a first-class limit | A third app-rule mode: one daily quiet-hours window, presets and custom times, split at midnight into two same-day DeviceActivity schedules, both-clock shield reconciliation, registered/stopped health | `AppUsageConfiguration`, `AppUsageController`, `ActivityMonitor`, `AppUsageSection`; frozen during a commitment; 15-minute validation |
| One status card and a single next action | Overview hero with plain status, three layer chips, one primary action and a collapsed issue list | `ProtectionHero.swift`, `MobileRootView.swift`; setup keeps the full diagnostic view |
| Less copy before the controls | Browser/coverage explanations moved behind a disclosure in the app-limit section | `AppUsageSection.swift`, English/Arabic copy |

## What was deliberately not copied

- **Halo proximity blocking.** It needs Bluetooth, a signed-in account and cloud
  state. That conflicts with Hisn’s on-device, no-account design.
- **Quick disable / time-boxed unblocking.** Hisn’s commitment model makes
  weakening wait; a one-tap “disable for 15 minutes” would undermine it.
- **Daily opens as a protection claim or a streak.** A count of opens is not a
  measure of protection or recovery. Hisn keeps a pause count only and does not
  advertise a score.
- **A cloud dashboard, account or cross-device state.** The pause tally stays in
  `UserDefaults` on this device and is never sent anywhere. Quiet hours use only
  Apple’s Screen Time stores.
- **ScreenZen’s visual identity or copy.** Hisn uses its own SwiftUI components,
  colours and English/Arabic strings.

## Limits and follow-up

- This is one installed Mac artifact, its strings and one window, plus public
  descriptions of other apps. Do not cite it as a competitive benchmark or as
  evidence that ScreenZen is better or worse at blocking.
- The implementation is covered by policy, controller and render tests, not by a
  user study, a screen-reader audit or a physical-device review. Those remain
  owner actions.
- DeviceActivity callbacks, the midnight split, both-clock reconciliation and
  Screen Time shield enforcement still need a signed, entitled build on a real
  iPhone. The simulator cannot prove them.
- No native analysis (with a configured provider) was run. If a future audit
  needs call graphs, install a provider deliberately and record its side effects;
  do not treat this inventory as a substitute.
