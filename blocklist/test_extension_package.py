"""The upload check must cover UI dependencies as well as network rules."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("extension_package", Path(__file__).resolve().parents[1] / "extension/check_package.py")
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class ExtensionPackageTests(unittest.TestCase):
    def check_fixture(self, omitted=(), extra=None):
        manifest = {"manifest_version": 3, "action": {"default_popup": "popup.html", "default_icon": {"16": "icon.png"}},
                    "options_page": "options.html", "icons": {"32": "icon.png"}}
        files = {"manifest.json": json.dumps(manifest), "seed/terms.json": "{}",
                 "popup.html": '<script src="popup.js"></script><link href="ui.css" rel="stylesheet"><img src="icon.png">',
                 "options.html": '<script src="options.js"></script>',
                 "popup.js": "", "options.js": "", "ui.css": "", "icon.png": "fixture"}
        if extra:
            files.update(extra)
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            seed = repo / "extension/seed/terms.json"
            seed.parent.mkdir(parents=True)
            seed.write_text("{}")
            archive = repo / "extension.zip"
            with zipfile.ZipFile(archive, "w") as package:
                for name, value in files.items():
                    if name not in omitted:
                        package.writestr(name, value)
            return checker.check(archive, repo)

    def test_complete_ui_is_accepted(self):
        self.assertEqual(self.check_fixture(), [])

    def test_missing_page_is_rejected(self):
        self.assertTrue(any("popup.html" in error for error in self.check_fixture(omitted=["popup.html"])))

    def test_missing_icons_and_ui_dependencies_are_rejected(self):
        for name in ["icon.png", "popup.js", "ui.css", "options.js"]:
            with self.subTest(name=name):
                self.assertTrue(any(name in error for error in self.check_fixture(omitted=[name])))

    def test_remote_ui_assets_are_rejected(self):
        errors = self.check_fixture(extra={"options.html": '<script src="https://example.test/ui.js"></script>'})
        self.assertTrue(any("not bundled" in error for error in errors))

    def test_evaluation_corpus_is_not_shipped(self):
        errors = self.check_fixture(extra={"eval/corpus/fixture.json": "{}"})
        self.assertTrue(any("forbidden" in error for error in errors))
