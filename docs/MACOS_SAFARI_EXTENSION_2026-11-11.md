# macOS Safari Web Extension — first target

Date: 11 November 2026.

## What was added

`HisnSafariExtension` is now a real macOS app-extension target embedded in
`Hisn.app/Contents/PlugIns`. It uses Safari's
`com.apple.Safari.web-extension` extension point, the same shared scanner as the
iOS Safari extension, and a build phase that verifies the signed seed terms and
visual assets before staging them.

The staged extension contains:

- `background.html` and the shared `background.js`;
- page and X/feed content scripts;
- the shared normalizer, scorer and seed terms;
- the optional local visual engine and models;
- the block page and options page.

It does not contain a copy of the scanner source: the build stage copies the
canonical `extension/` and `ios/HisnWebExtension/` files only after the seed and
visual asset hashes pass.

## Deliberate first-step limits

- **No native messaging yet.** The native handler accepts no messages and sends
  nothing to the app. The extension uses its bundled scanner and its own local
  visual preferences. It does not yet read the Mac lock, hand lists, custom
  words or filter health.
- **BrowserGuard still treats Safari as uncovered.** During an active guard,
  Safari still receives the existing warning and close path. The extension is
  useful outside that path today; treating Safari as a supported browser needs a
  heartbeat contract first, not just an installed `.appex`.
- **No load in Safari was recorded.** The target builds and embeds, and the
  staged bundle is tested. Safari requires the user to enable the extension;
  unsigned debug builds are not a distribution test.

## Verification

| Check | Result |
|---|---|
| `./test.sh macos` | 624 tests pass; the Safari target compiles and is embedded |
| `macos/test_safari_extension.py` | passes: target point, handler, PlugIns embedding, staged manifest, shared scorer, seed terms, visual engine and icons |
| Built appex | `HisnSafariExtension.appex` at `Hisn.app/Contents/PlugIns`, with `Contents/Resources/manifest.json`, `background.js`, content scripts, `seed/terms.json` and visual assets |

## Next steps

1. Add a verified Safari extension heartbeat and a privacy-preserving `BrowserGuard`
   path that allows Safari only while that heartbeat is fresh.
2. Bridge the app-group lock/settings to the extension through the handler.
3. Add a localized Safari setup guide and deep link to Safari's Extensions
   settings.
4. Sign and load the target in a real Safari profile; record what Safari
   actually enables, including private-window behavior.
