"""Packaging rejects generated cache and developer preparation tools."""

import importlib.util
import hashlib
import json
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

    def test_visual_build_sources_and_dependencies_do_not_ship(self):
        for name in ("visual/engine.js", "visual/build.mjs", "visual/package.json",
                     "visual/pnpm-lock.yaml", "visual/node_modules/library/index.js",
                     "visual/test/decisions.test.js"):
            with self.subTest(name=name):
                self.assertIn(f"forbidden file shipped: {name}", self.problems_for(name))

    def test_incomplete_visual_runtime_is_rejected(self):
        self.assertTrue(any("missing bundled visual resource" in problem
                            for problem in self.problems_for("visual/broker.js")))

    def test_visual_permission_requires_runtime_when_all_assets_missing(self):
        with tempfile.TemporaryDirectory(prefix="hisn-package-visual-") as directory:
            archive = Path(directory) / "extension.zip"
            with zipfile.ZipFile(archive, "w") as bundle:
                bundle.writestr("manifest.json", '{"permissions":["offscreen"]}')
            self.assertTrue(any("missing bundled visual resource" in problem
                                for problem in CHECKER.check(archive)))

    def visual_zip(self, archive, corrupt=False):
        files = {name: b"fixture" for name in CHECKER._PREPARER.VISUAL_REQUIRED}
        files["manifest.json"] = b'{}'
        files["visual/offscreen.html"] = b'<script type="module" src="./offscreen.js"></script>'
        for model in ("blazeface", "gender"):
            files[f"visual/models/{model}.json"] = json.dumps({
                "weightsManifest": [{"paths": [f"{model}.bin"]}]}).encode()
            files[f"visual/models/{model}.bin"] = b"model fixture"
        artifacts = {name.removeprefix("visual/"): {
            "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
            for name, data in files.items()
            if name.startswith("visual/") and name != "visual/assets.json"}
        files["visual/assets.json"] = json.dumps({"schema": 1, "artifacts": artifacts}).encode()
        if corrupt:
            files["visual/models/gender.bin"] = b"corrupted model fixture"
        with zipfile.ZipFile(archive, "w") as bundle:
            for name, data in files.items():
                bundle.writestr(name, data)

    def test_complete_visual_runtime_passes_visual_integrity_checks(self):
        with tempfile.TemporaryDirectory(prefix="hisn-package-visual-") as directory:
            archive = Path(directory) / "extension.zip"
            self.visual_zip(archive)
            problems = CHECKER.check(archive)
            self.assertFalse(any("visual" in problem for problem in problems), problems)

    def test_packaged_model_corruption_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix="hisn-package-visual-") as directory:
            archive = Path(directory) / "extension.zip"
            self.visual_zip(archive, corrupt=True)
            self.assertIn("visual artifact differs from recorded build: models/gender.bin", CHECKER.check(archive))


if __name__ == "__main__":
    unittest.main()
