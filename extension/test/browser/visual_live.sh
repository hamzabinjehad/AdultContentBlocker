#!/bin/bash
#
# The offscreen visual engine through the real extension, in real Chrome.
#
# The status request must cross the service worker -> offscreen boundary. A
# Chrome service worker has no sender.url, which the old offscreen gate rejected;
# this harness fails if the status returns failed, unavailable or never paints.
#
#     extension/test/browser/visual_live.sh
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

WORK="$(mktemp -d "${TMPDIR:-/tmp}/hisn-visual-live.XXXXXX")"
WORK="$(cd "$WORK" && pwd -P)"
LINUX_FLAGS=()
if [ "$(uname)" = "Linux" ]; then LINUX_FLAGS=(--no-sandbox --disable-dev-shm-usage); fi
BROWSER_PID=""
cleanup() {
    [ -n "$BROWSER_PID" ] && { kill "$BROWSER_PID"; wait "$BROWSER_PID"; } 2>/dev/null || true
    sleep 0.2; rm -rf "$WORK" 2>/dev/null || true
}
trap cleanup EXIT

python3 "$EXT/prepare_unpacked.py" --out "$WORK/ext" >/dev/null
cp "$HERE/visual-test-boot.js" "$HERE/visual-test.html" "$HERE/visual-test.js" "$WORK/ext/"
printf '\nimport "./visual-test-boot.js";\n' >> "$WORK/ext/background.js"
python3 - "$WORK/ext/manifest.json" <<'PY'
import json, sys
path = sys.argv[1]
manifest = json.load(open(path))
manifest.pop("key", None)
manifest["permissions"] = [p for p in manifest["permissions"] if p != "nativeMessaging"]
with open(path, "w") as output:
    json.dump(manifest, output)
PY

DEVTOOLS=9$((RANDOM % 900 + 100))
"$BROWSER" --headless=new --disable-gpu --no-first-run --no-default-browser-check \
    --disable-background-timer-throttling --disable-renderer-backgrounding \
    --disable-backgrounding-occluded-windows \
    --use-mock-keychain --password-store=basic ${LINUX_FLAGS[@]+"${LINUX_FLAGS[@]}"} \
    --user-data-dir="$WORK/profile" --remote-debugging-port="$DEVTOOLS" \
    --load-extension="$WORK/ext" --disable-extensions-except="$WORK/ext" \
    about:blank > "$WORK/browser.log" 2>&1 &
BROWSER_PID=$!

tabs() { curl -s "http://127.0.0.1:$DEVTOOLS/json/list" 2>/dev/null || echo "[]"; }
for _ in $(seq 100); do [ "$(tabs)" != "[]" ] && break; sleep 0.1; done

title=""
for _ in $(seq 200); do
    title="$(tabs | python3 -c '
import json, sys
for target in json.load(sys.stdin):
    if target.get("type") == "page" and "visual-test.html" in target.get("url", ""):
        print(target.get("title", ""))
        break
')"
    case "$title" in HISN\ VISUAL\ PASS*|HISN\ VISUAL\ FAIL*) break ;; esac
    sleep 0.1
done

if [ "$title" = "HISN VISUAL PASS" ] || [[ "$title" == "HISN VISUAL PASS "* ]]; then
    echo "  ok   offscreen visual status crosses the real worker boundary ($title)"
    echo "visual-live: PASS"
    exit 0
fi
echo "  FAIL offscreen visual status: ${title:-no visual-test.html page}"
tabs | python3 -c '
import json, sys
for target in json.load(sys.stdin):
    if target.get("type") == "page":
        print("  tab:", target.get("url"), "|", target.get("title"))
'
echo "--- the browser ($BROWSER), last lines:"
tail -15 "$WORK/browser.log" | sed 's/^/    /'
echo "visual-live: FAIL"
exit 1
