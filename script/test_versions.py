"""Keep the pinned release set, the platform manifests and the generated page in sync."""
import unittest
from pathlib import Path
from unittest import mock

import versions

ROOT = Path(__file__).resolve().parents[1]


class VersionMatrixTests(unittest.TestCase):
    def test_platform_manifests_match_the_pinned_release(self):
        self.assertEqual(versions.problems(), [])

    def test_generated_page_is_current(self):
        self.assertEqual(versions.DOC.read_text(), versions.document())

    def test_page_names_every_platform_and_the_seed(self):
        page = versions.DOC.read_text()
        release = versions.load()
        for platform in release["platforms"]:
            self.assertIn(platform["name"], page)
            self.assertIn(platform["version"], page)
        seed = versions.seed_facts()
        self.assertIn(f"{seed['core_domain_count']:,}", page)

    def test_seed_is_signed_and_above_the_coverage_floor(self):
        seed = versions.seed_facts()
        self.assertGreaterEqual(seed["core_domain_count"], 100_000)
        self.assertLessEqual(seed["core_domain_count"], seed["domain_count"])
        self.assertTrue((ROOT / "seed/manifest.json.sig").stat().st_size > 0)

    def test_conflicting_xcode_targets_are_refused(self):
        self._assert_conflict(
            'MARKETING_VERSION = 1.0;\nCURRENT_PROJECT_VERSION = 1;\n'
            'MARKETING_VERSION = 1.1;\nCURRENT_PROJECT_VERSION = 1;\n')

    def test_missing_build_number_is_refused(self):
        self._assert_conflict('MARKETING_VERSION = 1.0;\n')

    def _assert_conflict(self, project_text):
        import tempfile
        with tempfile.TemporaryDirectory(prefix="hisn-versions-") as directory:
            project = Path(directory) / "project.pbxproj"
            project.write_text(project_text)
            with mock.patch.object(versions, "ROOT", Path(directory)):
                with self.assertRaises(versions.Drift):
                    versions.xcode_version("project.pbxproj")


if __name__ == "__main__":
    unittest.main()
