# Hisn visual and protection-layer QA — 2026-10-11

Outcome: the selected adaptive protection-layer interface and benign local
runtime checks pass this engineering iteration. Complete adult-content coverage,
targeted female/male accuracy, removal resistance and physical Safari operation
are **not** certified. Source changes are not an installed-app upgrade.

## Design basis and surfaces

The user's selected protection-layer layout, dark presentation and later
automatic dark/light request guided the implementation. Existing Hisn brand
assets and the icon system were reused. The earlier concept is a design brief,
not a pixel-identical runtime screenshot or an accuracy benchmark.

Rendered extension surfaces were inspected in isolated Helium (Chromium
155.0.8059.39; device pixel ratio 2), with configured CSS viewports:

- Popup: 400 × 800 CSS viewport; popup content constrained to 600px. Arabic RTL,
  dark and light. Main actions stay visible while the layer list scrolls.
- Settings: 1100 × 900 CSS pixels, Arabic dark mode, and 390 × 844 narrow layout.
- Benign media fixture: normal synthetic image/video and a public-domain
  astronaut image. Matching all-faces preference blurs the whole media.

Evidence is in [`dist/qa-visual-2026-10-11/`](dist/qa-visual-2026-10-11/):
`popup-dark-ar.png`, `popup-light-ar.png`, `options-dark-ar.png`,
`options-narrow-ar.png`, `visual-safe-fixture.png`, `visual-face-fixture.png`,
`local-page-test.png`, `evidence.json` and test logs. Generated artifacts are
ignored by Git and must be preserved separately if needed beyond this checkout.

## Visual and interaction review

- Hierarchy: browser-only baseline and optional native reinforcement are
  separate; private-window permission gaps remain explicit. No universal
  protected-everywhere badge or invented detection percentage was added.
- Density: the compact popup keeps its principal action available within the
  browser popup limit. Arabic wrapping and narrow settings have no horizontal
  overflow in tested viewports.
- Color/type: shared light/dark tokens, readable Arabic system type, reused
  assets and text labels alongside status color. Color follows the browser's
  reported preference, not every custom theme.
- Controls: visual opt-in and appearance preferences have labels and live
  feedback. Autosaved controls have no redundant nonworking Save button. Save
  failure restores confirmed settings and remains visible after model polling.
- Honesty: visual classification and optional face preferences are separate,
  experimental and off by default. Face-only detection and whole-media blur
  are disclosed. No immediate adult-media reveal control was added.
- Accessibility limits: semantic controls, RTL and reduced-motion styling were
  reviewed; this is not a comprehensive VoiceOver, keyboard or WCAG audit.

## Verification performed

- Visible isolated Helium extension: 15 checks passed against the real worker,
  packaged local models, UI and synthetic readable video; no extension-page
  runtime errors in the captured flow. Test copies deliberately omit native
  messaging and use their own extension identity/profile.
- Real engine safe-page check: benign image not classified adult, all-faces
  blur applied, normal video available, and missing face assets reported failed
  instead of ready. Models ran locally without media uploads.
- The first technical explanation of this local page triggered the text
  keyword blocker despite containing no adult media. This false positive is
  recorded, not treated as a coverage success. The neutral test-page wording
  was revised without an allowlist or weakened rules; negation/technical-text
  classification still needs broader correction and regression coverage.
  The revised safe page and model flow passed with text protection still on;
  see `protected-page-qa.log`. Its screenshot is `local-page-test.png`.
- Female/male check: highest confidence on the astronaut fixture stayed below
  the selected threshold. The specific preference missed that example. This
  remains a known limitation; thresholds were not lowered to make the test pass.
- JavaScript suites passed, including 29 visual-decision/queue checks, 26
  scanner lifecycle checks and 21 actual broker race checks. Mocked adult
  scores exercise withholding, not real adult-classification quality.
- Browser scanner harness: 18/18 checks; settings and popup harnesses passed,
  including autosave error/retry, connection failure, lock ownership and RTL.
- Packaging: 33 Python checks and validated clean unpacked/runtime archive;
  model hashes, notices and required resources checked. These hashes establish
  build consistency, not independent artifact authenticity.
- Profile generator: 49 tests passed. No policy or DNS profile was installed.
- Mac: 452 tests, zero failures; 475 localized strings have Arabic coverage.
- iPhone/iPad: 20 Python package checks including Safari message-contract
  checks; latest unsigned generic iOS Simulator build succeeded. This is
  compilation, not physical Safari or hosted native test execution.

## Resolved during review

1. Popup actions below the browser height limit: made actions stationary and
   the layer list scrollable.
2. Face detector silently ignored: used detector confidence rather than the
   disabled mesh's zero confidence; rejected erroneous face-model output.
3. Known adult source revealed by seeking/cache eviction: kept source-specific
   withholding, with node-owned weak latches and a bounded active scan cache.
4. Opt-out during context creation/cancellation: wait only for existing
   creation, do not recreate contexts for cleanup, suppress late replies.
5. Constructed/failed model looking ready: publish the detector only after
   successful asset loading with actual loaded-weight evidence.
6. Redundant Save/error disappearing during polling: removed dead action and
   separated persistent save feedback from model status.

## Remaining release gates

- Appropriately obtained and handled adult-classification benchmark; false
  positives, misses, latency, CPU/memory/battery and representative face tests.
- Inaccessible/protected media, sampled moments, unsupported layouts, private
  windows and site permissions. Unknown is not verified clean; ordinary video
  remains available as requested.
- Physical iPhone/iPad Safari activation and model execution. Shared code does
  not give Safari the Mac browser guard or app-commitment enforcement.
- Signed installed Mac filter/bridge/guard acceptance, restart/sleep and
  multiple-profile behavior; managed policy/removal testing in each browser.
- Browser store publication and verified policy support. Ordinary extensions
  remain removable; administrator/owner recovery cannot be ruled out.
- In-app ChatGPT browser: the local test URL was queued for display, but no
  browser-control tool was available. Helium results are not ChatGPT-browser
  results. The safe page tests the engine, not extension installation.

The QA runs above did not change a personal browser account/profile, router,
DNS, commitment, management profile, installed app or guardian setting. At that
checkpoint no commit, push or release publication had occurred. The local test
server binds only to loopback.

## Version 1.1.1 follow-up

The user's popup screenshot exposed a compact-viewport regression: stationary
header/actions/footer could squeeze the layer list to zero height. The fix keeps
the chosen text size and switches to whole-document scrolling in short or
narrow viewports. English and Arabic layouts passed 69 popup checks in Helium,
including 200 × 280, 400 × 280, 200 × 650 and the usual 400 × 600 viewport.
These are CSS-viewport tests, not a claim of verified browser zoom at 200%.

Settings passed 58 checks. The Linux CI harness now uses real timers and an
explicit loopback verdict; crashes, timeouts and missing results fail instead of
being excused as successful skips. All five canonical harnesses passed the
real-time path in Helium; GitHub must separately verify its Linux runner.

Version 1.1.1/build 2 was generated for Mac, iPhone and iPad; the Chromium and
Safari manifests are 1.1.1. The Mac release and generic iOS Simulator builds
succeeded. Mac tests again passed 452 checks, with 475 Arabic strings covered.
The protected-account-preserving installer now stages and verifies before
stopping the app, retains the old bundle and agent, and attempts rollback on
failure. Local ad-hoc signing is not a Developer ID release or an activated
system network filter.
