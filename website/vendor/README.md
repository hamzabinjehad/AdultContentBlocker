# Vendored: tlock

`lib/tlock.bundle.js` is built from `tlock-js` 0.9.0 and committed, so the site
needs no install step at run time and no network at load time.

## Rebuild

```bash
cd website/vendor
pnpm install          # pinned by pnpm-lock.yaml
node build.mjs        # writes ../lib/tlock.bundle.js and .LEGAL.txt
```

Node must be on `PATH`: `esbuild-wasm` starts a child process. The build uses
`esbuild-wasm` rather than native esbuild so there is no postinstall binary to
approve; the output is the same IIFE the page loads.

`build.mjs` pins the format (`iife`, global `HisnTlockBundle`), the browser
targets and the global shim. `entry.js` is the only surface the vault uses:
`timelockEncrypt`, `timelockDecrypt`, `HttpChainClient`, `HttpCachingChain` and
`Buffer`, published as `window.HisnTlock`.

## Why these packages

`tlock-js` encrypts a payload under the drand quicknet public key for a future
round; only the threshold network's signature for that round can decrypt it.
That is the whole time lock — there is no server of ours in the path. See
[`docs/WEB_VAULT.md`](../../docs/WEB_VAULT.md).

The licence of every bundled package is in `NOTICES.md`; the inline notices
esbuild found are in `../lib/tlock.bundle.js.LEGAL.txt`.
