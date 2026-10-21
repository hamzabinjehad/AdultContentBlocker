# Hisn Android 0.4.3: protection development preview

The Android app contains an opt-in DNS-only VPN, saved custom rules, selected-app
accessibility restrictions, opt-in built-in adult-text checks, an in-app
commitment and an optional legacy-admin confirmation step. The interface supports
Arabic and English. This remains a development build, not the completed end goal.
Core tests and controlled accessibility fixtures pass on 0.4.3. Samsung
instrumentation passed on the earlier 0.4.0 build; it was not rerun for 0.4.3.
The current live VPN check passed local-block and external-resolver steps over
both UDP and reused TCP. On 0.4.2, Brave displayed an ordinary page, search results and
an IETF documentation page while the VPN was active. These are limited smoke
tests, not proof that every browser request uses Hisn. Broader real-app coverage
and VPN/lifecycle testing remain pending. Visual filtering, schedules,
usage limits, full traffic coverage and managed-device uninstall blocking are
not implemented.

## 0.4.3 private-browser diagnostic

Debug builds can arm a fixed, additive deny of exactly `www.example.org` for
120 seconds to check a real browser path without adult pages. The lease is
memory-only, uses elapsed time, expires during policy evaluation/status checks,
and is cleared with its DNS session. It never edits saved rules or commitments.
Clearing or expiring it always returns to the owner's existing policy. No
browser settings, system permissions, DNS fallback or rule-removal controls are
introduced. Release builds use a separate no-op hook and cannot arm this lease.

The shell-only debug controller is `test-live-browser-canary.py`:

```sh
python3 test-live-browser-canary.py --serial SERIAL --action start --confirm-harmless-example-domain
python3 test-live-browser-canary.py --serial SERIAL --action status
python3 test-live-browser-canary.py --serial SERIAL --action clear
```

It requires an already-approved running Hisn DNS session. Open a fresh private
tab manually, then navigate to `https://www.example.org/` during the lease.
Do not change secure-DNS or private-tab capture settings for this test. A live
browser pass requires both an observed DNS error and an increase in the
fixed-host evaluation counter during that navigation. The counter represents
policy evaluations, not unique requests, replies delivered or browser identity.
No hits is inconclusive: private mode is not proof of cache isolation, and
independent DNS can bypass the virtual endpoint. An error alone is not proof.
Brave's protected private-tab screenshots remain protected; ask the owner to
confirm visible behavior rather than disable privacy controls. DNS/browser
caches may retain an error after the two-minute rule expires.

On 2026-10-21 the Samsung test recorded matching policy evaluations and the
owner confirmed a DNS/site-unreachable error in the new Brave private tab.
This verifies the tested path, not every browser request or bypass.
See [0.4.3 verification](../docs/ANDROID_0.4.3.md) for details and limits.

## 0.4.2 bounded TCP DNS update

The virtual IPv4 DNS address now accepts TCP port 53, alongside UDP. A small,
bounded endpoint handles connection setup, DNS length framing, split and
pipelined requests, connection reuse, duplicate/overlapping packets, partial
acknowledgements, peer MSS/window limits, retransmission and half-close. It is
not a general TCP stack. Limits are 16 flows, 4,096-byte DNS frames, an 8,192-byte
receive buffer and four queued requests plus one active per flow. Fixed partial
frame/request deadlines, idle expiry and bounded retransmission prevent
unbounded retained work. There is no out-of-order buffering or negotiation of
Fast Open, SACK, timestamps or window scaling.

Both protocols use the existing local domain policy, validated DNS replies and
authenticated TLS upstream. The four-worker/64-request queue is shared; overload
is explicit. Validated replies too large for the UDP client's advertised budget
produce a TC retry signal, not a resource record cut in half. The limit is 512
bytes without EDNS and at most 1,232 with EDNS. The TCP path remains capped at
4,096 bytes. The dashboard separates accepted TCP queries, prepared replies
(not confirmed acknowledgements) and UDP retry signals.

The explicit route check now sends four fixed queries when successful: local
synthetic block plus `example.com` over UDP, then the same pair over one reused
TCP connection. Samsung checks passed, including simulated network invalidation
and recovery, and a native local test of two pipelined blocks. A full 4,096-byte
UDP-TC-to-TCP fallback passes in core tests; it has not been reproduced as a
large native/browser reply. See [0.4.2 verification](../docs/ANDROID_0.4.2.md).

## 0.4.1 network recovery update

The active DNS session observes `INTERNET + NOT_VPN` network callbacks using
Android's normal `ACCESS_NETWORK_STATE` permission. It unregisters when the
session closes. New/lost networks, changed link properties and validation/captive
state changes invalidate resolver connections and the previous route-check
result. Unchanged callbacks and signal/bandwidth churn do not cause resets.
This is conservative monitoring of matching underlying networks, not proof of
which physical network carried every query. No Wi-Fi/mobile setting is changed.

Invalidation cancels old in-flight TLS exchanges, rejects buffered old-generation
TLS replies and reconnects later requests to the same authenticated resolver. It does
not restart a revoked VPN, introduce a fallback resolver or automatically send
public test queries. Old-network failures cannot overwrite new-network health;
a current request failure during a check prevents its result being published as
a current pass. The dashboard distinguishes unsupported TCP/other packets,
malformed packets and cancelled old-network requests without keeping payloads.
Unsupported traffic still means incomplete coverage. In 0.4.1 these diagnostics
did not implement TCP DNS; 0.4.2 adds only the bounded port-53 endpoint described
above, not full traffic filtering.
Network invalidation is not an atomic withdrawal of answers already accepted
by the endpoint: a callback can race final publication, and pending TCP segments
can still be delivered. This is not a physical-network provenance guarantee.

Core tests and the live simulated-invalidation/recheck on Samsung pass. The
simulation does not toggle Wi-Fi or prove real Wi-Fi/mobile handover. See
[0.4.1 verification](../docs/ANDROID_0.4.1.md). Earlier instrumentation evidence
is recorded separately for 0.4.0; it was not rerun after this update.

## Implemented

- A build step verifies the existing signed seed bundle with the repository's
  pinned public key before packaging `domains_core.txt` and `terms.json`. Python 3 and the
  repository's existing blocklist dependencies are required for this step.
- The APK pins the verified manifest's domain digest and entry count. Runtime
  loading checks SHA-256, UTF-8, count, duplicates and domain validity. This is
  build-time signature verification plus runtime digest verification, not a
  runtime signed-update client. No downloaded list updates are implemented.
- The current seed loads 159,762 domain rules. Six IPv4 entries in the shared
  seed are explicitly excluded because a domain-only policy cannot enforce them.
- The opt-in adult-text loader pins the terms seed's SHA-256, checks its version,
  UTF-8, size and rule bounds, and compiles English/Arabic terms and negative
  context on this device. Together with three reviewed local recovery-context
  additions, the current policy contains 4,455 canonical rules. The additions
  are app code, not extra signed-seed entries. Loader failure is visible and
  does not disable independent custom literal rules.
- A consent screen describes resolver privacy, other-VPN conflicts and limits.
  Android's VPN approval is requested after this screen. Nothing starts on app
  launch or reboot. Starting the service can replace another active VPN.
- A foreground service routes only `10.111.0.2/32` into the local TUN interface
  and advertises that address as the DNS server. Normal internet traffic and
  IPv6 traffic use the underlying network. This is not a full traffic tunnel.
- IPv4 UDP and TCP DNS packets are checked for sizes, checksums, destination,
  fragmentation and supported header/options structure. TCP terminates only on
  the virtual DNS address's port 53. Four workers and a shared bounded 64-request
  queue limit resolver concurrency.
  No screen content or browsing log is stored.
- Domain matches return local NXDOMAIN without contacting the upstream server.
  Allowed queries go to Cloudflare Families at `1.1.1.3:853` with authenticated
  DNS-over-TLS, certificate and hostname validation, and TLS 1.2/1.3 only.
  Each worker reuses its connection. There is no plaintext or alternate-resolver
  fallback. The resolver can see allowed domain requests. Saved domain rules
  are applied immediately alongside the bundled seed.
- Upstream sockets must pass `VpnService.protect`. Transaction IDs are randomized
  upstream and restored for clients; mismatched questions, IDs, oversized and
  truncated replies are rejected. A bounded reply parser checks record lengths,
  compressed names and reachable CNAME/DNAME/HTTPS/SVCB aliases against the
  policy. Unrelated aliases do not block a query. This is not DNSSEC validation,
  nor full semantic validation of every resource-record type.
- The app shows connection state, loaded-domain count and session-only blocked,
  forwarded and error counters. A connected tunnel does not prove full protection.
  Errors keep the status degraded until the session ends.
- After a separate disclosure and owner confirmation, the DNS route check sends
  a synthetic `blocked.hisn-self-check.invalid` query through the virtual DNS
  route, then `example.com` only if the local NXDOMAIN check passes, first over
  UDP and then over one reused TCP connection (four queries on full success).
  Public queries use the existing authenticated resolver path; the probe itself is not
  protected from the VPN and has no fallback endpoint. A passed check describes
  this route only, not all apps, encrypted content or all device traffic. A
  stopped/restarted DNS session clears the previous result. The latest Samsung
  0.4.0 runs passed the local step but initially failed resolver socket protection.
  The transport now binds its socket locally before protecting it and only then
  connects; a regression test checks this ordering. The phone subsequently
  reported no active underlying internet network. Once connectivity returned,
  checks exposed reused TLS connections closed by the server. A pooled EOF/reset
  now gets one reconnect to the same authenticated endpoint; fresh errors,
  timeouts, identity failures and invalid frames do not. Tests cover stale
  recovery and no fresh-error retry. Three consecutive live checks then passed
  on the 0.4.0 APK. Broader network changes remain a release gate. The
  historical DEGRADED session state is not cleared by a passing check. No
  plaintext fallback was added.
- Supported queries receive SERVFAIL if the resolver or reply validation fails;
  unsupported packets are counted and dropped. This avoids silently switching
  to an unfiltered resolver, but can interrupt page loading.
- Rules persist in private app preferences. During an active 7-, 30- or 90-day
  in-app commitment, the settings backend rejects removal of saved restrictions
  and reductions in the remaining period. Disabling opted-in built-in adult-text
  checks is also rejected. Stronger settings are allowed. A new commitment is
  rejected until meaningful restrictions are configured; old empty commitments
  still load without erasing their remaining period and can receive added rules. The
  clock uses elapsed time and Android's boot counter, not the editable date.
  Progress is checkpointed on app pause and periodically during a DNS session;
  reboot or power-off can extend the period. Android settings, data clearing
  and uninstall remain outside this in-app lock.
- The accessibility service is off until the owner enables it. It acts only in
  explicitly selected apps. Chosen blocked apps, matching literal word rules or
  built-in adult-promotion evidence in the optional mode
  return to the launcher and show a generic restriction screen. Settings,
  launchers and dialers are excluded. Traversal is bounded and skips editable
  and password nodes and their descendants. Only leaf labels are read to avoid
  duplicated parent input values. Sensitive content may still be exposed by
  other labels; the consent screen discloses this. Screen text is not saved.
- Built-in adult-text checks use bounded local context within individual visible
  leaf labels, rather than exempting a whole screen because of unrelated medical
  or news wording. Custom rules are checked independently first; built-in benign
  context never overrides a chosen literal rule. Opt-in is off by default for
  existing installations and enabling it does not grant any Android permission
  or choose apps automatically.
- Long visible labels are sampled rather than skipped above 2,048 characters.
  A global 512-node/child-attempt budget and 32,768-character sample bound work;
  truncated tokens cannot manufacture whole-word matches. The dashboard reports
  incomplete inspections and configured accessibility layers independently of DNS.
- Normalized Unicode words and IDN domains must fit persistence limits before
  saving. Rejected updates preserve existing settings and remain reloadable.
- While a selected word-scanned app remains foreground, bounded periodic
  inspections run about once per second. They stop after leaving the selected
  app and on service interruption. Scroll events also trigger checks. On
  binding, the service probes current-window metadata for selected-app recovery.
- Layer health distinguishes permission enabled from service connected, as well
  as no saved restrictions, partial inspection and unreadable settings. A live
  service alone is never presented as configured enforcement.
- A shared setup guide supplies both the next-step message and its action.
  Its 1,280 tested state combinations distinguish rules, permission, binding,
  loading/error states, incomplete app inspections, DNS startup/degradation and
  the explicit route check. READY requires configured app protection, healthy
  service state, a ready or disabled optional policy, a running DNS layer and a
  passed check. It still does not mean complete device protection.
- Selected-window root changes receive bounded retries. Warning cooldown never
  suppresses a fresh enforcement action. The explanation screen is delayed until
  after Home to avoid the observed Samsung transition race.
- The optional legacy device-admin receiver declares an empty policy set:
  no wipe, password or device-lock capabilities. Android may require admin
  deactivation before uninstalling. The receiver can show a reminder, but it
  cannot stop deactivation, make the app undeletable, or establish device-owner
  management. Hisn never uses accessibility to trap the Android uninstall UI.

## Known coverage gaps

Apps' independent encrypted DNS, direct-IP connections, cached DNS, alternative resolvers, secure
browser DNS, other profiles and mixed-content websites can bypass this layer.
The virtual DNS server handles IPv4 UDP and bounded TCP port 53 only, not
client TLS port 853 or TCP to arbitrary destinations. Fragmented IP, compressed
questions and some non-hostname queries are unsupported; replies above the
4,096-byte cap, unsupported queries and resolver outages may cause page loads to fail. The
service reports errors rather than claiming those requests were filtered.

Always-on support is explicitly disabled. Do not enable lockdown for this
DNS-only route: it would prevent normal internet traffic from using the
underlying network. The service does not automatically restart after boot,
process death or permission loss. Android VPN settings can disconnect it.

The local tester accepts one literal rule per line with bounded rule and text
sizes. Tester inputs/results are not saved; rules entered in the separate saved
rule editor are saved. This tester checks custom literal rules, not the optional
built-in classifier. Whole-word matching avoids `sex` inside `Essex`; ambiguous
chosen rules can still match benign content. The built-in policy is a conservative
adult-promotion heuristic, not an unrestricted semantic classifier. It can miss
adult text without the required evidence or misclassify harmless text. Cross-node
phrases, hidden/secure text, images,
video and all-app coverage are not guaranteed. Text beyond traversal limits
may not be inspected, and intervention happens after an accessibility event.

## Build and test

Use JDK 17+, Android SDK platform 37 and build tools 36.0.0. The wrapper pins
Gradle 9.3.1. Set `JAVA_HOME` and `ANDROID_HOME`, then run:

```sh
sh test-core.sh
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest lintDebug
```

The core suite covers word boundaries, Unicode, bounded lists, seed tampering,
the actual full seed, domain exceptions, DNS/EDNS packets, packet checksums,
fragment rejection, malformed inputs, resolver timeouts and local UDP exchanges.
TCP tests cover framing, pipelining/reuse, sequence wrap, partial/future ACKs,
overlap/duplicates, peer MSS/window limits, retransmission, FIN/RST, queue/flow
caps, deadlines and late completions. Packet mutation/fuzz tests and a validated
4,096-byte UDP-TC-to-segmented-TCP fixture run without public network access.
The suite also checks all 1,280 setup-guide combinations, actual-seed adult-text
fixtures, local context, Unicode/evasion boundaries and bounded classifier work.
DNS-probe tests use loopback responders for route-check response validation,
timeouts, cancellation and no public query after a failed local check.
TLS tests generate a disposable localhost certificate, verify hostname and
trust failures, and test encrypted exchanges and connection reuse. Commitment
tests cover expiry, clock rollback, boot changes and checkpoints. The standard
suite uses loopback only. `ResolverProbe --public-example-only` is a separate,
explicit optional diagnostic which contacts Cloudflare for `example.com` only.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The app supports
Android 8.0+. A debug build is signed with a development key, not a production
release certificate.

The optional test APK uses a custom instrumentation runner. After installing
the app and test APK, run:

```sh
adb shell am instrument --user 0 -w app.hisn.android.test/app.hisn.android.CoreDeviceTestRunner
```

This test checks the seeds, Arabic rules, word boundaries, a local DNS block,
saved settings, built-in opt-in and commitment checks in isolated test preferences without
enabling services. Actual VPN routing, accessibility interventions, admin
deactivation behavior and lifecycle need separate controlled device tests after
approving the corresponding consent screens. On 2026-10-20 the device runner
passed on the Samsung with 159,762 bundled domains, Arabic/boundary matching,
local DNS blocks, Unicode persistence/rejection, long-text sampling and
commitment checks, verified adult-term loading, custom-rule independence,
empty-commitment rejection and opt-in persistence/commitment gating. Generated
test preferences are separate from `hisn_policy`
and cleared after testing; real user rules and commitments are untouched.

See [the REA review](../docs/REA_BLOCKERHERO_2026-10-19.md) for competitor
observations, evidence identifiers, independent improvements and remaining gates.
See [live Samsung guard tests](../docs/ANDROID_LIVE_GUARD_2026-10-19.md) for
verified fixture results and the opt-in external test driver. Instrumentation
restarts Hisn and can interrupt accessibility; use the external driver for live
service tests instead. Its shell-only setup activity is excluded from release.
The historical [0.3.1 verification](../docs/ANDROID_0.3.1.md) includes the successful periodic
foreground-check and stop-outside-selected-app tests, installed APK identity and
the visually checked Arabic dashboard. Follow-up controlled normal-content,
English/Arabic adult-promotion and benign medical-context fixtures passed on
2026-10-20. These are fixture checks, not proof of general app coverage.

A separate small, read-only text corpus produced 9/16 adult-text hits and 1/18
benign false positives. Its misses and false positive remain release-quality
work: these denominators describe only those fixtures, not general detection
accuracy. The current UDP/TCP VPN-path check and short Brave browsing smoke test
pass, but physical network-transition reliability, longer browsing sessions and
broader browser coverage still need acceptance testing. Passing core,
instrumentation, accessibility or route checks is not complete protection.

See the [0.4.0 verification record](../docs/ANDROID_0.4.0.md) for the installed
APK identity, socket-ordering correction and connected-network result.
See [0.4.2](../docs/ANDROID_0.4.2.md) for the current APK, TCP evidence and remaining
coverage limits. `test-live-tcp-local.py --serial SERIAL` performs only two
synthetic local blocks on the already-approved running VPN; it does not test
public resolver access or grant permissions.

References: [Android VPN](https://developer.android.com/develop/connectivity/vpn),
[VPN routes and address families](https://developer.android.com/reference/android/net/VpnService.Builder),
[foreground service types](https://developer.android.com/about/versions/14/changes/fgs-types-required),
[Cloudflare Families](https://developers.cloudflare.com/1.1.1.1/setup/#1111-for-families).
