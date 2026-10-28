# Hisn extension 1.2.0 — list freshness, clearer list blocks and safer handover

Development candidate, 2026-10-28. Not a store release. It builds on the
1.1.12 extension plus the short-video and pause work already in the tree.

## What changed

- **The block page now explains a signed-list block.** `blocked.html?reason=domain`
  previously fell through to the generic "This page is blocked" copy because
  `blocked.js` did not accept `domain` as a reason. It now says the address is
  on the signed list, explains that the list updates automatically, and points a
  false-positive report at **Always allowed**. The text is English and Arabic.
- **A native reply can no longer reset Strict to Standard.** `pollNative` used
  to insert `mode: "blocklist"` before merging the bridge's fields. The bridge
  sends `mode` today, but any older or malformed reply that omitted it would
  have rewritten a running Strict lock to Standard. The patch now leaves an
  absent field alone, exactly as the other bridge fields already did. A
  regression check covers it.
- **The block list has a visible age and an update path.** The popup/options
  status now says when the last signed generation was installed, warns after 14
  days without an update, and says when only the built-in list is in use.
  Settings has **Update block list**, which asks the worker to check now instead
  of waiting for the six-hour alarm. One update runs at a time; a second request
  shares the first answer.
- **Manual and automatic updates cannot overlap.** `updateListNow` serialises
  the download-and-install path and keeps the previous generation when the
  manifest, signature, shape or install is refused. A failed update reports a
  failure; it does not pretend the list changed.
- **Facebook share/reel links are covered.** The short-video rules and the
  in-page matcher now treat `facebook.com/share/reel/…` like the Reels pages,
  so a shared reel cannot sidestep the feed block.
- **Version 1.2.0.** The manifest version is bumped for packaging; the local
  development key remains until the first store upload.

## Verification

- `extension/test/run.sh` — all JavaScript suites, including the new status,
  runtime and short-video checks.
- `extension/test/browser/run.sh blocked.html` — the block page renders the
  signed-list reason in a real browser.
- `extension/test/browser/dnr.sh` — every static regex still compiles.
- `./test.sh extension` — package and evaluation corpus.
- `python3 extension/test/i18n.test.js` equivalent through the suite: English
  and Arabic keys, placeholders and every referenced key.

## Limits

- The list update still requires the published `lists` branch to exist and the
  device to reach `raw.githubusercontent.com`. A failed update keeps the old
  list; it does not fall back to a network resolver.
- The "Block list updated" time is local to this browser profile. It proves a
  verified install, not that every domain in the generation is reachable or
  classified.
- A false-positive allowlist entry only affects this browser's extension. The
  app-owned allowlist remains the authority when the app is connected.
- The signed-list block page still cannot name the exact domain: the redirect
  carries a reason only, and the extension deliberately keeps no browsing
  history to look it up.
