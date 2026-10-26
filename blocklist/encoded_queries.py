#!/usr/bin/env python3
"""Generate URL rules for explicit Arabic search queries.

    python3 blocklist/encoded_queries.py            # write extension/rules/encoded_queries.json
    python3 blocklist/encoded_queries.py --check    # fail if stale

WHY THIS LAYER EXISTS
---------------------
`build.py`'s keyword rules match ASCII terms in a URL. Arabic terms are skipped
there for a good reason: Chrome serialises a URI, so the bytes on the wire are
percent-encoded UTF-8, and a rule containing Arabic characters can make Chrome
reject a whole ruleset. The consequence is a real coverage gap for the audience
this product is named for: a search for `سكس` never matches a URL rule, and
only the page-text layer sees it after the results page has loaded.

This generator closes exactly that gap, and only that gap: it percent-encodes
strong, single-token Arabic terms from the SIGNED vocabulary and matches them
in a recognised query parameter. It deliberately does not match paths, so an
ordinary article URL that happens to contain the word is left to the text
layer and its contextual negatives.

BOUNDARIES
----------
RE2 has no lookahead, so the "the term ends here" check is a short alternation
of what may follow the encoded term: end of URL, `&`, `#`, an encoded space
(`%20`) or plus (`%2B`). A following `%D8`/`%D9` is another Arabic letter and
fails the boundary — which is what keeps `سكس` from matching `سكسونيا`
(Saxony). That case is a test, not an afterthought. The alternation is kept
deliberately short because Chrome rejects a regex over RE2's compiled-memory
budget with `memoryLimitExceeded`, and a rejected rule is indistinguishable
from a rule that did not match.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from urllib.parse import quote

sys.path.insert(0, str(Path(__file__).parent))

from terms import compile_terms  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
TERMS = ROOT / "blocklist" / "terms"
EXCLUSIONS = ROOT / "blocklist" / "encoded_query_exclusions.txt"
OUTPUT = ROOT / "extension" / "rules" / "encoded_queries.json"

# Above the published list (3) and keyword rules (4), on the same rung as the
# reviewed obfuscation aliases; below every hand list (1000+). See
# extension/lib/policy.js.
PRIORITY = 5
DOCUMENTS = ["main_frame", "sub_frame"]
BLOCK_PAGE = "/blocked.html?reason=terms"

# Parameters used by search boxes in the wild. A term in a `q=` value on a
# blog's own search is still an explicit search, so the parameter is the rail,
# not the host. Deliberately short: every alternative adds compiled memory to
# the regex, and Chrome rejects a rule over RE2's per-regex budget with
# `memoryLimitExceeded` (which silently disables the rule).
QUERY_PARAMS = ("q", "p", "query", "search", "search_query", "text", "wd")

# What may terminate the encoded term. `$` is the end of the URL; the literal
# marks cover a following parameter or fragment; `%20`/`%2B` are an encoded
# space/plus. Anything else — including a following `%D8`/`%D9` Arabic letter —
# fails the boundary, which is what keeps `سكس` out of `سكسونيا` (Saxony).
BOUNDARY = r"(?:$|[&+#]|%20|%2B)"

# Arabic, Arabic Supplement, Arabic Extended-A and the presentation forms NFKC
# folds away. A strong term outside these blocks is not this layer's job.
ARABIC = re.compile(r"^[\u0600-\u06ff\u0750-\u077f\u08a0-\u08ff]+$")


def load_exclusions(path: Path | None = None) -> set[str]:
    """Terms the query layer must not match, with their reasons in the file."""
    path = path or EXCLUSIONS
    terms = set()
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise ValueError(f"{path}: {exc}") from exc
    for number, raw in enumerate(lines, 1):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        if not ARABIC.fullmatch(line):
            raise ValueError(f"{path}:{number}: not a single Arabic term: {line!r}")
        terms.add(line)
    return terms


def strong_arabic_terms(payload: dict, excluded: set[str] | None = None) -> list[str]:
    """Signed, single-token Arabic terms at the unambiguous weight."""
    terms = set()
    for entry in payload["terms"]:
        term = entry.get("t", "")
        if entry.get("w", 0) < 8 or " " in term or len(term) < 3:
            continue
        if ARABIC.fullmatch(term):
            terms.add(term)
    return sorted(terms - (excluded or set()))


def query_regex(term: str) -> str:
    """The full URL pattern for one percent-encoded term."""
    params = "|".join(re.escape(p) for p in QUERY_PARAMS)
    # `[^&#]*` is the VALUE of the chosen parameter. It may contain further
    # percent-encoded words, but it may not cross a literal `&` into a
    # different parameter: a term in `?q=hello&x=term` is not a search for it.
    # `^https?://[^#]*` is the authority plus path; keeping it one character
    # class rather than alternations is what keeps this pattern inside RE2's
    # tiny compiled-memory budget.
    return (r"^https?://[^#]*[?&](?:" + params + r")=[^&#]*"
            + re.escape(quote(term, safe="")) + BOUNDARY)


def build_rules(payload: dict, exempt: list[str],
                excluded: set[str] | None = None) -> list[dict]:
    rail = sorted(set(exempt))
    rules = []
    for term in strong_arabic_terms(payload, excluded):
        condition: dict = {"regexFilter": query_regex(term),
                           "isUrlFilterCaseSensitive": False,
                           "resourceTypes": DOCUMENTS}
        if rail:
            condition["excludedRequestDomains"] = rail
        rules.append({"id": len(rules) + 1, "priority": PRIORITY,
                      "action": {"type": "redirect",
                                 "redirect": {"extensionPath": BLOCK_PAGE}},
                      "condition": condition})
    return rules


def outputs() -> dict[str, str]:
    payload = compile_terms(TERMS)
    excluded = load_exclusions()
    known = set(strong_arabic_terms(payload))
    unknown = excluded - known
    if unknown:
        raise ValueError("excluded terms are not strong single-token Arabic terms: "
                         + ", ".join(sorted(unknown)))
    exempt = sorted(set(payload["exempt_domains"])
                    | set(payload["keyword_exempt_hosts"]))
    rules = build_rules(payload, exempt, excluded)
    if not rules:
        # An empty ruleset is exactly the "looks healthy, enforces nothing"
        # failure the seed verifier exists to catch. Refuse to write it.
        raise SystemExit("FATAL: no strong Arabic terms in the compiled vocabulary")
    return {"extension/rules/encoded_queries.json": json.dumps(rules, indent=2) + "\n"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="fail instead of writing when the shipped file is stale")
    args = parser.parse_args()

    try:
        files = outputs()
    except (SystemExit, ValueError) as exc:
        parser.exit(1, f"encoded-query rules are invalid: {exc}\n")

    stale = []
    for name, content in files.items():
        path = ROOT / name
        if args.check:
            if not path.exists() or path.read_text(encoding="utf-8") != content:
                stale.append(name)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            print(f"wrote {name}")
    if stale:
        parser.exit(1, "Regenerate encoded-query rules: " + ", ".join(stale) + "\n")
    if args.check:
        rules = json.loads(files[next(iter(files))])
        print(f"encoded-query ruleset is current ({len(rules)} rules)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
