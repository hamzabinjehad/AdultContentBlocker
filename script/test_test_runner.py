"""Test native-runner failure handling without launching Xcode or changing OS state."""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class TestRunnerTests(unittest.TestCase):
    def run_mac(self, status, banner):
        with tempfile.TemporaryDirectory(prefix="hisn-runner-tests-") as directory:
            scratch = Path(directory)
            binary = scratch / "bin"
            binary.mkdir()
            xcode = binary / "xcodebuild"
            xcode.write_text('''#!/bin/bash
for argument in "$@"; do
    if [ "$argument" = -exportLocalizations ]; then exit 0; fi
done
if [ "$HISN_TEST_BANNER" = 1 ]; then echo '** TEST SUCCEEDED **'; fi
exit "$HISN_TEST_STATUS"
''')
            xcode.chmod(0o755)
            python = binary / "python3"
            python.write_text("#!/bin/bash\nexit 0\n")
            python.chmod(0o755)
            env = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ["PATH"],
                       RUNNER_TEMP=directory, HISN_TEST_STATUS=str(status),
                       HISN_TEST_BANNER="1" if banner else "0", XCODEBUILD_FLAGS="")
            return subprocess.run(["bash", str(ROOT / "test.sh"), "macos"], env=env,
                                  capture_output=True, text=True, timeout=10)

    def test_success_banner_does_not_hide_xcode_failure(self):
        result = self.run_mac(65, True)
        self.assertEqual(result.returncode, 65, result.stdout + result.stderr)
        self.assertNotIn("all requested suites passed", result.stdout)

    def test_zero_status_without_success_banner_is_not_a_pass(self):
        self.assertNotEqual(self.run_mac(0, False).returncode, 0)

    def test_actual_success_status_and_banner_pass(self):
        result = self.run_mac(0, True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("all requested suites passed: macos", result.stdout)

    def test_default_includes_native_mobile_tests(self):
        source = (ROOT / "test.sh").read_text()
        default = re.search(r'if \[ \$# -eq 0 \]; then\s+set -- ([^\n]+)', source)
        self.assertIsNotNone(default)
        self.assertEqual(default.group(1).split(),
                         ["python", "seed", "extension", "release", "macos", "ios"])

    def test_unknown_suite_fails_instead_of_running_default(self):
        result = subprocess.run(["bash", str(ROOT / "test.sh"), "not-a-suite"],
                                capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 2)


if __name__ == "__main__":
    unittest.main()
