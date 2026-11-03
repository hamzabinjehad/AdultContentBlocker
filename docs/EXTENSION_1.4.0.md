# Hisn extension 1.4.0 — UI/UX pass

Released 2 November 2026 as a development candidate. Unpacked and store-ready;
no Chrome Web Store listing, assigned store ID or managed force-install is
claimed.

## Design direction

The extension had grown honest, detailed and wordy. Every layer carried a
sentence, the settings dashboard repeated status in four places, and the eye had
to read before it could find the state. 1.4.0 keeps the behaviour and reduces the
reading path.

Seven principles drove the pass:

1. **Status first.** One hero answers “am I protected?” The popup and the
   dashboard both lead with it; everything else is secondary.
2. **Quiet defaults.** A healthy layer is one word — On, Off, Checking or Issue
   — with a state dot. Explanations moved to Details, the page the gear opens,
   or a tooltip.
3. **One primary action.** The popup has one full-width Manage protection
   action; repair and reconnect are secondary or icon-only. Each settings
   section has one primary action.
4. **Colour means state.** Blue is action, green is active, amber is attention,
   red is failure. The built-in-list message is information, not a warning, and
   is styled neutrally.
5. **Progressive disclosure.** Diagnostics, repair, the private-window guide and
   the longer allowlist rule remain available, collapsed by default.
6. **One rhythm.** A small spacing scale, 12/16px radii, one border colour and
   one subtle shadow. Cards group; borders divide; no decorative gradients.
7. **Narrow and RTL native.** Logical properties, 44px targets, one document
   scroll at very small popup sizes, and layouts checked at 160–480px in English
   and Arabic.

## What changed

### Popup

- Status hero card with a state-coloured shield and one short line.
- Six compact layer rows: title, one-word state and a gear. The removed
  sentences were “Works without the app”, “Checks words on accessible pages”,
  “Permission needed”, the engine status paragraph and the app row's extra
  label.
- Page-language row is a single label/value line; its explanation is a tooltip.
- Lock becomes a compact banner; Details stays collapsed unless repair is
  needed.
- One full-width primary action and a 44px reconnect icon.
- Privacy note shortened to “On-device. No uploads.”

### Settings overview

- One status card with state-coloured surface, repair action and notices.
- One layer card with six state chips, replacing the duplicated “Browser
  protection” list.
- App connection becomes a compact card beside the layers.
- Two quick links with one-line descriptions.
- Block list becomes one row with its real installed state and an Update
  button; the longer explanation is a tooltip.
- Footer: version, privacy/support links and the managed-policy sentence;
  removed the repeated on-device paragraph and the sidebar tagline.

### Settings, Rules and Visual checks

- Rules page: shorter headings and hints; the long allowlist rule is a tooltip.
- Content options: removed the always-on-content paragraph; shortened the
  page-word, sensitivity, language, short-video, custom-word and correction
  hints. Language support is a compact two-line statement.
- Visual checks: shortened the beta, coverage, person-hiding, presentation and
  privacy explanations. Behaviour and guard rails are unchanged.
- Arabic copy was shortened in step with English, not left longer.

### System

- New light and dark token sets with stronger contrast and fewer surfaces.
- Buttons, inputs, focus rings and touch targets standardised.
- Information notices are neutral; warning notices keep amber.
- Removed unused locale keys so the two translation tables only carry copy the
  product renders.
- `extension/test/browser/preview.html` gained a `view` parameter and a healthy
  enforcement fixture so screenshots show the normal state, not a permanent
  spinner.

## Evidence

Layout captures, including before/dark/Arabic cases and the block page, are in
[`design/extension-ui-2026-11-02/`](../design/extension-ui-2026-11-02/README.md).

- `./test.sh extension` — **PASS**: JavaScript suites, packaged-zip check and
  evaluation corpus at sensitivity 50/80/100.
- `./test.sh browser` — **PASS**: DNR, live scanner, six block pages, settings
  and popup in English and Arabic, feed compatibility and the real visual
  engine.
- `popup.html` browser checks cover 160, 200, 400 and 480px widths, both
  languages, touch targets, page-language row and the primary action.
- `settings.html` checks narrow and RTL layouts, the visual preferences, lock
  guards and connection states.

Package produced by `extension/package.sh`:

| Field | Value |
| --- | --- |
| Version | 1.4.0 |
| Path | `dist/hisn-extension-1.4.0.zip` |
| Files | 91 |
| Size | 6.2 MB |
| SHA-256 | `2cb7d723884b633b37f295ced0deec9ac0046c95d70d6c1fc80365acb41c5552` |

`python3 extension/check_package.py dist/hisn-extension-1.4.0.zip` passes.

## Limits

- No user study, screen-reader audit or physical store-profile review is
  claimed. The captures are deterministic preview renders, not a real toolbar
  auto-size measurement.
- Shortening copy does not change what is enforced. Lock, managed-by-app,
  private-window and visual-coverage limits remain in the UI where they affect
  a choice.
- No permission, network request, storage field or scoring rule changed. The
  language-aware scoring from 1.3.0 is intact.
- Store upload, the real store ID, managed force-install and a clean-profile
  acceptance pass remain owner actions recorded in
  [EXTENSION_STORE.md](EXTENSION_STORE.md).

## Reproduction

```bash
./test.sh extension
./test.sh browser
extension/package.sh
python3 extension/check_package.py dist/hisn-extension-1.4.0.zip
```
