import hashlib
import json
import sys
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

    TERMS = {"schema": 1, "version": 7,
             "host_terms": [{"t": "2a7a", "kind": "token"},
                            {"t": "3ahira", "kind": "substring"}],
             "never_keyword": ["essex", "accumulate"],
             "keyword_exempt_hosts": ["jins.com"]}

    def fixture(self, directory, domains, terms=None):
        key = Ed25519PrivateKey.generate()
        public = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        (directory / "key.hex").write_text(public.hex())
        raw = ("# signed fixture\n" + "\n".join(domains) + "\n").encode()
        (directory / "domains_core.txt").write_bytes(raw)
        artifacts = {"domains_core.txt": {"sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)}}
        if terms is not None:
            raw_terms = json.dumps(terms, sort_keys=True).encode()
            (directory / "terms.json").write_bytes(raw_terms)
            artifacts["terms.json"] = {"sha256": hashlib.sha256(raw_terms).hexdigest(),
                                       "bytes": len(raw_terms)}
        manifest = json.dumps({"schema": 1, "version": 7, "core_domain_count": len(domains),
            "built_at": "2026-10-01T20:29:42+00:00", "artifacts": artifacts}).encode()
        (directory / "manifest.json").write_bytes(manifest)
        (directory / "manifest.json.sig").write_text(key.sign(manifest).hex())
        return directory / "key.hex"

    def test_dns_assets_are_deterministic_pinned_and_sorted(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            key = self.fixture(directory, ["a.example.net", "b.example.net"], terms=dict(self.TERMS))
            metadata = rules.dns_assets(directory, key, directory / "out")
            out = directory / "out"
            raw_domains = (out / "dns-domains.txt").read_bytes()
            raw_terms = (out / "dns-host-terms.json").read_bytes()
            self.assertEqual(raw_domains, b"a.example.net\nb.example.net\n")
            self.assertEqual(metadata["count"], 2)
            self.assertEqual(metadata["domains_bytes"], len(raw_domains))
            self.assertEqual(metadata["terms_bytes"], len(raw_terms))
            self.assertEqual(metadata["domains_sha256"], hashlib.sha256(raw_domains).hexdigest())
            self.assertEqual(metadata["terms_sha256"], hashlib.sha256(raw_terms).hexdigest())
            self.assertEqual(metadata["adult_tlds"], list(rules.ADULT_TLDS))
            self.assertEqual(metadata["version"], 7)
            self.assertEqual(metadata["terms_version"], 7)
            terms = json.loads(raw_terms)
            self.assertEqual(terms["host_terms"], self.TERMS["host_terms"])
            self.assertEqual(terms["never_keyword"], sorted(self.TERMS["never_keyword"]))
            self.assertEqual(terms["keyword_exempt_hosts"], ["jins.com"])
            before = (out / "dns-metadata.json").read_bytes()
            rules.dns_assets(directory, key, directory / "out")
            self.assertEqual((out / "dns-metadata.json").read_bytes(), before)
            self.assertEqual((out / "dns-domains.txt").read_bytes(), raw_domains)
            self.assertFalse((out / "rules.json").exists())

    def test_dns_rejects_tampered_missing_or_invalid_terms(self):
        with tempfile.TemporaryDirectory() as temporary, self.subTest("tampered"):
            directory = Path(temporary)
            key = self.fixture(directory, ["a.example.net"], terms=dict(self.TERMS))
            (directory / "terms.json").write_bytes(b'{"schema":1}')
            with self.assertRaises(Rejected):
                rules.dns_assets(directory, key, directory / "out")
        with tempfile.TemporaryDirectory() as temporary, self.subTest("missing"):
            directory = Path(temporary)
            key = self.fixture(directory, ["a.example.net"], terms=None)
            with self.assertRaises(Rejected):
                rules.dns_assets(directory, key, directory / "out")
        for terms in [
            dict(self.TERMS, schema=2),
            dict(self.TERMS, version=0),
            dict(self.TERMS, host_terms=[]),
            dict(self.TERMS, host_terms=[{"t": "", "kind": "token"}]),
            dict(self.TERMS, host_terms=[{"t": "x", "kind": "regex"}]),
            dict(self.TERMS, host_terms=[{"t": "a\tb", "kind": "token"}]),
            dict(self.TERMS, never_keyword=["ok", ""]),
            dict(self.TERMS, keyword_exempt_hosts=["ok"] * 201),
        ]:
            with tempfile.TemporaryDirectory() as temporary, self.subTest(terms=terms):
                directory = Path(temporary)
                key = self.fixture(directory, ["a.example.net"], terms=terms)
                with self.assertRaises(Rejected):
                    rules.dns_assets(directory, key, directory / "out")

    def test_dns_mode_is_exclusive_of_safari_parts(self):
        with tempfile.TemporaryDirectory() as temporary, \
             patch.object(sys, "argv", ["prepare_rules.py", "--dns", "--part", "3",
                                        "--output", temporary]):
            with self.assertRaises(SystemExit):
                rules.main()

    def test_dns_assets_from_the_shipped_seed(self):
        with tempfile.TemporaryDirectory() as temporary:
            metadata = rules.dns_assets(rules.ROOT / "seed",
                                        rules.ROOT / "blocklist/public_key.hex",
                                        Path(temporary))
            out = Path(temporary)
            raw = (out / "dns-domains.txt").read_bytes()
            terms = json.loads((out / "dns-host-terms.json").read_text())
            self.assertGreater(metadata["count"], 200_000)
            self.assertEqual(metadata["domains_bytes"], len(raw))
            self.assertEqual(metadata["domains_sha256"], hashlib.sha256(raw).hexdigest())
            self.assertEqual(raw.count(b"\n"), metadata["count"])
            self.assertGreaterEqual(len(terms["host_terms"]), 50)
            self.assertNotIn(b"\n\n", raw)
            self.assertEqual(metadata["adult_tlds"], list(rules.ADULT_TLDS))

    def test_signed_input_checks_and_comments(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            key = self.fixture(directory, ["a.example.net", "b.example.net"])
            self.assertEqual(rules.verified_domains(directory, key),
                             (["a.example.net", "b.example.net"], 7, "2026-10-01T20:29:42+00:00"))
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
            key = self.fixture(directory, [f"site{i}.example.net" for i in range(rules.PARTS + 1)])
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
            for part in [0, rules.PARTS + 1]:
                with self.assertRaises(Rejected): rules.compile_part(domains, part)
            with self.assertRaises(Rejected): rules.compile_part(["a.example.net"], 2)

    def test_url_boundaries_against_shared_fixtures(self):
        cases = json.loads((rules.ROOT / "ios/fixtures/safari_cases.json").read_text())
        for case in cases:
            pattern = rules.rule(case["domain"])["trigger"]["url-filter"]
            self.assertEqual(bool(re.search(pattern, case["url"], re.I)), case["blocked"], case)

    def test_adult_tld_guard_is_complete_and_only_in_the_last_part(self):
        self.assertEqual(rules.ADULT_TLDS, ("adult", "porn", "sex", "xxx"))
        guard = rules.guard_rules()
        self.assertEqual(len(guard), len(rules.ADULT_TLDS))
        self.assertTrue(all(r["action"] == {"type": "block"} for r in guard))
        with patch.object(rules, "RULE_LIMIT", 10):
            domains = [f"site{i}.example.net" for i in range(rules.PARTS * 10 - 9)]
            for part in range(1, rules.PARTS):
                emitted = json.loads(rules.compile_part(domains, part))
                self.assertFalse(any(r in guard for r in emitted),
                                 f"guard must not be duplicated into part {part}")
            last = json.loads(rules.compile_part(domains, rules.PARTS))
            self.assertEqual(last[-len(guard):], guard,
                             "the last part ends with the guard rules")
            selected = len(domains) - (rules.PARTS - 1) * rules.RULE_LIMIT
            self.assertEqual(len(last), selected + len(guard))
            self.assertLessEqual(len(last), rules.RULE_LIMIT)

    def test_adult_tld_guard_respects_the_per_part_limit(self):
        with patch.object(rules, "RULE_LIMIT", 10), \
             patch.object(rules, "guard_rules", return_value=rules.guard_rules()[:4]):
            domains = [f"site{i}.example.net" for i in range((rules.PARTS - 1) * 10 + 9)]
            with self.assertRaises(Rejected):
                rules.compile_part(domains, rules.PARTS)

    def test_adult_tld_patterns_match_only_the_final_label(self):
        positives = [
            "https://new.porn/",
            "http://foo.xxx/",
            "https://a.b.sex:8443/watch",
            "https://user:pass@adult.example.adult/path",
            "https://new.porn./",
            "https://xxx/",
        ]
        negatives = [
            "https://notporn.com/",
            "https://porn.example.com/",
            "https://example.com/new.porn",
            "https://foo.xxx.example.com/",
            "https://xxx.evil.com/",
            "https://example.cam/",
            "https://example.com/",
        ]
        patterns = {r["trigger"]["url-filter"]: r for r in rules.guard_rules()}
        for url in positives:
            self.assertTrue(any(re.search(p, url, re.I) for p in patterns), url)
        for url in negatives:
            self.assertFalse(any(re.search(p, url, re.I) for p in patterns), url)

    def test_summary_and_last_part_metadata_include_the_guard(self):
        domains = [f"site{i}.example.net" for i in range(3)]
        with tempfile.TemporaryDirectory() as temporary, \
             patch.object(rules, "verified_domains", return_value=(domains, 7, None)), \
             patch.object(sys, "argv", ["prepare_rules.py", "--output", temporary]):
            rules.main()
            metadata = json.loads((Path(temporary) / "rules-metadata.json").read_text())
            self.assertEqual(metadata["count"], len(domains) + len(rules.guard_rules()))

        with tempfile.TemporaryDirectory() as temporary, \
             patch.object(rules, "RULE_LIMIT", 10), \
             patch.object(rules, "verified_domains",
                          return_value=([f"site{i}.example.net" for i in range(51)], 7, None)), \
             patch.object(sys, "argv", ["prepare_rules.py", "--part", str(rules.PARTS),
                                        "--output", temporary]):
            rules.main()
            metadata = json.loads((Path(temporary) / "rules-metadata.json").read_text())
            self.assertEqual(metadata["count"], 1 + len(rules.guard_rules()))

    def test_english_and_arabic_keys_match_and_swift_keys_exist(self):
        def keys(language):
            text = (rules.ROOT / f"ios/Hisn/{language}.lproj/Localizable.strings").read_text()
            return set(re.findall(r'^"([^"]+)"\s*=', text, re.M))
        english, arabic = keys("en"), keys("ar")
        self.assertEqual(english, arabic)
        for path in (rules.ROOT / "ios/Hisn").glob("*.swift"):
            source = re.sub(r'system(?:Image|Name):\s*"[^"]+"', '', path.read_text())
            for key in re.findall(r'"([a-z]+\.[a-z.]+)"', source):
                # SF Symbol names and Keychain services are not localization keys;
                # the dotted-symbol prefixes are listed rather than guessed at.
                if key.startswith(("hisn.", "shield.", "exclamationmark.",
                                   "arrow.", "hand.", "moon.", "pause.")): continue
                if key == "app.hisn.mobile.commitment": continue  # Keychain service, not UI text.
                self.assertIn(key, english, (path.name, key))


if __name__ == "__main__":
    unittest.main()
