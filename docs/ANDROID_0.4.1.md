# Hisn Android 0.4.1 — network-change recovery

2026-10-20 · version code 6 · development-signed APK installed on the authorized
Samsung SM-G990B2 / Android 16. Apple apps and browser code were not changed in
this update.

## Changes

- Observe matching physical/underlying networks with `INTERNET + NOT_VPN`, not
  the app's VPN default-network callback. Register only during a DNS session;
  unregister and clear ephemeral network metadata during shutdown.
- React to network availability/loss, changed link properties and meaningful
  validation/captive changes. Duplicate callbacks and bandwidth/signal changes
  do not trigger invalidation. No network settings are changed.
- Discard pooled TLS connections on a network event. Already-buffered old
  responses and active exchanges are rejected using transport generations.
  A later request creates a new protected socket to the same authenticated
  resolver. Close remains terminal; invalidation cannot reopen a closed transport.
- Cancel the active route check and mark earlier results stale on a network
  event. No test query is automatically sent. A late old-network error cannot
  erase a new successful check; current-network failure generations fence
  otherwise-successful check publication. Old cancelled clients can receive
  SERVFAIL without contaminating the new network's health.
- Show separate unsupported TCP/other, malformed and network-cancelled counts.
  TCP classification is diagnostic only. It does not forward unsupported
  traffic or promote it to a successfully filtered request. Reserved IPv4 flags
  are also rejected by the UDP adapter.
- Preserve rules, commitment checks, text checking, TLS certificate/hostname
  validation and the no-plaintext/no-alternate-resolver policy.

`ACCESS_NETWORK_STATE` is a normal manifest permission. The observer keeps
temporary link snapshots for equality comparison, not a browsing log. It sends
no public request, scans no screen and grants no system consent automatically.

## Verification

Core suites pass, including authenticated local TLS identity tests, maximum-one
stale retry, cross-thread cached invalidation, in-flight cancellation, a fully
buffered old reply, invalidation/close during socket opening, terminal closure,
and no retry for a fresh failure after invalidation. Packet diagnostics pass
valid/malformed cases plus 40,000 arbitrary/mutated inputs. Existing rule,
seed, clock and 1,280-state setup tests pass.

Debug/test APKs, lint and release-manifest generation pass: zero lint errors,
eight warnings. Debug fixture activities and their `DUMP` permission are absent
from the release manifest. Device instrumentation was not rerun in this update
because it restarts the active accessibility process; earlier evidence is in
[0.4.0](ANDROID_0.4.0.md).

On the Samsung, the opt-in external driver passed:

1. Local synthetic NXDOMAIN block plus authenticated `example.com` reply.
2. A simulated network callback clears the previous pass without stopping VPN.
3. A new complete route check passes with fresh resolver connections.
4. A simulated delayed old-network failure leaves the new passed result intact.

The full controlled app/text suite also passes on 0.4.1: ordinary, editable and
password controls remain available; custom words, long labels, child 75,
selected-app rules and rapid reopening are restricted. Periodic checks stop
outside the selected fixture. Fixture additions are removed, preserving owner
rules, without starting a commitment or granting permissions.
The separate adult-mode fixtures pass too: English/Arabic adult-promotion
labels are restricted and the benign medical control remains available.

The simulation calls Hisn's invalidation path; it does **not** toggle Wi-Fi/data
or prove a real radio handover. Android reported the observer registered and
delivered a real initial network-availability callback. Unsupported packets
still produce a limited/degraded session state; a passing UDP route test does
not clear historical coverage errors or establish full protection.

APK SHA-256:
`6839d03345a24a735f059d6a8ad0b70a3287c79750f1ff6bfc767c3b3e32175d`.

## Remaining work

Actual Wi-Fi/mobile/offline/captive transitions, prolonged normal browsing,
screen lock, lifecycle loss and battery still need acceptance tests. TCP DNS,
large replies, app-encrypted DNS and direct-IP coverage remain gaps. A revoked,
stopped or removed app cannot be resurrected by this observer. Android visual
filtering, OCR, schedules and managed uninstall prevention are not implemented.

Official references: [network callbacks](https://developer.android.com/reference/android/net/ConnectivityManager#registerNetworkCallback(android.net.NetworkRequest,%20android.net.ConnectivityManager.NetworkCallback)),
[network-state guidance](https://developer.android.com/develop/connectivity/network-ops/reading-network-state).
