"""English/Arabic parity for the iPhone and iPad app's Localizable.strings.

Runs anywhere (no Xcode): the same keys in both languages, the same placeholders in
the same order, Arabic text in every Arabic value, and every key the SwiftUI code
looks up present in both files. A missing key would silently show English (or the
raw key) to an Arabic reader, which the simulator tests only catch for the keys
they name.
"""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parent
STRINGS = {language: ROOT / f"Hisn/{language}.lproj/Localizable.strings" for language in ("en", "ar")}
# Swift sources compiled into the app (see generate_xcodeproj.py) whose literals are lookups.
SOURCES = sorted((ROOT / "Hisn").glob("*.swift")) + [
    ROOT / "../shared/apple/RouterAppDomainsView.swift",
    ROOT / "../shared/apple/MobileProtectionPolicy.swift",
    ROOT / "Shared/AppUsageConfiguration.swift",
]
# Values that are the same in both languages on purpose.
SAME_IN_BOTH = {"sites.placeholder"}
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?(lld|ld|d|@|f|%)")
ARABIC_LETTER = re.compile(r"[؀-ۿ]")
LATIN_WORD = re.compile(r"[A-Za-z]{3,}")
# SwiftUI initialisers whose first string literal is a LocalizedStringKey.
UI_CALL = re.compile(r"\b(?:Text|Button|Label|Section|Toggle|Picker|LabeledContent|DisclosureGroup|"
                     r"LocalizedStringKey|navigationTitle|Link|Stepper|alert|ProgressView|TextField|ShareLink)"
                     r"\(\s*\"((?:[^\"\\]|\\.)*)\"")
# Keys assigned to message properties, used as enum raw values or chosen by a ternary.
KEY = r"([a-z]+(?:\.[A-Za-z0-9]+)+)"
KEY_VALUE = re.compile(r"(?:errorKey|messageKey|customSiteMessage)\s*=\s*\"([a-z][\w.]*)\""
                       rf"|case\s+\w+\s*=\s*\"{KEY}\""
                       rf"|return\s+\"{KEY}\""
                       rf"|\?\s*\"{KEY}\"\s*:\s*\"{KEY}\"")
INTERPOLATION = re.compile(r"\\\((?:[^()]|\([^()]*\))*\)")


def parse(path):
    """Parses `"key" = "value";` lines, with comments and escaped quotes."""
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    entries = {}
    for number, line in enumerate(text.splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("//"):
            continue
        match = re.fullmatch(r'"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;', line)
        if not match:
            raise AssertionError(f"{path.name}:{number}: not a key = value line")
        key, value = match.groups()
        if key in entries:
            raise AssertionError(f"{path.name}:{number}: duplicate key {key}")
        entries[key] = value
    return entries


def placeholders(value):
    """Placeholder types in argument order; positional ones sorted by position."""
    found = [m for m in PLACEHOLDER.finditer(value) if m.group(2) != "%"]
    if any(m.group(1) for m in found):
        found.sort(key=lambda m: int(m.group(1) or 0))
    return [{"ld": "lld", "d": "lld"}.get(m.group(2), m.group(2)) for m in found]


def referenced_keys():
    keys = {}
    for source in SOURCES:
        text = source.read_text(encoding="utf-8")
        for match in UI_CALL.finditer(text):
            literal = match.group(1)
            if not literal or "\\(" in literal and not literal.split("\\(")[0].strip():
                continue
            keys.setdefault(literal, set()).add(source.name)
        for match in KEY_VALUE.finditer(text):
            for key in filter(None, match.groups()):
                keys.setdefault(key, set()).add(source.name)
    return keys


def candidates(literal):
    """An interpolated literal names a key with %lld (integers) or %@ (text) in each slot."""
    parts = INTERPOLATION.split(literal)
    if len(parts) == 1:
        return [literal]
    results = [parts[0]]
    for part in parts[1:]:
        results = [prefix + token + part for prefix in results for token in ("%lld", "%@")]
    return results


class LocalizationParityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.en = parse(STRINGS["en"])
        cls.ar = parse(STRINGS["ar"])

    def test_same_keys_in_both_languages(self):
        self.assertEqual(sorted(set(self.en) - set(self.ar)), [], "missing from Arabic")
        self.assertEqual(sorted(set(self.ar) - set(self.en)), [], "missing from English")

    def test_same_placeholders_in_the_same_order(self):
        for key in self.en:
            with self.subTest(key=key):
                self.assertEqual(placeholders(self.en[key]), placeholders(self.ar.get(key, "")))

    def test_every_value_is_written_in_its_language(self):
        for key, value in self.ar.items():
            with self.subTest(key=key):
                self.assertTrue(value.strip(), "empty Arabic value")
                self.assertTrue(self.en.get(key, "").strip(), "empty English value")
                if key in SAME_IN_BOTH:
                    continue
                if LATIN_WORD.search(PLACEHOLDER.sub("", self.en[key])):
                    self.assertRegex(value, ARABIC_LETTER, "Arabic value has no Arabic text")

    def test_every_key_the_app_looks_up_exists_in_both_languages(self):
        missing = []
        for literal, files in sorted(referenced_keys().items()):
            names = candidates(literal)
            for language, table in (("en", self.en), ("ar", self.ar)):
                if not any(name in table for name in names):
                    missing.append(f"{language}: {literal!r} ({', '.join(sorted(files))})")
        self.assertEqual(missing, [])

    def test_the_lookup_scan_finds_the_app_text(self):
        # Guards the scanner itself: these are looked up through each pattern it handles.
        keys = referenced_keys()
        for expected in ("tab.protection", "vault.title", "setup.status.configured",
                         "apps.health.unchecked", "sites.locked", "plan.layers \\(protection.configuredContentLayers)",
                         "screentime.notConfigured", "commitment.screenTimeLost"):
            self.assertIn(expected, keys)
        self.assertGreater(len(keys), 150)


if __name__ == "__main__":
    unittest.main()
