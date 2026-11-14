#!/bin/bash
#
# Measure the Android core matcher: seed load, DNS decisions and screen-text scans.
#
#     script/bench_android_core.sh
#
# Compiles the real android/core sources against android/bench/CoreBench.java and
# runs them on the committed seed and signed terms, so the numbers are the
# matcher's, not a simulation. Not part of test.sh: timings are local evidence,
# and a shared runner is not a benchmark. Needs a JDK (JAVA_HOME, or Android
# Studio's bundled one).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
OUT="$(mktemp -d "${TMPDIR:-/tmp}/hisn-android-bench.XXXXXX")"
trap 'rm -rf "$OUT"' EXIT
cd "$ROOT"

python3 - "$OUT" <<'PY'
import json, sys
out = sys.argv[1]
seed = json.load(open('seed/terms.json'))
with open(out + '/host-policy.tsv', 'w', encoding='utf-8') as f:
    for entry in seed['host_terms']:
        f.write(entry['kind'] + '\t' + entry['t'] + '\n')
    for term in seed['never_keyword']:
        f.write('never\t' + term + '\n')
    for host in seed.get('keyword_exempt_hosts', []):
        f.write('exempt\t' + host + '\n')
# The same subset AdultPolicyAssets loads, as android/test-core.sh builds it.
with open(out + '/adult-terms.tsv', 'w', encoding='utf-8') as f:
    rules = [r for r in seed['terms'] if r['l'] in ('en', 'ar', 'ar-latn')] + seed['negatives']
    for rule in rules:
        f.write(str(int(rule['w'])) + '\t' + rule.get('l', 'neg') + '\t' + rule['t'] + '\n')
manifest = json.load(open('seed/manifest.json'))
with open(out + '/seed-args', 'w') as f:
    f.write(manifest['artifacts']['domains_core.txt']['sha256'] + ' ' + str(manifest['core_domain_count']))
PY

"$JAVA_HOME/bin/javac" -nowarn -d "$OUT/classes" \
    android/core/src/main/java/app/hisn/core/*.java android/bench/CoreBench.java
# shellcheck disable=SC2046
"$JAVA_HOME/bin/java" -cp "$OUT/classes" app.hisn.core.CoreBench seed/domains_core.txt \
    $(cat "$OUT/seed-args") "$OUT/host-policy.tsv" "$OUT/adult-terms.tsv"
