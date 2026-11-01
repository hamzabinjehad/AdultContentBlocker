# Seed bundle

The starting list and keyword layer the filter ships with, so a machine that
has never completed an update — a first launch, or one with no network —
enforces something rather than nothing.

```
manifest.json       the signed manifest of the build this was cut from
manifest.json.sig   its Ed25519 signature
domains_core.txt    the budgeted core tier (239,885 domains in v5)
terms.json          the keyword layer, for domains registered since the build
```

The core is the four hand-picked adult sources plus the `core_fill` selection:
domains that both `blocklistproject-porn` and `oisd-nsfw` carry and that also
carry an adult TLD, token or IDN label. The signed manifest records how many
were selected and deferred. See the
[coverage pass record](../docs/COVERAGE_PASS_2026-10-30.md).

The extension carries the same build's `terms.json` and static rulesets under
`extension/seed/` and `extension/rules/`. All of it is placed by one command
and checked by one command:

```bash
python3 blocklist/seed.py verify                                       # CI and the tests run this
python3 blocklist/seed.py sync --build --sign-key keys/blocklist_ed25519.pem
```

The filter verifies the manifest's signature with the public key pinned in
`BlocklistStore.swift`, then checks each artifact's SHA-256 against it, on
exactly the path a downloaded list takes. A file the manifest does not
describe is a file the filter will not load — which is why `verify` fails on
that state, and why editing `blocklist/terms/` is not finished until the seed
is re-cut. Without the private key on your machine, dispatch the *Build and
publish blocklist* workflow with **refresh_seed** ticked and CI commits the
re-signed bundle to your branch.

The full 5,276,448-domain list is not committed; `blocklist/build.py` builds it and CI
publishes it to the `lists` branch. The version in this manifest is also where
CI starts its counter when nothing has been published yet, so a fresh install
never rejects the first published list as a rollback.
