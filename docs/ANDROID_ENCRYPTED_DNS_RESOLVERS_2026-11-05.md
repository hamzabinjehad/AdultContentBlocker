# Android encrypted-DNS resolver blocking — 2026-11-05

This is an enforcement record, not a release. It adds one DNS-layer coverage
fix and changes no rule, permission, consent text or stored value. The Android
version stays 1.1.0 (versionCode 27).

## Why

Hisn's DNS tunnel already answers the well-known DoH/DoT provider names with
NXDOMAIN and refuses TLS on port 853 to its own virtual server. An app that
hard-codes a public resolver's address never asks Hisn's DNS server, so it can
use that resolver to bypass domain filtering.

## What changed

- `EncryptedDnsResolvers` holds 18 dedicated IPv4 and 12 dedicated IPv6
  public-resolver addresses: Cloudflare, Google, Quad9, OpenDNS, AdGuard,
  Control D, CleanBrowsing, Verisign and Neustar.
- `DnsVpnService` adds each address as a host route (`/32` or `/128`) to its
  tunnel, so traffic to it enters the tunnel instead of the underlying network.
- The packet reader drops a packet whose destination is one of those addresses
  before DNS parsing. The count appears in diagnostics as **Encrypted-DNS
  addresses blocked**.
- The tunnel now has an IPv6 address (`fd00:111::1/128`) so the IPv6 host
  routes can be installed. It still advertises only the IPv4 virtual DNS
  server; ordinary IPv6 traffic is not captured.
- The DNS full disclosure names this coverage and its limits.

Shared CDN addresses are deliberately absent: blocking them would break
ordinary websites. The list is fixed addresses only, not every provider.

## Evidence

On the owner's Samsung SM-G990B2, direct 1.1.0 build, after starting DNS:

- `dumpsys connectivity` shows the VPN connected with `10.111.0.1/32` and
  `fd00:111::1/128`, `DnsAddresses: /10.111.0.2`, and host routes for every
  listed IPv4 and IPv6 resolver address through `tun0`.
- `ping -c 1 -W 2 1.1.1.1`: 100% packet loss.
- `ping -c 1 -W 2 8.8.8.8`: 100% packet loss.
- `ping -c 1 -W 3 google.com`: resolved through the tunnel and replied (0%
  loss), so ordinary DNS and non-resolver traffic still work.
- Diagnostics showed **Encrypted-DNS addresses blocked: 2**, matching the two
  test packets.

These are single-device checks, not a claim that every app on every phone is
covered.

## Tests

- `EncryptedDnsResolversTest`: every listed IPv4 and IPv6 literal is blocked;
  the virtual server `10.111.0.2` and filtered upstreams `1.1.1.3` and
  `1.0.0.3` are not blocked; short packets are rejected; no duplicate entries.
- `bash android/test-core.sh`: pass, including the new test and the existing
  239,880-domain seed, DNS reply guard, TLS transport and adult-text suites.
- `check-strings.py`: pass with the new diagnostic placeholder in English and
  Arabic.

## Limits kept

This is not a full tunnel and does not intercept TLS. It does not cover:

- DoH/DoT through a shared CDN address or an app-specific resolver;
- Android Private DNS set to a provider hostname (Hisn warns; a device owner
  can restrict it);
- direct IP access, cached answers, or content inside an app;
- another VPN taking over, or the VPN being turned off in Android settings;
- factory reset, ADB, root or a different Android user/profile.

The DNS consent still says exactly that. No claim of complete filtering is
made.
