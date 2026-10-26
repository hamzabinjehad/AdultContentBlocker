"""Percent-encoded Arabic search queries: generated, bounded, and testable.

The point of this layer is narrow enough to state as a test matrix: an explicit
term in a recognised search parameter is caught; the same bytes in a path, an
unrelated parameter, or the middle of a longer word (Saxony!) are not.
"""

from __future__ import annotations

import json
import re
import sys
import unittest
from pathlib import Path
from urllib.parse import quote

sys.path.insert(0, str(Path(__file__).resolve().parent))

import encoded_queries as encoded  # noqa: E402


class TermsTests(unittest.TestCase):

    def setUp(self):
        self.payload = encoded.compile_terms(encoded.TERMS)
        self.terms = encoded.strong_arabic_terms(self.payload)

    def test_selection_is_strong_single_token_arabic(self):
        by_term = {entry["t"]: entry for entry in self.payload["terms"]}
        self.assertTrue(self.terms, "the signed vocabulary must contain explicit Arabic terms")
        for term in self.terms:
            self.assertGreaterEqual(by_term[term]["w"], 8, term)
            self.assertNotIn(" ", term)
            self.assertGreaterEqual(len(term), 3)
            self.assertTrue(encoded.ARABIC.fullmatch(term), term)

    def test_exclusions_are_strong_terms(self):
        excluded = encoded.load_exclusions()
        self.assertTrue(excluded, "the reviewed exclusion file must not be empty")
        self.assertTrue(excluded <= set(self.terms),
                        "an exclusion must name a strong single-token term")

    def test_excluded_terms_do_not_get_a_rule(self):
        excluded = encoded.load_exclusions()
        rules = encoded.build_rules(self.payload, [], excluded)
        self.assertEqual(len(rules), len(self.terms) - len(excluded))
        for term in excluded:
            pattern = encoded.query_regex(term)
            self.assertFalse(any(r["condition"]["regexFilter"] == pattern for r in rules),
                             f"{term} must not have a query rule")

    def test_weak_or_multi_word_terms_are_excluded(self):
        payload = {"terms": [
            {"t": "سكس", "w": 8},
            {"t": "ضعيف", "w": 4},
            {"t": "كلمتان معا", "w": 8},
            {"t": "latin", "w": 8},
            {"t": "سكس", "w": 8},
        ]}
        self.assertEqual(encoded.strong_arabic_terms(payload), ["سكس"])


class RegexTests(unittest.TestCase):

    def setUp(self):
        self.term = "سكس"
        self.pattern = encoded.query_regex(self.term)
        self.encoded = quote(self.term, safe="")

    def match(self, url: str) -> bool:
        return bool(re.search(self.pattern, url, re.IGNORECASE))

    def test_catches_a_search_query(self):
        for url in (
            f"https://www.google.com/search?q={self.encoded}",
            f"https://www.bing.com/search?q={self.encoded}&form=QBLH",
            f"https://duckduckgo.com/?q={self.encoded}",
            f"https://search.brave.com/search?q={self.encoded}",
            f"https://www.youtube.com/results?search_query={self.encoded}",
            f"https://example.com/search?query={self.encoded}",
            f"https://example.com/?q=%D8%B3%D9%83%D8%B3%20%D8%B9%D8%B1%D8%A8%D9%8A",
            f"https://example.com/?q=%d8%b3%d9%83%d8%b3",  # lowercase escapes
        ):
            self.assertTrue(self.match(url), url)

    def test_does_not_match_a_path(self):
        for url in (
            f"https://news.example/{self.encoded}",
            f"https://news.example/{self.encoded}/story",
        ):
            self.assertFalse(self.match(url), url)

    def test_does_not_match_an_unrecognised_parameter(self):
        for url in (
            f"https://example.com/?x={self.encoded}",
            f"https://example.com/?ref={self.encoded}",
        ):
            self.assertFalse(self.match(url), url)

    def test_does_not_match_a_longer_word(self):
        # `سكسونيا` is Saxony. A substring rule without a boundary would block
        # every search for it, which is exactly the false positive the escaped
        # boundary exists to prevent.
        saxony = quote("سكسونيا", safe="")
        self.assertFalse(self.match(f"https://www.google.com/search?q={saxony}"))

    def test_a_recognised_parameter_must_precede_the_term(self):
        self.assertFalse(self.match(f"https://example.com/?q=hello&x={self.encoded}"))

    def test_excluded_benign_word_is_not_matched_by_the_shipped_rules(self):
        # طابون is a clay oven / flatbread. It is in the signed page vocabulary
        # (mined from adult domains) but a bare-word query rule would block a
        # food search, so it is in the reviewed exclusion file.
        taboon = quote("طابون", safe="")
        shipped = json.loads((encoded.ROOT / "extension/rules/encoded_queries.json")
                             .read_text(encoding="utf-8"))
        self.assertFalse(any(re.search(r["condition"]["regexFilter"],
                                       f"https://www.google.com/search?q={taboon}", re.IGNORECASE)
                             for r in shipped))


class OutputTests(unittest.TestCase):

    def test_shipped_ruleset_is_current(self):
        for name, content in encoded.outputs().items():
            path = encoded.ROOT / name
            self.assertTrue(path.exists(), f"{name} is missing")
            self.assertEqual(path.read_text(encoding="utf-8"), content,
                             f"{name} is stale — run python3 blocklist/encoded_queries.py")

    def test_rules_have_the_dnr_shape(self):
        payload = encoded.compile_terms(encoded.TERMS)
        rules = encoded.build_rules(payload, ["wikipedia.org"])
        self.assertEqual(len(rules), len(encoded.strong_arabic_terms(payload)))
        self.assertTrue(rules, "an empty ruleset would be a silent no-op")
        self.assertEqual([r["id"] for r in rules], list(range(1, len(rules) + 1)))
        for rule in rules:
            self.assertEqual(rule["priority"], encoded.PRIORITY)
            self.assertEqual(rule["condition"]["resourceTypes"], encoded.DOCUMENTS)
            self.assertFalse(rule["condition"]["isUrlFilterCaseSensitive"])
            self.assertEqual(rule["condition"]["excludedRequestDomains"], ["wikipedia.org"])
            self.assertEqual(rule["action"]["type"], "redirect")
            self.assertEqual(rule["action"]["redirect"]["extensionPath"],
                             "/blocked.html?reason=terms")
            re.compile(rule["condition"]["regexFilter"])

    def test_shipped_file_is_valid_json(self):
        rules = json.loads((encoded.ROOT / "extension/rules/encoded_queries.json")
                           .read_text(encoding="utf-8"))
        self.assertIsInstance(rules, list)
        self.assertTrue(all(isinstance(rule, dict) for rule in rules))


if __name__ == "__main__":
    unittest.main()
