#!/usr/bin/env python3
"""Run a canonical browser harness with real timers and an explicit verdict.

The Linux runner's file:// virtual-time dump can finish without executing the
harness. Serve our fixtures over loopback and let the existing #summary report
its result instead. Stdlib only, a fresh browser profile, no personal browser,
account, extension settings or native host. A crash/timeout is always a failure.
"""
from __future__ import annotations

import argparse
import errno
import http.server
import json
import os
from pathlib import Path
import secrets
import signal
import socketserver
import struct
import subprocess
import sys
import tempfile
import threading
import time
from urllib.parse import quote, urlsplit
import zlib

ROOT = Path(__file__).resolve().parents[2]
HARNESS_ROOT = Path(__file__).resolve().parent
MAX_REPORT_BYTES = 64 * 1024


def fixture_png(rgb):
    """Tiny deterministic PNG bytes for the test-only varying-origin route."""
    def chunk(kind, body):
        return (struct.pack(">I", len(body)) + kind + body
                + struct.pack(">I", zlib.crc32(kind + body)))

    row = b"\0" + bytes(rgb) * 32
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", 32, 32, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(row * 32)) + chunk(b"IEND", b""))


class HarnessScratch(tempfile.TemporaryDirectory):
    def cleanup(self):
        # A just-terminated Chromium helper can complete a final profile write
        # during removal. Retry only that known race, on this exact fresh test
        # directory; unexpected errors and a persistent race still fail QA.
        for attempt in range(20):
            try:
                super().cleanup()
                return
            except OSError as error:
                if error.errno != errno.ENOTEMPTY or attempt == 19:
                    raise
                time.sleep(0.05)


def stop_test_browser(process):
    """Stop only the isolated process group created by this harness."""
    if os.name == "posix":
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    elif process.poll() is None:
        process.terminate()
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)
    # Parent exit alone does not guarantee renderer/network helpers stopped.
    if os.name == "posix":
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


class Verdict:
    def __init__(self):
        self.ready = threading.Event()
        self.result = None
        self._lock = threading.Lock()

    def accept(self, raw):
        """Only a completed, nonempty harness result can end the run."""
        if len(raw) > MAX_REPORT_BYTES:
            return False
        try:
            value = json.loads(raw)
        except (ValueError, UnicodeError):
            return False
        if (not isinstance(value, dict) or value.get("status") not in ("pass", "fail")
                or not isinstance(value.get("text"), str) or not value["text"].strip()):
            return False
        with self._lock:
            if self.result is None:
                self.result = value
                self.ready.set()
        return True


def reporting_script(endpoint):
    return ("<script>(()=>{const endpoint=" + json.dumps(endpoint) + ";let sent=false;"
            "function report(){const s=document.getElementById('summary');"
            "if(sent||!s||!['pass','fail'].includes(s.dataset.result)||!s.textContent.trim())return;"
            "sent=true;fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json'},"
            "body:JSON.stringify({status:s.dataset.result,text:s.textContent})})"
            ".catch(()=>{sent=false;});}"
            "new MutationObserver(report).observe(document.documentElement,"
            "{subtree:true,childList:true,characterData:true,attributes:true});"
            "setInterval(report,100);report();})();</script>").encode()


def make_server(root, harness):
    verdict = Verdict()
    endpoint = "/__hisn_verdict_" + secrets.token_urlsafe(24)
    harness_path = "/" + harness.relative_to(root).as_posix()
    injected = reporting_script(endpoint)

    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(root), **kwargs)

        def setup(self):
            super().setup()
            self.connection.settimeout(5)

        def log_message(self, *_):
            pass

        def do_GET(self):
            request_path = urlsplit(self.path).path
            # Dedicated test-only routes model readable/denied image servers.
            # The varying route returns different pixels for anonymous CORS
            # and opaque image requests. Ordinary resources retain no-CORS
            # headers; no production permission is changed.
            if request_path in ("/__hisn_fixture_cors/allowed.png", "/__hisn_fixture_cors/denied.png",
                                "/__hisn_fixture_cors/varying.png"):
                varying = request_path == "/__hisn_fixture_cors/varying.png"
                body = (fixture_png((0, 255, 0) if self.headers.get("Origin") else (255, 0, 0))
                        if varying else (root / "icons/icon-32.png").read_bytes())
                self.send_response(200)
                self.send_header("Content-Type", "image/png")
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Cache-Control", "no-store")
                if request_path == "/__hisn_fixture_cors/allowed.png" or varying:
                    self.send_header("Access-Control-Allow-Origin", "*")
                if varying:
                    self.send_header("Vary", "Origin")
                self.end_headers()
                self.wfile.write(body)
                return
            if request_path != harness_path:
                return super().do_GET()
            body = harness.read_bytes() + injected
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            if self.path != endpoint:
                self.send_error(404)
                return
            try:
                size = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                size = 0
            if not 0 < size <= MAX_REPORT_BYTES:
                self.send_error(413)
                return
            if not verdict.accept(self.rfile.read(size)):
                self.send_error(400)
                return
            self.send_response(204)
            self.end_headers()

    class LoopbackServer(http.server.ThreadingHTTPServer):
        # Two-origin image fixtures open Chromium's parallel connection pools.
        # Keep their short burst from overflowing the stdlib's five-slot
        # listen backlog; this does not relax verdict or CORS checks.
        request_queue_size = 32

        def server_bind(self):
            # HTTPServer resolves getfqdn() while binding, although this test
            # only serves a numeric loopback address. Some hosted macOS
            # resolvers stall that unnecessary lookup before the run's timer
            # can start. Bind directly and never depend on DNS for local QA.
            socketserver.TCPServer.server_bind(self)
            self.server_name = "127.0.0.1"
            self.server_port = self.server_address[1]

    server = LoopbackServer(("127.0.0.1", 0), Handler)
    server.daemon_threads = True
    return server, verdict, endpoint, harness_path


def run_harness(browser, harness, timeout=45, linux=None):
    if not 0 < timeout <= 120:
        raise ValueError("harness timeout must be between zero and 120 seconds")
    server, verdict, _, harness_path = make_server(ROOT, harness)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    started = time.monotonic()
    failure = ""
    process = None
    try:
        with HarnessScratch(prefix="hisn-browser-harness-") as directory:
            scratch = Path(directory)
            flags = [str(browser), "--headless=new", "--disable-gpu", "--no-first-run",
                     "--no-default-browser-check", "--disable-background-networking",
                     "--disable-background-timer-throttling", "--use-mock-keychain",
                     "--password-store=basic", "--user-data-dir=" + str(scratch / "profile")]
            is_linux = sys.platform.startswith("linux") if linux is None else linux
            if is_linux:
                flags.extend(["--no-sandbox", "--disable-dev-shm-usage"])
            flags.append(f"http://127.0.0.1:{server.server_address[1]}" + quote(harness_path))
            log_path = scratch / "browser.log"
            with log_path.open("wb") as log:
                process = subprocess.Popen(flags, stdout=log, stderr=log, start_new_session=True)
                while not verdict.ready.wait(0.1):
                    if process.poll() is not None:
                        failure = f"browser exited ({process.returncode}) without a verdict"
                        break
                    if time.monotonic() - started >= timeout:
                        failure = f"no verdict within {timeout:g} seconds"
                        break
                stop_test_browser(process)
            result = verdict.result
            if result:
                for line in result["text"].splitlines():
                    print("  " + line)
            if failure or not result or result["status"] != "pass":
                print("browser harness: FAIL — " + (failure or "harness reported failure"), file=sys.stderr)
                if failure:
                    print(log_path.read_text(errors="replace")[-3000:], file=sys.stderr)
                return 1
            print(f"browser harness: PASS (real timers, {time.monotonic() - started:.1f}s)")
            return 0
    finally:
        if process is not None and process.poll() is None:
            stop_test_browser(process)
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--browser", type=Path, required=True)
    parser.add_argument("--harness", default="harness.html")
    parser.add_argument("--timeout", type=float, default=45)
    args = parser.parse_args()
    harness = (HARNESS_ROOT / args.harness).resolve()
    if (not harness.is_relative_to(HARNESS_ROOT) or harness.suffix != ".html"
            or not harness.is_file() or not args.browser.is_file()):
        parser.error("existing browser and local HTML harness are required")
    return run_harness(args.browser, harness, args.timeout)


if __name__ == "__main__":
    raise SystemExit(main())
