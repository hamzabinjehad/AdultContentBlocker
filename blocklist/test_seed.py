#!/usr/bin/env python3
"""
Tests for the seed bundle — the generated artifacts this repo SHIPS.

    python3 test_seed.py

Two halves. `TestSeedTool` exercises `seed.py` against a throwaway key in a
temporary repo, one failure mode at a time, so each check is known to fire.
`TestCommittedSeed` runs the same `verify` against the real tree, with the key
the clients really pin. That second half is the gate: it is the test that
fails when someone edits `blocklist/terms/` and does not re-cut the seed, when
`extension/seed/terms.json` drifts from `seed/terms.json`, or when the signed
manifest stops describing a file the filter bundles — the exact state that
left the keyword layer silently disabled in every shipped build until now.

Same conventions as `test_build.py`: stdlib unittest, no pytest. Needs
`cryptography`, like `keys.py`.
"""

from __future__ import annotations

import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import seed  # noqa: E402
from terms import compile_terms, serialize_terms  # noqa: E402

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey  # noqa: E402

HERE = Path(__file__).parent
REPO = HERE.parent


class FakeRepo:
    """A minimal repo layout with its own signing key and one signed build.

    Reuses the real `blocklist/terms/` sources so the canonical-terms check
    runs against real data, and stubs the two source files the key is pinned
    in with just the literal `seed.py` looks for.
    """

    def __init__(self, root: Path):
        self.root = root
        self.key = Ed25519PrivateKey.generate()
        self.pub_hex = self.key.public_key().public_bytes_raw().hex()

        shutil.copytree(HERE / "terms", root / "blocklist" / "terms")
        (root / "blocklist" / "public_key.hex").write_text(self.pub_hex + "\n")
        (root / "extension" / "lib").mkdir(parents=True)
        (root / "extension" / "public_key.hex").write_text(self.pub_hex + "\n")
        (root / "extension" / "lib" / "verify.js").write_text(
            f'const PUBLIC_KEY_HEX = "{self.pub_hex}";\n')
        (root / "macos" / "Hisn").mkdir(parents=True)
        (root / "macos" / "Hisn" / "BlocklistStore.swift").write_text(
            f'    public static let productionPublicKeyHex =\n        "{self.pub_hex}"\n')

        self.dist = root / "dist"
        self.dist.mkdir()
        self.write_build(version=4)

    def write_build(self, version: int, drop: set[str] = frozenset(),
                    domain_count: int = 900_000, core: list[str] | None = None,
                    rules_from: list[str] | None = None,
                    core_claimed: int | None = None) -> None:
        """A signed build with every artifact `seed.py` ships. `rules_from`
        builds the browser rules from a different core than the one shipped."""
        terms = compile_terms(self.root / "blocklist" / "terms")
        terms["version"] = version
        if core is None:
            core = ["pornhub.com"] + [f"d{i}.example" for i in range(seed.MIN_CORE_COUNT)]
        rules_core = core if rules_from is None else rules_from
        files = {
            "domains_core.txt": f"# Hisn blocklist version={version}\n" + "\n".join(core) + "\n",
            "terms.json": serialize_terms(terms),
            "dnr_block_rules.json": seed.serialize_dnr(
                seed.dnr_rules(rules_core, "block", limit=max(len(rules_core), 1))),
            "dnr_keyword_rules.json": "[]",
            "domains.txt": "pornhub.com\n",
        }
        artifacts = {}
        for name, text in files.items():
            (self.dist / name).write_text(text, encoding="utf-8")
            if name not in drop:
                artifacts[name] = {"sha256": seed.sha256_file(self.dist / name),
                                   "bytes": len(text.encode())}
        manifest = {"schema": 1, "version": version, "domain_count": domain_count,
                    "core_domain_count": len(core) if core_claimed is None else core_claimed,
                    "artifacts": artifacts}
        self.sign(json.dumps(manifest, indent=2, sort_keys=True).encode())

    def sign(self, manifest_bytes: bytes) -> None:
        (self.dist / "manifest.json").write_bytes(manifest_bytes)
        (self.dist / "manifest.json.sig").write_text(
            self.key.sign(manifest_bytes).hex() + "\n")


class TestSeedTool(unittest.TestCase):

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.repo = FakeRepo(self.tmp)
        seed.sync(self.repo.root, self.repo.dist, log=lambda *_: None)

    def test_synced_bundle_verifies(self):
        self.assertEqual(seed.verify(self.repo.root), [])
        self.assertEqual(seed.current_version(self.repo.root), 4)

    def test_sync_places_every_shipped_file(self):
        for rel, _ in seed.SHIPPED:
            self.assertTrue((self.repo.root / rel).exists(), rel)
        self.assertEqual((self.repo.root / "seed/terms.json").read_bytes(),
                         (self.repo.root / "extension/seed/terms.json").read_bytes())

    def test_sync_refuses_an_unsigned_build(self):
        """An unsigned seed is one the filter refuses, so refuse it here first."""
        (self.repo.dist / "manifest.json.sig").unlink()
        with self.assertRaises(SystemExit):
            seed.sync(self.repo.root, self.repo.dist, log=lambda *_: None)

    # -- the failure modes, one each ---------------------------------------

    def test_artifact_missing_from_manifest_is_reported(self):
        """The bug this tool exists for: a bundled file the manifest never
        mentions loads nowhere, and nothing else says so."""
        self.repo.write_build(version=4, drop={"terms.json"})
        seed.sync(self.repo.root, self.repo.dist, log=lambda *_: None)
        problems = seed.verify(self.repo.root)
        self.assertTrue(any("seed/terms.json" in p and "not in the signed manifest" in p
                            for p in problems), problems)

    def test_drifted_copy_is_reported(self):
        """The two `terms.json` copies must be the same bytes."""
        p = self.repo.root / "extension/seed/terms.json"
        p.write_text(p.read_text() + "\n")
        problems = seed.verify(self.repo.root)
        self.assertTrue(any(p_.startswith("extension/seed/terms.json") and "sha256" in p_
                            for p_ in problems), problems)

    def test_edited_term_sources_are_reported(self):
        """Editing a TSV without re-cutting the seed ships the OLD keyword layer."""
        tsv = self.repo.root / "blocklist" / "terms" / "terms.en.tsv"
        tsv.write_text(tsv.read_text() + "zzqualifiedtestterm\t8\n")
        problems = seed.verify(self.repo.root)
        self.assertTrue(any("differs from what blocklist/terms/ compiles to" in p
                            for p in problems), problems)

    def test_bad_signature_is_reported(self):
        other = Ed25519PrivateKey.generate()
        m = (self.repo.root / "seed/manifest.json").read_bytes()
        (self.repo.root / "seed/manifest.json.sig").write_text(other.sign(m).hex())
        problems = seed.verify(self.repo.root)
        self.assertTrue(any("signature does not verify" in p for p in problems), problems)

    def test_tampered_manifest_is_reported(self):
        """Editing the manifest — even a harmless-looking field — breaks its
        signature. That is the property everything else rests on."""
        p = self.repo.root / "seed/manifest.json"
        m = json.loads(p.read_text())
        m["version"] = 99
        p.write_text(json.dumps(m, indent=2, sort_keys=True))
        problems = seed.verify(self.repo.root)
        self.assertTrue(any("signature does not verify" in p for p in problems), problems)

    def test_clients_pinning_different_keys_is_reported(self):
        """One key in four places. A rotation that misses one client ships a
        list that client rejects, and it looks like a network error."""
        swift = self.repo.root / "macos/Hisn/BlocklistStore.swift"
        swift.write_text(swift.read_text().replace(self.repo.pub_hex, "ab" * 32))
        problems = seed.verify(self.repo.root)
        self.assertTrue(any("different public keys" in p for p in problems), problems)

    def test_seed_below_the_filters_size_floor_is_reported(self):
        """`BlocklistStore.load` throws `tooSmall` under 100k. A seed that
        trips it would be refused on first launch — catch it here."""
        self.repo.write_build(version=4, domain_count=50_000)
        seed.sync(self.repo.root, self.repo.dist, log=lambda *_: None)
        problems = seed.verify(self.repo.root)
        self.assertTrue(any("floor" in p for p in problems), problems)


    def test_browser_rules_from_another_core_are_reported(self):
        """Each file hashing to SOME signed build is not the same as the two
        shipping together: the browser's rules must be this core's."""
        core = ["pornhub.com"] + [f"d{i}.example" for i in range(seed.MIN_CORE_COUNT)]
        self.repo.write_build(version=4, core=core, rules_from=core[:-1000])
        seed.sync(self.repo.root, self.repo.dist, log=lambda *_: None)
        problems = seed.verify(self.repo.root)
        self.assertTrue(any("is not what seed/domains_core.txt builds to" in p
                            for p in problems), problems)

    def test_a_core_tier_below_its_floor_is_reported(self):
        self.repo.write_build(version=4, core=[f"d{i}.example" for i in range(1000)])
        seed.sync(self.repo.root, self.repo.dist, log=lambda *_: None)
        problems = seed.verify(self.repo.root)
        self.assertTrue(any("core floor" in p for p in problems), problems)

    def test_a_core_count_the_manifest_disagrees_with_is_reported(self):
        self.repo.write_build(version=4, core_claimed=123)
        seed.sync(self.repo.root, self.repo.dist, log=lambda *_: None)
        problems = seed.verify(self.repo.root)
        self.assertTrue(any("the signed manifest says 123" in p for p in problems), problems)


class TestCommittedSeed(unittest.TestCase):
    """The gate. Runs against the real tree with the key the clients pin."""

    # Only the publish workflow's `refresh_seed` run sets this, because that
    # run exists to fix exactly the failure this test reports; it re-runs the
    # gate against the re-cut seed before pushing anything. Nothing else
    # should ever set it, and a skip here is printed, never silent.
    @unittest.skipIf(os.environ.get("HISN_SKIP_SEED_GATE") == "1",
                     "HISN_SKIP_SEED_GATE=1: seed gate deferred to the refresh run")
    def test_shipped_bundle_is_verified(self):
        problems = seed.verify(REPO)
        self.assertEqual(problems, [],
                         "\n\nThe shipped seed does not verify:\n  - "
                         + "\n  - ".join(problems)
                         + "\n\nRe-cut it from a signed build:\n"
                         "  python3 blocklist/seed.py sync --build --sign-key <key>\n"
                         "or dispatch the publish workflow with refresh_seed.\n")


if __name__ == "__main__":
    unittest.main(verbosity=2)
