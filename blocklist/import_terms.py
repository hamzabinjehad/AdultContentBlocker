#!/usr/bin/env python3
"""
Import multilingual terms, and validate every one against our own corpus.

WHY IMPORTING A "BAD WORDS" LIST DIRECTLY IS WRONG
--------------------------------------------------
The canonical multilingual source (LDNOOBW, the list Shutterstock uses) is a
list of PROFANITY, and profanity is not pornography. Sampling it makes the gap
obvious and expensive:

    es  Asesinato      "murder"          — not sexual in any sense
    de  arschloch      an insult         — appears in no adult domain
    ru  byk            "bull"            — an insult
    ru  chernozhopyi   a racial slur     — blocking it blocks no porn
    fr  baiser         "kiss", and also  — genuinely ambiguous
    tr  am             two letters       — matches half the internet

Shipping that wholesale would block news about a murder, an argument on a
forum, and any French page mentioning a kiss, while adding almost nothing to
what this product is for. It would also be unreviewable: nobody here reads
Turkish, Thai and Korean well enough to hand-check 27 languages.

THE VALIDATOR WE ALREADY OWN
----------------------------
The blocklist is ~982,000 domains that are known adult sites, and their
hostnames are written in the vocabulary of adult content in every language its
audiences speak. That makes it an objective test nobody has to be a native
speaker to apply:

    a term that appears in adult domain names is adult vocabulary;
    a term that appears in none of them is not, whatever else it may be.

`asesinato` survives no such test. `porno`, `sexo`, `nackt`, `desnudo` and
`porno-russkoe` all do. The corpus decides, in every language at once.

SAFETY BOUNDARY, DELIBERATE AND ABSOLUTE
----------------------------------------
Imported terms are written to the page-text scoring tier ONLY. They never enter
`host_terms.tsv`, because a hostname verdict takes out an entire site and these
terms have had no native-speaker review — only a statistical one. Page-text
scoring sums many weighted signals against a threshold, with negatives and
exempt domains as backstops, so one imprecise term there cannot block a page by
itself. That asymmetry is the whole reason this import is safe to ship.

    python3 import_terms.py --corpus ../seed/domains_core.txt --min-hits 3
"""

from __future__ import annotations

import argparse
import re
import sys
import urllib.request
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from terms import normalize  # noqa: E402

BASE = ("https://raw.githubusercontent.com/LDNOOBW/"
        "List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words/master/")

# `en` and `ar` are hand-curated here already, and ours are several times the
# size of theirs; re-importing would only dilute weights we chose deliberately.
# `tlh` is Klingon, `eo` Esperanto, `kab` Kabyle — no adult-domain audience to
# speak of, and nothing to validate against.
# Scope: Arabic, English and German only.
#
# The earlier 22-language import is gone on purpose. Corpus validation cut
# 2,156 offered terms to 66, and reviewing those 66 showed the last mile cannot
# be automated: it admitted personal names (`anita`, `peter`, `mona`), a Swedish
# city and university (`lund`, `pitt`), the German word for "please" (`bitte`),
# slurs, identity terms, and clinical anatomy. Every one of those genuinely
# appears in adult domain names; none of them is safe to block.
#
# Shipping languages nobody here can read means shipping that class of error
# unseen. Three languages that can actually be reviewed beat twenty-two that
# cannot. `de` stays imported because the corpus evidence for German is strong
# and the results are checkable; `ar` and `en` are hand-curated instead, and
# ours are already several times the size of what this source offers.
KEEP = {"de"}
SKIP: set[str] = set()

# Below this a term matches too much to be safe at any weight. Two- and
# three-letter entries ("am", "kos") are the ones that turn a filter into an
# outage.
MIN_LEN = 4

# Terms that survive validation but are known to be ambiguous in ordinary use.
# The corpus cannot see context, so this is the one place native judgement is
# encoded by hand rather than derived.
AMBIGUOUS = {
    "baiser",    # fr: "kiss" at least as often as the other reading
    "bander",    # fr: "to bandage"
    "queue",     # fr: a queue, overwhelmingly
    "chatte",    # fr: a female cat
    "coq",       # fr: rooster — the national symbol
    "pipe",      # fr: a pipe
    "sperma",    # several: clinical, appears in medical text
    "vagina",    # clinical anatomy
    "penis",     # clinical anatomy
    "anus",      # clinical anatomy
    "negro",     # a slur, not adult vocabulary — different problem, not ours
    "bimbo",     # an insult
    "caca",      # es/pt: "poop"
    "asno",      # es: "donkey"
    "mama",      # pt: "mother" at least as often
    "hard",      # English, and everywhere
    "comer",     # pt: "to eat"
    "shit",      # profanity, not adult content
    "maal",      # hi: "goods/stuff" in ordinary use
    # An identity and a clinical term. Blocking it blocks LGBT health and
    # rights material and no pornography whatsoever — precisely the kind of
    # collateral damage that makes a filter indefensible.
    "homosexual",

    # ── WHAT CORPUS VALIDATION CANNOT SEE ──────────────────────────────────
    # Every term below passed the corpus test — it genuinely appears in many
    # adult domain names — and every one is still wrong to ship. The corpus
    # proves association, not meaning, and it is blind to four things:
    #
    #   personal names, which adult sites use constantly
    "anita", "peter", "mona", "bobo", "tanga",
    #   place names — Lund is a Swedish city and university, Pitt a US one
    "lund", "pitt",
    #   ordinary words in OTHER languages than the one that listed them.
    #   `bitte` is German for "please"; `bite` is an everyday English verb;
    #   `trio` a musical ensemble; `saco` a bag; `folle` simply "crazy".
    "bitte", "bite", "trio", "saco", "folle", "corno", "pipi",
    #   slurs, which are a different problem and blocking them blocks no porn
    "nigger", "gouine", "satan",
    #   identity terms — blocking these takes out health, legal and rights
    #   material and nothing else
    "travesti", "lesbica",
    #   clinical anatomy, which is what medical pages are made of. German
    #   `Nippel` is also a plumbing fitting.
    "vulva", "clitoris", "nippel",
    #   general profanity: not this product's business
    "merde", "porra", "bosta", "sacanagem", "arsch", "asshole", "pissen",
    "kont", "luder", "slet", "hoer",
}


def fetch(lang: str) -> list[str]:
    req = urllib.request.Request(BASE + lang, headers={"User-Agent": "hisn"})
    with urllib.request.urlopen(req, timeout=30) as fh:
        text = fh.read().decode("utf-8", "replace")
    return [w.strip() for w in text.splitlines() if w.strip()]


def load_corpus(path: Path) -> Counter:
    """Index the domain corpus ONCE into a token count.

    The first version kept the corpus as one blob and ran a bounded regex per
    term. That is O(terms x corpus): 2,156 terms against a 5-million-domain
    list is ~200GB of scanning and does not finish. Tokenising once and
    counting is O(corpus + terms) and takes seconds.

    Splitting on the complement of letters is also exactly the boundary rule we
    want, so the index and the delimiting test are the same operation rather
    than two that could disagree.
    """
    counts: Counter = Counter()
    with path.open(encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip().lower()
            if not line or line.startswith("#"):
                continue
            for token in SPLIT.split(line):
                if token:
                    counts[token] += 1
    return counts


# Letters only: digits and separators are boundaries. `porno365` therefore
# yields `porno`, and `st-reet` can never yield the Dutch `reet` as anything
# other than the standalone word it is not.
SPLIT = re.compile(r"[\W\d_]+", re.UNICODE)


# A match only counts when the term is DELIMITED — bounded by a separator, a
# digit, or the edge of a label. Raw substring counting was the first attempt
# and it failed exactly the way naive hostname matching fails:
#
#     reet   155 "hits"  — every one of them inside  st-reet
#     kont    80 "hits"  — inside  kont-akt
#     olla    85 "hits"  — inside  c-olla
#     pina   139 "hits"  — inside  s-pina
#     maal   109 "hits"  — inside  nor-maal
#
# Those would have shipped as Dutch, Swedish and Hindi "adult vocabulary" and
# scored against every page containing the word "street". Requiring a boundary
# on both sides drops them and keeps `porno`, `sexo`, `milf`, `fick`, which do
# appear as segments of their own.
def delimited_hits(corpus: Counter, term: str) -> int:
    return corpus[term]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--corpus", type=Path,
                    default=Path(__file__).parent.parent / "seed/domains_core.txt")
    ap.add_argument("--min-hits", type=int, default=3,
                    help="Domains a term must appear in to be admitted.")
    ap.add_argument("--weight", type=float, default=4.0,
                    help="Weight for imported terms. Lower than hand-curated "
                         "ones on purpose: these had statistical review, not "
                         "native review.")
    ap.add_argument("--out", type=Path,
                    default=Path(__file__).parent / "terms" / "terms.intl.tsv")
    args = ap.parse_args()

    corpus = load_corpus(args.corpus)
    print(f"corpus: {sum(corpus.values()):,} tokens, "
          f"{len(corpus):,} distinct", file=sys.stderr)

    kept: list[tuple[str, str, int]] = []
    stats: dict[str, tuple[int, int]] = {}

    langs = [l for l in sorted(fetch_langs()) if l in KEEP]
    for lang in langs:
        try:
            words = fetch(lang)
        except Exception as exc:
            print(f"  {lang}: fetch failed ({exc})", file=sys.stderr)
            continue

        admitted = 0
        for raw in words:
            term = normalize(raw)
            if len(term) < MIN_LEN or term in AMBIGUOUS:
                continue
            if not re.fullmatch(r"[^\W\d_]+", term, re.UNICODE):
                continue          # multi-word or punctuated: skip
            hits = delimited_hits(corpus, term)
            if hits >= args.min_hits:
                kept.append((term, lang, hits))
                admitted += 1
        stats[lang] = (len(words), admitted)
        print(f"  {lang}: {admitted:>3} of {len(words):>3} admitted",
              file=sys.stderr)

    # Dedupe, keeping the language that saw the most corpus evidence.
    best: dict[str, tuple[str, int]] = {}
    for term, lang, hits in kept:
        if term not in best or hits > best[term][1]:
            best[term] = (lang, hits)

    lines = [
        "# Imported multilingual terms — page-text scoring tier ONLY.",
        "#",
        "# GENERATED by import_terms.py. Do not hand-edit; re-run instead.",
        "#",
        "# Every term below was taken from the LDNOOBW multilingual obscenity",
        "# list and then VALIDATED against our own blocklist corpus: it appears",
        f"# in at least {args.min_hits} known adult domain names. That test is what",
        "# separates adult vocabulary from ordinary profanity, in languages",
        "# nobody here can hand-check. Words like the Spanish 'asesinato'",
        "# (murder) and the German 'arschloch' are in the source list and are",
        "# absent below, because no adult domain is named after them.",
        "#",
        f"# Weight is a flat {args.weight}, below every hand-curated term: these",
        "# passed a statistical review, not a native-speaker one.",
        "#",
        "# THESE NEVER ENTER host_terms.tsv. A hostname verdict takes out a",
        "# whole site; page-text scoring sums many signals against a threshold",
        "# with negatives and exempt domains as backstops. Only the second is",
        "# safe for terms reviewed this way.",
        "",
    ]
    for term, (lang, hits) in sorted(best.items()):
        lines.append(f"{term}\t{args.weight}\t# {lang}, seen in {hits} domains")
    args.out.write_text("\n".join(lines) + "\n", encoding="utf-8")

    total_in = sum(v[0] for v in stats.values())
    print(f"\nwrote {args.out}", file=sys.stderr)
    print(f"  {len(best)} terms admitted of {total_in} offered "
          f"({100 * len(best) / max(1, total_in):.0f}%) across "
          f"{len(stats)} languages", file=sys.stderr)
    return 0


def fetch_langs() -> list[str]:
    import json
    api = ("https://api.github.com/repos/LDNOOBW/"
           "List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words/contents/")
    req = urllib.request.Request(api, headers={
        "User-Agent": "hisn", "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=30) as fh:
        items = json.load(fh)
    return [i["name"] for i in items
            if i["type"] == "file" and "." not in i["name"] and len(i["name"]) <= 6]


if __name__ == "__main__":
    raise SystemExit(main())
