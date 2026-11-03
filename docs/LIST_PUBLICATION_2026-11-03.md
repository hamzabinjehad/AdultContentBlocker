# Blocklist v6 publication — 3 November 2026

The first signed list generation is published at
`https://raw.githubusercontent.com/hamzabinjehad/AdultContentBlocker/lists/manifest.json`.
This closes the "no `lists` branch on origin" gap in
[1.0 readiness](1.0_READINESS.md). It does not claim a scheduled publication: the
GitHub `list-signing` environment is still unconfigured, so this generation was
built and published once with the matching local Ed25519 key.

## Result

| Field | Value |
| --- | --- |
| Version | **6** (seed remains v5) |
| Built | 2026-11-03T15:39:23+00:00 |
| Domains | **5,274,046** |
| Core tier | **239,885** (240,000 capacity; 115 domain headroom) |
| Terms | 5,831 (ar 4,100, ar-latn 1,447, de 100, en 145, es 16, fr 10, it 9, pt 4) |
| Host terms | 116 |
| Browser DNR rules | 480 |
| Published files | 8, 34 MB total |
| Branch commit | `50ae455c2b1669a1a1857f65b7e477e69c4c48b0` |
| Manifest SHA-256 | `c2d5b38e5afc9aad7d44630b487b0590118eb251f6bb78bfa42bea8393b9be1e` |

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `manifest.json` | 2,816 | `c2d5b38e5afc9aad7d44630b487b0590118eb251f6bb78bfa42bea8393b9be1e` |
| `manifest.json.sig` | 129 | `a709271ddc10b7c553f65f8b6ed43ff0b16d2eda4c2663e3e3dbfdb4356495f6` |
| `domains_core.txt` | 4,314,769 | `b9208fc0debade036f5265c43613feb1e0fdebafd2858410c07be925d2ff1e06` |
| `domains.packed.deflate` | 21,360,566 | `e50d841e17ca9b684d36f5fb5d6bb6405844100c352ed999aa281f7c8da83e5e` |
| `dnr_block_rules.json` | 9,676,549 | `976712e16f160e4a7d3e3fb395af69572659132f722eacf3e55118ffc08f0b97` |
| `dnr_keyword_rules.json` | 79,908 | `efb2750c8e92e08992cd1aa47d5f16825bc6938a1fa2e93172b4ccc39a55e45d` |
| `domains.index` | 8,192 | `7d2c7ac4888bfd75cd5f56e8d61f69595121183afc81556c876732fd3782c62f` |
| `terms.json` | 288,545 | `870f81988ccc289386b2463bfa9bb8338ffd73f890a9f2cf0b1d0fa53e856b6a` |

The published manifest and signature were downloaded again over HTTPS and are
byte-for-byte identical to the signed candidate. `curl -I` returned **HTTP/2
200**. The public key is `blocklist/public_key.hex`
(`eb6751a0…e59ede`); the local private key's derived public half matches it.

## What ran

1. `python3 blocklist/build.py --sources blocklist/sources.json --out <candidate>
   --sign-key keys/blocklist_ed25519.pem`
2. `python3 blocklist/keys.py verify --dist <candidate> --pub blocklist/public_key.hex`
   — **signature OK**, all eight published artifacts verified.
3. `python3 blocklist/publish_guard.py --previous seed/manifest.json --new
   <candidate>/manifest.json --sources blocklist/sources.json --dist <candidate>`
   — **v5 → v6, 5,274,046 domains, 239,885 core — OK**.
4. `python3 blocklist/test_build.py` in an isolated worktree with the candidate as
   `dist/` — **43 tests OK**, including the real-artifact checks: known adult
   domains blocked, critical infrastructure not blocked, and the manifest covers
   every artifact.
5. The published branch was created as an orphan containing only the eight files
   clients download; `domains.txt` and `domains.packed` (129 MB each) are excluded,
   as in the workflow.

### The guard did its job first

The first build (15:37 UTC) timed out fetching `ut1-mixed-adult` and `ut1-adult`
and produced only 1,273,954 domains. Its signature was valid, but
`publish_guard.py` **refused** it: `domain_count fell from 5,276,448 to
1273954`. Nothing was published from that build. The retry (15:39 UTC) fetched
all ten sources, including `ut1-adult` (4,597,564 entries) and `ut1-mixed-adult`
(149), and passed. This record is intentionally explicit: a valid signature does
not make a degraded list safe to ship.

## What this means per client

- **Chromium extension:** `worker/lists.js` fetches `lists/manifest.json` and
  `.sig`, verifies Ed25519 in WebCrypto, rejects a lower version, validates the
  vocabulary and applies the 480 DNR rules. The URL and signature are verified;
  no installed-browser update run is claimed.
- **Mac:** `ListUpdater` fetches the generation and the filter picks up the full
  list. No installed signed build or device run is claimed.
- **iPhone/iPad and Android:** unchanged. They bundle the signed seed v5; this
  branch does not update them without a new app build. The APK-only Android
  decision and the iOS bundled-list model remain as documented.
- **Seed:** still v5 (239,885 core domains). It was not re-cut, so no bundled
  artifact or hash in the repository changed.

## Limits and next owner action

- The daily GitHub workflow still cannot publish: its `list-signing`
  environment secret is not configured. This manual generation proves the
  artifacts and the branch; it does not prove the scheduled path. Configure
  `BLOCKLIST_SIGNING_KEY` in the main-only `list-signing` environment (see
  [SETUP.md](SETUP.md)) before relying on daily freshness.
- The upstream UT1 host was intermittently slow; the retry succeeded with the
  same 120-second fetch deadline. A future run that times out will fail the
  guard and publish nothing, which is the intended safe outcome.
- No client install, browser profile, phone or Mac filter was exercised against
  v6. Do not extend this record into a coverage, latency or device claim.
