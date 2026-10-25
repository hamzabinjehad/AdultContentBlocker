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

## Minimal popup and one-tap site blocking — 2026-10-24

The Chromium popup is quieter. The status icon now carries the protection
colour instead of a separate dot, the language switch is a plain text button,
and the boxed status card and divider lines are gone. The "Protection layers"
caption is now a screen-reader-only heading. Layer rows are shorter, so all
five layers fit without scrolling in the 480 × 600 popup.

When the current tab is an ordinary `http(s)` site and the browser owns the
block list, a **Current site** row offers **Block**. It adds the canonical
host to the existing custom block list through the same guarded `update`
message, so the worker rechecks it. Open tabs on that host move to the block
page, which the worker already handles. Adding a block is tightening, so it
stays available during a browser lock. The row is hidden on browser and
extension pages, on IP-address hosts, and when the Hisn app owns the list.
When the host or a parent domain is already blocked, the row shows **Blocked**.
Removing a block still goes through **Manage protection** and its existing
lock rules.

Evidence (Linux, isolated Chromium profile, no native host): 54 settings unit
checks, including 16 new quick-block cases; the i18n and Arabic catalog gates;
the popup browser harness; and 53 of 54 UI QA checks (the remaining check
needs a real toolbar popup and also fails on the unchanged baseline here). An
end-to-end run blocked a test site from the popup: the worker stored the
host, closed the tab and opened `blocked.html?reason=custom`. Real toolbar
popup sizing, VoiceOver and Helium checks on macOS remain pending.
