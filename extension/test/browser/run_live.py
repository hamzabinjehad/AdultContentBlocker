#!/usr/bin/env python3
"""Run a canonical browser harness with real timers and an explicit verdict.

The Linux runner's file:// virtual-time dump can finish without executing the
harness. Serve our fixtures over loopback and let the existing #summary report
its result instead. Stdlib only, a fresh browser profile, no personal browser,
account, extension settings or native host. A crash/timeout is always a failure.
"""
from __future__ import annotations

import argparse
import http.server
import json
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import threading
import time
from urllib.parse import quote, urlsplit

ROOT = Path(__file__).resolve().parents[2]
HARNESS_ROOT = Path(__file__).resolve().parent
MAX_REPORT_BYTES = 64 * 1024


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
            if urlsplit(self.path).path != harness_path:
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

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
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
        with tempfile.TemporaryDirectory(prefix="hisn-browser-harness-") as directory:
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
                process = subprocess.Popen(flags, stdout=log, stderr=log)
                while not verdict.ready.wait(0.1):
                    if process.poll() is not None:
                        failure = f"browser exited ({process.returncode}) without a verdict"
                        break
                    if time.monotonic() - started >= timeout:
                        failure = f"no verdict within {timeout:g} seconds"
                        break
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=5)
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
            process.kill()
            process.wait(timeout=5)
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
