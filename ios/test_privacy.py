#!/usr/bin/env python3
"""App Store privacy-readiness checks that need no Xcode or Apple account.

The privacy manifest is a declaration, so the useful automated check is that it
exists, parses, says what the app actually does, and is part of every bundle the
project builds. A new required-reason API must update this test and the
generator together.
"""
import plistlib
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / "Hisn/PrivacyInfo.xcprivacy"
INFO = ROOT / "Hisn/Info.plist"
GENERATOR = ROOT / "generate_xcodeproj.py"
PROJECT = ROOT / "HisnMobile.xcodeproj/project.pbxproj"


def manifest():
    with MANIFEST.open("rb") as source:
        return plistlib.load(source)


def reasons(data):
    found = {}
    for entry in data.get("NSPrivacyAccessedAPITypes", []):
        found[entry["NSPrivacyAccessedAPIType"]] = set(entry.get("NSPrivacyAccessedAPITypeReasons", []))
    return found


class PrivacyManifestTests(unittest.TestCase):
    def test_manifest_exists_and_parses(self):
        self.assertTrue(MANIFEST.is_file())
        self.assertIsInstance(manifest(), dict)

    def test_no_tracking_and_no_collected_data(self):
        data = manifest()
        self.assertIs(data["NSPrivacyTracking"], False)
        self.assertEqual(data["NSPrivacyTrackingDomains"], [])
        self.assertEqual(data["NSPrivacyCollectedDataTypes"], [])

    def test_required_reason_apis_match_the_code(self):
        declared = reasons(manifest())
        self.assertEqual(declared["NSPrivacyAccessedAPICategoryUserDefaults"], {"CA92.1", "1C8F.1"})
        self.assertEqual(declared["NSPrivacyAccessedAPICategorySystemBootTime"], {"35F9.1"})
        # Every declared reason belongs to a category Apple defines. A typo here
        # is a review failure, not a runtime failure, so it must fail the suite.
        self.assertEqual(set(declared), {
            "NSPrivacyAccessedAPICategoryUserDefaults",
            "NSPrivacyAccessedAPICategorySystemBootTime",
        })

    def test_export_compliance_is_declared(self):
        for path in (INFO, ROOT / "HisnSafariOnly/Info.plist"):
            with path.open("rb") as source:
                info = plistlib.load(source)
            self.assertIs(info["ITSAppUsesNonExemptEncryption"], False, str(path))

    def test_generator_includes_the_manifest_in_every_bundle(self):
        source = GENERATOR.read_text()
        self.assertIn('privacy = "Hisn/PrivacyInfo.xcprivacy"', source)
        self.assertIn("monitor: [privacy], scanner: [privacy]", source)
        self.assertIn("**{blocker: [privacy] for blocker in blockers}", source)
        self.assertIn('".xcprivacy": "text.xml"', source)
        self.assertIn('refs[privacy]', source)

    def test_generated_project_references_the_manifest_for_every_bundle(self):
        project = PROJECT.read_text()
        reference = re.search(r"([0-9A-F]{24})  = \{\n\t\t\tisa = PBXFileReference;\n"
                              r"\t\t\tlastKnownFileType = text\.xml;\n"
                              r'\t\t\tpath = "Hisn/PrivacyInfo\.xcprivacy";', project)
        self.assertIsNotNone(reference, "privacy manifest file reference missing from the project")
        # app + four blockers + monitor + scanner
        self.assertEqual(project.count(f"fileRef = {reference.group(1)};"), 7)


if __name__ == "__main__":
    unittest.main()
