#!/usr/bin/env python3
"""Offline checks for accuracy.py: intervals, views, label rules and scoring. No device."""
from pathlib import Path
import json
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import accuracy  # noqa: E402


class Accuracy(unittest.TestCase):
    def test_wilson(self):
        self.assertIsNone(accuracy.wilson(0, 0))
        # Reference values for the 95% Wilson interval.
        self.assertEqual(accuracy.wilson(8, 10), [0.4902, 0.9433])
        self.assertEqual(accuracy.wilson(0, 20), [0.0, 0.1611])
        self.assertEqual(accuracy.wilson(20, 20), [0.8389, 1.0])

    def test_views_are_phone_screens(self):
        from PIL import Image
        for size in ((640, 480), (300, 900), (50, 50)):
            picture = Image.new("RGB", size, (200, 120, 90))
            for view in accuracy.VIEWS:
                self.assertEqual(accuracy.render(picture, view).size, accuracy.SCREEN, (size, view))

    def test_people_targets(self):
        self.assertTrue(accuracy.target("people-female", ["woman", "man"]))
        self.assertFalse(accuracy.target("people-female", ["man"]))
        self.assertTrue(accuracy.target("people-male", ["man"]))
        self.assertFalse(accuracy.target("people-all", []))

    def labels(self, **overrides):
        case = {"id": "a1", "file": "a1.png", "sha256": "0" * 64, "expected": "block", "category": "x"}
        case.update(overrides)
        return {"schema": 1, "kind": "adult", "cases": [case]}

    def load(self, data):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "labels.json"
            path.write_text(json.dumps(data))
            return accuracy.load_labels(path)

    def test_label_rules(self):
        self.assertTrue(accuracy.has_adult_positives(self.load(self.labels())))
        for bad in ({"file": "../a.png"}, {"file": "/tmp/a.png"}, {"expected": "maybe"}, {"id": "a b"},
                    {"sha256": "abc"}):
            with self.assertRaises(ValueError, msg=bad):
                self.load(self.labels(**bad))
        people = {"schema": 1, "kind": "people", "cases": [
            {"id": "p", "file": "p.png", "sha256": "0" * 64, "people": ["girl"]}]}
        with self.assertRaises(ValueError):
            self.load(people)

    def test_repository_guard(self):
        self.assertTrue(accuracy.inside_repo(accuracy.REPO / "android/build/x"))
        self.assertFalse(accuracy.inside_repo(Path(tempfile.gettempdir())))

    def test_adult_score(self):
        cases = [{"id": "p1", "view": "full", "category": "c", "expected": "block"},
                 {"id": "p2", "view": "tile", "category": "c", "expected": "block"},
                 {"id": "n1", "view": "full", "category": "b", "expected": "allow"},
                 {"id": "n2", "view": "tile", "category": "b", "expected": "allow"}]
        results = [{"id": "p1", "actual": "block", "strong": True, "roundtrip_ms": 40},
                   {"id": "p2", "actual": "unknown", "roundtrip_ms": 60},
                   {"id": "n1", "actual": "allow", "roundtrip_ms": 30},
                   {"id": "n2", "actual": "block", "roundtrip_ms": 50}]
        summary = accuracy.score("adult", cases, results)
        self.assertEqual(summary["overall"]["recall"]["hits"], 1)
        self.assertEqual(summary["overall"]["recall"]["total"], 2)
        self.assertEqual(summary["overall"]["false_block"]["rate"], 0.5)
        self.assertEqual(summary["unknown"], 1)
        self.assertEqual(summary["by_view"]["tile"]["recall"]["hits"], 0)
        self.assertIn("| All |", accuracy.markdown(summary))
        with self.assertRaises(ValueError):
            accuracy.score("adult", cases, results[:3])

    def test_people_score(self):
        cases = [{"id": "w", "view": "full", "people": ["woman"], "attributes": {"headwear": "hijab"}},
                 {"id": "m", "view": "full", "people": ["man"], "attributes": {"headwear": "none"}},
                 {"id": "e", "view": "full", "people": []}]
        results = [{"id": "w", "actual": "allow", "person": False},
                   {"id": "m", "actual": "allow", "person": True},
                   {"id": "e", "actual": "allow", "person": False}]
        summary = accuracy.score("people-female", cases, results)
        self.assertEqual(summary["overall"]["covered_when_wanted"]["rate"], 0.0)
        self.assertEqual(summary["overall"]["covered_other_people"]["rate"], 1.0)
        self.assertEqual(summary["by_attribute"]["headwear"]["hijab"]["covered_when_wanted"]["total"], 1)
        self.assertIn("headwear: hijab", accuracy.markdown(summary))


if __name__ == "__main__":
    unittest.main()
