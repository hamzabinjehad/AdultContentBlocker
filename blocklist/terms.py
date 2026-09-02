#!/usr/bin/env python3
"""
Keyword layer: normalisation, hostname matching, and the `terms.json` compiler.

WHY A KEYWORD LAYER EXISTS AT ALL
---------------------------------
`docs/THREAT_MODEL.md` Part 1 names the gap this closes:

    | New domain, registered today | **Open in blocklist mode** |

A domain list is a losing race by construction — someone else adds domains, we
copy them, and the delay between the two is the exposure. A keyword rule catches
`arab-sex-tube.com` on the day it is registered, without anyone having listed it.

WHAT EACH LAYER CAN SEE — the constraint that shapes everything here
--------------------------------------------------------------------
    macOS NEFilterDataProvider : hostname only (SNI). HTTPS hides the path.
    Chrome declarativeNetRequest: the full URL — host, path and query.
    Content script             : the rendered page text.

So "block the page if its *text* is explicit" is impossible at the network
layer and lives in the content script. What the network layer can do is match
the hostname and URL — and because the socket filter does it, that works for
every application on the Mac rather than Chrome alone.

THE `essex` PROBLEM
-------------------
This is the single highest-impact risk in the feature, and every design choice
below exists because of it. Naive substring matching on hostnames blocks
`essex.gov.uk`, `sussex.ac.uk`, `middlesex.edu`, `analytics.google.com` and
`therapist.com`. A wrong hostname verdict takes out an *entire site*, not one
page, and it is exactly the kind of silent over-block that makes someone rip the
whole tool out.

Four defences, all required, all tested in `test_terms.py`:

  1. Token matching, not substring, for anything ambiguous (`kind: "token"`).
     `essex` is one token and never equals `sex`.
  2. `kind: "substring"` is reserved for hand-curated strings verified to appear
     in no innocent word, and even then it runs against a host with the
     `never_keyword` tokens removed first — so `essex-porn.com` is still caught
     while `essex.gov.uk` is not.
  3. The existing two-tier safety rail in `sources.json` applies unchanged.
  4. A `never_keyword` list of known-innocent tokens, checked first.

ARABIC
------
Two tiers are needed and the second is the one that matters for hostnames:

  * `ar`      — Arabic script. Matches page text, and IDN hostnames after
                punycode decoding.
  * `ar-latn` — romanised Franco-Arabic (`sks`, `jins`, `3ahira`, `sharmota`),
                including the digit-for-letter substitutions 2→ء 3→ع 5→خ 7→ح
                9→ص. Arabic-audience adult domains are overwhelmingly ASCII, so
                an Arabic-script-only list would match almost no hostnames.

CROSS-LANGUAGE CONTRACT
-----------------------
`normalize()` and `host_matches()` are reimplemented in JavaScript
(`extension/lib/normalize.js`) and Swift (`macos/Hisn/Inspection.swift`,
`BlocklistStore.hostMatchesTerm`). Nothing at build time forces the three to
agree, and a divergence is silent: a signed term that simply never matches on
the device. `terms/normalize_cases.json` and `terms/host_cases.json` are the
shared fixture tables all three test suites assert against — the same device
this repo already uses for `test_collapse_lookup_invariant`.

    python3 terms.py compile --src terms --out ../dist/terms.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
from pathlib import Path

# --------------------------------------------------------------------------- #
# Normalising
# --------------------------------------------------------------------------- #

# Bidirectional and joiner controls. These are invisible, survive a copy-paste,
# and are the cheapest possible way to write a term that looks identical to a
# human and matches nothing at all.
_INVISIBLE = dict.fromkeys(map(ord, "​‌‍‎‏؜﻿"))

# Tatweel (kashida) — pure decoration, stretches a word without changing it.
_TATWEEL = dict.fromkeys([0x0640])

# Harakat/tashkeel. Optional in written Arabic, so the same word appears both
# with and without them and the two must compare equal.
_TASHKEEL = dict.fromkeys(
    list(range(0x064B, 0x0660)) + [0x0670] + list(range(0x06D6, 0x06EE))
)

# Orthographic folds. Arabic writers are inconsistent about all of these, and a
# term list that distinguishes them matches roughly half of what it should.
_FOLD = {
    ord("أ"): "ا", ord("إ"): "ا", ord("آ"): "ا", ord("ٱ"): "ا",
    ord("ى"): "ي", ord("ئ"): "ي",
    ord("ؤ"): "و",
    ord("ة"): "ه",
}

# Arabic-Indic and Extended Arabic-Indic digits.
_DIGITS = {0x0660 + i: str(i) for i in range(10)}
_DIGITS.update({0x06F0 + i: str(i) for i in range(10)})


def normalize(text: str) -> str:
    """
    Reduce text to the one spelling the term list is written in.

    Order matters. NFKC first, because it collapses the Arabic presentation
    forms (U+FB50–FDFF, U+FE70–FEFF) that copied text is full of; the folds
    below assume the standard code points that NFKC produces.
    """
    if not text:
        return ""
    s = unicodedata.normalize("NFKC", text)
    s = s.translate(_INVISIBLE)
    s = s.translate(_TATWEEL)
    s = s.translate(_TASHKEEL)
    s = s.translate(_FOLD)
    s = s.translate(_DIGITS)
    # Latin diacritics: decompose, drop the combining marks, recompose. Arabic
    # tashkeel is also category Mn but was removed above, so this is a no-op for
    # Arabic and does the work for "café" -> "cafe".
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return unicodedata.normalize("NFC", s).lower().strip()


# Letters and digits in any script, which is what a "word" is here. Splitting on
# the complement means the tokenizer needs no per-language rules.
_SPLIT = re.compile(r"[^0-9a-z؀-ۿݐ-ݿ]+")


def tokenize(text: str) -> list[str]:
    """Split already-normalised text into word tokens."""
    return [t for t in _SPLIT.split(text) if t]


# Bounded clitic affixes. Arabic glues the definite article and prepositions
# onto the following word, so `الإباحية` must match the term `اباحيه` — but the
# stripping has to be BOUNDED, because unbounded stemming makes `جنس` (sex) match
# `الجنسية` (nationality) and blocks every passport page in Arabic.
_AR_PREFIXES = ("وبال", "فبال", "وال", "بال", "فال", "كال", "لل", "ال",
                "و", "ف", "ب", "ك", "ل")
_AR_SUFFIXES = ("ات", "ون", "ين", "ها", "هم", "هن", "ك", "ي")


def token_variants(token: str) -> set[str]:
    """
    A token plus the bounded affix-stripped forms worth also testing.

    Deliberately shallow: one prefix and one suffix, never recursive. Each extra
    layer of stripping buys a few more true matches and a great many false ones.
    """
    out = {token}
    for p in _AR_PREFIXES:
        if token.startswith(p) and len(token) - len(p) >= 3:
            out.add(token[len(p):])
            break
    for s in _AR_SUFFIXES:
        if token.endswith(s) and len(token) - len(s) >= 3:
            out.add(token[: -len(s)])
            break
    # Trailing digits are decoration on a hostname label ("sex123"), but leading
    # or embedded digits may be Franco-Arabic letters ("3ahira"), so only the
    # trailing run is stripped.
    stripped = token.rstrip("0123456789")
    if stripped and stripped != token and len(stripped) >= 3:
        out.add(stripped)
    return out


# --------------------------------------------------------------------------- #
# Hostnames
# --------------------------------------------------------------------------- #

def decode_punycode(host: str) -> str:
    """
    Turn `xn--`-encoded labels back into Unicode before matching.

    Without this an Arabic-script domain is invisible to the entire keyword
    layer — the bytes on the wire are ASCII and share no characters with any
    Arabic term. A label that fails to decode is kept verbatim rather than
    dropped: an undecodable label is not a reason to stop checking the rest.
    """
    labels = []
    for label in host.split("."):
        if label.startswith("xn--"):
            try:
                labels.append(label[4:].encode("ascii").decode("punycode"))
                continue
            except Exception:
                pass
        labels.append(label)
    return ".".join(labels)


def host_tokens(host: str) -> set[str]:
    """Every token form of a hostname worth testing against the term list."""
    normalized = normalize(decode_punycode(host.strip().strip(".")))
    out: set[str] = set()
    for token in tokenize(normalized):
        out |= token_variants(token)
    return out


def host_matches(
    host: str,
    token_terms: set[str],
    substring_terms: set[str],
    never_keyword: set[str],
) -> bool:
    """
    The reference client implementation, ported to Swift and JS.

    Kept here, in Python, so `test_terms.py` can assert the same fixture table
    the other two suites assert — the pattern `test_collapse_lookup_invariant`
    established for the domain list.
    """
    normalized = normalize(decode_punycode(host.strip().strip(".")))
    tokens = host_tokens(host)

    # An innocent token disables nothing on its own; it only stops a SUBSTRING
    # term from matching the letters inside it. `essex-porn.com` must still be
    # caught, so the guard removes the innocent token and re-tests the remainder
    # rather than exempting the whole host.
    innocent = tokens & never_keyword
    if tokens & token_terms - never_keyword:
        return True

    haystack = normalized
    for word in innocent:
        haystack = haystack.replace(word, "")
    return any(term in haystack for term in substring_terms)


# --------------------------------------------------------------------------- #
# Compiling
# --------------------------------------------------------------------------- #

MIN_TERMS = 200
MIN_SUBSTRING_LEN = 4


def read_tsv(path: Path) -> list[dict]:
    """`term<TAB>weight` per line; `#` comments and blanks ignored."""
    rows = []
    if not path.exists():
        return rows
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        term = normalize(parts[0])
        if not term:
            print(f"{path.name}:{lineno}: not a usable term, skipped: {raw!r}",
                  file=sys.stderr)
            continue
        try:
            weight = float(parts[1]) if len(parts) > 1 else 5.0
        except ValueError:
            print(f"{path.name}:{lineno}: bad weight, skipped: {raw!r}",
                  file=sys.stderr)
            continue
        rows.append({"t": term, "w": weight})
    return rows


def read_list(path: Path) -> list[str]:
    if not path.exists():
        return []
    return sorted({
        normalize(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
        if normalize(line)
    })


def compile_terms(src: Path) -> dict:
    """Build the `terms.json` payload from the hand-maintained sources."""
    langs = {
        # Hand-curated first, generated second. The loop below keeps the first
        # weight it sees for a term, so a weight chosen by a human always beats
        # the one `gen_terms_ar.py` derived for the same spelling.
        "ar": read_tsv(src / "terms.ar.tsv")
              + read_tsv(src / "terms.ar-generated.tsv"),
        "ar-latn": read_tsv(src / "terms.ar-latn.tsv")
                   + read_tsv(src / "terms.ar-latn-generated.tsv"),
        "en": read_tsv(src / "terms.en.tsv"),
    }

    terms, seen = [], set()
    for lang, rows in langs.items():
        for row in rows:
            if row["t"] in seen:
                continue
            seen.add(row["t"])
            terms.append({"t": row["t"], "w": row["w"], "l": lang})

    negatives = [
        {"t": r["t"], "w": -abs(r["w"])}
        for r in read_tsv(src / "negatives.tsv")
        + read_tsv(src / "negatives.ar-generated.tsv")
    ]
    never_keyword = read_list(src / "never_keyword.txt")

    host_terms = []
    for lineno, raw in enumerate(
        (src / "host_terms.tsv").read_text(encoding="utf-8").splitlines(), 1
    ):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        term = normalize(parts[0])
        kind = parts[1].strip() if len(parts) > 1 else "token"
        if not term:
            continue
        if kind not in ("token", "substring"):
            raise SystemExit(f"host_terms.tsv:{lineno}: unknown kind {kind!r}")
        # The guard that keeps a careless edit from blocking half the web: a
        # short substring term is how `sex` ends up matching `essex.gov.uk`.
        if kind == "substring" and len(term) < MIN_SUBSTRING_LEN:
            raise SystemExit(
                f"host_terms.tsv:{lineno}: {term!r} is too short to be a "
                f"substring term (min {MIN_SUBSTRING_LEN}). Use kind=token."
            )
        if term in never_keyword:
            raise SystemExit(
                f"host_terms.tsv:{lineno}: {term!r} is also in never_keyword.txt"
            )
        host_terms.append({"t": term, "kind": kind})

    payload = {
        "schema": 1,
        "defaults": {"sensitivity": 50},
        "terms": sorted(terms, key=lambda r: r["t"]),
        "negatives": sorted(negatives, key=lambda r: r["t"]),
        "host_terms": sorted(host_terms, key=lambda r: r["t"]),
        "never_keyword": never_keyword,
        "exempt_domains": read_list(src / "exempt_domains.txt"),
    }
    validate(payload)
    return payload


def validate(payload: dict) -> None:
    """
    Refuse to emit a list that would quietly stop working.

    Mirrors the domain list's own plausibility floors (`build.py` source count,
    the CI 200k check, and both clients). A term list that silently collapses to
    nothing looks exactly like one that is working.
    """
    terms = payload["terms"]
    if len(terms) < MIN_TERMS:
        raise SystemExit(
            f"only {len(terms)} terms — refusing to emit a degraded list "
            f"(minimum {MIN_TERMS})"
        )
    by_lang: dict[str, int] = {}
    for row in terms:
        by_lang[row["l"]] = by_lang.get(row["l"], 0) + 1
    for required in ("ar", "ar-latn", "en"):
        if not by_lang.get(required):
            raise SystemExit(
                f"no {required!r} terms — Arabic coverage is a requirement, "
                f"not a nice-to-have"
            )
    if not payload["host_terms"]:
        raise SystemExit("no host_terms — the network keyword layer would be a no-op")


def lang_counts(payload: dict) -> dict[str, int]:
    out: dict[str, int] = {}
    for row in payload["terms"]:
        out[row["l"]] = out.get(row["l"], 0) + 1
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("command", choices=["compile"])
    ap.add_argument("--src", type=Path, default=Path(__file__).parent / "terms")
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()

    payload = compile_terms(args.src)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":"),
                   sort_keys=True),
        encoding="utf-8",
    )
    counts = lang_counts(payload)
    print(f"wrote {args.out} — {len(payload['terms'])} terms "
          f"({', '.join(f'{k}:{v}' for k, v in sorted(counts.items()))}), "
          f"{len(payload['host_terms'])} host terms, "
          f"{len(payload['never_keyword'])} never-keyword guards")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
