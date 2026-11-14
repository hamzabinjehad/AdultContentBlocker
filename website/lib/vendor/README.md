# Vendored motion library

`anime.min.js` is **anime.js 3.2.2** by Julian Garnier, MIT licensed. It is
vendored so the site can keep `script-src 'self'` and make no third-party
request.

- Source: <https://github.com/juliangarnier/anime> (npm `animejs@3.2.2`)
- File: `lib/anime.min.js` from the published package
- License: [`LICENSE.anime`](LICENSE.anime) (MIT, Copyright 2019 Julian Garnier)
- Downloaded: 14 November 2026
- Used by: `../animations.js` for the hero entrance, layered-phone reveal and
  scroll reveals; `../motion-flag.js` sets the no-flash initial state.

Do not load it from a CDN: the CSP deliberately allows only same-origin script.
