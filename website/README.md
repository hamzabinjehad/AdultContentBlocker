# Hisn website

The product site for the Android app, plus the Vault. Static files, no account,
no server, no analytics. The site is bilingual (English and Arabic), light and
dark, and every page has a strict CSP with no third-party resource.

- `/` — the product page, built from the Hisn app prototype (sky, painted
  landscapes, serif titles, the 3D objects) and approved as
  `website-next/prototype.html` on 25 November 2026: one phone in the painted
  scene, store buttons (Android live; iPhone, iPad and Mac coming), the verse
  band, four feature cards, who it is for, the hard minute, milestones, the
  three looks (Fajr, Madinah, Layl) as a live switch, free and donations,
  platforms and questions. English in the HTML, Arabic in `lib/home-copy.js`;
  `lib/home.js` runs the language switch, the looks, the phone timer and the
  breathing circle; styles in `assets/home.css`, art in `assets/hisn/`. The
  Patreon and coffee buttons stay hidden until their links exist.
- `/vault.html` — Hisn Vault, the off-device time lock. See
  [`docs/WEB_VAULT.md`](../docs/WEB_VAULT.md).
- `/partner.html` — the Vault's partner half.
- `/privacy.html` — the short privacy page for the download site.
- `/limits.html` — the opt-in list of what Hisn cannot do.

The design and the competitor pass are recorded in
[`docs/WEBSITE_2026-11-14.md`](../docs/WEBSITE_2026-11-14.md).

## Run it

```bash
cd website
bash stage-downloads.sh          # copies the signed APK from dist/ (not committed)
python3 -m http.server 8765
# http://127.0.0.1:8765/
```

A local server is required: ES modules and `crypto.subtle` do not run from
`file://`. The download links are not staged in a fresh clone; run
`stage-downloads.sh` first, or the build script below.

## Build a deployable copy

```bash
bash website/build-site.sh
# dist/hisn-website-<date>/       (static tree, APK staged in downloads/)
# dist/hisn-website-<date>.zip
cd dist/hisn-website-<date>
python3 -m http.server 8766
```

`stage-downloads.sh` reads `release.json`, refuses a file whose size or SHA-256
does not match, copies it to the stable public name `hisn-android.apk`, and
writes `SHA256SUMS`. The APK is deliberately not committed
(`website/downloads/*.apk` is ignored). `build-site.sh` leaves `release.json`,
the build log, the vendor notice and the checksum file out of the public folder;
the vendored runtime keeps its license.

## The files

| Path | What it is |
|---|---|
| `index.html` | The product page: hero, download, features, the step-by-step screens, privacy row, roadmap, FAQ. |
| `vault.html` | The Vault: create, setup window, sealed, due, revealed. |
| `partner.html` | The partner's half: keypair, public key, release. |
| `privacy.html` | The short privacy page, linked from the product page and the app record. |
| `limits.html` | The opt-in list of what Hisn cannot do, linked from the FAQ and footer rather than from the sales flow. |
| `release.json` | Internal release identity: version, bytes, SHA-256, certificate, source record. Not copied to the public build. |
| `index.html` / `lib/home.js` / `lib/home-copy.js` / `assets/home.css` | The product page: English in the HTML, Arabic in `home-copy.js`, the switch, looks, timer and breathing circle in `home.js`. `lib/site.js` still runs the privacy and limits pages. |
| `lib/crypto.js` | Vault AES-GCM, P-256 ECDH + HKDF, passcode drawing. |
| `lib/vault.js` | The vault record, both open paths, import validation. |
| `lib/tlock.js` | drand quicknet constants, round math, sealing/opening. |
| `lib/app.js` | The Vault page. The code lives in memory only. |
| `lib/partner-app.js` | The partner page. The private key lives in `localStorage`. |
| `lib/copy.js` | Vault English and Arabic strings. |
| `lib/store.js` | Vault `localStorage`, file download/import, clipboard. |
| `lib/tlock.bundle.js` | Committed tlock-js bundle; see `vendor/`. |
| `assets/style.css` | Shared colour tokens, typography and Vault controls, light and dark. |
| `assets/site.css` | The product, privacy and limits pages: green tokens (light and dark), header, hero, stepper, cards, footer. |
| `lib/how.js` | The "How Hisn works" stepper: choosing a step swaps the phone screen. Without it every step and the first screen still show. |
| `assets/home.png`, `apps.png`, `rules.png`, `block.png`, `phone-checks.png` | Real Hisn emulator captures used on the product page. |
| `test/site.test.mjs` | Release manifest, download links, translation keys, CSP and Vault links. |
| `lib/motion-flag.js`, `lib/animations.js` | The no-flash motion setup and the scroll/entrance animations, built on the vendored anime.js. |
| `lib/vendor/anime.min.js`, `LICENSE.anime` | anime.js 3.2.2, MIT; vendored so the CSP allows no third-party script. See `lib/vendor/README.md`. |
| `test/browser/landing.mjs` | Headless Chrome check of the product page in English and Arabic, desktop and mobile, plus the privacy and limits pages, with screenshots. |

## Tests

```bash
HISN_SKIP_NETWORK=1 node --test website/test/*.test.mjs
HISN_CHROME="/path/to/Chrome" node website/test/browser/landing.mjs --shots /tmp/hisn-landing
HISN_CHROME="/path/to/Chrome" node website/test/browser/e2e.mjs --shots /tmp/hisn-vault
```

The Node tests need Node 20+; the browser tests need a Chromium binary (the
macOS Chrome path is the default). The landing test fails on a console error or
on horizontal overflow at 390 px, and switches the page to Arabic and back.

## Hosting

The site is static. Hosting is still a trust boundary: whoever serves the
JavaScript at creation time can read a Vault code before it is sealed. The CSP
on the product and privacy pages allows no connection at all; the Vault page
allows only `https://api.drand.sh`, and the partner page allows none.

Publish the **built** folder, including `downloads/`, over HTTPS. Do not serve
the source tree directly in production (it contains tests, `release.json` and
the staging scripts). There is no analytics, cookie banner or third-party
script to add, and the site must not gain one.

## Arabic

The product page's Arabic lives in `lib/site.js`; the Vault's in `lib/copy.js`.
`test/site.test.mjs` fails if a page asks for a missing key or an Arabic value
contains no Arabic text. The Arabic has not been reviewed by a native speaker
yet — treat the security and privacy sentences as draft.
