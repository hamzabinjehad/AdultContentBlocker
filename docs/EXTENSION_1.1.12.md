# Hisn extension 1.1.12 — compound, normalisation and generated URL layers

Local development candidate, 26 October 2026. Not a GitHub publication, native
app installation or confirmation of browser/phone activation. No signed seed or
published-list artifact changed in this pass.

## Changes

- Match the no-space spelling of every reviewed multi-word term. `nudephotos`,
  `sexvideos` and `اباحيجديد` are one token, so the exact-token scorer used to
  miss them. A glued alias is derived at compile time from a phrase already in
  the vocabulary, shares that phrase's weight and occurrence cap, is dropped
  when the phrase is ignored, and is never created when the glued spelling is
  already a reviewed term. No substring matching, stemming or edit distance was
  added.
- Fold the Persian/Urdu spellings of the same Arabic letters: farsi yeh (ی),
  keheh (ک), yeh barree, heh goal, teh marbuta goal and noon ghunna. A page can
  visibly write `سکسی` where the list has `سكسي`; before this the layer matched
  nothing and reported nothing. Peh, tcheh, jeh and gaf are different letters
  and are deliberately not folded. The shared `normalize_cases.json` fixture and
  all three implementations (Python, JavaScript, Swift) were updated together;
  `seed.py verify` confirms the compiled list is unchanged.
- Add a reviewed text-evasion vocabulary for strong single-token brand terms
  (`p0rnhub`, `0nlyf4ns`, `s3xch4t`, `m1lf`, …). The lane still requires a
  signed term at weight 8, shares the canonical occurrence cap, and cannot
  restore an ignored term. The local-promotion evidence mask was widened from
  one byte to 32 bits: with more than eight reviewed terms a `Uint8Array`
  silently truncated the evidence and the lane stopped seeing the later entries.
- Generate `rules/evasion.json` (30 rules) from
  `blocklist/evasion_aliases.json` and the signed host vocabulary. `p0rn`- and
  `s3x`-spelled hosts and queries are covered, a host-token alias also drops
  embedded media, and every rule carries the reference-site rail. The generator
  refuses aliases for terms that are not in the signed list.
- Generate `rules/encoded_queries.json` (63 rules) for explicit Arabic search
  queries. Chrome sees a URI, so Arabic terms cannot live in a keyword rule;
  these rules match strong, single-token signed Arabic terms in a recognised
  query parameter only. Paths are out of scope, and the word-ending check keeps
  `سكس` from matching `سكسونيا` (Saxony). A reviewed exclusion file removes
  words that are ordinary in another context — `طابون`/`طبون` (taboon bread)
  and `الدعاره`/`مومسات` (prostitution vocabulary used by news and research) —
  from the query layer while leaving them in the context-aware page-text lane.
- Split the 1,286-line service worker into `worker/state.js`, `updates.js`,
  `rules.js`, `scanners.js`, `tabs.js`, `native.js`, `lists.js` and
  `inspection.js`. `background.js` is now the entry point and message table
  only. `worker-layout.test.js` guards the split, and `test_build.py` reads the
  update endpoint from its new home.
- The real-browser DNR harness now validates every static regex with
  `isRegexSupported` before matching anything, and covers obfuscated hosts, a
  longer innocuous label, embedded-media blocking, and the Arabic query
  boundaries.

## Validation scope

- `./test.sh extension`: all JavaScript suites, package validation and the
  evaluation corpus at sensitivity 50, 80 and 100.
- `./test.sh python seed`: blocklist, profile, network, packaging and mobile
  Safari suites; signed seed verification.
- `./test.sh macos`: 490 Swift tests, including the shared normalisation
  fixture.
- `extension/test/browser/dnr.sh`: a real headless Chromium loads the extension;
  the host contract, SafeSearch, Yandex/viewer policy, keyword, path,
  obfuscation, encoded-query and embedded-media cases all pass.
- New focused suites: 23 compound-term checks, 117 evasion-rule checks, 33
  encoded-query checks, 46 worker-layout checks, 54 scanner checks, and 164
  text-evasion/context checks.

The evaluation corpus is a small labelled regression set, not a measurement of
real-world recall or false-positive rates. The alias tables were reviewed by
hand; the generator and the fixtures bound what they can do, not how often the
underlying vocabulary is right.

## Remaining work

Whitespace-separated letter evasions remain out of scope by design. Broader
context calibration, more site-specific feed adapters, physical iPhone/iPad
acceptance and independent measurement of real-world misses remain open. The
encoded-query layer targets search parameters only; a provider that encodes a
query in a path or a POST body is outside what DNR can see.

For an existing unpacked Chromium installation, reload Hisn in the browser's
extensions page and refresh webpages after updating the files. Safari requires
a rebuilt/signed containing app and user-enabled website access; staging alone
does not install it. Do not remove a working extension or relax an active
commitment to apply an update.
