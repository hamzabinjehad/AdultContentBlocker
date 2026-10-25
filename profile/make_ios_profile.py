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
# Apple documents DenyListURLs as "no more than 500 URLs".
MAX_DENY_URLS = 500


def payload(ptype: str, identifier: str, name: str, description: str, **extra) -> dict:
    p = mac.payload(ptype, identifier, name, description, **extra)
    p["PayloadIdentifier"] = f"{ID_PREFIX}.{identifier}"
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


def p_web_filter(blocked_hosts: list[str]) -> dict:
    extra: dict = {"FilterType": "BuiltIn", "AutoFilterEnabled": True,
                   # Required for unsupervised iOS 16+; harmless when supervised.
                   "ContentFilterUUID": str(uuid.uuid4()).upper()}
    if blocked_hosts:
        extra["DenyListURLs"] = [f"https://{host}" for host in blocked_hosts]
    return payload(
        "com.apple.webcontent-filter", "webfilter", "Adult Website Filter",
        "Apple's built-in adult-content filter for Safari and apps that use it, plus the sites "
        "listed here. It does not classify images or videos.",
        **extra,
    )


def p_restrictions(allow_app_removal: bool) -> dict:
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


def build(args: argparse.Namespace) -> tuple[dict, str]:
    password = args.removal_password or mac.gen_password()
    content = [
        p_dns(args.doh, args.dns_addresses),
        p_web_filter(args.blocked_hosts),
        p_restrictions(args.allow_app_removal),
        p_removal_password(password),
    ]
    profile = {
        "PayloadType": "Configuration",
        "PayloadVersion": 1,
        "PayloadIdentifier": ID_PREFIX,
        "PayloadUUID": str(uuid.uuid4()).upper(),
        "PayloadDisplayName": args.display_name,
        "PayloadOrganization": mac.ORG,
        "PayloadDescription": (
            "Hisn protection for a supervised iPhone or iPad: family DNS, Apple's adult-website "
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
    ap.add_argument("--display-name", default="Hisn Phone Protection")
    ap.add_argument("--removal-password", default=None)
    ap.add_argument("--print-password", action="store_true",
                    help="Print the removal password. Do not, if the person it protects can read your terminal.")
    ap.add_argument("--allow-unreachable-resolver", action="store_true")
    ap.add_argument("--out", default="../dist/hisn-iphone.mobileconfig")
    args = ap.parse_args(argv)

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
    if len(args.blocked_hosts) > MAX_DENY_URLS:
        ap.error(f"{len(args.blocked_hosts)} sites; Apple's filter takes at most {MAX_DENY_URLS}")

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
