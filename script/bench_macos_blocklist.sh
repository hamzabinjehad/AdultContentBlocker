#!/bin/bash
#
# Measure the macOS socket filter's list load and per-flow verdict cost.
#
#     script/bench_macos_blocklist.sh           # the bundled seed (committed)
#     script/bench_macos_blocklist.sh full      # dist/ (or HISN_LIST_DIR)
#
# Compiles the real BlocklistStore against a small driver, so the numbers are
# the filter's, not a simulation. Not part of test.sh: timings are local
# evidence, and a shared runner is not a benchmark.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-seed}"
OUT="${TMPDIR:-/tmp}/hisn-bench"
mkdir -p "$OUT"
cd "$ROOT"

swiftc -O -o "$OUT/bench_blocklist" \
    macos/bench/BenchBlocklist.swift \
    macos/Hisn/BlocklistStore.swift \
    macos/Hisn/WebProtectionPolicy.swift \
    shared/apple/AdultTldGuard.swift \
    shared/apple/TextNormalizer.swift \
    shared/apple/Punycode.swift

"$OUT/bench_blocklist" "$MODE"
