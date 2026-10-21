# Hisn Android: competitive testing and improvement plan

Date: 2026-10-18. This plan builds on [the Android research plan](ANDROID_RESEARCH_PLAN.md).
It is not a claim that Hisn for Android has shipped or that competitor testing
is complete.

## What was actually checked

- The connected Samsung SM-G990B2 is an Android 16 device. HaramBlur was
  opened. Its permission screen reported one of two steps complete, while the
  Android accessibility settings list showed its service as enabled. This
  discrepancy is a reason to verify each permission and live service separately;
  it does not by itself prove a defect or establish why the screen differed.
- Android's projection manager reported no active screen-capture session at
  the time of the check. Until the owner starts a new session and approves the
  system prompt, a live visual detection or performance result is unavailable.
- REA inspected the installed HaramBlur APK. Its visual service declares a
  benchmark callback and calls into another method with inference statistics.
  This observation does not establish actual latency, that logging is enabled,
  or how accurate its detection is. Earlier REA observations and the limited
  BlockerHero uninstall attempt are recorded in the research plan.
- The same service's shutdown method contains a conditional Firebase Analytics
  event with fields for detection count, platform, device model, GPU type and
  GPU use. Static code alone does not prove this branch ran, that any event was
  transmitted, or that images or browsing contents were sent. It is a reason
  to explicitly review diagnostics separately from image processing and provide
  an informed analytics choice in Hisn.
- No accessibility permission, active BlockerHero rule, app removal, phone
  enrollment, reset, purchase, or subscription was changed for this plan.

## Test the competitors without risking the owner's phone

Run these only with the owner's consent to any required permission changes.
Use a disposable test profile or clearly disclosed test session. Keep a written
record of the initial settings and restore only settings changed for testing.
Do not disable an existing commitment or unregister device administration as a
routine test. Use synthetic text, non-explicit test media, and accounts without
private conversations. Measure one blocker at a time so the source of a block
is attributable.

| Area | Checks | Record |
| --- | --- | --- |
| Setup and recovery | Fresh start, denied permission, revoked permission, reboot, screen lock, Android capture stop, notification access | What is protected, what is not, and whether the status is truthful |
| Domains | Owned or reserved test domains, benign controls, subdomains, Wi-Fi and mobile, direct IP and encrypted DNS | Pass/fail per route and any bypass or connectivity loss |
| Words | Literal user rules, Arabic and English, separators, obfuscation, word boundaries, benign medical/educational text, text split between nodes | Missed matches, false alarms and response time |
| Apps and schedules | Chosen test app, allowlisted app, time-window transitions, split screen, restarts | Actual intervention and missed or unwanted blocks |
| Images | Labeled safe faces/people and non-person images, varied sizes, scrolling and multiple faces | Misses, false positives, visible-before-mask time and blur style |
| Video | Benign labeled moving faces, rapid scene changes, pause/resume, overlays | Delay, stale masks, dropped frames and playback disruption |
| Privacy | Password fields, notifications, banking/secure surfaces, screen capture permission | What the test can establish without collecting private content |
| Commitment | Start/end of a disposable short session, stronger in-app settings, clock/reboot behavior | Which changes are rejected and which platform controls remain available |

Do not infer universal blocking from a passing fixture. Do not use the user's
actual personal messages or private media as a test corpus. Secure surfaces,
other VPNs, browser encryption and OS versions need separate coverage.

## Hisn roadmap, in order

1. **Real Android foundation.** Set up an Android SDK and reproducible debug
   build, add a permission/onboarding flow, Arabic and English interface and
   a dashboard that separately verifies each layer. The repository currently
   contains only an experimental Java word matcher, not a working Android app.
2. **Network layer.** Verify Hisn's signed domain bundle and use it in a real
   user-consented VPN/DNS implementation. Test connectivity, fallback and Android
   VPN conflicts before calling the layer active. Mixed-content sites and
   encrypted traffic require other layers; don't block an entire shared domain
   without a user-selected rule.
3. **Better word detection.** Match the shared normalization fixtures and
   signed vocabulary across platforms; bound work without silently discarding
   text; use context and benign negatives to reduce false positives; support
   local user-defined literal rules and explain which rule category acted.
   Limit screen access to selected apps and avoid saving screen text.
4. **Apps and focus.** Add user-selected app restrictions and schedules with
   explicit authority and limitations. Normal-device interventions and managed
   device policy are distinct: never claim the first has the guarantees of the
   second. Preserve phone and emergency functions.
5. **Visual layer.** Obtain consent for capture; process locally with bounded
   queues and a reviewed, licensed model. Separate adult-content, person and
   face detection. Provide face/full-media coverage and blur/pixel/solid styles
   only where the adapter can support them. Apparent gender predictions are
   fallible and cannot establish identity; no model guarantees all results.
6. **Self-commitment.** Show the duration, chosen restrictions, recovery and
   limitations before activation. During an active plan, prevent weakening the
   app's own settings; allow stronger settings. Handle clock changes and reboot
   honestly. A managed-device experiment requires separate, informed consent
   and must never silently reset or enroll the owner's phone.
7. **Cross-device and privacy.** Match the concepts in Mac, iPhone/iPad and
   browser versions without removing existing features. Sync only explicitly
   chosen rules, not screen contents or browsing history; explain any impact
   on other people before changing router settings.

## Release gates

For every layer, publish device/OS and permission coverage, measured miss and
false-positive rates with denominators, median and 95th-percentile response
times, resource use and known bypasses. Test normal browsing and social media
as well as blocking. Verify process death, reboot, upgrade, revoked permission,
another VPN, offline state and storage errors. A passing unit test is not proof
that a phone is protected. Google Play restricts use of accessibility to prevent
uninstallation; ordinary Android administration is not an unremovable lock.
See the primary-source links and deeper implementation gates in the research
plan.
