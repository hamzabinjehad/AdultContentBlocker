#!/usr/bin/env python3
"""Static and staged checks for the macOS Safari Web Extension target.

The app cannot be loaded into Safari in CI (that needs a signed app and a user
enablement), but the target, its embedding, the signed scanner assets and the
manifest can all be checked here.
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PROJECT = (ROOT / "Hisn.xcodeproj" / "project.pbxproj").read_text(encoding="utf-8")
INFO = (ROOT / "HisnSafariExtension" / "Info.plist").read_text(encoding="utf-8")
ENTITLEMENTS = (ROOT / "HisnSafariExtension" / "HisnSafariExtension.entitlements").read_text(
    encoding="utf-8")


class SafariExtensionTests(unittest.TestCase):
    def test_target_and_handler_are_a_safari_web_extension(self):
        self.assertIn("HisnSafariExtension", PROJECT)
        self.assertIn("com.apple.product-type.app-extension", PROJECT)
        self.assertIn("com.apple.Safari.web-extension", INFO)
        self.assertIn("SafariWebExtensionHandler", INFO)
        self.assertIn("SafariWebExtensionHandler.swift", PROJECT)
        self.assertIn("group.app.hisn", ENTITLEMENTS)

    def test_app_embeds_the_extension_in_plugins(self):
        self.assertIn("Embed Safari Extension", PROJECT)
        self.assertIn("Contents/PlugIns", PROJECT)
        # The app must depend on the extension, not merely list it in Products.
        self.assertIn("remoteInfo = HisnSafariExtension", PROJECT)

    def test_build_stages_the_verified_shared_scanner(self):
        self.assertIn("../ios/prepare_web_extension.py", PROJECT)
        self.assertIn("UNLOCALIZED_RESOURCES_FOLDER_PATH", PROJECT)
        self.assertNotIn("engine.bundle.js", PROJECT, "visual assets are staged, not copied by pbxproj")
        self.assertNotIn("seed/terms.json", PROJECT, "terms are staged, not copied by pbxproj")

    def test_staged_bundle_has_the_expected_scanner(self):
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(
                [sys.executable, str(ROOT.parent / "ios" / "prepare_web_extension.py"),
                 "--output", tmp],
                cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
            out = Path(tmp)
            manifest = json.loads((out / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest["manifest_version"], 3)
            self.assertEqual(manifest["background"]["page"], "background.html")
            self.assertTrue((out / "background.js").is_file())
            self.assertTrue((out / "content" / "scan.js").is_file())
            self.assertTrue((out / "content" / "visual.js").is_file())
            self.assertTrue((out / "lib" / "score.js").is_file())
            self.assertTrue((out / "seed" / "terms.json").is_file())
            self.assertTrue((out / "visual" / "engine.bundle.js").is_file())
            self.assertTrue((out / "icons" / "icon-128.png").is_file())


if __name__ == "__main__":
    unittest.main()
