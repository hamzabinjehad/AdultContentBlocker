#!/bin/bash
#
# Run the scanner harness in headless Chrome and read back its verdict.
#
#     extension/test/browser/run.sh            # uses Google Chrome / Chromium if found
#     CHROME=/path/to/chrome extension/test/browser/run.sh
#
# The harness writes its result into the DOM; --dump-dom prints the DOM after
# the page has run under --virtual-time-budget, which fast-forwards timers so
# the sixteen virtual seconds the scenarios need take about one real one. No
# driver, no Node, no dependency — the browser is the whole toolchain.
#
# A missing browser is a FAILURE, not a skip: "the browser tests passed" must
# mean they ran.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
HARNESS="${1:-harness.html}"

find_chrome() {
    if [ -n "${CHROME:-}" ] && [ -x "$CHROME" ]; then echo "$CHROME"; return; fi
    for c in \
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
        "/Applications/Chromium.app/Contents/MacOS/Chromium" \
        "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge" \
        "/Applications/Helium.app/Contents/MacOS/Helium" \
        "$(command -v google-chrome || true)" \
        "$(command -v chromium || true)" \
        "$(command -v chromium-browser || true)"; do
        if [ -n "$c" ] && [ -x "$c" ]; then echo "$c"; return; fi
    done
}

CHROME_BIN="$(find_chrome)"
if [ -z "$CHROME_BIN" ]; then
    echo "no Chrome/Chromium/Edge found — set CHROME=/path/to/binary" >&2
    exit 1
fi

OUT="$(mktemp -t hisn-harness)"
trap 'rm -f "$OUT"' EXIT
PROFILE="$(mktemp -d -t hisn-harness-profile)"
trap 'rm -rf "$PROFILE" "$OUT"' EXIT

SCREENSHOT_ARGS=("--window-size=1280,1050")
if [ -n "${SCREENSHOT:-}" ]; then SCREENSHOT_ARGS+=("--screenshot=$SCREENSHOT"); fi
"$CHROME_BIN" --headless=new --disable-gpu --no-first-run --no-default-browser-check \
    --user-data-dir="$PROFILE" \
    --allow-file-access-from-files \
    --virtual-time-budget=40000 \
    "${SCREENSHOT_ARGS[@]}" --dump-dom "file://$HERE/$HARNESS" > "$OUT" 2>/dev/null &
BROWSER_PID=$!
# Some Chromium variants keep background services alive after dumping the DOM.
# Bound our isolated test process, and use its completed DOM as the verdict.
for ((attempt=0; attempt<300; attempt++)); do
    kill -0 "$BROWSER_PID" 2>/dev/null || break
    if grep -q '</html>' "$OUT" && { [ -z "${SCREENSHOT:-}" ] || [ -s "$SCREENSHOT" ]; }; then break; fi
    sleep 0.1
done
kill -TERM "$BROWSER_PID" 2>/dev/null || true
wait "$BROWSER_PID" 2>/dev/null || true

# The summary block, unescaped enough to read.
# Every line of the summary, not just the first: the results are one per line.
python3 - "$OUT" <<'PY'
import html, re, sys
m = re.search(r'<pre id="summary" data-result="[a-z]*">(.*?)</pre>', open(sys.argv[1]).read(), re.S)
for line in (m.group(1) if m else "").splitlines():
    print("  " + html.unescape(line))
PY

if grep -q 'data-result="pass"' "$OUT"; then
    echo "browser harness: PASS"
    exit 0
fi
if grep -q 'data-result="fail"' "$OUT"; then
    echo "browser harness: FAIL" >&2
    exit 1
fi
echo "browser harness: no verdict — the page did not finish (is the virtual time budget enough?)" >&2
exit 1
