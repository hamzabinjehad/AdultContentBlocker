#!/usr/bin/env python3
"""Serve extension/ over HTTP so the harness can be opened in a browser.

    python3 extension/test/browser/serve.py [port]      # then open /test/browser/harness.html

Only for looking at the harness by hand — run.sh drives headless Chrome from
file:// and needs no server.
"""
import http.server
import os
import sys
from pathlib import Path

os.chdir(Path(__file__).resolve().parents[2])      # extension/
port = int(sys.argv[1]) if len(sys.argv) > 1 else 8731


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass


http.server.ThreadingHTTPServer(("127.0.0.1", port), Quiet).serve_forever()
