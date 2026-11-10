# The browser extension: what to improve next

Date: 8 November 2026. Status: partially implemented. Items 1.1 (restore the
`offscreen` permission) and 1.2 (manifest/API contract test) are in the working
tree; the rest is proposed work. Version reviewed: `extension/manifest.json`
1.5.3.

This is a concentrated review of the Chromium extension only. It does not
re-plan the Mac app, iPhone/iPad or Android, and it does not mark any release
gate complete. A separate, repository-wide plan is
[the 8 November comprehensive plan](COMPREHENSIVE_IMPROVEMENT_PLAN_2026-11-08.md);
this one exists because the browser has its own failure modes — a service
worker that is torn down every thirty seconds, a page that is the least trusted
context in the product, and a store listing that does not exist yet.

## The short version

1. **The opt-in visual layer was dead in Chromium.** The manifest stopped
   declaring the `offscreen` permission its broker calls, so `chrome.offscreen`
   was undefined and the layer reported “unavailable”. Anyone who was already
   opted in could have every image and video hidden with no verdict and no
   explanation. The permission is restored in the working tree; the remaining
   work is to confirm the full scan-and-verdict path and to decide what “engine
   unavailable” should do instead of blanking the page.
2. **Publish it.** The extension has no distribution route beyond Load unpacked;
   the store record is still a 1.5.2 record. After the permission fix, upload
   unlisted, wire the store ID into native messaging, and test a managed
   force-install.
3. **Measure, then make the page cheap.** The scanner reads the whole body and
   walks the DOM for shadow roots every few seconds, including background tabs;
   the 30-second heartbeat rewrites rules and receipts that did not change.
   Benchmark first, then gate the fixes with budgets.
4. **Tell the truth about private windows.** `incognito: "spanning"` leaves the
   extension off in private windows until the user opts in, and the UI never
   mentions it. One notice, one link, no fake toggle.
5. **Build a quality loop.** The 50-case corpus is a regression set, not a
   measurement; false positives have no route back to the maintainers. Add a
   private labelled benchmark and a user-initiated, local report export.

Everything else below is sequencing, evidence and acceptance criteria.

## Evidence and boundaries

What was actually run for this review:

| Check | Result |
| --- | --- |
| `extension/test/run.sh` | 40 suites plus the syntax pass, 9,212 checks, all pass |
| `extension/eval/run.sh 50` | 50 cases (22 adult, 28 benign), 0 missed, 0 false positives |
| Rule inventory (counted) | 474 block rules carrying 473,172 domains; 116 keyword, 63 encoded-query, 30 evasion, 18 web-protection, 6 SafeSearch, 5 short-video, 2 adult-TLD, 1 path rule |
| Package size | `dist/hisn-extension-1.5.3.zip` 5.9 MB; visual runtime on disk 5.3 MB bundle + 840 KB models + 1.1 MB WASM |
| Vocabulary | 5,831 signed terms; index build measured at 7–30 ms in `jsc`; 200,000-character page scored in ~22 ms |
| Live API probe | Helium 0.21.1.1 (Chromium 157): an extension without `offscreen` sees `chrome.offscreen === undefined`; the same probe with the permission sees an object with `createDocument` |
| Code read | manifest, worker, policy, scanner, visual broker, options/popup, rules, tests, CI, docs |

What was **not** run: the repository's browser suite, a full visual-scanner pass,
a live store install, native messaging, and any personal browser profile.
Headless Chromium cannot start in this macOS session (the repository records a
related macOS headless-Chrome failure in [ci.yml](../.github/workflows/ci.yml)),
so the browser checks were limited to the minimal API probe above. The P0 is
confirmed by that probe and by the API contract; what remains is to confirm the
full scan-and-verdict path after the fix.

## Where the extension stands

| Area | Present in 1.5.3 | Evidence |
| --- | --- | --- |
| Domain blocking | 473,172 domains at priority 3, bundled and updatable | `rules/dnr_block_rules.json`; `worker/rules.js` |
| URL keywords | 116 rules, Latin and Arabic-transliterated paths/queries | `rules/dnr_keyword_rules.json` |
| Adult TLDs | `.adult`, `.porn`, `.sex`, `.xxx`, always on | `rules/adult_tlds.json`; `lib/policy.js` |
| SafeSearch | Google, Bing, DuckDuckGo, Yahoo, Brave; YouTube Restricted Mode | `rules/safesearch.json`; `rules/web_protection.json` |
| Page text | 7 languages, density + threshold scoring, X/Twitter per-post adapter, shadow DOM, frames | `lib/score.js`, `content/scan.js`, `content/feed.js` |
| Visual | Opt-in adult classifier + face detection, offscreen engine, hide-before-verdict | `visual/`, `content/visual.js` |
| Hand rules | Blocks, allowlist, custom words, ignored words, text exemptions, file export/import | `lib/transfer.js`, `worker/updates.js` |
| Commitment | Mac-app-owned timed locks; browser clamps to strict if the app goes silent mid-lock | `worker/native.js`, `lib/policy.js` |
| Repair | Versioned receipt; rules, rulesets and scanner registrations are read back | `lib/status.js`, `worker/rules.js` |
| UI | Popup, dashboard, block page with breathing pause; English and Arabic, RTL, dark mode | `popup.*`, `options.*`, `blocked.*`, `ui.css` |
| Distribution | Unpacked/development only; store checklist unchecked; managed-policy docs written | `docs/EXTENSION_STORE.md` |

This is a mature, unusually honest codebase. The improvements below are not a
rewrite; they are the next order of work that the code and its own records
point to.

---

## 1. Repair before extending (P0, this week)

### 1.1 The visual layer cannot start in Chromium: `offscreen` is not declared

`visual/broker.js` runs inference in a Chromium offscreen document:

- `supported()` returns true only when `chrome.offscreen.createDocument` exists
  (`visual/broker.js:29`);
- the engine is opened with `chrome.offscreen.createDocument(...)`
  (`visual/broker.js:48`);
- every status call returns `unavailable` when `supported()` is false
  (`visual/broker.js:199-204`).

The manifest no longer declares the permission the API depends on:

```json
"permissions": ["declarativeNetRequest", "storage", "alarms",
                "nativeMessaging", "scripting"]
```

(`manifest.json:16-22`). Chromium gates the namespace on
`"permissions: ['offscreen']"` — the feature file is
[`_api_features.json`](https://chromium.googlesource.com/chromium/src/+/main/extensions/common/api/_api_features.json)
(`"offscreen": { "dependencies": ["permission:offscreen"] }`), and the API page
says the permission must be declared. Without it, `chrome.offscreen` is
undefined, `supported()` is false, and `getVisualStatus` reports
`unsupported-runtime`. This was confirmed live: a minimal probe extension loaded
in Helium 0.21.1.1 (Chromium 157) reported `typeof chrome.offscreen ===
"undefined"` without the permission, and an object with `createDocument` when
the permission was added.

The removal happened in commit `63441a3` (1.2.1), described as removing an
“unused” permission. The broker was already using it. No test compares declared
permissions with the APIs the source calls, so the regression has been invisible
since.

**Why this is P0 rather than a feature gap.** As shipped in 1.5.3, a new user
could not enable visual checks: the controls are disabled whenever
`visual.available === false` (`options.js:537-539`). Worse, anyone who opted in
before 1.2.1 still had `inspectVisual: true` stored. `syncVisualScanning`
registers the scanner anyway (`worker/scanners.js:88-140`), and
`content/visual-gate.css:5-9` hides **every** image and video until a verdict
marks it `checked`. An unavailable engine never produces a `ready` verdict, so
`content/visual.js:336-339` routes every capture to `unknown()` (`:155-160`),
which keeps the media withheld. The result was a web with no images and no
explanation.

**Change:**

1. Add `"offscreen"` to `manifest.permissions` and a justification row to the
   store record (`docs/EXTENSION_STORE.md:60-70`), replacing the sentence at
   `:67` that says the permission was unused.
2. Decide the failure mode for “the engine can never run” separately from “this
   image has no verdict yet”. Recommended: on `available: false`/`unsupported-
   runtime`, remove the visual gate and tell the user visual checks are
   unavailable; keep transient failures hidden. An outage of the classifier is
   not evidence that the page is unsafe, and a permanently blank web is the
   fastest route to uninstall.
3. If the product decides the visual layer is **not** a Chromium feature, then
   remove it from the Chromium package, controls, store copy and docs instead —
   but do not leave a switch that cannot work.

**Acceptance:** in Chrome for Testing and Helium, with the extension loaded
unpacked, `chrome.offscreen` is defined in the service worker; enabling adult
visual checks reaches `state: "ready"`; a known adult fixture is hidden; an
unknown/engine-failure fixture either stays hidden **with a visible notice** or is
revealed by the chosen policy; no page has `visibility:hidden` media with a
missing engine.

**Status:** step 1 is implemented — the permission is declared, the store
record's permission table and justification now include it, and the live probe
above confirms the namespace appears. Steps 2–3 (the failure-mode policy and its
tests) remain open.

**Effort:** S for the permission; M for the failure-mode policy and tests.

### 1.2 Add a manifest/API contract test

The offscreen regression is a class of bug, not an incident. Add
`extension/test/manifest.test.js` (JavaScriptCore, no new toolchain) that:

- parses `manifest.json` and every static ruleset path;
- scans the shipped JavaScript for `chrome.<namespace>` uses and maps each one
  to the permission it requires, at minimum:
  `offscreen → offscreen`, `storage → storage`, `scripting → scripting`,
  `alarms → alarms`, `declarativeNetRequest → declarativeNetRequest`,
  `runtime.sendNativeMessage → nativeMessaging`;
- fails when a used namespace's permission is absent, and when a declared
  permission has no use (warn, not fail, for `nativeMessaging` because its use
  is conditional);
- asserts `background.type === "module"`, popup/options/service worker files
  exist, and `web_accessible_resources` lists only `blocked.html`;
- asserts the manifest version equals the extension entry in `versions.json`.

The last assertion also removes a real drift: `docs/EXTENSION_STORE.md:1,5,155`
still names 1.5.2 and its zip, while the manifest is 1.5.3.

**Status:** `manifest.json` now declares `offscreen`, and
`test/manifest.test.js` runs 38 checks in `test/run.sh`. Removing the permission
again makes the suite fail (verified), so the regression cannot return silently.
The failure-mode policy in 1.1 (what an unavailable engine should do) is still
open.

**Effort:** S.

### 1.3 Smoke-test the store package, not just the repository

`test.sh` loads the **repository** unpacked through `prepare_unpacked.py`, and
`check_package.py` checks files and hashes but never loads the zip
(`check_package.py:32-41`). The store build is different: `package.sh` strips
`key`, `_comment_*` and test files. A stripped manifest that loads fine in the
repo can still fail to register the worker or rulesets in the store build.

**Change:** make the browser DNR/scanner harness accept a staged directory, and
add one CI step that extracts the output of `package.sh` into a temp directory,
loads it in Chrome for Testing, and asserts: the worker starts; baseline
rulesets are enabled; `chrome.offscreen` exists; a listed host is blocked; the
block page renders; the repository ID is absent from the manifest.

A check run against today's artifacts makes the gap concrete: the existing
`dist/hisn-extension-1.5.3.zip` fails the current `check_package.py` with
“missing bundled visual resource: visual/wasm/tfjs-backend-wasm.wasm”. A fresh
package built after the permission fix passes and is 6.3 MB (it now includes the
three WASM kernels). The zip in `dist/` is therefore not the artifact the
current checker describes, which is exactly why the store build needs its own
smoke test.

**Effort:** M.

---

## 2. Publish (P1, next)

The extension is the only Hisn surface with no distribution route beyond Load
unpacked, and it is the cheapest surface to distribute. `docs/EXTENSION_STORE.md` is a good record but
it is a **1.5.2 record**: version, zip name and hash are stale, and the
permission table lacks `offscreen`. Shipping is not a code change, but it is
the highest-impact improvement in this plan.

Sequence:

1. Fix 1.1 and 1.3; regenerate the store record from the manifest (version, zip
   SHA-256, permission list) instead of hand-copying it.
2. Produce the store assets: 1280×800 screenshots of the popup, overview,
   rules page, blocked page and visual settings; a 440×280 promo tile; both
   languages. The current design captures are 480/560 px wide
   (`design/extension-ui-2026-11-04/`), which is not a store screenshot size.
3. Upload **unlisted** first; fill the single-purpose, permission and data-use
   answers already written in the store record; add the `offscreen`
   justification (“run the opted-in local model in a hidden document”).
4. Expect reviewer questions on `<all_urls>`, page text, `nativeMessaging` and
   the six-hourly list download. The answers exist; prepare them as a short
   reviewer note with concrete steps (the record already has reviewer steps).
5. After the store ID is assigned, wire it into
   `macos/Hisn/NativeMessagingInstaller.swift` and the profile/policy
   generators, and test a managed force-install on a standard user.
6. Keep the previous zip. An update that regresses blocking is worse than a
   late release.

**Acceptance:** an unlisted listing exists; the installed store build blocks a
listed domain and forces SafeSearch; native messaging works with the store ID;
managed force-install survives a disable attempt; the packaged zip equals the
tested sources.

**Effort:** M (mostly review and assets).

---

## 3. Make the extension cheap on every page (P1, measure first)

The extension is invisible only if it costs nothing. Today it is not measured;
the code suggests three costs worth instrumenting before changing anything:

### 3.1 Page-text scanning is full-page and always on

`content/scan.js` runs in every frame at `document_start`, observes every
mutation, and on each settled pass:

- reads `document.body.innerText` — a full layout read (`:298`);
- walks the entire DOM to find open and closed shadow roots, up to 20,000
  elements, calling `chrome.dom.openOrClosedShadowRoot` on each (`:329-346`);
- runs six `querySelectorAll` meta queries, plus headings and up to 100
  image/ARIA labels (`:267-324`);
- sends up to 200,000 characters to the worker, which scores them in ~22 ms at
  the measured rate.

The debounce has a 5-second ceiling (`:76`, `:153-183`), so a mutating page
repeats that work every five seconds. A background tab is throttled by the
browser but still runs it; a hidden iframe runs it too. The roadmap already
asks for incremental checks (`docs/HISN_IMPROVEMENT_ROADMAP.md`, row 4), so this
is unfinished intended work, not a new idea.

**Change, in order of value:**

1. **Visibility gating.** Do not collect or send when `document.hidden`; on
   `visibilitychange` to visible, re-arm once. Skip frames whose own viewport
   is zero-sized. Acceptance: background tabs produce zero scans per minute;
   the first visible pass after a tab returns still catches a block.
2. **Stop the scanner when the layer is off.** The bootstrap listens to
   `storage.onChanged` and calls `recheck()` (`:580-583`) but never stops. When
   `inspectText`, `failClosed` and the lock all say off, disconnect the
   observer, clear the href interval and stop scheduling; on re-enable, either
   resume in place or re-inject with a version marker that can restart. Today
   the page pays for a layer that returns `block: false` forever.
3. **Incremental collection.** Cache the per-zone text and invalidate only the
   zones a mutation can touch; re-walk shadow roots on a slower cadence (or
   only after new host elements appear) instead of every pass. Keep the
   signature equality rule; a changed node must still produce a new signature.
4. **A per-page budget.** On very large DOMs, scale the settle time with text
   size or cap scans per minute, and prefer `requestIdleCallback` where it is
   available. This is a safety valve, not the main optimisation.
5. **Clear the href interval on block.** `block()` (`:381-400`) stops the
   scheduler and observer but not the 1-second `setInterval` created at
   `:527`; it keeps waking for the life of the page.

**Acceptance:** a benchmark fixture (10k nodes, 200k characters, 20 frames)
records collection, message and score time separately; p50/p95 are reported
before and after; no `scan.test.js` or browser-harness block regresses; a
background tab costs zero scans; a disabled text layer costs zero scans.

### 3.2 The heartbeat re-proves and rewrites an unchanged policy

The worker polls the native app every 30 seconds (`background.js:47`), and every
poll applies the rules (`worker/native.js:133`). `applyRules` compares the
installed policy with the desired one (`worker/rules.js:82-84`) but still calls
`updateDynamicRules` with the same rules (`:115-118`), writes the receipt twice
(`:68`, `:153`), and re-reads rules, rulesets and scanner registrations. For a
browser-only install, each heartbeat also launches the native-host process, fails,
and applies the rules again on the loss path (`worker/native.js:162`).

**Change:**

1. When the readback already equals the desired policy, skip
   `updateDynamicRules` and keep the readback as the proof. The receipt remains
   honest because it is still derived from what Chrome holds.
2. Only write the receipt when it changes state (applied/pending/failed) or the
   worker session changed, not on every successful no-op.
3. Make the heartbeat cadence adaptive or measure it first: 30 seconds while a
   lock or a connected app makes detection matter; a slower idle cadence when no
   app has ever answered. Do not change the Mac guard's expectations without
   reading `docs/CHROME_ENFORCEMENT.md` and the app side first.

**Acceptance:** an idle minute with an unchanged policy performs zero DNR
writes and only readback calls; a policy change still applies within one
heartbeat; the existing runtime suite's readback/rollback cases still pass.

### 3.3 Do not optimise the ruleset blindly

The 9.65 MB static ruleset is large, but the design is deliberate: 474 rules
carrying 1,000 domains each is how 473k domains fit under Chrome's 30,000-rule
guaranteed budget. Chrome allows 100 static rulesets and 50 enabled; the
extension uses 10. The rule count is not the problem. The costs that might be
are install-time parse and the daily download.

Before changing the format, measure cold install time and the bytes actually
transferred on a daily update (the server may already gzip the JSON). Only if
those numbers are material should the extension move to a compressed artifact or
the packed domain format the Mac already uses. A binary format would add a
decompressor and a new failure path to the one artifact that must never fail
open.

---

## 4. Close coverage gaps (P1/P2)

### 4.1 Private windows are unprotected and unmentioned

`manifest.json:103` uses `incognito: "spanning"`, which still leaves the
extension **off** in private windows until the user turns on “Allow in
Incognito”. `worker/native.js:39-67` reads that answer with
`chrome.extension.isAllowedIncognitoAccess`, but only to send it to the Mac app
(`:74-78`). No popup or options code mentions it. `extension/MANIFEST.md:34-41`
says “the popup detects and asks for” the permission; it does not, and
`test/browser/popup.html` asserts that it does not.

**Change:** in the popup and overview, when the extension is not allowed in
private windows, show one warning row: private windows are not covered; open
`chrome://extensions/?id=<id>` to allow it. Do not add a fake mode or a toggle
the extension cannot enforce. This is the same honesty standard as the app row.

**Acceptance:** a browser fixture with the mocked API returning false shows the
notice and the link; true/undefined hides it; the docs and the existing popup
assertion are updated together.

**Effort:** M.

### 4.2 SafeSearch stops at five engines

The rules cover Google, Bing, DuckDuckGo, Yahoo and Brave
(`rules/safesearch.json`), and Yandex is blocked whole
(`rules/web_protection.json`). Other engines people use — Ecosia, Startpage,
Qwant, Baidu, Naver, Seznam — are not forced into family mode. Some default to
a safe mode; many do not.

**Change:** for each candidate, verify the provider's current parameter in the
browser (not from memory), add a rewrite rule at the same priority as the
existing ones, add a positive and a negative case to `test/browser/dnr.sh`
(including an “off” value that must be replaced and a lookalike host that must
not match), and record the verification date. Do not add a rule that cannot be
tested.

**Effort:** S per engine, M for a batch.

### 4.3 The URL keyword layer is English-centred

The 116 keyword rules are Latin/English plus Arabic transliterations. The scorer
already carries German, Spanish, French, Italian and Portuguese vocabulary, but
a path like `/pornos/` or a query with a non-English explicit term is left to the
page-text layer, which needs the page to render. The generator
(`blocklist/build.py` and the term table) is the right place: reviewed terms,
explicit boundaries, and the same reference-site exclusions the keyword rules
already carry.

**Change:** generate a small, reviewed set of non-English path/query terms and
add fixture cases per language. Gate: no benign fixture from the same language
blocks; every new rule has an excluded-domain rail. Do not transliterate whole
dictionaries or use substring matching.

**Effort:** M.

### 4.4 Feed adapters beyond X

Only X/Twitter gets per-post handling (`content/feed.js:14-16`). Reddit,
Instagram web, Telegram web and YouTube comments still get whole-page scoring,
which either blocks the service or misses the item. The roadmap deliberately
asks for real-DOM tests before adding adapters.

**Change:** one adapter at a time, starting with Reddit (its NSFW flag is
explicit and its post container is identifiable). Acceptance: one flagged post hides,
the rest of the feed stays readable, recycled nodes and late captions re-check,
and no post text or URL is retained. This is P2 because each adapter is a
maintenance liability and the page-level layer still catches the worst cases.

**Effort:** L per site.

### 4.5 State the residual gaps rather than inventing coverage

PDFs in Chrome's built-in viewer, canvas/WebGL content, closed shadow roots in
browsers without `chrome.dom`, DRM video, and encrypted traffic are not
inspectable by this extension. They belong in the “what this cannot do” copy,
not in a workaround that looks like coverage. OCR for text-in-images is a
possible future experiment (a few MB of WASM), but it is not worth adding before
the real-world benchmark exists.

---

## 5. Build a quality loop (P1/P2)

### 5.1 The corpus is a regression set, not a measurement

`extension/eval/corpus/` contains 50 cases (22 adult, 28 benign) across a
handful of languages. All pass, which is good; the repository itself calls it “a
small labelled regression set, not a measurement of real-world recall or
false-positive rates” (`docs/EXTENSION_1.1.12.md:67-70`). The end-goal plan asks
for ≥500 labelled cases per language, with ≤2% misses and ≤1% benign blocks.

**Change:** build two layers:

- a committed synthetic regression set, extended first with the negatives that
  matter (medical, recovery, religious, news, ordinary short posts) in Arabic,
  English and German;
- a private, licensed/consented real-page benchmark kept outside the repository,
  scored by the same evaluator, with per-language misses and false positives
  reported on every vocabulary change. Do not claim accuracy from the committed
  set.

**Acceptance:** the private benchmark exists and is re-run on each scorer
change; the committed benign gate still blocks nothing; the report separates
misses from false positives by language and page type.

**Effort:** L (curation, not code).

### 5.2 False positives have nowhere to go

The block page lets a person tap a word or the site
(`blocked.js:91-163`), but the result is a **local** exemption
(`worker/inspection.js:180-247`) — no server, by design, and no path back to the
list maintainers. With 473k domains and a 5.8k vocabulary, the maintainer's only
feedback loop is their own testing.

**Change:** an explicit, one-time export from Settings: a small JSON file listing
the words the person disowned and the sites they reported, with no timestamps,
no URLs, and a clear sentence that the file contains the sites they chose to
report and that sharing is their decision. No automatic upload, no new
permission, no network. A GitHub issue template can accept the file. If the
privacy review rejects host names, export only disowned terms.

**Acceptance:** the export exists only on a user action; the file is bounded and
contains only the named fields; no code path uploads it; the threat-model note
is updated.

**Effort:** M.

### 5.3 One block slot, not one per tab

`rememberBlock` writes one `lastTextBlock` record in session storage
(`worker/inspection.js:168-178`) and the block page reads it by recency
(`blocked.js:100-108`). Two tabs blocking at once overwrite each other; the
block page can show another tab's host and words. It is rare, but it is a
privacy-adjacent correctness bug and it is easy to fix: key the record by the
tab id the worker already receives (`sender.tab.id`), and have the block page ask
the worker for its own tab's record rather than reading a global slot.

**Acceptance:** two simultaneous blocks show their own terms; a block page with
no record shows no words and no site action; session storage is still cleared on
browser close.

**Effort:** S.

---

## 6. UI, accessibility and language (P1/P2)

The UI is in good shape — visible focus on most controls, dark mode, RTL,
reduced motion, `prefers-contrast`, text content built with `textContent`, no
injection path found. The remaining items are specific and cheap:

| # | Problem | Evidence | Change | Effort |
| --- | --- | --- | --- | --- |
| 6.1 | The 10.5 px popup privacy note is 4.06:1; placeholders are 3.23:1 (light) / 4.26:1 (dark), below 4.5:1 | `ui.css:447`, `ui.css:109`; tokens at `ui.css:19-43` | Use solid `var(--muted)` (5.2:1) and raise the note to 11.5 px | S |
| 6.2 | The text-sensitivity slider has no focus indicator | `ui.css:102` is overridden by `ui.css:110` `outline:none` | Add `input[type=range]:focus-visible` outline | S |
| 6.3 | Switching dashboard views moves no focus and announces nothing | `options.js:43-59`; views have no `tabindex="-1"` | Focus the view's `<h1>` and add a visually hidden `role="status"` | M |
| 6.4 | Disabled locked/app-managed fields do not reference the reason that explains them | `options.js:477-478`, `options.html:78,86,119`; the one correct pattern is `#blockShortVideo` | Point `aria-describedby` at the visible reason; keep the reason discoverable | M |
| 6.5 | The connect-app button's fixed `aria-label` says “Check connection” while the action is “Connect”; at ≤400 px the visible label disagrees with the name | `popup.html:40`, `popup.js:90`, `ui.css:627` | Name the button from the visible span and update both together | S |
| 6.6 | The custom-terms textarea lacks `dir="ltr"` in Arabic, unlike the two site lists | `options.html:94,101,146` | Add `dir="ltr"` | S |
| 6.7 | Discard-all, removing a correction and dismissing a dispute have no confirm/undo | `options.js:233-240, 264-288, 423-429` | Confirm discard-all when multiple sections are dirty; undo for chip removal/dismiss | M |
| 6.8 | A worker that never answers leaves the dashboard disabled with no explanation or retry | `options.js:450-460`, `:766-767` | Top-level “Protection status unavailable” notice with “Try again” | S |
| 6.9 | `content/inspect.css` is dead: it references `inspect.js`, `shadow.js` and `test/css-parity.test.js`, none of which exist; `content/visual-gate.css` is the live file | `content/inspect.css:1-28`; no source reference anywhere | Delete it (or restore the parity test if the intent was real) | S |
| 6.10 | The popup fixes its root at 480 px; every narrow test injects `width:auto`, so the shipped rule is unverified under a clamped window or zoom | `ui.css:374-376`, `:618-646`; `test/browser/popup.html` strips the class | Add a real `@media(max-width:480px)` reset and test with no injection | S |

### 6.11 A first-run card

There is no onboarding. A new install opens the popup on “Checking status…” and
the dashboard is a dense five-section page. Add one dismissible card in
Overview that answers, in the user's language:

- what is running now (domains, SafeSearch, text) and what is not (visual,
  private windows, another browser);
- how to block the current site;
- that the Mac app is optional and what it adds (timed lock, app-owned lists);
- one link to the block-list update state.

No new permission, one storage flag, and a “Don't show again” control. This is
the cheapest activation improvement available.

**Effort:** M.

### 6.12 Language: finish the manifest, then the tables

The page copy is fully translated (345 keys in `lib/locale/en.js` and `ar.js`),
but:

- manifest strings are not localized: no `_locales/`, no `default_locale`, no
  `__MSG__` (`manifest.json:3-4`), so Chrome's toolbar tooltip, extensions page,
  permission prompts and store listing stay English;
- `initLanguage()` awaits `chrome.storage.local.get` before calling
  `translatePage` (`lib/i18n.js:63-71, 83-85`), so an Arabic user sees an
  English/LTR first paint;
- `<html lang="en">` is hardcoded in all three pages (`popup.html:2` etc.);
- `t()` has no plural forms (`lib/i18n.js:25-28`), so “Unsaved sections: 1” is
  the rendered string.

**Change, in order:** add `_locales/{en,ar}/messages.json` and `__MSG_` manifest
strings; set `lang`/`dir` synchronously before the storage await (a tiny
non-module preload, or a cached last value); localize the blocked-page
fallbacks and the popup title; add plural keys via `Intl.PluralRules`. The five
scored languages (de/es/fr/it/pt) are a later translation project, not a code
project — do not machine-translate a safety product's UI without review.

**Effort:** M for en/ar manifest + first paint; L for more languages.

---

## 7. Reach, after the above (P2)

- **Firefox:** a one-week feasibility spike, not a commitment. The blockers are
  real: Firefox MV3 uses an event page rather than a Chromium service worker,
  `offscreen` does not exist (the visual engine would need a different host or
  would ship as text/network only), and `declarativeNetRequest` support and the
  `extensionPath` redirect need testing. Decide with a written capability matrix
  and a text-only prototype; do not port the visual layer first.
- **Safari:** the iPhone/iPad app already ships a Safari web extension built from
  the shared scanner; a macOS Safari extension is a separate product because
  Safari does not use DNR. Out of scope here.
- **Enterprise:** the force-install and policy generators exist; test them after
  the store ID exists (workstream 2). This is the only supported way to make
  the extension non-removable for a standard user, and the docs should keep
  saying so.

---

## 8. Sequence and gates

| Phase | Work | Gate before moving on | Effort |
| --- | --- | --- | --- |
| 0 (days) | 1.1 offscreen + failure mode; 1.2 manifest test; 1.3 store-package smoke test | Visual layer ready in a real Chromium; no media hidden by a missing engine; manifest test fails on the old manifest and passes on the new one | S–M |
| 1 (1–2 weeks) | 2 store publish unlisted; 4.1 private-window notice; 6.1, 6.2, 6.4, 6.5, 6.6, 6.9, 6.10; 5.3 per-tab block slot | Unlisted listing live; store build loads and enforces; private-window notice tested; axe serious/critical = 0 on popup/options/blocked | M |
| 2 (2–4 weeks) | 3.1 scan benchmark and incremental work; 3.2 no-op apply and adaptive heartbeat; 5.1 corpus expansion; 5.2 report export | p95 scan budget set and met; zero DNR writes on an unchanged idle minute; private benchmark re-runs; export contains only named fields | M–L |
| 3 (quarter) | 4.2 more search engines; 4.3 non-English URL terms; 6.11 onboarding; 6.12 `_locales`; 4.4 one feed adapter | Each new rule has a positive and a negative browser case; benign corpus unchanged; one adapter meets its hide-one-post gate | M–L |
| 4 (decision) | 7 Firefox spike; translation project; ruleset delivery only if measured | Written go/no-go with a capability matrix; no release claim without a real browser | M–L |

Dependencies: 1.1 before any visual claim; 1.3 before the store upload; 3.1's
benchmark before any scan rewrite; 5.1 before any accuracy number is repeated in
the README or store copy.

---

## 9. Scoreboard

| Measure | Today | Target after this plan |
| --- | --- | --- |
| Visual layer availability, Chromium | Was unavailable (missing permission); permission restored, full path still to verify | `ready` on opt-in; no hidden-media outage when the engine is unavailable |
| Store listing | None | Unlisted, then public; managed force-install verified |
| Manifest/API contract | Was untested; a 38-check manifest suite now runs in `test/run.sh` | Test fails on a declared/used mismatch |
| Idle worker, unchanged policy | Rewrites rules and receipt every 30 s | Zero writes; one readback; adaptive cadence measured |
| Page-text scan cost | Unmeasured; full DOM + shadow walk every ≤5 s | p50/p95 measured on a fixture; background tabs zero; no scan when the layer is off |
| Private windows | Unmentioned | One honest notice with a link to the browser setting |
| Labelled cases | 50 committed | ≥500 per language in a private benchmark; committed benign gate still zero |
| False-positive feedback | Local only | Explicit, bounded, user-initiated export; no automatic upload |
| UI accessibility | Several specific failures | Axe 0 serious/critical; contrast ≥4.5:1; keyboard-only pass; focus moves on view change |
| Manifest language | English only | `_locales` en/ar; correct Arabic first paint; plural forms |
| Dead shipped code | `content/inspect.css` | Removed or restored with its test |

---

## 10. Deliberately out of scope

- **A browser-only tamper-proof lock.** The extension can be disabled, its
  storage edited, and its profile copied. Only a managed policy makes it
  non-removable for a standard user. Do not add a fake lock; the Mac app owns
  the clock.
- **Remote classification, analytics or a server.** The product's privacy
  promise is the reason it is credible; every improvement above stays local.
- **OCR, DRM video, canvas/WebGL capture, PDF text.** Possible later
  experiments; not coverage today.
- **A universal content classifier.** Text and images are heuristics on top of
  the network layers; the docs and store copy should keep saying so.
- **Rewriting the ruleset format** before install time and update bytes are
  measured.

## 11. Decisions the owner has to make

1. Is the visual layer meant to run in Chromium? If yes, 1.1 is a bug fix. If
   no, remove it everywhere rather than shipping a dead switch.
2. Is the 30-second native heartbeat a product requirement from the Mac guard,
   or can it be adaptive when no app has ever answered?
3. Which languages get UI tables next, and who reviews the translations?
4. Who owns the private, licensed real-page benchmark, and where is it kept?
5. Is a false-positive export acceptable under the threat model, and may it
   include host names or only terms?
6. Store account, listing owner and timeline; the unlisted upload is the gate
   for every reach improvement.

---

## Appendix: the smallest patch for the P0

```diff
--- a/extension/manifest.json
+++ b/extension/manifest.json
@@
   "permissions": [
     "declarativeNetRequest",
     "storage",
     "alarms",
     "nativeMessaging",
-    "scripting"
+    "scripting",
+    "offscreen"
   ],
```

Then add the `offscreen` row to the permission table in
`docs/EXTENSION_STORE.md`, replace the “unused and has been removed” sentence at
`:67`, and add the manifest/API test from 1.2 so the next deletion fails a
build instead of a user's page.
