import hashlib
import json
import plistlib
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

from direct import make_source


def make_ipa(path: Path, *, version="1.8.0", build="14",
             bundle="app.hisn.mobile", include_plist=True) -> Path:
    with zipfile.ZipFile(path, "w") as archive:
        if include_plist:
            info = {
                "CFBundleIdentifier": bundle,
                "CFBundleShortVersionString": version,
                "CFBundleVersion": build,
                "CFBundleDisplayName": "Hisn",
            }
            archive.writestr("Payload/HisnMobile.app/Info.plist", plistlib.dumps(info))
            archive.writestr("Payload/HisnMobile.app/HisnMobile", b"\0")
    return path


class MakeSourceTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.ipa = make_ipa(self.root / "Hisn.ipa")

    def tearDown(self):
        self.directory.cleanup()

    def source(self, **overrides):
        arguments = dict(name="Hisn", identifier="app.hisn.mobile.source",
                         subtitle="Adult-content blocking on iPhone and iPad",
                         description="Direct build.",
                         icon_url="https://example.com/icon.png",
                         website="https://example.com",
                         date="2026-11-07")
        arguments.update(overrides)
        return make_source.build_source(
            self.ipa, "https://example.com/Hisn.ipa", **arguments)

    def test_source_describes_the_ipa_exactly(self):
        source = self.source()
        self.assertEqual(make_source.validate_source(source), [])
        version = source["apps"][0]["versions"][0]
        self.assertEqual(version["version"], "1.8.0")
        self.assertEqual(version["buildVersion"], "14")
        self.assertEqual(version["size"], self.ipa.stat().st_size)
        self.assertEqual(version["sha256"], hashlib.sha256(self.ipa.read_bytes()).hexdigest())
        self.assertEqual(version["date"], "2026-11-07")
        self.assertEqual(source["featuredApps"], ["app.hisn.mobile"])
        self.assertIn("com.apple.developer.networking.networkextension",
                      source["apps"][0]["appPermissions"]["entitlements"])

    def test_published_source_is_valid_json(self):
        out = self.root / "source.json"
        self.assertEqual(make_source.main([
            "--ipa", str(self.ipa), "--url", "https://example.com/Hisn.ipa",
            "--out", str(out), "--icon-url", "https://example.com/icon.png",
            "--date", "2026-11-07"]), 0)
        source = json.loads(out.read_text())
        self.assertEqual(make_source.validate_source(source), [])
        check = subprocess.run(
            ["python3", str(Path(make_source.__file__)), "--check", str(out)],
            capture_output=True, text=True)
        self.assertEqual(check.returncode, 0, check.stderr)
        self.assertIn("valid source", check.stdout)

    def test_http_download_url_is_refused(self):
        with self.assertRaises(make_source.Invalid):
            make_source.build_source(self.ipa, "http://example.com/Hisn.ipa",
                                     name="Hisn", identifier="x", subtitle="s",
                                     description="d", icon_url="https://example.com/i.png")
        with self.assertRaises(make_source.Invalid):
            make_source.build_source(self.ipa, "https://example.com/Hisn.zip",
                                     name="Hisn", identifier="x", subtitle="s",
                                     description="d", icon_url="https://example.com/i.png")

    def test_unrelated_or_broken_ipa_is_refused(self):
        wrong = make_ipa(self.root / "Wrong.ipa", bundle="com.example.other")
        with self.assertRaises(make_source.Invalid):
            make_source.build_source(wrong, "https://example.com/Hisn.ipa",
                                     name="Hisn", identifier="x", subtitle="s",
                                     description="d", icon_url="https://example.com/i.png")
        missing = make_ipa(self.root / "Missing.ipa", include_plist=False)
        with self.assertRaises(make_source.Invalid):
            make_source.read_app_info(missing)
        with self.assertRaises(make_source.Invalid):
            make_source.read_app_info(self.root / "nope.ipa")

    def test_validator_names_a_broken_source(self):
        source = self.source()
        source["apps"][0]["versions"][0]["sha256"] = "nope"
        source["apps"][0]["versions"][0]["downloadURL"] = "https://example.com/a.zip"
        errors = make_source.validate_source(source)
        self.assertTrue(any("SHA-256" in error for error in errors))
        self.assertTrue(any(".ipa" in error for error in errors))
        self.assertNotEqual(make_source.validate_source({}), [])

    def test_direct_build_script_is_valid_shell(self):
        script = Path(make_source.__file__).parent / "build_direct.sh"
        result = subprocess.run(["bash", "-n", str(script)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
