"""The iOS guardian pack must carry the right profile and never leak the password."""
import contextlib
import hashlib
import io
import json
import os
import plistlib
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import make_guardian_pack as pack


def generate(tmp: str, *args: str) -> tuple[int, str, str]:
    stdout, stderr = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
        code = pack.main(["--out", tmp, "--allow-unreachable-resolver", *args])
    return code, stdout.getvalue(), stderr.getvalue()


def by_type(profile: dict) -> dict:
    return {item["PayloadType"]: item for item in profile["PayloadContent"]}


class PackTests(unittest.TestCase):
    def test_writes_profile_manifest_readme_and_sums(self):
        with tempfile.TemporaryDirectory() as tmp:
            code, out, _ = generate(tmp, "--block-site", "example.com", "--removal-password", "s3cret")
            self.assertEqual(code, 0, out)
            for name in ("hisn-iphone.mobileconfig", "manifest.json", "GUARDIAN.md", "SHA256SUMS"):
                self.assertTrue((Path(tmp) / name).is_file(), name)
            self.assertEqual((Path(tmp) / "hisn-iphone.mobileconfig").stat().st_mode & 0o777, 0o600)

    def test_manifest_hash_matches_the_profile_and_never_holds_the_password(self):
        with tempfile.TemporaryDirectory() as tmp:
            generate(tmp, "--removal-password", "s3cret")
            profile_bytes = (Path(tmp) / "hisn-iphone.mobileconfig").read_bytes()
            manifest = json.loads((Path(tmp) / "manifest.json").read_text())
            self.assertEqual(manifest["profile_sha256"], hashlib.sha256(profile_bytes).hexdigest())
            self.assertEqual(manifest["profile_bytes"], len(profile_bytes))
            self.assertNotIn("s3cret", json.dumps(manifest))
            readme = (Path(tmp) / "GUARDIAN.md").read_text()
            self.assertNotIn("s3cret", readme)
            self.assertIn(hashlib.sha256(profile_bytes).hexdigest(),
                          (Path(tmp) / "SHA256SUMS").read_text())

    def test_the_password_is_inside_the_profile_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            generate(tmp, "--removal-password", "s3cret")
            profile = plistlib.loads((Path(tmp) / "hisn-iphone.mobileconfig").read_bytes())
            self.assertEqual(by_type(profile)["com.apple.profileRemovalPassword"]["RemovalPassword"],
                             "s3cret")

    def test_maximum_turns_on_every_hardening_switch(self):
        with tempfile.TemporaryDirectory() as tmp:
            generate(tmp, "--maximum")
            restrictions = by_type(plistlib.loads(
                (Path(tmp) / "hisn-iphone.mobileconfig").read_bytes()))["com.apple.applicationaccess"]
            for key in ["allowAccountModification", "allowPasscodeModification",
                        "allowAppInstallation", "allowAppClips", "allowEnablingRestrictions"]:
                self.assertIs(restrictions[key], False, key)
            self.assertIs(restrictions["forceAutomaticDateAndTime"], True)
            self.assertIs(restrictions["allowAppRemoval"], False)

    def test_default_does_not_turn_on_optional_hardening(self):
        with tempfile.TemporaryDirectory() as tmp:
            generate(tmp)
            restrictions = by_type(plistlib.loads(
                (Path(tmp) / "hisn-iphone.mobileconfig").read_bytes()))["com.apple.applicationaccess"]
            for key in ["allowAccountModification", "allowPasscodeModification",
                        "allowAppInstallation", "allowAppClips", "allowEnablingRestrictions"]:
                self.assertNotIn(key, restrictions, key)

    def test_maximum_refuses_to_leave_app_removal_allowed(self):
        with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                pack.main(["--out", tmp, "--allow-unreachable-resolver",
                           "--maximum", "--allow-app-removal"])

    def test_invalid_site_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                pack.main(["--out", tmp, "--allow-unreachable-resolver", "--block-site", "not a site"])

    def test_nonempty_output_needs_force(self):
        with tempfile.TemporaryDirectory() as tmp:
            generate(tmp)
            with self.assertRaises(SystemExit):
                generate(tmp)
            self.assertEqual(generate(tmp, "--force")[0], 0)

    def test_password_is_only_printed_when_asked(self):
        with tempfile.TemporaryDirectory() as tmp:
            _, out, err = generate(tmp, "--removal-password", "s3cret")
            self.assertNotIn("s3cret", out)
            self.assertNotIn("s3cret", err)
        with tempfile.TemporaryDirectory() as tmp:
            _, out, _ = generate(tmp, "--removal-password", "s3cret", "--print-password")
            self.assertIn("s3cret", out)

    def test_sites_file_and_manifest_count(self):
        with tempfile.TemporaryDirectory() as root:
            sites = Path(root) / "sites.txt"
            sites.write_text("# comment\nexample.com\nwww.example.org\n")
            out = Path(root) / "pack"
            generate(str(out), "--block-sites-file", str(sites))
            manifest = json.loads((out / "manifest.json").read_text())
            self.assertEqual(manifest["blocked_sites"], 2)
            web = by_type(plistlib.loads(
                (out / "hisn-iphone.mobileconfig").read_bytes()))["com.apple.webcontent-filter"]
            self.assertEqual(web["DenyListURLs"], ["https://example.com", "https://example.org"])

    def test_readme_is_named_and_has_the_guardian_steps(self):
        with tempfile.TemporaryDirectory() as tmp:
            generate(tmp)
            readme = (Path(tmp) / "GUARDIAN.md").read_text()
            self.assertIn("Apple Configurator", readme)
            self.assertIn("Supervise devices", readme)
            self.assertIn("30-day provisional", readme)

    def test_unreachable_resolver_is_refused_by_default(self):
        with tempfile.TemporaryDirectory() as tmp, \
                mock.patch.object(pack.mac, "resolver_is_reachable", return_value=False), \
                contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                pack.main(["--out", tmp])


if __name__ == "__main__":
    unittest.main()
