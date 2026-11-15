# Android: Block private tabs

Date: 15 November 2026. A new switch in Rules › Built-in checks, under Short videos.

## What it does

While it is on and App protection runs, Hisn closes Chrome's Incognito tabs. It recognises
one by Chrome's own view IDs, read from a real screen: `location_bar_incognito_badge` (the
"Incognito mode" badge in the toolbar, on every incognito page) and
`new_tab_incognito_container` (the incognito new-tab page). A normal tab has neither. No page
text or address is read.

The action is **Back**, repeated while the badge is on screen: Back walks the private tab's
history and then closes the tab, so Chrome stays usable. Home was rejected: the private tab
would still be in front on the next open, which would lock the person out of Chrome. A short
"Hisn closed a private tab" note shows at most every 10 s.

- Covered: Chrome, Chrome Beta, Dev and Canary (`PrivateBrowsing` in core).
- Not covered: other browsers. Their private modes were not checked on a device; they can be
  blocked as whole apps instead.
- Stored on the phone (`PrivateTabsStore`); a running protection lock refuses turning it off.
- English and Arabic.

## Checked

- `PrivateBrowsingTest` (Chrome channels, qualified IDs, unknown apps, lock) in `test-core.sh`.
- Emulator (API 36, Chrome): a leftover incognito tab was closed when Chrome opened; a new one
  was closed within about a second; a normal page stayed open. Closing a brand-new private
  tab can land on the home screen; the normal tabs remain.
- Owner's Galaxy (signed direct build): switch on in Rules, Chrome › Incognito → closed, with
  the note on screen (`01-samsung-private-tab-closed.png`); Chrome stayed on its normal tab.
- `bash android/test-ui.sh emulator-5560 direct`: 40 of 40.
