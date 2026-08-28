# Seed list

A starting blocklist so the extension and app work before the first CI run.

`domains_core.txt` is the high-confidence tier (~148k domains) — the same set
the browser extension ships. The full ~982k list is not committed; it is built
by `blocklist/build.py` and published to the `lists` branch.

Verify this seed before trusting it:

```bash
python3 blocklist/keys.py verify --dist seed --pub blocklist/public_key.hex
```

That will fail until you generate your own keypair and rebuild — the signature
here was made with a throwaway key, and `blocklist/public_key.hex` must be
replaced with your own public key before you ship anything.
