"""Keep the generated per-surface coverage page honest about the signed seed."""
import json
import unittest

import coverage

ROOT = coverage.ROOT


class CoverageTests(unittest.TestCase):
    def test_page_is_current(self):
        self.assertEqual(coverage.DOC.read_text(), coverage.document())

    def test_static_rules_match_the_signed_manifest(self):
        seed, static_rules = coverage.facts()
        self.assertEqual(static_rules, seed["dnr_rule_count"])

    def test_core_tier_fits_six_safari_blockers(self):
        seed, _ = coverage.facts()
        self.assertLessEqual(seed["core_domain_count"], coverage.SAFARI_BLOCKERS * coverage.SAFARI_CAPACITY)
        self.assertGreater(seed["core_domain_count"], 0)

    def test_capacity_agrees_across_builders_and_clients(self):
        """One number, four places: sources.json, the signed seed, the shard
        compiler and the Swift readiness check. Drift here silently drops
        domains from every surface at once."""
        capacity = coverage.SAFARI_BLOCKERS * coverage.SAFARI_CAPACITY
        cfg = json.loads((ROOT / "blocklist/sources.json").read_text())
        self.assertEqual(cfg["core_capacity"], capacity)
        seed = json.loads((ROOT / "seed/manifest.json").read_text())
        self.assertEqual(seed["core_capacity"], capacity)
        shards = (ROOT / "ios/prepare_rules.py").read_text()
        self.assertRegex(shards, rf"PARTS = {coverage.SAFARI_BLOCKERS}\b")
        self.assertRegex(shards, rf"RULE_LIMIT = {coverage.SAFARI_CAPACITY:_}\b")
        policy = (ROOT / "shared/apple/MobileProtectionPolicy.swift").read_text()
        self.assertIn(f"blockerIdentifiers = (1...{coverage.SAFARI_BLOCKERS})", policy)
        self.assertIn(f"perBlockerRuleLimit = {coverage.SAFARI_CAPACITY:_}", policy)

    def test_page_names_every_surface_and_tier(self):
        page = coverage.DOC.read_text()
        for surface in ("Chromium extension", "Mac content filter", "Safari and DNS on iPhone/iPad",
                        "Android DNS", "Router / network DNS"):
            self.assertIn(surface, page)
        seed, _ = coverage.facts()
        self.assertIn(f"{seed['core_domain_count']:,}", page)
        self.assertIn(f"{seed['domain_count']:,}", page)
        self.assertIn(f"{seed['term_count']:,}", page)


if __name__ == "__main__":
    unittest.main()
