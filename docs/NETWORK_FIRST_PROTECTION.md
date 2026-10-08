# Network-first protection: assessment and implementation plan

Research and code review: 2026-10-02. This is a proposed architecture, not a
record of installed router policies, device enrollment, or completed hardware
validation. The first implementation pass is recorded below.

**الخلاصة:** المشروع لم يصل بعد إلى أقصى حماية عملية. الاتجاه المناسب هو
حجب على الشبكة، ثم حماية مستقلة على الماك والهاتف تستمر خارج شبكة البيت.
مقاومة التعطيل تحتاج أن يحتفظ شخص موثوق بصلاحيات الإدارة، أو إدارة أجهزة
رسمية. موافقة تثبيت تطبيق على الماك لا تمنحه صلاحية إدارة الراوتر والهاتف.
لا يمكن ضمان منع كل المحتوى أو منع التعطيل إلى الأبد، خصوصاً مع جهاز آخر،
إعادة ضبط الجهاز، أو محتوى محلي. «الملفات الممنوعة» هنا تعني قوائم الحجب
التي نثبتها، وليس تنزيل المحتوى الممنوع نفسه.

## Recommendation and scope

Build a network-first setup flow with independently enforced device layers.
Attempt network setup first, but protect the Mac immediately if router access
is unavailable. A home router cannot enforce policy on cellular data, another
Wi-Fi network, or a new device outside that network.

The strongest practical configuration within this threat model combines a
guardian-controlled gateway, independently managed endpoints, restricted
applications, and a narrow allowlist where acceptable. This is a design
recommendation, not a measured ranking of every filtering product.

Two properties must be reported separately: **content coverage** and
**resistance to disabling protection**. Managing a filter does not make its
classification perfect. Allowing a website also permits some content on it;
mixed-content services can remain a route to unsuitable material.

## What this repository actually implements

### Network policy hardening (2026-10-04)

iOS and iPadOS now save the family DoH resolver with an explicit default-domain
scope (`matchDomains = [""]`). Readiness rejects a resolver scoped only to selected
domains, even if its server addresses and URL match Hisn. A saved configuration
is still not reported as enabled. These are configuration checks, not proof of
live DNS filtering; device activation and bypass behavior require signed-device
testing. Apple documents this scope in
[NEDNSSettings.matchDomains](https://developer.apple.com/documentation/networkextension/nednssettings/matchdomains).

The macOS network policy canonicalizes the three IDNA alternate dot separators
before built-in, custom, allowlist and strict-mode decisions, including manually
configured rules. Subdomain boundaries remain intact: `blocked.example.evil.org`
does not match `blocked.example`. This does not implement general Unicode
confusable detection or TLS content inspection. Existing Mac enforcement remains.

### Guided router entry point (2026-10-03)

The native setup screen now starts with an owner-permission check and **Find my
router**. It reads macOS's IPv4 gateway metadata locally, accepts only literal
private IPv4 addresses on Ethernet/Wi-Fi/bridge interfaces, and offers to open
the candidate's HTTPS page. An HTTP fallback requires a separate warning and
confirmation. It does not scan the network, guess router credentials, bypass
certificate validation, upload lists, or write router settings. The gateway and
interface are rechecked before opening. Address discovery does not identify a
router model or authenticate the router; users must confirm it against their
device's documentation.

VPN/unknown interfaces, public gateway addresses, IPv6-only networks and app-only
routers fall back to official/manual guidance or device protection. Advanced
routes remain available. The DNS setup explicitly distinguishes a provider's
categories from Hisn's signed list and explains false-positive/recovery limits.
Signing authenticates a publisher and bytes, not an adult-content classification.

This is a simpler entry point, **not universal one-click router installation**.
Router/firmware adapters, backup/rollback, supported authenticated configuration,
and live per-device enforcement tests remain necessary before such a claim.

### Network-filter hardening (2026-10-03)

The macOS socket filter now installs strict mode, allowed sites, custom blocks,
and blocked applications atomically. Each new-flow decision reads one consistent
policy and blocklist snapshot; a concurrent update cannot combine an old allowance
with new blocks or a new strict-mode setting with old site lists. Policy changes
are installed on the filter's serial work queue before the app receives an
acceptance response.

`NetworkFlowPolicyTests` covers concurrent policy changes, blocked-app precedence,
strict-mode unknown-host/IP denial, subdomain boundaries, and avoiding unnecessary
app-identity lookups. This is code-level verification, not proof of live system
extension or router enforcement. Existing flows are not re-evaluated by this
change; unknown destinations still pass in normal blocklist mode. No DNS settings,
router configuration, or installed system extension are changed by these tests.

| Capability | Current evidence | Limit |
|---|---|---|
| Signed domain/term pipeline and DNS publication | [build.py](../blocklist/build.py), [seed verification](../blocklist/seed.py), [network publisher](../network/README.md) | Local verified AdGuard rules now available; no live router adapter or resolver enforcement yet |
| Device DNS and hosts | [profile generator](../profile/make_profile.py), [hosts installer](../macos/block_dns.sh) | Affects the Mac; does not configure the household router |
| Mac system filter | [FilterDataProvider.swift](../macos/HisnFilter/FilterDataProvider.swift) | Decisions are by hostname/application at new-flow time; unidentified flows pass outside strict mode |
| Browser scanning | [scan.js](../extension/content/scan.js), [Inspection.swift](../macos/Hisn/Inspection.swift) | Text and image labels, not image pixels or video classification; Safari/Firefox lack the Hisn text extension |
| Guardian authority | [account setup](../macos/setup_guardian.sh), [policy authority](../macos/Hisn/PolicyAuthority.swift), [partner signatures](../macos/Hisn/PartnerService.swift) | Recovery remains accessible; signed-install persistence and IPC still need hardware validation |
| Setup flow | [NetworkSetupGuide.swift](../macos/Hisn/NetworkSetupGuide.swift), [ProtectedSetup.swift](../macos/Hisn/ProtectedSetup.swift) | Capability-based network guide precedes Mac checks; network stays unverified and is not counted as Mac progress |
| Mac DNS sample | [NetworkDNSProbe.swift](../macos/Hisn/NetworkDNSProbe.swift) | User-triggered system A/AAAA sample for Cloudflare's harmless category test; hosts/error checks and before/after local network/DNS metadata comparison; cache may apply; no gateway, transport-family, or other-device verification |
| Phone protection | [universal native app](../ios/README.md), [platform boundaries](APPLE_PLATFORMS.md) | Safari blockers, optional family DNS, authorized Screen Time and selected-app rules implemented; signed-device enforcement still needs validation |

Release findings and first-pass fixes:

1. A stopped/crashed Mac content provider can leave traffic passing. Existing
   [health documentation](HEALTH.md) acknowledges this. Code inspection found
   reassertion on app launch/view entry. The first pass adds periodic recovery,
   monotonic backoff, and sticky trusted lock evidence during IPC loss. Recovery
   is not continuous denial; the real provider restart still needs hardware testing.
2. The first pass makes policy acceptance follow a successful durable write.
   Damaged/unreadable stores enter strict administrator recovery, retain evidence
   across restarts, and report the problem instead of returning unlocked state.
   Fault regressions cover write failure, damaged copies, and clock checkpoints.
3. Already-open Mac sockets are not revoked when a lock tightens. See
   [POLICY.md](POLICY.md). Browser strict mode also allows external images and
   fetches as page plumbing, so browser-only strict mode is not equivalent to
   the stricter socket filter.
4. Generic claims such as “VPN-proof”, “all content”, or “cannot be bypassed
   alone” exceed the evidence. Seeing a tunnel's socket is different from
   identifying every destination or item transported through that tunnel.

## Proposed setup sequence

1. **Consent and administrator:** explain the selected scope, affected devices,
   other household users, who may release the lock, and the recovery route.
   Choose personal or managed deployment before changing settings. Keep device
   erasure and account changes as explicit, separate setup actions.
2. **Network:** assess administrator access and DNS/firewall capabilities on any
   router; model/firmware guide a future adapter only when one is implemented.
   Show a configuration preview and backup/recovery instructions. Configure DNS
   manually or use a filtering resolver. Unsupported/inaccessible routers are
   clearly marked and setup proceeds to the Mac.
3. **Mac:** install the signed filter, verify live blocking, connect managed
   browsers, then complete administrator separation after recovery works.
4. **Phones and other devices:** enroll/configure each separately and test
   Wi-Fi, cellular, and other Wi-Fi networks. A phone connected to the home
   router is not evidence that the phone is protected everywhere.
5. **Verification:** report each layer as unconfigured, awaiting approval,
   unsupported, verifying, verified, or degraded. A countdown, selected DNS
   address, or enrollment checkbox is not sufficient evidence of enforcement.

## Network implementation

Use a managed gateway with a filtering resolver. OpenWrt plus AdGuard Home is
a candidate reference implementation because it exposes firewall control and
list import; compatibility, memory use, and capacity must be measured on the
actual hardware before selecting it. An ISP router that only allows changing
DNS cannot provide the same enforcement.

- Advertise the approved resolver and enforce ordinary TCP/UDP DNS on both
  IPv4 and IPv6. OpenWrt documents DNS interception and blocking port 853 for
  encrypted DNS. [OpenWrt DNS interception](https://openwrt.org/docs/guide-user/firewall/fw3_configurations/intercept_dns).
- Lock unauthorized encrypted DNS/proxy settings on managed endpoints. Denying
  port 853 and known DoH hosts is partial protection: DoH uses HTTPS and can
  share port 443 with ordinary traffic. Do not promise universal DoH/VPN
  blocking from a port or domain list. [IETF RFC 8484](https://datatracker.ietf.org/doc/rfc8484/).
- For the strongest restricted deployment, design an explicit permitted-egress
  policy together with device/application restrictions. Blocking familiar VPN
  ports alone does not stop tunnels or proxies over allowed HTTPS services.
- Both primary and backup resolvers must enforce the policy. Resolver failure
  must not switch to an unfiltered public resolver.
- A privileged gateway updater must verify the signed manifest, artifact
  hashes, and monotonic version before deriving a local resolver list. Import
  from that verified local artifact, replace it atomically, and retain the last
  valid generation. A resolver fetching a raw URL does not automatically verify
  Hisn's signatures. Preserve the pipeline's CDN/infrastructure safety rules.
- Keep gateway administration with the guardian, outside the daily user's
  reach. Avoid storing a universal vendor administrator secret in the app.
  Physical reset is still a limit: TP-Link documents that reset removes custom
  settings, including parental controls. [Router reset example](https://www.tp-link.com/uk/support/faq/497/).

Network DNS cannot select a particular image, post, or file on an otherwise
allowed host. It does not inspect local files. A secure web gateway can provide
URL inspection, but HTTPS inspection requires additional trust/configuration;
it should not be silently enabled as part of simple DNS setup.
[DNS limits](https://adguard-dns.io/kb/adguard-home/faq/#are-there-any-known-limitations),
[HTTP inspection requirements](https://developers.cloudflare.com/cloudflare-one/traffic-policies/get-started/http/).

## Mac enforcement and stronger URL coverage

For personal deployment, retain the standard daily account with a separate
guardian administrator and signed filter. Do not describe that account split
as closing every Recovery or reinstall route; the current setup preserves the
daily user's FileVault secure token.

For managed deployment, macOS 15+ offers MDM
`NonRemovableSystemExtensions`, which prevents disabling/removing selected
system extensions while SIP is enabled. The separate
`NonRemovableFromUISystemExtensions` policy prevents disabling or uninstalling
through System Settings or Finder.
Neither capability comes from a normal app installation.
[Apple SystemExtensions](https://developer.apple.com/documentation/devicemanagement/systemextensions?changes=_2_2&language=objc).

Repair recovery and durable-policy acceptance before strengthening release
claims. A watchdog can shorten a gap, but cannot make a stopped content
provider enforce traffic. Continuous denial during loss needs another enforced
layer, with its exclusions and failure behavior tested on every supported
network, including roaming.

Investigate Apple's **URL Filter** APIs introduced in OS 26 as a coverage
upgrade beyond hostname filtering. They support full URL decisions with local
Bloom filters and PIR lookups. Consumer deployments are supported; the MDM
configuration requires supervision. Distribution requires entitlement/relay
approval, and applications using custom network stacks need explicit API
integration. This needs a prototype and signed distribution validation; it is
not implemented here and is not an image/video classifier.
[Apple URL filtering](https://support.apple.com/guide/deployment/filter-content-dep1129ff8d2/web),
[Apple implementation and deployment requirements](https://developer.apple.com/videos/play/wwdc2025/234/).

## Phone enforcement

**iPhone/iPad:** ordinary Screen Time/guardian settings offer a simpler personal
path, with clearly stated limits. The stronger managed path uses supervision,
managed filtering, and, where suitable, Always On VPN. Apple's Always On VPN
supports Wi-Fi and cellular, persists over restart, and drops non-exempt traffic
when its tunnels are unavailable. Network control-plane traffic is excluded;
configured captive-portal, application, and service exceptions can pass outside
the tunnel. Audit those exceptions and disable the user VPN toggle before
claiming enforced routing. Apple Watch pairing is unsupported. Manual supervision through
Configurator requires erasing the device.
[Apple VPN overview](https://support.apple.com/guide/deployment/vpn-overview-depae3d361d0/1/web/1.0),
[Apple VPN routing and exclusions](https://developer.apple.com/documentation/networkextension/routing-your-vpn-network-traffic),
[Apple supervision](https://support.apple.com/guide/deployment/about-device-supervision-dep1d89f0bff/web).

**Android:** fully managed Device Owner deployment can enforce always-on VPN
with lockdown, block VPN changes/uninstallation, and restrict debugging.
A personal work profile only protects its managed profile. System-app and
configured allowlist exceptions must be evaluated; do not label an ordinary
VPN app as impossible to remove.
[Android managed networking](https://developer.android.com/work/dpc/network-telephony),
[Android VPN API](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setAlwaysOnVpnPackage(android.content.ComponentName,%20java.lang.String,%20boolean,%20java.util.Set)).

Enrollment resistant to removal/re-enrollment after a wipe is a separate
managed-device deployment. Apple Automated Device Enrollment is designed for
organization-owned devices; Android zero-touch requires registered, assigned
devices. Neither is an automatic property of installing Hisn on a personal
device. Apple devices manually added through Configurator have a 30-day
provisional period during which the user can release enrollment and
supervision; claims about non-removal must account for that period. Wiping and
physical access remain part of the declared threat model.
[Apple enrollment](https://support.apple.com/guide/deployment/automated-device-enrollment-management-dep73069dd57/web),
[Apple Configurator provisional enrollment](https://support.apple.com/en-gb/guide/business/axm200a54d59/web),
[Apple profile removal](https://support.apple.com/guide/deployment/intro-to-device-management-profiles-depc0aadd3fe/web),
[Android zero-touch](https://support.google.com/work/android/answer/7514005).

## Implementation priorities and acceptance gates

| Priority | Deliverable | Evidence required before claiming it works |
|---|---|---|
| P0 | Reliable Mac authority and recovery | Signed install; disk-write failure; damaged stores; reboot; provider stop/crash; app absent; measured failure window |
| P1 | One supported gateway adapter and signed updater | Router backup/restore; valid/invalid/old list; DNS overrides; IPv4/IPv6; encrypted DNS; resolver outage; reboot; reset limits |
| P1 | Network-first onboarding and separate layer status | Router unsupported/no permission; fresh active blocking test; network change; no false success from self-report or DNS IP alone |
| P2 | Native phone enforcement | Wi-Fi/cellular/roaming; restart; removal attempts; tunnel failure; enrollment method and exceptions recorded |
| P2 | URL Filter prototype and tighter app/allowlist coverage | Signed consumer/managed deployment; custom network stacks; shared hosts; unknown hosts; already-open traffic |
| P3 | Optional visual classification | Independent multilingual/image/video corpus; false blocks and misses; latency/privacy measurements; no claim of perfect accuracy |

Use harmless owned test destinations and fixtures for enforcement checks.
Record OS/firmware, policy version, timestamp, active network, tested attack,
and result. Do not convert a small passing text corpus into a percentage for
all adult-content coverage. Recovery should be held by the guardian, reachable
without the protected device, and unable to silently become an unfiltered
general browsing path.

## First implementation pass

- Added [network/publish.py](../network/publish.py): pinned-signature/hash/count
  validation, rollback and same-version checks, infrastructure/CDN safety,
  serialized atomic DNS-rule publication, and last-valid-generation preservation.
  [Usage and resolver import](../network/README.md) explain deployment limits.
- Added an offline [capability planner](../network/setup.py) with unknown/manual
  DNS/local resolver/firewall/device fallback paths and separate IPv4/IPv6
  evidence requirements. No reported capability can mark enforcement verified
  or invent an available automatic adapter.
- Added a localized network-first guide in Mac setup, including provider DNS,
  signed local rules, inaccessible routers, and independent phone protection.
  Its informational state does not increment the Mac's verified checklist.
- Added a bounded, cancellable system DNS check to the Cloudflare guide. It
  samples adult-category and ordinary names separately for A/AAAA, rejects
  inconclusive/mixed/local-override evidence, and reports time and record type
  without converting a sample into verified router enforcement.
- Made Mac policy updates transactional and damaged policy recovery explicit.
- Added periodic filter recovery with bounded retry frequency, approval/restart
  guards, deliberate-disable race protection, and retained last trusted lock.
- Made authority loss a failed native heartbeat, and made policy/storage failures
  and an unreachable configured authority visible as incomplete protection.

Validation for this pass: 295 macOS tests; 159 Python tests (88 blocklist,
29 profile, 42 network); JavaScript/package/evaluation checks; seed signature
verification; and 14 release tests plus release configuration checks passed.
The network DNS guide was also rendered and visually checked at the minimum
window width in English and Arabic. These are software/fixture checks, not
live gateway, cellular, MDM, or provider-restart proof.

The follow-up DNS check adds 19 deterministic regressions, bringing the macOS
suite to 314 passing tests; 354 app strings have Arabic translations. Release
checks also pass. A read-only native lookup exercise completed through the
Mac's DNS service and reported non-null A/AAAA test replies instead of claiming
blocking. Result text was rendered and checked in both languages at minimum
width; native window captures can be transformed by Stage Manager, so the
text-layout images are also retained for review. This validates the sampling
path, not a newly installed router policy.

The defensible product promise is **verified protection across the configured
devices and networks, resistant to ordinary user disablement**. Coverage gaps,
unmanaged devices, local/offline media, physical reset, and recovery limits must
remain visible. Live router configuration, phone clients, continuous denial
during a provider outage, and signed managed-device validation remain future work.
