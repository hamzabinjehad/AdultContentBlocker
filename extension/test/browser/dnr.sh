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
#   * every case of blocklist/terms/host_cases.json — the contract the Python
#     compiler and the Swift filter are held to — gets the same verdict from
#     Chrome's own rules: blocked hosts never arrive, the rest do;
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
CASES="$(cd "$EXT/.." && pwd)/blocklist/terms/host_cases.json"

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

WORK="$(mktemp -d "${TMPDIR:-/tmp}/hisn-dnr.XXXXXX")"
# Canonicalize macOS /var or /tmp aliases before the preparation tool's
# deliberate symlink-path checks. This is our own fresh test directory.
WORK="$(cd "$WORK" && pwd -P)"
# Linux (CI): Ubuntu 24.04 forbids the unprivileged user namespaces Chrome's
# sandbox needs, and a small /dev/shm crashes renderers. The pages are local
# and ours, so the sandbox buys nothing here.
LINUX_FLAGS=()
if [ "$(uname)" = "Linux" ]; then LINUX_FLAGS=(--no-sandbox --disable-dev-shm-usage); fi

SERVER_PID=""
DEBUG_FLAGS=()
if [ -n "${DNR_NETLOG:-}" ]; then DEBUG_FLAGS=(--log-net-log="$DNR_NETLOG"); fi
cleanup() { [ -n "$SERVER_PID" ] && { kill "$SERVER_PID"; wait "$SERVER_PID"; } 2>/dev/null || true
            sleep 0.2; rm -rf "$WORK" 2>/dev/null || true; }
trap cleanup EXIT

# Exercise the same clean-copy path used by Load unpacked, even when the
# source has a cache from a different browser. Never mutate that source/cache.
python3 "$EXT/prepare_unpacked.py" --out "$WORK/ext" >/dev/null
cp "$HERE/dnr-setup.html" "$HERE/dnr-setup.js" "$HERE/dnr-worker.js" "$WORK/ext/"
python3 - "$WORK/ext/manifest.json" <<'PY'
import json, sys
path = sys.argv[1]
manifest = json.load(open(path))
# A temporary identity cannot connect to the real user's installed native host.
# The fixture must own its settings without touching the user's lock or lists.
manifest.pop("key", None)
manifest["background"]["service_worker"] = "dnr-worker.js"
with open(path, "w") as output:
    json.dump(manifest, output)
PY

openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=hisn-test" \
    -keyout "$WORK/key.pem" -out "$WORK/cert.pem" >/dev/null 2>&1
SPKI="$(openssl x509 -in "$WORK/cert.pem" -pubkey -noout \
    | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl base64 -A)"

# A domain the bundled static rules block, taken from the rules themselves.
BLOCKED="$(python3 -c "import json;r=json.load(open('$EXT/rules/dnr_block_rules.json'));print(r[0]['condition']['requestDomains'][0])")"

cat > "$WORK/server.py" <<PY
import http.server, json, ssl, sys
LOG = open("$WORK/requests.log", "a", buffering=1)
# One frame per case of the shared host contract.
CASE_FRAMES = "".join('<iframe src="https://%s/?hisn-case"></iframe>\n' % c["host"]
                      for c in json.load(open("$CASES"))["cases"]).encode()
POLICY = json.load(open("$EXT/../blocklist/web_protection.json"))
POLICY_FRAMES = "".join('<iframe src="https://%s/?hisn-policy"></iframe><img src="https://%s/image.png">\n' % (h, h)
    for h in POLICY["blockedSearchHosts"] + POLICY["blockedViewerDomains"]
    + ["api." + h for h in POLICY["blockedViewerDomains"]]).encode()
PAGE = b"""<!doctype html><meta charset=utf-8><body>
<iframe src="https://www.google.com/search?q=hisn&safe=off"></iframe>
<iframe src="https://www.bing.com/search?q=hisn&adlt=off"></iframe>
<iframe src="https://duckduckgo.com/?q=hisn&kp=-2"></iframe>
<script>
for (const url of [
 "https://www.google.com/search?q=background&safe=off",
 "https://www.bing.com/images/search?q=background&adlt=off",
 "https://duckduckgo.com/?q=background&kp=-2",
 "https://search.brave.com/images?q=background&safesearch=off"
]) fetch(url).catch(() => {});
</script>
<iframe src="https://www.youtube.com/results?search_query=hisn"></iframe>
<iframe src="https://search.brave.com/search?q=hisn&safesearch=off"></iframe>
<iframe src="https://search.brave.com/images?q=hisn&safesearch=off"></iframe>
<iframe src="https://search.brave.com/videos?q=hisn&safesearch=off"></iframe>
<iframe src="https://search.brave.com/news?q=hisn&safesearch=off"></iframe>
<iframe src="https://cn.bing.com/images/search?q=hisn"></iframe>
<iframe src="https://safe.search.brave.com/search?q=hisn"></iframe>
<iframe src="https://mail.yandex.ru/?hisn-unrelated"></iframe>
<iframe src="https://yandex.com.hisn.test/?hisn-lookalike"></iframe>
<iframe src="https://sotwe.com.hisn.test/?hisn-lookalike"></iframe>
<iframe src="https://$BLOCKED/"></iframe>
<iframe src="https://hot-sex.hisn.test/"></iframe>
<iframe src="https://kw.hisn.test/search?q=adult%20adhd"></iframe>
<iframe src="https://kw2.hisn.test/?sex=female"></iframe>
<iframe src="https://kw4.hisn.test/free-porn-videos"></iframe>
<iframe src="https://www.milford.hisn.test/"></iframe>
<iframe src="https://milfs.hisn.test/"></iframe>
<iframe src="https://www.reddit.com/r/nsfw_gifs/"></iframe>
<iframe src="https://www.reddit.com/r/nosleep/"></iframe>
<p id=done>loaded</p></body>""".replace(b"<p id=done>", CASE_FRAMES + POLICY_FRAMES + b"<p id=done>")
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
# --use-mock-keychain / --password-store=basic: on a Mac nobody is sitting at
# (a CI runner), Chrome otherwise asks the Keychain for its storage key and
# can wait on a prompt no one will answer.
"$BROWSER" --headless=new --disable-gpu --no-first-run --no-default-browser-check \
    --use-mock-keychain --password-store=basic ${LINUX_FLAGS[@]+"${LINUX_FLAGS[@]}"} \
    ${DEBUG_FLAGS[@]+"${DEBUG_FLAGS[@]}"} \
    --user-data-dir="$WORK/profile" \
    --load-extension="$WORK/ext" --disable-extensions-except="$WORK/ext" \
    --host-resolver-rules="MAP * 127.0.0.1:$PORT" --ignore-certificate-errors-spki-list="$SPKI" \
    "about:blank" > "$WORK/browser.log" 2>&1 &
BROWSER_PID=$!
# The host cases, judged from what reached the server. A host is compared
# lowercased and without a trailing dot, as a browser sends it.
cat > "$WORK/cases.py" <<'PY'
import json, sys
cases = json.load(open(sys.argv[1]))["cases"]
seen = set()
for line in open(sys.argv[2]):
    host, path = line.split("\t")[:2]
    if path.startswith("/?hisn-case"):
        seen.add(host.lower().rstrip("."))
norm = lambda h: h.lower().rstrip(".")
if sys.argv[3] == "--waiting":   # exit 0 once every case that should arrive has
    sys.exit(0 if all(norm(c["host"]) in seen for c in cases if not c["block"]) else 1)
bad = 0
for c in cases:
    arrived = norm(c["host"]) in seen
    if arrived == c["block"]:
        bad += 1
        print(f"  FAIL host case {c['host']}: expected {'blocked' if c['block'] else 'allowed'} — {c['why']}")
print(f"  {'ok  ' if not bad else 'FAIL'} host contract: {len(cases) - bad}/{len(cases)} cases as host_cases.json says")
sys.exit(1 if bad else 0)
PY
arrived() {  # every frame that is expected to reach the server has
    [ "$(grep -c 'q=background' "$WORK/requests.log" 2>/dev/null || true)" = "4" ] || return 1
    for h in 'www\.google\.com' 'www\.bing\.com' 'duckduckgo\.com' 'www\.youtube\.com' \
             'kw\.hisn\.test' 'kw2\.hisn\.test' 'www\.milford\.hisn\.test' 'www\.reddit\.com' \
             'search\.brave\.com' 'mail\.yandex\.ru' 'yandex\.com\.hisn\.test' 'sotwe\.com\.hisn\.test'; do
        grep -q "^$h	" "$WORK/requests.log" 2>/dev/null || return 1
    done
    python3 "$WORK/cases.py" "$CASES" "$WORK/requests.log" --waiting
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
refuse "a Reddit community named nsfw (rules/paths.json)"    '^www\.reddit\.com	/r/nsfw'
expect "…but not every community (/r/nosleep)"              '^www\.reddit\.com	/r/nosleep'

for path in search images videos news; do
    # The same endpoints must be protected for fetch(), not just page loads.
    expect "allowlisted Brave $path arrives with strict filtering" "^search\\.brave\\.com	/$path\\?.*safesearch=strict"
done
expect "background Google search is safe" '^www\.google\.com	/search\?.*q=background.*safe=active'
expect "background Bing image search is safe" '^www\.bing\.com	/images/search\?.*q=background.*adlt=strict'
expect "background DuckDuckGo search is safe" '^duckduckgo\.com	/\?.*q=background.*kp=1'
expect "background Brave image search is safe" '^search\.brave\.com	/images\?.*q=background.*safesearch=strict'
refuse "unsafe Brave search parameters never reach the server" '^search\.brave\.com	.*safesearch=off'
refuse "custom-blocked Bing host stays blocked despite SafeSearch" '^cn\.bing\.com	'
refuse "custom-blocked Brave alias stays blocked despite SafeSearch" '^safe\.search\.brave\.com	'
expect "unrelated Yandex services are not blanket-blocked" '^mail\.yandex\.ru	'
expect "Yandex lookalike is not blocked" '^yandex\.com\.hisn\.test	'
expect "viewer lookalike is not blocked" '^sotwe\.com\.hisn\.test	'
python3 - "$EXT/../blocklist/web_protection.json" "$LOG" <<'PY' || fail=1
import json, sys
policy = json.load(open(sys.argv[1]))
hosts = {line.split("\t")[0].lower().rstrip(".") for line in open(sys.argv[2])}
bad = [h for h in hosts if h in policy["blockedSearchHosts"] or any(
    h == d or h.endswith("." + d) for d in policy["blockedViewerDomains"])]
if bad:
    print("  FAIL built-in blocks reached the server:", bad)
else:
    print("  ok   Yandex and viewer documents/images blocked even when allowlisted")
sys.exit(bool(bad))
PY

python3 "$WORK/cases.py" "$CASES" "$LOG" --judge || fail=1

n="$(grep -cE '^www\.google\.com	/search\?q=hisn' "$LOG" || true)"
[ "$n" = "1" ] && echo "  ok   no redirect loop (one Google request)" \
               || { echo "  FAIL expected one Google request, saw $n"; fail=1; }

if [ "$fail" -ne 0 ]; then
    echo "--- requests the server saw:"; cat "$LOG"
    echo "--- the browser ($BROWSER), last lines:"; tail -15 "$WORK/browser.log" | sed 's/^/    /'
    echo "dnr: FAIL"; exit 1
fi
echo "dnr: PASS"
