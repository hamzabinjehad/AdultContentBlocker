# Website captures: the calm Android download page

Date: 14 November 2026. Captured from the static site in `website/` in headless
Chrome, served locally. The public page offers the free Android download and
shows the next Android update, Mac, iPhone/iPad, the browser extension and the
Vault as available or coming soon. Version numbers, hashes and source links are
not on the page.

## What the competitor pass changed

Unchaind’s site ([unchaind.app](https://unchaind.app/)) supplied the atmosphere:
a soft full-bleed aura, a centered promise, a phone with blurred screens layered
behind it, product stats and calm space. ScreenZen supplied the section flow:
benefit cards, step cards with real screens, FAQ and a final CTA. Hisn keeps
that shape and refuses the parts it cannot honestly copy — no faith language,
no invented test results or user counts, no shame, no streak ranks, no paywall.
The animation is anime.js 3.2.2 (MIT), vendored so the page makes no
third-party request; `motion-flag.js` and `animations.js` handle the no-flash
entrance, layered-phone reveal and scroll reveals, with a reduced-motion and
no-JavaScript fallback.

## Files

| File | What it shows |
| --- | --- |
| [landing-en-light.png](landing-en-light.png) | The full product page in light mode, 1280 px. Centered hero, layered phone, stats, benefits, download, four install cards, limits, roadmap, FAQ, final CTA. |
| [landing-en.png](landing-en.png) | The same page in dark mode. |
| [landing-mobile.png](landing-mobile.png) | 390 px viewport, full page: stacked hero and phone, two-column stats, single-column cards. |
| [hero-ar.png](hero-ar.png) | The Arabic hero at 1280×1600, `dir=rtl`. A viewport capture: headless Chrome’s full-page `captureBeyondViewport` shifts RTL pages at the right edge, so the full-page Arabic file is not kept. The page reports no horizontal overflow in either language. |
| [privacy-en.png](privacy-en.png) | The short, plain-language privacy page. |

The app screens on the page are real Hisn captures:
`assets/home.png` (native shell), `assets/rules.png` (rule tiles) and
`assets/phone-checks.png` (the phone check on a Xiaomi-labelled AVD).

## Run

```sh
cd website
bash stage-downloads.sh
python3 -m http.server 8765
# http://127.0.0.1:8765/
```

`bash website/build-site.sh` writes the deployable copy to
`dist/hisn-website-<date>/` and a zip. The public output excludes
`release.json` and the build log; the APK is staged as the stable
`downloads/hisn-android.apk`.
