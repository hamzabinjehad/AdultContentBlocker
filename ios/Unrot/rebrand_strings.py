#!/usr/bin/env python3
"""Rename Hisn to Unrot in the built app's Localizable.strings (Unrot edition only).

The shared Shield, Setup and More screens were written for Hisn. Unrot reuses them,
so after Xcode copies the string tables into UnrotMobile.app this rewrites the
values (never the keys) with Unrot's name in English and Arabic. The source files
stay Hisn's, so the Hisn build and its tests are unchanged.
"""
import argparse
import json
import pathlib
import subprocess

NAMES = {"Hisn": "Unrot", "حصن": "أنروت"}


def rebrand(path: pathlib.Path) -> int:
    raw = subprocess.run(["plutil", "-convert", "json", "-o", "-", str(path)],
                         check=True, capture_output=True).stdout
    table = json.loads(raw)
    changed = 0
    for key, value in table.items():
        new = value
        for old, name in NAMES.items():
            new = new.replace(old, name)
        if new != value:
            table[key] = new
            changed += 1
    if changed:
        subprocess.run(["plutil", "-convert", "binary1", "-o", str(path), "-"],
                       input=json.dumps(table, ensure_ascii=False).encode(), check=True)
    return changed


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", required=True, help="The built app's resources folder")
    args = parser.parse_args()
    tables = sorted(pathlib.Path(args.bundle).glob("*.lproj/Localizable.strings"))
    if not tables:
        raise SystemExit("no Localizable.strings in the built app")
    for table in tables:
        print(f"{table.parent.name}: {rebrand(table)} values renamed")


if __name__ == "__main__":
    main()
