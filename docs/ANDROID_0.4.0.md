# Hisn Android 0.4.0 — guided setup and adult-text checks

Verified 2026-10-20 on the owner-authorized Samsung SM-G990B2 / Android 16.
Version code 5. This is a development preview, not universal protection.

## Delivered

- A shared setup model drives the dashboard message and next action. It separates
  missing rules, permission, disconnected service, incomplete inspection,
  policy loading/failure, DNS health and route-check results. All 1,280 state
  combinations are tested; a connected service alone never means protection.
- Optional English/Arabic adult-promotion text checks load 4,455 canonical rules
  from the verified bundled vocabulary. Build-time signature verification and
  runtime pinned SHA-256/schema/version checks protect the packaged asset.
  Custom literal rules remain independent and work if the optional loader fails.
- Context checks run per visible accessibility leaf, not across a whole screen.
  Local medical/recovery/news context can suppress built-in evidence, never the
  owner's literal rules. Input/password subtrees are skipped. Nothing is sent
  to a classifier server or retained as a screen-text log.
- Upgrades leave the new built-in mode off until chosen. An active commitment
  cannot disable a previously enabled mode or remove restrictions. New empty
  commitments are rejected; legacy commitments retain their original period.
- An explicit two-step DNS route check tests a locally blocked `.invalid` name,
  then `example.com` over the existing authenticated upstream. The unprotected
  probe socket enters the TUN, and the current session must observe its local
  request. Stopped sessions cannot publish a stale pass. This does not verify
  every app's DNS settings or all traffic.
- Resolver sockets are bound locally before `VpnService.protect`, which precedes
  connection. This fixes the observed `PROTECT` failure on Samsung. TLS identity
  validation and the no-plaintext/no-alternate-resolver policy are unchanged.
  Diagnostics expose fixed failure categories and counters, not query contents.
- Reusing a server-closed TLS connection gets at most one reconnect to that same
  authenticated resolver, only for a reused EOF/socket-reset failure. A fresh
  failure, timeout, TLS identity failure or invalid frame is not retried. Tests
  check stale recovery and rejection of a fresh-failure retry.

## Verification

- Core suites pass: actual domain/vocabulary assets, Unicode and boundaries,
  48 layer-health states, 1,280 setup states, DNS packet/reply checks, loopback
  route checks, authenticated TLS identity/reuse and socket ordering, clocks and
  rule bounds. Desktop timing output is not a phone-performance promise.
- Debug APK, test APK, Android lint and release-manifest generation pass.
  Lint has zero errors and seven warnings. Shell fixture activities and their
  `DUMP` permission are absent from the release manifest.
- Samsung isolated instrumentation passes asset loading, persistence, opt-in
  behavior, literal-rule independence and commitment rejection/migration checks.
  Generated test preferences are separate from the owner's settings.
- Live fixtures pass: ordinary/editable/password content stays available;
  custom words, long labels, child 75, selected-app restrictions and rapid
  reopening trigger enforcement. Periodic checks run in the selected fixture
  and stop outside it. English/Arabic adult-promotion fixtures are restricted;
  the benign medical fixture stays available. Temporary fixture rules are
  restored; no real commitment is started or permission granted by the driver.
- Seed and extension suites pass separately. This is not fresh macOS/iOS build
  or installation evidence.

Live DNS: the local block traversed the TUN successfully. The initial public
resolver step exposed the socket-protection bug. After its correction the
Samsung temporarily had no active underlying internet network. Once connectivity
returned, one probe failed with a resolver EOF category, then a complete probe
passed both steps through the TUN and authenticated resolver. A later repeat
exposed stale pooled TLS connections. After the bounded stale-only reconnect
fix, three consecutive two-step route checks passed on the latest installed
APK. A temporary USB interruption was retried separately, not counted as a DNS
pass. The final session reports PASSED/local-block true, forwarded replies and
no categorized resolver error, but unsupported/other failed packets still
produce historical DEGRADED status. The session keeps
its historical DEGRADED status; the UI does not erase earlier errors on a pass.
Network-transition recovery and ordinary browsing still need acceptance testing.

Installed development APK SHA-256:
`0935650d4517918553d9d3ed2b2f995aa52ced5f9cd251fef3b9ea830af8a44d`.

## Quality boundary and next gates

A small separate English/Arabic text corpus produced 9/16 adult-text hits and
1/18 benign false positives. These are fixture counts, not a representative
accuracy estimate; accessibility label boundaries can change results. Missing
promotion wording and punctuation obfuscation remain important gaps.

Expand the connected-network test, then test real supported app layouts,
Wi-Fi/mobile transitions, resolver failure/recovery, service loss and battery.
Android image/video detection, OCR, schedules, usage limits, signed downloaded
updates, full-traffic/always-on coverage and managed uninstall prevention are
not implemented. Android settings can still revoke, stop or remove this app.
No normal installation is represented as undeletable or unstoppable.

See [implementation gates](ANDROID_IMPLEMENTATION_STATUS.md) and
[build and controlled-test instructions](../android/README.md).
