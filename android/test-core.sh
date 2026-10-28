#!/bin/sh
set -eu
cd "$(dirname "$0")"
hisn_test_output=$(mktemp -d "${TMPDIR:-/tmp}/hisn-core.XXXXXX")
# Test class files stay in a newly created temporary directory; no repo files are changed.
hisn_java_bin=${JAVA_HOME:+$JAVA_HOME/bin/}
"${hisn_java_bin}javac" -d "$hisn_test_output" core/src/main/java/app/hisn/core/*.java core/src/test/java/app/hisn/core/*.java
for hisn_test in TextFolding WordMatcher VisibleTextSample GuardHealth SetupGuide DomainPolicy BrowserCanary DnsPolicy DnsUdpTruncation DnsExchange DnsTunnelProbe DnsTunnelTcpProbe DnsReplyGuard Ipv4DnsPacket Ipv4TcpPacket DnsTcpServer DnsTcpFallback DnsPacketKind EncryptedDnsProviders DnsHealth CommitmentClock DailyAppSchedule PauseTally ShortVideoSurfaces UninstallProtection VisualFrameGate VisualExecutionPolicy; do
    "${hisn_java_bin}java" -cp "$hisn_test_output" "app.hisn.core.${hisn_test}Test"
done
# Compile the actual release-only hook separately: it must remain a no-op without Android APIs.
"${hisn_java_bin}javac" -cp "$hisn_test_output" -d "$hisn_test_output" \
    app/src/release/java/app/hisn/android/BrowserTestHooks.java \
    release-test/java/app/hisn/android/ReleaseBrowserTestHooksTest.java
"${hisn_java_bin}java" -cp "$hisn_test_output" app.hisn.android.ReleaseBrowserTestHooksTest
"${hisn_java_bin}javac" -d "$hisn_test_output/release-visual" \
    app/src/release/java/app/hisn/android/VisualTestHooks.java \
    release-test/java/app/hisn/android/ReleaseVisualTestHooksTest.java
"${hisn_java_bin}java" -cp "$hisn_test_output/release-visual" app.hisn.android.ReleaseVisualTestHooksTest
"${hisn_java_bin}javac" -d "$hisn_test_output/debug-visual" \
    app/src/debug/java/app/hisn/android/VisualTestHooks.java \
    release-test/java/app/hisn/android/DebugVisualTestHooksTest.java
"${hisn_java_bin}java" -cp "$hisn_test_output/debug-visual" app.hisn.android.DebugVisualTestHooksTest
python3 - <<'PY' > "$hisn_test_output/adult-terms.tsv"
import json
data = json.load(open('../seed/terms.json'))
rules = [r for r in data['terms'] if r['l'] in ('en', 'ar')] + data['negatives']
for rule in rules:
    assert not any(c in rule['t'] for c in '\r\n\t')
    print(str(int(rule['w'])) + '\t' + rule['t'])
PY
"${hisn_java_bin}java" -cp "$hisn_test_output" app.hisn.core.AdultTextPolicyTest "$hisn_test_output/adult-terms.tsv"
"${hisn_java_bin}java" -cp "$hisn_test_output" app.hisn.core.AdultTextCorpusTest "$hisn_test_output/adult-terms.tsv" core/src/test/resources/adult-text-corpus.tsv
"${hisn_java_bin}keytool" -genkeypair -alias hisn-test -keyalg EC -groupname secp256r1 -dname CN=localhost \
    -ext SAN=dns:localhost -validity 2 -storetype PKCS12 -keystore "$hisn_test_output/tls.p12" \
    -storepass hisn-test-only -noprompt > "$hisn_test_output/keytool.log" 2>&1
"${hisn_java_bin}java" -cp "$hisn_test_output" app.hisn.core.TlsDnsTransportTest "$hisn_test_output/tls.p12"
hisn_seed_digest=$(python3 -c 'import json; print(json.load(open("../seed/manifest.json"))["artifacts"]["domains_core.txt"]["sha256"])')
hisn_seed_count=$(python3 -c 'import json; print(json.load(open("../seed/manifest.json"))["core_domain_count"])')
"${hisn_java_bin}java" -cp "$hisn_test_output" app.hisn.core.DomainSeedTest ../seed/domains_core.txt "$hisn_seed_digest" "$hisn_seed_count"
