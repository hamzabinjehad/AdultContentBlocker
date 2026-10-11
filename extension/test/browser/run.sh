#!/bin/bash
#
# Run the scanner harness in headless Chrome and read back its verdict.
#
#     extension/test/browser/run.sh            # uses Google Chrome / Chromium if found
#     CHROME=/path/to/chrome extension/test/browser/run.sh
#
# The harness writes its result into the DOM; on macOS --dump-dom prints it after
# the page has run under --virtual-time-budget, which fast-forwards timers so
# the sixteen virtual seconds the scenarios need take about one real one. No
# driver, no Node, no dependency — the browser is the whole toolchain. Linux
# uses a stdlib-only loopback runner with real timers and the same verdict.
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

# Linux's file:// virtual-time dump can report no verdict even though Chrome
# exits successfully. Real timers on loopback HTTP exercise the actual page;
# a missing verdict now fails CI rather than looking like a successful skip.
if [ "$(uname)" = "Linux" ] || [ "${HISN_HARNESS_REALTIME:-}" = "1" ]; then
    python3 "$HERE/run_live.py" --browser "$CHROME_BIN" --harness "$HARNESS"
    exit $?
fi

# Templates with X's: GNU mktemp (Linux CI) refuses a bare prefix.
OUT="$(mktemp "${TMPDIR:-/tmp}/hisn-harness.XXXXXX")"
ERR="$(mktemp "${TMPDIR:-/tmp}/hisn-harness-err.XXXXXX")"
PROFILE="$(mktemp -d "${TMPDIR:-/tmp}/hisn-harness-profile.XXXXXX")"
trap 'rm -rf "$PROFILE" "$OUT" "$ERR"' EXIT

SCREENSHOT_ARGS=("--window-size=1280,1050")
if [ -n "${SCREENSHOT:-}" ]; then SCREENSHOT_ARGS+=("--screenshot=$SCREENSHOT"); fi
# --use-mock-keychain / --password-store=basic: on a Mac nobody is sitting at
# (a CI runner), Chrome otherwise asks the Keychain for its storage key and
# can wait on a prompt no one will answer.
"$CHROME_BIN" --headless=new --disable-gpu --no-first-run --no-default-browser-check \
    --use-mock-keychain --password-store=basic \
    --user-data-dir="$PROFILE" \
    --allow-file-access-from-files \
    --virtual-time-budget=40000 \
    "${SCREENSHOT_ARGS[@]}" --dump-dom "file://$HERE/$HARNESS" > "$OUT" 2>"$ERR" &
BROWSER_PID=$!
# Some Chromium variants keep background services alive after dumping the DOM.
# Bound our isolated test process, and use its completed DOM as the verdict.
# 30 seconds is ample where virtual time fast-forwards (about one, on a Mac).
# Every verdict says how long it took.
WAIT_TENTHS=300
STARTED=$SECONDS
for ((attempt=0; attempt<WAIT_TENTHS; attempt++)); do
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
    echo "browser harness: PASS ($((SECONDS - STARTED))s)"
    exit 0
fi
if grep -q 'data-result="fail"' "$OUT"; then
    echo "browser harness: FAIL" >&2
    exit 1
fi
echo "browser harness: no verdict after $((SECONDS - STARTED))s — the page did not finish (is the virtual time budget enough?)" >&2
# What the browser said, so a machine where it never started (CI) is not a
# silent "no verdict".
echo "  browser: $CHROME_BIN" >&2
echo "  DOM bytes: $(wc -c < "$OUT" | tr -d ' '); last browser lines:" >&2
tail -15 "$ERR" | sed 's/^/    /' >&2
exit 1
