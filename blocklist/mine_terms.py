#!/usr/bin/env python3
"""
Mine candidate keyword terms out of the domain blocklist we already have.

WHY THIS EXISTS
---------------
Hand-writing a term list does not scale and never finishes. Published "Arabic
bad word" lists do not solve it either, for a reason worth stating plainly:
almost all of them are lists of INSULTS, and an insult is not pornography. A
filter built from one blocks half of ordinary angry conversation while missing
the actual material.

But we are already holding the answer. The blocklist is ~982,000 domains that
are known adult sites, and their hostnames are written in exactly the
vocabulary we are trying to enumerate — in Arabic transliteration, in English,
and in whatever spellings the people who registered them actually use. Counting
tokens across that corpus produces a candidate list that is:

  * real — every term demonstrably appears in adult domain names,
  * ranked — by how often it actually occurs, not by guesswork,
  * current — it moves when the published list moves,
  * transliteration-native — it surfaces `sks`, `sharmota`, `ebahia` without
    anyone having to think of them.

WHAT THIS IS NOT
----------------
Output is a CANDIDATE list for human review, never a term list. It cannot be
wired straight in, because frequency alone cannot tell the difference between a
word that means something explicit and a word that merely keeps company with
one — `free`, `video`, `hd`, `tube`, `best` all rank near the top. Everything
here is a suggestion for a person to accept, weight, and classify as `token` or
`substring`.

    python3 mine_terms.py --list ../seed/domains_core.txt --top 200
    python3 mine_terms.py --list ../dist/domains.txt --min-count 40 --arabic-only
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from terms import normalize, tokenize  # noqa: E402

# Public suffixes and generic web furniture. These dominate any frequency count
# and say nothing about content — every corpus of domains is full of them.
STOP = {
    # suffixes — including the cheap new gTLDs, which dominate any count of a
    # modern adult-domain corpus and carry no meaning whatsoever
    "com", "net", "org", "info", "biz", "xyz", "top", "site", "online", "club",
    "vip", "cc", "tv", "co", "io", "me", "ru", "de", "fr", "nl", "pl", "it",
    "es", "br", "jp", "cn", "in", "uk", "us", "ws", "mobi", "pro", "live",
    "link", "click", "space", "website", "store", "shop", "app", "blog",
    "win", "one", "life", "world", "today", "fun", "art", "cam", "wtf",
    "cfd", "icu", "sbs", "cyou", "lat", "buzz", "homes", "casa", "ink",
    "bond", "quest", "beauty", "rest", "monster", "bet", "kim", "wiki",
    "name", "work", "help", "lol", "itch", "makeup", "hair", "skin", "boats",
    "yachts", "motorcycles", "autos", "christmas", "sale", "gift", "run",
    "fit", "gold", "zone", "guru", "ninja", "digital", "agency", "solutions",
    "services", "center", "company", "email", "systems", "network", "tech",
    "cloud", "host", "server", "domain", "page", "pw", "su", "tk", "ml",
    "ga", "cf", "gq", "blogspot", "wordpress", "tumblr", "weebly", "wixsite",
    # web furniture
    "www", "web", "site", "sites", "page", "pages", "home", "index", "main",
    "cdn", "img", "images", "static", "media", "content", "data", "files",
    "download", "downloads", "stream", "streaming", "player", "watch",
    "video", "videos", "vid", "vids", "movie", "movies", "film", "films",
    "clip", "clips", "photo", "photos", "pic", "pics", "picture", "pictures",
    "gallery", "album", "tube", "channel", "network", "portal", "hub",
    # generic modifiers
    "free", "best", "top", "new", "hot", "big", "hd", "full", "all", "the",
    "my", "your", "our", "you", "get", "go", "now", "here", "more", "plus",
    "pro", "max", "super", "mega", "ultra", "real", "true", "only", "just",
    "and", "for", "with", "from", "this", "that", "one", "two", "first",
    "daily", "night", "day", "time", "world", "global", "asia", "euro",
    "american", "russian", "indian", "chinese", "japanese", "korean",
    "online", "mobile", "app", "apps", "net", "link", "links", "list",
    # numerals spelled out and stray fragments
    "com1", "co1", "xyz1", "http", "https",
}

# A token has to look like a word, not an id. Domain lists are full of hashes,
# tracker ids and random subdomains, and every one of them is a unique token.
WORDLIKE = re.compile(r"^[a-z؀-ۿ][a-z0-9؀-ۿ]{2,19}$")
MOSTLY_DIGITS = re.compile(r"^[a-z]?[0-9]+[a-z]?$")

ARABIC = re.compile(r"[؀-ۿ]")
# Franco-Arabic is Latin script carrying digit-letters, or one of the consonant
# clusters Arabic transliteration produces and English essentially never does.
FRANCO_DIGITS = re.compile(r"[23579]")
FRANCO_CLUSTERS = ("kh", "gh", "sh", "th", "dh", "aa", "ee", "ou", "ay")


def looks_arabic(token: str) -> bool:
    """
    Arabic script, or a plausible Franco-Arabic romanisation.

    The load-bearing distinction is WHERE the digits are, and it is the same one
    `terms.token_variants` already makes: a TRAILING digit run is numbering
    (`porno365`, `xxx777`, `viet69` — Russian and Vietnamese sites, not Arabic),
    while a leading or embedded digit is a Franco-Arabic letter (`3arab`,
    `a7larab`, `6arabs`). Matching any digit anywhere floods the Arabic view
    with every numbered domain on the internet, which is exactly what the first
    version of this function did.
    """
    if ARABIC.search(token):
        return True
    core = token.rstrip("0123456789")
    if len(core) >= 3 and FRANCO_DIGITS.search(core):
        return True
    return sum(c in token for c in FRANCO_CLUSTERS) >= 2


def mine(path: Path, min_count: int, arabic_only: bool) -> list[tuple[str, int]]:
    counts: Counter[str] = Counter()
    domains = 0

    with path.open(encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            domains += 1
            # Only the registrable part carries intent; the public suffix is
            # noise, and a token counted once per domain stops one prolific
            # host's subdomains from dominating the ranking.
            for token in set(tokenize(normalize(line))):
                if token in STOP or MOSTLY_DIGITS.match(token):
                    continue
                if not WORDLIKE.match(token):
                    continue
                counts[token] += 1

    print(f"scanned {domains:,} domains, {len(counts):,} distinct tokens",
          file=sys.stderr)

    rows = [(t, c) for t, c in counts.items() if c >= min_count]
    if arabic_only:
        rows = [(t, c) for t, c in rows if looks_arabic(t)]
    return sorted(rows, key=lambda r: (-r[1], r[0]))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--list", type=Path,
                    default=Path(__file__).parent.parent / "seed/domains_core.txt")
    ap.add_argument("--min-count", type=int, default=20,
                    help="Ignore tokens appearing in fewer domains than this.")
    ap.add_argument("--top", type=int, default=150)
    ap.add_argument("--arabic-only", action="store_true",
                    help="Only Arabic script and plausible Franco-Arabic.")
    ap.add_argument("--tsv", action="store_true",
                    help="Emit term<TAB>weight ready to paste into a source "
                         "file, instead of a ranked table.")
    args = ap.parse_args()

    if not args.list.exists():
        print(f"no such list: {args.list}", file=sys.stderr)
        return 1

    rows = mine(args.list, args.min_count, args.arabic_only)[:args.top]

    # Existing terms are marked rather than dropped: seeing that a mined token
    # is already covered is what tells you the miner agrees with the hand list.
    known = set()
    src = Path(__file__).parent / "terms"
    for name in ("terms.ar.tsv", "terms.ar-latn.tsv", "terms.en.tsv",
                 "host_terms.tsv"):
        p = src / name
        if p.exists():
            for line in p.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line and not line.startswith("#"):
                    known.add(normalize(line.split("\t")[0]))

    if args.tsv:
        for token, count in rows:
            if token not in known:
                print(f"{token}\t5.0\t# seen in {count} domains")
        return 0

    print(f"\n{'token':<24}{'domains':>9}   status")
    print("-" * 52)
    for token, count in rows:
        print(f"{token:<24}{count:>9}   {'already listed' if token in known else ''}")
    new = sum(1 for t, _ in rows if t not in known)
    print(f"\n{new} of {len(rows)} are not yet in the term list.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
