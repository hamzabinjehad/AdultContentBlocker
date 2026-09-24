#!/bin/bash
# Score the evaluation corpus with the shipped vocabulary. See run.js.
#     extension/eval/run.sh [sensitivity]
set -euo pipefail
cd "$(dirname "$0")"
JSC=/System/Library/Frameworks/JavaScriptCore.framework/Versions/A/Helpers/jsc
[ -x "$JSC" ] || { echo "jsc not found — is this macOS?" >&2; exit 1; }
"$JSC" -m run.js -- "${1:-50}" "${2:-}"
