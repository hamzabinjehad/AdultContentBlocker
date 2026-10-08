# Hisn: coordinated improvement roadmap

Primary user goal: voluntary self-control that remains useful during a moment
of temptation. Family protection is optional. Preserve the existing Mac
features and implement corresponding mobile behavior where Apple supports it.

## Delivered in this pass

- Standard Chromium-profile runtime evidence reader. Repeated explicit missing
  or disabled-copy evidence overrides another profile's fresh heartbeat, with
  a warning; unreadable/malformed/conflicting data remains unconfirmed. Reads
  are asynchronous and bounded, launch recovery rereads promptly, and startup
  grace cannot be renewed using a different profile's heartbeat. Native Mac
  diagnostics name standard folders; mobile guidance explains selecting every
  browser app for separate consented Screen Time shields.
- Mac browser guarding can be explicitly required outside locks; active-lock
  guarding remains. Browser-level session evidence stops unverified restart
  loops resetting grace/warnings, and authoritative check-ins do not fall back
  to editable local stamps when the authority answers. Delayed force-closure
  rechecks consent and recovery. Safari exceptions and per-profile limitations
  are disclosed, not presented as verified protection.
- iPhone/iPad app selection explains consented Always-block browser shields
  and individual authorization limits instead of promising a Mac process guard.
- Extension policy replacement explicitly retires an inherited static Strict
  catch-all after successful dynamic installation. Regression tests preserve
  intentional Strict rules and keep existing rules if replacement fails.
- Extension update validation now checks positive/negative scoring entries and
  optional exemption-domain shapes before installing a generation. Malformed
  signed vocabulary cannot replace the existing working generation.
- Main test entrypoint includes native iOS tests by default, preserves Xcode's
  failure status even when a success banner appears, and has stubbed regression
  tests. Missing mobile tools are a failure, not a silent omission.
- Mobile commitment restoration treats wrong-type stored objects as damaged
  state, not a fresh unlocked install. A valid surviving mirror can recover the
  latest deadline; defaults writes require immediate readback before reporting
  success. This does not establish OS-level durability or deletion prevention.
- Shared versioned two-copy rule-store contract, tested on Mac and mobile.
  Mobile app rules and monitor reads use the store; deletion tombstones prevent
  stale legacy restoration. Corrupt/conflicting/unsupported records are refused.
  This is App Group redundancy, not a secure mirror or new Mac enforcement.
- Mobile reached-budget recovery: app refresh and same-day monitor registration
  reapply the saved selection's shield after a recorded threshold. No counter
  restart occurs; stale prior-day markers do not reapply today's shield. Apple
  authorization and actual callbacks still require physical-device acceptance.
- Network hardening: global mobile DNS scope and split-DNS readiness checks;
  consistent alternate-dot normalization in Mac network policy decisions.
- Confirmed seven-day extension on Mac, iPhone and iPad.
- Fixed Mac commitment extension so the no-self-release horizon follows the
  newly chosen deadline; root authority rejects inconsistent proposals.
- Mobile extension preserves the original start, persists across relaunch,
  and does not overwrite a plan after storage failure.
- Reject invalid extension inputs; retain existing authorized recovery paths.
- App-restriction health reporting on mobile: separate unreadable storage,
  invalid rules, missing authorization, stopped budget monitoring and confirmed
  configuration. One root-owned controller supplies both overview and setup.
- Persistent readiness-loss baseline on Mac, iPhone and iPad. A previously ready
  layer that becomes unconfirmed is named, with a route to recheck/setup. Stable
  identifiers and bounded two-copy storage retain the baseline across relaunch.
  Unreadable history is reported separately; it does not prove what happened
  while Hisn was closed or replace actual current checks.
- Reject future/non-finite browser heartbeat ages and non-positive filter domain
  counts as healthy evidence on Mac. No filter or recovery feature was removed.

## Prioritized next work, not yet implemented

Before calling browser guarding deletion-resistant, verify all browser profiles,
private/guest windows and effective managed force-install policies on an installed
Mac. The new standard-profile reader narrows explicit second-profile removal,
but bundle heartbeats and editable preferences do not prove every profile is
protected. Custom directories, unreadable formats and guest/private windows
remain acceptance gaps. Add native
runtime acceptance for deletion, reinstall, restart, sleep and warning recovery;
do not infer live enforcement from pure policy tests or an unsigned test build.

1. Extend protection-integrity acceptance testing: recheck supported permissions and every
   independently configured layer when returning to the app. Distinguish missing
   permission, stale evidence, failed registration and confirmed configuration.
   Do not silently replace child authorization or restart usage counters.
   Coordinate foreground budget recovery with monitor callbacks before clearing
   stale shields; a naive clear can race a newly reached threshold. Tie monitor
   registration evidence to the saved rule revision so interrupted updates do
   not report a newly saved selection as registered against an old monitor.
2. Real-device acceptance matrix: signed iPhone and iPad, Mac installed filter,
   app restart, device reboot, denied permissions, revoked permissions, expiry,
   monitoring failure and essential-service recovery. This is a release gate.
3. Mobile restriction persistence: evaluate a device-local protected mirror of
   selected-app rules and safe reconciliation after accidental data loss.
   Do not promise that Keychain persistence survives every reinstall or reset.
4. Router setup: verified per-model adapters, explicit owner consent, pre-change
   backup, rollback and post-change DNS tests. Keep unsupported routers on
   honest guided setup. Do not guess application domains or auto-change networks.
5. Content coverage: independently measured false positives and missed content;
   privacy-preserving reporting. A page scanner is only advertised on surfaces
   where it is actually supported, never across all iOS apps.

## Product and safety rules

- No percentage that claims to measure total protection from a layer count.
- No claim of permanent undeletability, all-content classification or guaranteed
  recovery from addiction.
- Tightening and extension require informed consent; no hidden extension of a
  deadline. Weakening inside an active commitment remains unavailable.
- Keep emergency, authentication and other essential services accessible.
- No external account, paid service, plugin installation or router mutation
  is required for this implementation pass.

Native SwiftUI controls and contextual permission explanations follow
[Apple's onboarding guidance](https://developer.apple.com/design/human-interface-guidelines/onboarding)
and [privacy guidance](https://developer.apple.com/design/human-interface-guidelines/privacy).
