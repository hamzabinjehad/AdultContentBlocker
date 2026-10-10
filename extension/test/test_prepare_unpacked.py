"""Clean unpacked copies retain identity and never delete source/browser data."""

import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location(
    "prepare_unpacked", Path(__file__).resolve().parents[1] / "prepare_unpacked.py")
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class PrepareUnpackedTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.source = self.root / "source"
        self.source.mkdir()
        self.output = self.root / "clean"
        self.manifest = {"manifest_version": 3, "name": "Hisn fixture", "version": "1.0.1",
                         "key": "fixture-pinned-key", "background": {"service_worker": "background.js", "type": "module"},
                         "action": {"default_popup": "popup.html", "default_icon": {"16": "icons/icon.png"}},
                         "icons": {"32": "icons/icon.png"}, "options_page": "options.html",
                         "declarative_net_request": {"rule_resources": [{"path": "rules/block.json"}]},
                         "web_accessible_resources": [{"resources": ["blocked.html"]}],
                         "content_scripts": [{"js": ["content/feed.js", "content/scan.js"], "css": ["ui.css"]}]}
        self.runtime = {
            "manifest.json": json.dumps(self.manifest, indent=2).encode() + b"\n",
            "background.js": b'import {score} from "./lib/score.js";\nimport "./content/feed.js";\n',
            "lib/score.js": b'export {norm} from "./normalize.js";\n',
            "lib/normalize.js": b'export const norm = 1;\n',
            "content/feed.js": b'globalThis.HisnFeed = {};\n', "content/scan.js": b'void 0;\n',
            "seed/terms.json": b'{"fixture":true}\n', "rules/block.json": b'[]\n',
            "icons/icon.png": b'PNG-fixture\x00\xff', "ui.css": b'body { color: blue; }\n',
            "popup.html": b'<link href="ui.css"><img src="icons/icon.png"><script src="popup.js"></script>',
            "popup.js": b'import "./lib/score.js";\n', "options.html": b'<script src="options.js"></script>',
            "options.js": b'void 0;', "blocked.html": b'<script src="blocked.js"></script>',
            "blocked.js": b'void 0;', "lib/icons.LICENSE": b'license text', "public_key.hex": b'0011',
        }
        for name, contents in self.runtime.items():
            self.write(name, contents)

    def write(self, name, contents=b"fixture"):
        path = self.source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(contents)
        return path

    def assert_no_output(self):
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.root.glob(".hisn-unpacked-*")), [])

    def test_contaminated_source_is_untouched_and_runtime_bytes_match(self):
        excluded = ["_metadata/generated_indexed_rulesets/cache.bin", "lib/_reserved/cache.bin",
                    "__MACOSX/resource", "._background.js", ".DS_Store", "test/check.js",
                    "eval/corpus.json", "keys/private.key", "secret.pem", "secret.p12",
                    "secret.pfx", "private.key", "package.sh", "check_package.py",
                    "prepare_unpacked.py", "tools/generate.py", "build.crx", ".credentials/token"]
        for name in excluded:
            self.write(name)
        before = {p.relative_to(self.source).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                  for p in self.source.rglob("*") if p.is_file()}
        self.assertEqual(helper.prepare(self.output, self.source), self.output)
        actual = {p.relative_to(self.output).as_posix(): p.read_bytes()
                  for p in self.output.rglob("*") if p.is_file()}
        self.assertEqual(actual, self.runtime)
        self.assertEqual((self.output / "manifest.json").read_bytes(), self.runtime["manifest.json"])
        self.assertEqual(json.loads(actual["manifest.json"])["key"], self.manifest["key"])
        after = {p.relative_to(self.source).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                 for p in self.source.rglob("*") if p.is_file()}
        self.assertEqual(before, after)
        self.assert_no_staging()

    def assert_no_staging(self):
        self.assertEqual(list(self.output.parent.glob(".hisn-unpacked-*")), [])

    def test_existing_output_is_never_overwritten(self):
        self.output.mkdir()
        marker = self.output / "browser-state"
        marker.write_bytes(b"keep")
        with self.assertRaisesRegex(helper.PreparationError, "already exists"):
            helper.prepare(self.output, self.source)
        self.assertEqual(marker.read_bytes(), b"keep")
        self.assert_no_staging()

    def test_empty_existing_output_is_also_refused(self):
        self.output.mkdir()
        with self.assertRaisesRegex(helper.PreparationError, "already exists"):
            helper.prepare(self.output, self.source)
        self.assertEqual(list(self.output.iterdir()), [])

    def test_overlapping_paths_are_refused(self):
        for output in (self.source, self.source / "new", self.root):
            with self.subTest(output=output):
                with self.assertRaisesRegex(helper.PreparationError, "overlap"):
                    helper.prepare(output, self.source)
        self.assert_no_output()

    def test_source_symlink_is_refused_without_following_it(self):
        outside = self.root / "outside.js"
        outside.write_bytes(b"outside")
        (self.source / "unknown.js").symlink_to(outside)
        with self.assertRaisesRegex(helper.PreparationError, "symlink"):
            helper.prepare(self.output, self.source)
        self.assertEqual(outside.read_bytes(), b"outside")
        self.assert_no_output()

    def test_source_root_and_output_parent_symlinks_are_refused(self):
        alias = self.root / "alias"
        alias.symlink_to(self.source, target_is_directory=True)
        with self.assertRaisesRegex(helper.PreparationError, "symlink"):
            helper.prepare(self.output, alias)
        elsewhere = self.root / "elsewhere"
        elsewhere.mkdir()
        link = self.root / "parent-link"
        link.symlink_to(elsewhere, target_is_directory=True)
        with self.assertRaisesRegex(helper.PreparationError, "symlink"):
            helper.prepare(link / "new", self.source)
        self.assertEqual(list(elsewhere.iterdir()), [])
        self.assert_no_output()

    def test_broken_output_symlink_is_refused(self):
        self.output.symlink_to(self.root / "does-not-exist")
        with self.assertRaisesRegex(helper.PreparationError, "symlink"):
            helper.prepare(self.output, self.source)
        self.assertTrue(self.output.is_symlink())

    def test_missing_worker_import_fails_without_publishing(self):
        (self.source / "lib/normalize.js").unlink()
        with self.assertRaisesRegex(helper.PreparationError, "missing bundled resource: lib/normalize.js"):
            helper.prepare(self.output, self.source)
        self.assert_no_output()

    def test_missing_dynamic_scanner_fails_without_publishing(self):
        for name in helper.REQUIRED_DYNAMIC:
            with self.subTest(name=name):
                original = (self.source / name).read_bytes()
                (self.source / name).unlink()
                with self.assertRaisesRegex(helper.PreparationError, "missing bundled resource"):
                    helper.prepare(self.output, self.source)
                self.write(name, original)
                self.assert_no_output()

    def test_manifest_and_html_dependencies_must_exist(self):
        for name in ("icons/icon.png", "ui.css", "popup.js", "rules/block.json", "blocked.html"):
            with self.subTest(name=name):
                original = (self.source / name).read_bytes()
                (self.source / name).unlink()
                with self.assertRaisesRegex(helper.PreparationError, "missing bundled resource"):
                    helper.prepare(self.output, self.source)
                self.write(name, original)
                self.assert_no_output()

    def test_real_locale_directory_is_preserved(self):
        self.manifest["default_locale"] = "en"
        self.write("manifest.json", json.dumps(self.manifest).encode())
        messages = self.write("_locales/en/messages.json", b'{"name":{"message":"Hisn"}}')
        helper.prepare(self.output, self.source)
        self.assertEqual((self.output / "_locales/en/messages.json").read_bytes(), messages.read_bytes())

    def test_invalid_manifest_fails_without_publishing(self):
        self.write("manifest.json", b"not JSON")
        with self.assertRaisesRegex(helper.PreparationError, "invalid or missing manifest"):
            helper.prepare(self.output, self.source)
        self.assert_no_output()

    def test_missing_local_key_fails_before_creating_output(self):
        del self.manifest["key"]
        self.write("manifest.json", json.dumps(self.manifest).encode())
        with self.assertRaisesRegex(helper.PreparationError, "local manifest key is required"):
            helper.prepare(self.output, self.source)
        self.assert_no_output()

    def test_escape_reference_fails_without_publishing(self):
        self.write("background.js", b'import "../outside.js";\n')
        with self.assertRaisesRegex(helper.PreparationError, "escapes extension"):
            helper.prepare(self.output, self.source)
        self.assert_no_output()

    def test_copy_failure_retains_and_reports_partial_output(self):
        with mock.patch.object(helper, "_copy_file", side_effect=OSError("disk full")):
            with self.assertRaisesRegex(helper.PreparationError, "partial output retained") as error:
                helper.prepare(self.output, self.source)
        self.assertIn(str(self.output), str(error.exception))
        self.assertTrue(self.output.is_dir())
        self.assert_no_staging()

    def test_new_existing_output_after_validation_is_never_reused(self):
        original = helper.validate

        def create_output(*args):
            original(*args)
            self.output.mkdir()
            (self.output / "keep").write_bytes(b"browser state")

        with mock.patch.object(helper, "validate", side_effect=create_output):
            with self.assertRaisesRegex(helper.PreparationError, "already exists"):
                helper.prepare(self.output, self.source)
        self.assertEqual((self.output / "keep").read_bytes(), b"browser state")
        self.assert_no_staging()

    def test_destination_parent_can_be_created(self):
        self.output = self.root / "new-parent" / "clean"
        helper.prepare(self.output, self.source)
        self.assertTrue((self.output / "manifest.json").is_file())
        self.assert_no_staging()


if __name__ == "__main__":
    unittest.main()
