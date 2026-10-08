import hashlib
import json
import re
import tempfile
import unittest
import plistlib
from pathlib import Path
from unittest.mock import patch

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

import prepare_rules as rules
from network.publish import Rejected


class SafariRuleTests(unittest.TestCase):
    def test_removal_authorization_contract(self):
        controller = (rules.ROOT / "ios/Hisn/ProtectionController.swift").read_text()
        self.assertIn("requestAuthorization(for: .child)", controller)
        guardian = controller.split("func requestGuardianProtection()", 1)[1].split("func refresh()", 1)[0]
        self.assertNotIn("requestAuthorization(for: .individual)", guardian)
        self.assertIn("func enablePersonalScreenTime()", controller)
        self.assertNotIn("revokeAuthorization(", controller)
        self.assertNotIn("UserDefaults", controller)
        with (rules.ROOT / "ios/Hisn/Hisn.entitlements").open("rb") as source:
            self.assertIs(plistlib.load(source)["com.apple.developer.family-controls"], True)

    def fixture(self, directory, domains):
        key = Ed25519PrivateKey.generate()
        public = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        (directory / "key.hex").write_text(public.hex())
        raw = ("# signed fixture\n" + "\n".join(domains) + "\n").encode()
        (directory / "domains_core.txt").write_bytes(raw)
        manifest = json.dumps({"schema": 1, "version": 7, "core_domain_count": len(domains),
            "artifacts": {"domains_core.txt": {"sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)}}}).encode()
        (directory / "manifest.json").write_bytes(manifest)
        (directory / "manifest.json.sig").write_text(key.sign(manifest).hex())
        return directory / "key.hex"

    def test_signed_input_checks_and_comments(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            key = self.fixture(directory, ["a.example.net", "b.example.net"])
            self.assertEqual(rules.verified_domains(directory, key), (["a.example.net", "b.example.net"], 7))
            (directory / "domains_core.txt").write_text("a.example.net\nevil.example.net\n")
            with self.assertRaises(Rejected): rules.verified_domains(directory, key)

    def test_bad_signature_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            key = self.fixture(directory, ["a.example.net"])
            (directory / "manifest.json.sig").write_text("00" * 64)
            with self.assertRaises(Rejected): rules.verified_domains(directory, key)

    def test_signed_invalid_duplicate_unsorted_or_protected_inputs_rejected(self):
        for domains in [["apple.com"], ["cloudfront.net"], ["a.example.net", "a.example.net"],
                        ["b.example.net", "a.example.net"], ["a.example.net\r"], ["UPPER.example.net"], ["a.example.net/path"]]:
            with self.subTest(domains=domains), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                key = self.fixture(directory, domains)
                with self.assertRaises(Rejected): rules.verified_domains(directory, key)

    def test_cdn_tenant_remains_blockable(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            key = self.fixture(directory, ["tenant.cloudfront.net"])
            self.assertEqual(rules.verified_domains(directory, key)[0], ["tenant.cloudfront.net"])

    def test_capacity_never_silently_truncates(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(rules, "RULE_LIMIT", 1):
            directory = Path(temporary)
            key = self.fixture(directory, [f"site{i}.example.net" for i in range(5)])
            with self.assertRaises(Rejected): rules.verified_domains(directory, key)

    def test_shards_are_complete_disjoint_and_deterministic(self):
        domains = [f"site{i}.example.net" for i in range(7)]
        with patch.object(rules, "RULE_LIMIT", 2):
            combined = []
            for part in range(1, 5):
                raw = rules.compile_part(domains, part)
                self.assertEqual(raw, rules.compile_part(domains, part))
                combined.extend(json.loads(raw))
            self.assertEqual(combined, [rules.rule(domain) for domain in domains])
            for part in [0, 5]:
                with self.assertRaises(Rejected): rules.compile_part(domains, part)
            with self.assertRaises(Rejected): rules.compile_part(["a.example.net"], 2)

    def test_url_boundaries_against_shared_fixtures(self):
        cases = json.loads((rules.ROOT / "ios/fixtures/safari_cases.json").read_text())
        for case in cases:
            pattern = rules.rule(case["domain"])["trigger"]["url-filter"]
            self.assertEqual(bool(re.search(pattern, case["url"], re.I)), case["blocked"], case)

    def test_english_and_arabic_keys_match_and_swift_keys_exist(self):
        def keys(language):
            text = (rules.ROOT / f"ios/Hisn/{language}.lproj/Localizable.strings").read_text()
            return set(re.findall(r'^"([^"]+)"\s*=', text, re.M))
        english, arabic = keys("en"), keys("ar")
        self.assertEqual(english, arabic)
        for path in (rules.ROOT / "ios/Hisn").glob("*.swift"):
            source = re.sub(r'system(?:Image|Name):\s*"[^"]+"', '', path.read_text())
            for key in re.findall(r'"([a-z]+\.[a-z.]+)"', source):
                if key.startswith(("hisn.", "shield.", "exclamationmark.")): continue
                if key == "app.hisn.mobile.commitment": continue  # Keychain service, not UI text.
                self.assertIn(key, english, (path.name, key))


if __name__ == "__main__":
    unittest.main()
