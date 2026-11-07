# Hisn Vault (web)

A static page that seals a code until a date. No account, no server, no
analytics; the vault is a JSON file you keep. The design, threat model and
verified evidence are in [`docs/WEB_VAULT.md`](../docs/WEB_VAULT.md).

## Run it

```bash
cd website
python3 -m http.server 8765
# http://127.0.0.1:8765/
```

A local server is required: ES modules and `crypto.subtle` do not run from
`file://`. Nothing is installed and nothing is fetched at load time except
`index.html`, the stylesheet and the two scripts.

## The files

| Path | What it is |
|---|---|
| `index.html` | The vault: create, setup window, sealed, due, revealed. |
| `partner.html` | The partner's half: keypair, public key, release. |
| `lib/crypto.js` | AES-GCM, P-256 ECDH + HKDF, passcode drawing. No DOM, no storage. |
| `lib/vault.js` | The vault record, both open paths, import validation. |
| `lib/tlock.js` | drand quicknet constants, round math, sealing/opening. |
| `lib/app.js` | The vault page. The code lives in memory only. |
| `lib/partner-app.js` | The partner page. The private key lives in `localStorage`. |
| `lib/copy.js` | English and Arabic strings, date and language handling. |
| `lib/store.js` | `localStorage`, file download/import, clipboard. |
| `lib/tlock.bundle.js` | Committed tlock-js bundle; see `vendor/`. |
| `assets/style.css` | Light and dark, RTL by logical properties. |

## Tests

```bash
node --test website/test/*.test.mjs                 # 16 tests, includes live drand
HISN_SKIP_NETWORK=1 node --test website/test/*.test.mjs
node website/test/browser/e2e.mjs --shots /tmp/hisn-shots
```

The first command needs Node 20+ and is not part of the repo's bare
`./test.sh` run (that run deliberately avoids a Node toolchain). Ask for it with
`./test.sh website`. The browser test needs a Chromium binary; set
`HISN_CHROME` to override the macOS default.

## Hosting

The page is static, but hosting is still a trust boundary: whoever serves the
JavaScript at creation time can read the code before it is sealed. If that
matters, run the page from a local copy, or pin a published build hash. The CSP
in both pages allows no third-party script, no frame, no form post and no
network connection other than `https://api.drand.sh` (the partner page allows
none at all).

## Arabic

Every string is in `lib/copy.js` under `en` and `ar`; `test/copy.test.mjs`
fails if the two tables drift apart or a page asks for a missing key. The Arabic
has not been reviewed by a native speaker yet — treat the security sentences as
draft.
