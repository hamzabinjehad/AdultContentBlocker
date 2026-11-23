#!/usr/bin/env python3
"""
Hisn removal-resistant profile for a SUPERVISED iPhone or iPad.

    python3 make_ios_profile.py --out ../dist/hisn-iphone.mobileconfig
    python3 make_ios_profile.py --block-site reddit.com --block-site x.com

Installing Hisn from the App Store gives the app's own layers. On a personal,
unsupervised device the person can still delete the app, remove a profile,
erase the phone or add a VPN. Apple closes those routes only on a SUPERVISED
device. Supervision is done once with Apple Configurator on a Mac, erases the
device, and the supervising Mac (held by a guardian or accountability partner)
becomes the way back. This file writes the profile to install afterwards.

What the profile asks for, each key checked against Apple's schema
(github.com/apple/device-management):

  * Encrypted family DNS that cannot be switched off
    (com.apple.dnsSettings.managed, ProhibitDisablement: supervised only).
  * Apple's built-in adult-content web filter, plus the person's own sites
    (com.apple.webcontent-filter, BuiltIn + AutoFilterEnabled + DenyListURLs).
  * Restrictions (com.apple.applicationaccess, all supervised only): no app
    deletion, no "Erase All Content and Settings", no interactive profile or
    certificate installation, no VPN creation, no iCloud Private Relay, no
    alternative-marketplace or web app installs, no Safari private browsing
    (iOS 26+), no explicit store content.
  * The profile cannot be removed without its removal password
    (PayloadRemovalDisallowed + com.apple.profileRemovalPassword; both are
    honoured on supervised devices only).

WHAT THIS DOES NOT DO
---------------------
* It does not supervise the device or install anything. On an unsupervised
  device most of the keys above are ignored and the profile stays removable.
* Supervision added by hand in Apple Configurator has a 30-day provisional
  period during which the person can release it. A device restore from a
  computer (DFU/recovery) erases everything, including this profile; Find My
  Activation Lock is what makes that restore need the Apple Account password.
* "No app deletion" applies to every app on the device; iOS has no per-app
  switch without MDM.
* It is configuration. Test each layer on the device before relying on it.
"""

from __future__ import annotations

import argparse
import plistlib
import sys
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import make_profile as mac  # noqa: E402  (shared payload helpers and resolvers)

ID_PREFIX = "app.hisn.profile.ios"
# The edition the profile is for: Hisn, or Unrot (the general edition, built with
# `ios/generate_xcodeproj.py --general`). Names and identifiers only; the
# protection is the same. Unrot's own prefix lets both profiles sit on one device.
EDITIONS = {
    "hisn": {"prefix": ID_PREFIX, "name": "Hisn", "app": "app.hisn.mobile",
             "display": "Hisn Phone Protection", "out": "../dist/hisn-iphone.mobileconfig"},
    "general": {"prefix": "app.unrot.profile.ios", "name": "Unrot", "app": "app.unrot.mobile",
                "display": "Unrot Phone Protection", "out": "../dist/unrot-iphone.mobileconfig"},
}
_prefix = ID_PREFIX
_name = "Hisn"
# Apple documents DenyListURLs as "no more than 500 URLs".
MAX_DENY_URLS = 500

# Other browsers, by App Store bundle identifier. iOS has no "every browser"
# switch, so this is a list of known ones and nothing more: a browser missing
# here still opens. --deny-app-installation is what stops a new one arriving.
# Safari stays, because the web filter and Safari's own extensions cover it.
BROWSER_BUNDLE_IDS = sorted([
    "com.google.chrome.ios",
    "com.google.GoogleMobile",
    "org.mozilla.ios.firefox",
    "org.mozilla.ios.focus",
    "com.brave.ios.browser",
    "com.microsoft.msedge",
    "com.opera.OperaTouch",
    "com.opera.gx",
    "com.opera.OperaMini",
    "com.duckduckgo.mobile.ios",
    "com.alohabrowser.alohabrowser",
    "com.miketigas.OnionBrowser",
    "company.thebrowser.ArcMobile2",
    "com.ucweb.iphone.lowversion",
    "com.dolphin.browser.iphone",
])

# Short-video feeds on the web. Whole sites where the site is the feed, and
# the feed's address where the rest of the site stays useful. Apple's filter
# compares the start of the address when a page loads; moving between videos
# inside an already open page is not a page load and is not promised here.
SHORT_VIDEO_URLS = sorted([
    "https://tiktok.com",
    "https://www.tiktok.com",
    "https://m.tiktok.com",
    "https://youtube.com/shorts",
    "https://www.youtube.com/shorts",
    "https://m.youtube.com/shorts",
    "https://instagram.com/reels",
    "https://www.instagram.com/reels",
    "https://instagram.com/reel/",
    "https://www.instagram.com/reel/",
    "https://www.facebook.com/reel",
    "https://m.facebook.com/reel",
    "https://www.facebook.com/watch",
    "https://m.facebook.com/watch",
    "https://www.snapchat.com/spotlight",
    "https://snapchat.com/spotlight",
])


def payload(ptype: str, identifier: str, name: str, description: str, **extra) -> dict:
    p = mac.payload(ptype, identifier, name, description, **extra)
    p["PayloadIdentifier"] = f"{_prefix}.{identifier}"
    return p


def canonical_host(raw: str) -> str | None:
    """The same host rules as the Mac app, the extension and the iOS app."""
    s = raw.strip().lower()
    if not s:
        return None
    if "://" in s:
        s = s.split("://", 1)[1]
    for stop in "/?#":
        s = s.split(stop, 1)[0]
    s = s.rsplit("@", 1)[-1].split(":", 1)[0].rstrip(".")
    if s.startswith("www."):
        s = s[4:]
    labels = s.split(".")
    if not s or len(s) > 253 or len(labels) < 2:
        return None
    for label in labels:
        if not (1 <= len(label) <= 63) or label.startswith("-") or label.endswith("-"):
            return None
        if not all(c.isascii() and (c.isalnum() or c == "-") for c in label):
            return None
    if labels[-1].isdigit():
        return None
    return s


def p_dns(doh_url: str, addresses: list[str]) -> dict:
    dns: dict = {"DNSProtocol": "HTTPS", "ServerURL": doh_url, "ProhibitDisablement": True}
    if addresses:
        dns["ServerAddresses"] = addresses
    return payload(
        "com.apple.dnsSettings.managed", "dns", "Encrypted Family DNS",
        "Sends DNS to a filtering resolver over HTTPS. On a supervised device it cannot be "
        "switched off in Settings. DNS cannot see individual pages, posts or videos.",
        DNSSettings=dns,
    )


HISN_APP_ID = "app.hisn.mobile"
HISN_DNS_PROVIDER_ID = "app.hisn.mobile.dnsproxy"


def p_hisn_dns_proxy(app_id: str, provider_id: str) -> dict:
    # Apple's managed DNS proxy payload. On a supervised device the proxy is
    # always on and the person has no switch for it; the app must already be
    # installed and signed with the dns-proxy entitlement.
    return payload(
        "com.apple.dnsProxy.managed", "dnsproxy", f"{_name} DNS Filter",
        f"Keeps {_name}'s own DNS filter on for every app. Supervised devices only.",
        AppBundleIdentifier=app_id,
        ProviderBundleIdentifier=provider_id,
    )


def p_web_filter(blocked_hosts: list[str], extra_urls: list[str] | None = None) -> dict:
    extra: dict = {"FilterType": "BuiltIn", "AutoFilterEnabled": True,
                   # Required for unsupervised iOS 16+; harmless when supervised.
                   "ContentFilterUUID": str(uuid.uuid4()).upper()}
    urls = [f"https://{host}" for host in blocked_hosts]
    urls += [u for u in (extra_urls or []) if u not in urls]
    if urls:
        extra["DenyListURLs"] = urls
    return payload(
        "com.apple.webcontent-filter", "webfilter", "Adult Website Filter",
        "Apple's built-in adult-content filter for Safari and apps that use it, plus the sites "
        "listed here. It does not classify images or videos.",
        **extra,
    )


def p_restrictions(allow_app_removal: bool, extra: dict | None = None) -> dict:
    keys = {
        # Each is ignored on an unsupervised device (Apple: "supervised only").
        "allowAppRemoval": allow_app_removal,
        "allowEraseContentAndSettings": False,
        "allowUIConfigurationProfileInstallation": False,
        "allowVPNCreation": False,
        "allowCloudPrivateRelay": False,
        "allowMarketplaceAppInstallation": False,
        "allowWebDistributionAppInstallation": False,
        "allowSafariPrivateBrowsing": False,
        "allowExplicitContent": False,
    }
    # Optional, explicit hardening. These are not defaults: denying app
    # installation, account changes or passcode changes can leave a person
    # unable to install a needed app or recover an account.
    keys.update(extra or {})
    return payload(
        "com.apple.applicationaccess", "restrictions", "Removal Protection",
        "Supervised devices only: blocks deleting apps, erasing the device, installing other "
        "profiles or VPNs, iCloud Private Relay and private browsing.",
        **keys,
    )


def p_removal_password(password: str) -> dict:
    return payload(
        "com.apple.profileRemovalPassword", "removalpw", "Removal Password",
        "Removing this profile needs the password held by your guardian or partner.",
        RemovalPassword=password,
    )


def hardening(args: argparse.Namespace) -> dict:
    keys = {}
    if args.deny_account_modification: keys["allowAccountModification"] = False
    if args.deny_passcode_modification: keys["allowPasscodeModification"] = False
    if args.deny_app_installation: keys["allowAppInstallation"] = False
    if args.deny_app_clips: keys["allowAppClips"] = False
    if args.deny_enabling_restrictions: keys["allowEnablingRestrictions"] = False
    if args.force_automatic_date_time: keys["forceAutomaticDateAndTime"] = True
    if getattr(args, "block_browsers", False): keys["blockedAppBundleIDs"] = list(BROWSER_BUNDLE_IDS)
    if getattr(args, "block_safari", False): keys["allowSafari"] = False
    return keys


def short_video_urls(args: argparse.Namespace) -> list[str]:
    return list(SHORT_VIDEO_URLS) if getattr(args, "block_short_video", False) else []


def build(args: argparse.Namespace) -> tuple[dict, str]:
    global _prefix, _name
    edition = EDITIONS[getattr(args, "edition", "hisn")]
    _prefix, _name = edition["prefix"], edition["name"]
    password = args.removal_password or mac.gen_password()
    content = [
        (p_hisn_dns_proxy(args.hisn_app_id, args.hisn_dns_provider_id)
         if getattr(args, "hisn_dns", False) else p_dns(args.doh, args.dns_addresses)),
        p_web_filter(args.blocked_hosts, short_video_urls(args)),
        p_restrictions(args.allow_app_removal, hardening(args)),
    ]
    if not getattr(args, "no_removal_password", False):
        # With a password, iOS shows Remove and asks for it. Without one, a
        # supervised device shows no Remove at all; only the supervising Mac
        # can take the profile off.
        content.append(p_removal_password(password))
    profile = {
        "PayloadType": "Configuration",
        "PayloadVersion": 1,
        "PayloadIdentifier": _prefix,
        "PayloadUUID": str(uuid.uuid4()).upper(),
        "PayloadDisplayName": args.display_name,
        "PayloadOrganization": mac.ORG if _name == "Hisn" else _name,
        "PayloadDescription": (
            f"{_name} protection for a supervised iPhone or iPad: family DNS, Apple's adult-website "
            "filter and removal protection. Keep the removal password with your guardian or "
            "partner. On an unsupervised device most of these settings do not apply."),
        "PayloadScope": "System",
        "PayloadRemovalDisallowed": True,
        "PayloadContent": content,
    }
    return profile, password


def read_sites(args: argparse.Namespace) -> tuple[list[str], list[str]]:
    raw = list(args.block_sites or [])
    if args.block_sites_file:
        raw += [line for line in Path(args.block_sites_file).read_text().splitlines()
                if line.strip() and not line.lstrip().startswith("#")]
    hosts, rejected = [], []
    for entry in raw:
        host = canonical_host(entry)
        if host is None:
            rejected.append(entry.strip())
        elif host not in hosts:
            hosts.append(host)
    return sorted(hosts), rejected


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--resolver", choices=sorted(mac.RESOLVERS), default=mac.DEFAULT_RESOLVER)
    ap.add_argument("--block-site", dest="block_sites", action="append", default=[],
                    help="A site to block in Safari, besides Apple's adult filter. Repeatable.")
    ap.add_argument("--block-sites-file", help="One site per line; # starts a comment.")
    ap.add_argument("--allow-app-removal", action="store_true",
                    help="Leave app deletion allowed (iOS has no per-app switch without MDM).")
    ap.add_argument("--edition", choices=sorted(EDITIONS), default="hisn",
                    help="general = Unrot: its app and DNS filter identifiers, names and output file")
    ap.add_argument("--display-name", default=None)
    ap.add_argument("--removal-password", default=None)
    ap.add_argument("--print-password", action="store_true",
                    help="Print the removal password. Do not, if the person it protects can read your terminal.")
    ap.add_argument("--allow-unreachable-resolver", action="store_true")
    ap.add_argument("--maximum", action="store_true",
                    help="Turn on every supervised hardening switch below. It blocks new apps, "
                         "account and passcode changes and Content & Privacy edits; read the "
                         "recovery limits in docs/IPHONE_SUPERVISED.md first.")
    # Optional supervised-only hardening. Off by default because each can take
    # away a recovery path the person may need.
    ap.add_argument("--deny-account-modification", action="store_true",
                    help="Supervised only: block adding or removing accounts.")
    ap.add_argument("--deny-passcode-modification", action="store_true",
                    help="Supervised only: keep the device passcode as it is.")
    ap.add_argument("--deny-app-installation", action="store_true",
                    help="Supervised only: block installing new apps, including needed ones.")
    ap.add_argument("--deny-app-clips", action="store_true",
                    help="Supervised only: block App Clips.")
    ap.add_argument("--deny-enabling-restrictions", action="store_true",
                    help="Supervised only: block changes to Content & Privacy restrictions.")
    ap.add_argument("--force-automatic-date-time", action="store_true",
                    help="Supervised only: keep automatic date and time on.")
    ap.add_argument("--block-browsers", action="store_true",
                    help="Supervised only: hide and block the known browsers other than Safari. "
                         "A list, not a guarantee; pair it with --deny-app-installation.")
    ap.add_argument("--hisn-dns", action="store_true",
                    help="Supervised only: use the installed Hisn app's DNS filter, always on, "
                         "instead of a public filtering resolver.")
    ap.add_argument("--hisn-app-id", default=None)
    ap.add_argument("--hisn-dns-provider-id", default=None)
    ap.add_argument("--no-removal-password", action="store_true",
                    help="Supervised only: leave out the removal password, so the phone shows no "
                         "Remove button and only the supervising Mac can remove the profile.")
    ap.add_argument("--block-safari", action="store_true",
                    help="Supervised only: hide Safari too, so a filtering browser app chosen by the "
                         "person is the only way onto the web. Links apps open in Safari stop working.")
    ap.add_argument("--block-short-video", action="store_true",
                    help="Add TikTok and the Shorts, Reels and Spotlight addresses to the web "
                         "filter. Covers page loads in Safari, not the apps.")
    ap.add_argument("--out", default=None)
    args = ap.parse_args(argv)
    edition = EDITIONS[args.edition]
    args.display_name = args.display_name or edition["display"]
    args.hisn_app_id = args.hisn_app_id or edition["app"]
    args.hisn_dns_provider_id = args.hisn_dns_provider_id or f"{args.hisn_app_id}.dnsproxy"
    args.out = args.out or edition["out"]
    if args.maximum:
        if args.allow_app_removal:
            ap.error("--maximum cannot be combined with --allow-app-removal")
        args.deny_account_modification = True
        args.deny_passcode_modification = True
        args.deny_app_installation = True
        args.deny_app_clips = True
        args.deny_enabling_restrictions = True
        args.force_automatic_date_time = True

    preset = mac.RESOLVERS[args.resolver]
    args.doh, args.dns_addresses = preset["doh"], preset["addresses"]
    if not args.allow_unreachable_resolver and not mac.resolver_is_reachable(args.doh):
        print(f"ERROR: {args.doh} does not resolve; a profile pointing at it would take the "
              "phone's DNS down. Pass --allow-unreachable-resolver if building offline.",
              file=sys.stderr)
        return 1

    args.blocked_hosts, rejected = read_sites(args)
    if rejected:
        ap.error("not a site: " + ", ".join(rejected))
    denied = len(args.blocked_hosts) + len(short_video_urls(args))
    if denied > MAX_DENY_URLS:
        ap.error(f"{denied} sites; Apple's filter takes at most {MAX_DENY_URLS}")

    profile, password = build(args)
    out = Path(args.out)
    mac.write_private(out, plistlib.dumps(profile, fmt=plistlib.FMT_XML))
    print(f"wrote {out}  ({len(profile['PayloadContent'])} payloads, "
          f"{len(args.blocked_hosts)} extra sites)", file=sys.stderr)
    if args.print_password:
        print(password)
    else:
        print("Removal password generated but NOT printed. It is inside the owner-readable "
              "profile file; give it to your guardian or partner before installing.",
              file=sys.stderr)
    print("\nThis profile only holds on a SUPERVISED device (Apple Configurator). Unsupervised, "
          "most settings are ignored and the profile can be removed. See docs/IPHONE_SUPERVISED.md.",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
