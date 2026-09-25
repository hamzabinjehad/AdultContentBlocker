#!/usr/bin/env python3
"""
Hisn hardening profile generator.

Produces a signed-ready macOS .mobileconfig that closes the network-level
bypasses a blocklist alone cannot touch: encrypted DNS overrides, iCloud
Private Relay, per-browser DNS-over-HTTPS, private browsing, and casual removal
of the profile itself.

    python3 make_profile.py --doh https://dns.hisn.app/dns-query \\
                            --out ../dist/hisn-hardening.mobileconfig

Every claim below was checked against Apple's own payload schemas
(github.com/apple/device-management). Where a control does NOT work without
supervision it is called out in the code rather than silently included.

WHAT THIS DOES AND DOES NOT DO
------------------------------
Without MDM supervision, a profile you install by hand can still be removed by
a *local administrator*. That is a hard platform limit, not a bug in this file.
`com.apple.profileRemovalPassword` raises the cost — removal then needs the
removal password AND admin credentials — but the real fix is architectural:
the person using the machine should not be an administrator, and should not
hold the removal password. See docs/THREAT_MODEL.md.
"""

from __future__ import annotations

import argparse
import plistlib
import secrets
import string
import sys
import uuid
from pathlib import Path

ORG = "Hisn"
ID_PREFIX = "app.hisn.profile"

# Public filtering resolvers that actually exist, need no account, and filter
# adult content by default.
#
# ── WHY THIS TABLE EXISTS ──────────────────────────────────────────────────
# The default used to be `dns.hisn.app`, which does not resolve — there is no
# such host. A profile built with it does not "fail to filter"; it points the
# whole machine at a nameserver that is not there and takes the network down.
# That is the worst possible failure for this product: the person disables the
# profile within a minute, having learned that the tool breaks their computer.
#
# `addresses` are the plain-IP resolvers used before DoH bootstraps. They must
# be the SAME service as the DoH endpoint, or the machine filters differently
# depending on which one answered.
RESOLVERS = {
    "cloudflare": {
        "doh": "https://family.cloudflare-dns.com/dns-query",
        "addresses": ["1.1.1.3", "1.0.0.3", "2606:4700:4700::1113"],
        "blurb": "Cloudflare Families — malware + adult content. Fast, free, no signup.",
    },
    "cleanbrowsing": {
        "doh": "https://doh.cleanbrowsing.org/doh/adult-filter/",
        "addresses": ["185.228.168.10", "185.228.169.11"],
        "blurb": "CleanBrowsing Adult filter — stricter, also forces SafeSearch.",
    },
    "adguard": {
        "doh": "https://family.adguard-dns.com/dns-query",
        "addresses": ["94.140.14.15", "94.140.15.16"],
        "blurb": "AdGuard Family — adult content + ads, forces SafeSearch.",
    },
}
DEFAULT_RESOLVER = "cloudflare"


def new_uuid() -> str:
    return str(uuid.uuid4()).upper()


def payload(ptype: str, identifier: str, name: str, description: str, **extra) -> dict:
    base = {
        "PayloadType": ptype,
        "PayloadVersion": 1,
        "PayloadIdentifier": f"{ID_PREFIX}.{identifier}",
        "PayloadUUID": new_uuid(),
        "PayloadDisplayName": name,
        "PayloadDescription": description,
        "PayloadEnabled": True,
    }
    base.update(extra)
    return base


# --------------------------------------------------------------------------- #
# Payload builders
# --------------------------------------------------------------------------- #

def p_dns(doh_url: str, addresses: list[str], supervised: bool) -> dict:
    """
    Force all DNS through our DoH resolver.

    com.apple.dnsSettings.managed — macOS 11.0+, manual install allowed.

    ProhibitDisablement is honoured on SUPERVISED devices only. We still emit
    it when supervision is available; on an unsupervised Mac the setting is
    accepted but not enforced, so the socket-level content filter in the app
    remains the load-bearing control.
    """
    dns: dict = {"DNSProtocol": "HTTPS", "ServerURL": doh_url}
    if addresses:
        dns["ServerAddresses"] = addresses
    if supervised:
        dns["ProhibitDisablement"] = True

    return payload(
        "com.apple.dnsSettings.managed", "dns",
        "Encrypted DNS",
        "Routes all DNS through the Hisn filtering resolver over HTTPS.",
        DNSSettings=dns,
    )


def p_removal_password(password: str) -> dict:
    """
    com.apple.profileRemovalPassword — macOS 10.7+, no supervision required,
    manual install allowed.

    Removing the profile then requires this password *and* admin credentials.
    The user must never see it: generate it, escrow it, forget it.
    """
    return payload(
        "com.apple.profileRemovalPassword", "removalpw",
        "Removal Password",
        "Requires a password before this profile can be removed.",
        RemovalPassword=password,
    )


def p_restrictions() -> dict:
    """
    com.apple.applicationaccess.

    allowCloudPrivateRelay: macOS 12.0+, and — verified against Apple's schema —
    it does NOT require supervision on macOS. This one matters more than it
    looks: iCloud Private Relay tunnels Safari traffic and DNS away from the
    system resolver, which silently defeats DNS-level filtering entirely.

    Note what is deliberately absent: allowVPNCreation is iOS-only
    (macOS: n/a). There is no profile key that stops a Mac user adding a VPN.
    VPN traffic is handled by the socket-level content filter instead, which
    sees flows before they enter a tunnel.
    """
    return payload(
        "com.apple.applicationaccess", "restrictions",
        "Restrictions",
        "Disables iCloud Private Relay, which would otherwise bypass DNS filtering.",
        allowCloudPrivateRelay=False,
    )


def p_system_settings() -> dict:
    """
    com.apple.systempreferences — hides the panes used to undo the setup.

    DisabledSystemSettings covers macOS 13+; DisabledPreferencePanes is the
    pre-13 spelling and is kept for older systems. Privacy & Security panes
    cannot be disabled by design, so Full Disk Access etc. stay reachable.

    Trade-off, on purpose: hiding Network settings means the user cannot fix
    their own Wi-Fi. Gate this behind --lock-settings rather than shipping it
    to everyone.
    """
    return payload(
        "com.apple.systempreferences", "syssettings",
        "System Settings Restrictions",
        "Hides the settings panes used to disable filtering.",
        DisabledSystemSettings=[
            "com.apple.Network-Settings.extension",
            "com.apple.NetworkExtensionSettingsUI.NESettingsUIExtension",
            "com.apple.Screen-Time-Settings.extension",
            "com.apple.Users-Groups-Settings.extension",
        ],
        DisabledPreferencePanes=[
            "com.apple.preference.network",
            "com.apple.preferences.configurationprofiles",
            "com.apple.preferences.users",
        ],
    )


CHROMIUM_POLICY = {
    # The important one. A browser doing its own DNS-over-HTTPS never consults
    # the system resolver, so the DNS payload above would be a no-op for it.
    "DnsOverHttpsMode": "off",
    "BuiltInDnsClientEnabled": False,
    # Stops the user pointing the browser at a proxy to escape the filter.
    "ProxySettings": {"ProxyMode": "system"},
    # DevTools can edit extension state and request headers, which is why this
    # is locked by default. It is a flag rather than a constant because the
    # person developing the extension needs the console on their own machine,
    # and a profile that makes the project undebuggable is one they will simply
    # not install — leaving every other control in this file unapplied too.
    "URLAllowlist": ["chrome://settings/help"],
    # A guest window runs no extensions, and a new profile has none installed
    # until the store force-installs one. Either is a fresh browser with the
    # page-text layer switched off, one menu away.
    "BrowserGuestModeEnabled": False,
    "BrowserAddPersonEnabled": False,
    # SafeSearch at the policy level: covers an allowlisted search engine,
    # which the extension's rewrite rules deliberately leave alone, and keeps
    # working if the extension is ever off. 2 = YouTube strict restricted mode.
    "ForceGoogleSafeSearch": True,
    "ForceYouTubeRestrict": 2,
}

CHROMIUM_DEVTOOLS_LOCK = {"DeveloperToolsAvailability": 2}   # 2 = disallowed

# Disabling private/incognito windows outright. This is the ONLY thing that
# closes the incognito hole for the extension: the extension declares
# `incognito: spanning`, so it runs in a private window only if the user has
# enabled it there — a determined person simply does not. Removing incognito
# means there is no private window to slip through. A flag rather than a
# constant because it is one of the two answers to the same question (the other
# is letting incognito exist and having the extension cover it), and the user
# chooses which.
CHROMIUM_INCOGNITO_BLOCK = {"IncognitoModeAvailability": 1}   # 1 = disabled


def chromium_extension_policy(extension_id: str, update_url: str) -> dict:
    """Force-install our extension and block every other one — for ANY Chromium.

    Force-installed means no Remove or Disable button; `"*": blocked` stops a
    proxy/"unblock" extension being added to route around it. Emitted only when
    an id is given, and only satisfiable once the extension is actually
    published (force-install pulls from an update URL, not a local folder) — the
    same caveat `docs/CHROME_ENFORCEMENT.md` states. Shared so Helium and the
    other forks the user actually runs get the same non-removable treatment as
    Chrome, not just DNS locks.
    """
    return {
        "ExtensionInstallForcelist": [f"{extension_id};{update_url}"],
        "ExtensionSettings": {
            extension_id: {
                "installation_mode": "force_installed",
                "update_url": update_url,
                "toolbar_pin": "force_pinned",
            },
            "*": {"installation_mode": "blocked"},
        },
    }


def p_chrome(extension_id: str, update_url: str,
             lock_devtools: bool = True, block_incognito: bool = True) -> dict:
    policy = dict(CHROMIUM_POLICY)
    # Google's own adult-site classifier for top-level navigations. It calls a
    # Google service, so it is set for Chrome alone — the forks strip or
    # replace Google services and would either ignore it or fail it.
    policy["SafeSitesFilterBehavior"] = 1
    if lock_devtools:
        policy.update(CHROMIUM_DEVTOOLS_LOCK)
    if block_incognito:
        policy.update(CHROMIUM_INCOGNITO_BLOCK)
    if extension_id:
        policy.update(chromium_extension_policy(extension_id, update_url))
    return payload("com.google.Chrome", "chrome", "Google Chrome Policy",
                   "Locks Chrome DNS, proxy, and extension settings.", **policy)


# Every Chromium fork reads the SAME policy keys, just under its own bundle
# identifier. Naming only Chrome and Edge left the obvious escape open: install
# Brave, turn on its built-in DNS-over-HTTPS, and every DNS-layer control on
# this Mac — the hosts file, the managed resolver, all of it — is bypassed,
# because the browser stopped asking the system to resolve anything.
#
# These are the Chromium browsers a person actually reaches for. It is not a
# complete list and cannot be: a fork published tomorrow needs adding here. That
# is the structural weakness of naming browsers one at a time, and the reason
# the socket-level filter — which never asks which browser it is — is the layer
# that actually closes this row of the threat model.
CHROMIUM_FAMILY = [
    ("com.brave.Browser", "brave", "Brave"),
    ("com.vivaldi.Vivaldi", "vivaldi", "Vivaldi"),
    ("com.operasoftware.Opera", "opera", "Opera"),
    ("company.thebrowser.Browser", "arc", "Arc"),
    ("net.imput.helium", "helium", "Helium"),
    ("org.chromium.Chromium", "chromium", "Chromium"),
    ("com.google.Chrome.beta", "chrome-beta", "Chrome Beta"),
    ("com.google.Chrome.dev", "chrome-dev", "Chrome Dev"),
    ("com.google.Chrome.canary", "chrome-canary", "Chrome Canary"),
    # The newer Chromium browsers. Each ships its own DoH client and reads the
    # same policy keys under its own bundle id.
    ("com.operasoftware.OperaGX", "opera-gx", "Opera GX"),
    ("org.chromium.Thorium", "thorium", "Thorium"),
    ("ru.yandex.desktop.yandex-browser", "yandex", "Yandex Browser"),
    ("ai.perplexity.comet", "comet", "Comet"),
    ("company.thebrowser.dia", "dia", "Dia"),
    ("com.openai.atlas", "atlas", "ChatGPT Atlas"),
]


def p_chromium_family(extension_id: str = "", update_url: str = "",
                      lock_devtools: bool = True,
                      block_incognito: bool = True) -> list[dict]:
    """DNS and proxy locks for every Chromium fork we know the id of.

    With the same force-install policy as Chrome when an id is given. This used
    to be omitted on the theory that nobody runs the extension in a fork — false
    for the fork the user actually runs (Helium). Without it the extension is
    removable on that browser, which is the whole thing the profile is meant to
    prevent. The publish caveat is identical to Chrome's, so an id is passed
    only when there is something to force-install.
    """
    out = []
    for bundle_id, slug, name in CHROMIUM_FAMILY:
        policy = dict(CHROMIUM_POLICY)
        if lock_devtools:
            policy.update(CHROMIUM_DEVTOOLS_LOCK)
        if block_incognito:
            policy.update(CHROMIUM_INCOGNITO_BLOCK)
        if extension_id:
            policy.update(chromium_extension_policy(extension_id, update_url))
        out.append(payload(bundle_id, slug, f"{name} Policy",
                           f"Locks {name} DNS and proxy settings.", **policy))
    return out


def p_edge(extension_id: str, update_url: str,
           lock_devtools: bool = True, block_incognito: bool = True) -> dict:
    policy = dict(CHROMIUM_POLICY)
    policy["ForceBingSafeSearch"] = 2     # Edge's own policy: strict
    if lock_devtools:
        policy.update(CHROMIUM_DEVTOOLS_LOCK)
    if block_incognito:
        policy.update(CHROMIUM_INCOGNITO_BLOCK)
    if extension_id:
        policy.update(chromium_extension_policy(extension_id, update_url))
    return payload("com.microsoft.Edge", "edge", "Microsoft Edge Policy",
                   "Locks Edge DNS, proxy, and extension settings.", **policy)


def p_firefox(lock_devtools: bool = True, block_incognito: bool = True) -> dict:
    """
    Firefox reads enterprise policy from com.mozilla.firefox on macOS.
    `Locked: True` is what stops the user flipping it back in about:config.
    """
    return payload(
        "org.mozilla.firefox", "firefox", "Firefox Policy",
        "Locks Firefox DNS-over-HTTPS and private browsing.",
        DNSOverHTTPS={"Enabled": False, "Locked": True},
        DisablePrivateBrowsing=block_incognito,
        DisableDeveloperTools=lock_devtools,
        Proxy={"Mode": "system", "Locked": True},
        BlockAboutConfig=True,
    )


# --------------------------------------------------------------------------- #
# Assembly
# --------------------------------------------------------------------------- #

def gen_password(length: int = 40) -> str:
    alphabet = string.ascii_letters + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(length))


def resolver_is_reachable(doh_url: str) -> bool:
    """
    Does the DoH endpoint's hostname actually resolve?

    A profile pointing at a nameserver that does not exist does not filter
    badly — it takes the machine's DNS down entirely. This check is cheap and
    catches the one mistake that guarantees the profile is uninstalled within
    the minute. It is the same instinct as the blocklist pipeline's refusal to
    publish a list that collapsed: fail the build, never ship the broken thing.
    """
    import socket
    from urllib.parse import urlparse

    host = urlparse(doh_url).hostname
    if not host:
        return False
    try:
        socket.getaddrinfo(host, 443)
        return True
    except OSError:
        return False


def build(args: argparse.Namespace) -> tuple[dict, str]:
    removal_password = args.removal_password or gen_password()

    payloads = [
        p_dns(args.doh, args.dns_addresses, args.supervised),
        p_restrictions(),
        p_removal_password(removal_password),
        p_chrome(args.extension_id, args.update_url,
                 lock_devtools=not args.allow_devtools,
                 block_incognito=not args.allow_incognito),
        p_edge(args.extension_id, args.update_url,
               lock_devtools=not args.allow_devtools,
               block_incognito=not args.allow_incognito),
        p_firefox(lock_devtools=not args.allow_devtools,
                  block_incognito=not args.allow_incognito),
        *p_chromium_family(args.extension_id, args.update_url,
                           lock_devtools=not args.allow_devtools,
                           block_incognito=not args.allow_incognito),
    ]
    if args.lock_settings:
        payloads.append(p_system_settings())

    profile = {
        "PayloadType": "Configuration",
        "PayloadVersion": 1,
        "PayloadIdentifier": ID_PREFIX,
        "PayloadUUID": new_uuid(),
        "PayloadDisplayName": args.display_name,
        "PayloadDescription": (
            "Hisn content protection. This profile is intentionally difficult "
            "to remove. Removal requires the removal password held by your "
            "accountability partner."
        ),
        "PayloadOrganization": ORG,
        "PayloadScope": "System",
        "PayloadRemovalDisallowed": True,
        "PayloadContent": payloads,
    }
    return profile, removal_password


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--resolver", choices=sorted(RESOLVERS),
                    default=DEFAULT_RESOLVER,
                    help="Filtering resolver preset. "
                         + " | ".join(f"{k}: {v['blurb']}"
                                      for k, v in RESOLVERS.items()))
    ap.add_argument("--doh", default=None,
                    help="Override the preset's DoH endpoint with your own.")
    ap.add_argument("--dns-addresses", nargs="*", default=None,
                    help="Override the preset's plain-IP resolvers.")
    ap.add_argument("--allow-unreachable-resolver", action="store_true",
                    help="Build even if the resolver hostname does not resolve. "
                         "Only for building offline — a profile pointing at a "
                         "nonexistent resolver takes the machine's DNS down.")
    ap.add_argument("--extension-id", default="",
                    help="Chrome Web Store ID of the Hisn extension.")
    ap.add_argument("--update-url",
                    default="https://clients2.google.com/service/update2/crx")
    ap.add_argument("--display-name", default="Hisn Content Protection")
    ap.add_argument("--supervised", action="store_true",
                    help="Emit supervision-only keys (ProhibitDisablement).")
    ap.add_argument("--allow-devtools", action="store_true",
                    help="Leave browser DevTools enabled. Weakens the profile — "
                         "DevTools can edit extension state — but is what makes "
                         "the profile installable while you are still building "
                         "the extension.")
    ap.add_argument("--allow-incognito", action="store_true",
                    help="Leave private/incognito browsing available. By default "
                         "the profile disables it entirely — the only thing that "
                         "closes the incognito hole for the extension. Pass this "
                         "if you instead want incognito to exist and rely on the "
                         "extension covering it once enabled there.")
    ap.add_argument("--lock-settings", action="store_true",
                    help="Also hide Network/Users/Screen Time panes. "
                         "Warning: the user can no longer fix their own Wi-Fi.")
    ap.add_argument("--removal-password", default=None,
                    help="Use a specific removal password instead of a random one.")
    ap.add_argument("--out", default="../dist/hisn-hardening.mobileconfig")
    ap.add_argument("--print-password", action="store_true",
                    help="Print the removal password to stdout. Do NOT do this "
                         "on the end user's machine.")
    args = ap.parse_args()

    # Resolve the preset, letting explicit flags override either half.
    preset = RESOLVERS[args.resolver]
    args.doh = args.doh or preset["doh"]
    if args.dns_addresses is None:
        args.dns_addresses = preset["addresses"]

    if not resolver_is_reachable(args.doh):
        print(f"ERROR: {args.doh} does not resolve.\n"
              "A profile pointing at a nameserver that is not there does not "
              "filter badly — it takes the machine's DNS down completely.\n"
              "Pick a --resolver preset, or pass "
              "--allow-unreachable-resolver if you are building offline.",
              file=sys.stderr)
        if not args.allow_unreachable_resolver:
            return 1

    profile, removal_password = build(args)

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(plistlib.dumps(profile, fmt=plistlib.FMT_XML))

    print(f"wrote {out}  ({out.stat().st_size:,} bytes, "
          f"{len(profile['PayloadContent'])} payloads)", file=sys.stderr)
    print(f"resolver: {args.doh}  ({', '.join(args.dns_addresses)})",
          file=sys.stderr)

    # Safari is not covered by the browser payloads above, and saying so is the
    # point. Chrome/Edge/Firefox policy keys have no Safari equivalent that
    # works on an unsupervised Mac, so on a Safari-only machine what this
    # profile actually delivers is the system DNS payload plus the Private
    # Relay switch — real protection, but DNS-level, and any VPN walks around
    # it. Claiming browser-level coverage here would be the "looks protected,
    # is not" failure the threat model rates worst.
    print("\nSafari note: the Chrome/Edge/Firefox payloads do nothing on a "
          "Mac that only runs Safari. What covers Safari here is the system "
          "DNS payload and the Private Relay switch — both real, both "
          "defeated by a VPN. The socket-level content filter is the only "
          "layer that closes that, and it needs a paid Apple Developer team.",
          file=sys.stderr)

    if args.print_password:
        print(removal_password)
    else:
        print("Removal password generated but NOT printed. Re-run with "
              "--print-password to escrow it, or pass --removal-password to "
              "supply one from your escrow service.", file=sys.stderr)

    if not args.supervised:
        print("\nNOTE: unsupervised build. ProhibitDisablement was omitted "
              "because macOS ignores it without supervision. The socket-level "
              "content filter is doing the real work in this configuration.",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
