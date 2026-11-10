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
generation is missing or damaged. It never starts unfiltered. While it runs, a
directory source watches the App Group pointer; when a newer generation is
committed, the proxy loads it off the query path and swaps the immutable engine
under a lock. Queries already in flight finish against the previous snapshot.

## Safari blockers

The six Safari content blockers read the same active generation. After the app
installs a generation it prebuilds six shards of at most 40,000 rules each,
puts the four sponsored adult TLDs in the last shard, and writes a readiness
marker only after all six files are durable. Each blocker identifies its 1-based
shard from its bundle identifier (`app.hisn.mobile.blockerN`) and gives that
file to Safari without parsing or building 40,000 rules in the extension. A
missing, damaged, partial or mismatched set falls back to the `rules.json`
compiled into each bundle, so Safari blocking never disappears because an
update failed. The app reloads all six blockers after a successful install.

The blocker targets now carry the Hisn App Group entitlement. A signed direct
build needs that group in the provisioning profile for each of the six blocker
bundle identifiers; an unprovisioned profile will fail at install even though
the simulator build compiles.

## The published header is not a domain

The first real fetch exposed a bug the synthetic fixture could not: the
published `domains_core.txt` starts with two generated `#` comment lines. The
iOS verifier checked the signed bytes and hash correctly, then handed the whole
file to `DomainIndex`, counted 239,887 entries against the manifest's 239,885,
and rejected the real v6 generation. Android and Mac already skip comments;
iOS did not.

`ListGeneration.verify` now canonicalizes the verified bytes before parsing and
storing: blank lines and `#` comments are dropped, CRLF is normalized, and one
trailing newline is kept. The signed hash still covers the original bytes. The
canonical file is what `ListGenerationStore`, the DNS proxy and the Safari shard
builder read, so all three agree on 239,885 domains.

`ListGenerationTests` now has a signed fixture with the same two-line header and
asserts that it verifies to 1,100 domains without a `#` line.

## Download path

[ListUpdater.swift](../ios/Hisn/ListUpdater.swift) runs when the app refreshes
with DNS enabled, at most once a day. It fetches the manifest, signature,
domains and terms over HTTPS with bounded sizes, verifies them, and installs the
generation. It stores only a coarse error type and the versions, never a queried
name or response body. There is no analytics or server of Hisn's.

The proxy reads the active generation when it starts and watches the App Group
pointer, so a download made while it is running is applied without a restart or
a DNS toggle. The reload parses the list on a private queue and swaps engines;
no query waits for it.

## Verification

| Check | Result |
| --- | --- |
| `ListGenerationTests` | Accepts a generated signed v7 fixture and a fixture with the published comment header; rejects a flipped signature and a changed domain artifact; store publishes a generation the proxy loader reads; v6 is refused after v7; v8 moves the pointer and floor the proxy watches |
| Real published generation | Host-verified with the iOS verifier: v6, 239,885 core domains, terms v6, pinned Ed25519 key; the artifact hashes/sizes match the published manifest |
| `ios/test_list_generation.py` | Pinned key equals `blocklist/public_key.hex`; update URL matches Android; the store uses the DNS App Group |
| `SafariGenerationRulesTests` | Pass; shard split, four guard rules on the last shard, metadata hash check, prebuilt shards and marker-gated bundle fallback |
| `./test.sh ios` | Pass, simulator tests and the Python suites |

## Limits

- The real v6 generation was fetched on the host and verified with the iOS
  verifier; no iPhone/iPad, signed IPA or Network Extension fetched it. The fixture uses a separate test key.
- The directory watcher is compiled and unit-tested at the store/pointer level,
  but no device has exercised a live swap while the proxy runs.
- No Safari blocker has loaded a downloaded generation on a device. The
  prebuild, readiness marker and metadata check are unit-tested; real Safari
  load timing and memory are not measured.
- The pinned key rotation still requires an app update, as on Android.
- The app downloads about 4.6 MB once a day when DNS is enabled; offline or a
  failed verification keeps the previous generation.
