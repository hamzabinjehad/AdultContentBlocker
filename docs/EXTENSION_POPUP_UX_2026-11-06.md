# Extension 1.5.3: the popup's status and its embedded settings

6 November 2026. A UX and regression record for the Chromium extension popup.
No permission, host permission, storage field, network path, scoring rule or
lock rule changed. The desktop settings page keeps its own layout.

## Why

A 480×600 popup in settings mode was reported as unusable: the five section
tabs were clipped after "Lock", a horizontal scrollbar sat under them, a
language row and a pinned footer took the height, and a blue focus ring was
drawn around a content area that was already cut off. The same pass found a
separate rendering bug on the status screen: the Hisn-app row stayed on
"Checking…", the lock strip was an empty amber bar, and notices and Details
never appeared.

Both defects came from the popup trying to be the full-page dashboard, and
from a reference that was deleted with the private-window row.

## What was wrong

### 1. A reference error stopped the status render

`popup.js` painted the hero and the three enforcement layers, then referenced
`badge` and `appLevel` — identifiers that no longer existed after 1.5.1 removed
the private-window row and took `const badge = …` with it. The rejection
happened inside the `getState` promise, so:

- the Hisn-app row never left its opening "Checking…";
- the lock block kept its placeholder `&nbsp;`, which laid out as an empty
  amber strip;
- `notices`, `details` and the empty state of the quick-block row were never
  rendered;
- `connectionAction` kept its opening label.

The browser popup suite already asserted the lock and app text, so it had been
failing since the 1.5.1 change. 1.5.2's recorded verification listed the
JavaScript suites but not the popup browser suite. The fix restores the 1.4.0
paint of the app row (without the removed private row):

```js
const appLevel = state && connection.fresh ? "active" : state?.appPresent ? "partial" : "off";
document.getElementById("appIndicator").className = `layer-indicator ${appLevel}`;
const badge = document.getElementById("connectionBadge");
badge.className = `layer-state ${appLevel}`;
```

The regression is now covered by a check that the popup window records no
`error` or `unhandledrejection` in the states the suite exercises.

### 2. The embedded settings page spent the popup on dashboard furniture

With the editor open, the popup spent its 600 px on:

| Layer | Approximate height |
| --- | --- |
| Popup header (brand and hidden language switch) | 44 px including the gap |
| Back / Open full page bar plus gap | 48 px |
| Embedded sidebar (tabs, then a language label and select) | 130 px |
| Pinned full-page footer (version, privacy, support) | about 65 px |
| Main and page padding | about 36 px |

The actual settings page was left with roughly 270 px and a native scrollbar.
The five tabs needed more width than a 452 px frame had with icons and 44 px
targets, so they became a horizontal scroll area whose last item — Settings —
was off-screen.

## What changed

### The popup while editing

- The popup header is hidden in editing mode; it is not a second brand.
- One 38 px bar carries **Back** (chevron), **العربية / English**, and
  **Open full page** (arrow). The language switch stays available because the
  embedded page's own row is gone.
- The frame gets the remaining 456×534.
- Focus still lands on Back when settings open and returns to Manage
  protection when they close.

### The embedded page

- The sidebar is a single row of five equal tabs: no icons, 12 px labels,
  42 px minimum height, labels allowed to wrap to two lines rather than clip.
- No language row, no pinned footer, no shortcut tiles that repeat the tab
  row. All three remain on the full settings page.
- Compact page padding and a compact status card.
- The scroll region reserves no gutter and uses a thin scrollbar.
- `.page-view:focus` and `:focus-visible` draw no ring; the page is a scroll
  region, not a control, and Chrome's focusable-scroller ring was the blue
  rectangle in the report.
- Below 360 px the tabs wrap onto two rows (3 + 2) rather than overflow.

### Copy

- The last tab was **Settings** opening a page headed **Content options**.
  It is now **Content** / **المحتوى**, so a tab inside settings no longer
  points at "Settings" and names what it edits.

## Measured result

The before values are from the 1.5.2 checkout rendered by the same preview
harness at 480×600; the after values are asserted by `embedded.html` at
452×530 (the frame size inside the popup).

| Measure | Before | After |
| --- | --- | --- |
| Popup status: app row | frozen "Checking…" | `Optional`, `App connected` or `App offline` with a tinted icon |
| Popup status: lock row | empty amber strip | hidden when no lock; deadline and detail when locked |
| Popup status: notices / Details | never painted | painted |
| Editor frame | approximately 456×470 | 456×534 (asserted) |
| Embedded sidebar | 130 px (tabs + language) | 55 px (one tab row) |
| Embedded footer | about 65 px, pinned | hidden in the popup |
| Embedded page area | about 270 px | 461 px (asserted) |
| Five tabs | clipped, horizontal scroll | one row, `scrollWidth === clientWidth` |
| Scroll-region focus ring | clipped blue outline | none |

## Files

| File | Change |
| --- | --- |
| `extension/popup.js` | Restores the app-row paint; adds the editor language switch |
| `extension/popup.html` | Compact editor bar with Back, language and full-page actions |
| `extension/ui.css` | Popup editor and embedded-settings layout |
| `extension/lib/icons.js` | `chevron-left` |
| `extension/options.html`, `lib/locale/en.js`, `lib/locale/ar.js` | Content tab label |
| `extension/test/browser/popup.html` | No-error state check, app-row paint, editor chrome, focus and language |
| `extension/test/browser/embedded.html` | New focused harness for the 452×530 embedded surface |
| `test.sh` | Runs the new harness in the browser suite |
| `design/extension-popup-2026-11-06/` | Before/after captures and README |

## Verification

- `extension/test/run.sh` — all JavaScript suites pass.
- `extension/test/browser/run.sh popup.html` — PASS. New checks: no script
  errors or unhandled rejections across the suite's states; the app row paints
  its icon and word together; the editor hides the popup header, offers a
  456×534 frame and puts focus on Back; Open full page escapes when no draft is
  pending; Back restores the header and returns focus; the editor language
  switch mirrors the popup.
- `extension/test/browser/run.sh embedded.html` — PASS. New checks: no
  horizontal overflow in English or Arabic; all five tabs inside the row at
  ≥ 40 px height with no clipped labels; footer, language row and shortcut
  tiles hidden; 461 px page area; Repair visible without scrolling; no focus
  ring; all five sections open; 360 px fallback wraps.
- `extension/test/browser/run.sh settings.html` — the desktop page suite is
  unchanged. It remains timing-sensitive in this harness on a loaded machine,
  as the earlier design record noted; the same file-import scenarios fail on
  some runs with the pre-change files restored too. The failures are recorded,
  not hidden.
- `python3 script/versions.py --check` — the 1.5.3 release matrix agrees.

## Limits

- The captures are isolated previews with a mocked extension bridge, not a real
  toolbar popup or a personal profile. Chrome popup sizing, OS overlay
  scrollbars, every browser theme, every zoom level and RTL at 200 px were not
  exercised.
- The embedded page remains a scrollable dashboard. It now gets the popup's
  height and fits its navigation, but it is not a purpose-built compact UI.
- The popup status still scrolls when the page-language row and a notice are
  both present; the primary action and every layer stay above the action bar.
- No user study, screen-reader audit, physical-device review or clean-profile
  acceptance is claimed.

## Reproduction

The preview harness used for the captures is a local QA page, not a shipped
file. The repeatable checks are:

```bash
extension/test/run.sh
extension/test/browser/run.sh popup.html
extension/test/browser/run.sh embedded.html
extension/test/browser/run.sh settings.html
```

The capture page served `extension/` over loopback with a mocked `chrome`
bridge, loaded `popup.html` in a 480×600 frame, opened settings, and captured
the result through the DevTools protocol at 2×. No browsing history or personal
data was used; the only host in the fixture is `example.test`.
