# Coverage pass — seed v5, 30 October 2026

This records the largest bundled-list change since the project started: the
core tier grows from **159,768** to **239,885 domains** on every surface that
carries it. It is a repository and simulator result, not a signed release or a
device-acceptance result.

## What changed

| Surface | Before | After |
| --- | --- | --- |
| Browser extension | 159,768 domains, 320 static DNR rules | **239,885 domains, 480 static DNR rules** |
| Safari on iPhone/iPad | 4 × 40,000 = 160,000 rules | **6 × 40,000 = 240,000 rules** |
| Android DNS seed | 159,768 domains | **239,885 in the signed seed; 239,880 loaded after the client's IP-entry exclusion** |
| Mac core fallback | 159,768 domains | **239,885 domains** (the full 5,276,448-domain list still arrives only with a published generation) |
| Full Mac/DNS list | 5,248,731 domains | **5,276,448 domains** |
| Keyword layer | 5,831 terms | unchanged |

Core growth is 80,117 domains (+50.1%). The full-list change is the upstream
refresh, not a selection change.

## How the 114,691 new core domains were chosen

The four hand-picked core sources alone collapse to 125,309 domains. A
**`core_fill`** selection adds domains from two independently curated,
permissively licensed sources:

- `blocklistproject-porn` — Unlicense;
- `oisd-nsfw` — free for personal and commercial use.

A domain is eligible only when all of these hold:

1. it appears in **both** fill sources (`min_sources: 2`);
2. it is not already covered by a core domain or one of its parents;
3. it is not removed by the never-block safety rail; and
4. it carries at least one adult signal: an adult TLD (`.adult`, `.cam`,
   `.porn`, `.sex`, `.xxx`), a reviewed adult token, or an IDN (`xn--`) label.

The consensus requirement is the precision gate. The signal is a selection
aid, not a block decision on its own: every selected domain was already on two
adult lists.

Of 115,952 eligible domains, **114,691** fit. **1,261** were deferred by the
240,000 cap. An IDN label outranks a plain ASCII token in the deterministic
order, so international candidates are not squeezed out by English-token long
tail; after that correction, every deferred candidate is a plain-token domain
and zero IDN-only domains were deferred. The signed manifest records the number
rather than hiding a truncation. Raising the cap means adding Safari parts, and
that is a separate decision.

The mixed ad/tracker snapshot (`stevenblack-laptop`) moved from the **core**
tier to **extended**. It remains in the full Mac/DNS list, but the bundled
slots now go to adult domains instead of spending roughly a sixth of the
Safari budget on non-adult hosts.

The UT1 academic lists stay **extended only**. Their terms are
non-commercial; they are not used as fill sources and the bundled core does not
contain domains selected from them.

## Capacity, not an accuracy claim

Six Safari parts of 40,000 rules each are used instead of raising a part's
size. The per-part size is the one already shipped and tested; adding parts is
the measured change. The app verifies every part's count, version and SHA-256.
`build.py`, `prepare_rules.py`, `MobileProtectionPolicy.swift` and
`script/coverage.py` all assert the same 240,000 cap.

This page does **not** claim better classification. It claims more listed adult
domains are bundled where the old core stopped. Page text, images, video,
in-app web views, encrypted native-app traffic, another browser and another
device remain outside a domain list.

## Verification on this machine

| Suite | Result |
| --- | --- |
| `./test.sh python` | **PASS** — 130 blocklist, 80 profile, 45 network, 45 unpacking, 38 mobile-rule tests, plus runner/version checks |
| `./test.sh seed` | **PASS** — v5, 5 shipped artifacts match the signed manifest |
| `./test.sh extension` | **PASS** — all JS suites, package check, evaluation corpus |
| `./test.sh release` | **PASS** — 33 installer/configuration tests |
| `./test.sh macos` | **PASS** — 498 tests, 536 strings with Arabic |
| `./test.sh ios` | **PASS** — 183 hosted tests, `** TEST SUCCEEDED **`, iPhone 17 / iOS 26.5 simulator |
| Android `sh test-core.sh` | **PASS** — 239,880 seed domains loaded; text/DNS/managed/watchdog suites |
| Android `assembleDebug assembleRelease bundleRelease lintDebug` | **PASS** — lint debug, 0 failures |

Artifacts produced locally, unsigned:

- Extension **1.2.2**: `dist/hisn-extension-1.2.2.zip`, 90 files, 5.9 MB,
  SHA-256 `6db421dccec745218f178d717bfbac9a948c1e91752a64dde863afce66fc24bd`.
- Android APK: `0dc0f6264daa3585e1bb7931ec29e9c82e477904ebba67484772e991c52eccde`.
- Android AAB: `6c5ce55c22f5a4a88815984491d987571d724e26df3ba1458545d40b7825a3a0`.

## What is not proven

- No signed or notarized Mac package; the Mac app still bundles the 239,885
  core tier, not the 5,276,448 full list.
- No physical iPhone/iPad or Android device run, no App Store/TestFlight or
  Play upload, and no signed production key.
- No Chrome Web Store listing or managed force-install.
- No published `lists` branch. Every client is still on the bundled seed.
- Six 40,000-rule Safari parts compile in the simulator; that is not proof
  every device enables six extensions or that a page outside the list is
  blocked.
- The 1,261 deferred domains are not blocked by the bundled tier. They remain
  in the full Mac/DNS build where that build is available.
- Counts are not a quality measure. The 41-case synthetic text corpus and the
  unmeasured real-world false-positive rate are unchanged by this pass.

## Reproduce

```sh
python3 blocklist/build.py --out /tmp/hisn-dist-v5 --version 5 \
  --sign-key keys/blocklist_ed25519.pem
python3 blocklist/seed.py sync --dist /tmp/hisn-dist-v5
python3 script/coverage.py
python3 script/notices.py
```

`blocklist/sources.json` holds the core capacity, fill sources, minimum source
count and the adult TLD/token lists. `seed/manifest.json` records the selected
and deferred counts for the shipped generation.
