"""Real-time harness cannot turn absent or malformed browser evidence green."""
import importlib.util
import errno
import contextlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch
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
                failure.exception.close()
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

    def test_numeric_loopback_server_never_uses_a_dns_resolver(self):
        with patch("socket.getfqdn", side_effect=AssertionError("local harness attempted DNS")):
            server, _, _, _ = runner.make_server(runner.ROOT, runner.HARNESS_ROOT / "popup.html")
        try:
            self.assertEqual(server.server_name, "127.0.0.1")
            self.assertEqual(server.server_port, server.server_address[1])
            self.assertGreaterEqual(server.request_queue_size, 12)
        finally:
            server.server_close()

    def test_only_explicit_cors_image_fixture_grants_cross_origin_pixels(self):
        server, _, _, _ = runner.make_server(runner.ROOT, runner.HARNESS_ROOT / "popup.html")
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.server_address[1]}"
        expected = (runner.ROOT / "icons/icon-32.png").read_bytes()
        try:
            for path, allowed in (("/__hisn_fixture_cors/allowed.png", True),
                                  ("/__hisn_fixture_cors/denied.png", False),
                                  ("/icons/icon-32.png", False)):
                with urlopen(base + path, timeout=5) as response:
                    self.assertEqual(response.status, 200)
                    self.assertEqual(response.headers.get_content_type(), "image/png")
                    self.assertEqual(response.read(), expected)
                    self.assertEqual(response.headers.get("Access-Control-Allow-Origin"),
                                     "*" if allowed else None)
            with self.assertRaises(HTTPError) as failure:
                urlopen(base + "/__hisn_fixture_cors/not-an-image.png", timeout=5)
            self.assertEqual(failure.exception.code, 404)
            failure.exception.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_varying_fixture_serves_different_pixels_for_the_same_url(self):
        server, _, _, _ = runner.make_server(runner.ROOT, runner.HARNESS_ROOT / "popup.html")
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        url = f"http://127.0.0.1:{server.server_address[1]}/__hisn_fixture_cors/varying.png"
        try:
            for origin, expected in ((None, (255, 0, 0)), ("http://127.0.0.1:12345", (0, 255, 0))):
                headers = {"Origin": origin} if origin else {}
                with urlopen(Request(url, headers=headers), timeout=5) as response:
                    self.assertEqual(response.read(), runner.fixture_png(expected))
                    self.assertEqual(response.headers.get("Access-Control-Allow-Origin"), "*")
                    self.assertEqual(response.headers.get("Vary"), "Origin")
                    self.assertEqual(response.headers.get("Cache-Control"), "no-store")
            self.assertNotEqual(runner.fixture_png((255, 0, 0)), runner.fixture_png((0, 255, 0)))
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_scratch_retries_only_bounded_directory_not_empty_race(self):
        scratch = runner.HarnessScratch()
        try:
            with patch.object(tempfile.TemporaryDirectory, "cleanup",
                              side_effect=[OSError(errno.ENOTEMPTY, "profile race"), None]) as clean:
                scratch.cleanup()
                self.assertEqual(clean.call_count, 2)
            with patch.object(tempfile.TemporaryDirectory, "cleanup",
                              side_effect=PermissionError(errno.EACCES, "unexpected")):
                with self.assertRaises(PermissionError):
                    scratch.cleanup()
        finally:
            scratch.cleanup()

    def test_browser_launch_owns_and_stops_only_its_new_process_group(self):
        with patch.object(runner.subprocess, "Popen") as launch, patch.object(runner.os, "killpg") as killpg:
            process = launch.return_value
            process.pid = 12345
            process.returncode = 0
            process.poll.return_value = 0
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                result = runner.run_harness(Path("/bin/sh"), runner.HARNESS_ROOT / "popup.html", timeout=1)
            self.assertEqual(result, 1)
            self.assertTrue(launch.call_args.kwargs["start_new_session"])
            self.assertEqual(killpg.call_args_list[0].args, (12345, runner.signal.SIGTERM))
            self.assertEqual(killpg.call_args_list[1].args, (12345, runner.signal.SIGKILL))

    def test_exited_browser_without_verdict_is_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            fake = Path(directory) / "empty-browser"
            fake.write_text("#!/bin/sh\nexit 0\n")
            fake.chmod(0o755)
            result = subprocess.run([sys.executable, str(RUNNER_PATH), "--browser", str(fake),
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
            result = subprocess.run([sys.executable, str(RUNNER_PATH), "--browser", str(fake),
                                     "--harness", "popup.html", "--timeout", "0.2"],
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
            self.assertIn("no verdict within", result.stderr)
            self.assertNotIn("PASS", result.stdout)

    def test_harness_cannot_escape_its_local_directory(self):
        result = subprocess.run([sys.executable, str(RUNNER_PATH), "--browser", "/bin/sh",
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
