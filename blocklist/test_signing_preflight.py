"""Regression tests for the publisher's secret-availability diagnostic.

These execute only the no-dependency preflight block with synthetic values.
They never access a signing key or launch the publishing job.
"""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent.parent
WORKFLOW = ROOT / ".github" / "workflows" / "update-blocklist.yml"
STEP = "      - name: Check signing configuration\n"


class TestSigningPreflight(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = WORKFLOW.read_text(encoding="utf-8")
        cls.build = cls.source.split("\n  build:\n", 1)[1]
        start = cls.build.index(STEP)
        end = cls.build.index("\n      - ", start + len(STEP))
        cls.step = cls.build[start:end]
        block = cls.step.split("        run: |\n", 1)[1]
        lines = block.splitlines()
        if any(line and not line.startswith("          ") for line in lines):
            raise AssertionError("Unexpected preflight indentation")
        cls.script = "\n".join(line[10:] if line else "" for line in lines) + "\n"

    def run_preflight(self, value):
        with tempfile.TemporaryDirectory() as directory:
            summary_path = Path(directory) / "summary.md"
            env = os.environ.copy()
            env.pop("SIGNING_KEY", None)
            if value is not None:
                env["SIGNING_KEY"] = value
            env["GITHUB_STEP_SUMMARY"] = str(summary_path)
            env["GITHUB_REPOSITORY"] = "example/hisn"
            result = subprocess.run(
                ["bash", "-eo", "pipefail", "-c", self.script],
                cwd=directory, env=env, capture_output=True, text=True, timeout=10,
            )
            summary = summary_path.read_text(encoding="utf-8") if summary_path.exists() else ""
            # Configuration checking never creates a private-key file.
            self.assertLessEqual({path.name for path in Path(directory).iterdir()}, {"summary.md"})
            return result, summary

    def assert_blocked(self, value):
        result, summary = self.run_preflight(value)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("::error title=Blocklist signing is not configured::", result.stdout)
        self.assertIn("BLOCKLIST_SIGNING_KEY", result.stdout)
        self.assertIn("https://github.com/example/hisn/settings/environments", summary)
        self.assertIn("list-signing", summary)
        self.assertIn("blocklist/public_key.hex", summary)
        self.assertIn("main", summary)
        self.assertIn("Do not generate a replacement key", summary)
        self.assertIn("publish unsigned lists", summary)

    def test_unset_secret_fails_with_actionable_summary(self):
        self.assert_blocked(None)

    def test_empty_secret_fails_with_actionable_summary(self):
        self.assert_blocked("")

    def test_whitespace_only_secret_is_unavailable(self):
        self.assert_blocked(" \t\r\n")

    def test_nonempty_secret_is_never_printed_or_written(self):
        sentinel = "test-only-presence-sentinel-not-a-signing-key"
        result, summary = self.run_preflight(sentinel)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn(sentinel, result.stdout + result.stderr + summary)
        self.assertEqual(summary, "")

    def test_secret_shell_characters_are_not_executed(self):
        sentinel = "test-only-$(exit 23)-`exit 24`-%s\nsecond-line"
        result, summary = self.run_preflight(sentinel)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn(sentinel, result.stdout + result.stderr + summary)

    def test_preflight_is_first_step_before_checkout_and_fetch(self):
        first_step = self.build.split("    steps:\n", 1)[1].split("      - ", 1)[1]
        self.assertTrue(first_step.startswith("name: Check signing configuration\n"))
        self.assertLess(self.build.index(STEP), self.build.index("      - uses: actions/checkout@"))
        self.assertLess(self.build.index(STEP), self.build.index("      - name: Fetch previous manifest"))

    def test_environment_and_ci_gate_are_preserved(self):
        self.assertIn("\n    needs: test\n", self.build)
        self.assertIn("\n    environment:\n      name: list-signing\n", self.build)
        self.assertIn("SIGNING_KEY: ${{ secrets.BLOCKLIST_SIGNING_KEY }}", self.step)
        self.assertNotIn("continue-on-error:", self.step)
        self.assertNotIn("if:", self.step)

    def test_signatures_guards_and_cleanup_remain_mandatory(self):
        names = [
            "Write signing key", "Build", "Verify what we are about to publish",
            "Real-artifact checks on the signed build", "Publish guard", "Sanity floor",
            "Remove signing key", "Publish to lists branch",
        ]
        positions = [self.build.index("      - name: " + name + "\n") for name in names]
        self.assertEqual(positions, sorted(positions))
        self.assertIn("--sign-key keys/blocklist_ed25519.pem", self.build)
        self.assertIn("--pub blocklist/public_key.hex", self.build)
        self.assertIn("      - name: Remove signing key\n        if: always()\n", self.build)
        self.assertIn("      - name: Publish to lists branch\n        if: ${{ github.ref == 'refs/heads/main' }}\n", self.build)


if __name__ == "__main__":
    unittest.main()
