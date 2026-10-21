# Android implementation and remaining release gates

Updated 2026-10-21. This describes the development work in `android/`, not a
production release or proof that the connected owner's phone is protected.
Mac, iOS/iPadOS and browser code were not changed in this Android implementation.

## End-state objective

Offer voluntary layered protection: verified domain blocking, selected-app and
word restrictions, appropriate visual filtering, focus commitments and accurate
status. Each layer must state its scope and recover from service/permission
loss without displaying false protection. Absolute detection or resistance to
a device owner's reset/recovery controls is not an achievable release claim.

## Implemented in the development preview (latest: 0.4.3)

| Layer | Code now present | Verification boundary |
| --- | --- | --- |
| List authenticity | Signature verification at build; APK-pinned hashes checked at load; 159,762 domain rules plus bounded adult-term assets | Actual domain/term seeds loaded in core tests and Samsung instrumentation |
| Network | IPv4 UDP plus bounded TCP53 at the virtual DNS endpoint, shared bounded workers, authenticated TLS upstream, local blocks and alias checks | Core UDP/TLS/TCP tests and Samsung UDP/TCP route checks pass; large UDP-to-TCP fallback is core-only evidence; physical transition reliability remains pending |
| Route verification | Explicit owner-confirmed virtual-DNS check: blocked `.invalid` then `example.com` over UDP and reused TCP; four queries on success; session-bound results | Loopback validation/cancellation and complete Samsung checks pass; this does not verify all traffic |
| Browser diagnostic (debug only) | Fixed `www.example.org` additive deny, memory-only 120-second lease; separate release no-op; no saved-rule changes | [0.4.3 core/release-hook tests](ANDROID_0.4.3.md) pass; Samsung policy count rose during private navigation and owner confirmed DNS error in Brave; this verifies only that tested path |
| Network changes | Underlying-network callbacks invalidate pooled/in-flight TLS and old route results; old-network errors are fenced; no automatic public probe or VPN restart | Core generation/buffered-response tests and Samsung simulated-change/recheck pass; real Wi-Fi/mobile handover pending |
| Packet diagnostics | Unsupported/malformed/old-network counts, accepted TCP queries, prepared replies and UDP TC signals; no payload retention | UDP/TCP parser and mutation tests pass; counts are diagnostics, not proof that all apps use the virtual endpoint |
| Saved rules | Domain and literal word lists, selected blocked/scanned apps and separate adult-text opt-in; private persistence | Samsung persistence, Unicode rejection/reload and opt-in checks pass |
| App/word intervention | Consent-based accessibility service restricted to chosen apps; home/restriction screen; input/password skips | Existing ordinary/editable/password/long-text/wide-tree/rapid-reopen fixtures and 2026-10-20 normal-content fixtures pass; real-app coverage pending |
| Built-in adult-text checks | Opt-in English/Arabic policy; 4,455 canonical rules from verified seed entries/context plus reviewed local recovery additions; per-leaf local context; custom rules independent | Core actual-seed and Samsung instrumentation tests pass; controlled English/Arabic adult-promotion and benign medical-context intervention fixtures pass |
| Setup guidance | Shared next-step message/action model separates rules, permission, binding, policy loading/error, limited scans and DNS/check states | All 1,280 enum combinations tested; READY requires every selected setup prerequisite, not universal protection |
| Commitment | Backend rejects weaker settings and disabling opted-in adult checks for 7/30/90 days; elapsed/boot clock; new empty commitments rejected | Pure clock tests and isolated Samsung preference tests pass; old empty commitments preserve time and permit added restrictions |
| Removal friction | Optional legacy admin receiver with no administrative policies | Compiles; latest activation/deactivation test pending |
| Visual filtering | Not implemented on Android | No accuracy or pre-display claim |

The 0.3.0 debug APK was installed and opened on the Samsung on 2026-10-19.
The final device runner passed seed loading, Arabic/boundary matching, local DNS
blocking, Unicode persistence and rejection, bounded long-text sampling and
commitment persistence/restriction checks. Earlier runs exposed and led to
fixes for instrumentation context/storage handling. Tests use a generated
preference file separate from `hisn_policy` and clear only that test file.
They do not enable VPN/accessibility/admin or start a real user commitment.
Complete end-to-end VPN routing was still unverified at that stage. After owner accessibility activation,
controlled live intervention fixtures passed; see `ANDROID_LIVE_GUARD_2026-10-19.md`.
The external driver avoids the process restart caused by instrumentation and
restores its temporary fixture additions without changing owner restrictions.

REA inspection of BlockerHero produced independent long-text, traversal-budget,
Unicode persistence and dashboard improvements. See
`REA_BLOCKERHERO_2026-10-19.md` for evidence and limitations.

Follow-up 0.3.1 was installed on the Samsung and passed the full external live
runner, including periodic selected-app checking and stopping after leaving that
app. It adds a tested layer-health model, scroll events and a selected-root probe
on binding. See `ANDROID_0.3.1.md`. That report's empty-rules/DNS-off state is a
historical observation, not the current 0.4.0 test state. Permission loss and
service-stop recovery still need broader tests and can require owner action.

## 0.4.0 verification boundary (2026-10-20)

Core checks and Samsung instrumentation passed. Controlled normal-content,
English/Arabic adult-promotion and benign medical-context fixtures also passed.
The instrumentation uses isolated preferences and does not start a real owner
commitment. The optional adult mode is off by default when upgrading; its
verified loader can fail visibly without deleting or suppressing custom rules.
Built-in negatives apply within local leaf context, never as a whole-screen
exemption, and do not override the user's chosen literal rules.

Initial live VPN route probes passed the synthetic local NXDOMAIN block but
failed the external-resolver step for `example.com`. Investigation
found that socket protection ran before native socket allocation. The transport
now binds locally before protection and connection, with a regression test.
The phone then reported no active underlying internet network. After connectivity
returned, one check failed with resolver EOF, then the next complete check passed
both local NXDOMAIN and the authenticated `example.com` response. Repeat checks
exposed stale pooled TLS connections: the latest build retries one reused
EOF/reset against the same authenticated endpoint, but not fresh errors,
timeouts, certificate failures or bad frames. Regression tests pass; three
consecutive live two-step checks passed on the final installed APK. The session
retains its historical DEGRADED state: a pass does not erase other failures.
Transition reliability and normal browsing still need acceptance tests. This is
route verification, not a complete-protection claim. The check is deliberately explicit
and uses the virtual route rather than a protected diagnostic socket. It does
not prove all apps use Hisn DNS even when it passes.

A small separate read-only text corpus yielded 9/16 adult-text hits and 1/18 benign
false positives. These are fixture-specific counts, not an accuracy estimate or
a representative benchmark. The misses/false positive require further work.
No Android image/video detection, full-traffic/always-on VPN, or undeletable
installation is implemented. The current deliverable remains a tested
development preview, not the requested universal end-state protection.

## 0.4.2 verification boundary (2026-10-20)

The installed version 0.4.2 adds a bounded IPv4 TCP53 DNS endpoint, not a general
TCP or full-traffic tunnel. It supports 16 flows, 4,096-byte DNS frames, split
and pipelined requests, reused connections, MSS/window-limited responses and
bounded retry/expiry. Oversized-for-UDP validated replies produce a TC response.
Core packet/engine/fallback tests, debug/test builds, lint and release-manifest
checks pass. Earlier instrumentation is historical; no fresh instrumentation
run was made because it restarts the active accessibility process.

Samsung native checks passed UDP and reused-TCP local blocks plus authenticated
`example.com` replies, simulated network invalidation/recheck and old-error
fencing. Two pipelined local TCP blocks also passed without internet access.
Controlled normal/editable/password, custom-word/app and English/Arabic adult
fixtures passed; fixture changes were restored without removing owner rules.

Brave displayed an ordinary page, search results and an IETF documentation page
with Hisn running. This short smoke test does not prove all browser requests use
Hisn or evaluate adult-content accuracy. A stale result observed afterward was
explicitly rechecked: UDP and TCP passed again. Historical errors/unsupported
packets correctly leave the session DEGRADED; a route pass does not erase them.
Physical Wi-Fi/mobile handover and large native UDP-to-TCP fallback remain
unverified. See [the complete record](ANDROID_0.4.2.md) for the APK identity.

## Next acceptance gates

The [0.4.1 follow-up](ANDROID_0.4.1.md) adds monitored connection invalidation;
[0.4.2](ANDROID_0.4.2.md) adds bounded TCP DNS. Neither completes real handover,
all-protocol coverage, visual filtering or uninstall resistance.

1. Completed: install both development APKs and run `CoreDeviceTestRunner`.
   Repeat these checks for future builds; keep test preferences isolated from
   real user settings and commitments.
2. Completed: diagnose socket allocation/protection and reused-EOF failures;
   obtain three consecutive complete route checks on 2026-10-20. This does not
   clear other session errors or complete the broader network gate. Check the
   previous VPN state before activation. After owner consent, expand blocked and
   benign DNS fixtures and actual tunnel counters; verify ordinary browsing,
   another VPN, resolver failure, service loss, screen lock, Wi-Fi/mobile
   transitions and IPv6 connectivity. Do not call the limited route full VPN
   protection or enable lockdown for it.
3. After accessibility consent, use a harmless fixture app for blocked-app and
   custom-word tests. Test password/editable descendants, duplicated labels,
   dynamic feeds, split nodes, secure surfaces, unavailable roots, process death
   and normal allowed browsing. Extend the small adult-text corpus, measure
   misses/false positives and test per-leaf context/custom-rule independence in
   real supported layouts. Do not use private messages as a corpus.
4. Exercise a disposable short commitment in the test APK, including restart,
   stronger-rule updates and rejected removals. Never start a real long
   commitment on the owner's phone as a routine test.
5. If the owner activates the optional admin step, observe its normal uninstall
   and deactivation UI without disabling an existing unrelated blocker. Report
   deactivation as available; do not present legacy admin as device-owner mode.
6. Expand the bounded DNS transport/record handling and verify native large-reply
   fallback. Implement an explicit signed list-update strategy. Address
   app-encrypted DNS and direct-IP coverage before
   considering a full traffic/always-on design. Measure CPU, memory, queue drops,
   end-to-end latency, resolver reachability and battery on the phone.
7. Add a separately consented visual layer with reviewed models, bounded capture
   and inference, clear capture-loss status, privacy and model evaluation.
   Android secure surfaces and capture-session stop/restart behavior remain
   coverage limits. Benchmark misses and false positives with denominators.
8. Add tested schedules/usage limits and an explicit distribution/signing/release
   process. Managed-device enrollment is a separate product mode, not an
   automatic upgrade or a reason to reset the owner's phone.

References and build instructions are in `android/README.md`; earlier static
competitor observations remain in `ANDROID_RESEARCH_PLAN.md` and are not copied
competitor implementations or evidence of Hisn's runtime quality.
