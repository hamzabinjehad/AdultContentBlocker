#!/usr/bin/env python3
"""The Play/direct version-name split, tested without building or installing an APK."""
import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("check_release", ROOT / "check-release.py")
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class DistributionTests(unittest.TestCase):
    def test_play_artifact_has_no_direct_suffix(self):
        self.assertIsNone(CHECK.distribution_problem("0.8.1", "0.8.1", "play"))

    def test_direct_artifact_cannot_be_checked_as_play(self):
        self.assertIsNotNone(CHECK.distribution_problem("0.8.1-direct", "0.8.1", "play"))
        self.assertIn("must never be uploaded", CHECK.distribution_problem("0.8.1-direct", "0.8.1", "play"))

    def test_direct_artifact_must_carry_the_suffix(self):
        self.assertIsNone(CHECK.distribution_problem("0.8.1-direct", "0.8.1", "direct"))
        self.assertIsNotNone(CHECK.distribution_problem("0.8.1", "0.8.1", "direct"))

    def test_unknown_distribution_is_refused(self):
        self.assertIsNotNone(CHECK.distribution_problem("0.8.1", "0.8.1", "store"))

    def test_gradle_versions_are_readable(self):
        values = CHECK.gradle_values()
        self.assertEqual(values["package"], "app.hisn.android")
        self.assertTrue(values["versionName"])
        self.assertTrue(values["versionCode"].isdigit())


if __name__ == "__main__":
    unittest.main()
