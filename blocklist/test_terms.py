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
    exempt = set(payload.get("keyword_exempt_hosts", []))
    return lambda host: host_matches(host, tokens, substrings, never, exempt)


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

    def test_letter_elongation_collapses_to_the_base_word(self):
        """
        Prevents shouted spellings from slipping the list: `pooorn` is `porn`
        held down on the keyboard. Both reductions are emitted, so a term's real
        gemination survives (`ass` from `asssss`) while a mere double is left
        alone. Kept identical to normalize.js and TextNormalizer.swift.
        """
        self.assertIn("porn", token_variants("pooorn"))
        self.assertIn("نيك", token_variants(normalize("نيييك")))
        self.assertIn("ass", token_variants("asssss"))
        self.assertNotIn("pas", token_variants("pass"))


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


# --------------------------------------------------------------------------- #
# The generated Arabic tables
# --------------------------------------------------------------------------- #

class TestGeneratedArabic(unittest.TestCase):
    """
    Guards on `gen_terms_ar.py`'s output.

    The Arabic tier is ~5,600 of the ~5,800 terms in the compiled list, and
    almost all of it is machine-expanded. That is only safe while the
    expansion cannot reach an innocent word, so the checks below are the price
    of the size.
    """

    def setUp(self):
        sys.path.insert(0, str(Path(__file__).parent))
        import gen_terms_ar
        self.gen = gen_terms_ar
        self.arabic, self.latin = gen_terms_ar.build()

    def test_no_innocent_word_matches(self):
        """
        The check that caught the real one.

        `الزب` was emitted as an affixed form of the two-letter stem `زب`, and
        `الزبون` — the customer — strips its `ون` suffix down to exactly that.
        Every page with a customer-service section scored as pornography. The
        bare word `زبون` is clean, so this has to test the inflected forms a
        page actually contains, not the dictionary form.
        """
        hits = self.gen.false_positives(self.arabic)
        self.assertEqual(hits, [], f"innocent words would be blocked: {hits[:10]}")

    def test_generated_files_match_the_generator(self):
        """A hand-edit to a generated table is silently lost on the next run."""
        for name, rows in (("terms.ar-generated.tsv", self.arabic),
                           ("terms.ar-latn-generated.tsv", self.latin)):
            on_disk = {
                line.split("\t")[0]
                for line in (SRC / name).read_text(encoding="utf-8").splitlines()
                if line.strip() and not line.startswith("#")
            }
            self.assertEqual(
                on_disk, set(rows),
                f"{name} is out of sync — run: python3 gen_terms_ar.py")

    def test_fiqh_vocabulary_is_never_a_positive_term(self):
        """
        Islamic jurisprudence must stay reachable.

        نكاح is the marriage contract; عورة, جنابة and حيض are core fiqh. A
        filter that scores them as pornography blocks scholarship for exactly
        the audience this product is built for, which is a worse failure than
        missing a tube site.
        """
        payload = compiled()
        positives = {r["t"]: r["w"] for r in payload["terms"]}
        for word in ("نكاح", "عقد النكاح", "جنابة", "حيض", "طهارة", "محرم"):
            self.assertNotIn(normalize(word), positives,
                             f"{word} is fiqh vocabulary, not pornography")

    def test_zina_stays_a_tie_breaker(self):
        """
        Regression: `زنا` at 6.0 blocked a four-line islamqa fatwa outright.

        The page `ما حكم الزنا؟` scored 77 against a threshold of 7.5 on the
        strength of its title alone. It is also a poor signal in the other
        direction — adult sites advertise with سكس and نيك, not with the legal
        term — so it earns tie-breaker weight and no more.
        """
        positives = {r["t"]: r["w"] for r in compiled()["terms"]}
        for spelling in ("زنا", "الزنا"):
            weight = positives.get(normalize(spelling))
            if weight is not None:
                self.assertLessEqual(
                    weight, 3.0,
                    f"{spelling} at {weight} blocks fatwa pages")

    def test_fatwa_register_is_negative(self):
        """
        A short fatwa carries almost no signal except its subject.

        `والله أعلم` and the Q-and-A frame are what separate scholarship from a
        page that merely uses the same nouns, and without them the four-line
        case above has nothing to pull it back down.
        """
        negatives = {r["t"] for r in compiled()["negatives"]}
        for marker in ("والله أعلم", "السؤال", "الجواب", "فتوى"):
            self.assertIn(normalize(marker), negatives)

    def test_franco_terms_avoid_english_words(self):
        """
        `عريان` romanises to `aryan`, `معرص` to `mars`, `طيز` to `tare`.

        Every one of those is an ordinary English word, and a generated Franco
        spelling that collides with one blocks pages that have nothing to do
        with Arabic at all.
        """
        try:
            words = {w.strip().lower()
                     for w in open("/usr/share/dict/words", encoding="utf-8")}
        except OSError:
            self.skipTest("no system dictionary on this machine")
        collisions = sorted(set(self.latin) & words)
        self.assertEqual(collisions, [],
                         f"Franco terms collide with English: {collisions}")

    def test_arabic_coverage_is_substantial(self):
        """
        The floor the expansion exists to clear.

        Arabic adult content spans dialects that share little explicit
        vocabulary, so a few hundred MSA terms look comprehensive and catch a
        fraction of real pages.
        """
        by_lang: dict[str, int] = {}
        for row in compiled()["terms"]:
            by_lang[row["l"]] = by_lang.get(row["l"], 0) + 1
        self.assertGreaterEqual(by_lang.get("ar", 0) + by_lang.get("ar-latn", 0),
                                5000)


if __name__ == "__main__":
    unittest.main(verbosity=2)
