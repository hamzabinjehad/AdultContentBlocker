# Bundled dependencies and licences

`lib/tlock.bundle.js` contains the packages below, pinned by
`pnpm-lock.yaml`. All are permissively licensed; the full licence text ships
with each package in the pnpm store, and the inline notices esbuild preserved
are in `../lib/tlock.bundle.js.LEGAL.txt`.

| Package | Version | Licence | Upstream |
|---|---|---|---|
| `tlock-js` | 0.9.0 | Apache-2.0 OR MIT | https://github.com/drand/tlock-js |
| `drand-client` | 1.2.5 | Apache-2.0 OR MIT | https://github.com/drand/drand-client |
| `@noble/curves` | 1.9.7 | MIT | https://github.com/paulmillr/noble-curves |
| `@noble/hashes` | 1.8.0 | MIT | https://github.com/paulmillr/noble-hashes |
| `buffer` | 6.0.3 | MIT | https://github.com/feross/buffer |
| `@stablelib/chacha20poly1305` | 1.0.1 | MIT | https://github.com/stablelib/stablelib |

`esbuild-wasm` 0.28.2 (MIT) is a build-time dependency only and is not part of
the committed bundle.

The bundle is unmodified third-party code. Hisn's own code is the wrapper in
`../lib/tlock.js` and the modules beside it.
