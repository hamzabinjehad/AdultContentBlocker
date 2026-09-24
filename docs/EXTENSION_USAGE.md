# Using Hisn in a browser

## Without the macOS app

Load `extension/` as an unpacked extension in a supported Chromium browser,
or install the published extension when available. Open the Hisn popup and
choose **Settings**. The bundled domain rules and page-text checking work
without a native app connection. Verified list updates run in the extension.

**Standard** mode blocks listed domains and applies content checks. **Strict**
mode limits browsing to saved allowed sites; save the sites you need before
applying it. Existing tabs outside the policy may close to a block page.
Allowed sites may load supporting resources from other destinations.

Settings lets you manage blocked sites, allowed sites, custom content words,
and page-text sensitivity. Invalid domain lines prevent that section from
saving and are identified by line number. Successful saves display the
canonical domains actually sent to the blocking engine.

Use the sidebar to move between sections. Each section saves independently;
an **Unsaved changes** label distinguishes edits from applied settings, and
**Discard edits** restores that section's saved values. The popup labels the
connection as **Browser only**, **App connected**, or **App offline**. Blocked
pages offer a new-tab action and a shortcut to review settings.

Browser-only filtering covers this browser, not other apps. It does not create
a tamper-resistant timed lock. Private-window coverage requires the browser's
extension permission unless private browsing is disabled by managed policy.

## With the macOS app

Follow [SETUP.md](SETUP.md) to install the app and native messaging bridge.
The extension checks for the bridge automatically. **Check connection** in
the popup or **Check app connection** in Settings retries immediately.
A missing connection does not establish whether the app is installed.

After a successful connection, the app owns blocking mode, site lists, custom
words, sensitivity, and timed locks. Its settings replace the browser values;
the extension shows those controls read-only. Edit them in the macOS app.
An open Settings page updates when the app's next heartbeat arrives.

Incorrect-block reports remain local to the browser. During an active lock
or fail-closed safeguard, reports wait for later review instead of weakening
protection. Review them in Settings after the restriction is lifted.

If the bridge disappears, filtering continues with the last settings. Losing
contact during an active lock triggers strict mode after the existing grace
period. Disconnection never silently transfers policy ownership to browser
settings; reopen the app and repair the bridge connection. The app connection
alone does not prove the macOS system filter is active—check its status in the app.

## Verification

Run `./test.sh extension` for logic, worker, and package checks. The worker
integration tests use the production background module with mocked Chrome
transport and storage.

Run `extension/test/browser/run.sh settings.html` for the Settings UI in a
headless Chromium browser (set `CHROME` to its executable if needed). This
uses the real page and DOM with simulated extension messages; it does not
activate a native filter or prove installation of the real bridge.
