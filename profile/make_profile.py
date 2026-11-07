#!/usr/bin/env python3
"""
Hisn hardening profile generator.

Produces an unsigned macOS .mobileconfig with independent network and browser
policy controls. Generating a payload does not install it, prove that a browser
accepts it, or make a device impossible to bypass. Test effective policy before
describing a deployment as enforced.

    python3 make_profile.py --doh https://dns.hisn.app/dns-query \\
                            --out ../dist/hisn-hardening.mobileconfig

For a fleet, upload the MDM build instead of installing it by hand:

    python3 make_profile.py --mdm --organization-name "Example Institute" \\
                            --team-id ABCDE12345 --supervised \\
                            --out ../dist/hisn-example-mdm.mobileconfig

For a supervised fleet, `--maximum` turns on every managed lock (and refuses
the weakening flags):

    python3 make_profile.py --mdm --maximum --organization-name "Example Institute" \\
                            --team-id ABCDE12345 \\
                            --out ../dist/hisn-example-maximum.mobileconfig

`--mdm` implies `--managed`, omits the local removal password (the MDM owns
removal), and still needs `--supervised` for supervision-only keys. `--team-id`
adds the profile-managed content filter and the system-extension policy that
makes the signed filter non-removable on macOS 15+; without it the filter stays
app-managed. See docs/MDM_DEPLOYMENT.md for the deployment and verification
steps.

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

# The app and the filter system extension it contains. These identifiers are
# part of the signed build, not deployment choices: the filter payload names
# the app as its plugin and the extension as its data provider, and the
# system-extension policy maps the same extension under the team ID that signs
# it. A profile with the wrong pair fails at the one thing it was installed for.
FILTER_APP_BUNDLE_ID = "app.hisn.Hisn"
FILTER_PROVIDER_BUNDLE_ID = "app.hisn.Hisn.HisnFilter"

# Bumped when the managed payload's keys change. The app shows it and treats a
# missing or older version as "the organization profile may be stale"; it is a
# compatibility marker, never proof that a payload is enforced.
PROFILE_VERSION = 2

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


def p_hisn_managed(organization_name: str, *, profile_layers: list[str] | None = None,
                   filter_managed: bool = False) -> dict:
    """
    Mark the app as managed by an organization.

    This is a custom settings payload for the app's own preference domain
    (`app.hisn.Hisn`). The app reads it with
    `CFPreferencesAppValueIsForced`; a `defaults write` cannot fake it. In
    managed mode the app hides local removal and weakening controls and shows
    the organization name. The payload does not make the profile unremovable:
    that is `PayloadRemovalDisallowed` plus MDM/account separation, and an
    administrator can still remove any local profile.

    The version and layer markers are informational, not enforcement. The app
    uses `HisnProfileVersion` to tell an older organization profile from the
    current one and `HisnFilterManaged` to avoid replacing a profile-managed
    filter with its own local configuration.
    """
    name = organization_name.strip() or "Your organization"
    return payload(
        "app.hisn.Hisn", "managed",
        "Hisn Managed Mode",
        f"Marks this Mac as managed by {name}. Local removal and weakening controls in Hisn are disabled.",
        HisnManaged=True,
        OrganizationName=name,
        HisnProfileVersion=PROFILE_VERSION,
        HisnFilterManaged=filter_managed,
        HisnProtectedLayers=sorted(set(profile_layers or [])),
    )


def p_restrictions(*, lock_erase: bool = False) -> dict:
    """
    com.apple.applicationaccess.

    allowCloudPrivateRelay: macOS 12.0+, and — verified against Apple's schema —
    it does NOT require supervision on macOS. This one matters more than it
    looks: iCloud Private Relay tunnels Safari traffic and DNS away from the
    system resolver, which silently defeats DNS-level filtering entirely.

    allowEraseContentAndSettings: macOS 12.0+, also not supervision-gated on
    macOS. It removes the Erase All Content and Settings button; it does not
    stop Recovery or a DFU restore. It is an MDM-build control: a fleet should
    not let a daily user wipe the managed Mac from the account it was set up to
    protect, while a personal profile has no business hiding the erase button.

    Note what is deliberately absent: allowVPNCreation is iOS-only
    (macOS: n/a). There is no profile key that stops a Mac user adding a VPN.
    VPN traffic is handled by the socket-level content filter instead, which
    sees flows before they enter a tunnel.
    """
    settings: dict = {"allowCloudPrivateRelay": False}
    if lock_erase:
        settings["allowEraseContentAndSettings"] = False
    return payload(
        "com.apple.applicationaccess", "restrictions",
        "Restrictions",
        "Disables iCloud Private Relay, which would otherwise bypass DNS filtering."
        + (" Also removes Erase All Content and Settings." if lock_erase else ""),
        **settings,
    )


def designated_requirement(team_id: str) -> str:
    """The default designated requirement for a Developer ID filter extension.

    The string is what macOS embeds in the provider's signature check. It must
    name the extension bundle and the team that signed it; a copy-pasted
    requirement with another team's ID makes the filter fail to start, which is
    worse than no filter payload because the profile looks complete. Operators
    who sign differently can pass the exact `codesign -d -r-` output instead.
    """
    return (
        f'identifier "{FILTER_PROVIDER_BUNDLE_ID}" and anchor apple generic '
        "and certificate 1[field.1.2.840.113635.100.6.2.6] "
        "and certificate leaf[field.1.2.840.113635.100.6.1.13] "
        f'and certificate leaf[subject.OU] = "{team_id}"'
    )


def validate_team_id(team_id: str) -> str:
    value = team_id.strip()
    if not re.fullmatch(r"[A-Z0-9]{10}", value):
        raise ValueError("team ID must be exactly 10 uppercase letters or digits")
    return value


def validate_filter_requirement(requirement: str, team_id: str) -> str:
    value = requirement.strip()
    if not value:
        raise ValueError("filter requirement must not be empty")
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        raise ValueError("filter requirement must not contain control characters")
    if FILTER_PROVIDER_BUNDLE_ID not in value:
        raise ValueError(
            "filter requirement must name the Hisn filter extension "
            f"({FILTER_PROVIDER_BUNDLE_ID})")
    if team_id and team_id not in value:
        raise ValueError("filter requirement must name the signing team ID")
    return value


def p_managed_filter(requirement: str, *, safari_private: bool = True) -> dict:
    """
    com.apple.webcontent-filter, plugin type (macOS 10.15+).

    The profile, not the app, tells the system to run the Hisn filter: it starts
    without Hisn being opened, applies to every user, and cannot be switched off
    from the person's copy of the app. That is the difference between a filter
    the app re-asserts and a filter the management layer owns.

    `PluginBundleID` is the containing app; `FilterDataProviderBundleIdentifier`
    is the system extension inside it. `FilterBrowsers` is iOS-only, so macOS
    uses `FilterSockets` and the designated requirement above.

    `SafariHistoryRetentionEnabled` (macOS 26+) makes Safari keep history and
    therefore refuse private browsing and history clearing. It is set
    explicitly so a build can turn it off without dropping the filter; Apple's
    own default for the key is true.
    """
    settings: dict = {
        "FilterType": "Plugin",
        "UserDefinedName": "Hisn Content Protection",
        "PluginBundleID": FILTER_APP_BUNDLE_ID,
        "FilterDataProviderBundleIdentifier": FILTER_PROVIDER_BUNDLE_ID,
        "FilterDataProviderDesignatedRequirement": requirement,
        "FilterSockets": True,
        "FilterPackets": False,
        "FilterGrade": "firewall",
        "SafariHistoryRetentionEnabled": safari_private,
    }
    return payload(
        "com.apple.webcontent-filter", "filter",
        "Hisn Managed Filter",
        "Runs the Hisn system filter under management control. On macOS 26 and later it also disables Safari private browsing and history clearing.",
        **settings,
    )


def p_system_extension_policy(team_id: str, *,
                              lock_user_overrides: bool = False) -> dict:
    """
    com.apple.system-extension-policy (macOS 10.15+, user-approved MDM).

    `AllowedSystemExtensions` lets the signed Hisn filter load without a second
    manual approval. `NonRemovableSystemExtensions` (macOS 15+) is the stronger
    key: with SIP enabled the extension cannot be disabled or uninstalled, and
    `NonRemovableFromUISystemExtensions` removes the System Settings/Finder
    route as well. Both need the extension to be allowed and signed by the team
    in the map.

    `AllowUserOverrides=false` stops the person approving other system
    extensions; it is opt-in because a Mac can have legitimate unrelated
    extensions. Without it, Hisn is non-removable but other extensions may
    still be approved.
    """
    def approved() -> dict[str, list[str]]:
        return {team_id: [FILTER_PROVIDER_BUNDLE_ID]}

    settings: dict = {
        "AllowedSystemExtensions": approved(),
        "NonRemovableSystemExtensions": approved(),
        "NonRemovableFromUISystemExtensions": approved(),
    }
    if lock_user_overrides:
        settings["AllowUserOverrides"] = False
    return payload(
        "com.apple.system-extension-policy", "systemext",
        "Hisn System Extension",
        "Allows the Hisn filter extension to load and makes it non-removable on macOS 15 and later.",
        **settings,
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


def build(args: argparse.Namespace) -> tuple[dict, str | None]:
    """Build a profile. An MDM build has no local removal password.

    A removal password is a manual-install concept: it raises the cost of
    dragging the profile out of System Settings. An MDM-delivered profile is
    removed by the MDM, and a password embedded in the uploaded artifact would
    only become one more secret to lose. `--mdm` therefore omits the payload.

    A `--team-id` on an MDM build adds the two payloads that make the filter a
    managed layer rather than an app-managed one: the web content filter and
    the system-extension policy that makes it non-removable. Without a team ID
    the profile is still useful, but it does not carry either, and the app says
    so instead of implying that the filter is managed.
    """
    validate_extension_source(args.extension_id, args.update_url)
    mdm = bool(getattr(args, "mdm", False))
    organization_name = getattr(args, "organization_name", "Your organization")
    managed = bool(getattr(args, "managed", False)) or mdm
    team_id = (getattr(args, "team_id", "") or "").strip()
    if team_id:
        team_id = validate_team_id(team_id)
    if team_id and not mdm:
        raise ValueError("--team-id builds an MDM filter payload; pass --mdm")
    requirement = getattr(args, "filter_requirement", "") or ""
    if requirement and not team_id:
        raise ValueError("--filter-requirement needs --team-id")
    if requirement:
        requirement = validate_filter_requirement(requirement, team_id)
    include_filter = bool(mdm and team_id and
                          not getattr(args, "without_managed_filter", False))
    if include_filter and not requirement:
        requirement = designated_requirement(team_id)
    if getattr(args, "lock_system_extensions", False) and not include_filter:
        raise ValueError("--lock-system-extensions needs the managed filter; "
                         "pass --team-id without --without-managed-filter")
    lock_erase = bool(mdm and not getattr(args, "allow_erase", False))
    safari_private = not getattr(args, "no_safari_private_lock", False)
    removal_password = None if mdm else (args.removal_password or gen_password())

    layers = ["dns", "private-relay", "browser-policy"]
    if lock_erase:
        layers.append("erase-lock")
    if include_filter:
        layers.extend(["filter", "system-extension-policy"])

    payloads = [
        p_dns(args.doh, args.dns_addresses, args.supervised),
        p_restrictions(lock_erase=lock_erase),
        *([p_managed_filter(requirement, safari_private=safari_private)]
          if include_filter else []),
        *([p_system_extension_policy(
            team_id, lock_user_overrides=bool(
                getattr(args, "lock_system_extensions", False)))]
          if include_filter else []),
        *([p_hisn_managed(organization_name, profile_layers=layers,
                          filter_managed=include_filter)] if managed else []),
        *([] if mdm else [p_removal_password(removal_password)]),
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

    description = (
        "Hisn content protection for deployment through an MDM. The MDM owns "
        "removal and can reinstall the profile; supervision-only keys are "
        "present only when the profile was built with --supervised."
        + (" It includes a profile-managed system filter and makes that "
           "extension non-removable on macOS 15 and later."
           if include_filter else
           " It has no managed filter payload; the app's own filter setup is "
           "still needed.")
        if mdm else
        "Hisn content protection. Installation is voluntary and separate "
        "from the app. Keep the removal password in your chosen recovery "
        "or escrow process; removal protection depends on platform and "
        "administrator/management permissions."
    )
    profile = {
        "PayloadType": "Configuration",
        "PayloadVersion": 1,
        "PayloadIdentifier": ID_PREFIX,
        "PayloadUUID": new_uuid(),
        "PayloadDisplayName": args.display_name,
        "PayloadDescription": description,
        "PayloadOrganization": (organization_name.strip() or ORG) if mdm else ORG,
        "PayloadScope": "System",
        "PayloadRemovalDisallowed": True,
        "PayloadContent": payloads,
    }
    return profile, removal_password


def build_layers(args: argparse.Namespace) -> list[tuple[str, dict, str | None]]:
    """Generate two independently removable artifacts; never install either.

    Network protection contains no app or extension dependency. Browser policy
    lives in a second profile so removing one layer does not remove the other.
    Generated passwords differ; an explicitly supplied escrow password is
    reused only at the operator's request. Preserve the old single profile path.

    The managed app payload travels with the network layer: it is the layer
    that marks the Mac as managed, so a split deployment must not lose it when
    the browser profile is removed. An MDM split has no local password in
    either profile; the MDM owns removal for both.
    """
    combined, network_password = build(args)
    mdm = bool(getattr(args, "mdm", False))
    layers = []
    network_types = {"com.apple.dnsSettings.managed", "com.apple.applicationaccess",
                     "app.hisn.Hisn", "com.apple.webcontent-filter",
                     "com.apple.system-extension-policy"}
    for slug, title in (("network", "Network Protection"), ("browsers", "Browser Protection")):
        password = None if mdm else (network_password if slug == "network" else
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
        if not mdm:
            removal = p_removal_password(password)
            removal["PayloadIdentifier"] = f"{ID_PREFIX}.{slug}.removalpw"
            content.append(removal)
        description = (f"Hisn {title.lower()} for deployment through an MDM. "
                       "Removal is controlled by the MDM enrollment; no local "
                       "removal password is embedded."
                       if mdm else
                       f"Hisn {title.lower()}. Installed and removed separately "
                       "from the app and the other profile. Recovery requires your "
                       "escrowed removal password and applicable administrator/"
                       "management permissions.")
        layer.update(PayloadIdentifier=f"{ID_PREFIX}.{slug}", PayloadUUID=new_uuid(),
                     PayloadDisplayName=f"{args.display_name} — {title}", PayloadContent=content,
                     PayloadDescription=description)
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
    ap.add_argument("--mdm", action="store_true",
                    help="Build for upload to an MDM: implies --managed, "
                         "requires --organization-name, omits the local removal "
                         "password, and leaves removal to the MDM enrollment. "
                         "Supervision-only keys still need --supervised.")
    ap.add_argument("--maximum", action="store_true",
                    help="Strongest managed preset. Requires --mdm and "
                         "--team-id; implies --supervised and "
                         "--lock-system-extensions, and refuses the weakening "
                         "flags (--allow-devtools, --allow-incognito, "
                         "--allow-erase, --no-safari-private-lock, "
                         "--without-managed-filter). Only for a supervised "
                         "fleet.")
    ap.add_argument("--managed", action="store_true",
                    help="Mark the Mac as managed by an organization. The app "
                         "hides local removal and weakening controls; the "
                         "profile still needs MDM/account separation and "
                         "PayloadRemovalDisallowed to be hard to remove.")
    ap.add_argument("--organization-name", default="Your organization",
                    help="Name shown in Hisn's managed banner (with --managed "
                         "or --mdm).")
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
    ap.add_argument("--team-id", default="",
                    help="Apple Developer team ID (10 uppercase letters or "
                         "digits). With --mdm it adds a profile-managed content "
                         "filter and a system-extension policy that makes the "
                         "Hisn filter non-removable on macOS 15 and later. "
                         "Without it the MDM profile has no filter payload.")
    ap.add_argument("--filter-requirement", default="",
                    help="Exact designated requirement for the Hisn filter "
                         "extension. Defaults to the Developer ID requirement "
                         "generated from --team-id; use the output of "
                         "`codesign -d -r-` when the signing differs.")
    ap.add_argument("--without-managed-filter", action="store_true",
                    help="Do not add the managed filter or extension policy "
                         "even when --team-id is supplied.")
    ap.add_argument("--lock-system-extensions", action="store_true",
                    help="Also stop the person approving system extensions "
                         "this profile does not allow. Requires the managed "
                         "filter; can break unrelated software.")
    ap.add_argument("--allow-erase", action="store_true",
                    help="Do not remove Erase All Content and Settings from an "
                         "MDM profile. By default an MDM build blocks it.")
    ap.add_argument("--no-safari-private-lock", action="store_true",
                    help="Keep Safari private browsing and history clearing "
                         "available in the managed filter. By default the "
                         "filter payload sets SafariHistoryRetentionEnabled "
                         "(macOS 26+; older systems may not apply it).")
    ap.add_argument("--out", default="../dist/hisn-hardening.mobileconfig")
    ap.add_argument("--print-password", action="store_true",
                    help="Print the removal password to stdout. Do NOT do this "
                         "on the end user's machine.")
    args = ap.parse_args()
    if args.mdm:
        if args.print_password:
            ap.error("--mdm profiles have no removal password; drop --print-password")
        if args.removal_password:
            ap.error("--mdm relies on MDM-controlled removal; drop --removal-password")
        if not args.organization_name.strip() or args.organization_name.strip() == "Your organization":
            ap.error("--mdm requires --organization-name so the managed banner "
                     "names the organization the person should contact")
    if args.team_id:
        try:
            args.team_id = validate_team_id(args.team_id)
        except ValueError as error:
            ap.error(str(error))
        if not args.mdm:
            ap.error("--team-id adds an MDM-delivered filter; pass --mdm")
    elif args.filter_requirement:
        ap.error("--filter-requirement needs --team-id")
    if args.team_id and args.filter_requirement:
        try:
            args.filter_requirement = validate_filter_requirement(
                args.filter_requirement, args.team_id)
        except ValueError as error:
            ap.error(str(error))
    if args.lock_system_extensions and (not args.team_id or args.without_managed_filter):
        ap.error("--lock-system-extensions needs the managed filter; pass "
                 "--team-id without --without-managed-filter")
    if args.maximum:
        if not args.mdm or not args.team_id:
            ap.error("--maximum needs --mdm and --team-id")
        conflicts = [name for name, value in (
            ("--allow-devtools", args.allow_devtools),
            ("--allow-incognito", args.allow_incognito),
            ("--allow-erase", args.allow_erase),
            ("--no-safari-private-lock", args.no_safari_private_lock),
            ("--without-managed-filter", args.without_managed_filter),
        ) if value]
        if conflicts:
            ap.error("--maximum conflicts with " + ", ".join(conflicts))
        args.supervised = True
        args.lock_system_extensions = True
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
    # point. Chrome/Edge/Firefox policy keys have no Safari equivalent, so on a
    # Safari-only machine the system DNS payload and the Private Relay switch
    # are real protection but DNS-level, and any VPN walks around them. A
    # profile-managed Hisn filter extends socket-level coverage to Safari; a
    # generated file still does not prove the extension loaded.
    include_filter = bool(args.mdm and args.team_id and
                          not args.without_managed_filter)
    print("\nSafari note: the Chrome/Edge/Firefox payloads do nothing on a "
          "Mac that only runs Safari. "
          + ("This build also carries the Hisn system filter, which sees "
             "Safari socket flows once the signed extension is running; that "
             "is not proven by generating the file. "
             if include_filter else
             "What covers Safari here is the system DNS payload and the "
             "Private Relay restriction. ")
          + "DNS cannot classify individual encrypted posts/videos and tunnels "
            "can bypass it. Verify effective protection on a test Mac.",
          file=sys.stderr)

    if args.mdm:
        layers = ["DNS", "Private Relay", "browser policy"]
        if not args.allow_erase:
            layers.append("erase lock")
        if include_filter:
            layers.extend(["managed filter", "non-removable extension"])
            if not args.no_safari_private_lock:
                layers.append("Safari private-browsing lock (macOS 26+)")
        print("Layers in this build: " + ", ".join(layers) + ".",
              file=sys.stderr)
        print("MDM build: no removal password is embedded. Upload the "
              "owner-readable file to the MDM, scope it to the Macs that should "
              "be managed, and verify the forced HisnManaged preference on a "
              "test Mac. This command installed nothing.", file=sys.stderr)
        if args.maximum:
            print("Maximum preset: supervised DNS prohibition, non-removable "
                  "filter extension, no user system-extension approvals, erase "
                  "lock, Safari private-browsing lock, DevTools off and private "
                  "windows off. This is an operator assertion that the Macs are "
                  "supervised; verify it.", file=sys.stderr)
        if include_filter:
            print("Managed filter: app " + FILTER_APP_BUNDLE_ID + ", extension "
                  + FILTER_PROVIDER_BUNDLE_ID + ", requirement "
                  + ("from --filter-requirement" if args.filter_requirement
                     else "generated from --team-id") + ". The extension is "
                  "allowed and non-removable on macOS 15+; verify with "
                  "systemextensionsctl and the filter's own status.", file=sys.stderr)
            if not args.no_safari_private_lock:
                print("Safari: SafariHistoryRetentionEnabled is set, so macOS 26 "
                      "and later disables Safari private browsing and history "
                      "clearing. Older systems do not apply it; verify the "
                      "oldest macOS in the fleet.", file=sys.stderr)
            if args.lock_system_extensions:
                print("System extensions: AllowUserOverrides is false, so this "
                      "Mac cannot approve extensions the profile does not allow.",
                      file=sys.stderr)
        else:
            print("NOTE: no --team-id, so this profile has no managed content "
                  "filter and no non-removable extension policy. The app can "
                  "still enable its own filter, but the management layer does "
                  "not own it. Add --team-id after signing to close that gap.",
                  file=sys.stderr)
        if not args.allow_erase:
            print("Erase: Erase All Content and Settings is disabled for this "
                  "managed Mac. Recovery and a DFU restore still exist.",
                  file=sys.stderr)
    elif not args.print_password:
        print("Removal password generated but NOT printed. It is stored in "
              "plain text inside the owner-readable output profile; escrow "
              "that original secret before installation. Re-running generates "
              "a new secret and does not recover an already installed profile's password.",
              file=sys.stderr)

    if not args.supervised:
        if args.mdm:
            print("\nNOTE: unsupervised MDM build. ProhibitDisablement was "
                  "omitted because it requires supervision. A user-approved "
                  "enrollment that the MDM has not protected can be removed "
                  "in System Settings; use supervision and a profile-removal "
                  "setting for real resistance. No profile was installed by "
                  "this command.", file=sys.stderr)
        else:
            print("\nNOTE: unsupervised build. ProhibitDisablement was omitted "
                  "because it requires supervision. No profile or network filter "
                  "was installed by this command. Verify effective protection separately.",
                  file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
