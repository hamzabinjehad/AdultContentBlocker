# Hisn iPhone and iPad 1.8.0 — the device-wide DNS filter, and a direct build

Development candidate, 7 November 2026. Simulator-tested only; no Apple team,
signed device build, Family Controls entitlement or physical-device acceptance
is claimed. The direct distribution path is described in
[IOS_DIRECT.md](IOS_DIRECT.md).

## Why

On Android, Hisn's DNS layer runs **Hisn's own signed list** on the device:
236,586 core domains, four adult TLDs, the reviewed hostname terms and the
person's saved sites are answered locally, and only names that are not blocked
are sent to Cloudflare Families. Every app's system DNS goes through it, not
only the browser.

On iOS, the old DNS row configured a **Cloudflare Family DNS profile**. That is
a category filter chosen on Cloudflare's servers, not Hisn's list; the list
lived only in the six Safari content blockers, so every non-WebKit app was
outside it. It was also a system DNS-settings configuration a person could
switch off in Settings without any Hisn state changing.

1.8.0 replaces that with a Network Extension **DNS proxy** written for this
app. While it is on, iOS routes system DNS questions through Hisn:

- a question for the signed seed's core tier, an adult TLD, a reviewed hostname
  term or one of the person's saved sites is answered with a local NXDOMAIN;
- every other question is answered by Cloudflare Families over HTTPS;
- nothing is stored, and the app makes no claim beyond the DNS questions it
  actually sees.

The same release adds the **direct build**: an IPA built outside the App Store,
downloadable from any HTTPS host, that a sideloading tool signs with the
person's own Apple team. See [IOS_DIRECT.md](IOS_DIRECT.md).

## What changed

### The DNS proxy extension

`ios/HisnDNSProxy/` is a new app extension with the
`com.apple.networkextension.dns-proxy` extension point and the matching
`dns-proxy` entitlement. `DNSProxyProvider`:

- loads the build-verified assets at start and **refuses to start** if the
  domain list is missing, its byte count or SHA-256 does not match, its count
  is wrong, or the metadata's adult-TLD set differs from the code's. The app
  then reads the layer as off instead of showing an unfiltered tunnel as on.
- answers the route-check alias, and any name under the reserved
  `hisn.invalid` domain, from the device: an A question gets `203.0.113.7`
  (TEST-NET-3, reserved for documentation), and other types get NOERROR with
  no answer. No real resolver would answer those names.
- answers a blocked name with NXDOMAIN for every query type, copying the
  original ID and question bytes, with no upstream request.
- forwards allowed questions over HTTPS to Cloudflare Families at
  `https://1.1.1.3/dns-query`, falling back once to `1.0.0.3` on a transport,
  HTTP, content-type, size or question-mismatch failure. The endpoints are IP
  literals, and their certificate carries those addresses as subject
  alternative names, so the resolver needs no DNS lookup of its own.
- validates each reply before forwarding it: response bit, ID, one question,
  name, type and class must match; the body is bounded to 65,535 bytes.
- handles both UDP and TCP DNS flows. TCP requests and replies are
  length-prefixed and answered in order; a fragmented or oversized frame closes
  the flow instead of growing a buffer.
- respects the client's EDNS UDP payload size (512–4,096) and replaces a reply
  that does not fit with a TC reply, so the stub resolver retries over TCP.
- bounds work to 16 concurrent exchanges and drops a malformed question rather
  than forwarding it. It logs no query and stores no history.

### The build-time data

`ios/prepare_rules.py --dns` verifies the signed seed exactly as the Safari
path does (Ed25519 manifest, byte count and SHA-256, sorted unique canonical
domains, infrastructure safety policy), verifies `terms.json` against the same
signed manifest, and writes three files into the extension bundle:

| File | Content |
| --- | --- |
| `dns-domains.txt` | the core tier, one sorted domain per line (236,586) |
| `dns-host-terms.json` | the hostname subset of the signed terms: 116 terms, 96 never-keyword tokens, 4 exempt sites |
| `dns-metadata.json` | schema, seed version, count, build date, both SHA-256 digests and sizes, terms version, the four adult TLDs |

The extension re-checks both digests and the count at start. A damaged **domain
list** is fatal; a damaged **terms file** is not: the keyword rail turns off and
the domain list keeps enforcing, exactly as Android's resolver does. The
metadata's adult-TLD set is compared with `AdultTldGuard.tlds`, so the build
and the policy cannot drift apart.

Domains are indexed without a `Set` of 236,000 `String`s: the extension reads
the file once, records line offsets, and binary-searches the bytes. A lookup
for a host walks its parent names and compares without allocating a `String`
per entry. `DomainIndex` rejects an unsorted, duplicated, uppercase, empty or
non-canonical file rather than working around it.

### The policy, shared with the other platforms

- `ios/Shared/DomainIndex.swift` — the sorted-tier index and canonical host
  rules.
- `ios/Shared/HostTermPolicy.swift` — the token/substring/never-keyword/exempt
  rules, ported from Android's `HostTermPolicy` and the Mac's
  `BlocklistStore`. It uses the same `TextNormalizer` and punycode decoder,
  which moved to `shared/apple/` in this release so all three platforms compile
  one copy.
- `ios/Shared/DNSMessage.swift` — the bounded query parser and reply builder.
- `ios/Shared/DNSFilterEngine.swift` — the decision order: self-check alias,
  the person's sites, the list, the adult-TLD rail, the hostname terms.
- `ios/Shared/DNSUpstream.swift` — the validated Cloudflare Families client.

### The app

- The Setup → DNS section is now **DNS filter**: turn it on, turn it off, and
  **Check the DNS route**. The check asks the system resolver for a fresh
  random name under `hisn.invalid` and expects Hisn's own
  `203.0.113.7`; a cached answer from before the filter was on cannot make it
  pass. The result is labelled as evidence about this app's resolver path, not
  about every app.
- The status is read back from `NEDNSProxyManager`: **On · all apps**,
  **Saved · not on**, **Another DNS filter is on**, **Off**, or **Could not
  check**. A saved configuration is never shown as filtering.
- A running commitment freezes the off action, like every other saved rule.
  Turning the filter on is always allowed because it only tightens.
- The person's saved blocked sites are mirrored to the App Group for the
  extension, which re-reads them on a 30-second cache; the app does not need to
  restart the tunnel for the extension to notice an edit, though iOS's
  cross-process preference cache can delay it. The Screen Time filter keeps its
  own copy.
- English and Arabic copy explain, on the screen: this filters system DNS;
  an app with its own encrypted DNS can bypass it; page content and images are
  not inspected; Cloudflare sees names that are not blocked; it is not a full
  VPN and stores no history.
- `NEDNSSettingsManager` and the old Family DNS configuration path are gone.
  The app's entitlement changes from `dns-settings` to `dns-proxy`, so an
  existing profile that only has DNS Settings must be reissued. The
  provisioning profile must actually include `dns-proxy`; the entitlements
  file is a request, not a grant.

### The direct build

- `ios/direct/build_direct.sh` builds Release for `generic/platform=iOS`,
  checks that all six Safari blockers, the scanner, the monitor, both shield
  extensions and the DNS extension (with its three data files) are present,
  and writes `dist/ios/Hisn-1.8.0-14.ipa` plus `build-info.json`.
  `--adhoc` signs each nested bundle first and the app last with its
  entitlement requests, for installers that preserve an existing signature.
- `ios/direct/make_source.py` writes the AltStore/SideStore source JSON from
  the IPA's own `Info.plist` and bytes: version, build, size and SHA-256 are
  read, never typed by hand, and `--check` validates a published file.
- `docs/IOS_DIRECT.md` names the install paths and the entitlement facts.
- The build is unsigned by default and **not installable on a stock iPhone**
  until an installer signs it with a profile that includes the capabilities.
  That sentence is in the script's output and the docs, not hidden.

## Files

| File | Change |
| --- | --- |
| `ios/HisnDNSProxy/` | new DNS proxy extension: provider, Info.plist, entitlements |
| `ios/Shared/DomainIndex.swift`, `HostTermPolicy.swift`, `DNSMessage.swift`, `DNSFilterEngine.swift`, `DNSUpstream.swift` | new shared, tested policy and wire code |
| `ios/Hisn/DNSRouteProbe.swift`, `DNSCustomSitesShare.swift` | route check and the App Group mirror |
| `ios/Hisn/ProtectionController.swift` | proxy install/readback/disarm, route check, custom-site mirror |
| `ios/Hisn/SetupViews.swift` | DNS filter controls and the route check result |
| `ios/Hisn/en.lproj`, `ar.lproj` | new and rewritten DNS copy in both languages |
| `ios/Hisn/Hisn.entitlements` | `dns-settings` → `dns-proxy` |
| `ios/prepare_rules.py` | `--dns` writes the verified domain, terms and metadata files |
| `ios/generate_xcodeproj.py` | the DNS proxy target, embedding, build phase, version 1.8.0 / 14 |
| `ios/HisnTests/DNSFilterTests.swift` | 18 simulator tests |
| `ios/test_rules.py`, `test_privacy.py`, `test_direct.py` | build-data, wiring and source-JSON tests |
| `ios/direct/` | the direct IPA build and AltStore source generator |
| `shared/apple/TextNormalizer.swift`, `Punycode.swift`, `AdultTldGuard.swift` | moved from `macos/Hisn/` so iOS compiles the same policy |
| `macos/generate_xcodeproj.py` | follows the move; the Mac targets are otherwise unchanged |

## Verification

Run `./test.sh python` and `./test.sh ios`.

- `./test.sh ios`, 7 November 2026 on the iPhone 17 simulator (iOS 26.5):
  **55 Python tests and 271 simulator tests, 0 failures**. This is a simulator
  result only; see the device gaps below.
- Python: the seed, rules, privacy/wiring and direct-distribution suites pass.
  `test_rules.py` gains four DNS tests: the exact bytes and digests of both
  generated files, determinism, the shipped seed's output, and rejection of a
  tampered, missing, wrongly-versioned or invalid terms payload. `test_privacy`
  pins the extension point and both entitlement files. `test_direct.py` builds
  a fake IPA and checks the source JSON against its bytes, refuses HTTP or
  non-IPA URLs, and runs `bash -n` on the build script.
- Simulator: 19 new tests in `DNSFilterTests.swift`:
  - query parsing, EDNS sizes, and rejection of responses, bad opcodes,
    multiple questions, answer records in a query, compression pointers,
    over-long labels, non-ASCII labels, truncation, oversized additional
    sections and malformed OPT records;
  - NXDOMAIN echoing, response matching, TC fallback and the self-check answer;
  - the domain index's apex/subdomain coverage and rejection of damaged files;
  - the hostname rules, punycode Arabic hosts, never-keyword and exempt sites;
  - the engine's four reasons, the `.hisn.invalid` alias, asset digests and the
    terms-failure degradation;
  - the resolver's fallback on transport, HTTP, content-type and question
    mismatch, and its refusal to send an oversized query;
  - a prefix-line search case that caught a real comparator bug before release:
    when the query was a prefix of a listed name, the binary search could walk
    past the exact entry. The fix is in `DomainIndex.compare` and is pinned by
    `testPrefixLinesAreOrderedCorrectlyInTheSearch`.
- **The real list checked outside the test bundle:** loaded with the Release
  `DomainIndex`, all **236,586** entries were found, `pornhub.com`,
  `www.pornhub.com`, `xvideos.com` and `sub.xvideos.com` blocked,
  `pornhub.com.unblocked.example` did not, and `google.com` and
  `essex.gov.uk` were not in the core list. This is a local code check, not a
  device DNS run.
- The proxy extension itself is **not** exercised here: the Simulator has no
  Network Extension provider, so `test.sh ios` removes the appex before
  installing the test host. `NEDNSProxyProvider`, its permission prompt, the
  leak of DNS through it, UDP/TCP flow handling and the Cloudflare transport
  all still need a signed device build.
- No signed IPA, device acceptance, real DoH exchange or DNS-route pass is
  claimed. The only DNS evidence in this release is the pure code above.

## Limits

- **System DNS only.** A question an app resolves through the system resolver
  reaches the filter. An app that speaks DNS-over-HTTPS or DNS-over-TLS to its
  own server, connects to an IP literal, or uses a private resolver can leave
  the path. This is the same class of gap Android's resolver documents, and it
  is in the app's DNS copy rather than hidden.
- **No page, text, image or video inspection outside Safari.** The DNS filter
  sees names, not content. Hisn Text and the Safari blockers still cover
  WebKit only.
- **Not a full VPN.** Only DNS goes through the extension. Other traffic keeps
  its normal route and the system VPN slot is taken by the proxy.
- **The person can turn it off.** It is a Network Extension the person enables
  and a commitment can only freeze the app's own action. On an unsupervised
  phone, Settings can still disable it; the app shows the layer off on its next
  refresh. There is no background callback for that change.
- **Family Controls still needs Apple's approval.** A profile without
  `com.apple.developer.family-controls` cannot run the Screen Time layers, and
  the direct IPA does not change that; see [IOS_DIRECT.md](IOS_DIRECT.md).
- **Screen Time rules still use the person's picks.** There is no "block every
  app except these" mode on iOS in this release; always-block, the daily
  budget, quiet hours and the pause rule apply to the apps, categories and
  websites the activity picker returns. This is the Android allowlist's open
  counterpart.
- **The list changes only with a build.** The DNS assets are compiled into the
  extension and signature-verified at start. There is no downloaded list
  update, and a new seed needs a new signed build.
- **No device evidence.** Everything above about real blocking, the permission
  prompt, Cloudflare reachability, battery, memory and the route check is
  untested on hardware.
