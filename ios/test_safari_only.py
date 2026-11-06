"""Limited edition must stay honest and independent of the full target."""
import hashlib
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import prepare_rules  # noqa: E402  (shard count must match the generator)


class SafariOnlyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        full = ROOT / "HisnMobile.xcodeproj/project.pbxproj"
        before = hashlib.sha256(full.read_bytes()).hexdigest()
        subprocess.run([sys.executable, str(ROOT / "generate_xcodeproj.py"), "--safari-only"], check=True,
                       capture_output=True)
        if hashlib.sha256(full.read_bytes()).hexdigest() != before:
            raise AssertionError("limited generation changed the full project")
        cls.project = (ROOT / "HisnSafari.xcodeproj/project.pbxproj").read_text()

    def test_no_privileged_targets_or_entitlements(self):
        for forbidden in ["CODE_SIGN_ENTITLEMENTS", "HisnActivityMonitor", "AppUsageController", "ProtectionController"]:
            self.assertNotIn(forbidden, self.project)
        source = (ROOT / "HisnSafariOnly/SafariOnlyApp.swift").read_text()
        for forbidden in ["import FamilyControls", "import ManagedSettings", "import NetworkExtension"]:
            self.assertNotIn(forbidden, source)

    def test_the_limited_app_checks_every_part_the_project_ships(self):
        # The project has shipped six blockers since the full app's 1.1.4; the
        # limited app once hardcoded four, which read a six-part bundle's
        # metadata as unverifiable and hid parts 5 and 6.
        source = (ROOT / "HisnSafariOnly/SafariOnlyApp.swift").read_text()
        self.assertIn("MobileProtectionPolicy.blockerIdentifiers.count", source)
        for stale in ["(1...4)", "0..<4", "parts == 4", "count: 4)", "total: 4", "/ 4\""]:
            self.assertNotIn(stale, source, f"stale four-part assumption: {stale!r}")

    def test_complete_parts_and_shared_scanner(self):
        for index in range(1, prepare_rules.PARTS + 1):
            self.assertIn(f"app.hisn.safari.blocker{index}", self.project)
        self.assertIn("app.hisn.safari.HisnText", self.project)
        self.assertIn("prepare_rules.py", self.project)
        self.assertIn("prepare_web_extension.py", self.project)

    def test_separate_identity_and_no_nonexistent_test_reference(self):
        self.assertIn('app.hisn.safari', self.project)
        self.assertNotIn('app.hisn.mobile;', self.project)
        scheme = (ROOT / "HisnSafari.xcodeproj/xcshareddata/xcschemes/HisnSafari.xcscheme").read_text()
        self.assertNotIn('HisnMobileTests', scheme)

    def test_scope_and_expiry_are_localized(self):
        for language in ['en', 'ar']:
            text = (ROOT / f"Hisn/{language}.lproj/Localizable.strings").read_text()
            for key in ['limited.scope', 'limited.limits', 'limited.expiry', 'limited.configOnly', 'limited.privacy']:
                self.assertIn(f'"{key}" =', text)

    def test_domain_only_alias_preserves_all_parts_without_scanner(self):
        subprocess.run([sys.executable, str(ROOT / "generate_xcodeproj.py"), "--safari-only",
                        "--domain-only", "--reuse-text-id-for-part-one"], check=True, capture_output=True)
        project = (ROOT / "HisnSafari.xcodeproj/project.pbxproj").read_text()
        self.assertNotIn("SafariWebExtensionHandler.swift", project)
        self.assertNotIn("prepare_web_extension.py", project)
        self.assertEqual(project.count('productType = "com.apple.product-type.app-extension"'),
                         prepare_rules.PARTS)
        self.assertIn("HISN_SAFARI_DOMAIN_ONLY = YES", project)
        self.assertIn("HISN_SAFARI_REUSE_TEXT_ID = YES", project)
        self.assertIn("app.hisn.safari.HisnText", project)
        for index in range(2, prepare_rules.PARTS + 1):
            self.assertIn(f"app.hisn.safari.blocker{index}", project)

    def test_alias_rejected_without_limited_domain_mode(self):
        result = subprocess.run([sys.executable, str(ROOT / "generate_xcodeproj.py"),
                                 "--reuse-text-id-for-part-one"], capture_output=True)
        self.assertNotEqual(result.returncode, 0)


if __name__ == '__main__':
    unittest.main()
