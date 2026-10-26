#!/usr/bin/env python3
"""English/Arabic resource parity for the Android app. Run by test-core.sh.

Checks that res/values and res/values-ar define the same string and plural keys
with the same placeholders, that Arabic plurals carry every CLDR category, that
values aapt2 would reject or silently alter are absent, and that every
R.string/R.plurals/@string reference in the app sources names a real key.
"""
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent
RES = ROOT / "app/src/main/res"
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]|%%")
QUANTITIES = {"en": {"one", "other"}, "ar": {"zero", "one", "two", "few", "many", "other"}}
ARABIC_LETTER = re.compile(r"[؀-ۿ]")
LATIN_WORD = re.compile(r"[A-Za-z]{3,}")


def load(path):
    strings, plurals = {}, {}
    for element in ET.parse(path).getroot():
        name = element.get("name")
        if element.tag == "string":
            if name in strings:
                raise SystemExit(f"{path}: duplicate string {name}")
            strings[name] = "".join(element.itertext())
        elif element.tag == "plurals":
            if name in plurals:
                raise SystemExit(f"{path}: duplicate plurals {name}")
            plurals[name] = {item.get("quantity"): "".join(item.itertext()) for item in element}
    return strings, plurals


def placeholders(text):
    return sorted(match.group(0) for match in PLACEHOLDER.finditer(text) if match.group(0) != "%%")


def value_problems(where, text):
    problems = []
    # aapt2 drops an unescaped straight quote and fails on a bare apostrophe.
    if re.search(r"(?<!\\)['\"]", text):
        problems.append(f"{where}: unescaped ' or \" (use ’ or escape it)")
    stray = PLACEHOLDER.sub("", text)
    if "%" in stray:
        problems.append(f"{where}: a % that is not a placeholder (write %% for a literal percent)")
    return problems


def main():
    en_strings, en_plurals = load(RES / "values/strings.xml")
    ar_strings, ar_plurals = load(RES / "values-ar/strings.xml")
    problems = []

    for kind, en, ar in (("string", en_strings, ar_strings), ("plurals", en_plurals, ar_plurals)):
        for name in sorted(set(en) - set(ar)):
            problems.append(f"{kind} {name}: missing from values-ar")
        for name in sorted(set(ar) - set(en)):
            problems.append(f"{kind} {name}: only in values-ar")

    for name in sorted(set(en_strings) & set(ar_strings)):
        english, arabic = en_strings[name], ar_strings[name]
        if placeholders(english) != placeholders(arabic):
            problems.append(f"string {name}: placeholders {placeholders(english)} vs {placeholders(arabic)}")
        if LATIN_WORD.search(PLACEHOLDER.sub("", english)) and not ARABIC_LETTER.search(arabic):
            problems.append(f"string {name}: Arabic value has no Arabic text")
        problems += value_problems(f"values/{name}", english) + value_problems(f"values-ar/{name}", arabic)

    for name in sorted(set(en_plurals) & set(ar_plurals)):
        for language, items in (("en", en_plurals[name]), ("ar", ar_plurals[name])):
            if set(items) != QUANTITIES[language]:
                problems.append(f"plurals {name} ({language}): quantities {sorted(items)}, "
                                f"expected {sorted(QUANTITIES[language])}")
            for quantity, text in items.items():
                problems += value_problems(f"{language}/{name}/{quantity}", text)
        expected = placeholders(en_plurals[name].get("other", ""))
        if placeholders(ar_plurals[name].get("other", "")) != expected:
            problems.append(f"plurals {name}: Arabic 'other' placeholders differ from English")
        for language, items in (("en", en_plurals[name]), ("ar", ar_plurals[name])):
            for quantity, text in items.items():
                # A category may name its number in words ("يومان"), but never use another argument.
                if not set(placeholders(text)) <= set(expected):
                    problems.append(f"plurals {name} ({language}/{quantity}): unknown placeholder")

    referenced = set()
    for source in (ROOT / "app/src").rglob("*.java"):
        for kind, name in re.findall(r"(?<![\w.])R\.(string|plurals)\.(\w+)", source.read_text(encoding="utf-8")):
            referenced.add((kind, name, source))
    for source in (ROOT / "app/src").rglob("*.xml"):
        if "/res/values" in source.as_posix():
            continue
        for name in re.findall(r"@string/(\w+)", source.read_text(encoding="utf-8")):
            referenced.add(("string", name, source))
    for kind, name, source in sorted(referenced, key=lambda item: (item[0], item[1], str(item[2]))):
        if name not in (en_strings if kind == "string" else en_plurals):
            problems.append(f"{source.relative_to(ROOT)}: R.{kind}.{name} does not exist")

    if problems:
        print("\n".join(problems), file=sys.stderr)
        raise SystemExit(f"String parity check failed ({len(problems)} problems)")
    print(f"String parity passed ({len(en_strings)} strings, {len(en_plurals)} plurals, "
          f"{len(referenced)} source references, English and Arabic)")


if __name__ == "__main__":
    main()
