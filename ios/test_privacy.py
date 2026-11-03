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
        self.assertIn("shield_config: [privacy], shield_action: [privacy]", source)
        self.assertIn("**{blocker: [privacy] for blocker in blockers}", source)
        self.assertIn('".xcprivacy": "text.xml"', source)
        self.assertIn('refs[privacy]', source)

    def test_shield_extensions_are_configured_for_family_controls(self):
        bundles = [
            (ROOT / "HisnShieldConfig/Info.plist",
             "com.apple.ManagedSettingsUI.shield-configuration-service",
             ".ShieldConfigurationExtension"),
            (ROOT / "HisnShieldAction/Info.plist",
             "com.apple.ManagedSettingsUI.shield-action-service",
             ".ShieldActionExtension"),
        ]
        for path, point, principal in bundles:
            with path.open("rb") as source:
                info = plistlib.load(source)
            extension = info["NSExtension"]
            self.assertEqual(extension["NSExtensionPointIdentifier"], point)
            self.assertTrue(extension["NSExtensionPrincipalClass"].endswith(principal), str(path))
        with open(ROOT / "HisnShieldConfig/HisnShieldConfig.entitlements", "rb") as source:
            config = plistlib.load(source)
        with open(ROOT / "HisnShieldAction/HisnShieldAction.entitlements", "rb") as source:
            action = plistlib.load(source)
        for data in (config, action):
            self.assertIs(data["com.apple.developer.family-controls"], True)
        self.assertEqual(config["com.apple.security.application-groups"], ["group.app.hisn.mobile"])

    def test_generated_project_references_the_manifest_for_every_bundle(self):
        project = PROJECT.read_text()
        reference = re.search(r"([0-9A-F]{24})  = \{\n\t\t\tisa = PBXFileReference;\n"
                              r"\t\t\tlastKnownFileType = text\.xml;\n"
                              r'\t\t\tpath = "Hisn/PrivacyInfo\.xcprivacy";', project)
        self.assertIsNotNone(reference, "privacy manifest file reference missing from the project")
        # app + six blockers + monitor + scanner + shield config + shield action
        self.assertEqual(project.count(f"fileRef = {reference.group(1)};"), 11)
        for name in ["HisnShieldConfig.appex", "HisnShieldAction.appex"]:
            self.assertIn(name, project, f"{name} missing from the generated project")


if __name__ == "__main__":
    unittest.main()
