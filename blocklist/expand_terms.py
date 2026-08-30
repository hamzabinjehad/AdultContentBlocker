#!/usr/bin/env python3
"""
Generate Arabic surface forms and Franco-Arabic spellings from a small seed.

WHY GENERATION, NOT ENUMERATION
-------------------------------
"Collect every explicit word in every Arabic dialect" is not a finishable task.
Language is generative, dialects disagree, and slang moves faster than any list.
Hand-writing surface forms is the slowest possible way to lose that race.

Arabic, however, is unusually friendly to generation, and for two independent
reasons:

  1. **Root and pattern.** A root like ن-ي-ك yields نيك، ينيك، تنيك، منيوك،
     منيوكة، نياك mechanically. One root entered by hand produces a dozen forms
     nobody had to think of.

  2. **Franco-Arabic is a substitution cipher.** Arabic letters with no Latin
     equivalent map to digits by near-universal convention — ع→3، ح→7، خ→5،
     ق→8/2/q، ص→9، ط→6 — and the remaining letters have two or three accepted
     romanisations each. One Arabic word therefore expands to a bounded set of
     spellings people actually type. THIS is the tier that matters for
     hostnames, because hostnames are ASCII: the blocklist's own Arabic domains
     are `3arabicporn`, `a7larab`, `6arabs`, `aflamsexaraby` — exactly what this
     produces.

Output is a CANDIDATE list for human review, like `mine_terms.py`. Generation
is precise about morphology and completely blind to meaning: it will happily
produce a form that is also an innocent word, which is what `never_keyword.txt`
and the negatives list exist to catch.

    python3 expand_terms.py --franco terms/terms.ar.tsv | head -40
    python3 expand_terms.py --franco terms/terms.ar.tsv --tsv >> terms/terms.ar-latn.tsv
"""

from __future__ import annotations

import argparse
import itertools
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from terms import normalize  # noqa: E402

# Franco-Arabic romanisation. Order matters only for readability; every letter
# is independent. The digit forms are the ones that make this tier work — an
# all-alphabetic transliteration misses how people actually type.
#
# Kept deliberately SHORT per letter. Every extra variant multiplies the output
# combinatorially, and the rare spellings buy far fewer real matches than the
# noise they add.
FRANCO = {
    "ا": ["a"],      "ب": ["b"],       "ت": ["t"],   "ث": ["th", "s"],
    "ج": ["j", "g"], "ح": ["7", "h"],  "خ": ["5", "kh"],
    "د": ["d"],      "ذ": ["z", "dh"], "ر": ["r"],   "ز": ["z"],
    "س": ["s"],      "ش": ["sh", "ch"],"ص": ["9", "s"],
    "ض": ["d"],      "ط": ["6", "t"],  "ظ": ["z"],
    "ع": ["3", "a"], "غ": ["gh", "8"], "ف": ["f"],
    "ق": ["2", "q", "8"], "ك": ["k"],  "ل": ["l"],
    "م": ["m"],      "ن": ["n"],       "ه": ["h"],
    "و": ["o", "w", "ou"], "ي": ["i", "y", "ee"],
    "ء": ["2"],
}

# Cap the expansion per word. A six-letter word with three ambiguous letters is
# already 27 spellings; without a cap a long phrase explodes into thousands of
# candidates nobody will ever review.
MAX_PER_WORD = 12

# Arabic verb/noun patterns applied to a triliteral root. Each entry maps the
# root letters (1,2,3) into a surface form. These cover the forms that actually
# appear in explicit vocabulary rather than the full classical paradigm.
PATTERNS = [
    "{a}{b}{c}",           # فعل   — bare verbal noun
    "ي{a}{b}{c}",          # يفعل  — he does
    "ت{a}{b}{c}",          # تفعل  — she does
    "م{a}{b}و{c}",         # مفعول — passive participle (m)
    "م{a}{b}و{c}ه",        # مفعوله — passive participle (f)
    "{a}{b}ا{c}",          # فعال  — intensive / agent
    "{a}{b}{c}ه",          # فعله  — instance noun
    "{a}ا{b}{c}",          # فاعل  — active participle
    "{a}ا{b}{c}ه",         # فاعله — active participle (f)
]


def franco(word: str) -> list[str]:
    """
    Every plausible Franco-Arabic spelling of one Arabic word.

    ── THE SHORT-VOWEL PROBLEM ────────────────────────────────────────────
    A letter-by-letter transliteration is not what people write, and the gap is
    not cosmetic — it is the difference between this generator working and being
    useless. Arabic does not write short vowels, so `عرب` transliterates
    mechanically to `3rb`. But Franco-Arabic is PHONETIC: people type what they
    say, vowels included, so the real domains in our own blocklist are
    `3arabicporn`, `3arabporn`, `6arabs` — `3arab`, never `3rb`.
    Generating only the consonant skeleton matched none of them.
    So each word is emitted twice: as the bare skeleton, and with a short `a`
    inserted between consonants. Both spellings occur in the wild, and the pair
    is bounded — no combinatorial explosion, just double.
    """
    word = normalize(word)
    if not word or " " in word:
        return []

    choices = []
    for ch in word:
        if ch in FRANCO:
            choices.append(FRANCO[ch])
        elif ch.isascii():
            choices.append([ch])
        else:
            return []          # a character we have no mapping for

    # Long vowels and the digit-letters already carry a vowel sound, so
    # inserting after them produces spellings nobody writes ("3aa", "oa").
    carries_vowel = set("aeiou23579")

    out: list[str] = []
    seen: set[str] = set()
    for combo in itertools.product(*choices):
        skeleton = "".join(combo)
        variants = [skeleton]

        vocalised: list[str] = []
        for i, piece in enumerate(combo):
            vocalised.append(piece)
            last = piece[-1]
            nxt = combo[i + 1][0] if i + 1 < len(combo) else ""
            if (last not in carries_vowel and nxt
                    and nxt not in carries_vowel):
                vocalised.append("a")
        variants.append("".join(vocalised))

        for v in variants:
            if v and v not in seen:
                seen.add(v)
                out.append(v)
        if len(out) >= MAX_PER_WORD:
            break
    return out[:MAX_PER_WORD]


def from_root(root: str) -> list[str]:
    """Surface forms of a triliteral root, e.g. `نيك` -> نيك، ينيك، منيوك…"""
    letters = [c for c in normalize(root) if not c.isspace()]
    if len(letters) != 3:
        return []
    keys = dict(zip("abc", letters))
    return [normalize(p.format(**keys)) for p in PATTERNS]


def read_terms(path: Path) -> list[str]:
    out = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        term = line.split("\t")[0].strip()
        if term:
            out.append(term)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source", type=Path, nargs="?",
                    help="A .tsv of Arabic terms, or omit and use --roots.")
    ap.add_argument("--franco", action="store_true",
                    help="Emit Franco-Arabic spellings of each term.")
    ap.add_argument("--roots", nargs="*", default=[],
                    help="Triliteral roots to expand morphologically.")
    ap.add_argument("--weight", default="5.0")
    ap.add_argument("--tsv", action="store_true",
                    help="Emit term<TAB>weight ready to paste into a source file.")
    args = ap.parse_args()

    known = set()
    src = Path(__file__).parent / "terms"
    for name in ("terms.ar.tsv", "terms.ar-latn.tsv", "terms.en.tsv"):
        p = src / name
        if p.exists():
            known |= {normalize(t) for t in read_terms(p)}

    produced: dict[str, str] = {}          # form -> what it came from

    for root in args.roots:
        for form in from_root(root):
            produced.setdefault(form, f"root {root}")

    if args.source:
        for term in read_terms(args.source):
            if args.franco:
                for spelling in franco(term):
                    produced.setdefault(spelling, term)
            else:
                produced.setdefault(normalize(term), term)

    new = {f: s for f, s in produced.items() if f and f not in known}

    if args.tsv:
        for form, origin in sorted(new.items()):
            print(f"{form}\t{args.weight}\t# from {origin}")
    else:
        print(f"{'candidate':<22}{'from':<20}")
        print("-" * 44)
        for form, origin in sorted(new.items()):
            print(f"{form:<22}{origin:<20}")

    print(f"\n{len(produced)} forms generated, {len(new)} not already listed.",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
