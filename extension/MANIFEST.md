# manifest.json — why each unusual key is there

These notes used to live in the manifest as `_comment_*` keys. Chromium does
not recognise them and shows an "Unrecognized manifest key" warning for each
when the extension is loaded unpacked — harmless, but noise on every load. The
manifest is machine config, not a place for prose, so the prose moved here and
the keys were removed. `package.sh` still strips anything like this defensively.

## `key`
Pins this extension's Chrome/Edge/Chromium ID to
`hfhaffbmoeepcdolgejeidkgaoapcjig` regardless of where it is loaded from. The
native messaging host manifest that lets the browser reach the macOS app has to
name this ID in `allowed_origins` *before* the extension is ever loaded, so the
ID cannot be left to chance. The Chrome Web Store assigns its own ID and ignores
this field — see `docs/CHROME_ENFORCEMENT.md` and `docs/EXTENSION_KEY.md`.

## `permissions: ["scripting", …]`
`scripting` is here for `registerContentScripts`, not `executeScript`. The
page-text scanner is registered dynamically rather than declared as a
`content_scripts` block, so that switching text scanning off actually stops the
injection instead of merely making it a no-op — a static `content_scripts`
block would run on every page of every site regardless of settings.

## `web_accessible_resources: ["blocked.html"]`
Chrome refuses a `declarativeNetRequest` redirect whose `extensionPath` is not
web-accessible, and it refuses it SILENTLY — the navigation just fails instead
of landing on the block page. Every block rule redirects to `/blocked.html`, so
without this entry each produced an error page rather than the calm, deliberate
screen the user is meant to see. Listing a resource does expose the extension ID
to page script, a cost this extension otherwise avoided; it is accepted for this
one file only, and only because the ID is already pinned and public. Nothing
else is listed, and nothing else should be.

## `incognito: "spanning"`
`split` would give incognito its own storage — an UNLOCKED context the user
could browse in freely — so it is out. `not_allowed` kept the extension out of
private windows entirely, so page-text scanning did not run there. `spanning`
shares the one background context (the lock state carries in), and the browser
keeps the extension OFF in private windows until the user turns on "Allow in
Incognito" — opt-in by design, which the popup detects and asks for. The other
answer, removing private browsing entirely, is `make_profile.py` (default).

## `declarative_net_request` keyword ruleset
Keyword rules match the whole URL — host, path AND query string — which is the
one thing the macOS socket filter provably cannot do, because HTTPS encrypts
everything after the hostname. This is therefore the only layer that can stop a
search for an explicit term on a domain that must stay reachable, and the only
one that sees adult paths on otherwise legitimate hosts. Short or ambiguous
terms use `regexFilter` with explicit non-letter boundaries so `sex` cannot
match essex.gov.uk and `sks` cannot match tasks.office.com; every rule also
carries an `excludedRequestDomains` rail for reference and public-health sites.
