"""The URL-obfuscation ruleset: generated, reviewed, and never stale.

`evasion.py --check` is the gate that catches an edited table whose ruleset
was not regenerated; `test.sh` runs it. These tests cover the parts a byte
comparison cannot: that the table cannot smuggle in vocabulary the signed list
does not contain, that the host-token regex means what its comment says, and
that the shipped rules have the shape Chrome accepts.
"""

from __future__ import annotations

import json
import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import evasion  # noqa: E402


class AliasTableTests(unittest.TestCase):

    def write(self, text: str) -> Path:
        handle = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False)
        handle.write(text)
        handle.close()
        self.addCleanup(Path(handle.name).unlink)
        return Path(handle.name)

    def test_shipped_table_parses(self):
        table = evasion.load_aliases()
        self.assertEqual(set(table), {"substring", "host_token"})
        aliases = [e["alias"] for entries in table.values() for e in entries]
        self.assertEqual(len(aliases), len(set(aliases)), "no alias may appear twice")

    def test_rejects_unknown_keys(self):
        path = self.write(json.dumps({"substring": [{"alias": "p0rn", "canonical": "porn"}],
                                      "host_token": [{"alias": "s3x", "canonical": "sex"}],
                                      "surprise": []}))
        with self.assertRaisesRegex(evasion.AliasError, "unknown keys"):
            evasion.load_aliases(path)

    def test_rejects_non_ascii_alias(self):
        # A non-ASCII urlFilter can make Chrome reject the whole downloaded
        # ruleset, and a punycode hostname never contains Arabic letters anyway.
        path = self.write(json.dumps({"substring": [{"alias": "سكس", "canonical": "porn"}],
                                      "host_token": [{"alias": "s3x", "canonical": "sex"}]}))
        with self.assertRaisesRegex(evasion.AliasError, "lowercase letters/digits"):
            evasion.load_aliases(path)

    def test_rejects_identical_alias_and_canonical(self):
        path = self.write(json.dumps({"substring": [{"alias": "porn", "canonical": "porn"}],
                                      "host_token": [{"alias": "s3x", "canonical": "sex"}]}))
        with self.assertRaisesRegex(evasion.AliasError, "identical"):
            evasion.load_aliases(path)

    def test_rejects_duplicate_alias_across_categories(self):
        path = self.write(json.dumps({"substring": [{"alias": "s3x", "canonical": "porn"}],
                                      "host_token": [{"alias": "s3x", "canonical": "sex"}]}))
        with self.assertRaisesRegex(evasion.AliasError, "already used"):
            evasion.load_aliases(path)

    def test_rejects_empty_category(self):
        path = self.write(json.dumps({"substring": [],
                                      "host_token": [{"alias": "s3x", "canonical": "sex"}]}))
        with self.assertRaisesRegex(evasion.AliasError, "non-empty"):
            evasion.load_aliases(path)


class VocabularyTests(unittest.TestCase):

    def setUp(self):
        self.host = {"porn": "substring", "sex": "token", "milf": "substring"}
        self.page = {"porn", "sex"}

    def table(self, substring=(), host_token=()):
        return {"substring": list(substring), "host_token": list(host_token)}

    def test_accepts_known_canonicals(self):
        evasion.validate_against_vocabulary(
            self.table([{"alias": "p0rn", "canonical": "porn"}],
                       [{"alias": "s3x", "canonical": "sex"}]), self.host, self.page)

    def test_rejects_invented_vocabulary(self):
        with self.assertRaisesRegex(evasion.AliasError, "not in host_terms"):
            evasion.validate_against_vocabulary(
                self.table([{"alias": "g4mbling", "canonical": "gambling"}]),
                self.host, self.page)

    def test_substring_requires_an_unambiguous_canonical(self):
        # `sex` is a host-token term; a substring rule would catch
        # `essex.gov.uk` and every `?sex=female` form.
        with self.assertRaisesRegex(evasion.AliasError, "host-token term"):
            evasion.validate_against_vocabulary(
                self.table([{"alias": "s3x", "canonical": "sex"}]), self.host, self.page)

    def test_rejects_an_alias_that_is_already_a_term(self):
        with self.assertRaisesRegex(evasion.AliasError, "already a reviewed term"):
            evasion.validate_against_vocabulary(
                self.table([{"alias": "porn", "canonical": "porn"}]),
                {**self.host, "porn": "substring"}, {"porn", "other"})


class RegexTests(unittest.TestCase):

    def test_host_token_is_whole_label(self):
        pattern = evasion.host_token_regex("s3x")
        for url in ("https://s3x.example/", "https://www.s3x.example:8443/a",
                    "http://cdn.s3x123.example/watch", "https://s3x.example?x=1"):
            self.assertTrue(re.search(pattern, url, re.IGNORECASE), url)
        for url in ("https://s3xeducation.example/", "https://nots3x.example/",
                    "https://example.com/s3x", "https://example.com/?q=s3x"):
            self.assertFalse(re.search(pattern, url, re.IGNORECASE), url)

    def test_aliases_are_regex_escaped(self):
        # The generator only accepts alphanumerics, but asserting it through the
        # pattern documents why that restriction can never quietly relax.
        for table in evasion.load_aliases().values():
            for entry in table:
                self.assertTrue(re.fullmatch(r"[a-z0-9]+", entry["alias"]))

    def test_generated_rules_have_the_dnr_shape(self):
        table = evasion.load_aliases()
        rules = evasion.build_rules(table, ["wikipedia.org"])
        self.assertEqual(len(rules), len(table["substring"]) + 2 * len(table["host_token"]))
        self.assertEqual([r["id"] for r in rules], list(range(1, len(rules) + 1)))
        documents = [r for r in rules if r["action"]["type"] == "redirect"]
        embedded = [r for r in rules if r["action"]["type"] == "block"]
        self.assertEqual(len(documents), len(table["substring"]) + len(table["host_token"]))
        self.assertEqual(len(embedded), len(table["host_token"]))
        for rule in documents:
            self.assertEqual(rule["action"]["redirect"]["extensionPath"], evasion.BLOCK_PAGE)
            self.assertEqual(rule["condition"]["resourceTypes"], evasion.DOCUMENTS)
        for rule in embedded:
            self.assertEqual(rule["condition"]["resourceTypes"], evasion.EMBEDDED)
        for rule in rules:
            self.assertEqual(rule["priority"], evasion.PRIORITY)
            self.assertEqual(rule["condition"]["excludedRequestDomains"], ["wikipedia.org"])
            self.assertTrue("urlFilter" in rule["condition"]
                            or "regexFilter" in rule["condition"])


class OutputTests(unittest.TestCase):

    def test_shipped_ruleset_is_current(self):
        for name, content in evasion.outputs().items():
            path = evasion.ROOT / name
            self.assertTrue(path.exists(), f"{name} is missing")
            self.assertEqual(path.read_text(encoding="utf-8"), content,
                             f"{name} is stale — run python3 blocklist/evasion.py")

    def test_ruleset_matches_the_signed_vocabulary(self):
        table = evasion.load_aliases()
        payload = evasion.compile_terms(evasion.TERMS)
        kinds = {entry["t"]: entry["kind"] for entry in payload["host_terms"]}
        # The table may only spell terms this build actually ships; a merge that
        # drops a term must not leave an alias pointing at nothing.
        for entries in table.values():
            for entry in entries:
                self.assertIn(entry["canonical"], kinds)


if __name__ == "__main__":
    unittest.main()
