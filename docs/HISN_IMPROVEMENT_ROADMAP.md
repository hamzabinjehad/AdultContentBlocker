# Hisn: coordinated improvement roadmap

Primary user goal: voluntary self-control that remains useful during a moment
of temptation. Family protection is optional. Preserve the existing Mac
features and implement corresponding mobile behavior where Apple supports it.

## Current iteration: word blocking and responsiveness — 2026-10-17

This section prioritizes the next work; it does not mark the rest of this
roadmap complete. Keep existing Mac protection and commitment rules intact.

### Implemented in source in this iteration

- Compile multi-word vocabulary into an exact-token prefix tree once per
  effective vocabulary. Skip impossible phrase continuations rather than
  allocating every token-window combination on every page/post check.
- Fix custom phrases pasted with repeated spaces, tabs or line breaks. Use the
  same whitespace key for ignoring a phrase, and rebuild the compiled matcher
  when ignored terms change. Leave the shared Unicode normalisation contract
  unchanged.
- Preserve contextual negative terms, overlapping phrases, zone weights,
  occurrence caps, sensitivity and density thresholds. Do not lower thresholds
  to manufacture better coverage, and do not change domain/URL filtering.
- Add regression coverage for the spacing bug and compare complete scores,
  matched terms and verdicts against the former exhaustive algorithm across
  the labelled corpus, every shipped multi-word term, and ignored phrases.
- Follow-through candidate 1.1.11 adds reviewed, whole-token evasion matching
  for a small set of existing strong terms: digit substitutions, selected
  lookalike letters and punctuation-separated single letters. Aliases share
  the canonical occurrence cap and cannot restore ignored/absent/weak terms.
- Correct the French `sexe` form-choice ambiguity locally, not through a
  site exemption. Only the seed weight-6 label followed immediately by the
  reviewed choices is skipped; stronger custom rules and other explicit
  occurrences still score. General contextual-negative cancellation has not
  been redesigned.

The shared scorer is used by Chromium and staged into Safari for macOS,
iPhone and iPad. Source/staging changes are not proof of an installed mobile
update. No native app, browser-management policy or lock was replaced here.

Validation for the initial optimisation and its follow-through is recorded in
[the 1.1.11 candidate notes](EXTENSION_1.1.11.md). The original corpus's French
form false block is now corrected. The expanded 41-case synthetic corpus reports
0/18 adult misses and 0/23 benign blocks at sensitivity 50, 80 and 100. This is
a small regression set, not evidence of real-world perfect classification.

### Ordered next work — partial progress, not a completed roadmap

Rows 1–3 build on the fixtures, reviewed aliases and local form correction
implemented above. Broader context calibration and new site adapters remain
separate work; the follow-through does not mark these release gates complete.

| Priority | Work | Acceptance gate |
| --- | --- | --- |
| 1 | Expand labelled Arabic, English and German fixtures for disguised words: mixed scripts, digit substitutions, separated letters, hashtags and split inline nodes. Add medical, recovery, religious, news and ordinary-post counterexamples first. | Report misses and false blocks separately by language and surface; every existing benign fixture must still pass. Never infer real-world accuracy from this synthetic corpus. |
| 2 | Implement conservative evasion candidates only for reviewed high-confidence terms. Preserve whole-token boundaries and avoid arbitrary substring matching, recursive Arabic stemming or a global digit-to-letter substitution. | New evasion fixtures block without blocking nationality, place names or benign lookalikes. Bound candidates and page processing; keep the compiler/native normalisation contract synchronized if it changes. |
| 3 | Improve local context scoring for ambiguous terms. Evaluate nearby contextual evidence rather than letting unrelated negative text elsewhere wash out an explicit post. | Test explicit content plus benign padding, genuine medicine and recovery content, short posts, captions and conflicting metadata. Ship only after reviewing both missed-content and false-block changes. |
| 4 | Make dynamic text checks incremental. Rescore changed posts/regions, retain bounded in-memory signatures, and measure collection, queue and scoring separately. | Test 10/50/100-post feeds, rapid edits, navigation, caption changes and policy changes. Stale clean verdicts must not survive changed content; ordinary X videos must continue playing. Measure warm and cold p50/p95 on each supported browser before setting a latency claim. |
| 5 | Add granular adapters for other mixed-content sites only after testing their real DOMs. Explain a block privately in trusted extension UI, without leaking matched text to a webpage or retaining browsing history. | One flagged item does not block an entire normal feed; reused nodes and late captions are rechecked. No raw post text, page capture or inferred personal traits in persistent diagnostics. Existing commitment rules govern any weakening/report workflow. |
| 6 | Verify the complete release on Chromium, macOS Safari, physical iPhone and iPad; verify extension loss and independent network protection separately. | Browser/profile matrix, sleep/restart, opt-in permissions, connection loss and active-lock tests. Confirm staged files equal tested sources. Installation and GitHub publication are separate explicit release steps, not inferred from passing tests. |

Continue visual detection and network integrity work alongside these gates;
word checking cannot identify content that has no useful text, inspect every
encrypted native app, or make an administrator-owned device impossible to bypass.
Keep setup honest about supported surfaces and independently verified layers.

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
