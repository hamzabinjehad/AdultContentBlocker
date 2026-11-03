# Hisn iPhone and iPad 1.4.0 — a shield that explains itself

Released 3 November 2026 as a development candidate. Simulator-tested only; no
Apple team, Family Controls entitlement, signed build or physical-device
acceptance is claimed.

## Why

1.3.0 could apply always-block, daily-budget and quiet-hours shields, but iOS
drew its own default shield. The person saw no Hisn text, no reason, and no
indication of which saved rule was holding the app. In Arabic, the default
shield did not follow the language chosen in Hisn at all.

ScreenZen and comparable apps put a calm, explanatory screen at the moment of
restriction. Hisn cannot copy their quick-unlock or pause-before-open flows
without undermining its commitment model, but it can make the shield honest and
legible: name the rule, speak the person’s language, and offer no bypass inside
the shield.

## What changed

### Two Screen Time extensions

- **`HisnShieldConfig`** subclasses `ShieldConfigurationDataSource` and returns
  the system-rendered shield for apps, categories, web domains and
  category-scoped web domains. It sets a shield icon, title, subtitle, one
  primary button and no secondary button.
- **`HisnShieldAction`** subclasses `ShieldActionDelegate`. Its three token
  callbacks (application, category, web domain) all return `.close`. That
  dismisses the shield view; the ManagedSettings shield stays applied, so the
  app or site remains inaccessible. There is no unlock action, no quick
  disable, and no rule mutation in the extension.

### What the shield says

`ios/Shared/ShieldTextPolicy.swift` is a small, fixed English/Arabic table
compiled into the app, the configuration extension and the tests. It chooses a
reason from the saved rule:

| Reason | When | English title | Arabic title |
| --- | --- | --- | --- |
| `always` | saved mode is always-block | Hisn is protecting this app | حصن يحمي هذا التطبيق |
| `dailyBudget` | daily-budget mode and the reached-day marker is present | Today’s time is used | انتهى وقت اليوم |
| `quietHours` | quiet-hours mode and the window is active | Quiet hours | ساعات هادئة |
| `unknown` | rule unreadable, budget marker missing, or window inactive | Hisn is protecting this app | حصن يحمي هذا التطبيق |

- The subtitles say what the rule does and, for the generic case, tell the
  person to review Hisn. No message claims that blocking was observed.
- The app writes its language choice (`system`, `ar` or `en`) into the App Group
  under `hisn.shield.language` on launch and on change. `system` follows the
  extension’s preferred language. If the App Group is unavailable, the shield
  falls back to the device language and the generic reason.
- The quiet-hours reason uses the same both-clock rule as the app: if either the
  device clock or the saved Apple-time reading is inside the window, the shield
  can say quiet hours. A budget reason requires the reached-day marker, so a
  stale budget shield is not described as “time used”.
- The copy is a fixed table rather than `Localizable.strings` because the
  extension runs in its own process and bundle. The policy tests cover both
  languages in both directions; the app’s key-parity test does not see these
  strings.

### Signing, privacy and packaging

- Both extensions declare `com.apple.developer.family-controls`.
  `HisnShieldConfig` also declares the `group.app.hisn.mobile` App Group because
  it reads the saved rule and language.
- Both embed `Hisn/PrivacyInfo.xcprivacy`. The manifest now ships in eleven
  bundles: the app, six Safari blockers, the activity monitor, the Safari
  scanner, and both shield extensions.
- Device builds embed the extensions in the app under PlugIns. Simulator builds
  strip both appexes after the embed phase because some simulator runtimes
  reject the ManagedSettingsUI extension point; the policy tests do not need
  the appexes. The Safari-only candidate does not build them because it has no
  Family Controls entitlement.
- Version 1.4.0, build 8. The app, six blockers, monitor, scanner and both
  shield extensions share the version set.

## Files

| File | Change |
| --- | --- |
| `ios/HisnShieldConfig/ShieldConfigurationExtension.swift` | Shield data source for apps, categories, domains and category-scoped domains |
| `ios/HisnShieldConfig/Info.plist` | Shield configuration extension point and principal class |
| `ios/HisnShieldConfig/HisnShieldConfig.entitlements` | Family Controls and App Group |
| `ios/HisnShieldAction/ShieldActionExtension.swift` | Token callbacks returning `.close`; no unlock |
| `ios/HisnShieldAction/Info.plist` | Shield action extension point and principal class |
| `ios/HisnShieldAction/HisnShieldAction.entitlements` | Family Controls |
| `ios/Shared/ShieldTextPolicy.swift` | Language handoff, reason selection, fixed bilingual copy |
| `ios/Hisn/HisnMobileApp.swift` | Writes the language choice to the App Group |
| `ios/HisnTests/ShieldTextPolicyTests.swift` | Language, reason, both-clock, fallback and copy tests |
| `ios/generate_xcodeproj.py`, project | Two extension targets, embedding, bundle IDs, version 1.4.0/build 8 |
| `ios/test_privacy.py` | Eleven manifest bundles and shield extension-point checks |
| `ios/README.md` | 1.4.0 section, signing steps and shield verification step |

## Verification

- `./test.sh ios` — **39 Python and 213 hosted tests, 0 failures** on the
  iPhone 17 simulator, iOS 26.5. The hosted count was 206 before this release;
  the seven new tests are the shield policy tests.
- The app builds for the simulator with `CODE_SIGNING_ALLOWED=NO`; simulator
  builds strip the shield appexes after embedding so the test runner installs
  on runtimes that do not know the extension point. Device builds keep the
  embed phase, and the extension Info.plists and entitlements are checked by
  the Python tests.
- Python checks cover the shield plists, Family Controls entitlements, App Group
  on the configuration extension, privacy-manifest membership in eleven bundles
  and the generator references.
- Clean-build REA evidence: `ev_3d4715f53f0eeef3e757ed6297845585d36c285eea54801189412303cfe188ef`,
  SHA-256 `b4dd458876ee8e25b708521253262c00fe3820dadf00e90dc5892589349a3619`.
  This is a static artifact inventory, not runtime proof.
- No physical device, Family Controls entitlement or signed IPA was available.
  The system-rendered shield, extension activation, token association, language
  handoff in the extension process and post-tap shield persistence remain
  unproven.

## Limits

- The shield extensions need Apple’s Family Controls entitlement on four
  targets: the app, `HisnActivityMonitor`, `HisnShieldConfig` and
  `HisnShieldAction`. Adding the plist key is not approval.
- The Simulator cannot render or activate Screen Time shields, and simulator
  test builds strip the appexes. The tests prove the policy and the extension
  configuration, not the system UI.
- The shield has no unlock action. Outside a commitment, the person removes or
  weakens the rule in Hisn; inside a commitment, the saved rule is frozen.
  iOS Settings and permission revocation remain outside Hisn’s control.
- A missing or unreadable rule gets the generic message; the shield never
  guesses that a budget was reached or that quiet hours are active.
- Apple has reported ApplicationToken association problems on some iOS 17.5+
  builds. The extensions compile against the current SDK, but a real-device
  test must confirm that the shield appears for the selected tokens and that the
  language handoff survives a relaunch.
- No custom shield artwork, submenu, “ask for more time” flow or quick unlock
  is included. Those would either weaken the commitment or need a separate
  product decision.

## Device acceptance still needed

1. Build with Family Controls on all four targets and apply an always-block rule
   to a disposable app; confirm Hisn’s title/subtitle and the single button.
2. Repeat for a daily budget after its threshold, and for quiet hours on both
   sides of midnight.
3. Switch Hisn’s language to Arabic and back; open a shielded app and confirm the
   extension process uses the chosen language after a relaunch.
4. Tap the button; confirm the shield view closes and the app remains
   inaccessible. Confirm no path in the shield unlocks it.
5. Revoke Family Controls in Settings; confirm iOS removes the shields and Hisn
   reports the lost layer. Retest after restoring access.
6. Record the results in the release validation record. No simulator or static
   test substitutes for these steps.

## Next owner actions

The Apple Developer Program, Family Controls distribution entitlement for the
app and three extensions, a disposable iPhone and TestFlight remain the owner
actions in [the iOS improvement plan](IOS_IMPROVEMENT_PLAN.md) and
[1.0 readiness](1.0_READINESS.md). This release does not change those.
