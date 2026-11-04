# Hisn Mac 1.1.11 — a state-first window, and a way to ask about a site

Released 4 November 2026 as a development candidate. Unsigned and unnotarized;
no Developer ID package, notarized build, physical-Mac acceptance or VoiceOver
audit is claimed.

## Why

1.1.10 removed repeated titles and put the main action above the evidence.
Using the five pages afterwards showed four remaining problems:

- **The layer chips and the expanded detail said the same thing twice.** The
  Overview listed three layer names as chips, then repeated the same names,
  states and prose in an expanded disclosure.
- **The primary action did not follow the state.** With two of three layers
  running it still said “Start a lock…”, while a lock was the less useful next
  step; with nothing running the same button said “Continue setup”. During the
  first seconds of a launch it became a disabled “Checking…” button.
- **Setup led with prose, not progress.** The readiness card carried several
  paragraphs, a command and a caveat before a thin progress bar. The step
  groups had chevrons but no per-group progress.
- **There was no way to ask the lists a question.** A person editing an
  allowlist could not ask “would this site be blocked, and why?” without
  opening it. Screen Zen's *Test a website* and similar tools in the category
  solve exactly that.

## Design research

The Screen Zen 2.0.0 (build 63) window on this Mac was opened read-only; no
setting, rule, account or stored value was changed. The capture and evidence
stay in temporary storage and are not redistributed. The observation was a
design vocabulary — a calm card stack, big page titles, rows with a leading
icon and one action, state on the right, and a website tester — not a benchmark
of its blocking.

Public product pages and interface reviews of **Opal**, **one sec**, **Jomo**
and **Clearspace** were also read for category patterns: one decision per
screen, a progress or commitment hero, strict modes with a visible consequence,
and a short friction step before a weakening action.

No competitor code, assets, visual identity, copy or data model was copied into
Hisn. The checker, progress ring, cards and strings are Hisn's own.

## What changed

| Page | Before | After |
| --- | --- | --- |
| Overview | chips, then a disclosure repeating the same layers | one status hero with a tile per layer: name, state word, colour |
| Overview | primary action sometimes started a lock while a layer was missing | one state-aware action: **Finish setup**, **Review setup**, **Start a lock…** or **Manage lock**; the lock remains the secondary action when it is valid |
| Overview | warnings sat below the cards | warnings sit directly under the status they qualify |
| Overview | a full card for the pause; no site checker | one tools card: **Take a pause…** and **Check a website** |
| Setup | readiness prose, command and caveat, then a thin bar | a progress ring with the count, compact readiness, a next-step line |
| Setup | stage disclosure labels had no progress | each stage shows `done/total` and the next stage carries a **Next** badge |
| Blocking Rules | two list editors in one long section | one card per list, a real empty state, and a count from the same parser Save uses |
| Blocking Rules | no way to test an address | a **Check a website** row, also in the window toolbar, the menu bar and ⌘K |
| Lock | checkbox-style mode toggles, plain end-date line | trailing switches, an end-date band, and elapsed progress on a running lock |
| Settings | card headings without icons | one icon per card, matching the rows elsewhere |
| All pages | — | a read-only site checker with the deciding rule named |

### The site checker

`SiteCheckSheet` answers in the same rule order as the filter:

1. built-in search/viewer rules (cannot be allowed);
2. a hand-typed block (outranks an allowance);
3. strict mode (only the allowlist is reachable);
4. a hand-typed allowance (outranks the lists);
5. the published list, then the hostname keyword layer.

It reads the installed generation from the shared container when available and
falls back to the **signed core seed bundled with the app** on a fresh install
or an ad-hoc build. The result names its source and version; the UI says
plainly when the full list has not been installed. An unreadable list is
reported as `listUnavailable`, never turned into “not blocked”. The sheet
checks the address only: page text, images, video and each browser profile's
extension state are separate layers and the footer says so.

The checker is read-only. **Add to Always block** uses the existing
`SiteLists.addBlock` path, so it still works during a lock and cannot loosen
one.

## Files

| File | Change |
| --- | --- |
| `macos/Hisn/ContentView.swift` | status hero and layer tiles, state-aware action, tools card, Setup ring and stage counts, list cards, lock end-date band and progress, settings icons, site-check sheet |
| `macos/Hisn/SiteChecker.swift` | pure rule order, installed/bundled list loading, async check service |
| `macos/Hisn/BlocklistStore.swift` | read-only `isOnPublishedList` and `matchesKeyword` queries |
| `macos/Hisn/FilterSetupControl.swift` | compact readiness card; the build-block paragraph is shown once |
| `macos/Hisn/StatusMenu.swift`, `macos/Hisn/HisnApp.swift` | **Check a website…** menu item (⌘K) and a pending-sheet request |
| `macos/Hisn/Localizable.xcstrings` | 44 new strings with Arabic; 645 total |
| `macos/HisnTests/SiteCheckerTests.swift`, `BlocklistStoreTests.swift` | rule order, source reporting, published-list and keyword queries |
| `macos/generate_xcodeproj.py`, `macos/Hisn.xcodeproj` | 1.1.11, build 22; seed files are also app resources for the checker |
| `design/macos-ui-1.1.11/` | the after captures and their README |

## Verification

- `./test.sh macos` — **541 tests, 0 failures**; the localization export reads
  **645 strings, all with Arabic**.
- The five pages and the checker were captured from a real development build
  running on the owner's Mac, dark appearance, 760 pt window width, English.
  The 1.1.10 captures in `design/macos-ui-1.1.10/` are the before state.
- The checker was exercised with an allowed example, a blocked domain, and an
  address on neither list. The bundled-seed path was used because this ad-hoc
  build has no app-group container; the installed-list path is the preferred
  path on a signed build.
- No rule, lock, filter, browser-bridge or protection-layer behaviour changed.
  The checker adds no enforcement; it only reads the same lists.

## Limits

- The captures are one window width (760 pt), dark mode, English. Wide windows,
  light mode, Arabic RTL layout, VoiceOver order and keyboard traversal are not
  measured by this pass.
- The bundled core list is smaller than the published full list. The result
  says which one answered, but a domain only in the full list can be missed
  until the container copy is installed.
- The checker reads the app's mirrored lists, not the filter's root-owned
  authority copy at runtime. If those ever disagree, the filter remains the
  enforcing side; the sheet is an explanation, not proof that a request was
  blocked.
- Opening the checker may load the installed list into the app process on
  first use. It reuses `BlocklistStore.shared`, so this is the same copy the
  list updater already holds, not a second one; a full-list install still
  costs a one-time parse of several million hashes.
- No layout snapshot or accessibility-tree test exists. The previous pass's
  recommendation for a small UI-test target remains open.
- The Screen Zen observation is one window and one settings-gated state, not a
  competitive benchmark.
