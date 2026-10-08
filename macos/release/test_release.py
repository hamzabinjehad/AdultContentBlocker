import copy
import json
import os
import plistlib
import pwd
import subprocess
import tempfile
import unittest
from pathlib import Path

import check_release as release


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.app_entitlements = release.read_plist(release.ROOT / "Hisn/Hisn.DeveloperID.entitlements")
        self.filter_entitlements = release.read_plist(release.ROOT / "HisnFilter/HisnFilter.DeveloperID.entitlements")
        self.info = release.read_plist(release.ROOT / "Hisn/Info.plist")

    def test_distribution_entitlements_are_valid(self):
        self.assertEqual(release.check_entitlements(self.app_entitlements, self.filter_entitlements), [])

    def test_development_provider_cannot_ship(self):
        self.filter_entitlements[release.NETWORK] = ["content-filter-provider"]
        self.assertTrue(release.check_entitlements(self.app_entitlements, self.filter_entitlements))

    def test_debug_entitlement_cannot_ship(self):
        self.app_entitlements["com.apple.security.get-task-allow"] = True
        self.assertTrue(release.check_entitlements(self.app_entitlements, self.filter_entitlements))

    def test_missing_sandbox_is_rejected(self):
        self.filter_entitlements["com.apple.security.app-sandbox"] = False
        self.assertTrue(release.check_entitlements(self.app_entitlements, self.filter_entitlements))

    def test_mismatched_groups_are_rejected(self):
        self.filter_entitlements[release.GROUPS] = ["group.other"]
        self.assertTrue(release.check_entitlements(self.app_entitlements, self.filter_entitlements))

    def test_empty_and_wildcard_extension_ids_are_rejected(self):
        for ids in ([], ["*"], ["https://example.com"], "hfhaffbmoeepcdolgejeidkgaoapcjig"):
            with self.subTest(ids=ids):
                self.info["HisnExtensionIDs"] = ids
                self.assertTrue(release.check_browser_config(self.info))

    def test_public_release_needs_a_store_extension(self):
        self.info.pop("HisnChromeStoreURL", None)
        self.info.pop("HisnEdgeStoreURL", None)
        self.assertTrue(release.check_browser_config(self.info, public=True))
        self.assertEqual(release.check_browser_config(self.info), [])

    def test_store_link_must_match_admitted_id(self):
        self.info["HisnChromeStoreURL"] = "https://chromewebstore.google.com/detail/hisn/" + "a" * 32
        self.assertTrue(release.check_browser_config(self.info, public=True))
        self.info["HisnExtensionIDs"].append("a" * 32)
        self.assertEqual(release.check_browser_config(self.info, public=True), [])

    def test_store_link_must_use_expected_https_host(self):
        for prefix in ("http://chromewebstore.google.com", "https://chromewebstore.google.com.evil.example"):
            self.info["HisnChromeStoreURL"] = prefix + "/detail/hisn/" + self.info["HisnExtensionIDs"][0]
            self.assertTrue(release.check_browser_config(self.info))

    def make_bundle(self, root):
        app = root / "Hisn.app"
        extension = app / "Contents/Library/SystemExtensions/HisnFilter.systemextension"
        info = copy.deepcopy(self.info)
        info.update(CFBundleIdentifier="app.hisn.Hisn", CFBundleVersion="2", CFBundleShortVersionString="1.0")
        for directory, values in ((app, info), (extension, dict(info, CFBundleIdentifier="app.hisn.Hisn.HisnFilter"))):
            (directory / "Contents/MacOS").mkdir(parents=True)
            (directory / "Contents/Info.plist").write_bytes(plistlib.dumps(values))
        for executable in (app / "Contents/MacOS/Hisn", app / "Contents/MacOS/HisnBridge",
                           extension / "Contents/MacOS/HisnFilter"):
            executable.write_bytes(b"fixture")
            executable.chmod(0o755)
        for language in ("en", "ar"):
            (app / f"Contents/Resources/{language}.lproj").mkdir(parents=True)
        return app, extension

    def test_missing_bridge_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            app, _ = self.make_bundle(Path(directory))
            self.assertEqual(release.check_bundle(app, signed=False), [])
            (app / "Contents/MacOS/HisnBridge").unlink()
            self.assertTrue(release.check_bundle(app, signed=False))

    def test_mismatched_filter_version_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            app, extension = self.make_bundle(Path(directory))
            path = extension / "Contents/Info.plist"
            info = release.read_plist(path)
            info["CFBundleVersion"] = "1"
            path.write_bytes(plistlib.dumps(info))
            self.assertIn("App/filter CFBundleVersion differ", release.check_bundle(app, signed=False))

    def test_missing_arabic_bundle_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            app, _ = self.make_bundle(Path(directory))
            (app / "Contents/Resources/ar.lproj").rmdir()
            self.assertIn("Missing ar localization", release.check_bundle(app, signed=False))

    def test_native_manifest_serializes_unusual_paths_without_installing(self):
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory) / 'Hisn "quoted" \\ test.app'
            bridge = app / "Contents/MacOS/HisnBridge"
            bridge.parent.mkdir(parents=True)
            bridge.write_bytes(b"fixture")
            bridge.chmod(0o755)
            result = subprocess.run(
                ["bash", str(release.ROOT / "install_native_host.sh"), "--app-path", str(app),
                 "--extension-id", "a" * 32, "--extension-id", "b" * 32, "--print-manifest"],
                check=True, capture_output=True, text=True)
            manifest = json.loads(result.stdout)
            self.assertEqual(manifest["path"], str(bridge))
            self.assertEqual(manifest["allowed_origins"],
                             ["chrome-extension://" + "a" * 32 + "/", "chrome-extension://" + "b" * 32 + "/"])

    def test_package_cannot_relocate_or_preserve_old_bundle_files(self):
        components = release.read_plist(release.ROOT / "release/Components.plist")
        self.assertEqual(components[0]["RootRelativeBundlePath"], "Applications/Hisn.app")
        self.assertIs(components[0]["BundleIsRelocatable"], False)
        self.assertEqual(components[0]["BundleOverwriteAction"], "upgrade")

    def test_login_agents_start_in_background_and_restart_only_failed_exits(self):
        # Execute only the actual plist generators with temporary destinations,
        # never the build, install, or launchctl operations. Unconditional
        # KeepAlive would repeatedly launch Hisn in every other Mac account,
        # where --for-user deliberately exits successfully without protecting.
        install_source = (release.ROOT / "install.sh").read_text()
        install_start = install_source.index('cat > "$AGENT_TMP" <<PLIST\n')
        install_end = install_source.index('\nPLIST\n', install_start) + len('\nPLIST\n')
        package_source = (release.ROOT / "release/installer/postinstall").read_text()
        package_start = package_source.index('/usr/libexec/PlistBuddy -c "Add :Label')
        package_end = package_source.index('plutil -lint "$TEMP_AGENT"', package_start)
        user_name = pwd.getpwuid(os.getuid()).pw_name
        generators = {
            "developer installer": install_source[install_start:install_end],
            "package installer": package_source[package_start:package_end],
        }
        with tempfile.TemporaryDirectory() as directory:
            for name, generator in generators.items():
                with self.subTest(installer=name):
                    destination = Path(directory) / f"{name}.plist"
                    environment = dict(os.environ, APP="/Applications/Hisn.app",
                                       LABEL="app.hisn.agent", USER_NAME=user_name,
                                       AGENT_TMP=str(destination), TEMP_AGENT=str(destination))
                    subprocess.run(["bash", "-euc", generator], env=environment,
                                   check=True, capture_output=True, text=True)
                    agent = release.read_plist(destination)
                    self.assertEqual(agent["Label"], "app.hisn.agent")
                    self.assertEqual(agent["ProgramArguments"],
                                     ["/Applications/Hisn.app/Contents/MacOS/Hisn",
                                      "--background", "--for-user", user_name])
                    self.assertIs(agent["RunAtLoad"], True)
                    self.assertEqual(agent["KeepAlive"], {"SuccessfulExit": False})
                    self.assertEqual(agent["LimitLoadToSessionType"], "Aqua")
                    self.assertGreaterEqual(agent["ThrottleInterval"], 10)


if __name__ == "__main__":
    unittest.main()
