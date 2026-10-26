#!/bin/bash
# JavaScript test runner for the extension's pure logic.
#
# Uses JavaScriptCore's `jsc`, which ships inside macOS itself — no Node, no
# npm, no package.json, no lockfile. That keeps the promise the rest of this
# repo makes: clone it and everything runs, with no install step and no
# dependency to audit. `node --test` would have been the obvious choice and it
# would have added the first third-party toolchain to a codebase that has
# deliberately avoided one.
#
# Pure modules — `lib/normalize.js`, `lib/score.js`, `lib/policy.js` — import
# directly. The real worker entry point is exercised through a mocked
# `chrome.*` transport in `runtime.test.js`, which is what keeps the wiring
# honest without a browser; browser-only behaviour lives in `test/browser/`.
# That split is also why scoring and normalisation live in their own files
# rather than inside the service worker.
#
#     extension/test/run.sh
set -euo pipefail
cd "$(dirname "$0")"

JSC=/System/Library/Frameworks/JavaScriptCore.framework/Versions/A/Helpers/jsc
if [ ! -x "$JSC" ]; then
  echo "jsc not found at $JSC — is this macOS?" >&2
  exit 1
fi

fail=0

# Every script the extension ships must at least PARSE. blocked.js was once
# committed missing its last `});` — a SyntaxError no unit suite noticed,
# because none imports it — and for as long as that lasted the block page
# ran no script at all: no reason, no timer, no report buttons.
echo "── syntax: every script the extension ships"
for f in ../background.js ../popup.js ../options.js ../blocked.js ../lib/*.js ../lib/locale/*.js ../worker/*.js ../visual/{broker,decisions,engine,offscreen,queue}.js; do
  # checkModuleSyntax takes SOURCE, not a path — readFile it first.
  "$JSC" -e "checkModuleSyntax(readFile('$f'))" >/dev/null || { echo "  syntax error: $f" >&2; fail=1; }
done
for f in ../content/*.js; do
  "$JSC" -e "checkSyntax('$f')" >/dev/null || { echo "  syntax error: $f" >&2; fail=1; }
done
[ "$fail" -eq 0 ] && echo "  all scripts parse"

for t in *.test.js; do
  [ -e "$t" ] || continue
  echo "── $t"
  if ! "$JSC" -m "$t"; then fail=1; fi
done

if [ "$fail" -ne 0 ]; then
  echo
  echo "FAILED" >&2
  exit 1
fi
echo
echo "all JavaScript tests passed"
