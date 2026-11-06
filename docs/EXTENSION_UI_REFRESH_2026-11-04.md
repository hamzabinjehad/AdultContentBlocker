# Extension UI refresh: REA design input, 4 November 2026

This is a **design-research record**, not a security assessment, a quality
ranking or a claim about another product's runtime behaviour. It records what
REA could observe statically in ScreenZen, what public product descriptions
showed, what was implemented in the Hisn Chromium extension, and what remains
unknown.

## Tool and method

- [REA](https://github.com/morluto/rea) **6.5.0** (`rea-agents`) was already
  present in the local npm npx cache from the project's earlier Android/iOS
  audits. It ran from that cache; nothing was installed, no MCP server was
  registered, and no editor configuration was changed.
- Commands used:
  - `rea inspect-artifact /Applications/ScreenZen.app --format json`
  - `rea analyze-javascript-application /Applications/ScreenZen.app/Contents/Resources/ScreenZenFirefoxExtension --format json`
  - `rea capture-native-ui-scenario /Applications/ScreenZen.app --pid … --window-id … --steps '[{"kind":"wait","milliseconds":300}]'` — attempted, but the native macOS provider reported `capability_unavailable` in this environment, so no live AX tree or window capture is claimed.
- The competitor outputs remain in a temporary directory outside the
  repository. No ScreenZen code, asset, string table, font, animation, logo or
  visual identity was copied into Hisn. The observations are an inventory of
  UI structure and vocabulary, not a product benchmark.
- Web research covered ScreenZen's App Store listing and independent reviews,
  plus public design references for Opal, one sec and uBlock Origin Lite. Those
  pages describe product behaviour; they are not runtime evidence of a specific
  build.

## Evidence

| Subject | REA operation | Evidence | Observed digest |
| --- | --- | --- | --- |
| `/Applications/ScreenZen.app` | `inspect-artifact` | `ev_c701bfefbb3252e374277098fb30fe3aa4b5f770f4b9dd908546c0d6f4fbd45c` | SHA-256 `3d71a3666b43ba400dc05e62d772c7a5f85eea06234e750b42c7d698fe8c7547` |
| ScreenZen bundled Firefox extension | `analyze-javascript-application` | `ev_4b826149fff4c166b69631bd685dcc708370089b8e8474346515d25722c3aa2f` | SHA-256 `92b0b35b56ded1cbdf242322e6fcca9e9c4b42df0824293f295887a115ee60cf`; graph `245d7e99…`; 5 relevant files, 14 nodes, 21 relationships, 15,355 AST nodes, 0 parse failures |
| ScreenZen native popover | `capture-native-ui-scenario` | not produced | `capability_unavailable` (no native macOS analysis provider) |

The JavaScript analyzer parsed HTML and JavaScript as **inert text**; no
competitor bundle bootstrap code, service worker or block page was executed.
The native capture failure is recorded rather than replaced with an unverified
description.

## What the research shows

### ScreenZen's browser block page

The bundled extension's block page is a deliberately small, full-screen
experience rather than a settings card:

- a dark full-viewport field; a centred logo/animation; one large status
  message; the block reason and any lock/timer context;
- the pause is the product: a breathing exercise with an explicit progress
  bar, followed by a clear continue/unlock action;
- "Close tab" is present but placed last; the page does not lead with a way to
  leave;
- the vocabulary is plain and non-judgemental, with a connection/retry state
  for the native app.

The REA graph also confirms the extension surface is compact (five source
files) rather than a full dashboard inside the block page. This supports the
design conclusion that the intervention moment should carry the weight, not a
long settings form.

### Comparable public patterns

- **Opal** pairs a calm, high-contrast visual system with a status-first home,
  named rules and a single clear session state. Public design galleries show
  grouped cards, luminous depth and a strong "active session" area.
- **one sec** leads with friction at the moment of opening an app, not with a
  score.
- **uBlock Origin Lite** keeps the popup spare: current-page status, a small
  number of controls, and a separate dashboard for depth.

### What Hisn already required

Hisn's [design guide](DESIGN_GUIDE.md) and [UI principles](UI_PRINCIPLES.md)
already ask for status first, one primary action, progressive disclosure,
explicit state words, Arabic parity, on-device operation and no protection
score. The pass kept those constraints.

## What was implemented

| Borrowed idea | Hisn implementation | Why it fits |
| --- | --- | --- |
| Pause as the product | The block page's pause panel now holds the breathing orb, a three-breath progress strip, the rotating reminder, four needs with icons and the local pause tally. | It makes the intervention moment the strongest part of the extension without changing enforcement. |
| One status, one next action | Popup and overview keep one status hero; the primary action is unchanged, and secondary actions are quieter. | Matches Hisn's existing hierarchy and the competitors' calm dashboards. |
| Grouped, scannable cards | Six protection layers became distinct icon tiles with state pills; settings cards got consistent headers, spacing and switches. | The state answer is faster to read than five identical shield rows. |
| Close-tab parity | The block page gained a quiet "Close this tab" link beside the existing new-tab and settings actions. | Gives the same low-friction exit the competitor block page offers, without adding an unlock path. |
| Fewer words at the decision point | Page subtitles, shortcut descriptions and the long language list were removed; long hints, block reasons and pause reminders were shortened. | The redesigned screens still carried too much explanatory text; the shortest truthful sentence now carries the decision. |
| Calm depth | Soft status tints, a restrained ambient background, one radius/shadow scale and a breathing halo. | Visual calm supports the product's stated goal; it does not imitate any competitor's palette, logo, font or animation. |

The localized labels added in this pass are Hisn's own English and Arabic
copy: `opt.overview.sub`, `opt.mode.standardShortDesc`,
`opt.mode.strictShortDesc`, `visual.fineTune`, `blocked.eyebrow`,
`blocked.breathHint`, `blocked.closeTab` and `blocked.reportSummary`.

## Deliberately not copied

- **ScreenZen's visual identity.** No dark `#282735` palette, Outfit font,
  animated rock logo, frame sequence, background image or copy was used.
- **The unlock / continue flow.** Hisn's commitment model has no in-line
  "unlock"; the block page offers leaving and reviewing settings, not weakening
  protection.
- **Streaks, open counts or a protection score.** Hisn keeps its local pause
  tally only; a count is not presented as evidence of protection.
- **An account, cloud sync or replacement-app launch.** These conflict with the
  on-device, no-account promise.
- **Competitor code, assets, models or string tables.** None were copied.
- **Runtime claims.** This pass changed no rule, permission, worker handler,
  storage key or lock decision.

## Limits and verification

- The native UI capture did not run; this document does not claim to have
  observed ScreenZen's live popover or its accessibility tree.
- The competitor page structure was read statically. It does not prove that
  ScreenZen's breathing, unlock or blocking paths work at runtime.
- Most visual captures of Hisn were made in an isolated preview harness with a
  mocked extension bridge, not a real toolbar popup. A separate real-worker
  Playwright run in Helium verified the actual worker baseline and repair
  readback and captured the dark English/Arabic popup plus the dark overview,
  rules, content options, lock and block page. The visual-checks page was not
  captured because Helium reports the local visual engine as
  `unsupported-runtime`; branded Chrome 156 refused to load the unpacked
  extension, so the full `ui-qa.cjs` actual-toolbar scenario did not complete.
- `extension/test/run.sh`, `popup.html`, `blocked.html` and a clean run of
  `settings.html` passed. The settings harness's file-import checks are
  timing-sensitive and also failed intermittently with the pre-change files
  restored; that is recorded in the design README rather than presented as a
  clean visual-test result.
- Dark visual-checks/media acceptance, a screen-reader audit, a user study and
  a physical-device review remain pending.

## Sources consulted

- [ScreenZen on the App Store](https://apps.apple.com/us/app/screenzen-screen-time-control/id1541027222)
- [ScreenZen review — MakeUseOf](https://www.makeuseof.com/app-to-stop-phone-addiction-screenzen/)
- [13 Screen Time App Design Examples — ScreensDesign](https://screensdesign.com/articles/screen-time-app-design/)
- [Opal screens — Lazyweb](https://www.lazyweb.com/company/opal)
- [Opal iOS screen-time selection — Mobbin](https://mobbin.com/explore/screens/2f164310-28b1-4053-ba7e-773da7f7adb9)
- [uBlock Origin Lite — Tooltivity](https://tooltivity.com/extensions/ublock-origin-lite)
- [REA project](https://github.com/morluto/rea)
