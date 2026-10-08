"""Offline adversarial and failure checks for the local DNS publisher."""

from __future__ import annotations

import fcntl
import hashlib
import json
import os
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

import publish as publisher


class PublisherTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.state = self.root / "state"
        self.key = Ed25519PrivateKey.generate()
        self.public = self.root / "public.hex"
        self.public.write_text(self.key.public_key().public_bytes(
            serialization.Encoding.Raw, serialization.PublicFormat.Raw).hex() + "\n")
        self.safety = self.root / "sources.json"
        self.safety.write_text(json.dumps({
            "never_block_suffix": ["infrastructure.example.net"],
            "never_block_apex": ["shared-cdn.net"],
        }))
        self.bundle_index = 0

    def bundle(self, *, version=1, domains=None, artifact="domains.txt", raw=None,
               count=None, manifest_changes=None):
        self.bundle_index += 1
        path = self.root / f"bundle-{self.bundle_index}"
        path.mkdir()
        if domains is None:
            domains = ["bad.example.net", "tenant.shared-cdn.net"]
        if raw is None:
            raw = ("# Hisn signed fixture\n" + "\n".join(domains) + "\n").encode()
        (path / artifact).write_bytes(raw)
        manifest = {
            "schema": 1, "version": version,
            publisher.ARTIFACTS[artifact]: len(domains) if count is None else count,
            "artifacts": {artifact: {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}},
        }
        manifest.update(manifest_changes or {})
        self.sign(path, json.dumps(manifest, indent=2, sort_keys=True).encode())
        return path

    def sign(self, path, raw):
        (path / "manifest.json").write_bytes(raw)
        (path / "manifest.json.sig").write_text(self.key.sign(raw).hex() + "\n")

    def publish(self, bundle, **kwargs):
        return publisher.publish(bundle, self.state, self.public,
                                 safety_policy=self.safety, min_count=1, **kwargs)

    def snapshot(self):
        return (os.readlink(self.state / "current"),
                (self.state / "current/adguard.txt").read_bytes(),
                (self.state / "current/metadata.json").read_bytes())

    def assert_rejected_unchanged(self, bundle, pattern, **kwargs):
        before = self.snapshot()
        with self.assertRaisesRegex((publisher.Rejected, OSError), pattern):
            self.publish(bundle, **kwargs)
        self.assertEqual(before, self.snapshot())

    def test_publication_verifies_and_preserves_subtree_and_cdn_tenant_semantics(self):
        result = self.publish(self.bundle())
        self.assertEqual("published", result["status"])
        self.assertEqual(2, result["domain_count"])
        rules = (self.state / "current/adguard.txt").read_text()
        self.assertIn("||bad.example.net^\n", rules)
        self.assertIn("||tenant.shared-cdn.net^\n", rules)
        self.assertNotIn("@@", rules)
        self.assertNotIn("||shared-cdn.net^", rules)
        self.assertEqual(result["output_sha256"], hashlib.sha256(rules.encode()).hexdigest())
        metadata = json.loads((self.state / "current/metadata.json").read_text())
        self.assertEqual(result["generation"], metadata["generation"])
        self.assertTrue((self.state / "current/manifest.json.sig").is_file())

    def test_same_signed_generation_is_idempotent(self):
        bundle = self.bundle()
        self.publish(bundle)
        before = self.snapshot()
        result = self.publish(bundle)
        self.assertEqual("unchanged", result["status"])
        self.assertEqual(before, self.snapshot())
        self.assertFalse(any(path.name.startswith(".stage-") for path in (self.state / "generations").iterdir()))

    def test_hosts_export_is_verified_exact_and_idempotent(self):
        bundle = self.bundle()
        result = self.publish(bundle, output_format="hosts")
        output = self.state / "current/hosts.txt"
        rules = output.read_bytes()
        self.assertEqual("hosts-exact-domain-v1", result["format"])
        self.assertIn(b"0.0.0.0 bad.example.net\n", rules)
        self.assertIn(b"0.0.0.0 tenant.shared-cdn.net\n", rules)
        self.assertNotIn(b"0.0.0.0 shared-cdn.net\n", rules)
        self.assertNotIn(b"||", rules)
        self.assertIn(b"wildcard coverage is not guaranteed", rules)
        self.assertEqual(hashlib.sha256(rules).hexdigest(), result["output_sha256"])
        unchanged = self.publish(bundle, output_format="hosts")
        self.assertEqual("unchanged", unchanged["status"])
        self.assertEqual(rules, output.read_bytes())
        for bad in [self.bundle(version=0), self.bundle(version=1, domains=["other.example.net"])]:
            with self.assertRaises(publisher.Rejected):
                self.publish(bad, output_format="hosts")
            self.assertEqual(rules, output.read_bytes())
        with self.assertRaisesRegex(publisher.Rejected, "format differs"):
            self.publish(self.bundle(version=2))
        output.write_bytes(b"0.0.0.0 damaged.example.net\n")
        with self.assertRaisesRegex(publisher.Rejected, "damaged"):
            self.publish(self.bundle(version=2), output_format="hosts")

    def test_hosts_export_rejects_unsigned_tampered_and_unsafe_input(self):
        for mode in ["signature", "artifact", "protected", "injection"]:
            with self.subTest(mode=mode):
                bundle = self.bundle(domains=["infrastructure.example.net"] if mode == "protected"
                                     else ["bad.example.net/path"] if mode == "injection" else None)
                if mode == "signature": (bundle / "manifest.json.sig").write_text("00" * 64)
                if mode == "artifact": (bundle / "domains.txt").write_text("other.example.net\n")
                with self.assertRaises(publisher.Rejected):
                    self.publish(bundle, output_format="hosts")
                self.assertFalse((self.state / "current").exists())

    def test_output_format_cannot_change_existing_adguard_publication(self):
        self.publish(self.bundle())
        self.assert_rejected_unchanged(self.bundle(version=2), "format differs", output_format="hosts")
        self.assert_rejected_unchanged(self.bundle(version=2), "unsupported output", output_format="shell")

    def test_higher_version_switches_all_files_and_retains_previous(self):
        first = self.publish(self.bundle())
        old_rules = (self.state / "current/adguard.txt").read_bytes()
        second = self.publish(self.bundle(version=2, domains=["other.example.net"]))
        self.assertEqual(2, json.loads((self.state / "current/metadata.json").read_text())["version"])
        self.assertEqual(old_rules, (self.state / "generations" / first["generation"] / "adguard.txt").read_bytes())
        self.assertNotEqual(first["generation"], second["generation"])

    def test_wrong_signature_and_tampered_artifact_leave_current_intact(self):
        self.publish(self.bundle())
        wrong_sig = self.bundle(version=2)
        (wrong_sig / "manifest.json.sig").write_text("00" * 64)
        self.assert_rejected_unchanged(wrong_sig, "signature")
        tampered = self.bundle(version=2)
        (tampered / "domains.txt").write_text("new.example.net\n")
        self.assert_rejected_unchanged(tampered, "bytes/SHA-256")

    def test_rollback_and_same_version_equivocation_are_rejected(self):
        self.publish(self.bundle(version=4))
        self.assert_rejected_unchanged(self.bundle(version=3), "rollback")
        self.assert_rejected_unchanged(self.bundle(version=4, domains=["different.example.net"]), "same-version")
        self.assert_rejected_unchanged(self.bundle(version=4, manifest_changes={"built_at": "changed"}), "same-version")

    def test_initial_trusted_version_and_count_floors_are_enforced(self):
        with self.assertRaisesRegex(publisher.Rejected, "configured minimum"):
            self.publish(self.bundle(version=3), min_version=4)
        with self.assertRaisesRegex(publisher.Rejected, "domain count"):
            publisher.publish(self.bundle(), self.state, self.public,
                              safety_policy=self.safety, min_count=3)
        self.assertFalse((self.state / "current").exists())

    def test_signed_count_size_and_digest_must_match_actual_artifact(self):
        self.publish(self.bundle())
        self.assert_rejected_unchanged(self.bundle(version=2, count=3), "signed count")
        for field, value in (("bytes", 1), ("sha256", "a" * 64)):
            bundle = self.bundle(version=2)
            manifest = json.loads((bundle / "manifest.json").read_text())
            manifest["artifacts"]["domains.txt"][field] = value
            self.sign(bundle, json.dumps(manifest).encode())
            self.assert_rejected_unchanged(bundle, "bytes/SHA-256")

    def test_rule_injection_noncanonical_local_and_duplicate_domains_are_rejected(self):
        self.publish(self.bundle())
        bad_lines = ["||bad.example.net^$important", "bad.example.net\r", "BAD.example.net",
                     "*.example.net", "bad.example.net/path", "bad..example.net", "localhost",
                     "bad.local", "bad.example", "bad_thing.example.net", "-bad.example.net",
                     "xn--é.example.net", "a" * 64 + ".example.net"]
        for line in bad_lines:
            with self.subTest(line=line):
                self.assert_rejected_unchanged(self.bundle(version=2, domains=[line]),
                                              "DNS domain|non-ASCII")
        for lines in (["bad.example.net", "bad.example.net"],
                      ["z.example.net", "a.example.net"]):
            self.assert_rejected_unchanged(self.bundle(version=2, domains=lines), "sorted and unique")

    def test_never_block_suffix_apex_and_covering_parent_are_rejected(self):
        self.publish(self.bundle())
        for domain in ("infrastructure.example.net", "a.infrastructure.example.net",
                       "example.net", "shared-cdn.net", "www.shared-cdn.net"):
            with self.subTest(domain=domain):
                self.assert_rejected_unchanged(self.bundle(version=2, domains=[domain]), "protected")

    def test_explicit_core_artifact_checks_core_count_and_pins_tier(self):
        bundle = self.bundle(artifact="domains_core.txt")
        with self.assertRaisesRegex(publisher.Rejected, "does not include domains.txt"):
            self.publish(bundle)
        result = self.publish(bundle, artifact="domains_core.txt")
        self.assertEqual("domains_core.txt", result["artifact"])
        self.assert_rejected_unchanged(self.bundle(version=2), "tier differs")

    def test_duplicate_keys_unsafe_unused_filenames_and_invalid_schema_are_rejected(self):
        self.publish(self.bundle())
        bundle = self.bundle(version=2)
        raw = (bundle / "manifest.json").read_bytes().replace(b'"version": 2', b'"version": 2, "version": 1')
        self.sign(bundle, raw)
        self.assert_rejected_unchanged(bundle, "duplicate JSON key")
        for unsafe in ("../outside", "/tmp/outside", "a/b", "a\\b", "..", "."):
            with self.subTest(unsafe=unsafe):
                bundle = self.bundle(version=2)
                manifest = json.loads((bundle / "manifest.json").read_text())
                manifest["artifacts"][unsafe] = {"bytes": 0, "sha256": "0" * 64}
                self.sign(bundle, json.dumps(manifest).encode())
                self.assert_rejected_unchanged(bundle, "unsafe artifact")
        for changes in ({"schema": True}, {"schema": 2}, {"version": "2"}, {"domain_count": True}):
            self.assert_rejected_unchanged(self.bundle(version=2, manifest_changes=changes), "schema|integer")

    def test_input_symlinks_and_special_files_are_not_followed(self):
        self.publish(self.bundle())
        for name in ("manifest.json", "manifest.json.sig", "domains.txt"):
            bundle = self.bundle(version=2)
            outside = self.root / "outside"
            outside.write_bytes((bundle / name).read_bytes())
            (bundle / name).unlink()
            (bundle / name).symlink_to(outside)
            self.assert_rejected_unchanged(bundle, ".")
        bundle = self.bundle(version=2)
        (bundle / "domains.txt").unlink()
        os.mkfifo(bundle / "domains.txt")
        self.assert_rejected_unchanged(bundle, "regular file")

    def test_unsafe_output_symlinks_and_permissions_are_rejected(self):
        outside = self.root / "outside-state"
        outside.mkdir()
        self.state.symlink_to(outside, target_is_directory=True)
        with self.assertRaises(OSError):
            self.publish(self.bundle())
        self.state.unlink()
        self.state.mkdir()
        self.state.chmod(0o777)
        with self.assertRaisesRegex(publisher.Rejected, "group/world"):
            self.publish(self.bundle())
        self.state.chmod(0o755)
        (self.state / "generations").symlink_to(outside, target_is_directory=True)
        with self.assertRaises(OSError):
            self.publish(self.bundle())
        self.assertEqual([], list(outside.iterdir()))

    def test_arbitrary_current_target_and_symlinked_generation_are_rejected(self):
        self.publish(self.bundle())
        target = os.readlink(self.state / "current")
        (self.state / "current").unlink()
        (self.state / "current").symlink_to("../../outside")
        with self.assertRaisesRegex(publisher.Rejected, "unsafe generation"):
            self.publish(self.bundle(version=2))
        (self.state / "current").unlink()
        (self.state / "current").symlink_to(target)
        original = self.state / target
        moved = self.root / "moved"
        original.rename(moved)
        original.symlink_to(moved, target_is_directory=True)
        with self.assertRaises(OSError):
            self.publish(self.bundle(version=2))

    def test_key_change_and_same_version_safety_change_do_not_reset_trust(self):
        self.publish(self.bundle())
        before = self.snapshot()
        self.key = Ed25519PrivateKey.generate()
        self.public.write_text(self.key.public_key().public_bytes(
            serialization.Encoding.Raw, serialization.PublicFormat.Raw).hex())
        self.assert_rejected_unchanged(self.bundle(version=2), "pinned key differs")
        self.assertEqual(before, self.snapshot())

    def test_changed_safety_policy_requires_a_new_signed_version(self):
        bundle = self.bundle()
        self.publish(bundle)
        policy = json.loads(self.safety.read_text())
        policy["never_block_apex"].append("other-cdn.net")
        self.safety.write_text(json.dumps(policy))
        self.assert_rejected_unchanged(bundle, "same-version")
        self.assertEqual("published", self.publish(self.bundle(version=2))["status"])

    def test_damaged_current_metadata_or_rules_never_reinitialize_version(self):
        self.publish(self.bundle(version=4))
        metadata = self.state / "current/metadata.json"
        original = metadata.read_bytes()
        metadata.write_bytes(b"not JSON")
        with self.assertRaisesRegex(publisher.Rejected, "saved metadata"):
            self.publish(self.bundle(version=3))
        metadata.write_bytes(original)
        (self.state / "current/adguard.txt").write_bytes(b"! truncated")
        with self.assertRaisesRegex(publisher.Rejected, "damaged"):
            self.publish(self.bundle(version=5))

    def test_oversized_content_is_rejected_without_replacing_current(self):
        self.publish(self.bundle())
        self.assert_rejected_unchanged(self.bundle(version=2), "byte limit", max_bytes=1)
        self.assert_rejected_unchanged(self.bundle(version=2, raw=b"#" + b"a" * publisher.MAX_LINE + b"\n"),
                                      "oversized")

    def test_failed_generation_write_keeps_last_good_and_removes_stage(self):
        self.publish(self.bundle())
        before = self.snapshot()
        with mock.patch.object(publisher, "write_file", side_effect=OSError("disk full")):
            with self.assertRaisesRegex(OSError, "disk full"):
                self.publish(self.bundle(version=2))
        self.assertEqual(before, self.snapshot())
        self.assertFalse(any(path.name.startswith(".stage-") for path in (self.state / "generations").iterdir()))

    def test_failed_pointer_swap_and_directory_fsync_restore_last_good(self):
        self.publish(self.bundle())
        before = self.snapshot()
        with mock.patch.object(publisher.os, "replace", side_effect=OSError("swap failed")):
            with self.assertRaisesRegex(OSError, "swap failed"):
                self.publish(self.bundle(version=2))
        self.assertEqual(before, self.snapshot())
        real_fsync = os.fsync
        state_inode = self.state.stat().st_ino
        failed = False

        def fail_commit_once(fd):
            nonlocal failed
            info = os.fstat(fd)
            if (not failed and stat.S_ISDIR(info.st_mode) and info.st_ino == state_inode and
                    os.readlink(self.state / "current") != before[0]):
                failed = True
                raise OSError("durability failed")
            real_fsync(fd)

        with mock.patch.object(publisher.os, "fsync", side_effect=fail_commit_once):
            with self.assertRaisesRegex(OSError, "durability failed"):
                self.publish(self.bundle(version=2))
        self.assertTrue(failed)
        self.assertEqual(before, self.snapshot())

    def test_lock_timeout_does_not_change_published_rules(self):
        self.publish(self.bundle())
        before = self.snapshot()
        with (self.state / ".publish.lock").open("rb") as handle:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaisesRegex(publisher.Rejected, "update lock"):
                self.publish(self.bundle(version=2), lock_timeout=0)
        self.assertEqual(before, self.snapshot())

    def test_concurrent_processes_cannot_publish_a_rollback(self):
        self.publish(self.bundle())
        processes = []
        for version in (2, 3):
            command = [sys.executable, str(Path(publisher.__file__).resolve()),
                       "--bundle", str(self.bundle(version=version)), "--state", str(self.state),
                       "--public-key", str(self.public), "--safety-policy", str(self.safety),
                       "--min-count", "1"]
            processes.append(subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True))
        for process in processes:
            stdout, stderr = process.communicate(timeout=15)
            self.assertIn(process.returncode, (0, 1), stderr)
            if process.returncode:
                self.assertIn("rollback rejected", stderr)
            else:
                self.assertEqual("published", json.loads(stdout)["status"])
        self.assertEqual(3, json.loads((self.state / "current/metadata.json").read_text())["version"])

    def test_real_repository_seed_is_consumable_without_other_signed_artifacts(self):
        repository = Path(publisher.__file__).resolve().parent.parent
        result = publisher.publish(repository / "seed", self.state,
                                   repository / "blocklist/public_key.hex",
                                   artifact="domains_core.txt")
        signed = json.loads((repository / "seed/manifest.json").read_text())
        self.assertEqual(signed["core_domain_count"], result["domain_count"])
        self.assertEqual(signed["version"], result["version"])
        self.assertEqual("domains_core.txt", result["artifact"])


if __name__ == "__main__":
    unittest.main()
