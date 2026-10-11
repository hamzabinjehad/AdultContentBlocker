#!/usr/bin/env python3
"""
Hisn hardening profile generator.

Produces an unsigned macOS .mobileconfig with independent network and browser
policy controls. Generating a payload does not install it, prove that a browser
accepts it, or make a device impossible to bypass. Test effective policy before
describing a deployment as enforced.

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
import copy
import ipaddress
import os
import plistlib
import re
import secrets
import string
import sys
import uuid
from pathlib import Path
from urllib.parse import urlparse

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
    Configure system DNS to use a filtering DoH resolver.

    com.apple.dnsSettings.managed — macOS 11.0+, manual install allowed.

    ProhibitDisablement requires supervision. It is omitted unless requested;
    an unsupervised profile is not proof of non-disableable DNS. Applications,
    tunnels and other networks can need additional verified protections.
    """
    dns: dict = {"DNSProtocol": "HTTPS", "ServerURL": doh_url}
    if addresses:
        dns["ServerAddresses"] = addresses
    if supervised:
        dns["ProhibitDisablement"] = True

    return payload(
        "com.apple.dnsSettings.managed", "dns",
        "Encrypted DNS",
        "Configures system DNS to use a filtering resolver over HTTPS. Does not inspect individual encrypted posts or videos.",
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
    # Only the admin-installed browser link (install_native_host.sh) is used.
    # A user-level host manifest is the user's own file: point it at a script
    # that answers "no lock, allow everything" and the extension believes it —
    # the browser checks the user's folder first. With this off, answering as
    # the app needs an administrator.
    "NativeMessagingUserLevelHosts": False,
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
    """Request managed installation on browsers that accept these policies.

    Publication, effective policy and removal behavior must be verified on each
    target browser. The candidate catalog is not evidence of universal fork
    support. Omitting the ID preserves unpacked-development installation.
    """
    validate_extension_source(extension_id, update_url)
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


# Candidate Chromium policy domains. Forks can change their policy engine,
# branding, update services and supported keys. A matching payload or compiled
# policy name alone is not proof that policy is accepted and enforced. Keep
# targets selectable and require effective-policy checks for each deployment.
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
    # Additional candidates; no native-host registration or deployment proof
    # is implied for these products.
    ("com.operasoftware.OperaGX", "opera-gx", "Opera GX"),
    ("org.chromium.Thorium", "thorium", "Thorium"),
    ("ru.yandex.desktop.yandex-browser", "yandex", "Yandex Browser"),
    ("ai.perplexity.comet", "comet", "Comet"),
    ("company.thebrowser.dia", "dia", "Dia"),
    ("com.openai.atlas", "atlas", "ChatGPT Atlas"),
]

# App/native-host support is narrower than the policy-candidate catalog above.
# These domains have an explicit matching native registration in Swift and the
# shell installer. Edge's policy domain is not its macOS application bundle ID.
# More policy payloads are not evidence that a fork accepts them in practice.
NATIVE_LINKED_BROWSER_IDS = {
    "com.google.Chrome", "com.google.Chrome.beta", "com.google.Chrome.dev",
    "com.google.Chrome.canary", "com.microsoft.Edge", "com.brave.Browser",
    "com.vivaldi.Vivaldi", "com.operasoftware.Opera", "company.thebrowser.Browser",
    "org.chromium.Chromium", "net.imput.helium",
}
BROWSER_POLICY_IDS = {"com.google.Chrome", "com.microsoft.Edge", "org.mozilla.firefox",
                      *(entry[0] for entry in CHROMIUM_FAMILY)}


def validate_extension_source(extension_id: str, update_url: str) -> None:
    """Validate packaging inputs, not publication or effective policy.

    A malformed ID or a non-HTTPS/credential-bearing URL must never create a
    blanket extension block while the promised forced extension cannot load.
    A syntactically valid ID still needs a real published, accepted update feed.
    """
    if not extension_id:
        return
    if not re.fullmatch(r"[a-p]{32}", extension_id):
        raise ValueError("extension ID must contain exactly 32 letters a–p")
    if ("\\" in update_url or
            any(character.isspace() or ord(character) < 32 or ord(character) == 127
                for character in update_url)):
        raise ValueError("extension update URL must not contain whitespace, backslashes or control characters")
    try:
        parsed = urlparse(update_url)
        # Accessing port also rejects malformed/non-numeric/out-of-range ports.
        parsed.port
        valid = (parsed.scheme == "https" and parsed.hostname
                 and parsed.username is None and parsed.password is None
                 and not parsed.fragment)
        if valid:
            hostname = parsed.hostname.rstrip(".")
            try:
                ipaddress.ip_address(hostname)
            except ValueError:
                ascii_hostname = hostname.encode("idna").decode("ascii")
                valid = (len(ascii_hostname) <= 253 and
                         all(re.fullmatch(r"[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?", label)
                             for label in ascii_hostname.split(".")))
    except (ValueError, UnicodeError):
        valid = False
    if not valid:
        raise ValueError("extension update URL must be HTTPS, without credentials or a fragment")


def p_chromium_family(extension_id: str = "", update_url: str = "",
                      lock_devtools: bool = True,
                      block_incognito: bool = True) -> list[dict]:
    """Emit requested controls for known candidate Chromium policy domains.

    Each browser still needs an accepted update source and effective-policy
    verification. These artifacts are not installation or enforcement proof.
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
        # Without this key Firefox on macOS reads none of the others: the
        # policies below were delivered, listed in System Settings, and ignored.
        EnterprisePoliciesEnabled=True,
        DNSOverHTTPS={"Enabled": False, "Locked": True},
        DisablePrivateBrowsing=block_incognito,
        DisableDeveloperTools=lock_devtools,
        Proxy={"Mode": "system", "Locked": True},
        BlockAboutConfig=True,
    )


# --------------------------------------------------------------------------- #
# Assembly
# --------------------------------------------------------------------------- #

def write_private(path: Path, data: bytes) -> None:
    """
    Write the profile readable by its owner only.

    The file carries the removal password in plain text, and was written 0644
    into the repo's dist/ — any account on the Mac, the one the lock is for
    included, could read the password off the disk and remove the profile.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "wb") as f:
        os.fchmod(f.fileno(), 0o600)   # an existing file keeps its old mode otherwise
        f.write(data)


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
    validate_extension_source(args.extension_id, args.update_url)
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
    selected = set(getattr(args, "browsers", None) or [])
    unknown = selected - BROWSER_POLICY_IDS
    if unknown:
        raise ValueError("unknown browser policy domains: " + ", ".join(sorted(unknown)))
    if selected:
        payloads = [p for p in payloads
                    if p["PayloadType"] not in BROWSER_POLICY_IDS or p["PayloadType"] in selected]

    profile = {
        "PayloadType": "Configuration",
        "PayloadVersion": 1,
        "PayloadIdentifier": ID_PREFIX,
        "PayloadUUID": new_uuid(),
        "PayloadDisplayName": args.display_name,
        "PayloadDescription": (
            "Hisn content protection. Installation is voluntary and separate "
            "from the app. Keep the removal password in your chosen recovery "
            "or escrow process; removal protection depends on platform and "
            "administrator/management permissions."
        ),
        "PayloadOrganization": ORG,
        "PayloadScope": "System",
        "PayloadRemovalDisallowed": True,
        "PayloadContent": payloads,
    }
    return profile, removal_password


def build_layers(args: argparse.Namespace) -> list[tuple[str, dict, str]]:
    """Generate two independently removable artifacts; never install either.

    Network protection contains no app or extension dependency. Browser policy
    lives in a second profile so removing one layer does not remove the other.
    Generated passwords differ; an explicitly supplied escrow password is
    reused only at the operator's request. Preserve the old single profile path.
    """
    combined, network_password = build(args)
    layers = []
    network_types = {"com.apple.dnsSettings.managed", "com.apple.applicationaccess"}
    for slug, title in (("network", "Network Protection"), ("browsers", "Browser Protection")):
        password = (network_password if slug == "network" else
                    (args.removal_password or gen_password()))
        layer = copy.deepcopy(combined)
        content = []
        for p in layer["PayloadContent"]:
            if p["PayloadType"] == "com.apple.profileRemovalPassword":
                continue
            belongs = p["PayloadType"] in network_types
            if belongs == (slug == "network"):
                p["PayloadIdentifier"] = p["PayloadIdentifier"].replace(ID_PREFIX, f"{ID_PREFIX}.{slug}", 1)
                content.append(p)
        removal = p_removal_password(password)
        removal["PayloadIdentifier"] = f"{ID_PREFIX}.{slug}.removalpw"
        content.append(removal)
        layer.update(PayloadIdentifier=f"{ID_PREFIX}.{slug}", PayloadUUID=new_uuid(),
                     PayloadDisplayName=f"{args.display_name} — {title}", PayloadContent=content,
                     PayloadDescription=f"Hisn {title.lower()}. Installed and removed separately from the app and the other profile. Recovery requires your escrowed removal password and applicable administrator/management permissions.")
        layers.append((slug, layer, password))
    return layers


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
    ap.add_argument("--browser", dest="browsers", action="append",
                    choices=sorted(BROWSER_POLICY_IDS),
                    help="Emit policy for this browser domain only; repeat for several. Default keeps the existing full candidate catalog; effective policy must still be verified.")
    ap.add_argument("--separate-layers", action="store_true",
                    help="Write separate network and browser profiles instead of one combined file. Does not install or apply them; each can remain after the app or the other profile is removed.")
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
    try:
        validate_extension_source(args.extension_id, args.update_url)
    except ValueError as error:
        ap.error(str(error))

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

    out = Path(args.out)
    artifacts = (build_layers(args) if args.separate_layers else
                 [("combined", *build(args))])
    for slug, profile, removal_password in artifacts:
        target = (out.with_name(f"{out.stem}-{slug}{out.suffix}")
                  if args.separate_layers else out)
        write_private(target, plistlib.dumps(profile, fmt=plistlib.FMT_XML))
        print(f"wrote {target}  ({target.stat().st_size:,} bytes, "
              f"{len(profile['PayloadContent'])} payloads)", file=sys.stderr)
        if args.print_password:
            print(f"{slug}: {removal_password}" if args.separate_layers else removal_password)
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
          "DNS payload and the Private Relay restriction. DNS cannot classify "
          "individual encrypted posts/videos and tunnels can bypass it. A "
          "separately authorized, signed network filter can add protection; "
          "these generated files do not prove that filter is installed or effective.",
          file=sys.stderr)

    if not args.print_password:
        print("Removal password generated but NOT printed. It is stored in "
              "plain text inside the owner-readable output profile; escrow "
              "that original secret before installation. Re-running generates "
              "a new secret and does not recover an already installed profile's password.",
              file=sys.stderr)

    if not args.supervised:
        print("\nNOTE: unsupervised build. ProhibitDisablement was omitted "
              "because it requires supervision. No profile or network filter "
              "was installed by this command. Verify effective protection separately.",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
