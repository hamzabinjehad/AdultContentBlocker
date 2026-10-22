import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("visual_device_runner", Path(__file__).with_name("run-device.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class SummaryTests(unittest.TestCase):
    def test_repeated_views_are_not_counted_as_independent_accuracy_examples(self):
        rows = [{"id": "benign", "expected": "allow", "actual": "allow", "roundtrip_ms": 10},
                {"id": "benign", "expected": "allow", "actual": "block", "roundtrip_ms": 30},
                {"id": "positive", "expected": "block", "actual": "allow", "roundtrip_ms": 20},
                {"id": "unknown", "expected": "block", "actual": "unknown", "roundtrip_ms": 15000}]
        manifest = {"unique_sources": 3, "cases": [rows[0], rows[2], rows[3]]}
        result = runner.summary({"results": rows}, manifest)
        self.assertEqual(result["unique_views"], 3)
        self.assertEqual(result["first_verdict_confusion_by_view"],
                         {"true_positive": 0, "false_negative": 1, "true_negative": 1, "false_positive": 0, "unknown": 1})
        self.assertEqual(result["inconsistent_views"], 1)
        self.assertEqual(result["unknown_calls"], 1)
        self.assertEqual(result["roundtrip_median_ms"], 20)
        self.assertEqual(result["roundtrip_p95_ms"], 30)

    def test_no_positive_data_does_not_produce_a_recall_claim(self):
        row = {"id": "benign", "expected": "allow", "actual": "allow", "roundtrip_ms": 10}
        result = runner.summary({"results": [row]}, {"cases": [row]})
        self.assertIsNone(result["recall"])
        self.assertEqual(result["positive_views"], 0)

    def test_all_unknown_is_not_reported_as_fast_success(self):
        row = {"id": "missing", "expected": "allow", "actual": "unknown", "roundtrip_ms": 15000}
        result = runner.summary({"results": [row]}, {"cases": [row]})
        self.assertIsNone(result["roundtrip_median_ms"])
        self.assertEqual(result["first_verdict_confusion_by_view"]["unknown"], 1)


if __name__ == "__main__":
    unittest.main()
