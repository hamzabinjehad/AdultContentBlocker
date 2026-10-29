"""Keep the generated per-surface coverage page honest about the signed seed."""
import unittest

import coverage

ROOT = coverage.ROOT


class CoverageTests(unittest.TestCase):
    def test_page_is_current(self):
        self.assertEqual(coverage.DOC.read_text(), coverage.document())

    def test_static_rules_match_the_signed_manifest(self):
        seed, static_rules = coverage.facts()
        self.assertEqual(static_rules, seed["dnr_rule_count"])

    def test_core_tier_fits_four_safari_blockers(self):
        seed, _ = coverage.facts()
        self.assertLessEqual(seed["core_domain_count"], coverage.SAFARI_BLOCKERS * coverage.SAFARI_CAPACITY)
        self.assertGreater(seed["core_domain_count"], 0)

    def test_page_names_every_surface_and_tier(self):
        page = coverage.DOC.read_text()
        for surface in ("Chromium extension", "Mac content filter", "Safari on iPhone/iPad",
                        "Android DNS", "Router / network DNS"):
            self.assertIn(surface, page)
        seed, _ = coverage.facts()
        self.assertIn(f"{seed['core_domain_count']:,}", page)
        self.assertIn(f"{seed['domain_count']:,}", page)
        self.assertIn(f"{seed['term_count']:,}", page)


if __name__ == "__main__":
    unittest.main()
