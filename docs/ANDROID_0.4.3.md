# Hisn Android 0.4.3 — private-browser diagnostic

2026-10-21 · version code 8 · development-signed APK installed on the authorized
Samsung SM-G990B2 / Android 16. Android-only change; no new Apple installer or
browser-extension release is part of this update.

## Implemented

A debug-only controller temporarily adds a deny of exactly `www.example.org`.
The fixed two-minute lease is in memory, uses the monotonic clock, expires on
evaluation/status, and clears on DNS session close. Rollback or unavailable
clock expires the extra deny. Other hosts always use the existing policy;
expiry/clear returns the fixed host to that same policy, including any owner
block. There is no arbitrary-host parameter, network request, persistent rule
write, commitment change or settings bypass in the lease.

The hook is shared by UDP and TCP DNS policy processing, including reply-alias
checks. Its counter counts matching policy evaluations, not unique queries,
delivered answers, detected adult content or the identity of a browser. Repeated
policy checks can increase it more than once per DNS message. Only a fresh arm
resets it; clear/expiry retains this small in-memory diagnostic count.

Debug and release hooks are separate build-variant sources. The release hook
always delegates unchanged, and debug fixture entry points remain shell-only
and excluded from the release manifest. No system permission is granted and no
browser privacy/security setting is modified. A test controller refuses to
arm without explicit acknowledgement of the fixed harmless test domain, and
clears stale fixture output before each command.

## Verification

Core tests, debug and test APK builds, lint, release Java compilation and
release-manifest generation passed. Lint reports zero errors and seven warnings.
The release manifest contains neither debug fixture activity nor its `DUMP`
permission. Device instrumentation was not rerun because it restarts the active
accessibility process; earlier instrumentation evidence is historical.

Canary tests cover inactive/active exact-host behavior, case/trailing-dot
handling, unrelated host and owner-rule preservation, exact deadline, clear,
rearm, clock failure/rollback/overflow, immutable snapshots and concurrent
evaluations. DNS-message integration tests cover A/AAAA/HTTPS local NXDOMAIN,
question/ID preservation, zero upstream calls on a canary deny, and existing
owner policy after clear/expiry. A separate test compiles the actual release
hook and verifies that arm/clear never override owner ALLOW/BLOCK/INVALID
decisions. Existing domain/TCP/TLS/packet/seed/text/commitment suites passed.

The Samsung external DNS runner passed local synthetic block and authenticated
`example.com` resolution over UDP and one reused TCP connection. Simulated
network invalidation cleared the old result; a new complete check passed;
simulated old-network failure did not overwrite it. These are route checks and
simulations, not a physical Wi-Fi/mobile handover or full-device coverage test.

Two pipelined synthetic local blocks also passed over a native Android TCP
connection without public resolver access. The full controlled app/text runner
passed ordinary/editable/password controls, long/wide custom-word labels,
selected-app blocking and rapid-reopen enforcement; periodic checks stopped
outside the selected fixture. Its temporary additions were restored while
preserving owner rules. These fixtures do not establish general app coverage.
The separate adult-mode runner passed English/Arabic adult-promotion controls
and the benign medical control, then restored its additions. The saved-rule
fingerprint still matched after both live runners completed.

## Private-browser observation

Brave's declared `OPEN_PRIVATE_TAB` action opened a fresh private tab. Its
private-tab screenshots are protected; no capture protection was disabled.
With Brave foreground, the standard address-bar shortcut was used to navigate
to `https://www.example.org/` during the active lease. The fixed-host policy
counter rose from 0 to 60 evaluations during the attempt. The owner separately
confirmed that the new private tab showed a DNS/site-unreachable error.

**The specific private-browser blocking test passed:** matching Hisn policy
activity and the owner-observed DNS failure corroborate the tested navigation.
No adult page was opened. This is not an accuracy benchmark or proof that all
browser requests, future private sessions or independent encrypted DNS paths
are covered. No private-page screenshot was obtained or inferred.

A normal-page screenshot attempt in this update captured Android VPN settings,
not the page; it is not counted as a new ordinary-browsing pass. Earlier Brave
smoke-test evidence is in 0.4.2. Only owner-authorized test navigation was used;
no unrelated private tabs, accounts or history were inspected.

The saved-rule fingerprint (domain/word/app selections and adult-text opt-in)
was identical before and after installation/testing. No real commitment was
started. The installed APK version is 0.4.3 / code 8. Session degradation from
unsupported packets remains visible even when the separate route check passes.
The canary was explicitly cleared afterward: active false, remaining time zero,
with its 60 evaluation count retained only in memory until rearm/session close.

## Limits and next gates

The diagnostic does not add full-traffic filtering, Android visual/OCR
detection, schedules, usage limits or uninstall prevention. Independent secure
DNS, cached DNS, direct IP and other profiles remain coverage gaps. Private
mode is not itself a bypass fix or evidence of cache isolation. Negative caches
may retain a DNS error after the temporary deny expires. Preserve existing
browser privacy/security controls; do not weaken them to obtain a test pass.

Physical network transitions, longer browsing and lifecycle/battery acceptance
tests remain pending. See [0.4.2](ANDROID_0.4.2.md) for the existing bounded TCP
implementation and limits.

APK SHA-256:
`5d46498a4adbdd0d85a1bea11444d9ecac4a1b709d04b65b5c8953ff8d1bc335`.
