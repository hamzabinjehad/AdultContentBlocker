# Protection settings inside the popup

Development follow-up to 1.1.2: Chromium's **Manage protection** and layer
shortcuts now open the existing guarded settings interface inside the toolbar
popup. No new tab is required. **Open full page** remains optional.

- Browser-only users can edit blocked/allowed sites, text checks and visual
  preferences. Domain/text protection does not require the native app.
- App-connected users see the same ownership and commitment restrictions as
  the full settings page. App-owned lists stay read-only; visual settings keep
  their existing cannot-weaken rules. No standalone native lock is added.
- **Back** keeps the embedded document and unsaved drafts while the popup stays
  open. Closing a browser popup can discard drafts: explicitly save site/text
  edits. Visual preferences save automatically with confirmed success/error.
- Opening a full page warns when edits are unsaved or a save is in progress.
- Arabic/English and automatic light/dark appearance are shared. Small or
  zoomed viewports can scroll without collapsing the embedded editor.

The popup document requests an intrinsic 480 × 600 size on its root element.
Using only a body width capped by `100vw` allowed the browser's initial narrow
popup viewport to constrain its own auto-sizing. An isolated **real Helium
toolbar popup**, opened with the action API and inspected through popup views,
now reports the requested width and no horizontal overflow. Narrow tab/iframe
previews explicitly override root sizing; they are not auto-size proof.

This is a local source change, not a new signed native app release. Reload the
extension loaded from this checkout to try it. The native app does not need to
be uninstalled or reset. Safari already uses its settings page as its popup;
this change does not alter Safari's existing feature/commitment limits.

Verification uses the popup/settings browser harnesses, extension unit/package
checks, and `extension/test/browser/ui-qa.cjs` in an isolated Helium profile.
The real worker saves popup edits; an isolated managed/locked fixture checks
read-only controls. This is not a test of a real native connection or the
toolbar bubble in ChatGPT's browser. No real accounts or protection settings
are changed by the isolated tests.
