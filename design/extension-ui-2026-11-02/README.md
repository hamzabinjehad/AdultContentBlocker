# Extension UI pass — 2026-11-02

Captures from the deterministic preview harness (`extension/test/browser/preview.html`)
with a simulated healthy browser-only state. They show layout, spacing, colour
and RTL behaviour; they are not a screen recording, a real toolbar auto-size
measurement or a user study.

- `popup-light-en.png`, `popup-dark-en.png` — the redesigned toolbar popup.
- `popup-light-ar.png` — the same popup in Arabic, right to left.
- `options-overview-light-en.png`, `options-overview-dark-en.png` — the
  dashboard with one status hero, six compact layer rows, two shortcuts and a
  one-line block-list row.
- `options-overview-light-ar.png` — the dashboard in Arabic.
- `options-rules-light-en.png` — Rules after the hint trim.
- `options-settings-light-en.png` — Content options after the hint trim.
- `options-media-light-en.png` — Visual checks, unchanged in behaviour.
- `blocked-light-en.png` — the block page after the token refresh.

## Design principles applied

1. **Status first.** One hero answers “am I protected?” before any technical
   detail. The popup and the options dashboard lead with it.
2. **Quiet defaults.** Healthy layers read as one word — On, Off, Checking or
   Issue — with a state dot. The old sentences (“Works without the app”, “Checks
   words on accessible pages”) were removed from the list. Explanations moved to
   the page the gear opens, the Details disclosure, or a `title`.
3. **One primary action.** The popup has one full-width Manage protection
   button; repair and reconnect are secondary or icon-only. Each settings
   section has one primary save/add action.
4. **Colour means state.** Blue is action, green is active, amber is attention,
   red is failure. The built-in-list message is an information notice, not a
   warning, and is now styled neutrally.
5. **Progressive disclosure.** Details, repair, diagnostics, the private-window
   guide and the long allowlist rule remain available but no longer occupy the
   main reading path. Tooltips carry secondary explanations.
6. **One rhythm.** A 4/8/12/14/16/18 spacing scale, 12/16px radii, one border
   colour and one subtle shadow. Cards group; borders divide; no decorative
   gradients or shadows on static content.
7. **Narrow and RTL native.** Logical properties, 44px touch targets, one
   document scroll at very small popup sizes, and a layout tested at 160, 200,
   400 and 480 px in both languages.

## What changed

- New light/dark token set and a calmer, higher-contrast palette.
- Popup: status hero card, compact layer rows, page-language row without a
  paragraph, compact lock banner, single primary action, one-line privacy note.
- Options overview: status hero with repair, layer status chips, compact app
  row, two quick links, one-line block-list row, Details disclosure.
- Options copy: removed the always-on-content hint, the sidebar tagline, the
  duplicate fact sheet line and several paragraphs; moved longer rules into
  tooltips. The zero-text-reduction warnings (lock behaviour, managed-by-app,
  visual limits, private windows) remain visible when relevant.
- Arabic copy was shortened with the English copy, not left as a longer
  translation.

## Verification

- `extension/test/browser/run.sh popup.html` — pass, including 160–480px
  layouts, English and Arabic, touch targets and the page-language row.
- `extension/test/browser/run.sh settings.html` — pass, including narrow and
  RTL layouts, visual preferences and lock guards.
- `extension/test/browser/run.sh blocked.html` — pass.
- The JavaScript suite (i18n, status, settings, worker) passes. No behaviour,
  permission, network path or storage field changed in this pass.