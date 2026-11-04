# REA design audit: ScreenZen, comparable iOS apps and Hisn, 4 November 2026

This is a **design-research record**, not a security assessment, a benchmark or
a claim about another product's behaviour. It records what REA could observe
statically, what web research showed, what was implemented in Hisn iPhone/iPad
1.5.0, and what remains unknown.

## Tool and method

- REA 6.8.0 was installed from npm into a temporary directory from a portable
  Node 22.23.4 runtime, with installation scripts disabled
  (`npm install --ignore-scripts rea-agents@latest`). No MCP server was
  registered, no editor or agent configuration was changed, no native
  disassembler was installed, and no inspected application code was executed.
- The provider-free command used for this pass was `inspect-artifact` on the
  installed `/Applications/ScreenZen.app` and on the built
  `HisnMobile.app` from the tested simulator build. REA reported no native
  Swift/SwiftUI analysis provider, so neither result is decompilation.
- The 1.3.0 iOS audit and the 1.1.10 Mac audit were read for design vocabulary
  only; their captures and observations are not re-presented as new evidence.
- Web research covered ScreenZen's App Store listing, independent reviews and
  comparisons with Opal, one sec and Jomo. Those pages describe product
  behaviour; they are not runtime evidence of a particular build.
- Third-party UI captures and strings remain temporary engineering artifacts
  and are not redistributed. No ScreenZen code, assets, visual identity or copy
  was reused.

## Evidence

| Subject | REA operation | Evidence | Observed digest |
| --- | --- | --- | --- |
| `/Applications/ScreenZen.app` (2.0.0, build 63) | `inspect-artifact` | `ev_d2543bc4839da8fb59d8ae289793cad987daad652e2c13c7ad817e44f1b23402` | SHA-256 `3d71a3666b43ba400dc05e62d772c7a5f85eea06234e750b42c7d698fe8c7547` |
| Built `HisnMobile.app` 1.4.0 (8), clean simulator build of this tree | `inspect-artifact` | `ev_1d3e32a14ad19bb2c08c2c24fabc7ad51b7cfc9f5b3e86781bbc5fb81cb5d531` | SHA-256 `cb3f0d4cbc7de4a67179fbdf414aded25d4c0b252d41ed7871407da35fd7f403` |

The ScreenZen digest matches the previous audits' installed artifact, so this
is the same observed build rather than a newer copy.

### What REA could not establish

- Native Swift/SwiftUI semantics, data flow or enforcement behaviour. There was
  no native analysis provider; this was an inventory, not decompilation.
- Whether ScreenZen's blocking, pause, time-window or unlock paths work as
  designed, on which OS versions, or under which account.
- Any iOS-specific ScreenZen behaviour. The observed artifact is the Mac app;
  the iOS app is a separate product with different APIs.
- A complete product model. An inventory does not prove the absence or presence
  of a feature.

## What the research shows

The observations below are specific to ScreenZen 2.0.0 build 63 on this Mac and
to the public descriptions of comparable apps:

- **Pause is the product, not a settings sub-page.** Reviews describe the
  breathing or delay screen as the core mechanism, with strict blocks layered on
  top. The pause is a deliberate interruption before an app opens, not a hidden
  setting.
- **The dashboard is calm and object-based.** The app's main surface is a short
  list of rules; each row is an object with one state and one way in. Advanced
  options live behind a detail view rather than above the main controls.
- **One decision per screen.** Onboarding and rule creation state one choice,
  its consequence and one primary action. Empty states carry the next action,
  not a manual.
- **Settings can be locked and progress is local.** ScreenZen has a settings
  passcode, daily opens and streaks; comparable apps (Opal, one sec, Jomo) use
  schedules, friction, strict modes and local progress. Hisn already has a
  commitment lock and deliberately does not copy daily opens or streaks as a
  protection claim.
- **Comparable visual language.** Opal is described as the most polished; one
  sec is the friction-pause reference; Jomo is praised for a calm, minimal
  scheduler. The common thread is a status-first screen, large type, few colours
  and controls that appear only when relevant.

Sources consulted:

- [ScreenZen on the App Store](https://apps.apple.com/us/app/screenzen-screen-time-control/id1541027222)
- [Screen Zen: a free screen-time control app (MakeUseOf)](https://www.makeuseof.com/app-to-stop-phone-addiction-screenzen/)
- [ScreenZen, reviewed honestly (Finit)](https://getfinit.com/blog/screenzen-review)
- [ScreenZen app review (WhistleOut)](https://www.whistleout.com/CellPhones/Apps/screenzen-app-review)
- [Screen Time Index: app blocker comparison](https://screentimeindex.com/posts/screen-time-app-comparison/)
- [Screen Time Index: Jomo alternatives](https://screentimeindex.com/posts/jomo-app-alternatives/)
- [Opal vs Jomo (Screen Time Index)](https://screentimeindex.com/posts/opal-vs-jomo/)
- [Best app blockers for iPhone in 2026 (Orbin)](https://orbin.app/en/guides/best-app-blockers-for-iphone/)
- [Best screen time apps for iPhone (Detox)](https://detox.so/blog/best-screen-time-apps-iphone)
- [I tested every screen time app (Dogear)](https://dogearhq.com/screen-time-apps/)
- [Apple: DeviceActivitySchedule](https://developer.apple.com/documentation/deviceactivity/deviceactivityschedule)
- [Apple: ManagedSettings shield](https://developer.apple.com/documentation/managedsettings/shieldsettings)

## What was implemented in Hisn 1.5.0

| Borrowed idea | Hisn implementation | What changed |
| --- | --- | --- |
| Status first, one primary action | Hero card with layer chips and one action; Setup progress card leads with the state | `ProtectionHero.swift`, `SetupViews.swift`, `MobileRootView.swift` |
| Rows as objects with one action | Plan rows, rule rows and setup step rows with one state and one destination | `OverviewCards.swift`, `SetupViews.swift` |
| Drill-in instead of a wall of form | Three Setup step pages and three overview editor sheets | `SetupViews.swift`, `MobileRootView.swift` |
| Grouped settings cards | Language, Safari extension, gray screen, privacy and about cards | `MobileRootView.swift` |
| A visible mode decision | Always block, daily budget and quiet hours shown as three rows with icons and a checkmark | `AppUsageSection.swift` |
| A calm pause surface | The 1.3.0 pause is retained; its count appears on the overview only for today | `Pause.swift` |

## What was deliberately not copied

- **Quick disable or time-boxed unblocking.** The commitment model makes
  weakening wait; a one-tap "disable for 15 minutes" would undermine it.
- **Daily opens as a protection claim or a streak.** A count of opens is not a
  measure of protection or recovery. Hisn keeps a pause count only and does not
  advertise a score.
- **Location/Halo unlocking.** It needs Bluetooth, a signed-in account and cloud
  state, which conflicts with Hisn's on-device, no-account design.
- **A cloud dashboard, account or cross-device state.** The pause tally stays in
  `UserDefaults` on this device and is never sent anywhere.
- **ScreenZen's visual identity or copy.** Hisn uses its own SwiftUI components,
  colours and English/Arabic strings.

## Limits and follow-up

- This is one installed Mac artifact and public descriptions of other apps. Do
  not cite it as a competitive benchmark or as evidence that ScreenZen is
  better or worse at blocking.
- The implementation is covered by policy, controller and render tests, not by
  a user study, a screen-reader audit or a physical-device review. Those remain
  owner actions.
- DeviceActivity callbacks, the midnight split, both-clock reconciliation and
  Screen Time shield enforcement still need a signed, entitled build on a real
  iPhone. The simulator cannot prove them.
- No native analysis (with a configured provider) was run. If a future audit
  needs call graphs, install a provider deliberately and record its side
  effects; do not treat this inventory as a substitute.
