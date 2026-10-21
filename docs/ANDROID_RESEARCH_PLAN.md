# Hisn for Android: research and execution plan

Research date: 2026-10-18. This is a plan, not an Android release.

## Evidence and boundaries

The connected test device was a Samsung SM-G990B2 (Galaxy S21 FE) on Android
16. REA 6.5.0 with the pinned JADX headless 0.7.1 engine inspected local copies
of the installed BlockerHero 1.5.0 and HaramBlur 2.0.5 APK sets. REA's package,
class and selected method observations are static, not a measurement of actual
classification quality, runtime performance, network coverage or signature
authenticity. The APKs contain proprietary and third-party material; use the
observations to design independent Hisn code, not to copy implementations or
model weights. No extracted app code is part of Hisn.

BlockerHero observations:

- Its accessibility event handler has explicit branches for app features such
  as YouTube Shorts and Snapchat Stories, focus sessions, and supported browser
  address-bar nodes. It also has a keyword model containing a compiled pattern.
  These are selected observations, not a complete description of every rule.
- REA traced that model's pattern getter to a helper that iterates supplied
  keywords, ignores text shorter than three characters and returns the first
  matching pattern's label. That isolated helper is not proof that the entire
  app lacks contextual checks, nor a quality comparison against Hisn.
- The keyword factory attempts to compile the supplied pattern and returns no
  keyword on compilation failure. This is a static observation of that factory;
  it does not establish how the UI validates rules or warns about errors.
- In an earlier device test, a normal uninstall screen returned to the launcher
  and a direct uninstall request failed with `DELETE_FAILED_DEVICE_POLICY_MANAGER`.
  The accessibility service was enabled and the app was listed as a device admin.
  This demonstrates the tested behavior, not that every removal path is blocked.
- Its visible UI included a one-day uninstall protection counter and a self
  accountability mode describing a timed typing challenge. The counter alone
  is not proof that an entire commitment is enforced across reboot or clock
  changes.
- The [Play listing](https://play.google.com/store/apps/details?hl=en_US&id=com.blockerhero)
  advertises app and keyword blocking, scheduled focus and several
  accountability choices. These are published feature claims, not evidence
  that every mode was tested or cannot be bypassed on the connected phone.

HaramBlur observations:

- The app exposes system-wide protection, optional reminders for selected apps,
  and blur, pixelation and solid-color overlay styles. On the inspected device,
  a user-approved screen-capture session and a foreground detection service
  became active. The screen explicitly required Android's sharing confirmation.
  A later Android projection status check reported no active session; its cause
  was not established. An enabled in-app switch is not enough evidence that
  capture is currently operating.
- The settings screen offers face-category choices and a sensitivity selection.
  This establishes the available controls, not their classification accuracy.
- Static inspection found MediaProjection, virtual-display, image-reader,
  bitmap and canvas fields in its blur service. One inspected method copies an
  image into a reusable bitmap, resizes it into a pooled bitmap and returns it
  for downstream processing. This does not identify its model, precision,
  actual frame rate, all supported apps, or privacy properties at runtime.
- The installed ARM64 split includes TensorFlow Lite CPU and GPU native
  libraries. Their presence does not establish which model or execution path
  is used, whether GPU acceleration is active, or any measured speed.
- A selected native blur service branch checks a 50 ms interval before image
  conversion and dispatch. This is a condition in one branch, not a measured
  end-to-end latency, a guaranteed 20 FPS, or evidence that every frame is
  inspected before display.
- Its inspected accessibility event handler reacts to window-state changes
  and, in whole-system mode, dispatches the foreground package name for further
  handling. This single handler does not establish how all screen content is
  obtained, how selected-app reminders work, or whether screenshots are kept.
- A harmless `example.com` launch returned to the launcher while both apps were
  running; without an isolated browser/permission setup the cause is unknown.
  An explicit Chrome launch then brought Chrome to the foreground with the
  test URL in its address bar; this did not establish that the page loaded.
  Neither observation is a passing blocking or visual test.
- The [HaramBlur Android listing](https://play.google.com/store/apps/details?hl=en_US&id=com.haramblur)
  describes local visual analysis and video blurring; these are developer
  claims, not verified privacy or speed measurements. An extension user also
  reported delayed detection and missed small faces in a
  [public issue](https://github.com/alganzory/HaramBlur/issues/213). Treat that
  as one report and include small faces, moving subjects and detection delay in
  Hisn's benchmark; do not assume the Android app shares the reported bug.

Current Hisn baseline: `android/core/` contains only an experimental JVM word
matcher. It is not an Android application, does not read screen text, enforce
blocks or lock removal, and does not yet implement the repository's shared
normalization and signed-term contract. The Mac, iOS/iPadOS and browser versions
remain separate implementations. See `SELF_CONTROL_COMMITMENT.md` and
`NETWORK_FIRST_PROTECTION.md` for their specific coverage and gaps.
The checked-in signed term seed has 5,831 positive terms and 209 negatives;
the experimental literal matcher accepts at most 1,000 distinct user rules.
It therefore cannot load that seed as-is. The shared normalization contract has
27 fixtures and must be used to assess parity before integration.
The repository's seed verification command succeeded during this research pass:
version 4 and five shipped artifacts matched the signed manifest. This verifies
the checked-in bundle, not Android integration or the quality of every term.
The standalone `WordMatcherTest` also passed with Android Studio's bundled JDK;
its existing cases test the prototype, not Android enforcement or shared-contract
parity, and explicitly expect the current truncation behavior.
The existing Python term suite passed 29 tests; it emitted a pre-existing
unclosed-file `ResourceWarning` for `/usr/share/dict/words`. This validates the
Python baseline only and does not establish Java or Android parity.
The browser extension's intended JavaScriptCore harness also passed its current
suite, including normalization, text context and evasion fixtures. These are
extension tests, not Android device or visual accuracy measurements.

## Platform constraints and product decisions

- Android permits a user to unregister an ordinary device admin before removing
  it. Stronger device-owner policies are restricted to managed enrollment and
  cannot guarantee resistance to device wipe or physical recovery. No factory
  reset, enrollment, root or ownership transfer is part of this plan without a
  separate deliberate decision. [Android device administration](https://developer.android.com/work/device-admin).
- Google Play's Accessibility API policy explicitly prohibits preventing
  disabling/uninstalling apps using accessibility except in specified parental
  or enterprise-management scenarios. A privately distributed build is not a
  license to bypass Android's security controls. Decide the actual distribution
  and permitted authority before implementing any uninstall-interception UI.
  Google also requires consented device-setting changes to be easily
  reversible; a release review must address that separately from in-app
  commitment behavior. [Accessibility policy](https://support.google.com/googleplay/android-developer/answer/18258653),
  [device-setting policy](https://support.google.com/googleplay/android-developer/answer/9888077).
- A media projection needs user consent per capture session for apps targeting
  Android 14+; on Android 15 QPR1+ the system also stops projection when the
  screen locks, and capture can be stopped by the user. Secure surfaces may not
  be readable. The S21 FE's restart-after-unlock behavior must be measured.
  The app must explicitly report unavailable capture, never promise universal
  pre-display protection. [Media projection](https://developer.android.com/media/grow/media-projection),
  [secure windows](https://developer.android.com/security/fraud-prevention/activities).
- An app targeting Android 15+ cannot start a media-projection foreground
  service directly from a boot-completed receiver. A reboot recovery workflow
  must request the user's participation instead of claiming automatic capture.
  [Foreground-service restrictions](https://developer.android.com/about/versions/15/changes/foreground-service-types).
- An always-on VPN is an opt-in system feature, not a permanent privilege.
  Android allows only one active VPN per user/profile, and preparing another
  VPN can stop the existing one. App-provided encrypted DNS, direct IP traffic,
  IPv6, DNS caching and local content need explicit tests; DNS filtering does
  not classify images or videos on a mixed-content service.
  [Android VPN](https://developer.android.com/develop/connectivity/vpn).
- The phone's Chrome browser cannot simply run the desktop Chrome Web Store
  extension. Firefox for Android supports compatible extensions, but Hisn's
  existing extension needs an explicit compatibility and permissions audit;
  it is not already proven to work there. Private-window access is a separate
  user choice. [Chrome Web Store help](https://support.google.com/chrome_webstore/answer/1698338),
  [Firefox for Android add-ons](https://support.mozilla.org/en-US/kb/find-and-install-add-ons-firefox-android).
- For development-only device-owner enrollment, Google's guide requires a
  device with no accounts, other users or work profile. A normal already-used
  personal phone may therefore require backup and factory reset for fully
  managed provisioning; personal-profile and work-profile policies also have
  different scopes. This is not an instruction to reset the connected phone.
  [Android Enterprise developer guide](https://developer.android.com/work/guide.html).

## Ordered work and acceptance gates

### P0: development environment and accurate protection state

1. Complete Android Studio SDK setup; pin JDK, Gradle, Android plugin, compile
   SDK and dependency versions in an Android project without touching Apple
   targets. Establish a reproducible debug build, local unit tests and a
   connected-device smoke test.
2. Build a protection dashboard showing each layer as off, awaiting consent,
   configured, verified, degraded or unsupported. Use actual service and
   policy evidence; a stored toggle is not proof of enforcement.
3. Record service loss, boot, process death and permission revocation as states.
   Keep normal calling, emergency functions and navigation available. Do not
   present a nonexistent protection layer as active.

Acceptance: debug APK installs on S21 FE, survives ordinary process restart,
shows truthful individual states and does not affect existing BlockerHero.

### P1: signed domain list and on-device DNS

1. Import `seed/manifest.json`, signature, key and `domains_core.txt` through a
   verified build step. Validate signature, hashes and rollback version, with
   a last-known-good atomic update. Distinguish list authenticity from quality.
2. Implement a bounded, tested DNS policy with label-boundary suffix matching,
   IDNA, IPv4/IPv6 and explicit allowed/custom overrides. Separately design a
   functioning VpnService packet pipeline, forwarding and failure behavior;
   a DNS matcher by itself must never be presented as an active VPN.
3. Provide an opt-in always-on/lockdown guide and a known-safe test domain.
   Decide what happens to connectivity if the engine or resolver fails, and
   display this before enabling the choice. No silent public-DNS fallback.

Acceptance: controlled positive, negative, wildcard and bypass fixtures; live
Wi-Fi and cellular tests, airplane transitions, restart, another VPN, resolver
failure, direct IP, encrypted DNS and both address families. Record gaps.

### P1: text and keyword protection

1. Define a privacy-limited accessibility adapter scoped to selected apps and
   supported browsers; protect text input fields and avoid persisting screen
   text, passwords, notifications or messages. User-visible consent must state
   what can be read, processed and retained. Exclude editable/password nodes and
   their descendants, text-change event payloads, and screen content outside
   the chosen foreground apps; test inherited or duplicated parent labels and
   fail conservatively when node metadata is insufficient. Even with those
   safeguards, Android may expose sensitive content in non-editable nodes, so
   disclose that risk rather than claiming guaranteed exclusion.
2. Port and test Hisn's existing shared normalization contract using
   `blocklist/terms/normalize_cases.json` plus the extension corpus. Preserve
   Arabic folding and bounded clitics, multilingual context, explicit phrase
   rules, literal user rules, negation and benign contexts.
3. Replace the current prototype's silent 65,536-codepoint truncation with a
   bounded, observable chunk/overflow strategy. Its current implementation
   removes all nonspacing marks and does not reproduce the shared Arabic folds;
   do not consume signed terms with it until parity and false positives are
   measured. Bound node traversal, per-event work, text size and queues.
   Treat custom-rule capacity separately from signed vocabulary size; select an
   indexed multi-pattern algorithm and benchmark worst-case input before loading
   thousands of terms, instead of scanning the complete screen once per rule.
4. Separate the narrow user rule action from the context-aware adult classifier.
   Scope decisions to the relevant post or page when technically supported;
   do not block an entire social network because of a single unrelated node.

Acceptance: identical shared normalization fixtures on Java/Android, Swift,
Python and JavaScript; multilingual positives and benign controls; obfuscated
word cases; split nodes and virtualized feeds; no input-field inspection; X,
YouTube and ordinary posts remain usable. Measure false positives, recall,
latency and battery, not just pass counts.

Test reporting should distinguish a literal rule the user explicitly chose
from an automatic adult-content decision. Include ordinary educational and
health text, Arabic diacritics and spelling variants, multilingual text,
legitimate names containing a rule as a substring, and relevant positive
fixtures. Test dynamic edits, duplicated accessibility events, text split
across nodes and a rule beyond the current input cap. For blocked items, show
the rule category and relevant layer without saving or displaying the content
itself. A local false-positive report should require explicit user action and
never silently send screen data.

### P2: selected apps and schedules

Offer opt-in per-app schedules and a screen explaining the exact enforcement
available on the current device. For a normal personal profile, investigate a
clearly disclosed accessibility-based foreground-app intervention and test
its actual delay and bypasses; it is not equivalent to suspending an Android
package. With a separately provisioned managed device/profile, assess Android's
`setPackagesSuspended` policy within its user scope, check failures returned by
the API, and account for packages Android does not allow to be suspended. Usage
statistics require separate user-granted access and are not enforcement.
Never interfere with the dialer, emergency communication, or required system
functions. [Android app suspension](https://developer.android.com/work/dpc/security),
[usage statistics](https://developer.android.com/reference/android/app/usage/UsageStatsManager).

Acceptance: selected-app intervention, exceptions and scheduled boundaries
tested across restart, screen lock, split screen, changed time zone, a missing
permission and different profiles. Record any bypass and false intervention;
do not describe a normal consumer device as fully managed.

For a later side-by-side comparison, use a disposable test profile or device
and a synthetic feed with clearly labeled benign fixtures. Test each competitor
alone, then Hisn alone, with matching settings and the same actions; keep the
current phone's apps and settings unchanged until the owner approves a test
that changes them. Capture only test screens and aggregate timings. Testing
multiple blockers at once makes it impossible to attribute an intervention to
either one. Never use the connected phone for factory reset or ownership setup.

### P2: visual protection and user-controlled presentation

1. Use an opt-in MediaProjection foreground service with explicit capture scope
   and no audio. Keep frames, crops and text on device by default; discard raw
   frames and do not record a browsing history. Reveal when protection is off.
2. Test a model only after its source, usage license, size and benchmark corpus
   are reviewed. Treat face/person/adult detection as different model tasks;
   apparent gender is probabilistic and must not be presented as identity.
3. Process the latest frame with a bounded single-flight queue, capped sampling,
   reusable buffers, expiry for stale results, rotation handling and thermal
   limits. Evaluate cost on S21 FE before choosing target frame rates. Once a
   session is started, allow detection while the app UI is closed, subject to
   Android's service rules, and provide clear recovery on capture loss.
4. Offer blur, pixelate and solid overlays and image/video scope only where the
   chosen capture adapter can actually distinguish them. A pre-scan cover can
   hide ordinary content and cannot cover unsupported secure surfaces; make that
   tradeoff visible before a commitment starts.
5. Handle `MediaProjection.Callback.onStop()` and display a clear notification
   when capture stops. After screen lock or process loss, ask for a new session;
   do not silently reuse a consent token or report the visual layer as active.

Acceptance: benign and labeled visual fixtures, no real adult material needed
for smoke tests, model recall and false-positive report, onset and overlay
latency, stale overlay cleanup, blocked capture, rotation, long scrolling,
video, memory, thermal and battery tests. No claim of perfect coverage.

Benchmark design before selecting any model:

- Use a consented, appropriately licensed, locally stored labeled fixture set;
  record whether each frame has a face, a person and/or explicit content as
  separate labels. Include benign pictures, small and occluded faces, multiple
  people, diverse lighting and motion. Face appearance is not gender identity.
- Keep training/tuning examples separate from a held-out evaluation set and
  report results by size, lighting, motion and chosen sensitivity as well as
  overall. Report missed items and false alarms with denominators, not a single
  unqualified accuracy number. Expand the corpus before making release claims.
- Record time from a known fixture becoming visible to effective coverage,
  including capture, model and overlay; report median, 95th percentile and worst
  observed delay. Separately measure unprotected flashes, blank time, frame
  drops and stale masks. A temporary cover can trade more blanking for fewer
  flashes; measure both outcomes under the same workload.
- Repeat a fixed scrolling and video workload on the S21 FE for CPU and any
  supported hardware delegate. Record model version, screen size, power mode,
  battery drain and thermal throttling; compare results with protection off.
  Treat desired speed as a target to establish from these measurements, not an
  assumed millisecond guarantee.

### P2: voluntary commitment and device administration

1. Confirm duration, selected layers, consequences and known gaps in advance.
   During a plan, reject in-app attempts to shorten the horizon or weaken
   already-selected rules; allow strengthening them. Persist atomically and
   make uncertain state explicit on damaged storage.
2. Use elapsed realtime while the device is running, record boot identity and
   wall-time changes, and explain that trusted time across a reboot/offline
   period is unresolved. Do not claim a 30-day lock is tamper-proof because
   of a locally stored wall-clock deadline.
3. Ordinary admin can introduce friction; a managed device owner is a separate
   explicit deployment with tested enrollment and recovery. Do not use an
   accessibility overlay to defeat uninstall/disable flows in a Play build.
   A backup contact is optional, consistent with the self-control goal.
4. Router and independent device DNS provide additional protection but do not
   survive every reset or cover cellular automatically. Explain shared impact
   on the household before a network configuration change.

Acceptance: active-plan UI and direct-method refusal, update/reboot/clock and
storage-failure tests, transparent authority status, exact removal-policy tests
on a disposable device/profile, and emergency communications remain functional.

### P3: cross-device usability and release readiness

Use matching concepts and localized explanations on Mac, iOS/iPadOS and Android
without weakening existing enforcement. Sync only explicitly selected rules;
use authenticated versioned changes and keep an active stricter local policy
when sync is unavailable. Do not sync sensitive browsing or screen data.

Run a device matrix (S21 FE first, then other Samsung/Pixel versions), battery
optimization and OEM background tests, accessibility reviews, Arabic/English
localization, a false-positive appeal path and a release/privacy review. Label
all gaps and preserve source and license notices for any reused dependency.

## Immediate next steps

1. Finish SDK setup and verify the build toolchain.
2. Bring Android normalization and tests into parity with the shared contract.
3. Implement and device-test domain filtering before claiming network coverage.
4. Add accessibility text scanning only with a scoped, disclosed data design.
5. Run a controlled visual benchmark after a user-started capture session.

No Android APK or complete blocking layer was created or installed in this
research pass. No protection setting was changed by the REA static analysis.
