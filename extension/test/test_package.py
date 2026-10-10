"""Packaging rejects generated cache and developer preparation tools."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

EXTENSION = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("hisn_package_check", EXTENSION / "check_package.py")
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)


class PackageExclusionTests(unittest.TestCase):
    def problems_for(self, extra):
        with tempfile.TemporaryDirectory(prefix="hisn-package-exclusions-") as directory:
            archive = Path(directory) / "extension.zip"
            with zipfile.ZipFile(archive, "w") as bundle:
                bundle.writestr("manifest.json", "{}")
                bundle.writestr(extra, "generated fixture")
            return CHECKER.check(archive)

    def test_generated_metadata_is_not_shippable(self):
        name = "_metadata/generated_indexed_rulesets/cache"
        self.assertIn(f"forbidden file shipped: {name}", self.problems_for(name))

    def test_even_an_empty_metadata_directory_is_rejected(self):
        name = "_metadata/"
        self.assertIn(f"forbidden file shipped: {name}", self.problems_for(name))

    def test_unpacked_preparation_tool_does_not_ship(self):
        name = "prepare_unpacked.py"
        self.assertIn(f"forbidden file shipped: {name}", self.problems_for(name))


if __name__ == "__main__":
    unittest.main()
