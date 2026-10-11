"""Real-time harness cannot turn absent or malformed browser evidence green."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

RUNNER_PATH = Path(__file__).resolve().parent / "browser/run_live.py"
SPEC = importlib.util.spec_from_file_location("hisn_browser_run_live", RUNNER_PATH)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class BrowserRunnerTests(unittest.TestCase):
    def test_only_nonempty_completed_verdict_is_accepted(self):
        state = runner.Verdict()
        for value in ({"status": "pending", "text": "Running"},
                      {"status": "pass", "text": ""}, {"status": "pass", "text": "  "},
                      {"status": "pass", "text": None}, [], "pass"):
            self.assertFalse(state.accept(json.dumps(value).encode()))
            self.assertFalse(state.ready.is_set())
        self.assertFalse(state.accept(b"not JSON"))
        self.assertFalse(state.accept(b"x" * (runner.MAX_REPORT_BYTES + 1)))
        self.assertTrue(state.accept(b'{"status":"pass","text":"ok fixture"}'))
        self.assertTrue(state.ready.is_set())

    def test_first_failure_cannot_be_overwritten_by_later_pass(self):
        state = runner.Verdict()
        self.assertTrue(state.accept(b'{"status":"fail","text":"FAIL fixture"}'))
        self.assertTrue(state.accept(b'{"status":"pass","text":"ok fixture"}'))
        self.assertEqual(state.result["status"], "fail")

    def test_loopback_endpoint_requires_its_token_and_completed_result(self):
        harness = runner.HARNESS_ROOT / "popup.html"
        server, state, endpoint, harness_path = runner.make_server(runner.ROOT, harness)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.server_address[1]}"
        try:
            with urlopen(base + harness_path, timeout=5) as response:
                body = response.read()
            self.assertIn(endpoint.encode(), body)
            self.assertIn(b"MutationObserver", body)
            for path, payload, status in (("/__unknown", b"{}", 404),
                                          (endpoint, b'{"status":"pending","text":"Running"}', 400)):
                with self.assertRaises(HTTPError) as failure:
                    urlopen(Request(base + path, data=payload, method="POST"), timeout=5)
                self.assertEqual(failure.exception.code, status)
                self.assertFalse(state.ready.is_set())
            with urlopen(Request(base + endpoint,
                                 data=b'{"status":"fail","text":"FAIL fixture"}', method="POST"),
                         timeout=5) as response:
                self.assertEqual(response.status, 204)
            self.assertTrue(state.ready.is_set())
            self.assertEqual(state.result["status"], "fail")
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_exited_browser_without_verdict_is_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            fake = Path(directory) / "empty-browser"
            fake.write_text("#!/bin/sh\nexit 0\n")
            fake.chmod(0o755)
            result = subprocess.run(["python3", str(RUNNER_PATH), "--browser", str(fake),
                                     "--harness", "popup.html", "--timeout", "1"],
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
            self.assertIn("without a verdict", result.stderr)
            self.assertNotIn("PASS", result.stdout)

    def test_pending_browser_times_out_as_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            fake = Path(directory) / "pending-browser"
            fake.write_text("#!/bin/sh\nexec sleep 5\n")
            fake.chmod(0o755)
            result = subprocess.run(["python3", str(RUNNER_PATH), "--browser", str(fake),
                                     "--harness", "popup.html", "--timeout", "0.2"],
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
            self.assertIn("no verdict within", result.stderr)
            self.assertNotIn("PASS", result.stdout)

    def test_harness_cannot_escape_its_local_directory(self):
        result = subprocess.run(["python3", str(RUNNER_PATH), "--browser", "/bin/sh",
                                 "--harness", "../../../manifest.json"],
                                capture_output=True, text=True, timeout=5)
        self.assertEqual(result.returncode, 2)
        self.assertIn("local HTML harness", result.stderr)

    def test_shell_runner_never_excuses_a_missing_verdict(self):
        source = RUNNER_PATH.with_name("run.sh").read_text()
        self.assertNotIn("skipped on the Linux runner", source)
        self.assertIn("run_live.py", source)


if __name__ == "__main__":
    unittest.main()
