#!/bin/bash
#
# The extension's network rules in a real browser — not a model of them.
#
# Loads the extension unpacked into a headless Chromium, points the search
# engines, YouTube and a listed adult domain at a local HTTPS server with
# --host-resolver-rules, opens a page that frames each of them, and reads the
# server's log to see what actually arrived:
#
#   * Google, Bing and DuckDuckGo requests arrive already rewritten to their
#     strict SafeSearch parameter — exactly once each, so the redirect rules
#     do not loop;
#   * YouTube requests carry `YouTube-Restrict: Strict`;
#   * a domain on the bundled blocklist never arrives at all;
#   * URL keyword rules fire — token terms on the hostname only (a search for
#     "adult adhd" or a `?sex=female` form is left alone), substring terms
#     anywhere, with innocent continuations guarded (Milford is not milf).
#     The token rules were once silently dropped by Chrome: a \p{L} boundary
#     compiled past DNR's regex memory limit.
#
# Needs a Chromium build that still honours --load-extension (Helium,
# Chromium; branded Chrome 137+ ignores it) and openssl.
#
#     extension/test/browser/dnr.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
EXT="$(cd "$HERE/../.." && pwd)"

find_chromium() {
    if [ -n "${CHROME:-}" ] && [ -x "$CHROME" ]; then echo "$CHROME"; return; fi
    for c in "/Applications/Helium.app/Contents/MacOS/Helium" \
             "/Applications/Chromium.app/Contents/MacOS/Chromium" \
             "$(command -v chromium || true)"; do
        if [ -n "$c" ] && [ -x "$c" ]; then echo "$c"; return; fi
    done
}
BROWSER="$(find_chromium)"
[ -n "$BROWSER" ] || { echo "no Chromium that loads unpacked extensions — set CHROME" >&2; exit 1; }

WORK="$(mktemp -d -t hisn-dnr)"
SERVER_PID=""
cleanup() { [ -n "$SERVER_PID" ] && { kill "$SERVER_PID"; wait "$SERVER_PID"; } 2>/dev/null || true
            sleep 0.2; rm -rf "$WORK" 2>/dev/null || true; }
trap cleanup EXIT

# A copy, so the browser's _metadata cache never lands in the source tree.
rsync -a --exclude test --exclude _metadata --exclude keys "$EXT/" "$WORK/ext/"

openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=hisn-test" \
    -keyout "$WORK/key.pem" -out "$WORK/cert.pem" >/dev/null 2>&1

# A domain the bundled static rules block, taken from the rules themselves.
BLOCKED="$(python3 -c "import json;r=json.load(open('$EXT/rules/dnr_block_rules.json'));print(r[0]['condition']['requestDomains'][0])")"

cat > "$WORK/server.py" <<PY
import http.server, ssl, sys
LOG = open("$WORK/requests.log", "a", buffering=1)
PAGE = b"""<!doctype html><meta charset=utf-8><body>
<iframe src="https://www.google.com/search?q=hisn"></iframe>
<iframe src="https://www.bing.com/search?q=hisn"></iframe>
<iframe src="https://duckduckgo.com/?q=hisn"></iframe>
<iframe src="https://www.youtube.com/results?search_query=hisn"></iframe>
<iframe src="https://$BLOCKED/"></iframe>
<iframe src="https://hot-sex.hisn.test/"></iframe>
<iframe src="https://kw.hisn.test/search?q=adult%20adhd"></iframe>
<iframe src="https://kw2.hisn.test/?sex=female"></iframe>
<iframe src="https://kw4.hisn.test/free-porn-videos"></iframe>
<iframe src="https://www.milford.hisn.test/"></iframe>
<iframe src="https://milfs.hisn.test/"></iframe>
<p id=done>loaded</p></body>"""
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        host = self.headers.get("Host", "")
        LOG.write(f"{host}\t{self.path}\t{self.headers.get('YouTube-Restrict', '-')}\n")
        body = PAGE if host.startswith("harness.hisn.test") else b"<p>ok</p>"
        self.send_response(200)
        self.send_header("Content-Type", "text/html")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *a): pass
CTX = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
CTX.load_cert_chain("$WORK/cert.pem", "$WORK/key.pem")
class TLSServer(http.server.ThreadingHTTPServer):
    # A deep backlog, and each TLS handshake in its connection's own thread
    # rather than inside accept(): with socketserver's queue of 5 and the
    # handshakes serialised, a dozen frames connecting at once overflowed
    # the queue and macOS reset whichever connection came last.
    request_queue_size = 128
    daemon_threads = True
    def get_request(self):
        sock, addr = self.socket.accept()
        return CTX.wrap_socket(sock, server_side=True, do_handshake_on_connect=False), addr
    def finish_request(self, request, client_address):
        try:
            request.do_handshake()
        except (ssl.SSLError, OSError):
            return
        super().finish_request(request, client_address)
srv = TLSServer(("127.0.0.1", 0), H)
print(srv.server_address[1], flush=True)
srv.serve_forever()
PY
python3 "$WORK/server.py" > "$WORK/port" &
SERVER_PID=$!
for _ in $(seq 50); do [ -s "$WORK/port" ] && break; sleep 0.1; done
PORT="$(head -1 "$WORK/port")"

# With an extension loaded, headless Chromium does not exit after --dump-dom,
# so run it in the background and stop it once the frames have been fetched.
"$BROWSER" --headless=new --disable-gpu --no-first-run --no-default-browser-check \
    --user-data-dir="$WORK/profile" \
    --load-extension="$WORK/ext" --disable-extensions-except="$WORK/ext" \
    --host-resolver-rules="MAP * 127.0.0.1:$PORT" --ignore-certificate-errors \
    "https://harness.hisn.test/" > /dev/null 2>&1 &
BROWSER_PID=$!
arrived() {  # every frame that is expected to reach the server has
    for h in 'www\.google\.com' 'www\.bing\.com' 'duckduckgo\.com' 'www\.youtube\.com' \
             'kw\.hisn\.test' 'kw2\.hisn\.test' 'www\.milford\.hisn\.test'; do
        grep -q "^$h	" "$WORK/requests.log" 2>/dev/null || return 1
    done
}
for _ in $(seq 200); do arrived && break; sleep 0.1; done
sleep 1   # let any stray request (a loop, the blocked frame) land
kill -TERM "$BROWSER_PID" 2>/dev/null || true
wait "$BROWSER_PID" 2>/dev/null || true

LOG="$WORK/requests.log"
fail=0
expect() {  # description, grep pattern
    if grep -qE "$2" "$LOG"; then echo "  ok   $1"; else echo "  FAIL $1"; fail=1; fi
}
refuse() {
    if grep -qE "$2" "$LOG"; then echo "  FAIL $1"; fail=1; else echo "  ok   $1"; fi
}
expect "the harness page loaded"                      '^harness\.hisn\.test'
expect "Google arrives with safe=active"              '^www\.google\.com	/search\?.*safe=active'
refuse "Google never arrives without it"              '^www\.google\.com	/search\?q=hisn	'
expect "Bing arrives with adlt=strict"                '^www\.bing\.com	/search\?.*adlt=strict'
expect "DuckDuckGo arrives with kp=1"                 '^duckduckgo\.com	/\?.*kp=1'
expect "YouTube carries YouTube-Restrict: Strict"     '^www\.youtube\.com	.*	Strict$'
refuse "the listed domain ($BLOCKED) never arrives"   "^$BLOCKED"
refuse "a token keyword blocks a site NAMED with it (hot-sex.…)" '^hot-sex\.hisn\.test'
expect "…but not a search that mentions it (adult adhd)"  '^kw\.hisn\.test'
expect "…nor a form field (?sex=female)"                   '^kw2\.hisn\.test'
refuse "a substring keyword fires anywhere (/free-porn-…)" '^kw4\.hisn\.test'
expect "Milford is not milf (guarded continuation)"        '^www\.milford\.hisn\.test'
refuse "…while milfs still is"                              '^milfs\.hisn\.test'

n="$(grep -cE '^www\.google\.com	/search' "$LOG" || true)"
[ "$n" = "1" ] && echo "  ok   no redirect loop (one Google request)" \
               || { echo "  FAIL expected one Google request, saw $n"; fail=1; }

if [ "$fail" -ne 0 ]; then
    echo "--- requests the server saw:"; cat "$LOG"
    echo "dnr: FAIL"; exit 1
fi
echo "dnr: PASS"
