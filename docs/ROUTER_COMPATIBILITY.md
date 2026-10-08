# Router identification and family protection

Research checked against manufacturer documentation and upstream GitHub sources
on 2026-10-03. These are **guided configuration routes**, not a hardware-tested
matrix or automatic router installers.

**للمستخدم:** أدخل اسم الراوتر أو الموديل الموجود على الملصق، واختر الشركة.
يقترح حصن دليلًا من الاسم عند وضوحه، ويطلب تأكيد الاختيار. افتح إعدادات الراوتر
أو تطبيقه الرسمي، ثم اتبع التعليمات المناسبة. تغيير DNS يستخدم تصنيف المزوّد؛
قائمة حصن تحتاج خادم فلترة أو ميزة استيراد متوافقة. الإعداد لا يكتمل بمجرد فتح
الصفحة: اختبر الحجب وموقعًا عاديًا على كل جهاز، واحفظ الإعدادات الأصلية للاسترجاع.

## Identification in the app

1. Read the local default IPv4 gateway and interface from macOS after the user
   starts discovery. A gateway address describes a route, not a manufacturer.
   Cross-check the gateway against that interface's current IPv4 addresses and
   contiguous subnet masks. Reject off-subnet, self, network and broadcast
   addresses, missing metadata and unusable masks before offering a settings URL.
2. Ask for router family and model; hardware/firmware revision is optional.
   The fields remain in the setup view and are not sent to a lookup service.
3. Suggest a family only from explicit names/model prefixes. Shared addresses
   such as `192.168.1.1` and ambiguous labels such as `AX3000` give no suggestion.
   Normalize compatibility characters (including full-width model text), cover
   GL.iNet model prefixes, and suppress suggestions when unrelated families
   conflict. TP-Link Deco retains its more specific phone-app guide.
4. Require a user selection before treating the hint as the selected guide.
   Selecting a guide still does not establish firmware capability or enforcement.
5. Keep family/model details when moving to DNS or custom-list instructions.
   App-managed families use their official app instructions. Other families can
   open the current local gateway's page, with the existing network recheck.

UPnP device descriptions can carry manufacturer/model fields, as documented by
[Microsoft](https://learn.microsoft.com/en-us/windows/win32/upnp/creating-a-device-description)
and implemented in [MiniUPnP](https://github.com/miniupnp/miniupnp/blob/master/miniupnpd/upnpdescgen.c).
That would be a future hint source: advertisements are not authenticated device
identity and may be unavailable. This implementation does not scan SSDP, enable
UPnP, or trust a network-provided administrator URL.

## Documented routes

| Family | Where configuration starts | Hisn-list route and limits | Primary source |
|---|---|---|---|
| TP-Link Archer/DSL | Web interface; Internet DNS or LAN DNS, depending on model | Separate filtering resolver; exact hardware revision matters | [TP-Link DNS guide](https://www.tp-link.com/us/support/faq/1712/) |
| TP-Link Deco | Owner's Deco phone app; Router mode | Custom DNS to a filtering resolver; WAN/LAN paths vary | [Deco guide](https://www.tp-link.com/us/support/faq/1855/) |
| ASUS | Web interface; WAN DNS | Filtering resolver; firmware-specific methods | [ASUS guide](https://www.asus.com/global/support/faq/1045253/) |
| FRITZ!Box | Web interface; upstream DNS or local DNS advertisement | Local filtering server; distinguish upstream from client settings | [FRITZ!Box guide](https://fritz.com/en/apps/knowledge-base/fritz-box-5490/165_Configuring-different-DNS-servers-in-the-FRITZ-Box) |
| NETGEAR/Orbi | Model's web interface and applicable DNS guide | Filtering resolver; article's supported-model list must cover the device | [NETGEAR guide](https://kb.netgear.com/30510/How-do-I-set-static-Domain-Name-System-servers-on-my-NETGEAR-router) |
| Linksys | Model's network settings and Static DNS fields | Filtering resolver; menus differ by product family | [Linksys network fields](https://support.linksys.com/kb/article/119-en/?section_id=162) |
| D-Link | Exact model/revision manual | Filtering resolver when supported; no universal menu path established | [D-Link support](https://support.dlink.com/) |
| OpenWrt | Router administration and package setup | AdGuard Home on suitable hardware, or separate resolver | [OpenWrt AdGuard Home](https://openwrt.org/docs/guide-user/services/dns/adguard-home) |
| GL.iNet | Applications > AdGuard Home on supported models | Verified AdGuard rules; the app identifies documented unsupported models | [GL.iNet model list and guide](https://docs.gl-inet.com/router/en/4/interface_guide/adguardhome/) |
| MikroTik/RouterOS | DNS Adlist where available in installed version | Verified local hosts file; capacity, AAAA and descendant semantics require testing | [RouterOS DNS/Adlist](https://manual.mikrotik.com/docs/network-management/dns/) |
| Ubiquiti UniFi | Content Filter policy for selected networks/devices | Built-in categories and manual domain lists; full Hisn bulk-import support is not established | [UniFi guide](https://help.ui.com/hc/en-us/articles/12568927589143-Content-and-Domain-Filtering-in-UniFi) |
| eero | eero phone app; Advanced networking > DNS | Filtering resolver; eero Plus/HomeKit can affect settings | [eero guide](https://eero.com/support/articles/how-do-i-set-up-custom-dns-servers-with-eero) |
| Google Nest/Google Wifi | Google Home; Advanced networking > DNS | Filtering resolver through custom DNS | [Google guide](https://support.google.com/googlehome/answer/6274141?hl=en) |
| Other/ISP-managed | Official device app/manual or provider assistance | Capability assessment, separate resolver where possible, or device protection | [General router DNS guidance](https://developers.cloudflare.com/1.1.1.1/setup/router/) |

Do not substitute vendor examples of ordinary public DNS for filtering addresses.
Every primary/secondary resolver should apply the intended family policy.
Content categories, Hisn custom domains, DNS enforcement, and protection away
from home are separate properties to verify. DNS setup alone leaves encrypted
DNS/tunnel paths and administrator changes to assess.

## Delivering the actual Hisn lists

The existing signed publisher verifies the pinned Ed25519 key, artifact digest,
domain syntax/count, safety exclusions and version floor before publishing a
complete generation. It supports two exports:

- `adguard`: `current/adguard.txt`, domain-and-descendant rules for AdGuard Home.
- `hosts`: `current/hosts.txt`, exact-name entries for compatible router importers.

Formats use separate state directories. Changing format in an established state
is rejected. A hosts file does not invent descendants of collapsed domains;
never present it as equivalent wildcard coverage without consumer-level tests.
See [network instructions](../network/README.md) for commands and import steps.

AdGuard Home accepts supported DNS filter rules according to its
[upstream rules documentation](https://github.com/AdguardTeam/AdGuardHome/wiki/Hosts-Blocklists).
The upstream [OpenAPI](https://github.com/AdguardTeam/AdGuardHome/blob/master/openapi/openapi.yaml)
and [technical documentation](https://github.com/AdguardTeam/AdGuardHome/blob/master/AGHTechDoc.md)
describe authenticated filter management. A future adapter can use that API
after an explicit resolver selection, backup and version check. No authenticated
API adapter or automatic router installation is shipped by this change.

## Verification evidence

Offline publisher tests cover both formats, signature/hash rejection, protected
infrastructure, rollback, format pinning and damaged retained output. A local
export of the shipped signed core seed is also exercised. Native tests cover
family suggestions, ambiguous input, phone-app paths, documented GL.iNet model
exclusions and Arabic/English rendering. These do not prove live router import,
DNS routing, reboot persistence, or family-device coverage on real hardware.
