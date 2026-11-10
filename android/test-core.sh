#!/bin/sh
set -eu
# Resolve the script before changing directory: the orphan-test guard below must
# be able to read this file when the suite is invoked as `sh android/test-core.sh`.
hisn_script=$(cd "$(dirname "$0")" && pwd)/$(basename "$0")
cd "$(dirname "$0")"
# English and Arabic resources: same keys, plurals and placeholders; every referenced key exists.
python3 check-strings.py
# Every bundled list source has its license text and an English and Arabic summary entry.
python3 check-notices.py
# The Play/direct split is enforceable before an APK is uploaded.
python3 test_check_release.py
hisn_test_output=$(mktemp -d "${TMPDIR:-/tmp}/hisn-core.XXXXXX")
# Test class files stay in a newly created temporary directory; no repo files are changed.
hisn_java_bin=${JAVA_HOME:+$JAVA_HOME/bin/}
"${hisn_java_bin}javac" -d "$hisn_test_output" core/src/main/java/app/hisn/core/*.java core/src/test/java/app/hisn/core/*.java
for hisn_test in TextFolding AdultTldPolicy WordMatcher VisibleTextSample LabelScan GuardWindows VisualCadence VisualRetry AccessibilityGrant InterventionRepeat GuardHealth SetupGuide ProtectionReadiness PolicyVersion PolicyIdentity PolicyReceipt EvidenceAge DnsCheck HostDecision SiteCheckInput Ed25519 SignedManifest RulePreview RuleDocument DomainPolicy DistractionSites SocialApps BrowserCanary BrowserCoverage DnsPolicy DnsUdpTruncation DnsExchange DnsTunnelProbe DnsTunnelTcpProbe DnsReplyGuard Ipv4DnsPacket Ipv4TcpPacket DnsTcpServer DnsTcpFallback DnsPacketKind EncryptedDnsProviders EncryptedDnsResolvers DnsHealth DnsNotice PolicyChangeError RemainingTime CommitmentClock DailyAppSchedule AppTimeLimit PauseTally ShortVideoSurfaces UninstallProtection ProtectionLock ManagedRestrictions AlwaysOnVpn BlockedAppSuspension GuardianPairing GuardianPolicy SafeSearch DnsPolicyRewrite BrowserAllowlist DomainListHash LayerWatch SeedFreshness SelfProtectionScreens VisualFrameGate VisualExecutionPolicy InspectionHealth VisualHealth VisualVerdictCache VisualRuntimeDeadline VisualDuty ResolverFailover UnderlyingNetwork SettingsIntegrity SavedSettings; do
    "${hisn_java_bin}java" -cp "$hisn_test_output" "app.hisn.core.${hisn_test}Test"
done
# A test class that is not named above would compile and never run. Every file in the test
# directory must be reachable from this script, whether in the loop or as a fixture test.
for hisn_test_file in core/src/test/java/app/hisn/core/*Test.java; do
    hisn_test_name=$(basename "$hisn_test_file" .java)
    if ! grep -q "${hisn_test_name%Test}" "$hisn_script"; then
        echo "test class not run by this script: $hisn_test_name" >&2
        exit 1
    fi
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
# weight<TAB>language<TAB>phrase, the same subset AdultPolicyAssets loads (English, Arabic,
# Franco-Arabic "ar-latn" and the negative context).
python3 - <<'PY' > "$hisn_test_output/adult-terms.tsv"
import json
data = json.load(open('../seed/terms.json'))
rules = [r for r in data['terms'] if r['l'] in ('en', 'ar', 'ar-latn')] + data['negatives']
for rule in rules:
    assert not any(c in rule['t'] for c in '\r\n\t')
    print(str(int(rule['w'])) + '\t' + rule.get('l', 'neg') + '\t' + rule['t'])
PY
"${hisn_java_bin}java" -cp "$hisn_test_output" app.hisn.core.AdultTextPolicyTest "$hisn_test_output/adult-terms.tsv"
# Hostname keyword fixture: the same signed terms, exemptions and shared cases the Mac and Chrome use.
python3 - <<'PY' > "$hisn_test_output/host-policy.tsv"
import json
seed = json.load(open('../seed/terms.json'))
for entry in seed['host_terms']:
    assert not any(c in entry['t'] for c in '\r\n\t')
    print(entry['kind'] + '\t' + entry['t'])
for term in seed['never_keyword']:
    print('never\t' + term)
for host in seed.get('keyword_exempt_hosts', []):
    print('exempt\t' + host)
for case in json.load(open('../blocklist/terms/host_cases.json'))['cases']:
    print('case\t' + ('1' if case['block'] else '0') + '\t' + case['host'])
PY
"${hisn_java_bin}java" -cp "$hisn_test_output" app.hisn.core.HostTermPolicyTest "$hisn_test_output/host-policy.tsv"
"${hisn_java_bin}java" -cp "$hisn_test_output" app.hisn.core.AdultTextCorpusTest "$hisn_test_output/adult-terms.tsv" core/src/test/resources/adult-text-corpus.tsv
"${hisn_java_bin}keytool" -genkeypair -alias hisn-test -keyalg EC -groupname secp256r1 -dname CN=localhost \
    -ext SAN=dns:localhost -validity 2 -storetype PKCS12 -keystore "$hisn_test_output/tls.p12" \
    -storepass hisn-test-only -noprompt > "$hisn_test_output/keytool.log" 2>&1
"${hisn_java_bin}java" -cp "$hisn_test_output" app.hisn.core.TlsDnsTransportTest "$hisn_test_output/tls.p12"
hisn_seed_digest=$(python3 -c 'import json; print(json.load(open("../seed/manifest.json"))["artifacts"]["domains_core.txt"]["sha256"])')
hisn_seed_count=$(python3 -c 'import json; print(json.load(open("../seed/manifest.json"))["core_domain_count"])')
"${hisn_java_bin}java" -cp "$hisn_test_output" app.hisn.core.DomainSeedTest ../seed/domains_core.txt "$hisn_seed_digest" "$hisn_seed_count"
