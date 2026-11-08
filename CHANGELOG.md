# Changelog

Newest first. Versions are per platform; the pinned set is checked in
[versions.json](versions.json) and rendered in [docs/VERSIONS.md](docs/VERSIONS.md).
Per-release evidence lives in `docs/` and is linked from each entry. A version
bump without an entry here is an unfinished release.

## Android 1.5.2 — 2026-11-08

### Changed

- **DNS filtering restarts itself after an update.** When the app returns to the
  foreground, if DNS was left on, Android still holds the VPN approval, and the
  foreground tunnel is not running, the app starts it without another tap. This
  repairs the common post-update *DNS filtering was left on but is not running*
  gap.
- **A live accessibility service can be re-armed.** When the app reads
  accessibility as enabled but the service as disconnected, it broadcasts an
  explicit re-arm request; a live service reruns its idempotent setup. This
  covers the system-bound service that did not repeat `onServiceConnected`
  after an update. If the service is not alive at all, Android's accessibility
  settings remain the only repair; an app cannot toggle its own accessibility
  grant.
- 1.5.1's protected/Incognito cover, the 1.5.0 people cover, native adult TFLite
  path, 350 ms moving cadence, verdict cache and after-block pause are retained.
  No block threshold, permission, stored rule or consent text changed.

### Verification

- Core suite, strings (578/16/570), notices, debug/androidTest builds and lint.
- Phone update: DNS auto-start is **PASS** (the foreground service returned
  without another tap); the guard re-arm path is compiled and the service was
  bound, but the phone was dozing before a visual smoke test. No adult content
  is used. The protected/Incognito cover remains the 1.5.1 behaviour.
- No adult-positive corpus, phone adult test, battery or thermal measurement.

## Android 1.5.1 — 2026-11-08

### Changed

- **Incognito and other protected windows are covered, not left visible.**
  Android rejects screenshots of `FLAG_SECURE` windows with
  `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` (6). That includes Chrome Incognito.
  No accessibility or MediaProjection capture can return those pixels without
  root or a system privilege, and HaramBlur cannot bypass it either. Hisn now
  reports a **PROTECTED** state and covers the whole selected window with the
  same touch-blocking overlay, showing *Hisn can't check this window* instead
  of silently leaving the private page visible.
- The protected cover retries at 2 s and keeps the loaded runtime; it clears on
  leaving the window, screen off, a later inspected sample, or turning the layer
  off. Text and DNS layers remain independent and active where the app exposes
  them.
- The 1.5.0 people cover, native adult TFLite path, 350 ms moving cadence,
  verdict cache and after-block pause are unchanged.

### Verification

- API 36 emulator, FLAG_SECURE fixture: `STATE PROTECTED`, `CAPTURE_ERROR 6`,
  `OVERLAYS 1`, zero samples from the protected window; the protected cover
  rendered and the people-off suite still passes.
- This does **not** inspect protected pixels. It is a fail-closed cover for a
  window Android will not let any app capture. No root, Xposed, MediaProjection
  workaround or secure-flag bypass is added.
- Phone Incognito cover on the SM-G990B2 after the 1.5.1 install and accessibility
  re-bind; no protected pixels were inspected.
- No adult-positive corpus, phone adult test, battery or thermal measurement.

## Android 1.5.0 — 2026-11-08

### Added

- **Opt-in people cover in Rules › Built-in checks › Blur people.** Off, **All
  faces**, **Women (estimated)** and **Men (estimated)**. When on, the visual
  guard runs the packaged WebView face engine (Human BlazeFace and the gender
  model) at a **384 px** frame, still runs the adult classifier in the same pass,
  and covers the whole selected window with a touch-blocking accessibility
  overlay when a face matches. The adult classifier and its thresholds remain
  independent and still close the detected app.
- **A deliberately stronger response than a small face blur.** The cover is a
  **10 px abstract snapshot** on an opaque scrim, not a readable face blur. It
  blocks app taps, clears only on a later clean sample, when the person leaves
  the selected app, on screen off, or when the layer is disabled. A face-runtime
  error fails closed while a cover is already up.
- **Women/men is a recall-biased estimate, not identity.** Uncertain faces are
  covered, so the men setting can also hide a woman when the estimate is
  uncertain; all-faces mode avoids the estimate entirely.
- The face models were already in the pinned visual inventory and notices; the
  Android build now copies `models/blazeface.*` and `models/gender.*` and serves
  them only to the isolated `hisn.invalid` WebView. The five licence files and
  the visual notices are unchanged.

### Verification

- Face-model smoke on the pinned public-domain NASA astronaut portrait:
  `PASS: wasm verdict=allow face=true` in all and female modes; male also matched
  under the uncertainty rule.
- Live all-faces guard on the API 36 emulator: `PEOPLE all`, `FRAME_EDGE 384`,
  `BACKEND wasm`, `OVERLAYS 1`, 0 capture failures, 1 floor rejection,
  109–135 ms inference and 145–190 ms sample latency.
- The people-off live suite passes all ten checks with `BACKEND tflite-cpu` and
  `FRAME_EDGE 224`; core, strings (574/16/566), notices, builds, lint and signed
  artifacts pass.
- No phone, adult-positive corpus, battery, thermal or body-only measurement;
  no claim of accuracy or superiority over HaramBlur. The cover blocks app
  interaction and the status-bar strip can remain visible; both are disclosed.

## Android 1.4.1 — 2026-11-07

### Changed

- **A moving screen is sampled at the platform ceiling, not half of it.** The
  fast cadence was 250 ms, below Android's 333 ms per-window accessibility
  screenshot floor, so a moving screen could spend every other request on
  `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT` and then enter the unreadable
  backoff. The fast interval is now **350 ms**, and a request rejected by the
  floor retries at that floor without incrementing `captureFailures`, backing
  off, or reporting the window unreadable. The debug status adds
  `RATE_LIMITED`.
- **Measured on the API 36 emulator live guard.** In an 8 s moving fixture the
  native runtime produced **23 real samples instead of 11**, with **0 capture
  failures instead of 6**; one request was rate-limited at a window edge. The
  same run reported `BACKEND tflite-cpu`, 24–30 ms inference, 24–29 ms capture
  and 68 ms end-to-end sample.
- **The native model is mapped and its buffers reused.** `TfliteClassifier`
  maps the 9.8 MB uncompressed model from the APK instead of copying it, reuses
  its 224×224 input buffer, and zeroes the input and source pixels after each
  frame. The 1.4.0 score parity, thresholds, WebView fallback, verdict cache,
  centred near-miss crop, after-block pause and text scan are unchanged.

### Verification

- The live visual suite on the API 36 emulator passes all ten checks, including
  `BACKEND tflite-cpu`, benign still and moving sampling, input pause, secure
  window, synthetic intervention, hold recovery and opt-out.
- Core suites, strings/notices and the debug/androidTest builds and lint pass.
  Signed Play/direct artifacts and hashes are recorded in
  [the 1.4.1 note](docs/ANDROID_1.4.1.md).
- No phone, adult-positive corpus, GPU, battery or thermal measurement is
  claimed. The 23-vs-11 sample result is a synthetic fixture on one emulator.

## Android 1.4.0 — 2026-11-07

### Changed

- **Native TFLite is the primary visual runtime.** The same nsfwjs 4.4.0
  MobileNetV2 weights were reconstructed, converted through Keras to a float32
  TFLite model, and pinned by SHA-256 in the APK. `TfliteClassifier` preprocesses
  224×224 bilinear with `alignCorners`, divides by 255 and applies the same
  0.70/0.85 Porn/Hentai thresholds on one daemon worker; the WebView engine stays
  as the fallback if the native model cannot load.
- **Measured roughly 3× faster inference.** On the API 36 emulator the native
  CPU path measured **38–45 ms median** and 1.6–1.9 s model load, against
  123 ms/3.0 s for WASM and 188 ms/3.3 s for WebGL. One native run's p95 was
  203 ms; a later run had an emulator hitch. No phone claim.
- **Converted scores match TFJS to 3.34 × 10⁻⁶** across 12 benign views and
  five classes; 0/12 benign views blocked. No adult-positive corpus was used.
- The TensorFlow Lite Apache 2.0 notice is listed under **Licenses and sources**;
  the NSFWJS weights notice is retained. The direct APK grows to ~29 MB because
  of the 9.8 MB model and four-ABI native libraries; the Play AAB is ~16 MB.
- 1.3.2's verdict cache, 1.3.1's centred near-miss crop, 1.3.0's after-block
  pause and the 2,048-label text scan are retained. No threshold, capture size,
  permission, stored field or consent text changed.

### Verification

- Core suite, strings (564/16/555), notices (5 license files), debug/androidTest
  builds, lint (0 errors) and `CoreDeviceTestRunner` on API 36 — **PASS**.
- Native benchmark, 36 calls: 0/12 benign false blocks, 38–45 ms median; the
  live debug guard reported `BACKEND tflite-cpu`.
- Signed Play APK/AAB and direct APK pass `check-release.py` and `apksigner`.
  Hashes, raw reports and limits: [1.4.0 record](docs/ANDROID_1.4.0.md).

### Limits

- No adult-positive corpus and no adult-accuracy claim; only benign parity and
  false-block evidence.
- Emulator CPU only; no phone, GPU, battery, thermal or long-run measurement.
  The GPU delegate was unreliable on the emulator and is not used.
- The APK is ~29 MB direct (16 MB Play AAB); the WebView fallback assets are
  retained.
- The fallback path is code-verified, not forced on a device.

## Android 1.3.2 — 2026-11-07

### Added

- **A bounded last-verdict cache for unchanged visual frames.**
  `VisualVerdictCache` keeps one coarse 16×16 signature and an allow/block word
  per selected window for five seconds. When a still window's signature is
  unchanged, `VisualGuard` reuses the verdict instead of running the model
  again; a window change, scroll/content activity, engine reset or close clears
  it. No pixels, URL or text are retained, and the four-window cache evicts the
  oldest. The debug status reports `REUSED`; `samples` still counts real model
  samples.
- `VisualVerdictCacheTest` covers signature, window, TTL, unknown, bound,
  retain and clear.

### Verification

- Core suite, strings (563/16/554), notices, debug/androidTest builds, lint
  (0 errors) and `CoreDeviceTestRunner` on the API 36 emulator — **PASS**.
- Signed Play APK/AAB and direct APK pass `check-release.py` and `apksigner`.
  Hashes and limits: [1.3.2 record](docs/ANDROID_1.3.2.md).

### Limits

- No device cache-hit or battery measurement; the 123 ms figure is the 1.3.1
  inference a hit avoids. The capture still runs.
- The exact coarse signature can reuse an allow for up to five seconds after a
  subtle change; activity and window changes clear it.
- No model, threshold or accuracy change. No adult-positive corpus and no phone
  run. Native TFLite/GPU remains the larger first-inference win.

## Android 1.3.1 — 2026-11-07

### Changed

- **The visual guard starts on WASM instead of trying WebGL first.**
  `VisualClassifier` requests `wasm`; the engine still falls back to WebGL and
  then CPU if WASM is unavailable. On the API 36 emulator the `auto` ladder
  chose WebGL (3.3 s load, 188 ms median on 12 benign views); the WASM path
  loaded in 3.0 s and measured 123 ms median. An earlier `auto` run spent 12.1 s
  loading WebGL.
- **An uncertain full frame gets one closer look.** When the full-frame
  Porn/Hentai scores are at least 0.45 (or sum 0.60) but below the block
  threshold, the centred 68% of the frame is cropped and classified once more;
  it blocks only if the same 0.70/0.85 thresholds are met. A failed second look
  keeps the first `allow`. No model, block threshold, capture size or polling
  interval changed.
- **`region.js` and a Node test.** The near-miss and crop logic is a pure
  module served from the packaged assets; `region.test.mjs` checks thresholds,
  geometry, pixel offset and malformed input.

### Verification

- `node android/visual-eval/region.test.mjs` — **PASS**.
- `android/test-core.sh`, strings (563/16/554), notices, `assembleDebug`,
  `assembleDebugAndroidTest`, `lintDebug` (0 errors) and `CoreDeviceTestRunner`
  on the API 36 emulator — **PASS**.
- Real packaged model on 12 benign views × 3: **0 false blocks** in the WASM,
  `auto` and 1.3.0 baseline runs. Raw reports:
  [benchmark JSON](docs/ANDROID_VISUAL_1.3.1_2026-11-07.json).
- Signed Play APK/AAB and direct APK pass `check-release.py` and `apksigner`.
  Hashes and limits: [1.3.1 record](docs/ANDROID_1.3.1.md).

### Limits

- No adult-positive corpus or adult-accuracy claim. Benign controls stayed at
  0 false blocks; the closer look only helps a frame already near the threshold.
- The crop is centred and 68%; edge and very small regions may still be missed.
  A four-tile sweep is not implemented.
- Emulator and benign images only; no phone, battery, thermal or memory
  measurement. WebGL may be faster on a real GPU; the fallback remains.
- The model load is still about three seconds; the warm model and 224 px capture
  are unchanged.

## Android 1.3.0 — 2026-11-07

### Added

- **A per-app quiet period after a detection.** The app whose screen was
  detected stays closed for **1, 5, 15, 30 or 60 minutes**, default **5**. Only
  that app is held; another selected app is unaffected. Reopening inside the
  period returns Home and shows the pause screen with the remaining time. The
  period is measured by the elapsed clock and is not a background job.
- **Settings › Protection › After a block.** The row reads *Keep this app closed
  for 5 min* and opens the 1/5/15/30/60-minute choice. The value is stored apart
  from the sealed rule file because it is a comfort setting: changing it never
  lets a matched word or image pass.
- **Rest longer and lock the screen on the pause screen.** After a word or
  image detection it shows *Hisn closed this app for about 5 min*, **Rest 5 more
  minutes**, **Rest 15 more minutes**, **Rest 1 hour** and **Lock the screen**.
  A rest extends the hold for that app; the lock uses Android's accessibility
  lock action (API 28+). Both return Home.
- `app.hisn.core.RestPeriod` and its tests: fixed choices, invalid-value
  fallback, milliseconds and rounded remaining minutes.

### Changed

- **A browser's page is left before its window is closed.** For an app that can
  open web links, the guard performs Back and then Home, so reopening the
  browser does not restore the page that was just detected. Other apps only
  receive Home. This uses the accessibility actions the guard already had; it
  does not force-stop another app.
- The fixed one-minute hold becomes the configured quiet period. The live
  fixtures select one minute so their 62-second recovery checks still work; the
  product default is five. No detection rule, threshold, permission or consent
  text changed.

### Verification

- `android/test-core.sh` — every core suite passes, including `RestPeriodTest`.
- `check-strings.py` — 563 strings, 16 plurals and 554 source references,
  English and Arabic; `check-notices.py` passes.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest` and `:app:lintDebug`
  (0 errors) pass; `CoreDeviceTestRunner` passes on the API 36 emulator.
- The Settings row and picker, and the pause screen's rest/lock card, were read
  back through the emulator accessibility tree and captured in
  [design/android-after-block-2026-11-07](design/android-after-block-2026-11-07/README.md).
- Signed Play APK/AAB and direct APK pass `check-release.py`; both APKs verify
  with `apksigner` under the unchanged release certificate. Hashes and the
  remaining limits are in the [1.3.0 record](docs/ANDROID_1.3.0.md).

### Limits

- Android cannot force-stop another app or power off the phone. “Close” is
  Back/Home; the app stays in Recents and a browser may keep its tabs. Back
  depends on the browser's history.
- The screen lock is API 28+, turns the display off and keeps the normal unlock;
  it is not a phone shutdown and does not change the PIN.
- The quiet period is not sealed with the protection lock: it never changes
  detection, but it can be shortened during a lock. The Rest action only
  lengthens it.
- No real detection drove the new close path: the screens were opened through
  the debug restriction fixture, no adult content was used, and no 1.3.0 phone
  run is claimed. The Arabic labels are parity-checked but not captured.

## Android 1.2.3 — 2026-11-07

### Changed

- **The text guard reads the screen off its main thread.** `TextGuardService`
  now fetches the selected windows' roots on the main thread and walks them on
  one daemon scanner thread; Android resolves every `getChild()` across a
  Binder, so a large feed could previously stall the service, and the app being
  read. The verdict returns to the main thread, where Home, the restriction
  screen and every policy write still happen. A generation token discards a
  walk whose screen changed, and a 20-second watchdog records an incomplete
  inspection, replaces a stuck scanner executor and retries instead of leaving
  the layer silent.
- **A selected app's accessibility event can block before the walk.** The
  event's own text and source description are inspected transiently, never
  stored. A promotion label ("Watch free porn") blocks immediately; a bare
  explicit label ("Porn") still waits for the full pass, which knows whether
  another line on the screen names protective context. Only the selected window
  in use may act before the walk; a queued event from a window the person
  already left falls back to the full pass. When the event carries no text, the
  tree walk is the fallback.
- **The walk reads 2,048 nodes, checks a partial match every 64 nodes and can
  stop early.** The old 512-node budget cut off the label in use on large real
  screens; the new budget and checkpoint stop a matching word from waiting for
  the rest of a long feed. A selected app is still re-read at least every three
  seconds, so text that appears without a text-bearing event is not missed. A
  clean screen no longer re-walks on every content change: a video frame or
  animation does not mark text dirty.
- **The image layer keeps the model warm and stops retrying protected windows
  four times a second.** The WebView and model stay loaded while the screen is
  on, and a two-minute screen-off grace keeps them for an immediate screen-on.
  A capture's long edge is 224 px instead of 384, a frame is treated as failed
  after 5 s instead of 15 s, and a window Android refuses to capture backs off
  1, 2 then 4 s. A window change clears that backoff and the sampling interval.
  The debug status reports the real backend, frame edge and capture, inference
  and sample timings.

### Added

- `LabelScan.Check`, `LabelScan.CHECK_INTERVAL` and the checkpoint overload;
  `VisibleTextSample.isEmpty()`; the scanner executor and watchdog; the
  `stress` fixture (700 labels, the word at 650) and its live case; and
  `BACKEND`/`FRAME_EDGE`/`LAST_CAPTURE_MS`/`LAST_INFERENCE_MS`/`LAST_SAMPLE_MS`
  in the visual test status. No permission, activity, service, string or stored
  field was added.

### Verification

- `android/test-core.sh` — every core suite passes, including the new
  `LabelScan` cases for the 2,048-node budget: the old 512-node budget cuts a
  700-label screen and the old 1,024-node budget cuts a 1,500-label screen; the
  current budget reads both. The updated `VisualDuty` screen-off grace passes.
- `check-strings.py` — 555 strings, 16 plurals and 545 source references,
  English and Arabic; `check-notices.py` passes.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest` and `:app:lintDebug`
  (0 errors) pass.
- `CoreDeviceTestRunner` passes on the API 36 `hisn_ui` emulator: seed, Arabic
  matching, local DNS block, saved rules, adult terms, visual settings, daily
  limits, commitment, uninstall guard, layer-watch and seed-age checks. The
  live `stress` case — 700 labels with the word at 650 — blocked on the same
  emulator after the accessibility service was bound; the status recorded a
  tree scan (`FAST_TEXT 0`, `SCANS 7`, `LAST_SCAN_MS 21`), so the walk read past
  the old budget.
- Signed Play APK/AAB and direct APK pass `check-release.py`; both APKs verify
  with `apksigner` under the unchanged release certificate. Hashes and the
  remaining limits are in the [1.2.3 record](docs/ANDROID_1.2.3.md).

### Limits

- No 1.2.3 phone run. 1.2.2 was installed on the owner's Samsung SM-G990B2;
  this build was not.
- No device latency, memory, thermal or battery benchmark. The gain is a
  design consequence; the only measured image numbers remain the
  [WASM engine note](docs/VISUAL_ENGINE_WASM_2026-11-07.md) on a Mac and in
  Node.
- The 224 px edge is a cost choice, not an accuracy result. No adult-positive
  corpus was used, and adult recall remains unestablished.
- The event fast path is unit-covered through the shared matcher, not
  live-verified here: the manual emulator event did not reach the service as a
  text-bearing event, so the tree scan decided. The full walk remains its
  fallback.
- Keeping the model warm while the screen is on trades memory and battery for a
  faster next sample; the two-minute grace was chosen without a power
  measurement.
- The 2,048-node budget is not device-measured. The live `stress` fixture is
  700 labels; the 1,500-label case is unit-tested. No frame-time, memory or
  battery number was taken for the extra Binder work.
- Repeated package reinstalls and a System UI ANR made later emulator live runs
  intermittent. The full ten-check live suite is not claimed for 1.2.3.

## Mac 1.1.18 — 2026-11-07

### Added

- **Optional distraction-site chips in first run and Blocking Rules.** Twelve
  named sites — YouTube, Reddit, X, Instagram, Facebook, TikTok, Twitch, Netflix,
  Hacker News, Amazon, Pinterest and LinkedIn — can be added to the person's
  block list with one tap. Every chip names the site; there is no category that
  hides what is blocked. A chip writes through the existing guarded
  `SiteLists.save`, so a selected chip is already in the list the filter and
  the extension read. A site covered by a wider hand-written rule is shown as
  blocked and the chip does not remove the parent rule. Adding still works
  during a lock or a managed policy; removal still waits. The adult-content
  lists, lock, partner path, browser guard and private-window policy are
  unchanged.
- **A three-decision first run.** The welcome sheet now has three steps with a
  three-segment progress bar: how the layers work, the optional distraction
  sites, and the private-window decision. **Not now** on the private-window
  step continues to Setup instead of closing the pass.
- **MDM enrollment is read as its own fact.** `ManagedMode` carries
  `MDMEnrollment`, populated by `/usr/bin/profiles status -type enrollment`.
  The probe is bounded to two seconds, cached for ten minutes, and runs only
  after the forced `HisnManaged` flag is present, so an unmanaged Mac never
  pays for it. The **Organization policy** layer distinguishes **DEP and MDM**,
  **enrolled in MDM**, an **unapproved enrollment**, a **local profile** and
  an unreadable answer; none of them becomes "not managed".
- **The MDM profile can own the filter.** `profile/make_profile.py --mdm
  --team-id` emits a `com.apple.webcontent-filter` plugin payload for the
  signed Hisn extension and a `com.apple.system-extension-policy` with
  `AllowedSystemExtensions`, `NonRemovableSystemExtensions` and
  `NonRemovableFromUISystemExtensions`; on macOS 15+ with SIP the filter cannot
  be disabled or uninstalled from System Settings or Finder. The same build
  blocks Erase All Content and Settings, and on macOS 26+ sets
  `SafariHistoryRetentionEnabled` so Safari private browsing and history
  clearing are unavailable. `--maximum` is the supervised-fleet preset and
  refuses every weakening flag.
- **The app leaves a profile-owned filter alone.** The managed payload carries
  `HisnProfileVersion=2` and `HisnFilterManaged`; the app stops replacing or
  disabling the filter and the Setup control says the organization owns it.
  **Settings › Device management** shows the profile version and opens a guided
  handoff to Device Management or an organization-supplied profile; it cannot
  enroll the Mac or accept for the person.
- **`verify_mdm.sh` checks the layers.** It reads the managed-preferences
  plists for the profile version, managed-filter declaration, DNS,
  `ProhibitDisablement`, Private Relay, Erase and browser policy, then reads
  `systemextensionsctl` for the filter extension. `--strict` requires the full
  set and exits 1 when a layer is missing; the default exit codes stay 0/1/2.

### Changed

- **Blocking Rules is one list card.** A `Block list / Allow list` segmented
  control replaces the two full editors printed one above the other. The card
  keeps the established **Always block these** / **Always allowed** headings,
  per-list empty copy, one shared Save/Revert pair and a **Check a website**
  action beside the lists. Both drafts stay in memory, so switching tabs loses
  no edit and the pair guard is unchanged.
- **One vocabulary for the lists.** Site-checker and chip actions now say
  **block list** instead of **Always block**, matching the Rules card. The
  list headings and the allow-list explanations keep their established names.

### Redesigned — craft pass

- **The Mac window is no longer a card grid.** Warm paper and ink surfaces with
  a deep ink-blue accent replace the default system-blue cards; a New York
  serif statement marks one answer per page; tracked section labels, 28-point
  controls and hairlines replace the repeated rounded heading cards and the
  "Explain" text on every row. The info button is now icon-only.
- **Overview** is a two-column composition: one layer table and a narrow rail
  for the next step, today's numbers and tools. The next daily lock is
  actionable from the rail. **Lock** is a compact composer with duration chips,
  a serif length figure, mode captions, a collapsed daily schedule and a slim
  footer. **Setup** collapses network protection into an optional disclosure
  and moves **Check again** next to the status. **Settings** is a native grouped
  form, with the mislabelled visual-checks card fixed to
  **Browser extension → Visual checks in the extension**. **Rules** groups words
  and apps with their Save row. The welcome sheet and pause use the same
  editorial voice; the pause now has circular breath progress.
- No protection rule, consent sentence, partner path, lock rule or privacy
  promise changed. No screen-time budget, timed disable, streak or account was
  added.
- Record: [Mac craft pass](docs/MACOS_CRAFT_2026-11-07.md); captures:
  [design/macos-craft-2026-11-07](design/macos-craft-2026-11-07/README.md).

### Verification

- `HisnTests/ExtraSitesTests` — 6 data tests.
- `HisnTests/MacUIRefreshTests/testExtraSitesRenderForReview` — English and
  Arabic first-run and picker captures, including selected and parent-covered
  chips.
- The Rules page is in the full page render and the Arabic render set.
- `python3 macos/check_localization.py --xliff …` — 847 strings, all with
  Arabic.
- Full Mac suite — 601 tests, 0 failures; `profile` suite — 127 tests, 0
  failures, including the generator and verifier parser.

Records:
[Mac extra sites and rules](docs/MACOS_EXTRA_SITES_2026-11-07.md), the
[Mac craft pass](docs/MACOS_CRAFT_2026-11-07.md),
[MDM deployment](docs/MDM_DEPLOYMENT.md) and the
[Mac 1.1.18 MDM note](docs/MACOS_1.1.18.md); current captures:
[design/macos-craft-2026-11-07](design/macos-craft-2026-11-07/README.md).

### Limits

- No user study, VoiceOver audit or physical-Mac install.
- The chips are a convenience over the existing custom block list, not a new
  protection layer.
- The time-budget, timed-disable, open-count and streak ideas visible in the
  ScreenZen reference were deliberately not added: they would introduce a
  weakening surface or a motivational claim the product does not make.
- A signed Developer ID build is required for the managed filter; a wrong
  designated requirement leaves a complete-looking profile that cannot load
  the extension. `NonRemovableSystemExtensions` needs macOS 15+ and SIP, and
  `SafariHistoryRetentionEnabled` needs macOS 26+; Recovery and the MDM console
  still remove the extension. No MDM-enrolled Mac or positive deployment run
  was available.
- The filter is socket-level, not Apple's OS 26 URL Filter; encrypted posts and
  videos are not classified.

## iPhone & iPad 1.8.0 — 2026-11-07

### Added

- **A device-wide DNS filter.** `HisnDNSProxy` is a Network Extension DNS
  proxy: while it is on, iOS sends system DNS questions through Hisn. The
  signed core tier (236,586 domains), the four adult TLDs, the 116 reviewed
  hostname terms and the person's saved sites are answered locally with
  NXDOMAIN; allowed names are resolved by Cloudflare Families over HTTPS with
  a validated fallback. This replaces the old Family DNS profile, which used
  Cloudflare's categories rather than Hisn's list and covered no non-WebKit
  app. UDP and TCP flows, EDNS payload sizes, TC fallback, response matching
  and a bounded query gate are implemented; nothing is logged or stored.
- **Build-verified DNS data.** `ios/prepare_rules.py --dns` verifies the
  Ed25519-signed seed and the signed `terms.json` and writes
  `dns-domains.txt`, `dns-host-terms.json` and `dns-metadata.json` into the
  extension. The extension re-checks both SHA-256 digests and the count before
  it will start; a damaged domain list fails closed, and a damaged terms file
  leaves the list enforcing.
- **The route check.** The app asks the system resolver for a fresh random
  name under the reserved `hisn.invalid` domain and expects Hisn's own
  `203.0.113.7`. It is labelled as evidence about this app's resolver path,
  not about every app.
- **The direct build.** `ios/direct/build_direct.sh` builds a Release IPA
  outside the App Store, checks every embedded extension and its data, and
  writes the IPA's size and SHA-256 to `build-info.json`. `--adhoc` signs each
  bundle with its entitlement requests. `ios/direct/make_source.py` writes and
  checks the AltStore/SideStore source JSON from the IPA's own bytes.
  [docs/IOS_DIRECT.md](docs/IOS_DIRECT.md) records the install paths and the
  entitlement facts.
- **Shared policy code.** `TextNormalizer`, `Punycode` and `AdultTldGuard`
  moved to `shared/apple/`, so the Mac and the iOS DNS filter compile one copy
  of the keyword and adult-TLD rules. The Mac project follows the move; its
  behaviour is unchanged.

### Changed

- **The DNS layer is now Hisn's own policy.** The app's Setup → DNS section is
  **DNS filter** with On/Off, readback (On · all apps, Saved · not on, Another
  DNS filter is on, Off, Could not check) and the route check. A commitment
  freezes turning it off; turning it on only tightens.
- The app entitlement moves from `dns-settings` to `dns-proxy`. A provisioning
  profile that only carries DNS Settings must be reissued. The Family Controls
  requirement for the Screen Time layers is unchanged, and the direct build
  does not bypass it.
- The person's saved blocked sites are mirrored to the App Group for the DNS
  extension, which re-reads them on a short cache; the Screen Time filter
  keeps its own copy.
- Both localizations rewrite the DNS copy to describe system-DNS scope,
  DoH/DoT bypass, Cloudflare visibility, the absence of history and the
  route-check limit. English and Arabic keys stay identical.
- Version 1.8.0, build 14.

### Verification

- `./test.sh python`: the rules suite adds four DNS-generation tests (exact
  bytes and digests, determinism, the shipped seed, and rejection of tampered,
  missing, wrongly-versioned or invalid terms); the privacy suite pins the
  `com.apple.networkextension.dns-proxy` extension point and both entitlement
  files; the new `test_direct.py` builds a fake IPA, checks the source JSON
  against its bytes, refuses HTTP or non-IPA URLs and syntax-checks the build
  script.
- `./test.sh ios`, 7 November 2026 on the iPhone 17 simulator (iOS 26.5):
  **55 Python and 271 simulator tests, 0 failures**. The 19 new simulator tests
  in `DNSFilterTests.swift` cover the query parser and its hostile inputs,
  NXDOMAIN and TC replies, response matching, the domain index, the hostname
  rules and punycode, the engine's reasons and asset digests, and the
  resolver's fallback and size bounds.
- **Not exercised:** the Network Extension provider, the iOS permission
  prompt, real UDP/TCP flow handling, Cloudflare reachability, the route
  check on a device, battery, memory, and the direct install. The Simulator
  cannot run a DNS proxy; `test.sh ios` removes the appex before installing
  the test host. No signed IPA, App Store upload or physical-device
  acceptance is claimed.

[the 1.8.0 record](docs/IOS_1.8.0.md) ·
[direct distribution](docs/IOS_DIRECT.md) ·
[the iOS README](ios/README.md)

## Browser extension 1.5.3 — 2026-11-06

### Added

- **An always-on adult-TLD guard.** `rules/adult_tlds.json` is a new static
  ruleset at `PRIORITY_LIST`: a document whose host ends in `.adult`, `.porn`,
  `.sex` or `.xxx` lands on the block page with the site-list reason, and every
  embedded resource type from such a host is dropped. A name registered after
  the last list build is therefore blocked without waiting for a list update.
  The hand allowlist still outranks it, as it does the published list.
- `lib/policy.js` exports `ADULT_TLDS` and `isAdultTld()`. `decide` models the
  guard for browser-only installs and the open-tab sweep now sends an already
  open guarded tab to the block page with the `domain` reason.
- `test/adult-tlds.test.js` asserts the ruleset's two rules, the four TLDs and
  nothing else, and the URL boundaries: absolute FQDN trailing dot, userinfo,
  port, subdomains, `notxxx.com`, `porn.example.com`, `xxx.evil.com`, a path and
  the deliberate `.cam` non-guard.
- `blocklist/terms/policy_cases.json` gains five cross-language cases; the Mac
  `PolicyContractTests` and the extension's `policy.test.js` both assert them.

### Changed

- The ruleset is appended last in `manifest.json`, never inserted before an
  existing one: Chromium keys its compiled static-rule cache by position.
  `ruleset-order.test.js` records it, `check_package.py` pins it to the repo
  bytes, and `MANIFEST.md` explains the guard.
- Version 1.5.3. The packaged zip name follows the manifest version.

### Fixed

- **The popup status renders whole again.** `popup.js` still referenced the
  `badge` and `appLevel` identifiers that 1.5.1 deleted with the private-window
  row. The `getState` promise rejected after the hero and three layers were
  painted, so the Hisn-app row stuck on "Checking…", the lock block stayed an
  empty amber strip, and notices, the quick-block row and Details never
  rendered. The app row paints its indicator and its word from one connection
  state again, and a browser check now fails on any popup script error or
  unhandled rejection.
- **The embedded settings use the popup they are in.** The editor hides the
  duplicate popup header, keeps one 38 px bar (Back, language, Open full page),
  and gives the frame 456×534. Inside it, the five sections share one tab row
  with no horizontal scrollbar (Settings was previously off-screen), the
  language row, pinned full-page footer and shortcut tiles that repeat the
  tabs are hidden, the page area grows from roughly 270 px to 461 px, and the
  scroll region no longer draws a clipped focus ring. The language switch stays
  in the compact bar, and the ambiguous Settings tab is renamed Content beside
  its "Content options" heading.
- Captures and measurements:
  [design/extension-popup-2026-11-06](design/extension-popup-2026-11-06/README.md);
  engineering record:
  [the popup UX note](docs/EXTENSION_POPUP_UX_2026-11-06.md).

### Verification

- `extension/test/run.sh` — all JavaScript suites pass, including 63/63
  adult-TLD checks and the shared policy fixture.
- `extension/test/browser/run.sh popup.html` — PASS, including the new
  no-script-error check, the app-row paint, the compact editor chrome and focus,
  and the editor language switch.
- `extension/test/browser/run.sh embedded.html` — PASS on the new focused
  harness: five tabs in one 452 px row in English and Arabic, no horizontal
  overflow, hidden full-page furniture, a 461 px page area, the Repair action
  above the fold, no scroll-region focus ring, and the narrow wrap fallback.
  `settings.html` remains timing-sensitive in this harness on a loaded machine
  (the file-import scenarios fail on some runs with the same files restored);
  that is recorded, not hidden.
- `./test.sh extension` — the unpacked-load Python checks pass (45 tests), the
  package check passes, and the evaluation corpus keeps its 0/22 adult misses
  and 0/28 benign blocks. The browser-runner exit-status test is timing
  sensitive under load and passed when rerun alone; the full browser suite was
  not run on this machine.

### Limits

- The guard covers four TLDs, not every generic TLD; a random new `.com` still
  depends on the list or the 116 hostname terms.
- `.cam` is deliberately excluded: it has ordinary camera and webcam uses.
- Static rulesets are enforcement only where Chrome sees the request; a
  service-worker cache response is unchanged.
- The popup captures are isolated previews with a mocked bridge at 480×600,
  not a real toolbar popup. The embedded settings page still scrolls as a
  dashboard, and no user study, screen-reader audit or physical-device review
  was run.

## Mac 1.1.17 — 2026-11-06

### Added

- **An explicit adult-TLD rail.** `AdultTldGuard.swift` (shared by the app, the
  filter and the bridge) treats a final label of `.adult`, `.porn`, `.sex` or
  `.xxx` as blocked. `BlocklistStore.blocked` checks it after the hand
  allowance and before the published list, so the answer does not depend on a
  list generation or on `terms.json` loading. `.cam` is not guarded.
- The site checker names the rule: a new `SiteCheckResult.Source.adultTld`
  answer says the address ends in an adult-only TLD, in English and Arabic. It
  answers even when the installed list cannot be read, rather than reporting
  `listUnavailable`.

### Verification

- `./test.sh macos` — 578 XCTest cases, 0 failures; 721 strings, all with
  Arabic. The new `AdultTldGuardTests` and `SiteCheckerTests` cases pass.

### Limits

- Policy constant, not a list update: a new domain under any other TLD is still
  the list's job.
- Unsigned/unnotarized development build; no Developer ID, notarization or
  physical-device acceptance is claimed.
- The rare non-adult site on an adult-sponsored TLD is over-blocked by design;
  the hand allowance is the escape hatch.

## iPhone & iPad 1.7.2 — 2026-11-06

### Added

- **Adult-TLD rules in the Safari blockers.** `prepare_rules.py` appends one
  rule per adult-sponsored TLD (`.adult`, `.porn`, `.sex`, `.xxx`) to the final
  blocker part, which had 3,414 unused slots. The generated total becomes
  236,590 rules, still below the 240,000-rule cap. A name registered after the
  seed build is blocked in Safari instead of waiting for an app update.
- `ios/test_rules.py` asserts the guard is only in the last part, that a full
  final part plus the guard is rejected rather than truncated, and the URL
  boundaries (`notxxx.com`, `porn.example.com`, `xxx.evil.com`, a path and
  `.cam` are allowed).
- The WebKit compile test now compiles the final part with the guard rules, so
  the Safari regex subset is checked by the engine, not only by Python.

### Verification

- `python3 -m unittest discover -s ios -p 'test_*.py'` — 44 tests pass.
- `./test.sh ios` — 252 simulator tests, 0 failures; WebKit compiled all six
  Safari parts, including the guard, in 311.6 s of the part-compile test.

### Limits

- Simulator-tested only. Real Safari shielding and the enabled state of all six
  blockers need a signed, entitled device.
- The guard lives in the final blocker part; losing that part loses the guard
  with its domain slice.
- `.cam` is deliberately excluded; other new TLDs and random names still need
  the list or Hisn Text.
- No runtime list update was added.

## Android 1.2.2 — 2026-11-06

### Added

- **The hostname-keyword layer reaches Android DNS.** `HostTermPolicy` applies
  the same contract as the Mac and Chrome: 59 token terms, 57 substring terms,
  `never_keyword` removal, keyword-exempt hosts, Arabic/Persian folds, punycode
  decoding, trailing-digit and keyboard-hold variants. A new host such as
  `arab-sex-tube.com` is blocked by the DNS tunnel even though no seed list
  carries it, while `essex.gov.uk`, `sussex.ac.uk`, `tasks.office.com`,
  `milford.ct.us`, `pornic.fr`, JINS and Kos stay reachable. Decisions are
  cached (bounded at 8,192).
- **An adult-TLD rail in the same policy.** A name ending in `.adult`, `.porn`,
  `.sex` or `.xxx` is blocked before the seed lookup. Both rails sit after the
  person's saved domain allowance and before the seed, and the same composed
  policy judges CNAME/DNAME/SVCB targets through `DnsReplyGuard`.
- `HostTermPolicyTest` asserts all 42 shared `host_cases.json` cases against
  the real signed terms plus the named safety cases and the malformed-policy
  refusals. `AdultTldPolicyTest` covers the TLD rail and the `.cam` exclusion.
- `AdultPolicyAssets` now reads and verifies `terms.json` once for the DNS
  service before the tunnel starts, using the existing pinned
  `TERMS_SEED_SHA256`; a failure leaves the seed enforcing and the keyword
  layer off rather than loading unverified bytes.

### Changed

- **The browser coverage dialog answers the private-window question where the
  browser list is.** It now states that incognito tabs still get the DNS layer
  and the text and image inspection, and that Android cannot switch another
  browser's private mode off unless the phone is managed by a device owner. Two
  strings, English and Arabic; no layer, rule or permission changed. The
  coverage analysis behind it is in
  [docs/ANDROID_PRIVATE_WINDOWS_AND_PLATFORMS_2026-11-06.md](docs/ANDROID_PRIVATE_WINDOWS_AND_PLATFORMS_2026-11-06.md).

### Verification

- `android/test-core.sh` (JDK 17+) — every core suite passes, including the two
  new classes. `check-strings.py` and `check-notices.py` pass; no string,
  permission, activity, service or stored field changed.

### Limits

- Android DNS still cannot see paths, queries, IP literals, DoH to an unlisted
  resolver, or another VPN; the RSA remain the layer's largest holes. The
  guard closes the hostname half only.
- A hand allowance outranks both rails; that is the false-positive escape hatch.
- No Play upload is claimed and the seed is unchanged. 1.2.1's
  accessibility-grant and classifier fixes are retained. A 1.2.2 build
  (versionCode 31) was compiled and installed in place on the owner's phone
  after this entry: it carries this entry's DNS policy, the 1.2.1 fixes and the
  protection pass's service-lifecycle repair — the guard now arms itself from
  its first accessibility event when Android rebinds it without repeating
  `onServiceConnected`. Record:
  [docs/ANDROID_PROTECTION_PASS_2026-11-06.md](docs/ANDROID_PROTECTION_PASS_2026-11-06.md).

## Android 1.2.1 — 2026-11-06

### Fixed

- **A granted accessibility service could read as "permission required".** After
  the service was turned back on in Android Settings, the dashboard still showed
  the layer as missing while Settings showed it on and `dumpsys accessibility`
  listed it enabled and bound: the check trusted only
  `AccessibilityManager.getEnabledAccessibilityServiceList`, whose cached copy a
  long-lived process can keep stale. `app.hisn.core.AccessibilityGrant` now reads
  Android's own `enabled_accessibility_services` value as the second source,
  gated on `accessibility_enabled = 1`, and accepts both entry formats Android
  writes. A bound-but-stale service now reads **reconnect required**, never a
  missing permission. Both call sites (`Permissions.accessibility` and
  `MainActivity.guardPermissionEnabled`) use it.
- **A classifier failure blinded the image layer for a flat 30 s.**
  `VisualGuard.fail()` freed the model and refused sampling for 30 s, so one
  killed WebView renderer, JavaScript timeout or dropped frame cost half a
  minute plus a ~2.2 s model reload. `app.hisn.core.VisualRetry` retries after
  **5 s**, doubles each further failure, keeps the previous **30 s** cap and is
  cleared by any finished sample. The engine is still freed on failure, so a
  broken engine cannot loop.

### Changed

- `test-live-visual.py` is hold-aware: a detection holds the app for
  `TextGuardService.BLOCK_HOLD_MILLIS` (60 s), so the driver now asserts the
  hold, waits it out and only then asserts that ordinary sampling recovers. Its
  adb timeout went 25 → 45 s. The ten-check suite passes against 1.2.1.

### Verification

- `android/test-core.sh` — all suites pass, with the new `AccessibilityGrant`
  (both entry formats, separators, near-miss packages, the isolated test
  package) and `VisualRetry` (5/10/20/30 s, cap, reset) tests; strings pass with
  553 strings and 16 plurals in English and Arabic.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest` and
  `:app:assembleRelease -PhisnDistribution=direct` pass; `:app:lintDebug`
  reports 0 errors.
- `test-live-visual.py` on the owner's Samsung SM-G990B2, isolated visual-check
  build next to the owner's app: 10 checks pass — real capture and benign
  inference, moving-frame sampling, editable and password pause, secure window
  unreadable, synthetic intervention after a real verdict, the 60 s hold, the
  hold ending with sampling recovered, leaving the app, and opt-out.
- Measured on the same phone with the real model: 8 samples in 30 s on a still
  fixture (~3.8 s) and 17 in 30 s on a moving one (~1.8 s), matching the 2 s
  base, the 1 s fast interval and the still back-off.
- 1.2.1 (versionCode 30) installed in place over 1.2.0 with the owner's key;
  saved rules and the running protection lock survived, DNS filtering was
  restarted and the guard reconnected. Record:
  [docs/ANDROID_PROTECTION_PASS_2026-11-06.md](docs/ANDROID_PROTECTION_PASS_2026-11-06.md).

### Limits

- No adult-positive corpus was used. The image layer's adult recall and
  precision remain unestablished, and the fixture block is a synthetic hook
  after a real allow verdict, not a model detection.
- Real applications, real video playback, battery, thermal and long-run
  behaviour were not measured; the cadence numbers come from synthetic fixtures.
- The stale-cache condition was observed on the phone but not reproduced under
  control; the new parser is unit-tested and the wiring is two call sites.
- DNS query failures on this network (54 → 55 over a minute while 85 → 91
  queries were forwarded) are reported, not explained; no DNS behaviour changed.
- The accessibility service was destroyed and re-created repeatedly on the test
  phone while it was driven from adb, and the guard's reported state followed
  it. Battery optimisation is not the cause (device-idle whitelisted,
  `RUN_ANY_IN_BACKGROUND` allowed); the cause was not established, and the guard
  reconnected after a full disable/enable. A quiet-phone capture around a drop
  is the next step.

## Android 1.2.0 — 2026-11-06

### Changed

- **One line per rule.** The Protection rule list answers in a trailing column:
  a 22 dp line icon, a title, and **On**, **Off**, a time range, **7 hours
  left** or an app count at the end of the row. The filled icon tile is gone,
  rows are 54 dp high instead of 62–74 dp, and the seven rules now fit above the
  fold on the owner's phone instead of needing about 1,250 of 2,340 px.
- **Section labels instead of icon-tiled headers.** A quiet 12.5 sp label sits
  above each card, so a page has one heading level less and only items carry
  icons. Applied to Protection, Rules and Settings.
- **The app bar names the page.** Protection keeps **Hisn** and its tagline;
  Rules and Settings show their own names. The separate page titles are gone,
  the bar is 56 dp high instead of 68 dp, and the duplicate heading is removed.
- **The hero follows its verdict.** The Protection card is amber with an amber
  border while something needs attention and the blue accent otherwise; the mark
  is 92 dp instead of 116 dp.
- **The DNS card states the consequence.** Its header carries a state pill and
  one honest sentence. When the layer is off it says *Not running. No domain is
  filtered until you turn this on.* (`dns_off_body`, English and Arabic) instead
  of repeating `DNS filtering · Off` under a header that already said it. The
  dead **DNS filtering is on** button is gone: a start action appears only when
  there is something to start.
- **App protection is a status row, not a card.** On Rules it is one row with
  its state and the same disclosure the old button opened, in place of a card
  that held a lone "Connected" line.
- **Frozen rows stay readable.** A locked row shows a lock where the chevron was
  at 0.72 alpha; disabled controls sit at 0.58 instead of 0.45, which failed
  contrast on the second line.
- **Row summaries stop repeating their own titles** — `Daily time limits · Off`
  becomes `Off`, `Protection lock · About 8 hours remaining` becomes
  `About 8 hours remaining`, and the same for the schedule and its all-apps
  variant, in English and Arabic.
- **Smaller screens.** The voluntary pause's exit is a quiet button rather than
  the loudest control (an enforced pause keeps the way out primary); the
  first-run mark is 124 dp (116 dp on the lock page); the pause row uses a pause
  icon instead of the diagnostics bars; **Licenses and sources** no longer
  describes itself in a second line.

### Added

- `Ui.sectionLabel`, `Ui.statusPill`, trailing-value rows with
  `SettingRow.setValue` / `setLocked`, the `bg_hero_attention` drawable, the
  `ic_pause` drawable and the `dns_off_body` string in English and Arabic. No
  new permission, activity, service or stored field.

### Verification

- `android/check-strings.py` — 553 strings, 16 plurals and 543 source references
  matched in English and Arabic; `android/check-notices.py` passes;
  `android/test-core.sh` passes every core suite.
- `:app:assembleDebug`, `:app:lintDebug` (0 errors) and a release-signed direct
  build (`-PhisnDistribution=direct`) pass.
- The release APK was installed in place over 1.1.1 on the owner's Samsung
  SM-G990B2 (Android 16, 1080 × 2340, 420 dpi override) with the owner's key.
  Saved rules and the running protection lock were preserved; Android disabled
  the accessibility service when the package was replaced, it was re-enabled in
  Settings and the guard reconnected, and DNS filtering was started again from
  the app's own consent sheet. Captures in English light, English dark and
  Arabic dark:
  [design/android-ui-2026-11-06](design/android-ui-2026-11-06/README.md), record
  in [docs/ANDROID_UI_2026-11-06.md](docs/ANDROID_UI_2026-11-06.md).

### Limits

- Presentation only: no enforcement layer, permission, stored value, disclosure
  or commitment rule changed, and nothing new is claimed about what any layer
  blocks.
- Large font scales, tablets, landscape and every row state were not captured;
  the trailing answer is one line with an end ellipsis.
- ScreenZen was observed read-only on the same phone for structure only; no
  code, asset, string, colour or data model was copied.
- No signed APK is published and no Play upload is claimed. The APK built here
  is local.

## iPhone & iPad 1.7.1 — 2026-11-06

### Added

- **Chosen weekdays for quiet hours.** A Monday-first day mask on the
  quiet-hours rule: rules saved before this release run every day, a window that
  crosses midnight belongs to the day it starts on, and each window leg is
  checked against that day by the monitor (the morning leg checks yesterday).
  The editor gains day chips with the UI language's initials and every-day /
  weekdays / weekend presets; the rule row says *weekdays*, *weekends* or *N
  days*. A weekday change is frozen during a commitment like every other field.
- **End now for a running pause session.** Today and the Apps rule row show a
  running session with the minutes it bought and an action that re-shields
  immediately. It only tightens, so a commitment does not refuse it.
- **The commitment deadline on Today.** An active commitment now shows its end
  date on the first screen instead of only in More.
- **Handoff guidance before iOS 26.5.** The pause editor explains that the
  shield's **Pause first** reaches Hisn through a notification and offers to
  allow notifications, or to open Settings when they were declined.

### Changed

- The quiet-hours sentence no longer says "every day"; the days phrase carries
  that, and the shield copy no longer calls the window daily.
- `apps.windowScope` states the day rule, including the midnight case.

### Verification

- `./test.sh ios` — **40 Python and 252 simulator tests, 0 failures** on the
  iPhone 17 simulator (iOS 26.5); nine new weekday tests.
- No entitlement, signed build or device acceptance is claimed. Record:
  [the 1.7.1 note](docs/IOS_1.7.1.md).

## iPhone & iPad 1.7.0 — 2026-11-06

### Added

- **A pause rule.** A fourth app-rule mode: the shield asks **Is this
  important?** instead of only closing, a deliberately finished ten-second
  pause buys one session of the chosen minutes, and the rule allows 1–10 opens
  per day. It has its own `ManagedSettingsStore` and a mirrored day record; a
  damaged record reads as unreadable and closes the day rather than handing out
  a fresh budget. The DeviceActivity monitor ends a session at its threshold
  and re-shields a session from another day or one older than any possible
  reading of its minutes.
- **Pause first on the shield.** The pause shield's secondary button hands a
  request to Hisn — `.openParentalControlsApp` on iOS 26.5 and later, a local
  notification before that — and never opens the app itself. A request older
  than ten minutes is ignored, and the completion handler is called exactly
  once when the notification path races the timeout.
- **The breath screen.** The app consumes a handoff at most once per
  foreground, shows a ten-second ring, and enables **Open for N minutes** only
  when it finishes. Closing spends nothing. The grant order is failure-safe:
  monitor, session record, open consumed, shield cleared last.
- English and Arabic copy for the mode, the editor, the handoff and every grant
  result.

### Changed

- The app-rule editor offers the pause mode with opens per day and minutes per
  open; the shared minutes field is clamped when the mode changes, because the
  valid ranges differ between a daily budget and a pause session.
- The shield's secondary button is only configured for a pause rule with opens
  left; every other mode keeps the existing one-button shield.

- **Four places instead of three tabs: Today, Apps, Practice, More.** The
  first screen now answers "what do I do now?" — one deep card for the
  protection state and its single action, the week's practice streak, the app
  rules in a sentence, and one practice card. The checks, plan, Lock Center,
  commitment, vault, sites and settings moved one tap into More; nothing was
  deleted. The old Setup tab's three steps are still there, with their exact
  words.
- **A rule editor that fits a decision.** Apps shows each rule as a row with
  the system-drawn app icons, one sentence and the opens as dots. The editor
  is a sheet: pick apps, choose one of four modes (pause, always, daily limit,
  quiet hours) with a one-line description each, then only the numbers that
  mode uses. `AppUsageSection` and `GuardSection` are removed; the rules keep
  the same controller calls, validation and frozen-rule rules.
- **The practice pause in the shield's navy.** `PauseView` and the handoff
  screen share the shield's deep card, mark and rounded type, so the system
  screen and the app screen read as one voice. A finished breath records the
  practice day that the streak counts.
- **The design language.** Warm paper/ink canvas, one indigo accent, calm green
  for "held", ember for attention, rounded display type, one filled action per
  screen; every state is also carried by an icon and words. All four tabs
  render at the largest accessibility sizes and in Arabic RTL, and body text
  stays at 4.5:1 or better on its surface.

### Verification

- `./test.sh ios` — **40 Python and 243 simulator tests, 0 failures** on the
  iPhone 17 simulator (iOS 26.5); 21 pause-rule tests.
- `UIRenderTests` now renders all four tabs in English, Arabic, dark mode and
  iPad, plus the pause handoff, the rule editor and More in both styles.
- `PauseRuleTests` cover day rollover, budget exhaustion, damaged and
  conflicting records, session bounds, handoff freshness and the practice-day
  history without Apple's Screen Time service.
- No entitlement, signed build or physical-device acceptance is claimed. The
  pause shield rendering, its secondary button and `openParentalControlsApp`
  need a signed, entitled device; only the code paths are covered here. Record:
  [the 1.7.0 note](docs/IOS_1.7.0.md).

### Limits

- Real shielding needs the Family Controls entitlement; the simulator cannot
  show a Screen Time shield.
- Apple's usage threshold is not a hard clock, so a session can overrun its
  minutes until the monitor's next reconciliation.
- The notification handoff needs notification permission, and the direct open
  needs iOS 26.5; without either the shield closes and the pause screen remains
  reachable from Hisn.
- Pause is a chosen mode, not a default; a commitment freezes a rule change like
  any other, and the day record is device-local.

## iPhone & iPad 1.6.0 — 2026-11-06

### Added

- **Browser and VPN guard.** A second, independent always-block app rule, so
  the apps that route around Safari blocking — other browsers, VPN apps — stay
  shielded beside whatever mode the main rule uses for other apps. Its own
  mirrored record and `ManagedSettingsStore`, frozen during a commitment like
  every saved rule, a row on the overview rules card, and a section in Setup
  step 2 and the App limits sheet. The improvement plan's last remaining M2.5
  code item.
- **Lock Center.** Setup step 3's removal protection becomes the ladder as its
  own object: the current level with its evidence and stale reason, one row per
  rung (passcode vault, family guardian, supervised) with what it stops and the
  exit it keeps, the guardian request with its consent sheet, and the person's
  own confirmations with the optional deletion self-test. The footer states
  what no level stops. No new lock is claimed; the honest ladder is the
  answer to "make the app undeletable".
- **Monitor guard watch.** The DeviceActivity monitor's layer-loss check
  expects app shields when a guard is saved and observes the guard's own
  store, so a lost guard is told rather than silent.

### Changed

- The commitment's frozen-rules line now names the guard, in English and
  Arabic.
- `RemovalProtectionSection` is replaced by `LockCenterView`; every caveat it
  carried lives in the Lock Center.

### Fixed

- **Hisn Safari (limited edition) checked only four of its six parts.** The
  limited app still assumed the pre-1.1.4 four-part bundle: it read the
  six-part `rules-metadata.json` as unverifiable, hid parts 5 and 6, and
  disabled reload. It now uses the shared `MobileProtectionPolicy` part count,
  pinned by a new `test_safari_only.py` assertion. Found while building the
  limited edition for a real iPhone 11 (Personal Team, device evidence in
  `ios/HisnSafariOnly/README.md`).

### Verification

- `./test.sh ios` — **39 Python and 221 simulator tests, 0 failures** on the
  iPhone 17 simulator (iOS 26.5); five new guard-rule tests.
- REA 6.8.0 `inspect-artifact` on the built 1.6.0 app: evidence
  `ev_33f65972e29c1415eeeb05ff3265387c087bb9caef117ca19f02ab494da6cf9e`.
  Inventory only, no native analysis provider.
- Renders of every tab and the three drill-ins in both languages are in
  `design/ios-ui-1.6.0/`.
- No entitlement, signed build or physical-device acceptance is claimed; the
  guard's real shielding and the shield-combination behaviour need a signed,
  entitled device. Record: [the 1.6.0 note](docs/IOS_1.6.0.md).

## Mac 1.1.16 — 2026-11-06

### Added

- **Blocked connections today.** The filter counts denied flows for the current
  local day and reports the number, with the day it belongs to, over the
  existing XPC health reply. The menu bar and the Overview's Layer details show
  it when it is non-zero and for today. Only an aggregate is kept — no host,
  app, path or address — the count is capped at 9,999, and restarting the
  filter or crossing local midnight starts again at zero.
- **The lock's time left in the menu bar.** While a lock runs the menu-bar
  label shows the remaining time in whole minutes, rounded up, in day/hour/
  minute units; with no lock it reads **Hisn**.

### Changed

- **A dropped XPC reply no longer abandons the authority request batch.**
  `FilterSync` now runs every queued request through a testable `apply` seam,
  continues past a nil reply and keeps refusals in order. Each request is a
  whole state, so the next tick reconciles whatever was missed, only in the
  stricter direction.

### Verification

- `./test.sh macos` — **568 tests, 0 failures**; 720 strings, all with Arabic.
- New tests cover the tally's day rollover, a read-only other day, the cap and
  two time zones; the batch continuing past a missing reply and refusal order;
  and the countdown's rounding and minute granularity.
- No live XPC run with real filtered traffic is claimed. The count travels on
  the health reply the authority tests already cover; its arithmetic is
  unit-tested.

### Limits

- Per filter process and local day: a restart starts at zero, and nothing is
  persisted by design.
- It counts denied flows, not sites or people; retries and subresources inflate
  it, and traffic denied elsewhere is invisible.
- A count is not evidence of coverage; liveness remains the heartbeat and the
  XPC answer.
- With no authority answer the app shows no count, and the menu text is coarse
  by design.

## Mac 1.1.15 — 2026-11-05

### Added

- **Organization Mode.** A configuration profile can set the forced preference
  `HisnManaged` in the app's own domain. The app reads it with
  `CFPreferencesAppValueIsForced` — a `defaults write` cannot fake it — and
  shows **Managed by [organization]** at the top of the Overview, Setup, Rules
  and Settings, and in the menu bar.
- **No local removal or weakening while managed.** The private-window sheet
  hides **Not now** and the Device Management removal route; Rules accepts new
  blocks and removal of allowances but refuses the reverse; Settings,
  the partner key and the gray-screen switch become tightening-only; the
  account-split confirmation is disabled. The app does not pretend this stops a
  local administrator from removing the profile.
- **Managed profile generator.** `profile/make_profile.py --managed
  --organization-name "…"` adds a forced `app.hisn.Hisn` payload
  (`HisnManaged = true`, `OrganizationName`) to the existing DNS/browser
  profile. The profile already sets `PayloadRemovalDisallowed = true` and a
  removal password; an MDM/supervised deployment is still what makes removal
  resistance real.
- **Removal-disallowed narrow profile.** When the app is managed, the
  private-windows profile it creates also sets `PayloadRemovalDisallowed`.

### Verification

- `./test.sh macos` — **559 tests, 0 failures**; 718 strings, all with Arabic.
- `profile` suite — managed payload, organization name, empty-name fallback and
  removal-disallowed tests pass.
- The generated example profile was inspected with `plutil`: it contains the
  forced `HisnManaged`/`OrganizationName` payload and
  `PayloadRemovalDisallowed => true`.

### Limits

- A local administrator, Recovery, or an erase can still remove any profile.
  Organization Mode hides in-app bypasses; it is not a substitute for MDM,
  supervision and a standard daily account.
- Safari still has no supported private-browsing profile key; the managed
  profile's DNS/browser policies do not change that. Use the organization's
  Safari restriction, Screen Time, or block Safari.

## Mac 1.1.14 — 2026-11-05

### Added

- **A first-run explanation, in order.** The app now opens on the Overview, not
  the twelve-step diagnostics page. On first launch a three-screen welcome
  shows the three protection layers in one line each, asks once whether Hisn
  may disable private browsing, and names the browsers it can and cannot
  control. It is skippable and reachable again from Settings › How Hisn works.
- **Private windows are a first-class layer.** The status hero now includes
  **Private windows** beside the filter and the browser extension. When a
  private window is uncovered, the headline becomes **Private windows are
  open**, the primary action is **Prevent private browsing…**, and the app no
  longer lets a green "Browser extension" row imply the private window is
  monitored.
- **One consent sheet, every browser.** It lists installed Chromium browsers
  (Chrome, Helium, Edge, Brave, Vivaldi, Opera, Arc and the rest of the
  catalogue), Firefox channels, Zen/Floorp where policy is supported, and names
  Safari, DuckDuckGo, Tor, Mullvad, LibreWolf and Waterfox as not controllable
  rather than silently counting them as protected. **Not now** changes nothing;
  a running lock disables the removal route and says so.
- **Firefox private-window policy.** The narrow profile now sets
  `EnterprisePoliciesEnabled` and `DisablePrivateBrowsing` for Firefox as well
  as `IncognitoModeAvailability = 1` for Chromium. It still changes nothing
  else: no DNS, no DoH, no guest windows.
- The extension heartbeat reports whether Chrome's "Allow in Incognito" is on,
  so an unpacked extension that is not allowed in private windows is visible as
  a gap without waiting for a policy.

### Changed

- Setup's top card is shorter: one next step, a count of other checks, and no
  duplicated limits paragraph. Layer details on the Overview starts collapsed.
- The launch-time block-list check waits thirty seconds, so the first frame is
  not competing with a background download. The previous signed list remains
  enforced throughout.

### Verification

- `./test.sh macos` — **554 tests, 0 failures**; 707 strings, all with Arabic.
- New `PrivateBrowsingTests` cover the installed-browser catalogue, forced
  Chromium and Firefox policy, the extension's incognito answer, the mixed
  profile payload, and the status layer/headline.
- Rendered captures: welcome (English and Arabic), private-window sheet, and
  the Overview at the 760-point minimum width.

## Browser extension 1.5.2 — 2026-11-05

### Added

- The native heartbeat carries `incognitoAllowed`, read from
  `chrome.extension.isAllowedIncognitoAccess`. The Mac app records it per
  browser and treats `false` as an uncovered private-window gap; a missing
  answer (older Chrome, an engine without the API) is not treated as `false`.

### Verification

- `extension/test/run.sh` — all suites pass, including a runtime assertion that
  the heartbeat message carries the field.
- Packaged: `dist/hisn-extension-1.5.2.zip`, 92 files, 5.9 MB, SHA-256
  `ef3f7d8a9c8e2a290891ef92b56f2b44fdc3d0ad071cf83c44dfe6f41fc25d7f`.
  Unpacked copy: `dist/hisn-unpacked-chrome-1.5.2`.

## Mac 1.1.13 — 2026-11-05

### Added

- **One action for private windows.** When a browser's private windows are
  still open, Setup now shows a card naming those browsers and a **Create the
  private-windows profile…** button. It writes a small configuration profile
  that forces only `IncognitoModeAvailability = 1` for each installed Chromium
  browser, opens it for approval in System Settings, and says what to do next.
  The existing Check again re-reads the policy.
- The narrow profile changes nothing else — no DNS, no DoH, no guest windows;
  the full hardening profile keeps that job.

### Verification

- `./test.sh macos` — **542 tests, 0 failures** (a new test reads the generated
  profile and asserts it sets only `IncognitoModeAvailability`); 644 strings,
  all with Arabic.
- Version 1.1.13, build 25. The button was pressed on the installed build: the
  profile was written to
  `~/Library/Application Support/Hisn/Hisn-Private-Windows-Disabled.mobileconfig`,
  validated with `plutil`, and System Settings opened for approval. macOS 26 no
  longer installs profiles from the command line, so final approval is the
  person's step.

## Browser extension 1.5.1 — 2026-11-04

### Removed

- **The private-window mode is gone from the browser.** The popup no longer
  lists a "Private windows" layer or shows the "Not active in private windows
  yet" notice with an "Allow in Incognito" walkthrough, and the options page no
  longer shows the private-windows row or its hint. The `incog.*` and
  `opt.layer.private` strings are deleted in English and Arabic.
- Private browsing is now the Mac app's job: its hardening-profile step
  disables private windows in every configured browser, and Setup reports them
  as open until the profile is installed. The extension no longer coaches a
  person into enabling Incognito so it can be covered there.

### Verification

- `./test.sh extension` — all suites pass, including the popup browser test
  which now asserts the popup has no incognito mode.
- Packaged: `dist/hisn-extension-1.5.1.zip`, 92 files.

## Mac 1.1.12 — 2026-11-04

### Changed

- **Browser protection is continuous.** The guard no longer waits for a lock or
  for the "Keep browser protection on" opt-in: while Hisn runs, a browser whose
  extension stops answering shows a one-minute countdown and is then closed, and
  unsupported browsers including Safari close on the same rule.
- **The opt-in is removed, not hidden.** The setting, its confirmation sheet, the
  stored `guardRequireOutsideLock` preference, the `setRequireOutsideLock` setter
  and the setup issue that asked for the opt-in are gone. Settings states
  "Browser protection is always on while Hisn runs." instead of offering it.

### Verification

- `./test.sh macos` — **541 tests, 0 failures**; new and updated tests cover the
  continuous guard and readiness without an opt-in; all strings have Arabic.
- Version 1.1.12, build 24. Installed in place on the owner's Mac. The close path
  is the same one that ran under a lock; a real removed-extension run is still an
  owner action.

## iPhone & iPad 1.5.0 — 2026-11-04

### Changed

- **The overview is a calm card stack.** The status hero, the pause card, one
  protection-plan card with `0 of 3` progress and four state rows, a protection
  lock card and a rules card replace the old list plus full readiness and
  commitment sections. The duplicate `0/3` and the repeated plan evidence are
  gone; the detailed checks stay one disclosure down.
- **Saved rules are one tap away.** **Set a lock / Manage lock**, **Blocked
  sites** and **App limits** open their editors in sheets instead of sending the
  person to find the right Setup step. The full commitment form no longer sits
  on the status page.
- **Setup is three guided steps.** One status card carries the state, issues,
  last check time and `0 of 3 steps done`; step cards lead to blocking layers,
  app limits plus commitment, and the passcode/removal page. The four checks
  follow the steps instead of preceding them.
- **App limits shows the decision.** Always block, daily combined budget and
  daily quiet hours are visible rows with icons and a checkmark instead of a
  menu picker; the selected items and count stay above the rule.
- **Settings is grouped cards.** Language, the Safari extension, gray screen,
  privacy and about each get one card; the full privacy note and coverage copy
  stay behind disclosures.
- **Less visible text, without removing caveats.** The Safari, Screen Time,
  DNS, family, router, Safari-extension, app-limit, blocked-site, vault and
  removal sections now lead with a short action or status line; the full scope,
  steps and limits are one disclosure down. Setup issues collapse behind a
  count, the overview plan and checks use short states ("Needs a signed
  device", "Self only", "Not set"), and the gray-screen explanation moves under
  **Why and how**.
- **Stale Safari copy fixed.** The Setup, Safari and Hisn Text guides said
  "Hisn 1–4" even though the app has shipped six Safari blockers since 1.1.4;
  they now say **Hisn 1–6** in English and Arabic.
- **Accessibility.** Status headers, step cards, pause, lock and rule rows stack
  at accessibility text sizes; status pills and the progress count no longer use
  fixed widths; cards follow system light/dark and Increase Contrast; the
  4.5:1 status colours are unchanged.
- No rule, lock, commitment, Screen Time, DNS, Safari, pause or shield behaviour
  changed. The 1.4.0 shield, 1.3.0 pause and quiet hours, and the 1.2.0
  saved-rule freeze remain.

### Verification

- `./test.sh ios` — **39 Python and 216 simulator tests, 0 failures** on the
  iPhone 17 simulator (iOS 26.5). Three new tests cover the today-only pause
  count and the new phone/iPad light/dark renders including the three Setup
  drill-ins.
- Design research: REA 6.8.0 `inspect-artifact` on ScreenZen 2.0.0 (build 63)
  and the built 1.4.0 app; observation only. Captures in
  `design/ios-ui-1.5.0/`; record in
  [the 1.5.0 notes](docs/IOS_1.5.0.md) and
  [the REA audit](docs/REA_AUDIT_IOS_2026-11-04.md).
- Version 1.5.0, build 9. Simulator-tested only; no Apple team, Family Controls
  entitlement, signed build or physical-device result is claimed.

## Mac 1.1.11 — 2026-11-04

### Added

- **Check a website.** A read-only sheet, reachable from the window toolbar,
  Overview, Blocking Rules, the menu bar and ⌘K, answers whether an address
  would be blocked and names the rule that decided it. It reads the installed list,
  falls back to the signed core seed bundled with the app, and reports an
  unreadable list as unavailable rather than as “not blocked”. **Add to Always
  block** uses the existing guarded save path and still works during a lock.
- `BlocklistStore.isOnPublishedList(host:)` and `matchesKeyword(host:)`, two
  read-only queries that let the checker explain one layer at a time.
- `SiteCheckerTests` and published-list/keyword query tests.

### Changed

- **Overview is a status hero, not a stack of summaries.** One card carries the
  headline, summary and a tile per layer (name, state word, colour). The
  duplicate chip strip and expanded layer repetition are gone.
- **One state-aware primary action.** The button reads **Finish setup** when
  nothing enforces, **Review setup** when some layers run but a check is
  missing, **Start a lock…** when the setup checks pass, and **Manage lock**
  during a lock. The lock remains the secondary action when it is valid. The
  disabled “Checking…” state no longer blocks the first seconds of launch.
- **Warnings sit under the status they qualify**, before the next action.
- **Pause and the site checker share one tools card** instead of the pause
  taking a full card.
- **Setup leads with a progress ring** and a next-step line; the readiness card
  is compact, the repeated build-block paragraph is gone, and each stage shows
  `done/total` plus a **Next** badge.
- **Blocking Rules is one card per list**, with an empty state, a parser-derived
  count, app icons for blocked apps, and a Check a website row.
- **The Lock page reads the commitment as a band**: the end date is emphasised,
  the mode toggles are trailing switches, and a running lock shows elapsed
  progress between its start and end.
- **Settings card headings carry icons**, matching the rows elsewhere.
- The sidebar's lock item shows the remaining time instead of a bare lock icon.

### Verification

- `./test.sh macos` — **541 tests, 0 failures**; **645 strings, all with
  Arabic**.
- Five pages and the checker were captured from a real development build on the
  owner's Mac, dark, 760 pt, English; the 1.1.10 captures are the before state.
  The bundled-seed path was exercised because the ad-hoc build has no app-group
  container.
- No rule, lock, filter, browser-bridge or protection-layer behaviour changed.
  New strings have Arabic; the old `": "` placeholder key is gone.
- Design research: Screen Zen 2.0.0 (build 63) observed read-only; Opal, one
  sec, Jomo and Clearspace product pages and reviews read publicly. No
  competitor code, assets, visual identity or copy was reused.

## Mac 1.1.10 — 2026-11-03

### Changed

- **One page title, not three.** The large in-page header (icon, title, subtitle)
  is gone; the window title bar already names the page and its purpose. Setup
  previously said "Protected Setup" in the title bar, again as a header and a
  third time as a section heading. Every page now starts with its content.
- **Overview puts the action before the evidence.** The one primary button sits
  directly under the layer chips; with the layer details open it used to land at
  the bottom of the card, below three explanations. The commitment card moved up
  beside the protection card, and the laptop-readiness card appears only while a
  check is missing instead of repeating a passing verdict.
- **Setup is one card.** Readiness, a progress bar, "2 of 12 steps done" and
  "Next: …" now share the top card; the duplicate "Protected Setup" section and
  its second verdict are gone.
- **Lock states what the chosen mode means, once.** The fixed-commitment
  paragraph follows the toggle — before, turning the fixed commitment off left
  the paragraph on screen — and the duplicate "Your chosen commitment" card is
  removed. The action is now pinned to the bottom edge of the window with a live
  consequence line ("This lock would enforce 2 of 3 protection layers."), so the
  decision stays visible while the options scroll instead of sitting under two
  warnings.
- **Blocking Rules leads with the lists.** The editable site and app lists come
  first; the browser guide follows them instead of pushing every control below
  the fold.
- **A blocked-sites strip.** The sites list opens with chips for what this
  person has added, before the controls that change them; it reads the same
  edited text the rows do, so the two cannot disagree.
- **Settings is grouped cards.** Content checking, browser guidance, partner,
  gray screen, language and details each become one card instead of a flat
  column of headings and dividers, and the three check toggles are trailing
  switches rather than leading checkboxes.
- **Site lists are rows, not a text box.** Each always-blocked and always-allowed
  list shows one domain per row with a Remove control, an inline add field and a
  live count; bulk paste stays one disclosure down, with the one-domain-per-line
  rules next to the box. Rows and the add field write the same newline text the
  save path already parses, so there is one source of truth.

### Verification

- `./test.sh macos` — **526 tests, 0 failures**; 597 strings, all with Arabic.
- Design research: REA 6.4.0 `inspect-artifact` and
  `capture-native-ui-scenario` on the Screen Zen 2.0.0 (build 63) window,
  observation only. Captures in `design/macos-ui-1.1.10/`.
- Version 1.1.10, build 21. Unsigned and unnotarized; no physical-Mac,
  VoiceOver or signed-build result is claimed.

## Android 1.1.1 — 2026-11-06

### Added

- **Weekday schedules.** A schedule can now repeat on chosen days of the week
  instead of every day. `DailyAppSchedule` carries a Monday-first day mask; an
  overnight window belongs to the day it starts on, so a 22:00–07:00 window
  selected for Monday covers Monday evening into Tuesday morning, and the
  ten-minute arming window follows the same rule. A mask of zero days is
  rejected.
- **A day picker in the schedule editor.** **Days this window repeats on**
  opens seven checkboxes, localised with the device locale's day names, and
  refuses an empty selection. The row summary reads **Every day** or a count
  such as **3 days a week**; the Protection and Rules summaries name the same
  repetition in English and Arabic.

### Changed

- A schedule saved before this version has no day mask and reads as every day,
  which is what its single window already did.
- Preservation during a protection lock is checked across all seven days, so
  changing the mask cannot drop a previously covered overnight portion.

### Verification

- `android/test-core.sh` — all core suites pass, including the per-day
  boundaries, overnight anchoring, seven-day preservation and the exhaustive
  minute grid; strings and notices pass with 552 strings and 16 plurals in
  English and Arabic.
- `:app:compileDebugJavaWithJavac`, `:app:assembleDebug` and `:app:lintDebug`
  pass with 0 errors.
- No signed APK, device run or emulator acceptance is claimed for 1.1.1. The
  day picker follows the existing dialog and setting-row components; a device
  check of night, weekday and empty-selection cases remains open.

### Limits

- The device clock and time zone decide, as with every schedule; this is not a
  background scheduler.
- One window per schedule: different windows on different days are not part of
  this version.
- The schedule still requires accessibility to be enabled and connected and
  restricts selected apps, not the phone or the internet.
- Existing installs stay every day until someone opens the editor and chooses.

## Android 1.1.0 — 2026-11-03

### Added

- **A first-run welcome.** Three skippable pages explain blocking, deliberate
  opening and protection locks, with one large icon and one idea per page.
  Finishing opens Rules; **Skip for now** opens Protection. It appears only when
  no rules are configured and has not been seen. English and Arabic RTL.
- **Take a pause on Protection.** A visible row opens the existing breathing
  pause without a block. It changes no rule, grants no permission and remains
  available during a protection lock. **Back to Hisn** returns immediately.
- **Today's use in the daily-limit editor.** Each selected app shows
  **Used X of Y today** with a progress bar from Android's on-device usage
  record; without Usage access the editor says so instead of showing zeros.
- The pause row moved near the top of Protection, after the hero/attention area.
- ScreenZen's Android onboarding was observed read-only as design input; Hisn
  uses its own copy, icons, colours and components. No ScreenZen code, assets or
  copy was used. Open-count, streak, paywall and cloud features remain out of
  scope.

### Release

- Signed direct APK `1.1.0-direct`, versionCode 27, package
  `app.hisn.android`; the Play APK/AAB are built and checked but not uploaded.
  Certificate unchanged:
  `2F:BE:D6:57:E6:AB:C7:1C:15:CA:D0:CD:99:1C:53:2F:59:77:2D:16:95:D7:C3:7E:65:A1:14:38:5E:94:70:C5`.
- SHA-256: direct APK
  `4d80a3c7c50e8e0637c69e9aa8b604d55902d1ee55c9f96c7e30c5712f0d7dff`;
  Play APK `f90c43b83cac0a79383dd8e0ccaecee4f9f604993ba618c02c23c7ebb6a75a5c`;
  Play AAB `ee5bce6fd4d70bc9fcfe0a43ce6bc019dd04377ca0d7266c1eba4ba2ea78dc4f`.
- The owner's Samsung updated from 1.0.0-direct to 1.1.0-direct in place
  (`firstInstallTime` unchanged) and rendered the Arabic welcome. The 0.9.0
  preview was debug-signed; its rules and optional admin did not survive the
  earlier 1.0.0 migration on this phone, so the safe preview path remains
  uninstall first. New installs from 1.0.0 onward use the release key.

### Verification

- `JAVA_HOME=<JBR> sh test-core.sh` — **PASS**, including `AppTimeLimitTest`,
  sealed settings, protection lock, DNS, text, visual, guardian and seed suites.
- `python3 check-strings.py` — **PASS**, 480 strings, 15 plurals, 506 source
  references in English and Arabic.
- `./gradlew assembleDebug assembleDebugAndroidTest lintDebug` — **PASS**, 0 lint
  errors.
- `CoreDeviceTestRunner` on the API 36 emulator — **PASS**.
- Welcome UI in English and Arabic, Skip/Set up, page-by-page back, the
  voluntary pause and the "Used 4 min of 15 min today" row — **PASS** on the
  API 36 emulator.
- Signed Play/direct APKs and AAB pass `check-release.py` and `apksigner`.

### Limits

- No full physical-phone enforcement, TalkBack/large-font or OEM-skin audit is
  claimed; the phone run above is a launch/update and Arabic-welcome check.
- The welcome appears only when no rules are configured. Pause and usage display
  change no rule and grant no permission. Daily limits still need accessibility
  and Usage access; clock/time-zone changes, clearing usage data, force-stop,
  Safe Mode, ADB/root or a factory reset still bypass them.
- The Play AAB is not uploaded and Play approval is not claimed.

## Blocklist v6 — 2026-11-03

### Published

- **The first signed list generation is live** at
  `https://raw.githubusercontent.com/hamzabinjehad/AdultContentBlocker/lists/manifest.json`:
  **5,274,046 domains**, **239,885 core**, 5,831 terms, 480 DNR rules. The
  manifest and signature were downloaded over HTTPS, are byte-for-byte the
  signed candidate, and returned HTTP/2 200.
- **The publish guard refused a degraded build first.** The initial build timed
  out fetching two UT1 sources and produced 1,273,954 domains; its signature
  was valid, but the guard refused the drop from 5,276,448. Nothing was
  published from it. A retry fetched all ten sources and passed.
- `keys.py verify` — signature OK; `publish_guard.py` — v5 → v6 OK;
  `test_build.py` — 43 tests OK, including known-adult-blocked and
  critical-infrastructure-not-blocked checks. Published files total 34 MB;
  `domains.txt` and `domains.packed` stay off the branch because they exceed
  GitHub's 100 MB limit.

### Limits

- The bundled seed stays **v5**; it was not re-cut. iOS and Android still update
  only with an app build. No installed Chromium or Mac client is proven to have
  installed v6.
- The daily GitHub workflow still cannot publish: the main-only `list-signing`
  environment secret is not configured. This generation was published once with
  the matching local key. See
  [the publication record](docs/LIST_PUBLICATION_2026-11-03.md).

## Browser extension 1.5.0 — 2026-11-03

### Added

- **Move your rules.** Settings → Blocking Rules exports the five hand lists
  (blocked sites, allowlist, custom words, ignored words, skipped sites) as a
  versioned `hisn.rules` JSON file, and imports one in another browser. The
  file contains no blocking mode, lock deadline, list generation, pause count
  or browsing data.
- Import merges **add-only**: it never removes an entry, so a more permissive
  file cannot undo a stricter browser. A running lock withholds additions to
  the allowlist and corrections while still applying new blocks and words; the
  report says what was withheld, invalid, or beyond the 200-word limit.
- An app-managed browser exports its lists but refuses an import: the Mac app
  owns them and would overwrite the file on its next heartbeat.

### Verification

- `./test.sh extension` — **PASS**: JavaScript suites, package and evaluation
  corpus. The new `transfer.test.js` (45 checks) covers file shape,
  schema/version refusal, invalid-entry counting, add-only merge, lock
  direction, the 200-word ceiling and app-managed refusal.
- `extension/test/browser/run.sh settings.html` — **PASS**, including a real
  file reaching the guarded worker, an invalid file writing nothing, and a
  locked browser applying blocks while withholding allowances.

### Changed

- Version 1.5.0. No permission, host permission, storage field or network path
  was added. English and Arabic copy added.

## Mac 1.1.9 — 2026-11-03

### Changed

- **The browser-guard warning is now an intervention card.** A dark, calm card
  shows a shield, the browser's name, the exact reason, a large countdown in
  seconds, and one **Open browser** action. There is no dismiss or bypass:
  restoring the extension is what cancels the close. The panel is movable,
  hides its title bar, stays above other windows and resizes for several
  browsers.
- **Overview is status-first.** Compact layer chips answer which layers are
  ready at a glance. The long evidence, readiness count and per-layer details
  sit behind a **Layer details** disclosure that opens automatically when a
  layer needs attention or is still checking.
- No policy, filter, lock, browser-bridge or protection-layer behaviour
  changed. Version 1.1.9, build 13.

### Verification

- `./test.sh macos` — **526 tests, 0 failures**; 597 strings, all with Arabic.
  The new guard-card render test covers English and Arabic in the same sizes as
  the panel.
- Design captures are in
  [`design/macos-ui-1.1.9/`](design/macos-ui-1.1.9/README.md).
- The guard-card render is a deterministic SwiftUI render, not a physical
  click-through; the panel's countdown and close logic remain covered by
  `BrowserGuardTests`.

Unsigned and unnotarized. See [the 1.1.9 record](docs/MACOS_1.1.9.md).

## Mac 1.1.8 — 2026-11-03

### Added

- **A pause window.** A 30-second guided breath (4 seconds in, 6 out, three
  times), five rotating reminders, and four needs — Rest, Company, Calm,
  Something to do — each with one offscreen tip. It is reachable from Overview,
  the menu bar and **Protection › Take a pause… (⇧⌘P)**, and it is available
  during a lock. Closing is always immediate.
- **A local count only.** The pause stores a calendar day, a count and the time
  of the last pause in `UserDefaults`: a 30-second merge window, a 9,999 cap and
  a new day starting over. No host, reason, history or network request. The menu
  bar shows today's count once a pause is recorded.
- English and Arabic copy, VoiceOver labels for the breathing state and needs,
  and design captures in
  [`design/macos-pause-2026-11-03/`](design/macos-pause-2026-11-03/README.md).

### Changed

- No rule, permission, filter, browser bridge, schedule or lock behaviour
  changed. The Mac version is 1.1.8, build 12.

### Verification

- `./test.sh macos` — **521 tests, 0 failures**; 589 strings, all with Arabic,
  including the export of every string the compiled code uses.
- Eight new `PauseTests` cover the tally, merge window, day boundary, cap,
  breath timeline, reminder rotation, stored shape and render in both languages
  at both stages, plus the Overview entry in both languages.
- Design research using REA on Screen Zen and Hisn is recorded in
  [the REA audit](docs/REA_AUDIT_SCREENZEN_2026-11-03.md). No Screen Zen code,
  assets or copy was reused.

Unsigned and unnotarized. No physical-Mac, VoiceOver or signed-build result is
claimed; see [the 1.1.8 record](docs/MACOS_1.1.8.md).

## iPhone & iPad 1.4.0 — 2026-11-03

### Added

- **A shield that explains itself.** `HisnShieldConfig` returns the system
  Screen Time shield for every app, category, site and web domain Hisn shields.
  It names the saved rule — always block, daily budget reached or quiet hours —
  in the language chosen in Hisn, using a small fixed English/Arabic table and
  the App Group language choice.
- **No unlock action.** `HisnShieldAction` answers the shield's one button with
  Close. The ManagedSettings shield remains applied until the rule ends or the
  person changes it in Hisn outside a commitment. A missing or unreadable rule,
  a budget shield without its reached-day marker, or an outside-window
  quiet-hours shield gets a generic honest message rather than a false claim.
- Both extensions carry the Family Controls entitlement; the configuration
  extension also carries the shared App Group. Privacy manifests now cover
  eleven bundles: the app, six blockers, monitor, scanner and both shield
  extensions.

### Verification

- `./test.sh ios` — **39 Python tests and 213 simulator tests, 0 failures** on
  the iPhone 17 simulator, iOS 26.5. The hosted count was 206 before this
  release; the seven new tests cover shield text, language and reasons.
- Version 1.4.0, build 8.

Simulator-tested only. No Apple team, Family Controls distribution entitlement,
signed build or physical-device result is claimed. The system-rendered shield
still needs a signed build on a real iPhone.

## iPhone & iPad 1.3.0 — 2026-11-03

### Added

- **On-demand pause.** A 30-second guided breath (4 seconds in, 6 out, three
  times), five rotating reminders, four needs with offscreen tips and a local
  day/count tally. It is available during a commitment, closes immediately and
  changes no protection rule. The tally is a day, a count and the last time; no
  host, reason or history.
- **Protection hero.** The overview leads with one status, layer chips and one
  next action; the full diagnostics are one disclosure below instead of a wall
  of text.
- **Daily quiet hours.** A DeviceActivity window applies the selected apps'
  shields, splits a cross-midnight window into two same-day legs, enforces
  Apple's 15-minute minimum and checks both wall and monotonic time. The health
  row distinguishes registered from stopped; a commitment freezes the rule and
  the guardian path can tighten, never loosen it.

### Changed

- The protection lock still freezes the saved blocked-site list and app-limit
  rules while a commitment runs; adding or restoring a protection layer remains
  available, turning one off does not.
- Version 1.3.0, build 7. No new permission or network path was added.

### Verification

- `./test.sh ios` — **206 tests, 0 failures** on the iOS 26.5 simulator
  (iPhone 17), including 9 pause tests, 12 quiet-hours tests and the existing
  Safari-blocker and privacy checks.
- `python3 -m unittest ios.test_localization` and the Python matrix pass;
  `script/versions.py --check` passes.

Simulator-tested only. No Apple team, Family Controls distribution entitlement,
signed build, TestFlight or physical-device result is claimed. The Screen Time
behavior, quiet-hours registration and pause sheet still need a real device.

## Android 1.0.0 — 2026-11-03

### Added

- **Daily app time limits.** Rules gains a separate card: choose apps and one
  daily foreground-time limit from five minutes to 24 hours. Android's own
  on-device usage record is read only after the person grants the separate
  **Usage access** permission; Hisn closes a limited app at the threshold and
  shows the pause screen with a time-limit reason until local midnight.
- A limit is a saved rule: the protection lock freezes it in both directions,
  and the guardian tighten-only path preserves saved limits and cannot remove
  or raise one. Missing Usage access or accessibility is shown as attention,
  never as enforced.
- The 0.9.0 protection lock, direct-build removal guard, managed-device
  previews, DNS layer, text checks, visual sampling, schedules and pause screen
  are unchanged. Version metadata now says 1.0.0 rather than preview.

### Release

- **Signed direct-sideload APK** at 1.0.0-direct, versionCode 26, package
  `app.hisn.android`. Published at
  [the android-1.0.0 release](https://github.com/hamzabinjehad/AdultContentBlocker/releases/tag/android-1.0.0);
  the asset was re-downloaded and its SHA-256 and certificate re-verified. The
  Play APK and AAB are built and checked but not uploaded; Play approval is not
  claimed. The signing certificate is RSA 4096
  (`CN=Hisn Android`, SHA-256
  `2F:BE:D6:57:E6:AB:C7:1C:15:CA:D0:CD:99:1C:53:2F:59:77:2D:16:95:D7:C3:7E:65:A1:14:38:5E:94:70:C5`).
- Artifact SHA-256: direct APK
  `c42591ec8223e3d24d8159f0536d3212ee0f351b5c3edc1fc4d03c0d30afaac0`;
  Play APK `bded282fd19fb524d77daf42d0a60a0660bd6db5c06acbba316e168743fe5517`;
  Play AAB `0794e0d509ca939cc012e2a01e82353211611c0c54174cefe7e4e40ec07cfad7`.
- The debug-signed 0.9.0 preview cannot be updated in place; uninstall it first.
  The owner must back up the new keystore and password offline in two places.

### Verification

- `JAVA_HOME=<JBR> sh test-core.sh` — **PASS**, including
  `AppTimeLimitTest`, the sealed settings round-trip, protection-lock freeze,
  DNS, text, visual, guardian and seed suites.
- `python3 check-strings.py` — **PASS**, 460 strings, 15 plurals and 485 source
  references in English and Arabic.
- `./gradlew assembleDebug assembleDebugAndroidTest lintDebug` — **PASS**, 0
  lint errors.
- `CoreDeviceTestRunner` on the API 36 emulator — **PASS**, including time-limit
  persistence/freeze and damaged settings.
- Rules UI read back through the accessibility tree, and one live 5-minute
  time-limit pass against the test-only fixture app closed it and showed the
  daily-limit pause screen on the same emulator.
- Both release APKs verify with `apksigner`; `check-release.py` passes for the
  direct and Play distributions.

### Limits

- No physical-phone 1.0.0 run is claimed. Usage access is a separate special
  permission; Android's record resets at local midnight and changing the clock,
  time zone or usage data can change it. Enforcement can lag by up to about a
  minute while a static screen stays open. Settings, launchers, dialers,
  installers and Hisn itself are excluded. Accessibility off, force-stop, Safe
  Mode, ADB/root or a factory reset still bypasses app-level limits.

## iPhone & iPad 1.2.0 — 2026-11-03

### Changed

- **The protection lock now freezes the saved rule lists**, inspired by Android
  0.9.0. While a commitment runs, blocked sites and app-limit rules are
  read-only: no additions, removals, parent-domain consolidation, app selection
  changes, mode changes, minute changes or clearing. An unreadable commitment
  counts as locked. Extending the commitment remains available.
- **Layer recovery is still allowed.** The Screen Time filter cannot be turned
  off inside Hisn during a commitment, but it can be enabled or restored after
  iOS revokes access. DNS setup and Safari rule reload remain available because
  they add or repair layers rather than rewrite the frozen rules. iOS Settings
  switches remain outside the app's control.
- The commitment section shows **Protection lock running** and the frozen-rule
  scope in English and Arabic; site and app controls are disabled rather than
  failing after a tap.
- `AppUsageConfiguration.permits` now requires full rule equality while
  committed, and `CustomSitePolicy.permits` freezes the whole site list. Both
  controllers refuse direct calls, not only the SwiftUI controls.
- The iOS Safari scanner packaging allowlist now includes `lib/language.js`, the
  shared language-aware scorer from extension 1.3.0.

### Verification

- `./test.sh ios` — **185 tests, 0 failures** on the iPhone simulator.
- `python3 -m unittest ios.test_localization` — pass; English/Arabic parity.
- `./test.sh python` — pass.
- Version 1.2.0, build 6. Simulator-tested only; no Apple team, Family Controls
  entitlement, signed build or device acceptance is claimed.

## Browser extension 1.4.0 — 2026-11-02

### Changed

- **UI/UX redesign.** One status hero answers “am I protected?” before detail.
  Popup layers are compact rows with a one-word state (On, Off, Checking,
  Issue) and a gear; the old per-layer sentences moved to Details, the page the
  gear opens, or a tooltip. One full-width primary action replaces competing
  buttons. The lock is a compact banner and the page-language row is a single
  label/value line.
- **Settings dashboard simplified.** The duplicated “Browser protection” list
  and the four status blocks are now one state card, one chip list, a compact
  app-connection card, two quick links and a one-line block-list row. The
  longer list-update explanation is a tooltip.
- **Less copy in English and Arabic.** Removed the sidebar tagline, the
  always-on-content paragraph, the repeated on-device footer sentence, and
  several hints; shortened status, repair, connection, visual, rules and
  content copy. Lock, managed-by-app, private-window and visual-coverage
  limits remain visible where they affect a choice.
- **Calmer visual system.** New light/dark tokens with stronger contrast,
  standardised controls, 44px touch targets, neutral information notices and
  warning amber reserved for warnings. The block page uses the same surfaces.
- Removed unused locale keys. No permission, network path, storage field or
  scoring rule changed; the 1.3.0 language-aware vocabulary is retained.

### Verification

- `./test.sh extension` — **PASS**, including the evaluation corpus (0/22 adult
  cases missed, 0/28 benign pages blocked at 50/80/100).
- `./test.sh browser` — **PASS**, including 160–480px popup layouts, Arabic RTL,
  settings guards and six block pages.
- Design captures and principles:
  [`design/extension-ui-2026-11-02/`](design/extension-ui-2026-11-02/README.md).
- Package: `dist/hisn-extension-1.4.0.zip`, 91 files, 6.2 MB, SHA-256
  `2cb7d723884b633b37f295ced0deec9ac0046c95d70d6c1fc80365acb41c5552`.

Version 1.4.0. Unpacked and store-ready; no listing, assigned store ID or
managed force-install is claimed.

## Browser extension 1.3.0 — 2026-11-02

### Added

- **Language-aware page-text scoring.** Every reviewed term in the signed
  vocabulary now carries a language (`en`, `ar`, `ar-latn`, `de`, `es`, `fr`,
  `it`, `pt`). The scorer picks a vocabulary view for the language the page is
  actually written in, instead of applying all ~5,800 terms to every page. This
  removes weak cross-language homographs without weakening the layer:
  - the detected language's own terms, English (the common base), shared
    spellings and the user's typed words are always present;
  - terms at or above weight 7 — explicit labels such as an adult studio or a
    title phrase — are included in every language;
  - negative/context phrases are never filtered;
  - an undeclared, unsupported or unclear language uses the complete index.
- **Script evidence outranks the page's tag.** The detector counts Arabic and
  Latin letters in the rendered sample. Dominant Arabic text on a page that
  declares `lang="en"` still selects the Arabic vocabulary, closing the
  one-attribute bypass that language-exclusive matching would have created.
- **Feed items are judged in their own language.** A post's `lang` attribute
  and a bounded sample of its rendered text accompany the existing body/alt
  zones. A language change is part of the equality signature, so a changed post
  is re-judged rather than reusing a clean verdict.
- **The popup shows the detected page language**, with a plain explanation of
  the rule; the options page names the supported lists and the fallback. Both
  are translated into Arabic.
- The iOS Safari web extension stages the same `lib/language.js` module and
  shares the language-aware scorer; its text-check copy now says so.
- **The real-model visual smoke test now covers four synthetic images**, not
  one flat fill: flat green, flat blue, a soft gradient and a hard checkerboard,
  plus the existing person-preference shapes. Nothing is uploaded; the test
  still runs only the packaged engine on loopback.

### Unchanged

- No new extension permission, network request, durable field or browsing
  record. Language tags and the bounded text sample stay in extension memory
  and are sent only between the content script, worker and popup.
- Not language-exclusive matching, and not a claim of complete coverage:
  mixed-language pages, text hidden from rendering, images and videos without
  readable text, and other browsers remain outside this layer.

### Verification

- `./test.sh extension` passes: every JavaScript suite, package check, and the
  evaluation corpus at sensitivity 50/80/100. At 50 the corpus reports 0/22
  adult cases missed and 0/28 benign pages blocked; dilution holds through
  8,000 filler words and every adult tube body remains blocked under a benign
  title/meta.
- New `test/language.test.js` (68 checks) covers tag normalisation, script
  detection, the declared-`en` bypass, view membership, the unknown-language
  fallback and ignored-word invalidation. `feed.test.js` (94 checks) covers
  per-post language signals.
- `./test.sh browser` passes, including the scanner harness, popup and settings
  pages in English and Arabic, feed compatibility, and the expanded real-engine
  visual smoke in CPU mode.
- Package: `dist/hisn-extension-1.3.0.zip`, 91 files, 6.2 MB, SHA-256
  `a8471ab3a3e4db4f61656507c933ec51981264162f2c8d593bd63d33694abcac`.

Version 1.3.0. Unpacked and store-ready; no listing, assigned store ID or
managed force-install is claimed.

## Guardian console — unreleased

### Added

- **Phone–laptop pairing and signed policy.** A P-256 reference protocol in
  `script/guardian_protocol.py`: a `hisn://pair/1` offer, a six-digit SAS that
  both sides derive from ECDH, a device-signed response, and a policy envelope
  with device binding, a replay counter, issue/expiry windows and an ECDSA
  signature. `docs/PAIRING_PROTOCOL.md` is the wire format and threat model;
  `script/test_guardian_protocol.py` has 33 tests and
  `script/test_guardian_console.py` adds 11 file-level tests for a laptop
  console (`keygen`, `offer`, `verify-response`, `sign-policy`,
  `verify-policy`). `android/core/.../GuardianPairing.java` adds the phone-side
  offer parser, response builder/verifier and SAS, checked against a
  Python-generated fixture in both directions; a Java policy verifier checks
  the version-2 flat envelope and its replay/expiry/tamper rules. A
  `android/test-live-pairing.py` run on the API 36 emulator exchanged a Python
  offer with the debug activity, verified the Java response and SAS, and had
  the activity verify a Python-signed policy. On API 31+ the debug identity is
  an EC P-256 key inside AndroidKeyStore (sign + key agreement), persisted
  across runs; older runtimes use an AES-256-GCM-wrapped software key. The
  QR/product pairing UI and applying domain rules are still missing. With
  `--apply-policy`, the same emulator run verified a Python-signed policy and
  applied its tighten-only rules: keep installed, lockdown, strict, blocked-app
  suspension, a blocked package and a blocked domain. The sync read back
  `protected` and Chrome was suspended. Applying a signed domain list with hash
  verification and the product pairing UI remain open.
- **Laptop Android provisioning helper.** `script/guardian_provision.py` reads
  a factory-reset phone, refuses accounts, secondary users, another device
  owner and an unfinished setup wizard, then installs Hisn and appoints it
  device owner only after a typed confirmation. It never wipes, removes
  accounts or uninstalls, and it verifies the appointment by readback.

- **iOS supervised guardian pack.** `profile/make_guardian_pack.py` writes the
  `.mobileconfig` (mode 0600, password inside), a manifest with its SHA-256,
  `GUARDIAN.md` (supervision, verification and recovery steps) and
  `SHA256SUMS`. It refuses `--maximum --allow-app-removal` and never writes the
  password to the manifest or checklist. 12 tests.

## Android 0.9.0 — 2026-11-02

### The protection lock

- **Saved rules are fully frozen while the lock runs.** The in-app commitment
  is now presented as the **protection lock**. Starting one makes every rule on
  the Rules page read-only in both directions: domains, words, blocked and
  inspected apps, the daily schedule, the built-in adult/visual/short-video
  switches, SafeSearch and the gray-screen choice. The earlier rule allowed
  adding restrictions during a commitment; that path is closed because the
  product promise is now "the rules cannot change", not "they cannot weaken".
  Extending the lock is still allowed. `ProtectionLock` holds the pure decision
  and `PolicyStore` refuses the write for every caller, not only the UI.
- **The guardian path remains the one exception.** A verified, signed policy
  from the paired laptop may still add blocked domains and apps while the lock
  runs, through `PolicyStore.updateFromGuardian`. It is tighten-only; it cannot
  remove a rule, turn a switch off or loosen the schedule.
- **The lock arms removal protection on its own.** In the direct build the
  accessibility removal guard now watches Hisn's uninstall, App info, disable,
  Safe Mode and VPN screens whenever a protection lock runs, even if the
  keep-installed switch is off. The boot shield copy and the 15-minute layer
  watchdog follow the same effective state. On a device-owner install Android's
  uninstall block was already applied for the lock; managed lockdown remains
  the separate opt-in. The Play build still cannot block its own removal, and
  the app now says so in the attention list while a lock runs.
- **UI.** Rules gains a visible "Rules are locked" card with the remaining
  time; every editor, switch, row and the schedule button is disabled while it
  is true. The Protection hero names the lock and remaining time, and when
  rules exist without a lock it points to the lock action. The copy across the
  app now says "protection lock" instead of "commitment". The uninstall card
  distinguishes guard-on-for-the-lock, guard-disconnected and ordinary off
  states, and the uninstall dialog explains that the guard is on even when its
  switch reads off. Version 0.9.0, versionCode 25. The Android
  core suite, string parity, debug/test builds, lint and both release
  distribution checks pass; no physical-device run of the lock is claimed.

### Fixed
- **Faster blocking paths.** `DomainPolicy` now keeps a bounded cache of
  repeated DNS decisions, so a familiar hostname skips IDNA/validation and two
  parent lookups. The accessibility guard inspects changed content after 50 ms
  instead of 150 ms and discovers new windows after 250 ms instead of 500 ms.
  Window-state changes were already inspected immediately. This is a latency
  improvement, not a guarantee: an unlisted domain or an uninspected app still
  gets through until another layer acts.

- **`no_control_apps`, not `no_apps_control`.** The managed-lockdown app-control
  restriction used the wrong Android key, so it was silently missing from the
  device-owner readback while every other key was held. An API 36 emulator run
  caught it; the key and every status/test/doc reference were corrected.
- **Emulator evidence recorded.** The same run proved the nine-key managed
  lockdown, strict mode, blocked-app suspension readback and
  `DELETE_FAILED_DEVICE_POLICY_MANAGER` on the app's own uninstall. A fresh
  wiped run also proved release cleanup: suspension resumed, always-on cleared,
  device ownership removed and an ordinary uninstall succeeded. The Settings
  screens were then inspected: Force stop showed “Blocked by work policy”, date
  and time showed “Enabled by admin”, VPN showed the policy block, and Users
  showed Add user/guest “Controlled by admin”. Safe Mode, erase, Private DNS,
  default apps, the tunnel and a physical phone remain unproven.

### Added

- **Physical guard evidence.** On a Samsung SM-G990B2 (Android 16) with the
  direct build, accessibility on and Keep Hisn installed, opening Hisn's App
  info produced the Arabic shield page, and `adb shell pm uninstall
  app.hisn.android` returned `DELETE_FAILED_DEVICE_POLICY_MANAGER` while the
  legacy admin was active. This is physical friction/admin evidence, not a
  device-owner claim. The shield delay was then reduced from 250 ms to 100 ms;
  the physical App-info run still reached `ShieldActivity` reliably at 100 ms.
- **Immediate touch-blocking overlay.** On Android 11+, the accessibility
  service now draws a short-lived `TYPE_ACCESSIBILITY_OVERLAY` over the watched
  Settings screen before pressing Back/Home and starting the shield. It consumes
  taps during the transition, so a second tap cannot reach Uninstall or Force
  stop before the explanation appears. The overlay is removed as the shield
  starts and on service teardown; the system bars are excluded.
- **Release-shaped Android build.** Both the Play-style and `-direct` release
  APKs build and pass `android/check-release.py`; the direct one was then
  test-signed with the debug key and installed on the SM-G990B2. It is
  non-debuggable, has no DUMP permission or debug-only components, and preserves
  app data. The signing key is still a **debug** key, so this is a test install,
  not a publishable artifact.
- **Less text, clearer first screen.** Protection now shows status, three layer
  tiles, a collapsed attention row, one DNS action and app checks. Advanced DNS
  options, guide details, limits, privacy detail and uninstall explanations are
  progressive disclosures. A duplicate DNS recommendation card is hidden when
  the DNS card already owns the next action. The status paragraph and seed line
  are shortened. Principles and sources are recorded in
  [docs/UI_PRINCIPLES.md](docs/UI_PRINCIPLES.md).
- **VPN settings guard.** While **Keep Hisn installed** is on, a Settings VPN
  screen that names Hisn and offers disconnect, forget, always-on, edit or
  settings is closed and replaced by the shield page. It is text/package based
  friction, not system enforcement.
- **Unrestricted-battery setup.** Guide now has **Keep protection running**,
  which opens Android's battery-optimization exemption request and reports
  whether Hisn is exempt. The app does not force the exemption.
- **Direct-build protection page.** When the accessibility screen guard closes
  Hisn's uninstall, App info, storage, device-admin, accessibility or Safe Mode
  screen, a full-screen page now names the reason and holds *Back to Hisn* and
  *Go to home screen* for eight seconds before enabling them. It reads nothing
  itself, never returns to Settings, and the system Home button, dialler and
  emergency screens stay reachable. The previous in-app restriction notice
  remains as the fallback if the page cannot start.
- **Opt-in new-app install lock.** A separate switch, **Also stop new app
  installs (downloads)**, closes the package installer's Install confirmation
  and any Play Store page that offers Install (home, search result or app page)
  so another browser or VPN cannot be installed from those screens. It is
  separate from the keep-installed switch; app updates, Hisn's own update page
  and the update list stay usable. The switch is sealed with the other settings
  and cannot be turned off during a commitment.
- The layer watchdog counts either screen-guard switch as an armed accessibility
  layer, and the status line reports installs-only, both switches and the
  service-off state separately.
- **Managed lockdown closes three more routes.** On a device-owner phone,
  managed lockdown now also holds `no_add_user` on every supported version, on
  Android 9+ (API 28) `no_config_date_time` and, on Android 14+ (API 34),
  `disallow_config_default_apps`; the existing Safe Mode, all-app controls,
  factory reset, VPN and Android 10+ Private DNS keys remain.
  The desired set is gated by API so Android 8 is never asked for a key it does
  not know, and the read-only live status reader expects the same set. A
  device-wide install block (`no_install_apps`) is still not used because it
  would freeze Play updates too. Physical device acceptance is still pending.
- **Optional strict install block.** A separate **Strict** switch adds
  `no_install_unknown_sources_globally` on Android 10+ while managed lockdown
  holds. It is off by default because it also blocks a manual sideloaded Hisn
  update. USB debugging stays available, so ADB verification and recovery still
  work. The status, live reader, readback, release order and English/Arabic
  disclosures include it; a device-wide install block is still excluded.
  Physical device acceptance is still pending.
- **Opt-in blocked-app suspension.** A separate **Also suspend blocked apps in
  Android** switch suspends every installed app in Hisn's block list while
  managed lockdown holds. Suspended apps disappear from the launcher and cannot
  be opened; their data is kept. The status reads `FLAG_SUSPENDED` back;
  unblocking, turning the switch off or releasing managed mode resumes them.
  Cleanup depends on Android listing installed (including suspended) packages,
  and no physical device has been tested.
- **Manual guardian pairing screen.** Guide → **Guardian pairing** now opens a
  product screen where the owner pastes a `hisn://pair/1` offer, sees the
  six-digit SAS and device ID, and copies the response JSON for the laptop. It
  uses the EC-in-AndroidKeyStore identity and sends nothing over the network.
  On the API 36 emulator a pasted Python offer produced a displayed SAS and a
  copied 439-byte response that Python verified. The same screen now accepts a
  signed policy and applies tighten-only rules through a shared applier. On the
  API 36 emulator a Python-signed policy pasted into the product screen reported
  “Policy applied (already-protected)”. Policies may also carry a canonical
  `blocked_domains_sha256`; Python and Java now compute the same hash and the
  applier refuses a mismatch. A camera QR scanner and separate laptop-side
  response/policy transport are still missing.
- **Browser allowlist action.** The browser coverage screen now has **Keep only
  this** on each installed browser. It selects every other installed browser
  for blocking in one tap; saving applies the block and, with suspension on,
  suspends the others. The selection is pure-tested (`BrowserAllowlist`) and is
  explicitly limited to browsers visible at that moment.
- **Optional SafeSearch DNS rewrite.** The Network section has **Force SafeSearch
  on search engines**, off by default. While DNS filtering is on, Hisn answers
  exact Google, Bing, DuckDuckGo and YouTube hostnames with a local CNAME to the
  restricted target; country domains, YouTube APIs and other app endpoints are
  not guessed. The answer is built locally, preserves the question, is limited
  to A/AAAA and never reaches the upstream resolver. `SafeSearchTest` and
  `DnsPolicyRewriteTest` cover the mapping and response; a device test of the
  target certificates and search/playback behavior is still pending.
- **Always-on DNS VPN.** On a device-owner phone with managed lockdown and DNS
  filtering both on, Hisn now asks Android to keep the DNS-only tunnel always
  on (`setAlwaysOnVpnPackage(..., false)`), reads the package back, and clears
  it when DNS is turned off, the lockdown is released or the VPN is revoked.
  The manifest advertises always-on support. This is not a full tunnel and does
  not enable VPN lockdown; non-DNS traffic is never routed through Hisn. The
  live status reports the always-on request separately from the seven user
  restrictions. Physical device acceptance is still pending.

### Verification

- Core tests compile and pass, including new `SelfProtectionScreensTest` cases
  for the installer confirmation, a Play Store detail page, updates, Hisn's own
  reinstall, the store home and switch-independent classification, plus
  `SavedSettingsTest` round-trip and 0.5.3-upgrade checks for `install_lock`.
  String parity passes (383 strings, 14 plurals); the direct debug and
  debug-androidTest APKs build.
- Live on the API 36 emulator (`hisn_ui`) with the direct debug build: Hisn's
  App info was closed and the uninstall reason page appeared; a Play Store app
  page for Chrome was closed and the install reason page appeared; the Arabic
  page rendered RTL; the eight-second hold disabled both buttons; *Back to
  Hisn* opened the app.
- Not checked: the real package-installer confirmation, the switch dialog,
  TalkBack, an OEM skin and a physical device. No version bump, signed build or
  store submission is part of this entry.

## Android 0.8.4 — 2026-11-01

### Added

- **The daily schedule has two modes.** It can keep blocking the apps you
  choose, or block every app except a keep list you choose. The same searchable
  picker edits either list, the row in Rules names the mode, and the editor
  explains both. In allowlist mode the guard keeps the system windows it already
  excludes (settings, launchers, dialer, installers, Hisn) reachable; keeping at
  least one app available is required before the schedule can be saved.
- **Commitment coverage is preserved across a mode switch.** A blocklist may
  become an allowlist only when every app it blocked is still blocked, an
  allowlist may only become another allowlist whose keep list did not grow, and
  the window may not shrink; switching to a mode that blocks fewer apps is
  refused while a commitment runs.
- **The one-second checks stay bounded.** An allowlist is watched inside its
  window and for ten minutes before it; a once-a-minute metadata sweep covers an
  app opened earlier and left untouched, so a window start is still enforced
  without an all-day heartbeat.

### Verification

- Core tests cover allowlist blocking, the ten-minute arming rule, mode-switch
  preservation, the new saved field and the 0.5.3 upgrade default; string parity
  passes (369 strings, 14 plurals).
- Live on the Samsung SM-G990B2 with a sealed test policy for a 12:45–13:15
  window: the kept browser opened normally and a non-kept app was refused by the
  restriction screen. The owner's original schedule was restored byte for byte
  afterwards.
- Version 0.8.4, versionCode 24. No signed store build or broader device
  acceptance is claimed.

## Android 0.8.3 — 2026-11-01

### Added

- **Rules → Browsers without protection names the layer that covers each
  installed app that can open ordinary web links**, or says that none does. The
  answer comes from one pure `BrowserCoverage` rule: blocked wins, then the
  accessibility inspection while the guard is connected, then the DNS layer
  while it runs; otherwise the browser is uncovered. Browsers with no covering
  layer start checked, so **Block selected** refuses them in one tap without
  touching app blocks that are not browsers.
- The screen states its own limits where the decision is made: DNS filters
  names only, so a browser with its own VPN, Tor or private resolver can leave
  that path and belongs in the app block; blocking a browser does not prevent
  installing another one.
- New manifest package-visibility queries for `http` and `https` handlers, used
  only to enumerate what is installed. The example.com probe keeps apps that
  handle a single site out of the list.

### Verification

- `BrowserCoverageTest` passes with the other core tests; string parity holds
  (362 strings, 13 plurals); the debug APK builds and was side-loaded on the
  Samsung SM-G990B2 for a browser-list and one-tap block check.
- Version 0.8.3, versionCode 23. No signed store build or physical-device
  acceptance beyond that local debug install is claimed.

## Seed v5 — 2026-10-30

### Added

- **The bundled core grows from 159,768 to 239,885 domains (+50.1%).** The four
  hand-picked adult sources collapse to 125,309 domains; `core_fill` adds
  114,691 domains that both `blocklistproject-porn` (Unlicense) and
  `oisd-nsfw` (free for personal and commercial use) carry and that also carry
  an adult TLD, a reviewed token, or an IDN label. Selection, capacity and the
  1,261 deferred domains are recorded in the signed manifest and explained in
  [the coverage pass record](docs/COVERAGE_PASS_2026-10-30.md).
- **Safari capacity rises from four to six 40,000-rule parts**, from 160,000 to
  240,000 entries. The bundled core fits with 115 entries of headroom. The
  browser's static ruleset grows from 320 to 480 rules.
- **The mixed ad/tracker snapshot moves out of the core tier.** It stays in the
  full Mac/DNS list; the bundled slots now go to adult domains. The UT1 lists
  remain extended-only because their terms are non-commercial.
- The full list refreshes from 5,248,731 to 5,276,448 domains; the keyword
  layer stays at 5,831 terms.

### Verification

- `./test.sh python seed extension release macos ios` passes; Android core and
  the Gradle debug/release/bundle/lint builds pass. See the coverage record for
  counts and hashes.
- No signed store build, physical-device run, published `lists` generation or
  full-list Mac bundle is claimed.

## Browser extension 1.2.2 — 2026-10-30

### Changed

- Bundles seed v5's 239,885-domain core tier and 480 static DNR rules. The
  packaged store zip is `dist/hisn-extension-1.2.2.zip`, 90 files, 5.9 MB,
  SHA-256 `fd254f2eeb5a3a14ee017281675d12a11ca4a6d355ba075c97bf68a54f04157b`.
  Packaging now fixes timestamps and sorts entries, so two builds of the same
  tree are byte-identical.
- No store listing, real store ID or managed force-install is claimed.

## Mac 1.1.7 — 2026-11-02

### Added

- **Protection authority is read, not self-certified.** Setup now reads whether
  the daily account is an administrator, whether the visible `hisn_guardian`
  account exists with a secure token, and whether the root guardian daemon is
  loaded. The account-split step is complete only when all three are verified.
  A checked manual box can no longer make an administrator account look split.
- **Lock-gated authority and removal.** While a protection lock runs, the app
  disables the Users & Groups shortcut, hides the guardian setup/restore
  commands, and prevents the account-split confirmation from changing. After
  the lock, the commands return. Restoring administrator rights remains a
  guardian action; Hisn never stores the password.
- The Setup card states plainly that Hisn does not create the guardian account
  or store its password. The app cannot make itself an administrator; a hidden
  self-granted admin would be a backdoor.
- **Lock page warning.** Before a lock starts, the app warns when the daily
  account is still an administrator, so the person sees that the lock does not
  stop their own admin access. The Setup authority card is the fix.
- **UI/UX pass.** The sidebar has a persistent protection/lock status footer
  and a lock badge; the toolbar carries a lock action and page subtitle;
  Overview has one primary action; duplicate lock CTAs were removed; new Go and
  Protection menus add ⌘1–⌘5, ⌘R and ⌘⇧L. The default window grows to 980×700.
  Render captures are in `design/macos-ui-2026-11-02/`; no policy changed.

Version 1.1.7, build 11. `./test.sh macos` passes 508 tests including
`MacAuthorityTests`; localization passes 561 strings with Arabic. Unsigned and
unnotarized; no account split or standard-account run is claimed.

## Mac 1.1.6 — 2026-10-30

### Changed

- Bundles seed v5 and its 239,885-domain core fallback, plus regenerated
  third-party notices naming the two selected fill sources.
- The full 5,276,448-domain list still arrives only through a published
  generation; this build does not bundle it. Version 1.1.6, build 10. No
  signed package or device result is claimed.

## iPhone & iPad 1.1.4 — 2026-10-30

### Changed

- **Six Safari blockers instead of four**, each still capped at 40,000 rules.
  The project generator, readiness model, setup copy (English and Arabic) and
  tests all derive from the blocker count. Seed v5 fits with 115 entries of
  headroom.
- Version 1.1.4, build 5. Simulator-tested only; no Apple team, Family Controls
  entitlement, TestFlight build or physical-device acceptance is claimed.

## Android 0.8.2 — 2026-10-30

### Changed

- Bundles seed v5's 239,885-domain core tier (239,880 after the client's
  IP-entry exclusion) and names `blocklistproject-porn` and `oisd-nsfw` in the
  English and Arabic licenses/sources screen.
- Version 0.8.2, versionCode 22. Unsigned preview; no signed production build,
  Play listing or device-owner run is claimed.

## Browser extension 1.2.1 — 2026-10-30

### Added

- **Store-ready metadata.** `short_name` and `homepage_url` in the manifest;
  version 1.2.1. The packaging script still strips the development key and
  comments, so the published ID differs from the unpacked ID.
- **An honest options footer.** Settings states that matching stays on the
  device, shows the installed version, links the privacy policy and support
  page, and says that browser policy can make the extension non-removable
  without Hisn being able to detect it from the page.
- **A full store-readiness record.** [EXTENSION_STORE.md](docs/EXTENSION_STORE.md)
  has listing copy, permission justifications, privacy answers, the managed
  `ExtensionSettings` policy, reviewer steps and post-publication ID wiring.
- Removed the unused `offscreen` permission. The extension now declares only
  `declarativeNetRequest`, `storage`, `alarms`, `nativeMessaging`, `scripting`
  and `<all_urls>`.

### Verification

- `extension/test/run.sh` passes, including English/Arabic key and placeholder
  parity.
- `./test.sh extension` passes (unit suites, package check, benign corpus).
- `./test.sh browser` settings page and related harnesses pass in Helium
  0.20.2.1; `package.sh` produces a store-clean zip.
- No store listing, real ID, review result or managed force-install is claimed.

## Mac 1.1.5 — 2026-10-30

### Added

- **A root LaunchDaemon backs the login agent.** `/Library/LaunchDaemons/app.hisn.guardian.plist`
  runs a root-owned `/Library/Application Support/Hisn/hisn-guardian` loop. It
  reads the protected account from a root-owned config, checks the GUI domain and
  re-bootstraps `gui/<uid>/app.hisn.agent` when a standard user has booted it
  out. It logs only account/action/time to `guardian.log`, waits quietly while
  the account is logged out, and refuses `root`/`loginwindow`/malformed names.
- The package `postinstall` and `macos/install_update.sh` install the daemon and
  its config; the release package carries the daemon files;
  `macos/install.sh --remove-agent` removes the daemon as well as the agent.
- **Guardian status is visible.** Details shows whether the root daemon is
  installed and the last local action (agent restored, reload failed, account
  missing or config unreadable).
- **Details shows list freshness.** The last update time, a warning after seven
  days without an update, and a failed-update line; the previous signed
  generation remains enforced.
- **It does not fight a normal quit.** The login agent's `KeepAlive` handles a
  crash or force-quit; a normal quit outside a lock stays quit. The daemon does
  not duplicate the app's filter-health XPC model.
- **Licenses and sources.** Details opens the bundled `Notices.txt` with every
  list source, its licence/URL and the redistributed texts.
- Version 1.1.5, build 9. See [the 1.1.5 record](docs/MACOS_1.1.5.md).

### Verification

- `./test.sh release` passes (33 tests), including the new
  `release/test_guardian.py` (9 tests: plist shape, config parsing, refused
  accounts, logged-out quiet, missing-agent reload, failed reload, healthy agent,
  installer wiring, shell syntax).
- `./test.sh macos` passes (498 tests plus the Arabic-string check, 536 strings).
- `release.sh --smoke` builds the unsigned package; its scripts payload contains
  the daemon script, plist and helper. Nothing was installed.
- No root install, standard-account bootout, reboot or notarized-package result
  is claimed.

## iOS/iPadOS 1.1.3 — 2026-10-30

### Added

- **Privacy manifest in every bundle.** `Hisn/PrivacyInfo.xcprivacy` ships in the
  app, four Safari blockers, `HisnActivityMonitor` and `HisnText`: no tracking,
  no collected data, and required reasons for app-only/app-group `UserDefaults`
  and the monotonic boot clock. `Info.plist` declares
  `ITSAppUsesNonExemptEncryption` false.
- **Signed Safari list age.** `prepare_rules.py` copies the signed seed's
  `built_at` into `rules-metadata.json`; the list section shows **Built** and
  warns after 90 days that a newer signed build is the only Safari list update.
  `SeedFreshness` accepts ISO offset timestamps and plain dates, clamps future
  dates to zero and has Swift tests for the boundary.
- **Settings links and version.** Settings shows the app version/build and links
  to the repository privacy policy and support page.
- **Optional supervised-profile hardening.** `make_ios_profile.py` gains six
  opt-in, supervised-only keys: account changes, passcode changes, app
  installation, App Clips, enabling restrictions and automatic date/time. Each
  is off by default because it can remove a recovery path; the guide documents
  the tradeoff and the Python tests cover both states.
- `ios/test_privacy.py` checks the manifest, export declaration, required
  reasons and membership in all seven bundles.
- **Licenses and sources.** Settings opens the bundled `Notices.txt` with every
  list source, its licence/URL, the redistributed texts and the visual runtime
  notices. Generated by `script/notices.py`, tested on both Apple apps.
- Version 1.1.3, build 4. See [the 1.1.3 record](docs/IOS_1.1.3.md).

### Verification

- `./test.sh ios` passes on the iPhone 17 / iOS 26.5 simulator (183 hosted
  tests) and the 32 Python rule, privacy and localization checks pass.
- No Apple team, Family Controls entitlement, TestFlight build or physical-device
  result is claimed.

## Android 0.8.1 — 2026-10-30

### Added

- **The 1.0 list-update decision is recorded and visible.** Android receives
  list changes only with a new signed APK; there is no runtime list download.
  `SeedFreshness` reads the signed seed's build date and warns after 90 days
  with the age and the update path. A plain date or ISO offset timestamp is
  accepted, future times clamp to zero, and an unparsable time shows no warning
  rather than a wrong one.
- **The watchdog is visible and can ask for notification permission.**
  Protection shows the last check time, a lost layer, or notifications being
  off. Enabling **Keep Hisn installed** on Android 13+ requests
  `POST_NOTIFICATIONS`; a denial is shown, not treated as protection. A healthy
  check no longer erases the last-run time.
- The in-app Guide now covers the optional managed lockdown, the 15-minute
  check, the lack of always-on/lockdown VPN, and the APK-only list path.
- **The Android 1.0 network decision is recorded.** The DNS-only VPN stays
  owner-started/start-on-boot, not always-on or lockdown. `setGlobalPrivateDns`
  and a full tunnel are deferred; a stopped VPN is made visible by the watchdog
  and cannot be replaced by an ordinary user when managed lockdown holds
  `no_config_vpn`.
- The device runner compiles read-only watchdog-expectation and seed-age checks.
- Version 0.8.1, versionCode 21. See
  [the 0.8.1 record](docs/ANDROID_0.8.1.md).

### Verification

- `android/test-core.sh` passes with `SeedFreshnessTest`, `LayerWatchTest`, the
  API-aware `ManagedRestrictionsTest` and English/Arabic parity (280 strings).
- `assembleDebug assembleDebugAndroidTest assembleRelease lintDebug` passes; so does
  the direct flavor (`-PhisnDistribution=direct`).
- `check-release.py` passes on the unsigned release APK: 0.8.1 (21), no
  debuggable/test-only flags, no DUMP permission, no debug components.
  SHA-256 `1e00bf2eb870f693a8e49d097835b516ca20780cea343436b1023624452813c4`.
- `check-release.py --expect-distribution play` or `direct` makes the Play/direct
  split enforceable; both a Play and a direct artifact check pass, and the
  direct artifact is refused as Play. Its SHA-256 is
  `16878b73c66d0501a5735980a1dad96b58b9a2bef71dfb832fc2a172c41485b0`.
- `test_check_release.py` covers the suffix rule in `test-core.sh`.
- `bundleRelease` passes (unsigned AAB SHA-256
  `b45ab915c176910d4e6b49f91f94a466a7c524ca163a7ed5cc4acb404369bfcc`).
- No device run is claimed.

## Android 0.8.0 — 2026-10-30

### Added

- **Private DNS in managed lockdown.** On Android 10+ (API 29), the device-owner
  lockdown now applies and reads back `disallow_config_private_dns` as a fifth
  restriction. Android 8/9 expect four keys; the policy takes the API level, and
  the live checker builds the expected set from the device SDK. One missing or
  extra key is out of sync, never a protected state.
- **A background layer watchdog.** A persistent 15-minute `JobScheduler` job
  compares the layers the owner left armed (the direct build's accessibility
  guard while **Keep Hisn installed** is on, and DNS while left on) with the live
  state. A lost layer gets one local notification, repeats at most every twelve
  hours and clears on recovery; it never restarts a service, reads screen text
  or contacts a server. Scheduling is refreshed on app open, DNS start/stop,
  boot and keep-switch changes.
- Version 0.8.0, versionCode 20. See
  [the 0.8.0 record](docs/ANDROID_0.8.0.md).

### Verification

- `android/test-core.sh` passes with `LayerWatchTest`, the API-aware
  `ManagedRestrictionsTest` and English/Arabic string parity (275 strings).
- `assembleDebug assembleDebugAndroidTest assembleRelease lintDebug` passes.
- No device-owner or real layer-loss result is claimed; the disposable-device
  checklist is in the 0.8.0 record.

## Unreleased — `release/1.0`, 2026-10-29

### Added

- **Android 0.7.0 (versionCode 19): optional managed lockdown.** On a
  device-owner phone, a second explicit opt-in applies and reads back four
  Android restrictions: Safe Mode, app uninstall/disable/clear-data/force-stop
  for every app, the Settings factory reset and VPN configuration changes. Both
  managed choices are saved in one write; release clears restrictions in reverse
  order, then the uninstall block, then device ownership. Status shows ON, Off,
  idle or out of sync from Android's own readback. The broad app-controls
  restriction is disclosed before it is applied. No device acceptance is
  claimed. See [the 0.7.0 record](docs/ANDROID_0.7.0.md).
- **One machine-checked version set.** `versions.json` pins the extension, Mac,
  iOS/iPad and Android versions; `script/versions.py` checks each platform
  manifest and regenerates [docs/VERSIONS.md](docs/VERSIONS.md). A conflict in
  an Xcode target or a mismatch with the manifest fails `./test.sh python`.
- **Per-surface coverage page.** `script/coverage.py` derives
  [docs/COVERAGE.md](docs/COVERAGE.md) from the signed seed and the static DNR
  ruleset: 159,768 core domains, 5,248,731 full-list domains, 5,831 terms, 320
  static and 116 keyword DNR rules. It states which client carries which tier.
- **Privacy policy and support page.** [PRIVACY.md](PRIVACY.md) records the
  on-device matching, the absence of accounts/telemetry, the only routine
  network requests and every permission. [SUPPORT.md](SUPPORT.md) has setup
  links, troubleshooting, safe reporting and the consent/emergency rules.
- **Android live managed checker.** `test-live-managed-status.py
  --expect-lockdown` reads the four restrictions back without changing policy;
  the debug exporter now includes the held and desired sets.

### Changed

- The full list count in the docs is corrected to the signed artifact
  (5,248,731 domains, not 982k). The core tier is 159,768 and the keyword layer
  is 5,831 terms; see [COVERAGE.md](docs/COVERAGE.md).
- The iOS hosted suite now runs to completion on the iOS 26.5 simulator
  (181 tests, `** TEST SUCCEEDED **`), closing the M0 runner gap recorded in the
  1.0 plan. The suite is slow because the render tests are real UI passes; that
  is time, not a hang.
- A single bare `./test.sh` run passes end to end, and `./test.sh browser`
  passes every harness in Helium 0.20.2.1 (Chromium 156), including the DNR and
  SafeSearch rules and the live-page evasion checks. Android
  `assembleDebug assembleDebugAndroidTest lintDebug` passes with the final
  strings.
- The macOS suite is green at 495 tests plus the Arabic-string check; Python,
  seed, extension, release and Android core suites are green on the same tree.

### Fixed

- Android managed-mode status can no longer show PROTECTED when a requested
  restriction is missing, or READY while an old restriction remains. The
  combined state is OUT_OF_SYNC and the periodic repair runs.
- Managed-mode release now clears restrictions before the uninstall block, so a
  failed ownership release cannot leave the phone hardened but unmanaged.

## 2026-10-28 — Extension 1.2.0

- The block page explains a signed-list block (`reason=domain`) in English and
  Arabic, instead of falling through to the generic message.
- A native-messaging reply that omits `mode` can no longer reset Strict to
  Standard; `pollNative` leaves absent fields alone.
- The status shows the installed list version/date, warns after 14 days, and
  names the bundled-only state. Settings has **Update block list**, serialised
  against the automatic alarm; a failed update keeps the current generation.
- Facebook `share/reel/…` links are covered by the short-video rules and the
  in-page matcher.
- Version 1.2.0. Tests: extension unit suites, block page/settings/popup and DNR
  browser harnesses, package and evaluation corpus. See
  [EXTENSION_1.2.0.md](docs/EXTENSION_1.2.0.md).

## 2026-10-28 — Android 0.6.0

- Opt-in device-owner uninstall protection, with platform readback, an in-app
  status/release path and boot/foreground reconciliation after a commitment
  expires.
- The uninstall-screen guard and boot shield for the direct build; the pause
  tally and short-video blocking.
- Device evidence on the Samsung SM-G990B2 is recorded as far as it goes; the
  phone was not a device owner, so no blocked-uninstall result is claimed. See
  [ANDROID_0.6.0.md](docs/ANDROID_0.6.0.md).

## 2026-10-26 — iOS/iPadOS 1.1.2

- A commitment that holds: trusted Apple server time, a restored filter,
  layer-loss alerts and the passcode vault for a date-limited exit without a
  second person.
- Honest Screen Time status, readable colours, Dynamic Type and iPad layout;
  **My blocked sites** through the Screen Time adult-content filter.
- HisnActivityMonitor app shields, usage budgets and the selected-app picker.
- Development build; no Apple team, Family Controls entitlement or device
  acceptance is claimed. See [UPDATE_1_1_2.md](docs/UPDATE_1_1_2.md) and the
  [iOS plan](docs/IOS_IMPROVEMENT_PLAN.md).

## 2026-10-26 — macOS 1.1.4 (build 8)

- Ships the Persian/Urdu text folds to installed Macs, so the Mac filter and the
  extension make the same decision on the shared normalisation cases.
- Reads the lock inside `ScreenGrayscale.set` rather than as a default argument,
  fixing the grayscale state read.
- Unsigned, unnotarized development build; the guardrail and filter tests pass
  (495 tests, Arabic-string check). See [ROAD_TO_1.0.md](docs/ROAD_TO_1.0.md).

## 2026-10-01 — signed seed bundle v4

- Core tier: 159,768 high-confidence domains; full build: 5,248,731 domains;
  keyword layer: 5,831 terms (4,100 Arabic, 1,447 Franco-Arabic, 145
  English, 100 German and 39 French/Spanish/Italian/Portuguese) plus 116 host
  terms.
- Ed25519-signed manifest, rollback floor and a fail-closed client. No `lists`
  branch has been published yet, so every client is still on this seed.

Earlier per-version records (Android 0.3.1 through 0.5.5, extension 1.1.8
through 1.1.12, and the 1.1.1/1.1.2 updates) are in the [docs index](docs/README.md).
