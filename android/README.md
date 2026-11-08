# Hisn Android 1.1.0: signed self-control release

The Android app contains an opt-in DNS-only VPN, saved custom rules, selected-app
accessibility restrictions, opt-in daily app schedules, opt-in daily app time
limits, built-in adult-text checks and sampled image/video checks, an in-app
protection lock, an optional legacy-admin confirmation step, an opt-in
device-owner uninstall guard with an optional managed lockdown, and a background
layer-health watchdog. The interface follows the system light/dark appearance,
uses bottom navigation for Protection, Rules and Guide, and supports Arabic and
English.

1.0.0 was the first signed direct-sideload release. 1.1.0 adds a first-run
welcome, a voluntary pause action, and today's usage in the daily-limit editor;
it adds no enforcement layer and relaxes no rule. The APK and certificate
fingerprints for both releases are recorded in
[the 1.1.0 record](../docs/ANDROID_1.1.0.md).

1.1.1 is a development build: a schedule can repeat on chosen weekdays, with a
Monday-first day mask, a day picker in the editor, overnight windows anchored to
the day they start on, and every-day defaults for files saved before this
version. It adds no permission or stored field beyond the schedule's own mask;
no signed APK or device run is claimed. See
[the 1.1.1 record](../docs/ANDROID_1.1.1.md).

The Play build is produced from the same source but is not uploaded; its
accessibility declaration still needs Google Play review, and the direct
build's removal guard is deliberately absent from it.

Core tests pass and the signed 1.1.0 candidates are checked with
`check-release.py`. Device evidence is version-specific in the release records
below. The 1.1.0 UI pass ran on the API 36 emulator, and the owner's Samsung
accepted the 1.0.0 → 1.1.0 update in place and rendered the Arabic welcome; no
full physical-phone acceptance of enforcement is claimed. The visual model and
policy tests ran in a separate Samsung debug installation; the owner's existing
app and permissions were not replaced or enabled. The current live VPN check
passed local-block and external-resolver steps over both UDP and reused TCP. On
0.4.2, Brave displayed an ordinary page, search results and an IETF
documentation page while the VPN was active. These are limited smoke tests, not
proof that every browser request uses Hisn. Broader real-app coverage and
VPN/lifecycle testing remain pending. Visual accuracy and real-app capture
acceptance remain pending; the isolated live fixture sequence passes. Daily
time limits read Android's on-device usage record and are only enforced while
Hisn's accessibility service is connected; they are not a
usage-statistics-based parental control system. Full traffic coverage is not
implemented. Managed uninstall blocking and the broader lockdown exist only as
an opt-in device-owner preview; the ordinary install remains removable, and
actual blocked-uninstall and restriction acceptance needs a disposable
provisioned device.

## 1.5.3: go back on a matched person

**Rules › Built-in checks › When a person is detected** now offers **Go back to
the last page** (default) and **Cover the whole screen**. A matched face hides
any cover and performs one Android Back, at most once per 1.2 s; a browser
returns to its previous page, and a second matching page is handled on its own
sample. If Back is refused the cover is shown. Adult content still closes the
app; Incognito still uses the 1.5.1 protected cover. The 1.5.2 DNS/guard
recovery and all prior visual behaviour are retained. Details:
[the 1.5.3 record](../docs/ANDROID_1.5.3.md).

## 1.5.2: make the blocking layers survive an update

Two update-recovery gaps are closed. If DNS was left on, Android still holds
the VPN approval, and the tunnel is not running, `MainActivity.onResume` starts
it without another tap. If accessibility is enabled but the app reads the
service disconnected, it broadcasts a re-arm request; a live service reruns its
idempotent setup. The app cannot toggle its own accessibility grant, and if the
service is not alive the setup card remains the repair. The protected/Incognito
cover and all prior visual behaviour are retained. Details:
[the 1.5.2 record](../docs/ANDROID_1.5.2.md).

## 1.5.1: cover protected and Incognito windows

Chrome Incognito and every other `FLAG_SECURE` window cannot be screenshotted;
Android returns `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` (6), and MediaProjection
sees black as well. No ordinary app, including HaramBlur, can inspect those
pixels. Hisn previously reported the window unreadable and left the page
visible; 1.5.1 reports a **PROTECTED** state and covers the whole selected
window with the touch-blocking overlay, *Hisn can't check this window*. It
retries at 2 s and clears on leaving, screen off or disabling the layer. Text
and DNS layers remain independent.

On the API 36 emulator the `FLAG_SECURE` fixture reported `STATE PROTECTED`,
`CAPTURE_ERROR 6`, `OVERLAYS 1` and zero samples from the protected window; the
people-off suite still passes. The phone was updated to 1.5.1 and Hisn's accessibility service was re-bound;
Chrome Incognito then showed the protected-window cover. No adult image,
protected pixels, root, Xposed or secure-flag bypass is used. Details:
[the 1.5.1 record](../docs/ANDROID_1.5.1.md).

## 1.5.0: an opt-in people cover

Rules › Built-in checks › **Blur people** adds Off, **All faces**, **Women
(estimated)** and **Men (estimated)**. A matching face covers the whole selected
window with a touch-blocking accessibility overlay. The cover is a 10 px
abstract snapshot on an opaque scrim, not a readable face blur; it clears on a
later clean sample, when the person leaves, on screen off, or when the layer is
turned off. The adult classifier still runs in the same pass and still closes
the detected app.

People mode switches the guard to the packaged WebView face engine (Human
BlazeFace plus the gender model) at 384 px; the adult-only path stays on native
TFLite at 224 px. Women/men is a recall-biased appearance estimate, not identity;
uncertain faces are covered, so the men setting can also hide a woman when the
estimate is uncertain. All-faces mode avoids the estimate. The feature is
**faces only**, not full-body, and the cover blocks app interaction until the
person leaves.

On the API 36 emulator the pinned public-domain astronaut portrait matched in all
and female modes (male also matched under the uncertainty rule); the live
all-faces guard reported `BACKEND wasm`, `FRAME_EDGE 384`, `OVERLAYS 1`, 0
capture failures and 109–145 ms sample latency. The people-off regression suite
passed all ten checks with `BACKEND tflite-cpu` and `FRAME_EDGE 224`. No phone,
adult corpus, battery, thermal or body-only measurement is claimed, and no
comparison with HaramBlur's accuracy is established. Signed Play/direct artifacts
and hashes: [the 1.5.0 record](../docs/ANDROID_1.5.0.md).

## 1.4.1: sample moving screens at the platform floor

The fast visual cadence was 250 ms, below Android's 333 ms per-window
screenshot floor. A moving screen could therefore be rejected every other
request and then enter the one-second unreadable backoff. The cadence is now
**350 ms**, and a floor rejection is retried at the floor without counting as a
capture failure or reporting the window unreadable. The debug status reports
`RATE_LIMITED`.

On the API 36 emulator the live guard's 8 s moving fixture produced **23 real
samples instead of 11**, with **0 capture failures instead of 6**, at 24–30 ms
native inference and 24–29 ms capture. The live visual suite passes all ten
checks with `BACKEND tflite-cpu`. The model is mapped from the uncompressed
APK and its buffers are reused and zeroed; the 1.4.0 native-first runtime,
WebView fallback, score parity, verdict cache, near-miss crop, after-block
pause and text scan are unchanged. No phone, adult corpus, GPU, battery or
thermal measurement is claimed. Signed Play/direct artifacts and hashes:
[the 1.4.1 record](../docs/ANDROID_1.4.1.md).

## 1.4.0: native TFLite visual runtime

The image model now runs natively. The same nsfwjs 4.4.0 MobileNetV2 weights
were reconstructed, converted to a float32 TFLite model pinned by SHA-256, and
run on one daemon worker with the same 224×224 bilinear `alignCorners`, 0..1
normalization and 0.70/0.85 Porn/Hentai thresholds. The WebView engine remains
the fallback if the native model cannot load.

On the API 36 emulator native CPU measured **38–45 ms median** inference and
1.6–1.9 s model load, versus 123 ms/3.0 s for WASM and 188 ms/3.3 s for WebGL.
Converted scores matched TFJS to 3.34×10⁻⁶ on 12 benign views; 0/12 were
blocked. No adult-positive corpus, phone, GPU, battery or thermal measurement
is claimed. The 9.8 MB model and four-ABI libraries take the direct APK to
~29 MB (Play AAB ~16 MB); the WebView fallback assets are retained. The
TensorFlow Lite Apache 2.0 notice is in **Licenses and sources**. Core,
strings/notices, builds, lint, the API 36 device runner and signed artifacts
pass. Details: [the 1.4.0 record](../docs/ANDROID_1.4.0.md).

## 1.3.2: reuse the verdict on an unchanged frame

The visual guard still captures a still selected window every one to two
seconds, but no longer runs the model again when the coarse 16×16 signature is
unchanged within five seconds. A bounded `VisualVerdictCache` keeps one
allow/block word per selected window; a window change, scroll or content
activity, engine reset or close clears it. No pixels, URL or text are stored,
and `samples` still counts real model samples while `REUSED` reports cache hits.

On the API 36 emulator a real inference is about 123 ms median, which a cache
hit avoids; there is no phone cache-hit or battery measurement yet, and the
capture still happens. Core suites, strings/notices, builds, lint, the API 36
device runner and signed artifacts pass. No model, threshold, accuracy,
permission or stored field changed. Details:
[the 1.3.2 record](../docs/ANDROID_1.3.2.md).

## 1.3.1: a faster visual pass and a closer look

The image guard now starts on the packaged **WASM** backend instead of trying
WebGL first; the engine still falls back to WebGL and CPU if WASM is missing.
On the API 36 emulator the `auto`/WebGL path measured 188 ms median and loaded
in 3.3 s, while WASM measured 123 ms and loaded in 3.0 s. An earlier `auto` run
spent 12.1 s in WebGL.

When a full-frame Porn/Hentai score is at least 0.45 (or sum 0.60) but below the
block threshold, the guard crops the centred 68% of the frame and classifies it
once more; it blocks only if the same 0.70/0.85 thresholds are met. A failed
second look keeps the first `allow`. The crop and near-miss logic is the pure
`region.js` module, served from the packaged assets and covered by a Node test.

The 12 benign views × 3 stayed at 0 false blocks in the WASM, `auto` and 1.3.0
baseline runs. There is no adult-positive corpus, so adult recall is still
unmeasured; the crop is centred and may miss edge or very small regions; a
phone benchmark, battery and thermal measurement remain open. No model, block
threshold, capture size, polling interval, string or permission changed.
Core/strings/notices/build/lint, the API 36 device runner and signed artifacts
pass. Details and raw reports:
[the 1.3.1 record](../docs/ANDROID_1.3.1.md).

## 1.3.0: close the page, rest the app

A detection now leaves the browser page first with Back, then closes the app
with Home, so reopening the browser does not restore the page that was just
detected. Only the app whose screen was detected is held: for **1, 5, 15, 30 or
60 minutes**, default **5**. Reopening inside the period returns Home and shows
the pause screen with the remaining time. **Settings › Protection › After a
block** chooses the period; the value is a comfort setting outside the sealed
rule file, so changing it never lets a matched word or image pass.

The pause screen offers **Rest 5 more minutes**, **Rest 15 more minutes**,
**Rest 1 hour** and **Lock the screen** (API 28+). A rest extends that app's
hold; the lock uses Android's accessibility lock action. Android cannot
force-stop another app or power off the phone, so close means Back/Home, the app
stays in Recents, a browser may keep its tabs and Back depends on its history.
A real detection in a real browser is still a device check; the screens were
verified through the debug restriction fixture. Core suites, strings, notices,
debug/androidTest builds, lint and the API 36 device runner pass; signed
Play/direct artifacts pass `check-release.py` and `apksigner`. Details and
limits: [the 1.3.0 record](../docs/ANDROID_1.3.0.md).

## 1.2.3: the screen is read off the main thread

The accessibility tree walk moved off the service's main thread onto one daemon
scanner thread, so a large feed no longer stalls the guard or the app being
read. A selected app's own accessibility event is matched first, transiently:
a promotional label such as **Watch free porn** blocks immediately, while a
bare **Porn** label still waits for the full pass that can see protective
context elsewhere; only the selected window in use may act before the walk, so
an event from a window the person already left falls back to it. The walk now
reads 2,048 nodes (the old budget was 512),
checks a partial match every 64 nodes and can stop early; a static screen is
still reread every three seconds. A generation token discards a walk whose
screen changed, and a 20-second watchdog replaces a scanner that stops
reporting instead of leaving the text layer silent.

The image layer keeps its WebView and model warm while the screen is on, with a
two-minute screen-off grace; captures at the model's 224 px input edge; gives a
frame 5 s; and backs off 1 s, 2 s then 4 s on a window Android refuses to
capture. A window change clears the backoff and the sampling interval. The
debug status reports the selected backend, frame edge and capture, inference
and sample timings.

`sh test-core.sh` passes every core suite, including the new `LabelScan` case
for the 2,048-node budget (a 700-label screen past 512 and a 1,500-label screen past 1,024)
and the updated `VisualDuty` grace. `assembleDebug`,
`assembleDebugAndroidTest` and `lintDebug` (0 errors) pass, and
`CoreDeviceTestRunner` passes on the API 36 emulator. The live fixture driver's
`stress` case — 700 labels with the word at 650 — blocked on the same emulator
through the tree scan. No 1.2.3 phone run, device benchmark or adult-accuracy
claim is made. The signed Play APK/AAB and direct APK pass `check-release.py`
and `apksigner`. Details and limits:
[the 1.2.3 record](../docs/ANDROID_1.2.3.md).

## 1.2.2 the unlisted-host guard

Two on-device rails now sit in front of the seed list in the DNS policy, after
the person's saved domain allowance:

- **Adult TLDs.** A name whose final label is `.adult`, `.porn`, `.sex` or
  `.xxx` is blocked without a list entry. `.cam` is deliberately excluded: it
  is generic and has ordinary camera uses.
- **Hostname keywords.** The signed 116-term layer the Mac and Chrome already
  use — 59 token terms, 57 substring terms, `never_keyword` removal and exempt
  hosts. A new host such as `arab-sex-tube.com` is blocked even though no seed
  list carries it, while `essex.gov.uk`, `sussex.ac.uk`, `tasks.office.com`,
  `milford.ct.us`, `pornic.fr`, JINS and Kos stay reachable.

The terms file is read synchronously from the APK-pinned `terms.json` before
the tunnel starts, with the existing digest and version checks. A failure
leaves the seed enforcing and the keyword layer off; unverified bytes are never
used. Decisions are cached at 8,192 entries so a repeated DNS question does not
repeat the folding work. The same composed policy judges CNAME/DNAME/SVCB
targets through `DnsReplyGuard`.

`sh test-core.sh` passes every core suite, including the new
`HostTermPolicyTest` over all 42 shared `host_cases.json` cases and
`AdultTldPolicyTest`. The 1.2.1 protection pass — the stale accessibility-grant
read and the classifier retry rewrite — is retained. No new permission,
activity, service or stored field was added; a signed 1.2.2 APK was not built
for this pass. Version 1.2.2, versionCode 31. Details and limits:
[the guard record](../docs/BLOCKING_GUARD_2026-11-06.md).

## Protection pass (1.2.1–1.2.2)

- **The permission check has a second source.** `AccessibilityManager`'s enabled
  list is a cache; a process that outlived a re-enable can keep a stale copy and
  report a running guard as **Permission required**. `Permissions.accessibility`
  and `MainActivity.guardPermissionEnabled` now fall back to Android's own
  `enabled_accessibility_services` value (gated on `accessibility_enabled = 1`,
  both entry formats accepted, parsed by `app.hisn.core.AccessibilityGrant`). A
  bound service with a stale cache reads **reconnect required**, which is true.
- **A bound service can no longer stay unarmed.** The setup moved into an
  idempotent `startGuard()` called from `onServiceConnected` and from the first
  accessibility event, so a service that Android rebound without repeating the
  callback is armed on its first event instead of sitting silent with
  `connected` false.
- **Classifier failures back off instead of blinding.** `VisualGuard.fail()`
  used to refuse sampling for a flat 30 s and free the model. `VisualRetry`
  retries after 5 s, doubles each further failure, keeps the 30 s cap and is
  cleared by a finished sample.
- **The live driver is hold-aware.** `test-live-visual.py` now asserts the 60 s
  post-detection hold, waits it out, then asserts sampling recovers; its adb
  timeout is 45 s. Ten checks pass on the owner's phone; measured cadence with
  the real model is ~3.8 s on a still fixture and ~1.8 s on a moving one.
- Record: [docs/ANDROID_PROTECTION_PASS_2026-11-06.md](../docs/ANDROID_PROTECTION_PASS_2026-11-06.md).

## UI/UX pass (1.2.0)

- **One line per rule.** `Ui.settingRow` is a 22 dp line icon, a 15.5 sp title,
  an optional 12.5 sp summary and an optional trailing answer, 54 dp minimum
  height. `SettingRow.setValue` writes the answer with its state colour and
  keeps the spoken label in step; `setLocked` swaps the chevron for a lock
  instead of washing the row out. The filled icon tile is gone.
- **Sections are labels, not headers.** `Ui.sectionLabel` puts a quiet 12.5 sp
  label above a card, so only items carry icons.
- **The app bar names the page.** Protection shows **Hisn** and its tagline;
  Rules and Settings show their own names, and the separate page titles were
  removed. The bar is 56 dp high.
- **Protection.** The hero is tinted by its own verdict
  (`bg_hero_attention`). The DNS card has a headline with a `Ui.statusPill` and
  a body that states the consequence when the layer is off (`dns_off_body`); the
  start button appears only when there is something to start, so the disabled
  `DNS filtering is on` button is gone.
- **App protection** is a standalone status row on Rules with the same
  disclosure its button opened, not a card holding one line.
- **Row summaries stop repeating their titles** in English and Arabic
  (`time_limits_off`, `schedule_off`, `schedule_summary`, `schedule_summary_all`,
  `commitment`, `commitment_remaining`).
- **The voluntary pause** uses a quiet exit; the enforced pause keeps its
  primary way out. Disabled controls sit at 0.58 alpha instead of 0.45, which
  failed contrast on the secondary line.
- Verified on the owner's Samsung SM-G990B2 as a release-signed direct build
  installed in place over 1.1.1 (replacing the package disables the
  accessibility service; it was re-enabled and the guard reconnected, and DNS
  filtering was restarted), in English light, English dark and Arabic dark;
  captures in [design/android-ui-2026-11-06](../design/android-ui-2026-11-06/README.md),
  record in [docs/ANDROID_UI_2026-11-06.md](../docs/ANDROID_UI_2026-11-06.md).
  ScreenZen was observed read-only for structure only.

## UI/UX pass (1.1.0)

- **First-run welcome.** Three skippable pages explain blocking, deliberate
  opening and protection locks, with one large icon and one idea per page.
  Finishing opens Rules; **Skip for now** opens Protection. It appears only when
  the app has no configured rules and has not been seen. English and Arabic RTL.
- **Take a pause on Protection.** A visible row opens the existing breathing
  pause without a block. It changes no rule, grants no permission and is
  available during a protection lock. **Back to Hisn** returns immediately.
- **Today's use in the daily-limit editor.** Each selected app shows
  **Used X of Y today** with a progress bar from Android's on-device usage
  record. Without Usage access the editor says so instead of showing zeros.
- The pause row moved near the top of Protection, after the hero/attention area.
- ScreenZen's Android onboarding was observed read-only on the owner's phone;
  its three-value structure informed the welcome, but Hisn uses its own copy,
  icons, colours and components. No ScreenZen code, assets or copy was used.
- Verified on the API 36 emulator in English and Arabic, including Skip/Set up,
  page-by-page back, the voluntary pause and the "Used 4 min of 15 min today"
  row. The owner's Samsung updated from 1.0.0 to 1.1.0 in place and rendered the
  Arabic welcome. No full physical-phone enforcement or Play claim.

## Protection lock (0.9.0)

The in-app commitment is now the **protection lock**, and it freezes every saved
rule instead of only refusing weaker ones.

- While a lock runs, the Rules page is read-only: domains, words, blocked and
  inspected apps, browser selection, daily schedule, built-in text/image/short
  video switches, SafeSearch and the gray screen cannot change. Starting a
  longer lock is allowed; a guardian-signed policy may still add rules through
  the separate tighten-only path.
- `PolicyStore.save` enforces the freeze for every caller; the UI also disables
  each control and shows a "Rules are locked" card with the remaining time.
- In the direct build the accessibility removal guard is armed by the lock even
  when **Keep Hisn installed** is off. The boot-shield copy and the 15-minute
  layer watchdog use the same effective state, so uninstall, App info, disable,
  Safe Mode and VPN screens are closed while the lock runs. A device-owner
  install applies Android's uninstall block for the lock as before.
- The Play build cannot block its own removal. While a lock runs it adds an
  attention item saying so, rather than letting the lock read as stronger than
  it is.
- Limits are unchanged and stated in the app: the accessibility service can be
  turned off, boot-key Safe Mode, ADB, root and a factory reset remain outside
  the guard, and the lock does not cover Android settings, app data, the device
  clock or another device.

Verified through the product UI and `CoreDeviceTestRunner` on the API 36
emulator, including the read-back of disabled controls and an Arabic RTL
capture; no physical-device run of 0.9.0 is claimed. See
[the 0.9.0 record](../docs/ANDROID_0.9.0.md).

## Daily time limits (1.0.0)

Rules now has a separate **Daily time limits** card. The owner chooses apps and
one foreground-time limit from 5 minutes to 24 hours. When Android's own
on-device usage record for an app reaches the saved limit, Hisn closes that app
and shows the pause screen with a time-limit reason until local midnight.

- The limit is a saved rule. A protection lock freezes it in both directions
  with every other rule; the guardian tighten-only path preserves saved limits
  and cannot remove or raise one.
- Enforcement needs both Hisn accessibility and Android's separate **Usage
  access** special permission. The Rules card says which of the two is missing,
  and Protection adds an attention item when limits are saved without Usage
  access. A limit is never reported as enforced without the grant.
- Hisn reads Android's `UsageStatsManager` foreground total for the current
  local day and caches one query per app for 15 seconds. It checks a limited app
  when it comes on screen, while it stays on screen, and in the once-a-minute
  sweep. A limit can therefore be enforced a little late while a static screen
  is left open.
- The Usage record resets at local midnight. Changing the device clock, time
  zone or date, or clearing Android's usage data, can change or reset it. Hisn
  does not claim a trusted clock or tamper-proof counting.
- Settings, dialers, launchers, installers and Hisn itself stay excluded. This
  limits selected apps; it is not a device-wide time budget, an open-count
  limit, or a parental-control usage report. Nothing is uploaded.

Verified for 1.0.0: `AppTimeLimitTest`, the `SavedSettings` sealed round-trip,
`CoreDeviceTestRunner` time-limit persistence/freeze checks on the API 36
emulator, the Rules editor and Arabic summary through the accessibility tree,
and one live 5-minute enforcement pass against the test-only fixture activity
on the same emulator. See [the 1.0 record](../docs/ANDROID_1.0.md). No
physical-phone run of 1.0.0 is claimed.

## Protection page and install lock (direct build)

Direct builds only; Play builds compile both out under the accessibility policy
explained in [Android tamper resistance](../docs/ANDROID_TAMPER_RESISTANCE.md).

- **A full-screen page replaces the closed settings screen.** When the guard
  closes Hisn's uninstall, App info, storage, device-admin, accessibility or
  Safe Mode screen, `ShieldActivity` now appears with the reason, holds its two
  buttons for eight seconds, and then offers *Back to Hisn* or the home screen.
  It reads nothing itself, and the system Home button, the dialler and emergency
  screens stay reachable.
- **VPN settings are closed too.** While **Keep Hisn installed** is on, a
  Settings VPN screen that names Hisn and offers disconnect, forget, always-on,
  edit or settings is closed and replaced by the shield page. This covers the
  other common way to switch off the DNS layer without uninstalling.
- **Battery and permission setup.** Guide now has **Keep protection running**,
  which opens Android's unrestricted-battery request and reports whether Hisn is
  exempt. Notifications and accessibility remain separate, explicit setup steps.
- **A separate install switch.** **Also stop new app installs (downloads)**
  closes the package installer's Install confirmation and any Play Store page
  that offers Install (home, search result or app page), so a new browser or VPN
  cannot be installed from those screens while it is on. App updates, Hisn's own
  update page and the store's update list stay usable.
  It is sealed with the other settings and cannot be turned off during a
  commitment.
- **Saved and watched.** The install switch uses the existing sealed settings
  file and copy; the layer watchdog counts either screen-guard switch as an
  armed accessibility layer.
- **Verified on the API 36 emulator (`hisn_ui`), direct debug build:** Hisn's
  App info and a Play Store app page were both closed and showed the correct
  reason page, the Arabic page rendered RTL, the eight-second hold disabled both
  buttons, and *Back to Hisn* opened the app. The real package-installer screen,
  TalkBack, an OEM skin and a physical device remain unverified. Included in
  version 0.9.0.

## Managed lockdown: new users, clock, default apps and strict mode (device owner)

Device-owner only. While **Managed lockdown** is on, Hisn now also holds
`no_add_user` on every supported Android, on Android 9+ (API 28)
`no_config_date_time` and, on Android 14+ (API 34),
`disallow_config_default_apps`. Android 8 and 13 are never asked for a later
key. The status readback, repair and reverse release order include all three,
and `test-live-managed-status.py` expects five keys on Android 8, six on
Android 9, seven on Android 10–13 and eight on Android 14+; with **Strict** on,
nine on Android 14+, eight on 10–13, six on 9 and five on 8. Physical device
acceptance is still pending.

A separate **Strict** switch adds `no_install_unknown_sources_globally` on
Android 10+. It is off by default because it also blocks a manual sideloaded
Hisn update; the guardian turns managed lockdown off when no commitment runs to
update the phone. USB debugging stays available, so ADB verification and
recovery still work. A device-wide install block (`no_install_apps`) is still
not used because it would freeze Play updates as well.

## Always-on DNS VPN (device owner)

Device-owner only, and only while DNS filtering is already on. When managed
lockdown holds, the app calls `setAlwaysOnVpnPackage(Hisn, false)`, reads the
package back, and clears it when DNS is turned off, the lockdown is released or
the VPN is revoked. The manifest now advertises always-on support.

This is the DNS-only tunnel, not a full tunnel, and it does **not** enable VPN
lockdown: non-DNS traffic is never routed through Hisn. A full-tunnel service
that could use lockdown is still future work. Another always-on VPN, a revoked
VPN approval, an app-data clear or a factory reset still ends the DNS layer.
The status line and `test-live-managed-status.py` report the always-on request
separately from the eight user restrictions (nine with strict); a mismatch is out of sync, not a
green light. Physical device acceptance is pending; API 36 emulator evidence, including Settings refusals, is in [the live record](../docs/ANDROID_LIVE_TEST_2026-11-01.md).

## Blocked-app suspension (device owner)

Device-owner only, off by default. While **Managed lockdown** holds, the
separate **Also suspend blocked apps in Android** switch calls
`setPackagesSuspended` for every installed app in Hisn's block list. The app
disappears from the launcher and cannot be opened; its data is kept. The status
reads `FLAG_SUSPENDED` back, and unblocking the app, turning the switch off or
releasing managed mode resumes it.

The cleanup depends on Android listing installed (including suspended)
packages. If app data is cleared, the block list is gone and the app relies on
that listing to resume anything still suspended. Physical device acceptance is
still pending; do not treat this as proof that a stranded app cannot remain.

## Optional SafeSearch rewrite

Off by default. The Network section has **Force SafeSearch on search engines**.
While DNS filtering is on, Hisn answers exact `google.com`, `www.google.com`,
`bing.com`, `www.bing.com`, `duckduckgo.com`, `www.duckduckgo.com`,
`youtube.com`, `www.youtube.com` and `m.youtube.com` with a CNAME to the
engine's restricted target. Country domains, `youtubei` APIs and other
app-specific endpoints are not guessed.

The answer is built locally (`DnsPolicy` REWRITE), preserves the question, is
limited to A/AAAA queries and never reaches the upstream resolver. `SafeSearch`
and `DnsPolicyRewrite` tests cover the mapping and the CNAME. The rewrite has
**not** been tested on a device; a wrong target certificate or an API change
could break search or playback, which is why it is opt-in.

## UI and UX refresh

This pass changes presentation only. It adds no protection rule and relaxes no
consent text; the version and list changes in the current tree belong to the
separate seed update and are recorded there.

- **Protection answers three questions first.** A hero headline says whether the
  selected layers are active or need attention, three tiles name Network, Apps
  and Images as On, Off, Needs attention or Not supported, and a short
  **What needs attention** list names the concrete reasons behind a warning.
  The existing explanatory text moves into a disclosure rather than disappearing.
- **Real bottom navigation.** Protection, Rules and Guide have icons, labels and
  a selected state; back returns to Protection, page scroll positions survive a
  rotation, and pages cap their reading width on tablets and landscape.
- **Switches and searchable lists.** Layer buttons became switches whose summary
  says what the layer does or what the policy is doing. App lists gained search,
  icons, a live count and an accessible checked state; editors count lines and
  keep their refusal reason inline. The schedule form is grouped and keeps the
  same validation.
- **A calmer, legible surface.** Semantic day/night colours, one type scale,
  rounded cards, vector icons, notification colour and the white shield small
  icon, an adaptive launcher icon, an Android 12 splash theme and two launcher
  shortcuts for Rules and Guide. The stale 0.5.5 subtitle now formats the real
  version name.
- **The pause screen.** The reason, an immediate **Return to home screen**
  action and the breathing card are separated; the breathing exercise gained a
  step counter and cycle dots. Every reminder, need choice and the daily tally
  remain.
- **Accessibility.** Headings, live regions that do not repeat unchanged text,
  switches that read their summary, checkable app rows, 48 dp targets, RTL
  mirroring and colour-plus-text state. Private screens keep `FLAG_SECURE`;
  every consent dialog keeps its original words.

Verification is local: debug and release builds, lint (0 errors; the 11
debug-only warnings already present), string parity (351 strings, 13 plurals),
core tests, and both release-distribution checks pass. Twenty API 36 emulator
captures cover light, dark, Arabic, attention, restriction, commitment,
dialogs, keyboard insets, 130% text and landscape in
[design/android-ui-2026-10-30](../design/android-ui-2026-10-30/README.md);
secure app chooser and editor windows were checked through their accessibility
tree. Physical-device, TalkBack and Arabic OEM acceptance remain open. See
[the UI refresh record](../docs/ANDROID_UI_REFRESH.md).

## 0.8.4 Schedule allowlist

- **The daily schedule can now block every app except a keep list.** The editor
  has two modes: block the apps you choose, or block everything and keep the
  apps you choose available. The keep list is what the same app picker edits,
  and the schedule line in Rules says which mode is active.
- Coverage rules are unchanged in spirit. Switching from a blocklist to an
  allowlist during a commitment is refused unless every previously blocked app
  is still blocked, an allowlist can only become another allowlist whose keep
  list did not grow, and the window may not shrink.
- The guard watches an allowlist only inside its window and for ten minutes
  before it, so the one-second checks do not run all day; a once-a-minute
  metadata sweep covers an app opened earlier and left untouched. Settings,
  launchers, the dialer, installers and Hisn itself stay reachable in both
  modes.
- Version 0.8.4, versionCode 24. Verified by unit tests and a live allowlist
  window on the Samsung SM-G990B2 (blocked app refused, kept browser opened);
  the owner's saved schedule was restored afterwards.

## 0.8.3 Browser coverage

- **Rules → Browsers without protection** lists the installed apps that can
  open ordinary web links and names the layer that covers each one: blocked,
  inspected for text and images, DNS filtering only, or no layer. Browsers with
  no covering layer start checked, so one tap blocks them all.
- **Keep only this** selects every other installed browser in one tap; saving
  blocks them and, with blocked-app suspension on, suspends them. It is an
  allowlist of the browsers visible at that moment, not a rule for a browser
  installed later; strict unknown-source mode reduces that route, and a
  Play-installed browser still appears and must be handled.
- The screen states its limits in place: DNS filters names only, so a browser
  with its own VPN, Tor or private resolver can leave that path and should be
  blocked as an app. Blocking a browser does not stop installing another one,
  and Android settings can still change it.
- Version 0.8.3, versionCode 23. New package-visibility queries for `http` and
  `https` handlers are the only manifest change. Device acceptance is still
  pending beyond the local debug install.

## 0.8.2 Seed v5 coverage

- Bundles seed v5's **239,885-domain core tier** (239,880 after the client's
  five IP-entry exclusions), up from 159,768. The seed still changes only with
  a new signed APK; the 90-day freshness warning is unchanged.
- The licenses/sources screen names `blocklistproject-porn` (Unlicense) and
  `oisd-nsfw` (free commercial), whose selected subsets are now in the bundled
  core. The mixed ad/tracker snapshot is no longer in the core; it remains in
  the full Mac/DNS list.
- Version 0.8.2, versionCode 22. See the
  [coverage pass record](../docs/COVERAGE_PASS_2026-10-30.md). Device
  acceptance is still pending.

A 2026-10-30 emulator record covers live text and whole-app blocking, the DNS
local-block and route checks, and a one-day device-owner uninstall lock. It also
records the test-only fixture limitation and the read-only physical-phone
inspection. See
[the live test record](../docs/ANDROID_LIVE_TEST_2026-10-30.md).

## 0.8.1 List age, visible watchdog and the update decision

- **Android 1.0 updates the list only through a new signed APK.** The bundled
  list's signed build date is checked in `SeedFreshness`; after 90 days the
  Protection page says how old it is and that a newer signed build is the only
  update path. There is no runtime list download and no second trust path.
- **The background check is visible.** Protection shows the last run time, a
  lost layer, or notifications being off. Enabling **Keep Hisn installed** on
  Android 13+ requests the notification permission that the closed-app warning
  depends on.
- The in-app Guide now covers the optional managed lockdown, the 15-minute
  check, the lack of always-on/lockdown VPN, and the APK-only list update.
- Version 0.8.1, versionCode 21. See
  [the 0.8.1 record](../docs/ANDROID_0.8.1.md).

## 0.8.0 Private DNS lock and the layer watchdog

- **Private DNS joins managed lockdown** on Android 10+ (API 29):
  `disallow_config_private_dns` is applied and read back with the four existing
  keys. Android 8/9 are asked for four, Android 10+ for five; a missing or extra
  key is out of sync, never a green light.
- **A persistent 15-minute JobScheduler check** compares the layers the owner
  left armed (the direct build's accessibility guard while **Keep Hisn
  installed** is on, and DNS while it is left on) with what is actually running.
  A stopped layer raises one local notification, repeats at most every twelve
  hours, and clears when the layer is back. It never restarts a service and
  sends nothing to a server. Scheduling is refreshed on app open, DNS
  start/stop, boot and keep-switch changes.
- Version 0.8.0, versionCode 20. *Historical.* See
  [the 0.8.0 record](../docs/ANDROID_0.8.0.md).

No device-owner provisioning, restriction enforcement or layer-loss device run
has been applied to the test Samsung. The actual refusals, the watchdog notice
and the release path still require a disposable, factory-reset, account-free
phone.

## 0.7.0 managed lockdown (optional, device-owner only)

On top of the 0.6.0 uninstall block, a device owner can turn on **Managed
lockdown**. While a commitment or **Keep Hisn installed** is holding the app,
Hisn applies and reads back four Android restrictions: no Safe Mode
(`no_safe_boot`), no app uninstall/disable/clear-data/force-stop for every app
(`no_control_apps`), no Settings factory reset (`no_factory_reset`), and no VPN
configuration changes (`no_config_vpn`). App controls is deliberately broad and
is a separate opt-in with a disclosure. The status screen shows ON, Off, idle or
out of sync from Android's own readback; release clears the restrictions in
reverse order, then the uninstall block, then device ownership. Version 0.7.0,
versionCode 19. *Historical.* See [the 0.7.0 record](../docs/ANDROID_0.7.0.md).

## 0.6.0 pause screen and short-video blocking

- The restriction screen opens with a pause: three guided breaths, a reminder,
  a "What do you need right now?" choice with tips, and today's pause count
  (a local date and number only). Returning home is never delayed. Checked on
  the Samsung in Arabic and English in an isolated debug install.
- **Block short videos** in Rules leaves YouTube Shorts and Instagram Reels with
  BACK, so the rest of each app stays usable, and closes TikTok. Players are
  recognised by view ID (`core/.../ShortVideoSurfaces.java`); no screen text is
  read. Commitment-locked like other layers. On the Samsung with YouTube 21.15
  (isolated debug install, 2026-10-28): all three Shorts player IDs were present
  in the Shorts tab and absent from the home feed; opening Shorts showed the
  restriction screen and left YouTube on its home feed; reopening YouTube and
  playing an ordinary video caused no intervention. Instagram Reels IDs and
  TikTok remain unconfirmed on a device (neither app was installed).

## 0.6.0 managed uninstall protection preview

A device owner can make Android refuse to uninstall an app; an ordinary install
or a legacy admin cannot. Hisn now supports that platform capability as an
explicit opt-in:

- While a commitment is active, or while **Keep Hisn installed** is on,
  `DevicePolicyManager.setUninstallBlocked` is applied for the app's user. The
  app reads the platform flag back and shows "Not managed", "Admin only",
  "Ready", "Protected" or "Out of sync" rather than guessing.
- The keep switch cannot be turned off during a commitment. Once both the
  commitment and the switch are off, **Remove managed mode** clears the block
  and releases device-owner status so Android can uninstall Hisn again.
- Boot and foreground entry reconcile a commitment that expired while the app
  was not running. If the app is never opened again, a stale block may remain;
  that is the restrictive direction and is documented.
- Provisioning is not done by the app. The ordinary route is a factory-reset,
  account-free device followed by
  `adb shell dpm set-device-owner app.hisn.android/.CommitmentAdminReceiver`.
  On a laptop, `script/guardian_provision.py` does the same install and
  appointment with a read-only dry run and a typed confirmation; it refuses
  accounts, secondary users, another owner and an unfinished setup wizard.
  A daily-use phone with accounts or another owner cannot safely be converted
  in place. No provisioning was applied to the test Samsung.

Arabic matching now shares the signed-list folds with the other clients:
Persian/Urdu letter variants, taa marbuta, alef maqsura and Arabic-Indic digits
normalise in both custom word rules and the built-in adult-text policy.
Multi-word phrases also match their no-space spelling (`nudephotos`). See
[`TextFolding`](core/src/main/java/app/hisn/core/TextFolding.java).

Core state tests, `assembleDebug`, `assembleDebugAndroidTest` and `lintDebug`
pass. The connected Samsung is not Hisn's device owner, so the build must show
managed protection as unavailable there; the actual refusal and release flow
still require a disposable provisioned device. See
[the 0.6.0 record](../docs/ANDROID_0.6.0.md) and
[tamper-resistance scope](../docs/ANDROID_TAMPER_RESISTANCE.md).

## 0.5.3 honest status, notices and housekeeping

The setup guide now reaches ready for DNS alone, the selected-app layer alone, or
both, and says which layer is off. "Last screen scan incomplete" clears after any
later complete inspection, and an image-check failure stays visible until a later
sample. The DNS notification has a proper channel name and shows the real session
state. Refused rule changes name their real cause instead of always "locked".
Image sampling stops while the screen is off and the model is freed after three
idle minutes. Remaining commitment time uses plurals and switches to days after 48
hours. Arabic disclosures now say everything the English says, Android 13+ lists
Hisn under App languages, the DNS section shows the bundled list's version and
date, and the Guide page has a Licenses and sources screen. `test-core.sh` now
checks English/Arabic resource parity and list notices; CI builds and lints the
app. Phone acceptance is pending; see [the 0.5.3 record](../docs/ANDROID_0.5.3.md).

## 0.5.2 blocking hardening and commitment clock fix

DNS filtering now turns off Firefox's default DNS-over-HTTPS, answers well-known
encrypted-DNS provider names with "not found", warns when strict Private DNS can
bypass Hisn, recovers from a degraded state, and resumes after a reboot when left
on. The screen guard catches separated spellings such as "p o r n", no longer
treats Arabic names and foods (Nick, Borno, couscous) as explicit on their own,
lets image checks run on scrolling feeds and videos, and keeps a blocked app
blocked for 60 seconds. Commitments now count running time after a restart, so
they cannot outlive the chosen duration, and a 1-day option exists. Core tests
and builds pass; phone acceptance is still pending. See
[the 0.5.2 record](../docs/ANDROID_0.5.2.md).

## 0.5.1 visual runtime fallback, release signing and 1.0 plan

The visual model now tries WebGL first. If that runtime fails to load, it retries
once in a fresh CPU runtime and keeps CPU for the rest of the process. A failed
frame stays unknown and is never replayed, and replies from a replaced runtime are
ignored. The debug benchmark accepts `--backend auto|cpu|webgl` so both paths can
be compared on the same corpus. On the Samsung (isolated test package, owner app
unchanged) WebGL took 121 ms median per call versus 1,989 ms on CPU, with identical
verdicts on the 12-view benign corpus. Injected load and frame failures recovered on
CPU as designed. Adult recall and battery cost remain unmeasured; see
[the 0.5.1 record](../docs/ANDROID_0.5.1.md).

`assembleRelease` signs the APK when all four `HISN_ANDROID_*` variables are set.
The finish line, the non-goals and the remaining device and owner steps are in
[the Android 1.0 plan](../docs/ANDROID_1.0_PLAN.md).

## 0.5.0 sampled image/video checks and app goal

The app now separates Protection, Rules and Guide. Its goal is to reduce exposure
to adult websites, explicit text and flagged visual content while supporting the
boundaries the owner chooses. English and Arabic explanations cover setup, privacy
and each layer's actual scope.

In Rules, opt into experimental image/video checks and select apps to inspect.
Android 14+ and the accessibility service are required. A bundled local model
checks downscaled selected-window samples about every two seconds. A flagged
sample returns to Home and shows a restriction notice; it does not blur a specific
image or pause a specific video. Samples are not saved or uploaded. Identified
focused inputs pause capture, and protected windows remain unreadable.

The actual model and isolated policy/commitment tests passed on Samsung. After
explicit test-service activation, nine live fixture checks passed for screenshots,
moving shapes, input/password pause, protected windows, a synthetic intervention
and capture stopping. This is not an adult-accuracy or real-video benchmark.
See [0.5.0 evidence, repeatable driver and remaining gates](../docs/ANDROID_0.5.0.md).

The [benign-image/performance baseline](../docs/ANDROID_VISUAL_BENCHMARK_2026-10-22.md)
adds twelve correlated views from four public-domain/CC0 photographs, each run
three times through the production classifier on Samsung. No view was falsely
blocked, no call was unknown, and repeats agreed. Median roundtrip was 1,984 ms
(p95 2,290 ms), close to the nominal inspection interval. Live sampling can be
slower while a request is busy. Adult recall and battery drain remain unmeasured;
the powered-phone run does not establish battery life. See
[reproduction tools](visual-eval/README.md).

## 0.4.6 explicit media labels

Complete two-word explicit media labels can now be detected without a promotional
verb. Longer research/recovery prose, weak or missing vocabulary and negative
context remain protected by regression tests. The same synthetic set now detects
23/25 intended blocks with 0/27 benign false blocks; two challenges remain.
See [the release record](../docs/ANDROID_0.4.6.md) for scope and limitations.

## 0.4.5 adult-text matching

Compact punctuation-obfuscated strong terms, such as `p.o.r.n`, are recovered
conservatively without joining whitespace, URL separators or arbitrary substrings.
Ten reviewed local context rules reduce false blocks of recovery and filtering
references; custom literal rules remain independent. The 52-example synthetic
regression set improved from 7/25 to 21/25 intended blocks and from 4/27 to 0/27
benign false blocks. Four challenge misses remain; this is not a general accuracy
estimate. See [the release record](../docs/ANDROID_0.4.5.md).

## 0.4.4 daily selected-app schedule

An optional daily local-time window can restrict a separate set of selected
apps, including overnight windows such as 22:00–07:00. It defaults off on new
and upgraded installations. Changes remain a draft until Save; equal endpoints
are rejected rather than interpreted as a full day. Use permanent app blocks
for all-day restrictions. Existing permanent blocks and word checks remain
independent, including outside the scheduled window.

The existing accessibility service checks an allowed scheduled foreground app
about once per second so a static screen can be restricted when its window
starts. It uses root ownership and explicitly selected package metadata before
action; stale events from another selected app cannot silently cancel that
foreground schedule. No alarm, screen capture, device clock change or new
permission is used. Initial content can appear before intervention. Disabling
accessibility, changing the device clock/timezone or removing Hisn remain gaps.

During a commitment, the backend rejects removing protected schedule apps,
disabling their schedule or reducing any previously blocked minute. Expanding
coverage is allowed; moving every old scheduled app to permanent blocking also
preserves coverage. Other rule editors preserve schedule choices. Schedule-only
protection is recognized by setup; an enabled schedule with no apps cannot
justify a new commitment.

Existing in-app VPN-settings links now give a dismissible commitment reminder
while time remains. It does not intercept system settings opened elsewhere or
prevent deactivation/uninstall. The separate opt-in device-owner mode added in
0.6.0 can block ordinary uninstall while a commitment runs; see
[tamper-resistance scope](../docs/ANDROID_TAMPER_RESISTANCE.md).

`test-live-schedule.py --serial SERIAL` runs isolated persistence/commitment
checks, then uses a harmless static fixture across a real upcoming local-time
boundary. It refuses to replace any saved owner schedule or run during a real
commitment. It does not change the device clock or grant permissions, and
restores its fixture additions. See [0.4.4 verification](../docs/ANDROID_0.4.4.md).

## 0.4.3 private-browser diagnostic

Debug builds can arm a fixed, additive deny of exactly `www.example.org` for
120 seconds to check a real browser path without adult pages. The lease is
memory-only, uses elapsed time, expires during policy evaluation/status checks,
and is cleared with its DNS session. It never edits saved rules or commitments.
Clearing or expiring it always returns to the owner's existing policy. No
browser settings, system permissions, DNS fallback or rule-removal controls are
introduced. Release builds use a separate no-op hook and cannot arm this lease.

The shell-only debug controller is `test-live-browser-canary.py`:

```sh
python3 test-live-browser-canary.py --serial SERIAL --action start --confirm-harmless-example-domain
python3 test-live-browser-canary.py --serial SERIAL --action status
python3 test-live-browser-canary.py --serial SERIAL --action clear
```

It requires an already-approved running Hisn DNS session. Open a fresh private
tab manually, then navigate to `https://www.example.org/` during the lease.
Do not change secure-DNS or private-tab capture settings for this test. A live
browser pass requires both an observed DNS error and an increase in the
fixed-host evaluation counter during that navigation. The counter represents
policy evaluations, not unique requests, replies delivered or browser identity.
No hits is inconclusive: private mode is not proof of cache isolation, and
independent DNS can bypass the virtual endpoint. An error alone is not proof.
Brave's protected private-tab screenshots remain protected; ask the owner to
confirm visible behavior rather than disable privacy controls. DNS/browser
caches may retain an error after the two-minute rule expires.

On 2026-10-21 the Samsung test recorded matching policy evaluations and the
owner confirmed a DNS/site-unreachable error in the new Brave private tab.
This verifies the tested path, not every browser request or bypass.
See [0.4.3 verification](../docs/ANDROID_0.4.3.md) for details and limits.

## 0.4.2 bounded TCP DNS update

The virtual IPv4 DNS address now accepts TCP port 53, alongside UDP. A small,
bounded endpoint handles connection setup, DNS length framing, split and
pipelined requests, connection reuse, duplicate/overlapping packets, partial
acknowledgements, peer MSS/window limits, retransmission and half-close. It is
not a general TCP stack. Limits are 16 flows, 4,096-byte DNS frames, an 8,192-byte
receive buffer and four queued requests plus one active per flow. Fixed partial
frame/request deadlines, idle expiry and bounded retransmission prevent
unbounded retained work. There is no out-of-order buffering or negotiation of
Fast Open, SACK, timestamps or window scaling.

Both protocols use the existing local domain policy, validated DNS replies and
authenticated TLS upstream. The four-worker/64-request queue is shared; overload
is explicit. Validated replies too large for the UDP client's advertised budget
produce a TC retry signal, not a resource record cut in half. The limit is 512
bytes without EDNS and at most 1,232 with EDNS. The TCP path remains capped at
4,096 bytes. The dashboard separates accepted TCP queries, prepared replies
(not confirmed acknowledgements) and UDP retry signals.

The explicit route check now sends four fixed queries when successful: local
synthetic block plus `example.com` over UDP, then the same pair over one reused
TCP connection. Samsung checks passed, including simulated network invalidation
and recovery, and a native local test of two pipelined blocks. A full 4,096-byte
UDP-TC-to-TCP fallback passes in core tests; it has not been reproduced as a
large native/browser reply. See [0.4.2 verification](../docs/ANDROID_0.4.2.md).

## 0.4.1 network recovery update

The active DNS session observes `INTERNET + NOT_VPN` network callbacks using
Android's normal `ACCESS_NETWORK_STATE` permission. It unregisters when the
session closes. New/lost networks, changed link properties and validation/captive
state changes invalidate resolver connections and the previous route-check
result. Unchanged callbacks and signal/bandwidth churn do not cause resets.
This is conservative monitoring of matching underlying networks, not proof of
which physical network carried every query. No Wi-Fi/mobile setting is changed.

Invalidation cancels old in-flight TLS exchanges, rejects buffered old-generation
TLS replies and reconnects later requests to the same authenticated resolver. It does
not restart a revoked VPN, introduce a fallback resolver or automatically send
public test queries. Old-network failures cannot overwrite new-network health;
a current request failure during a check prevents its result being published as
a current pass. The dashboard distinguishes unsupported TCP/other packets,
malformed packets and cancelled old-network requests without keeping payloads.
Unsupported traffic still means incomplete coverage. In 0.4.1 these diagnostics
did not implement TCP DNS; 0.4.2 adds only the bounded port-53 endpoint described
above, not full traffic filtering.
Network invalidation is not an atomic withdrawal of answers already accepted
by the endpoint: a callback can race final publication, and pending TCP segments
can still be delivered. This is not a physical-network provenance guarantee.

Core tests and the live simulated-invalidation/recheck on Samsung pass. The
simulation does not toggle Wi-Fi or prove real Wi-Fi/mobile handover. See
[0.4.1 verification](../docs/ANDROID_0.4.1.md). Earlier instrumentation evidence
is recorded separately for 0.4.0; it was not rerun after this update.

## Implemented

- A build step verifies the existing signed seed bundle with the repository's
  pinned public key before packaging `domains_core.txt` and `terms.json`. Python 3 and the
  repository's existing blocklist dependencies are required for this step.
- The APK pins the verified manifest's domain digest and entry count. Runtime
  loading checks SHA-256, UTF-8, count, duplicates and domain validity. This is
  build-time signature verification plus runtime digest verification, not a
  runtime signed-update client. No downloaded list updates are implemented.
- The current seed loads 239,880 domain rules. Five IP entries in the shared
  seed are explicitly excluded because a domain-only policy cannot enforce them.
- The opt-in adult-text loader pins the terms seed's SHA-256, checks its version,
  UTF-8, size and rule bounds, and compiles English/Arabic terms and negative
  context on this device. Together with ten reviewed local protective/recovery-context
  additions, the current policy contains 4,462 canonical rules. The additions
  are app code, not extra signed-seed entries. Loader failure is visible and
  does not disable independent custom literal rules.
- A consent screen describes resolver privacy, other-VPN conflicts and limits.
  Android's VPN approval is requested after this screen. Nothing starts on app
  launch. After a reboot, `VpnBootReceiver` restarts DNS filtering only if the
  owner left it on and `VpnService.prepare` still returns no consent screen;
  otherwise it posts a "not resumed" notification and never asks for consent.
  Android sends the boot broadcast only after the first unlock, and not to a
  force-stopped app; device battery or sleeping-app settings may also withhold it.
  An Android revocation (another VPN took over, or a disconnect in Settings)
  clears that choice and posts a "DNS filtering stopped" notification. A service
  error posts a similar notification but keeps the choice, so the next boot
  tries again. Notifications need Android's notification permission. Starting
  the service can replace another active VPN.
- A foreground service routes only `10.111.0.2/32` into the local TUN interface
  and advertises that address as the DNS server. Normal internet traffic and
  IPv6 traffic use the underlying network. This is not a full traffic tunnel.
- IPv4 UDP and TCP DNS packets are checked for sizes, checksums, destination,
  fragmentation and supported header/options structure. TCP terminates only on
  the virtual DNS address's port 53. Four workers and a shared bounded 64-request
  queue limit resolver concurrency.
  No screen content or browsing log is stored.
- Domain matches return local NXDOMAIN without contacting the upstream server.
  Allowed queries go to Cloudflare Families at `1.1.1.3:853` with authenticated
  DNS-over-TLS, certificate and hostname validation, and TLS 1.2/1.3 only.
  When `1.1.1.3` refuses or times out, the same exchange moves once to the
  second Families address, `1.0.0.3:853`, with the same identity
  (`family.cloudflare-dns.com`); `1.1.1.3` is tried again by one connection
  after 30 seconds, doubling to at most five minutes, and at once when the
  network in use changes. TLS identity failures and bad replies never move.
  Each worker reuses its connection. There is no plaintext fallback and no
  other resolver. The resolver can see allowed domain requests. Saved domain
  rules are applied immediately alongside the bundled seed.
- Only the network that protected resolver sockets use resets connections: on
  Android 12+ the best-matching non-VPN network Android reports, on Android 8
  to 11 the one Hisn ranks first the way Android does. A change on a standby
  network, such as mobile data kept up beside Wi-Fi, keeps them.
- Upstream sockets must pass `VpnService.protect`. Transaction IDs are randomized
  upstream and restored for clients; mismatched questions, IDs, oversized and
  truncated replies are rejected. A bounded reply parser checks record lengths,
  compressed names and reachable CNAME/DNAME/HTTPS/SVCB aliases against the
  policy. Unrelated aliases do not block a query. This is not DNSSEC validation,
  nor full semantic validation of every resource-record type.
- The app shows connection state, loaded-domain count and session-only blocked,
  forwarded and error counters. A connected tunnel does not prove full protection.
  A failure marks the session degraded. It returns to connected only after two
  minutes without a new failure and at least ten validated resolver answers, and
  never while network monitoring is unavailable. Error counters and a recovery
  count stay visible for the whole session. Unrelated packets (other protocols
  or ports, IPv6) and refused port-853 connections are counted separately and do
  not degrade the session.
- Android's automatic Private DNS probes port 853 on the advertised DNS server.
  Those SYNs get an immediate TCP reset, so Android falls back to filtered port
  53 instead of waiting for a timeout. Strict Private DNS (a provider hostname on
  the underlying network) is detected read-only and shown as a warning: system
  DNS may then go to that provider and bypass Hisn. Hisn never changes it, and
  answers that one hostname normally so Android's own lookup of it still works.
- Well-known public DoH/DoT provider hostnames (Google, Cloudflare, Quad9,
  OpenDNS, CleanBrowsing, NextDNS, AdGuard, Mullvad, Control D, Comcast, CIRA,
  CZ.NIC, DNS.SB, AliDNS, DNSPod, and `resolver.arpa` discovery; the list is in
  `EncryptedDnsProviders`) and Firefox's `use-application-dns.net` canary get
  local NXDOMAIN, which turns off Firefox's default DoH. This reduces but does
  not eliminate browser DoH bypass: clients with hard-coded bootstrap IPs,
  IP-literal endpoints, unlisted providers or user-forced Firefox DoH never ask
  Hisn. Hisn's own upstream is pinned to `1.1.1.3` and `1.0.0.3` and uses its
  hostname only for TLS identity, so it does not depend on resolving these names.
- Underscore names (SRV, `_dmarc`, DDR) and single-label names can never equal
  a domain rule, so they are forwarded instead of failing and do not degrade
  the session. The hostname part of an underscore name is still checked: one
  under a blocked domain, such as `_8443._https.blocked.example`, stays blocked.
- The VPN is marked unmetered on Android 10+, so it inherits the underlying
  network's metered state instead of making Wi-Fi look metered to apps.
- After a separate disclosure and owner confirmation, the DNS route check sends
  a synthetic `blocked.hisn-self-check.invalid` query through the virtual DNS
  route, then `example.com` only if the local NXDOMAIN check passes, first over
  UDP and then over one reused TCP connection (four queries on full success).
  Public queries use the existing authenticated resolver path; the probe itself is not
  protected from the VPN and has no fallback endpoint. A passed check describes
  this route only, not all apps, encrypted content or all device traffic. A
  stopped/restarted DNS session clears the previous result. The latest Samsung
  0.4.0 runs passed the local step but initially failed resolver socket protection.
  The transport now binds its socket locally before protecting it and only then
  connects; a regression test checks this ordering. The phone subsequently
  reported no active underlying internet network. Once connectivity returned,
  checks exposed reused TLS connections closed by the server. A pooled EOF/reset
  now gets one reconnect to the same authenticated endpoint; fresh errors,
  timeouts, identity failures and invalid frames do not. Tests cover stale
  recovery and no fresh-error retry. Three consecutive live checks then passed
  on the 0.4.0 APK. Broader network changes remain a release gate. The
  historical DEGRADED session state is not cleared by a passing check. No
  plaintext fallback was added.
- Supported queries receive SERVFAIL if the resolver or reply validation fails;
  unsupported packets are counted and dropped. This avoids silently switching
  to an unfiltered resolver, but can interrupt page loading.
- Rules persist in private app preferences. During an active 7-, 30- or 90-day
  in-app commitment, the settings backend rejects removal of saved restrictions
  and reductions in the remaining period. Disabling opted-in built-in adult-text
  checks is also rejected. Stronger settings are allowed. A new commitment is
  rejected until meaningful restrictions are configured; old empty commitments
  still load without erasing their remaining period and can receive added rules. The
  clock uses elapsed time and Android's boot counter, not the editable date.
  Progress is checkpointed on app pause and periodically during a DNS session;
  reboot or power-off can extend the period. Android settings and data clearing
  remain outside this in-app lock. Uninstall is outside it too unless the owner
  separately provisions device-owner mode; that mode blocks ordinary uninstall
  while a commitment or the keep-installed switch is active.
- Settings are saved twice, in `hisn_policy` and `hisn_policy_copy`, each
  sealed with a SHA-256 over its values. A damaged or unreadable main file is
  replaced by its copy and Protection asks the owner to check the rules. If
  neither can be read although settings were saved, the commitment is kept
  active, shown as unknown, for at most 90 days of running time, the longest
  one can last, instead of reading as a new installation. Clearing app data
  removes both files and resets everything.
- The accessibility service is off until the owner enables it. It acts only in
  explicitly selected apps. For a category-based checklist of apps to consider
  — not an "adult app" verdict — see
  [apps to consider blocking](../docs/APPS_TO_CONSIDER.md). Chosen blocked apps, matching literal word rules or
  built-in adult-promotion evidence in the optional mode
  return to the launcher and show a generic restriction screen. Settings,
  launchers and dialers are excluded. Traversal is bounded and skips editable
  and password nodes and their descendants. Childless labels are read, and a
  card or button's own content description only when nothing inside it has text
  and no input or password field sits inside it, so a parent never repeats an
  input value (`LabelScan`). Sensitive content may still be exposed by other
  labels; the consent screen discloses this. Screen text is not saved.
- Built-in adult-text checks use bounded local context within individual visible
  labels, rather than exempting a whole screen because of unrelated medical
  or news wording. A label of up to three words that are all explicit terms
  ("Porn", "سكس") blocks on its own, unless a label on the same screen names
  recovery, blocking, research or similar context; then only promotions and media
  labels block there. Custom rules are checked independently first; built-in benign
  context never overrides a chosen literal rule. Opt-in is off by default for
  existing installations and enabling it does not grant any Android permission
  or choose apps automatically.
- The built-in check uses the seed's English, Arabic and Franco-Arabic (Arabizi)
  terms. Most single Arabizi spellings are also names or everyday chat, so they
  count only beside a reviewed clear Arabizi term; see
  [the text and privacy record](../docs/ANDROID_0.5.4_TEXT_PRIVACY.md).
- The rule editors, app lists, schedule editor and a word tester holding text
  are FLAG_SECURE windows: Android leaves them out of screenshots, screen
  recordings and the recent-apps preview. Anyone looking at the screen and
  accessibility services can still read them.
- Long visible labels are sampled rather than skipped above 2,048 characters.
  A global 512-node/child-attempt budget and 32,768-character sample bound work;
  truncated tokens cannot manufacture whole-word matches. The dashboard reports
  incomplete inspections and configured accessibility layers independently of DNS.
- Normalized Unicode words and IDN domains must fit persistence limits before
  saving. Rejected updates preserve existing settings and remain reloadable.
- While a selected word-scanned app remains foreground, bounded periodic
  inspections run about once per second. They stop after leaving the selected
  app and on service interruption. Scroll events also trigger checks. On
  binding, the service probes current-window metadata for selected-app recovery.
- Layer health distinguishes permission enabled from service connected, as well
  as no saved restrictions, partial inspection and unreadable settings. A live
  service alone is never presented as configured enforcement.
- A shared setup guide supplies both the next-step message and its action.
  Its 7,680 tested state combinations distinguish rules, whether DNS filtering
  was left on, permission, binding, loading/error states, incomplete app
  inspections, image-check failures, DNS startup/degradation and the explicit
  route check. Each layer is optional. Ready for the app layer requires
  configured app protection, healthy service state, a ready or disabled optional
  policy and no failing image check; ready for DNS requires a running DNS layer
  and a passed check. When only one layer is chosen, the guide says the other is
  off. Unreadable settings are never ready. Ready does not mean complete device
  protection.
- "Last screen scan incomplete" is set by a partial or failed inspection and
  cleared by a later complete one, including blocked-app and schedule
  decisions. An image-check failure stays on the dashboard until a later sample
  succeeds or the layer is turned off.
- Image sampling never runs while the screen is off, and the on-device model is
  released after three minutes without a sample.
- The ongoing DNS notification ("DNS filtering status" channel) shows starting,
  connected, recent failures or a strict Private DNS bypass.
- A refused rule change names its cause: too many apps (200 per list), invalid
  input, an active commitment, no saved restrictions, a failed save or
  unreadable settings.
- The Guide page's Licenses and sources screen summarises the bundled lists'
  and engine's origins in English and Arabic and shows each full license text
  from the APK; nothing is downloaded. The DNS section shows the bundled list's
  version and build date; lists change only with a new app version.
- Selected-window root changes receive bounded retries. Warning cooldown never
  suppresses a fresh enforcement action. The explanation screen is delayed until
  after Home to avoid the observed Samsung transition race.
- The optional legacy device-admin receiver declares an empty policy set:
  no wipe, password or device-lock capabilities. Android may require admin
  deactivation before uninstalling. The receiver can show a reminder, but it
  cannot stop deactivation and cannot establish device-owner management on its
  own. The separate device-owner mode can block ordinary uninstall; it is not a
  factory-reset or administrator lock. Hisn never uses accessibility to trap the
  Android uninstall UI.

## Known coverage gaps

Apps' independent encrypted DNS, direct-IP connections, cached DNS, alternative resolvers, secure
browser DNS, other profiles and mixed-content websites can bypass this layer.
Blocking known provider hostnames narrows, but does not close, the encrypted-DNS
gap. Strict Private DNS can route system DNS around Hisn; it is warned about, not changed.
The virtual DNS server handles IPv4 UDP and bounded TCP port 53 only; client TLS
port 853 is refused with a reset, and TCP to other destinations is not served. Fragmented IP, compressed
questions and malformed multi-label names are unsupported; replies above the
4,096-byte cap, unsupported queries and resolver outages may cause page loads to fail. The
service reports errors rather than claiming those requests were filtered.

Always-on support is explicitly disabled. Do not enable lockdown for this
DNS-only route: it would prevent normal internet traffic from using the
underlying network. After boot it resumes only if left on and VPN approval
remains; it does not restart after process death or permission loss. Android
VPN settings can disconnect it.

The local tester accepts one literal rule per line with bounded rule and text
sizes. Tester inputs/results are not saved; rules entered in the separate saved
rule editor are saved. This tester checks custom literal rules, not the optional
built-in classifier. Whole-word matching avoids `sex` inside `Essex`; ambiguous
chosen rules can still match benign content. The built-in policy is a conservative
adult-promotion heuristic, not an unrestricted semantic classifier. It can miss
adult text without the required evidence or misclassify harmless text. Cross-node
phrases, hidden/secure text, images,
video and all-app coverage are not guaranteed. Text beyond traversal limits
may not be inspected, and intervention happens after an accessibility event.

## Build and test

Use JDK 17+, Android SDK platform 37 and build tools 36.0.0. The wrapper pins
Gradle 9.3.1. Set `JAVA_HOME` and `ANDROID_HOME`, then run:

```sh
sh test-core.sh
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest lintDebug assembleRelease
```

Add `-PhisnDistribution=direct` for the sideload build. It turns on the
accessibility uninstall-screen guard (`BuildConfig.SELF_PROTECTION`) and adds
`-direct` to the version name. Never upload a direct build to Google Play; see
[ANDROID_TAMPER_RESISTANCE.md](../docs/ANDROID_TAMPER_RESISTANCE.md).

CI runs the same commands in its `android` job. `test-core.sh` starts with two
resource checks: `check-strings.py` (English and Arabic have the same string
keys, plurals and placeholders, every Arabic plural category, nothing aapt2
would reject, and every referenced key exists) and `check-notices.py` (every
bundled list source has its license text and an entry in both summaries). Add
any new `*Test` class to the loop in `test-core.sh`.

The core suite covers word boundaries, Unicode, bounded lists, seed tampering,
the actual full seed, domain exceptions, DNS/EDNS packets, packet checksums,
fragment rejection, malformed inputs, resolver timeouts and local UDP exchanges.
TCP tests cover framing, pipelining/reuse, sequence wrap, partial/future ACKs,
overlap/duplicates, peer MSS/window limits, retransmission, FIN/RST, queue/flow
caps, deadlines and late completions. Packet mutation/fuzz tests and a validated
4,096-byte UDP-TC-to-segmented-TCP fixture run without public network access.
The suite also checks all 7,680 setup-guide combinations, actual-seed adult-text
fixtures, local context, Unicode/evasion boundaries, bounded classifier work and
the managed-uninstall state/commitment matrix.
DNS-probe tests use loopback responders for route-check response validation,
timeouts, cancellation and no public query after a failed local check.
TLS tests generate a disposable localhost certificate, verify hostname and
trust failures, and test encrypted exchanges and connection reuse. Commitment
tests cover expiry, clock rollback, boot changes and checkpoints. The standard
suite uses loopback only. `ResolverProbe --public-example-only` is a separate,
explicit optional diagnostic which contacts Cloudflare for `example.com` only.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The app supports
Android 8.0+. A debug build is signed with a development key, not a production
release certificate.

The optional test APK uses a custom instrumentation runner. After installing
the app and test APK, run:

```sh
adb shell am instrument --user 0 -w app.hisn.android.test/app.hisn.android.CoreDeviceTestRunner
```

This test checks the seeds, Arabic rules, word boundaries, a local DNS block,
saved settings, built-in opt-in, commitment and uninstall-guard checks in
isolated test preferences without enabling services. It passed on the Samsung
`0.6.0-direct` build on 2026-10-28. To read the managed state from a connected
debug build without changing it, run:

```sh
python3 test-live-managed-status.py --serial SERIAL
```

On an ordinary install it must report not-managed and uninstall not blocked.
`--expect-managed` is only for a disposable device that has deliberately been
provisioned as Hisn's device owner. It now also checks the uninstall keep switch and commitment
guard, and that a non-owner build reports no protection and performs no platform
change. Actual VPN routing, accessibility interventions, admin deactivation
behavior, device-owner blocked-uninstall behavior and lifecycle need separate
controlled device tests after approving the corresponding consent screens. On
2026-10-28 the 0.6.0-direct runner passed the uninstall-guard checks and the
read-only managed-status check reported the phone as not managed. On 2026-10-20 the device runner
passed on the Samsung with 159,762 bundled domains, Arabic/boundary matching,
local DNS blocks, Unicode persistence/rejection, long-text sampling and
commitment checks, verified adult-term loading, custom-rule independence,
empty-commitment rejection and opt-in persistence/commitment gating. Generated
test preferences are separate from `hisn_policy`
and cleared after testing; real user rules and commitments are untouched.

See [the REA review](../docs/REA_BLOCKERHERO_2026-10-19.md) for competitor
observations, evidence identifiers, independent improvements and remaining gates.
See [the 1.0 ScreenZen/BlockerHero static comparison](../docs/ANDROID_REA_SCREENZEN_2026-11-03.md)
for the daily-time-limit design input and what was deliberately not copied.
See [live Samsung guard tests](../docs/ANDROID_LIVE_GUARD_2026-10-19.md) for
verified fixture results and the opt-in external test driver. Instrumentation
restarts Hisn and can interrupt accessibility; use the external driver for live
service tests instead. Its shell-only setup activity is excluded from release.
The historical [0.3.1 verification](../docs/ANDROID_0.3.1.md) includes the successful periodic
foreground-check and stop-outside-selected-app tests, installed APK identity and
the visually checked Arabic dashboard. Follow-up controlled normal-content,
English/Arabic adult-promotion and benign medical-context fixtures passed on
2026-10-20. These are fixture checks, not proof of general app coverage.

A separate small, read-only text corpus produced 9/16 adult-text hits and 1/18
benign false positives. Its misses and false positive remain release-quality
work: these denominators describe only those fixtures, not general detection
accuracy. The current UDP/TCP VPN-path check and short Brave browsing smoke test
pass, but physical network-transition reliability, longer browsing sessions and
broader browser coverage still need acceptance testing. Passing core,
instrumentation, accessibility or route checks is not complete protection.

See the [0.4.0 verification record](../docs/ANDROID_0.4.0.md) for the installed
APK identity, socket-ordering correction and connected-network result.
See [0.4.2](../docs/ANDROID_0.4.2.md) for the current APK, TCP evidence and remaining
coverage limits. `test-live-tcp-local.py --serial SERIAL` performs only two
synthetic local blocks on the already-approved running VPN; it does not test
public resolver access or grant permissions.

References: [Android VPN](https://developer.android.com/develop/connectivity/vpn),
[VPN routes and address families](https://developer.android.com/reference/android/net/VpnService.Builder),
[foreground service types](https://developer.android.com/about/versions/14/changes/fgs-types-required),
[Cloudflare Families](https://developers.cloudflare.com/1.1.1.1/setup/#1111-for-families).
