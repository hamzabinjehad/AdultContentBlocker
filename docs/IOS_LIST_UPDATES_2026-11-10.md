# iPhone/iPad: signed list generations, 10 November 2026

Status: implementation in the repository; simulator-tested with a synthetic
signed generation. No device, real network download or signed IPA was used.

## Why

The iOS DNS filter could only carry the list built into the app. The published
generation on 10 November was v6 with 239,885 core domains, while the bundled
seed is v5 with 236,586; a newly listed site stayed blocked only on the Mac,
the browser and Android until the next iOS app build. The DNS proxy already
reads a verified `dns-metadata.json`/`dns-domains.txt`/`dns-host-terms.json`
directory, so the update path can write that same shape.

## Trust boundary

[ListGeneration.swift](../ios/Shared/ListGeneration.swift) verifies:

- the manifest's exact bytes against the pinned Ed25519 key (the Python suite
  compares that key with `blocklist/public_key.hex`);
- the `domains_core.txt` and `terms.json` sizes and SHA-256 values from the
  signed manifest;
- one core tier between 1,000 and 250,000 domains;
- the domain index is sorted and parses, and its count matches the manifest;
- the host-term payload has schema 1, the generation's version, and compiles.

Any failure rejects the whole generation. There is no unsigned fallback path
and no partial install.

## Storage and activation

[ListGenerationStore.swift](../ios/Shared/ListGenerationStore.swift) writes a
staging directory in the App Group, writes the three files the proxy already
loads plus the manifest/signature, runs `DNSFilterAssets.load` on exactly that
directory, and only then renames it and moves the pointer. The floor is
monotonic: an older generation is refused after a restart too. The active
generation and one predecessor are kept.

[HisnDNSProxy/DNSProxyProvider.swift](../ios/HisnDNSProxy/DNSProxyProvider.swift)
tries the active generation first and falls back to the signed bundle if the
generation is missing or damaged. It never starts unfiltered.

## Download path

[ListUpdater.swift](../ios/Hisn/ListUpdater.swift) runs when the app refreshes
with DNS enabled, at most once a day. It fetches the manifest, signature,
domains and terms over HTTPS with bounded sizes, verifies them, and installs the
generation. It stores only a coarse error type and the versions, never a queried
name or response body. There is no analytics or server of Hisn's.

The proxy reads the active generation when it starts. A download made while the
proxy is running applies on its next start or a DNS toggle; this pass has no
live engine swap.

## Verification

| Check | Result |
| --- | --- |
| `ListGenerationTests` | Accepts a generated signed v7 fixture; rejects a flipped signature and a changed domain artifact; store publishes a generation the proxy loader reads; v6 is refused after v7 |
| `ios/test_list_generation.py` | Pinned key equals `blocklist/public_key.hex`; update URL matches Android; the store uses the DNS App Group |
| `./test.sh ios` | Pass, 279 simulator tests and the Python suites |

## Limits

- No iPhone/iPad device, real generation download or signed IPA exercised the
  path. The fixture uses a separate test key.
- The proxy must start again before a download can filter. A future pass can
  add a controlled reload.
- The pinned key rotation still requires an app update, as on Android.
- The app downloads about 4.6 MB once a day when DNS is enabled; offline or a
  failed verification keeps the previous generation.
