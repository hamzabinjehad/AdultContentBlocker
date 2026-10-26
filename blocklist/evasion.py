#!/usr/bin/env python3
"""Generate the URL-obfuscation ruleset from a reviewed alias table.

    python3 blocklist/evasion.py            # write extension/rules/evasion.json
    python3 blocklist/evasion.py --check    # fail if the shipped file is stale

WHY THIS LAYER EXISTS
---------------------
`build.py` emits one DNR rule per reviewed keyword. Those rules match the
hostname and path exactly as the URL is written, which is what keeps `porn`
from also catching `pornic` — but it also means a registered `p0rn...` or
`s3x...` host sails past. A blocklist that is one keystroke away from a new
domain is a losing race by construction; closing the cheapest substitutions
for the strongest terms raises the cost of that race for free.

WHAT THIS DOES NOT DO
---------------------
This is NOT a second vocabulary and NOT fuzzy matching. Every alias must name
a canonical term that already exists in `blocklist/terms/host_terms.tsv`, and
the generator refuses anything else. Aliases are ASCII alphanumerics only (a
punycode hostname never contains an Arabic letter, and a non-ASCII urlFilter can
make Chrome reject a whole downloaded ruleset). `substring` rules are limited
to canonicals the keyword layer already treats as unambiguous; everything else
is anchored to a whole hostname label, so `s3x.com` matches and
`s3xeducation.com` does not.

The macOS socket filter sees only a hostname and does not consume DNR rules.
This file therefore extends the browser layer only; the published domain list
remains the shared, platform-independent source of truth.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from terms import compile_terms  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ALIASES = ROOT / "blocklist" / "evasion_aliases.json"
TERMS = ROOT / "blocklist" / "terms"
OUTPUT = ROOT / "extension" / "rules" / "evasion.json"

# Above the published list (3) and keyword rules (4), below every hand list
# (1000+) and the built-in search/viewer policy (2000). See the ladder in
# extension/lib/policy.js.
PRIORITY = 5
DOCUMENTS = ["main_frame", "sub_frame"]
# A host label that spells a reviewed term with digit substitutions is strong
# enough to refuse embedded content as well, exactly as the published domain
# list does for its own hosts. Substring aliases stay document-only: a match in
# a path can be an ordinary page's asset, and breaking that page is worse than
# missing an image.
EMBEDDED = ["script", "stylesheet", "font", "image", "xmlhttprequest", "ping",
            "other", "media", "object", "websocket", "webtransport", "webbundle",
            "csp_report"]
BLOCK_PAGE = "/blocked.html?reason=terms"

_ALIAS = re.compile(r"^[a-z0-9]{3,32}$")
_CANONICAL = re.compile(r"^[a-z0-9]{2,32}$")
_CATEGORIES = ("substring", "host_token")


class AliasError(ValueError):
    """The table would generate a rule the keyword layer does not justify."""


def load_aliases(path: Path | None = None) -> dict[str, list[dict[str, str]]]:
    """Parse and structurally validate the alias table."""
    path = path or ALIASES
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise AliasError(f"{path}: {exc}") from exc
    if not isinstance(raw, dict):
        raise AliasError(f"{path}: the table must be a JSON object")
    unknown = set(raw) - set(_CATEGORIES) - {"_comment"}
    if unknown:
        raise AliasError(f"{path}: unknown keys: {', '.join(sorted(unknown))}")

    out: dict[str, list[dict[str, str]]] = {}
    seen: dict[str, str] = {}
    for category in _CATEGORIES:
        entries = raw.get(category, [])
        if not isinstance(entries, list) or not entries:
            raise AliasError(f"{path}: {category!r} must be a non-empty list")
        clean: list[dict[str, str]] = []
        for i, entry in enumerate(entries):
            where = f"{path}: {category}[{i}]"
            if not isinstance(entry, dict) or set(entry) != {"alias", "canonical"}:
                raise AliasError(f"{where}: expected exactly alias and canonical")
            alias, canonical = entry["alias"], entry["canonical"]
            if not isinstance(alias, str) or not _ALIAS.fullmatch(alias):
                raise AliasError(f"{where}: alias must be 3-32 lowercase letters/digits")
            if not isinstance(canonical, str) or not _CANONICAL.fullmatch(canonical):
                raise AliasError(f"{where}: canonical must be 2-32 lowercase letters/digits")
            if alias == canonical:
                raise AliasError(f"{where}: alias and canonical are identical")
            if alias in seen:
                raise AliasError(f"{where}: alias {alias!r} already used in {seen[alias]}")
            seen[alias] = category
            clean.append({"alias": alias, "canonical": canonical})
        out[category] = clean
    return out


def validate_against_vocabulary(table: dict[str, list[dict[str, str]]],
                               host_kinds: dict[str, str],
                               page_terms: set[str]) -> None:
    """Refuse aliases whose canonical term is absent from the signed vocabulary.

    The whole safety argument is "this only spells an existing strong term a
    different way". An alias for a term nobody added is a new vocabulary entry
    smuggled past review, so it is an error rather than a silently skipped rule.
    """
    for category in _CATEGORIES:
        for entry in table[category]:
            alias, canonical = entry["alias"], entry["canonical"]
            if canonical not in host_kinds:
                raise AliasError(
                    f"{alias!r}: canonical {canonical!r} is not in host_terms.tsv")
            if category == "substring" and host_kinds[canonical] != "substring":
                raise AliasError(
                    f"{alias!r}: {canonical!r} is only a host-token term; "
                    f"use the host_token category")
            if alias in host_kinds or alias in page_terms:
                raise AliasError(
                    f"{alias!r}: already a reviewed term — no alias needed")


def host_token_regex(alias: str) -> str:
    """A whole-label match, mirroring build.py's short-keyword rule.

    One optional preceding label (so `www.`/`cdn2.` still match), the alias as
    one label or a label prefix followed by digits, then a dot, colon, port or
    the end of the authority. `s3x.example` matches; `s3xeducation.example`
    does not.
    """
    return (r"^[a-z][a-z0-9+.-]*://(?:[^/?#]*[._-])?"
            + re.escape(alias)
            + r"[0-9]*(?:[._:-][^/?#]*)?(?:[/?#]|$)")


def build_rules(table: dict[str, list[dict[str, str]]],
                exempt: list[str]) -> list[dict]:
    """Turn a validated table into Chrome declarativeNetRequest rules."""
    rules: list[dict] = []
    rail = sorted(set(exempt))
    for entry in table["substring"]:
        condition: dict = {"urlFilter": entry["alias"], "resourceTypes": DOCUMENTS}
        if rail:
            condition["excludedRequestDomains"] = rail
        rules.append({"id": len(rules) + 1, "priority": PRIORITY,
                      "action": {"type": "redirect",
                                 "redirect": {"extensionPath": BLOCK_PAGE}},
                      "condition": condition})
    for entry in table["host_token"]:
        match = {"regexFilter": host_token_regex(entry["alias"]),
                 "isUrlFilterCaseSensitive": False}
        if rail:
            match["excludedRequestDomains"] = rail
        rules.append({"id": len(rules) + 1, "priority": PRIORITY,
                      "action": {"type": "redirect",
                                 "redirect": {"extensionPath": BLOCK_PAGE}},
                      "condition": {**match, "resourceTypes": DOCUMENTS}})
        rules.append({"id": len(rules) + 1, "priority": PRIORITY,
                      "action": {"type": "block"},
                      "condition": {**match, "resourceTypes": EMBEDDED}})
    return rules


def outputs() -> dict[str, str]:
    """Every file this generator owns, as text — the single byte form."""
    table = load_aliases()
    payload = compile_terms(TERMS)
    host_kinds = {entry["t"]: entry["kind"] for entry in payload["host_terms"]}
    page_terms = {entry["t"] for entry in payload["terms"]}
    validate_against_vocabulary(table, host_kinds, page_terms)
    exempt = sorted(set(payload["exempt_domains"])
                    | set(payload["keyword_exempt_hosts"]))
    rules = build_rules(table, exempt)
    return {"extension/rules/evasion.json": json.dumps(rules, indent=2) + "\n"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="fail instead of writing when a shipped file is stale")
    args = parser.parse_args()

    try:
        files = outputs()
    except AliasError as exc:
        parser.exit(1, f"evasion table is invalid: {exc}\n")

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
        parser.exit(1, "Regenerate the evasion ruleset: " + ", ".join(stale) + "\n")
    if args.check:
        print(f"evasion ruleset is current ({len(json.loads(files[next(iter(files))]))} rules)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
