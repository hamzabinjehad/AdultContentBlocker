#!/bin/bash
#
# The page scanner against pages built to get past it — in a real browser,
# with the real extension, the real worker and the real vocabulary.
#
#   shadow  — the explicit text lives only inside a CLOSED shadow root;
#   shy     — every word is split with soft hyphens (renders whole, tokenises
#             as fragments unless the normaliser strips them);
#   cancel  — the page cancels every navigation it can (Navigation API), so
#             the scanner's own location.replace never happens and the worker
#             has to replace the tab itself;
#   clean   — an ordinary page, which must be left alone.
#
# Each is opened in its own tab; the browser's DevTools endpoint then says
# which tabs ended on the block page.
#
#     extension/test/browser/scan_live.sh
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

WORK="$(mktemp -d -t hisn-scan-live)"
SERVER_PID=""; BROWSER_PID=""
cleanup() {
    [ -n "$BROWSER_PID" ] && { kill "$BROWSER_PID"; wait "$BROWSER_PID"; } 2>/dev/null || true
    [ -n "$SERVER_PID" ] && { kill "$SERVER_PID"; wait "$SERVER_PID"; } 2>/dev/null || true
    sleep 0.2; rm -rf "$WORK" 2>/dev/null || true
}
trap cleanup EXIT

rsync -a --exclude test --exclude _metadata --exclude keys "$EXT/" "$WORK/ext/"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=hisn-test" \
    -keyout "$WORK/key.pem" -out "$WORK/cert.pem" >/dev/null 2>&1

cat > "$WORK/server.py" <<'PY'
import http.server, ssl, sys, json
work = sys.argv[1]
# Explicit text from the evaluation corpus — known to score as adult.
adult = ("Watch free adult videos in HD. Thousands of adult movies added daily. "
         "Categories: anal sex, big tits, blowjob, amateur. Free porn, hardcore porn videos, "
         "xxx porn tube, nude girls, naked sex videos.")
shy = " ".join("­".join(word) for word in adult.split())
pages = {
    "shadow": f"""<!doctype html><title>Article</title><body><p>Recipes and gardening notes.</p>
<div id=host></div><script>
const r = document.getElementById('host').attachShadow({{mode:'closed'}});
r.innerHTML = '<section><h2>Videos</h2><p>{adult}</p></section>';
</script></body>""",
    "shy": f"<!doctype html><title>Article</title><body><p>{shy}</p></body>",
    "cancel": f"""<!doctype html><title>Article</title><body><p>{adult}</p><script>
if (window.navigation) navigation.addEventListener('navigate', (e) => e.preventDefault());
</script></body>""",
    "clean": """<!doctype html><title>Gardening</title><body><p>How to grow tomatoes on a
balcony: choose a sunny spot, water in the morning, and feed every two weeks.</p></body>""",
}
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        name = self.headers.get("Host", "").split(".")[0]
        body = pages.get(name, "<p>ok</p>").encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *a): pass
CTX = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
CTX.load_cert_chain(f"{work}/cert.pem", f"{work}/key.pem")
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
python3 "$WORK/server.py" "$WORK" > "$WORK/port" &
SERVER_PID=$!
for _ in $(seq 50); do [ -s "$WORK/port" ] && break; sleep 0.1; done
PORT="$(head -1 "$WORK/port")"
DEVTOOLS=9$((RANDOM % 900 + 100))

# --use-mock-keychain / --password-store=basic: on a Mac nobody is sitting at
# (a CI runner), Chrome otherwise asks the Keychain for its storage key and
# can wait on a prompt no one will answer.
"$BROWSER" --headless=new --disable-gpu --no-first-run --no-default-browser-check \
    --use-mock-keychain --password-store=basic \
    --user-data-dir="$WORK/profile" --remote-debugging-port="$DEVTOOLS" \
    --load-extension="$WORK/ext" --disable-extensions-except="$WORK/ext" \
    --host-resolver-rules="MAP *.hisn.test 127.0.0.1:$PORT" --ignore-certificate-errors \
    about:blank > "$WORK/browser.log" 2>&1 &
BROWSER_PID=$!

tabs() { curl -s "http://127.0.0.1:$DEVTOOLS/json/list" 2>/dev/null || echo "[]"; }
for _ in $(seq 100); do [ "$(tabs)" != "[]" ] && break; sleep 0.1; done
sleep 2   # let the worker install and register the scanner

for page in shadow shy cancel clean; do
    curl -s -X PUT "http://127.0.0.1:$DEVTOOLS/json/new?https://$page.hisn.test/" > /dev/null
done

verdicts() {
    tabs | python3 -c '
import json, sys
seen = {}
for t in json.load(sys.stdin):
    if t.get("type") != "page": continue
    u = t.get("url", "")
    if "blocked.html" in u: seen.setdefault("blocked", 0); seen["blocked"] += 1
    for p in ("shadow", "shy", "cancel", "clean"):
        if f"://{p}.hisn.test" in u: seen[p] = "open"
print(json.dumps(seen))'
}
for _ in $(seq 60); do
    v="$(verdicts)"
    python3 -c 'import json,sys; v=json.loads(sys.argv[1]); sys.exit(0 if v.get("blocked",0)>=3 else 1)' "$v" && break
    sleep 0.25
done
v="$(verdicts)"

fail=0
check() { # page expected(open|gone)
    state=$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get(sys.argv[2], "gone"))' "$v" "$1")
    if [ "$state" = "$2" ]; then echo "  ok   $1: $3"; else echo "  FAIL $1: $3 (tab is $state)"; fail=1; fi
}
check shadow gone "text in a closed shadow root is read and blocked"
check shy    gone "soft-hyphen-split words are read and blocked"
check cancel gone "a page that cancels its navigation is replaced by the worker"
check clean  open "an ordinary page is left alone"
n=$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("blocked",0))' "$v")
[ "$n" = "3" ] && echo "  ok   three block pages" || { echo "  FAIL expected 3 block pages, saw $n"; fail=1; }

[ "$fail" -eq 0 ] && echo "scan-live: PASS" || {
    echo "tabs: $v"
    echo "--- the browser ($BROWSER), last lines:"; tail -15 "$WORK/browser.log" | sed 's/^/    /'
    echo "scan-live: FAIL"; exit 1; }
