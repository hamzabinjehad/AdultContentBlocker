# Hisn Android 0.4.2 — bounded TCP DNS

2026-10-20 · version code 7 · development-signed APK installed on the authorized
Samsung SM-G990B2 / Android 16. This update changes Android only; it does not
install new Apple apps or browser extensions.

## What changed

- The DNS-only VPN now terminates IPv4 TCP port 53 on its virtual DNS address,
  alongside UDP. Both use the same domain policy, validated DNS replies and
  protected, authenticated Cloudflare Families DNS-over-TLS upstream. There is
  no alternate resolver, plaintext fallback or browsing log.
- A bounded endpoint implements setup/acknowledgements, two-byte DNS framing,
  split/pipelined requests, reuse, overlap/duplicate handling, partial ACKs,
  sequence wrap, peer MSS/window limits, retransmission and FIN/RST handling.
  Limits: 16 flows, 4,096-byte DNS frames, an 8,192-byte receive buffer and four
  queued requests plus one active per flow. Resolver work is sequential within
  each flow and shares the existing four-worker/64-request queue.
- Fixed partial-frame and request deadlines, idle expiry, bounded retries and
  flow-identity checks limit retained work and reject obsolete completions.
  No IP fragments, out-of-order buffering, TCP Fast Open or negotiated window
  scaling, timestamps or SACK are supported. This is not a general TCP stack or
  a claim of complete TCP standards conformance.
- Validated replies exceeding the client's UDP limit generate a TC retry
  response with a complete question and normalized OPT if applicable. Limits
  are 512 bytes without EDNS and up to 1,232 with EDNS. Resource records are
  never arbitrarily cut. TCP answers remain capped at 4,096 bytes.
- Shared queue rejection produces an explicit failure instead of orphaned
  work. Session/network generations continue to fence late upstream results.
- The owner-confirmed route check sends synthetic local-block and `example.com`
  queries over UDP, then over one reused TCP connection: four fixed queries on
  full success. Both English and Arabic disclosures describe this. No adult
  site is requested. No checks run automatically on network changes.
- Dashboard counters distinguish accepted TCP queries, prepared replies (not
  confirmed client acknowledgements) and UDP TC retry signals. A passing check
  does not clear historical request errors or unsupported-packet degradation.

## Verification completed

The core suites, debug/test APK builds, lint and release-manifest generation
pass. Lint reports zero errors and seven warnings. Debug fixture activities and
their shell-only `DUMP` permission are absent from the release manifest. No
fresh device instrumentation was run: it restarts the active accessibility
process. Earlier isolated instrumentation evidence remains in the 0.4.0 record.

New core coverage includes packet ownership/checksums/options and fuzz/mutation
inputs, handshake/repeated SYN, split frames, pipeline/reuse, duplicate/overlap,
partial/future ACK, sequence wrap, MSS/zero-window behavior, retransmission,
FIN/RST, caps/deadlines and late completions. A validated 4,096-byte TXT response
passes the full UDP TC signal to segmented TCP fallback fixture with a small
peer MSS. These are local deterministic tests, not native/browser large-reply
evidence or a formal complete TCP conformance suite.

On Samsung, the external drivers passed:

1. Synthetic block and authenticated `example.com` resolution over UDP and one
   reused TCP connection through the actual TUN route.
2. Simulated network invalidation clears the old pass; a new full check passes;
   a simulated old-network error does not overwrite the fresh result.
3. Two pipelined synthetic blocks on one native Android TCP connection, from
   Hisn's debug fixture process, without contacting a public resolver.
4. Controlled ordinary/editable/password controls remain visible; custom words,
   long/wide labels, selected-app and rapid-reopen fixtures are restricted;
   periodic checks stop outside the selected fixture. English/Arabic adult
   promotion fixtures are restricted; the benign medical control remains open.
   Temporary fixture additions are removed, preserving owner rules. No real
   commitment is started and no system permission is granted by the drivers.

## Brave smoke test

With owner authorization, Brave on the Samsung displayed `example.com`, a Brave
Search query for Android DNS-over-TCP/RFC 7766, and the RFC Editor's RFC 7766 page
while Hisn's VPN was active. No adult page, private account or unrelated tab was
inspected; browser security/account settings were not changed.

This demonstrates a short ordinary-browsing flow, not universal filtering or
proof that each request used Hisn (caches and independent secure DNS can bypass
the virtual endpoint). A later status check showed a stale route result and
historical timeout/unsupported counters; rerunning the explicit check passed
both UDP and TCP. The DEGRADED session state was retained, not relabelled as
complete protection. No native large-reply fallback or physical radio handover
was tested. Earlier raw shell-netcat experiments did not produce a valid result;
only the subsequent native in-app TCP fixture is counted as a pass.

## Remaining limits

TCP support is only to the virtual IPv4 DNS address's port 53, not arbitrary
TCP, client TLS853, app-controlled encrypted DNS or direct-IP traffic. The
VPN still routes only `10.111.0.2/32`; ordinary traffic and IPv6 use the underlying
network. It is not full-traffic, always-on or lockdown compatible. Fragmented,
unsupported or oversized queries/answers and resolver failures can interrupt
loading. Android settings can disconnect the VPN; service/permission loss,
reboot, app removal and data clearing are not prevented.

Physical Wi-Fi/mobile/captive/offline transitions, longer browsing, process
lifecycle, battery and memory/performance measurements remain release gates.
Network invalidation is not an atomic withdrawal of answers already accepted
by the endpoint: a callback can race final publication, and pending TCP segments
can still be delivered. This is not a physical-network provenance guarantee.
Android visual filtering, OCR, schedules, usage limits and managed-device
uninstall prevention are not implemented. This is a tested development preview,
not the requested absolute or undeletable end-state product.

APK SHA-256:
`b1d1ade44b679fedd752cabaac81979b1e56d21fce0b2af0bec1cb6f0ab4a5f6`.

Implementation references: [DNS over TCP, RFC 7766](https://www.rfc-editor.org/rfc/rfc7766.html),
[TCP, RFC 9293](https://www.rfc-editor.org/rfc/rfc9293.html).
No competitor source code was copied into this endpoint.
