#!/usr/bin/env python3
"""
Check a packaged extension zip before it goes anywhere near the Web Store.

    extension/package.sh --out /tmp/hisn.zip && python3 extension/check_package.py /tmp/hisn.zip

`package.sh` already refuses to stage a forbidden file. This is the check from
the OTHER side — it opens the zip that would be uploaded and asserts, from the
bytes alone, that:

  * nothing that must never ship is inside (tests, keys, the local `key`
    pin, house `_comment_*` keys);
  * everything the manifest points at is inside — the service worker, every
    static ruleset, the seed terms — because a manifest entry naming a missing
    file is a store rejection at best and an extension that loads with no
    rules at worst;
  * the seed and rules inside are byte-identical to the ones in the repo,
    which `blocklist/seed.py verify` has already tied to the signed manifest.
    A package is the last place drift can enter, and the one place nothing
    else looks.

Stdlib only, so CI can run it on any runner.
"""

from __future__ import annotations

import hashlib
from html.parser import HTMLParser
import json
import posixpath
import sys
from urllib.parse import urlsplit, unquote
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# Files whose packaged bytes must equal the repo's. Their provenance is the
# signed seed manifest; see blocklist/seed.py.
PINNED = (
    "seed/terms.json",
    "rules/dnr_block_rules.json",
    "rules/dnr_keyword_rules.json",
)

# Built-in policy is generated from maintained source, not the signed seed.
BUILT_IN = ("rules/web_protection.json", "rules/safesearch.json", "lib/web-protection.js")

FORBIDDEN_PREFIXES = ("test/", "eval/", "keys/", "_metadata/")
FORBIDDEN_SUFFIXES = (".pem", ".DS_Store", "package.sh", "check_package.py")


class PageAssets(HTMLParser):
    def __init__(self):
        super().__init__()
        self.assets: list[str] = []

    def handle_starttag(self, tag, attrs):
        attribute = {"script": "src", "link": "href", "img": "src"}.get(tag)
        if attribute and (value := dict(attrs).get(attribute)):
            self.assets.append(value)


def check(zip_path: Path, repo: Path = REPO) -> list[str]:
    problems: list[str] = []
    with zipfile.ZipFile(zip_path) as zf:
        names = set(zf.namelist())

        for n in sorted(names):
            if n.startswith(FORBIDDEN_PREFIXES) or n.endswith(FORBIDDEN_SUFFIXES):
                problems.append(f"forbidden file shipped: {n}")

        if "manifest.json" not in names:
            problems.append("no manifest.json in package")
            return problems
        try:
            manifest = json.loads(zf.read("manifest.json"))
        except ValueError as exc:
            problems.append(f"manifest.json is not valid JSON: {exc}")
            return problems

        for k in manifest:
            if k == "key":
                problems.append("manifest still carries the local `key` pin")
            if k.startswith("_comment"):
                problems.append(f"manifest still carries {k}")

        referenced = []
        action = manifest.get("action", {})
        for page in [action.get("default_popup"), manifest.get("options_page"),
                     manifest.get("options_ui", {}).get("page")]:
            if page:
                referenced.append(page)
        for icons in [manifest.get("icons", {}), action.get("default_icon", {})]:
            referenced.extend(icons.values() if isinstance(icons, dict) else [icons])
        sw = manifest.get("background", {}).get("service_worker")
        if sw:
            referenced.append(sw)
        for rr in manifest.get("declarative_net_request", {}).get("rule_resources", []):
            referenced.append(rr["path"])
        for war in manifest.get("web_accessible_resources", []):
            referenced += [r for r in war.get("resources", []) if "*" not in r]
        referenced.append("seed/terms.json")
        for r in referenced:
            if r not in names:
                problems.append(f"manifest references {r}, which is not in the package")

        for page in sorted(n for n in names if n.endswith(".html")):
            parser = PageAssets()
            parser.feed(zf.read(page).decode("utf-8"))
            for asset in parser.assets:
                url = urlsplit(asset)
                if url.scheme or url.netloc:
                    problems.append(f"{page}: UI asset is not bundled: {asset}")
                    continue
                path = posixpath.normpath(posixpath.join(posixpath.dirname(page), unquote(url.path)))
                if path not in names:
                    problems.append(f"{page}: missing UI asset: {path}")

        for rel in PINNED + BUILT_IN:
            if rel not in names:
                continue        # already reported above if referenced
            packaged = hashlib.sha256(zf.read(rel)).hexdigest()
            src = repo / "extension" / rel
            if not src.exists():
                problems.append(f"{rel}: no repo copy to compare against")
                continue
            if packaged != hashlib.sha256(src.read_bytes()).hexdigest():
                problems.append(f"{rel}: packaged bytes differ from extension/{rel}")

    return problems


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    problems = check(Path(sys.argv[1]))
    if problems:
        print("package is NOT shippable:", file=sys.stderr)
        for p in problems:
            print(f"  - {p}", file=sys.stderr)
        return 1
    print(f"package OK: {sys.argv[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
