#!/usr/bin/env python3
"""
Tests for the keyword layer.

    python3 -m pytest test_terms.py -q
    python3 test_terms.py

Same conventions as `test_build.py`: stdlib unittest, no pytest dependency,
and every docstring says WHAT BUG IT PREVENTS rather than what it does.

The load-bearing tests here are the negative ones. A missed adult domain costs
one gap in a list that is already a losing race; a wrongly blocked hostname
takes out an entire legitimate site, and that is the failure that makes someone
uninstall the whole product.
"""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from terms import (  # noqa: E402
    MIN_SUBSTRING_LEN,
    compile_terms,
    decode_punycode,
    host_matches,
    host_tokens,
    normalize,
    tokenize,
    token_variants,
    validate,
)

SRC = Path(__file__).parent / "terms"


def load_cases(name: str) -> list[dict]:
    return json.loads((SRC / name).read_text(encoding="utf-8"))["cases"]


def compiled() -> dict:
    return compile_terms(SRC)


def matcher(payload: dict):
    """Bind the compiled list into the shape `host_matches` wants."""
    tokens = {r["t"] for r in payload["host_terms"] if r["kind"] == "token"}
    substrings = {r["t"] for r in payload["host_terms"] if r["kind"] == "substring"}
    never = set(payload["never_keyword"])
    return lambda host: host_matches(host, tokens, substrings, never)


# --------------------------------------------------------------------------- #
# Normalisation — the cross-language contract
# --------------------------------------------------------------------------- #

class TestNormalize(unittest.TestCase):
    """
    The Python half of a three-language contract.

    `extension/lib/normalize.js` and `Inspection.normalizeTerm` (Swift) assert
    the SAME table. Nothing at build time forces the three to agree, and a
    divergence is silent: a signed term that never matches on the device. This
    is the keyword-layer analogue of `test_collapse_lookup_invariant`.
    """

    def test_normalize_golden(self):
        """Prevents a normaliser drift that silently unmatchable every Arabic term."""
        for case in load_cases("normalize_cases.json"):
            with self.subTest(case["why"], value=case["in"]):
                self.assertEqual(normalize(case["in"]), case["out"])

    def test_normalize_is_idempotent(self):
        """
        Prevents a fold that changes its own output.

        The compiler normalises terms once at build time and the runtime
        normalises page text on every pass; if the function is not idempotent
        the two produce different strings for the same word.
        """
        for case in load_cases("normalize_cases.json"):
            once = normalize(case["in"])
            self.assertEqual(normalize(once), once, f"not idempotent: {case['in']!r}")

    def test_franco_arabic_digits_survive(self):
        """
        Prevents digit-folding from destroying the romanised Arabic tier.

        2/3/5/7/9 are LETTERS in Franco-Arabic. Folding Arabic-Indic digits to
        ASCII must not tempt anyone into stripping ASCII digits too, which would
        turn `3ahira` into `ahira` and match nothing.
        """
        self.assertEqual(normalize("3ahira"), "3ahira")
        self.assertEqual(normalize("2a7a"), "2a7a")


class TestTokenize(unittest.TestCase):

    def test_arabic_and_latin_split_on_the_same_rule(self):
        """
        Prevents a regex-\\b tokenizer.

        `\\b` is ASCII-word-based even with the unicode flag, so it does not
        see Arabic word boundaries at all. Splitting on the complement of
        letters+digits needs no per-language rules.
        """
        self.assertEqual(tokenize("sks-arab.net"), ["sks", "arab", "net"])
        self.assertEqual(tokenize(normalize("صور إباحية")), ["صور", "اباحيه"])

    def test_bounded_prefix_stripping(self):
        """
        Prevents the Arabic definite article from disabling every term.

        `الإباحية` normalises to `الاباحيه`; without stripping `ال` it never
        matches the listed term `اباحيه`.
        """
        self.assertIn("اباحيه", token_variants(normalize("الإباحية")))

    def test_stripping_stays_bounded(self):
        """
        Prevents unbounded stemming, which is how `جنس` starts matching
        `الجنسية` and blocks every Arabic passport and visa page.
        """
        variants = token_variants(normalize("الجنسية"))
        self.assertNotIn("جنس", variants)

    def test_trailing_digits_are_decoration_but_leading_ones_are_not(self):
        """
        Prevents both halves of the digit problem at once: `sharmota123` must
        reduce to a matchable token, while `3ahira` must not lose its first
        letter.
        """
        self.assertIn("sharmota", token_variants("sharmota123"))
        self.assertNotIn("ahira", token_variants("3ahira"))


# --------------------------------------------------------------------------- #
# Hostnames — the half that matters most
# --------------------------------------------------------------------------- #

class TestPunycode(unittest.TestCase):

    def test_punycode_hostname_is_decoded_before_matching(self):
        """
        Prevents Arabic-script domains being invisible to the entire keyword
        layer. On the wire the label is ASCII and shares no character with any
        Arabic term, so without decoding the match can never happen.
        """
        self.assertEqual(decode_punycode("xn--mgbh0fb.example"), "مثال.example")

    def test_undecodable_label_is_kept_not_dropped(self):
        """
        Prevents one malformed label from discarding the rest of a hostname —
        which would turn a corrupt name into a free pass.
        """
        self.assertEqual(decode_punycode("xn--!!!.example.com"),
                         "xn--!!!.example.com")


class TestHostMatching(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        # staticmethod, or Python binds the lambda as an instance method and
        # every call arrives with `self` prepended.
        cls.match = staticmethod(matcher(compiled()))

    def test_host_cases_golden(self):
        """
        The cross-language hostname contract, positive and negative halves.

        Prevents a domain being blocked on the Mac and reachable in Chrome, or
        the reverse, with nothing anywhere to signal the disagreement.
        """
        for case in load_cases("host_cases.json"):
            with self.subTest(case["why"], host=case["host"]):
                self.assertEqual(self.match(case["host"]), case["block"])

    def test_host_keyword_never_blocks_innocent_hosts(self):
        """
        THE load-bearing negative test.

        Prevents the Scunthorpe problem: naive substring matching on hostnames
        blocks a county council, two universities, Google Analytics and the
        asset host of half the web. Each hostname below is a site that stays
        reachable only because `host_terms.tsv` marked the colliding term
        `token` rather than `substring`.
        """
        for host in ["essex.gov.uk", "sussex.ac.uk", "middlesex.edu",
                     "scunthorpe.gov.uk", "analytics.google.com",
                     "analysis.example.org", "assets.example.com",
                     "classroom.google.com", "institute.edu",
                     "constitution.org", "tasks.office.com",
                     "risks.example.com", "kosher-food.com", "therapist.com",
                     "apple.com", "github.com", "who.int",
                     "raw.githubusercontent.com", "icloud.com"]:
                with self.subTest(host=host):
                    self.assertFalse(self.match(host),
                                     f"{host} would be blocked — whole-site lockout")

    def test_innocent_token_does_not_immunise_the_rest_of_the_host(self):
        """
        Prevents the obvious over-correction to the test above.

        If a `never_keyword` hit exempted the whole hostname, registering
        `essex-porn.com` would be a complete bypass of the keyword layer. The
        guard removes the innocent token and re-tests what remains.
        """
        self.assertFalse(self.match("essex.gov.uk"))
        self.assertTrue(self.match("essex-porn.com"))

    def test_trailing_dot_does_not_bypass(self):
        """
        Prevents the FQDN bypass, here as well as in the domain list.

        `pornhub.com.` is a valid absolute name that resolves identically, so a
        keyword layer that misses it is a one-keystroke hole.
        """
        self.assertTrue(self.match("pornhub.com."))

    def test_matching_is_case_insensitive(self):
        """Prevents an uppercase hostname being a free pass."""
        self.assertTrue(self.match("PORNHUB.COM"))


# --------------------------------------------------------------------------- #
# Compiler
# --------------------------------------------------------------------------- #

class TestCompiler(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.payload = compiled()

    def test_compiles_the_committed_sources(self):
        """Prevents a syntax error in a .tsv reaching a release unnoticed."""
        self.assertGreaterEqual(len(self.payload["terms"]), 200)
        self.assertTrue(self.payload["host_terms"])

    def test_every_language_tier_is_present(self):
        """
        Prevents Arabic coverage silently dropping to zero.

        Arabic is a requirement of this product, not a nice-to-have, and the
        romanised tier specifically is what makes hostname matching work at all
        for Arabic-audience domains.
        """
        langs = {r["l"] for r in self.payload["terms"]}
        for required in ("ar", "ar-latn", "en"):
            self.assertIn(required, langs)

    def test_terms_are_stored_already_normalised(self):
        """
        Prevents the runtime having to normalise the list on every page.

        More importantly it prevents a term that normalisation would change
        from being stored in a form nothing can ever match.
        """
        for row in self.payload["terms"] + self.payload["host_terms"]:
            self.assertEqual(row["t"], normalize(row["t"]))

    def test_substring_terms_meet_the_length_floor(self):
        """
        Prevents a short substring term, which is precisely how `sex` ends up
        matching `essex.gov.uk`.
        """
        for row in self.payload["host_terms"]:
            if row["kind"] == "substring":
                self.assertGreaterEqual(len(row["t"]), MIN_SUBSTRING_LEN, row["t"])

    def test_no_term_is_also_a_never_keyword(self):
        """
        Prevents the two safety files contradicting each other, which would
        leave the verdict depending on evaluation order.
        """
        never = set(self.payload["never_keyword"])
        for row in self.payload["host_terms"]:
            self.assertNotIn(row["t"], never)

    def test_refuses_a_degraded_list(self):
        """
        Prevents shipping a list that silently collapsed.

        The same failure the domain pipeline guards at four layers: a list that
        quietly stops covering anything looks exactly like one that is working.
        """
        with self.assertRaises(SystemExit):
            validate({"terms": [{"t": "porn", "w": 8.0, "l": "en"}],
                      "host_terms": [{"t": "porn", "kind": "substring"}]})

    def test_refuses_a_list_with_no_arabic(self):
        """Prevents an English-only build passing as complete."""
        terms = [{"t": f"w{i}", "w": 5.0, "l": "en"} for i in range(300)]
        with self.assertRaises(SystemExit):
            validate({"terms": terms,
                      "host_terms": [{"t": "porn", "kind": "substring"}]})


if __name__ == "__main__":
    unittest.main(verbosity=2)
