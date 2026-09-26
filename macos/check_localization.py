#!/usr/bin/env python3
"""
Every string the Mac app shows has an Arabic translation that fits it.

    python3 macos/check_localization.py                      # the catalog alone
    python3 macos/check_localization.py --xliff ar.xliff     # plus what the code uses

The catalog (`Hisn/Localizable.xcstrings`) is what the app ships. Checked here:

  * every key has an Arabic value, marked translated;
  * the value's placeholders match the key's — same count, same types —
    whether written in order (`%@`) or by position (`%2$@`). A missing `%@`
    drops a browser's name from a warning; a `%@` where the key has `%lld`
    crashes the formatter. Plural forms may leave the number out ("نطاق
    واحد") but may not add one;
  * with `--xliff`, every string the code actually uses — as exported by
    `xcodebuild -exportLocalizations`, which reads the compiler's output, not
    the catalog — is translated. A new `Text("…")` without Arabic fails here
    instead of showing up in English in the middle of an Arabic window.

`./test.sh macos` runs both.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

CATALOG = Path(__file__).parent / "Hisn" / "Localizable.xcstrings"
LANGUAGE = "ar"
SPEC = re.compile(r"%(?:(\d+)\$)?(lld|ld|d|@|f)")
XLIFF = "{urn:oasis:names:tc:xliff:document:1.2}"


def placeholders(text: str) -> dict[int, str]:
    """{position: type} for every placeholder, positions from 1."""
    out: dict[int, str] = {}
    n = 0
    for m in SPEC.finditer(text.replace("%%", "")):
        n += 1
        out[int(m.group(1)) if m.group(1) else n] = m.group(2)
    return out


def units(localization: dict) -> list[tuple[str, dict]]:
    """(label, stringUnit) for a plain value or for each plural form."""
    if "stringUnit" in localization:
        return [("", localization["stringUnit"])]
    forms = localization.get("variations", {}).get("plural", {})
    return [(f" [{k}]", v.get("stringUnit", {})) for k, v in forms.items()]


def check_catalog(catalog: dict) -> list[str]:
    problems = []
    for key, entry in catalog["strings"].items():
        if entry.get("shouldTranslate") is False:
            continue
        loc = entry.get("localizations", {}).get(LANGUAGE)
        if not loc:
            problems.append(f"no Arabic: {key!r}")
            continue
        wanted = placeholders(key)
        found = units(loc)
        if not found:
            problems.append(f"empty Arabic entry: {key!r}")
        plural = "variations" in loc
        if plural and "other" not in loc["variations"].get("plural", {}):
            problems.append(f"plural without an 'other' form: {key!r}")
        for label, unit in found:
            if unit.get("state") != "translated" or not unit.get("value"):
                problems.append(f"not translated{label}: {key!r}")
                continue
            got = placeholders(unit["value"])
            fits = (all(wanted.get(p) == t for p, t in got.items()) if plural
                    else got == wanted)
            if not fits:
                problems.append(f"placeholders differ{label}: {key!r} has {wanted}, "
                                f"the Arabic {unit['value']!r} has {got}")
    return problems


def check_xliff(path: Path, catalog: dict) -> list[str]:
    """Strings the compiled code uses that the catalog does not translate."""
    problems = []
    root = ET.parse(path).getroot()
    for f in root.iter(f"{XLIFF}file"):
        if "InfoPlist" in (f.get("original") or ""):
            continue    # the bundle's name stays "Hisn"
        for u in f.iter(f"{XLIFF}trans-unit"):
            source = u.find(f"{XLIFF}source")
            key = u.get("id") or (source.text if source is not None else "")
            if key in catalog["strings"]:
                continue
            target = u.find(f"{XLIFF}target")
            if target is None or target.get("state") not in ("translated", "final"):
                problems.append(f"used in the code, no Arabic: {key!r}")
    return problems


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--catalog", type=Path, default=CATALOG)
    ap.add_argument("--xliff", type=Path, help="ar.xliff from xcodebuild -exportLocalizations")
    args = ap.parse_args()

    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    problems = check_catalog(catalog)
    if args.xliff:
        problems += check_xliff(args.xliff, catalog)
    for p in problems:
        print(f"  {p}", file=sys.stderr)
    if problems:
        print(f"localization: {len(problems)} problem(s)", file=sys.stderr)
        return 1
    print(f"localization: {len(catalog['strings'])} strings, all with Arabic")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
