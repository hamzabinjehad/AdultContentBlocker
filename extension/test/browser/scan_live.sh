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
#   policy  — unchanged content rechecked after a trusted settings update.
#   x/twitter — one adult tweet/caption withheld, benign posts stay usable;
#              continuous additions, DOM recycling and policy rechecks use the
#              real service-worker scorer without whole-tab redirects.
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

WORK="$(mktemp -d "${TMPDIR:-/tmp}/hisn-scan-live.XXXXXX")"
# Linux (CI): Ubuntu 24.04 forbids the unprivileged user namespaces Chrome's
# sandbox needs, and a small /dev/shm crashes renderers. The pages are local
# and ours, so the sandbox buys nothing here.
LINUX_FLAGS=()
if [ "$(uname)" = "Linux" ]; then LINUX_FLAGS=(--no-sandbox --disable-dev-shm-usage); fi

SERVER_PID=""; BROWSER_PID=""
cleanup() {
    [ -n "$BROWSER_PID" ] && { kill "$BROWSER_PID"; wait "$BROWSER_PID"; } 2>/dev/null || true
    [ -n "$SERVER_PID" ] && { kill "$SERVER_PID"; wait "$SERVER_PID"; } 2>/dev/null || true
    sleep 0.2; rm -rf "$WORK" 2>/dev/null || true
}
trap cleanup EXIT

rsync -a --exclude test --exclude _metadata --exclude keys "$EXT/" "$WORK/ext/"
cp "$HERE/scan-worker.js" "$HERE/scan-setup.html" "$HERE/scan-setup.js" "$WORK/ext/"
python3 - "$WORK/ext/manifest.json" <<'PY'
import json, sys
path = sys.argv[1]
manifest = json.load(open(path))
manifest.pop("key", None)  # No access to the user's real native bridge.
manifest["background"]["service_worker"] = "scan-worker.js"
with open(path, "w") as output:
    json.dump(manifest, output)
PY
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
feed = """<!doctype html><meta charset=utf-8><title>Mixed feed fixture</title><body>
<main><h1>Gardening feed</h1>
<article data-testid=tweet id=safe><p data-testid=tweetText>How to grow tomatoes on a balcony.</p></article>
<article data-testid=tweet id=bad><p data-testid=tweetText>ADULT_FIXTURE</p><video></video></article>
<article data-testid=tweet id=caption><p data-testid=tweetText>Today's weather.</p><img alt="ADULT_FIXTURE"></article>
<article data-testid=tweet id=medical><p data-testid=tweetText>Medical education and clinical reproductive health.</p>
<span hidden>ADULT_FIXTURE</span><script type=application/json>{"payload":"ADULT_FIXTURE"}</script></article>
<article data-testid=tweet id=policy><p data-testid=tweetText>gardenfixture</p></article>
</main><script>
const results = [], adult = "ADULT_FIXTURE";
const byId = (id) => document.getElementById(id);
const withheld = (id) => getComputedStyle(byId(id)).opacity === '0';
function check(ok, label) { results.push({ok, label}); }
const pause = (ms) => new Promise((r) => setTimeout(r, ms));
async function waitFor(predicate) {
  for (let i = 0; i < 140; i++) { if (predicate()) return true; await pause(50); }
  return false;
}
(async () => {
  check(await waitFor(() => withheld('bad') && withheld('caption') && !withheld('safe') && !withheld('medical')),
        'only signalled tweet/caption hidden; ordinary and medical/script-payload posts stay usable');
  check(byId('bad').querySelector('video').paused, 'media in hidden article remains paused');
  check(byId('bad').getAttribute('aria-hidden') === 'true' && byId('safe').getAttribute('aria-hidden') !== 'true',
        'blocked article inaccessible; benign article accessibility preserved');
  check(byId('bad').inert && !byId('safe').inert, 'blocked item inert for keyboard navigation; ordinary item usable');
  const link = document.createElement('a'); link.href = '#blocked-item'; link.textContent = 'Open fixture';
  byId('bad').append(link); link.focus();
  check(document.activeElement !== link, 'keyboard focus cannot enter hidden article');
  byId('bad').inert = false;
  check(await waitFor(() => byId('bad').inert), 'page cannot remove keyboard shielding from blocked item');
  check(byId('bad').getBoundingClientRect().height <= 1, 'blocked media-sized article collapses to its notice');
  const notices = document.querySelectorAll('[data-hisn-feed-notice]');
  check(notices.length >= 2 && Array.from(notices).every((n) => !n.querySelector('button,a')),
        'generic local notices have no instant reveal controls');
  byId('bad').querySelector('p').textContent = 'Fresh gardening notes from a recycled tweet node.';
  check(await waitFor(() => !withheld('bad')), 'recycled clean item gets fresh verdict and is released');
  byId('caption').style.removeProperty('opacity');
  check(await waitFor(() => withheld('caption')), 'page cannot reveal withheld item by stripping its opacity');
  byId('caption').querySelector('img').alt = 'Weather chart';
  check(await waitFor(() => !withheld('caption')), 'caption attribute change receives fresh clean verdict');
  for (let i = 0; i < 20; i++) {
    const article = document.createElement('article'); article.dataset.testid = 'tweet';
    article.id = 'scroll' + i;
    article.innerHTML = '<p data-testid=tweetText>' + (i === 10 ? adult : 'Safe gardening update ' + i) + '</p>';
    document.querySelector('main').append(article); await pause(30);
  }
  check(await waitFor(() => withheld('scroll10') && !withheld('scroll19')),
        'continuous infinite-scroll content checked without waiting for entire page to settle');
  check(await waitFor(() => withheld('policy')), 'trusted policy change rechecks unchanged feed item');
  let churn = 0;
  const observer = new MutationObserver((events) => { churn += events.length; });
  observer.observe(document.querySelector('main'), {childList:true, subtree:true, attributes:true, characterData:true});
  await pause(350); observer.disconnect();
  check(churn < 20, 'settled feed does not loop on scanner-owned style/notice mutations');
  const summary = document.createElement('pre'); summary.id = 'feed-summary';
  summary.textContent = JSON.stringify(results); document.body.append(summary);
  document.title = results.every((r) => r.ok) ? 'HISN FEED PASS' : 'HISN FEED FAIL';
})().catch(() => { document.title = 'HISN FEED FAIL'; });
</script></body>""".replace("ADULT_FIXTURE", adult)
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
    "policy": "<!doctype html><title>gardenfixture</title><body><p>Gardening notes.</p></body>",
    "x": feed,
    "twitter": feed,
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
    --use-mock-keychain --password-store=basic ${LINUX_FLAGS[@]+"${LINUX_FLAGS[@]}"} \
    --user-data-dir="$WORK/profile" --remote-debugging-port="$DEVTOOLS" \
    --load-extension="$WORK/ext" --disable-extensions-except="$WORK/ext" \
    --host-resolver-rules="MAP *.hisn.test 127.0.0.1:$PORT, MAP x.com 127.0.0.1:$PORT, MAP twitter.com 127.0.0.1:$PORT" --ignore-certificate-errors \
    about:blank > "$WORK/browser.log" 2>&1 &
BROWSER_PID=$!

tabs() { curl -s "http://127.0.0.1:$DEVTOOLS/json/list" 2>/dev/null || echo "[]"; }
for _ in $(seq 100); do [ "$(tabs)" != "[]" ] && break; sleep 0.1; done
sleep 2   # let the worker install and register the scanner

for page in shadow shy cancel clean policy; do
    curl -s -X PUT "http://127.0.0.1:$DEVTOOLS/json/new?https://$page.hisn.test/" > /dev/null
done
for host in x.com twitter.com; do
    curl -s -X PUT "http://127.0.0.1:$DEVTOOLS/json/new?https://$host/home" > /dev/null
done

verdicts() {
    tabs | python3 -c '
import json, sys
seen = {}
for t in json.load(sys.stdin):
    if t.get("type") != "page": continue
    u = t.get("url", "")
    if "blocked.html" in u: seen.setdefault("blocked", 0); seen["blocked"] += 1
    for p in ("shadow", "shy", "cancel", "clean", "policy"):
        if f"://{p}.hisn.test" in u: seen[p] = "open"
    for p in ("x.com", "twitter.com"):
        if f"://{p}/home" in u: seen[p] = t.get("title", "open")
print(json.dumps(seen))'
}
for _ in $(seq 100); do
    v="$(verdicts)"
    python3 -c 'import json,sys; v=json.loads(sys.argv[1]); sys.exit(0 if v.get("blocked",0)>=4 and all(v.get(p)=="HISN FEED PASS" for p in ("x.com","twitter.com")) else 1)' "$v" && break
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
check policy gone "adding a custom word rechecks an unchanged open page through the real worker"
check x.com "HISN FEED PASS" "granular X feed scanning keeps the tab and ordinary posts usable"
check twitter.com "HISN FEED PASS" "granular Twitter alias uses the same real-worker item path"
n=$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("blocked",0))' "$v")
[ "$n" = "4" ] && echo "  ok   four block pages" || { echo "  FAIL expected 4 block pages, saw $n"; fail=1; }

[ "$fail" -eq 0 ] && echo "scan-live: PASS" || {
    echo "tabs: $v"
    echo "--- the browser ($BROWSER), last lines:"; tail -15 "$WORK/browser.log" | sed 's/^/    /'
    echo "scan-live: FAIL"; exit 1; }
