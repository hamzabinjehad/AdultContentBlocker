#!/usr/bin/env python3
"""Put every attached device at the strongest protection this Mac can apply,
then say plainly what is still open.

    python3 script/fortify.py audit                 # read-only state of Mac, Android, iPhone
    python3 script/fortify.py apply --android       # strict Private DNS + always-on VPN
    sudo python3 script/fortify.py apply --macos    # hosts merge + filtering DNS
    python3 script/fortify.py apply --ios           # build the iPhone profile, print the install path
    python3 script/fortify.py verify                # re-audit, non-zero when a critical layer is open
    python3 script/fortify.py report --lang ar      # markdown report for the three devices

This is an operator tool, not a protection layer of its own. It drives the
pieces the repository already ships (block_dns.sh, make_ios_profile.py,
verify_enforcement.sh) and the phone settings that are reachable over adb and
devicectl. It never reports a layer as enforced without a readback.

WHAT IT CANNOT DO
-----------------
* Telegram media travels over MTProto to Telegram's data centres, not over DNS.
  No host list can separate porn from a normal photo in Telegram. Keeping the
  app keeps that hole; only blocking the app closes it.
* X serves adult images from its own CDN (pbs.twimg.com), not from adult
  domains. `--x-media` cuts the images and keeps the text; leave it off and X's
  in-app "Hide sensitive content" setting is the only content control.
* An iPhone without supervision or MDM cannot be locked from this Mac: the
  profile is removable and Screen Time is changed with the device passcode.
  The real lock is a Screen Time passcode held by someone else.
* A VPN/tunnel, a different resolver, an administrator account or an Apple
  Account reset all defeat these layers. See docs/THREAT_MODEL.md.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass, field
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DIST = REPO / "dist" / "fortify"

# Family resolvers whose whole point is adult/infringing-content filtering.
# The audit checks the *advertised* resolver only; it cannot prove that a
# query actually reached it (cache, VPN, or a local override can intervene).
FILTERING_RESOLVERS = {
    "94.140.14.15": "AdGuard Family",
    "94.140.15.16": "AdGuard Family",
    "94.140.14.14": "AdGuard default (not family)",
    "1.1.1.3": "Cloudflare Families",
    "1.0.0.3": "Cloudflare Families",
    "185.228.168.9": "CleanBrowsing Family",
    "185.228.169.9": "CleanBrowsing Family",
    "185.228.168.10": "CleanBrowsing Adult",
    "185.228.169.11": "CleanBrowsing Adult",
}

ADB_CANDIDATES = [
    Path.home() / "Library/Android/sdk/platform-tools/adb",
    Path("/opt/homebrew/bin/adb"),
    Path("/usr/local/bin/adb"),
]

STATUS_LABEL = {
    "enforced": "enforced",
    "partial": "partial",
    "open": "OPEN",
    "unknown": "unknown",
}
STATUS_RANK = {"unknown": 0, "open": 1, "partial": 2, "enforced": 3}


class FortifyError(Exception):
    """A step that could not run, with the command that failed."""


@dataclass
class Check:
    id: str
    title: str
    status: str
    detail: str = ""
    fix: str = ""

    def as_dict(self) -> dict:
        return {
            "id": self.id,
            "title": self.title,
            "status": self.status,
            "detail": self.detail,
            "fix": self.fix,
        }


@dataclass
class Device:
    kind: str  # macos | android | ios
    name: str
    identifier: str
    checks: list = field(default_factory=list)
    notes: list = field(default_factory=list)

    def add(self, check: Check) -> None:
        self.checks.append(check)

    def status(self) -> str:
        if not self.checks:
            return "unknown"
        return min((c.status for c in self.checks), key=lambda s: STATUS_RANK[s])

    def critical_open(self) -> list:
        return [c for c in self.checks if c.status == "open" and c.id in CRITICAL.get(self.kind, ())]

    def as_dict(self) -> dict:
        return {
            "kind": self.kind,
            "name": self.name,
            "identifier": self.identifier,
            "status": self.status(),
            "checks": [c.as_dict() for c in self.checks],
            "notes": self.notes,
        }


# Layers whose absence means the device is not actually protected. A missing
# layer here makes `verify` exit non-zero; an unknown one does not, because
# "could not read" is not the same as "confirmed open".
CRITICAL = {
    "macos": {"domain_blocklist", "filtering_dns", "system_filter"},
    "android": {"hisn_vpn", "private_dns", "accessibility"},
    "ios": {"hisn_safari", "screen_time"},
}


class Runner:
    """Small subprocess wrapper so the tests can run the logic without a device."""

    def __init__(self, dry_run: bool = False):
        self.dry_run = dry_run
        self.commands: list = []

    def run(self, args, timeout: int = 60, check: bool = False, input_text: str | None = None):
        self.commands.append(list(args))
        if self.dry_run:
            return subprocess.CompletedProcess(args, 0, "", "")
        try:
            proc = subprocess.run(
                [str(a) for a in args],
                capture_output=True,
                text=True,
                timeout=timeout,
                input=input_text,
            )
        except FileNotFoundError as exc:
            raise FortifyError(f"not found: {args[0]}") from exc
        except subprocess.TimeoutExpired as exc:
            raise FortifyError(f"timed out after {timeout}s: {' '.join(map(str, args))}") from exc
        if check and proc.returncode != 0:
            raise FortifyError(
                f"command failed ({proc.returncode}): {' '.join(map(str, args))}\n"
                f"{proc.stderr.strip()[:500]}"
            )
        return proc

    def text(self, args, timeout: int = 60, default: str = "") -> str:
        try:
            proc = self.run(args, timeout=timeout)
        except FortifyError:
            return default
        return proc.stdout if proc.returncode == 0 else default


# --------------------------------------------------------------------------- macos


VERIFY_LINE = re.compile(r"^\s+([\u25cf\u25d0\u25cb])\s+(\w+)\s+(.+?)\s{2,}(.*)$")
VERIFY_STATUS = {"\u25cf": "enforced", "\u25d0": "partial", "\u25cb": "open"}


def parse_verify_output(text: str) -> list:
    """Turn `macos/verify_enforcement.sh` rows into Checks.

    The script's contract is three symbols and a two-column body; parsing it
    here keeps the tool honest instead of re-deriving the same facts.
    """
    checks = []
    for line in text.splitlines():
        match = VERIFY_LINE.match(line)
        if not match:
            continue
        symbol, word, title, detail = match.groups()
        status = VERIFY_STATUS[symbol]
        # "observed" is the script's word for enforced-by-evidence.
        if word.lower() not in ("observed", "partial", "open"):
            continue
        slug = re.sub(r"[^a-z0-9]+", "_", title.lower()).strip("_")
        checks.append(Check(slug, title.strip(), status, detail.strip()))
    return checks


def advertised_resolvers(runner: Runner) -> list:
    text = runner.text(["scutil", "--dns"], timeout=15)
    seen = []
    for line in text.splitlines():
        match = re.search(r"nameserver\[\d+\]\s*:\s*(\S+)", line)
        if match:
            address = match.group(1).split("%")[0]
            if address not in seen:
                seen.append(address)
    return seen


def hosts_entry_count(runner: Runner) -> int:
    text = runner.text(["wc", "-l", "/etc/hosts"], timeout=10)
    match = re.search(r"(\d+)", text)
    return int(match.group(1)) if match else 0


def audit_macos(runner: Runner) -> Device:
    device = Device("macos", "MacBook", "local")
    verify = runner.run([REPO / "macos/verify_enforcement.sh"], timeout=180)
    if verify.returncode != 0 and not verify.stdout.strip():
        device.notes.append(f"verify_enforcement.sh failed: {verify.stderr.strip()[:200]}")
    parsed = parse_verify_output(verify.stdout)
    by_id = {c.id: c for c in parsed}
    for check in parsed:
        device.add(check)

    if "domain_blocklist" in by_id:
        by_id["domain_blocklist"].detail = f"{hosts_entry_count(runner)} lines in /etc/hosts"

    resolvers = advertised_resolvers(runner)
    family = [r for r in resolvers if r in FILTERING_RESOLVERS
              and "not family" not in FILTERING_RESOLVERS[r]
              and "default" not in FILTERING_RESOLVERS[r]]
    device.add(Check(
        "filtering_dns",
        "Filtering resolver",
        "enforced" if family and len(family) == len(resolvers or [None]) else ("partial" if family else "open"),
        ", ".join(f"{r} ({FILTERING_RESOLVERS.get(r, 'not a family resolver')})" for r in resolvers)
        or "no resolver read",
        "sudo python3 script/fortify.py apply --macos",
    ))

    # The Hisn system extension is the only layer that acts on sockets rather
    # than names; verify_enforcement already reports it.
    ext = by_id.get("system_filter")
    if ext is None:
        device.add(Check("system_filter", "Content filter", "unknown", "not reported"))
    return device


# ------------------------------------------------------------------------- android


APP_ANDROID = "app.hisn.android"


def adb_path() -> str:
    found = shutil.which("adb")
    if found:
        return found
    for candidate in ADB_CANDIDATES:
        if candidate.exists():
            return str(candidate)
    raise FortifyError("adb not found (Android platform-tools)")


def parse_adb_devices(text: str) -> list:
    devices = []
    for line in text.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            model = ""
            for part in parts:
                if part.startswith("model:"):
                    model = part.split(":", 1)[1]
            devices.append((parts[0], model))
    return devices


def android_devices(runner: Runner) -> list:
    try:
        adb = adb_path()
    except FortifyError:
        return []
    proc = runner.run([adb, "devices", "-l"], timeout=30)
    return [(adb, serial, model) for serial, model in parse_adb_devices(proc.stdout)]


def audit_android(runner: Runner, adb: str, serial: str, model: str) -> Device:
    device = Device("android", model or serial, serial)
    shell = lambda *args, timeout=20: runner.text([adb, "-s", serial, "shell", *args], timeout=timeout)  # noqa: E731

    packages = shell("pm", "list", "packages")
    installed = f"package:{APP_ANDROID}" in packages
    version = ""
    if installed:
        package = shell("dumpsys", "package", APP_ANDROID)
        if "versionName=" in package:
            version = package.split("versionName=")[-1].split()[0]
    device.add(Check(
        "hisn_app", "Hisn installed", "enforced" if installed else "open",
        f"{APP_ANDROID} {version}".strip(),
    ))

    connectivity = shell("dumpsys", "connectivity", timeout=30)
    vpn = f"VPN:{APP_ANDROID}" in connectivity or "VPN CONNECTED" in connectivity and APP_ANDROID in connectivity
    device.add(Check(
        "hisn_vpn", "DNS-filter VPN", "enforced" if vpn else "open",
        "VPN connected" if vpn else "no Hisn VPN in dumpsys connectivity",
        "open Hisn on the phone and turn Protection on",
    ))

    accessibility = shell("settings", "get", "secure", "enabled_accessibility_services")
    guard = "TextGuardService" in accessibility
    device.add(Check(
        "accessibility", "Text/app guard", "enforced" if guard else "open",
        "TextGuardService enabled" if guard else "accessibility service off",
    ))

    usage = shell("appops", "get", APP_ANDROID, "GET_USAGE_STATS")
    device.add(Check(
        "usage_access", "Usage access", "enforced" if "allow" in usage else "open",
        usage.strip() or "not granted",
    ))

    mode = shell("settings", "get", "global", "private_dns_mode").strip()
    spec = shell("settings", "get", "global", "private_dns_specifier").strip()
    if mode == "hostname" and spec:
        dns_status, dns_detail = "enforced", f"strict DoT → {spec}"
    elif mode == "opportunistic":
        dns_status, dns_detail = "partial", f"opportunistic ({spec or 'no hostname'}) — falls back to plain DNS"
    else:
        dns_status, dns_detail = "open", f"mode={mode or 'off'}"
    device.add(Check("private_dns", "Private DNS", dns_status, dns_detail,
                     "python3 script/fortify.py apply --android"))

    always_on = shell("settings", "get", "secure", "always_on_vpn_app").strip()
    if always_on in ("null", "0", ""):
        always_on = ""
    device.add(Check(
        "always_on_vpn", "Always-on VPN", "enforced" if always_on == APP_ANDROID else "partial" if always_on else "open",
        always_on or "not set",
        "python3 script/fortify.py apply --android",
    ))
    return device


# ----------------------------------------------------------------------------- ios


def parse_devicectl_devices(payload: dict) -> list:
    devices = []
    for entry in payload.get("result", {}).get("devices", []):
        props = entry.get("hardwareProperties", {})
        conn = entry.get("connectionProperties", {})
        reality = props.get("reality", "")
        if reality != "physical":
            continue
        devices.append({
            "udid": props.get("udid") or entry.get("identifier", ""),
            "name": entry.get("deviceProperties", {}).get("name", "iPhone"),
            "model": props.get("productType", ""),
            "paired": conn.get("pairingState") == "paired",
        })
    return devices


def ios_devices(runner: Runner) -> list:
    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as handle:
        path = handle.name
    try:
        proc = runner.run(["xcrun", "devicectl", "list", "devices", "--json-output", path], timeout=60)
        if proc.returncode != 0:
            return []
        payload = json.loads(Path(path).read_text())
    except (FortifyError, OSError, ValueError):
        return []
    finally:
        Path(path).unlink(missing_ok=True)
    return parse_devicectl_devices(payload)


def installed_ios_apps(runner: Runner, udid: str):
    """Bundle ids, or None when the phone could not be read.

    The distinction matters: an empty set means "read, and Hisn is not there",
    None means "locked or disconnected". Reporting the second as missing would
    turn every locked phone into a false alarm.
    """
    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as handle:
        path = handle.name
    try:
        proc = runner.run(
            ["xcrun", "devicectl", "device", "info", "apps", "--device", udid, "--json-output", path],
            timeout=90,
        )
        if proc.returncode != 0:
            return None
        payload = json.loads(Path(path).read_text())
    except (FortifyError, OSError, ValueError):
        return None
    finally:
        Path(path).unlink(missing_ok=True)
    apps = set()
    for app in payload.get("result", {}).get("apps", []):
        bundle = app.get("bundleIdentifier")
        if bundle:
            apps.add(bundle)
    return apps


def audit_ios(runner: Runner, entry: dict) -> Device:
    device = Device("ios", entry["name"], entry["udid"])
    if not entry["paired"]:
        device.notes.append("not paired — unlock it and trust this Mac")
        device.add(Check("paired", "Pairing", "open", "device not paired"))
        return device
    apps = installed_ios_apps(runner, entry["udid"])
    if apps is None:
        device.notes.append("app list unreadable this run (locked or disconnected)")
        device.add(Check("hisn_safari", "Hisn Safari blocker", "unknown",
                         "could not read the app list — unlock the iPhone and retry"))
        device.add(Check("hisn_app", "Hisn full app", "unknown",
                         "could not read the app list"))
    else:
        safari = "app.hisn.safari" in apps
        full = "app.hisn.mobile" in apps
        device.add(Check(
            "hisn_safari", "Hisn Safari blocker", "enforced" if safari else "open",
            "app.hisn.safari installed" if safari else "not installed",
            "install the Hisn Safari build from Xcode",
        ))
        device.add(Check(
            "hisn_app", "Hisn full app", "enforced" if full else "open",
            "app.hisn.mobile installed" if full
            else "needs the Family Controls entitlement — not signable with this team",
        ))
    # Neither Screen Time nor an installed profile is readable from the Mac.
    device.add(Check(
        "screen_time", "Screen Time web filter", "unknown",
        "per-device setting; read it on the phone (Settings ▸ Screen Time ▸ Content & Privacy)",
    ))
    device.add(Check(
        "dns_profile", "DNS profile", "unknown",
        "cannot be read remotely; install the generated profile and confirm on the phone",
    ))
    return device


# --------------------------------------------------------------------------- apply


MACOS_SERVICES = ("Wi-Fi", "iPhone USB")
FAMILY_DNS = ("94.140.14.15", "94.140.15.16")  # AdGuard Family

EXTRAS_START = "# >>> hisn fortify extras >>>"
EXTRAS_END = "# <<< hisn fortify extras <<<"
EXTRAS_MARKER = "# >>> hisn safesearch begin >>>"

X_MEDIA_BLOCK = """# >>> Hisn fortify: X media cut (text stays, images and video do not)
0.0.0.0 pbs.twimg.com
0.0.0.0 video.twimg.com
0.0.0.0 abs.twimg.com
# <<< Hisn fortify: X media cut"""


def extras_text(runner: Runner) -> str:
    """The small blocks `block_dns.sh --hosts` adds around the domain list.

    The full list is 236,586 domains (473,172 hosts lines once every `www.`
    twin is written). That file is measured to slow every lookup, and the
    filtering resolver already covers it with wildcard semantics. These extras —
    SafeSearch addresses, DoH/Private Relay names, popular subdomains — are the
    part a resolver cannot fix, so they are what actually belongs in /etc/hosts.
    """
    proc = runner.run([REPO / "macos/block_dns.sh", "--hosts", "--dry-run"], timeout=600)
    match = re.search(r"wrote the block to (.+)", proc.stdout)
    if not match:
        raise FortifyError("block_dns.sh --dry-run did not report its preview path")
    preview = Path(match.group(1).strip())
    text = preview.read_text(errors="replace")
    index = text.find(EXTRAS_MARKER)
    if index < 0:
        raise FortifyError(f"{preview} has no SafeSearch section")
    return text[index:].rstrip() + "\n"


def apply_extras_hosts(text: str, path: Path = Path("/etc/hosts")) -> int:
    current = path.read_text(errors="replace")
    if EXTRAS_START in current:
        before, _, rest = current.partition(EXTRAS_START)
        _, _, after = rest.partition(EXTRAS_END)
        current = before.rstrip("\n") + "\n" + after.lstrip("\n")
    if not current.endswith("\n"):
        current += "\n"
    path.write_text(current + EXTRAS_START + "\n" + text + EXTRAS_END + "\n")
    return text.count("\n")


def active_network_service(runner: Runner) -> str | None:
    route = runner.text(["route", "-n", "get", "default"])
    match = re.search(r"interface:\s*(\w+)", route)
    interface = match.group(1) if match else ""
    order = runner.text(["networksetup", "-listnetworkserviceorder"])
    service = None
    for line in order.splitlines():
        heading = re.match(r"\(\d+\)\s+(.*)$", line.strip())
        if heading:
            service = heading.group(1).strip()
        if interface and f"Device: {interface})" in line:
            return service
    return None


def dns_services(runner: Runner) -> list:
    services = []
    active = active_network_service(runner)
    if active:
        services.append(active)
    listed = runner.text(["networksetup", "-listallnetworkservices"])
    for extra in MACOS_SERVICES:
        if extra in listed and extra not in services:
            services.append(extra)
    return services


def macos_plan(runner: Runner, full_hosts: bool = False, x_media: bool = False) -> list:
    steps = [[REPO / "macos/block_dns.sh", "--merge"]] if full_hosts else [["extras"]]
    for service in dns_services(runner):
        steps.append(["networksetup", "-setdnsservers", service, *FAMILY_DNS])
    steps.append(["dscacheutil", "-flushcache"])
    if x_media:
        steps.append(["hosts-x-media"])
    return steps


def apply_macos(runner: Runner, full_hosts: bool = False, x_media: bool = False) -> None:
    if os.geteuid() != 0:
        print("This step needs root. Re-run exactly:")
        print(f"  sudo {sys.executable} {Path(__file__).resolve()} apply --macos"
              + (" --full-hosts" if full_hosts else "")
              + (" --x-media" if x_media else ""))
        print("It adds SafeSearch, the encrypted-DNS/Private Relay blocks and the")
        print("popular subdomains to /etc/hosts, then points the active network")
        print("service at AdGuard Family DNS. Add --full-hosts to also merge the")
        print("236,586-domain list (warned: 473k lines, slower lookups).")
        return

    for step in macos_plan(runner, full_hosts, x_media):
        if step[0] == "extras":
            text = extras_text(runner)
            lines = apply_extras_hosts(text)
            print(f"  → {lines} lines of SafeSearch / DNS-bypass / subdomain blocks in /etc/hosts")
            continue
        if step[0] == "hosts-x-media":
            apply_x_media_hosts()
            print("  → X media hosts added")
            continue
        print(f"  → {' '.join(map(str, step))}")
        runner.run(step, timeout=1800, check=True)
    runner.run(["killall", "-HUP", "mDNSResponder"], timeout=15)
    print("Mac layers applied. Read the result with: python3 script/fortify.py audit")


X_MEDIA_START = "# >>> Hisn fortify: X media cut"
X_MEDIA_END = "# <<< Hisn fortify: X media cut"


def apply_x_media_hosts(path: Path = Path("/etc/hosts")) -> None:
    text = path.read_text(errors="replace")
    if X_MEDIA_START in text:
        before, _, rest = text.partition(X_MEDIA_START)
        _, _, after = rest.partition(X_MEDIA_END)
        text = before.rstrip("\n") + "\n" + after.lstrip("\n")
    if not text.endswith("\n"):
        text += "\n"
    path.write_text(text + X_MEDIA_BLOCK + "\n")


def private_dns_mode_for(vpn_active: bool) -> str:
    """Strict Private DNS and the Hisn VPN do not coexist (measured).

    `hostname` makes Android run DoT against the active network's resolver. With
    Hisn's VPN up that is 10.111.0.2, which does not answer DoT, so every lookup
    fails. With the VPN down, `hostname` is the setting that cannot fall back to
    plain DNS. Opportunistic + the AdGuard hostname is the best that works with
    the VPN on.
    """
    return "opportunistic" if vpn_active else "hostname"


def apply_android(runner: Runner) -> int:
    devices = android_devices(runner)
    if not devices:
        print("No Android device in `adb devices`.")
        return 2
    changed = 0
    for adb, serial, model in devices:
        print(f"Android {model or serial} ({serial}):")
        shell = lambda *args: runner.run([adb, "-s", serial, "shell", *args], timeout=30)  # noqa: E731
        connectivity = runner.text([adb, "-s", serial, "shell", "dumpsys", "connectivity"], timeout=30)
        vpn_active = f"VPN:{APP_ANDROID}" in connectivity
        # Measured on the SM-G990B2: `hostname` makes Android speak DoT to the
        # VPN's own resolver (10.111.0.2), which does not answer DoT, so every
        # lookup fails while the Hisn VPN is up. With the VPN as the resolver,
        # opportunistic + the AdGuard Family hostname is the working strict-ish
        # setting; without the VPN, `hostname` is correct.
        mode = private_dns_mode_for(vpn_active)
        shell("settings", "put", "global", "private_dns_mode", mode)
        shell("settings", "put", "global", "private_dns_specifier", "dns-family.adguard.com")
        # Always-on, but not lockdown: a dead VPN must not cut the phone off.
        shell("settings", "put", "secure", "always_on_vpn_app", APP_ANDROID)
        shell("settings", "put", "secure", "always_on_vpn_lockdown", "0")
        print(f"  private DNS → {mode} + dns-family.adguard.com; VPN → always-on (no lockdown)")
        if vpn_active:
            print("  (strict mode would point DoT at the tunnel and break DNS — measured)")
        changed += 1
    print(f"{changed} device(s) updated. Re-read with: python3 script/fortify.py audit")
    return 0


def apply_ios(runner: Runner) -> int:
    devices = ios_devices(runner)
    DIST.mkdir(parents=True, exist_ok=True)
    out = DIST / "Hisn-iPhone.mobileconfig"
    proc = runner.run([
        sys.executable, REPO / "profile/make_ios_profile.py",
        "--resolver", "adguard", "--maximum", "--out", str(out),
    ], timeout=120)
    if proc.returncode != 0:
        print(proc.stderr.strip() or "profile generation failed")
        return 1
    # The generator always invents a removal password and never prints it. On an
    # unsupervised phone iOS ignores that payload, so it would only look like a
    # lock while nobody holds the password. Strip it and say so: the real lock on
    # this phone is a Screen Time passcode the guardian types, not this file.
    import plistlib
    payload = plistlib.loads(out.read_bytes())
    payload["PayloadContent"] = [
        item for item in payload["PayloadContent"]
        if item.get("PayloadType") != "com.apple.profileRemovalPassword"
    ]
    payload["PayloadRemovalDisallowed"] = False
    out.write_bytes(plistlib.dumps(payload))
    print(f"Profile written: {out}")
    if devices:
        for entry in devices:
            print(f"  target: {entry['name']} ({entry['udid']})")
    print("""
No removal password is embedded: on an unsupervised iPhone iOS ignores it, and
a password nobody holds is a prop, not a lock. This profile is removable.

Install it on the iPhone (there is no remote install without MDM):
  1. AirDrop the file to the iPhone, or put it in iCloud Drive and open it.
  2. Settings ▸ Profile Downloaded ▸ Install ▸ enter the passcode ▸ Install.
  3. Settings ▸ General ▸ VPN & Device Management — confirm it is listed.
Then set the real lock on the phone, with the guardian present:
  * Screen Time ▸ Content & Privacy ▸ Content Restrictions ▸ Web Content ▸
    Limit Adult Websites.
  * Screen Time passcode: the guardian types it, not you. A passcode you know
    is a pause button, not a lock.
  * Safari ▸ Extensions: enable every Hisn blocker and allow it on all sites.
A supervised device (Apple Configurator, one-time erase) is the only way to make
the DNS profile non-removable and to keep the app-deletion and VPN switches on.""")
    return 0


# -------------------------------------------------------------------------- render


ARABIC = {
    "macos": "الماك بوك",
    "android": "الأندرويد",
    "ios": "الآيفون",
    "enforced": "مطبّق",
    "partial": "جزئي",
    "open": "مفتوح",
    "unknown": "غير معروف",
}

ARABIC_TITLES = {
    "account_split": "فصل الحسابات",
    "app_files": "ملفات التطبيق",
    "login_agent": "عامل التشغيل عند الدخول",
    "domain_blocklist": "قائمة حجب النطاقات",
    "safesearch_dns": "البحث الآمن عبر DNS",
    "dns_bypasses": "طرق الالتفاف على DNS",
    "popular_subdomains": "النطاقات الفرعية الشائعة",
    "browser_incognito": "التصفح المتخفي",
    "browser_doh": "DNS-over-HTTPS في المتصفح",
    "guest_new_profiles": "حسابات الضيف والملفات الجديدة",
    "safesearch_policy": "سياسة البحث الآمن",
    "hisn_extension": "إضافة حصن",
    "extension_link": "ربط الإضافة بالتطبيق",
    "screen_time_filter": "فلتر Screen Time",
    "icloud_private_relay": "iCloud Private Relay",
    "system_filter": "فلتر النظام",
    "filtering_dns": "خادم DNS الفلتر",
    "hisn_app": "تطبيق حصن",
    "hisn_vpn": "VPN تصفية DNS",
    "accessibility": "حارس النص والتطبيقات",
    "usage_access": "صلاحية استخدام التطبيقات",
    "private_dns": "Private DNS",
    "always_on_vpn": "VPN دائم التشغيل",
    "hisn_safari": "حاجب Safari من حصن",
    "screen_time": "فلتر الويب في Screen Time",
    "dns_profile": "ملف DNS",
    "paired": "الاقتران بالماك",
}


def render_markdown(devices: list, lang: str = "en") -> str:
    ar = lang == "ar"
    lines = []
    title = "تقرير التحصين" if ar else "Fortify report"
    lines.append(f"# {title} — {time.strftime('%Y-%m-%d %H:%M')}")
    lines.append("")
    for device in devices:
        heading = ARABIC.get(device.kind, device.kind) if ar else device.kind
        lines.append(f"## {heading} · {device.name}")
        lines.append("")
        lines.append("| | " + ("الطبقة" if ar else "Layer") + " | " + ("الحالة" if ar else "State") + " | " + ("التفصيل" if ar else "Detail") + " |")
        lines.append("|---|---|---|---|")
        for check in device.checks:
            symbol = {"enforced": "●", "partial": "◐", "open": "○", "unknown": "?"}[check.status]
            label = ARABIC[check.status] if ar else STATUS_LABEL[check.status]
            title = ARABIC_TITLES.get(check.id, check.title) if ar else check.title
            detail = check.detail.replace("|", "\\|")
            lines.append(f"| {symbol} | {title} | {label} | {detail} |")
        lines.append("")
        if device.notes:
            for note in device.notes:
                lines.append(f"> {note}")
            lines.append("")
    return "\n".join(lines)


# ----------------------------------------------------------------------------- cli


def collect(runner: Runner) -> list:
    devices = [audit_macos(runner)]
    for adb, serial, model in android_devices(runner):
        devices.append(audit_android(runner, adb, serial, model))
    for entry in ios_devices(runner):
        devices.append(audit_ios(runner, entry))
    return devices


def cmd_audit(args) -> int:
    devices = collect(Runner())
    print(render_markdown(devices, args.lang))
    if args.json or args.out:
        payload = {"generated": time.strftime("%Y-%m-%dT%H:%M:%S"), "devices": [d.as_dict() for d in devices]}
        text = json.dumps(payload, ensure_ascii=False, indent=2)
        if args.out:
            Path(args.out).parent.mkdir(parents=True, exist_ok=True)
            Path(args.out).write_text(text + "\n")
            print(f"\nJSON written: {args.out}")
        if args.json:
            print(text)
    return 0


def cmd_verify(args) -> int:
    devices = collect(Runner())
    print(render_markdown(devices, args.lang))
    failures = [(d, c) for d in devices for c in d.critical_open()]
    print()
    if failures:
        print(f"{len(failures)} critical layer(s) open:")
        for device, check in failures:
            print(f"  - {device.kind} {device.name}: {check.title} — {check.fix or check.detail}")
        return 1
    print("No critical layer reads as open. Unknown layers still need a look on the device.")
    return 0


def cmd_report(args) -> int:
    devices = collect(Runner())
    text = render_markdown(devices, args.lang)
    out = Path(args.out) if args.out else REPO / "dist" / "fortify" / "report.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text + "\n")
    print(text)
    print(f"\nWritten: {out}")
    return 0


def cmd_apply(args) -> int:
    runner = Runner()
    code = 0
    if args.macos:
        apply_macos(runner, args.full_hosts, args.x_media)
    if args.android:
        code = max(code, apply_android(runner))
    if args.ios:
        code = max(code, apply_ios(runner))
    return code


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = parser.add_subparsers(dest="command", required=True)

    audit = sub.add_parser("audit", help="read-only state of every attached device")
    audit.add_argument("--lang", choices=["en", "ar"], default="en")
    audit.add_argument("--json", action="store_true")
    audit.add_argument("--out", help="write the JSON state to this path")
    audit.set_defaults(func=cmd_audit)

    verify = sub.add_parser("verify", help="audit, then exit non-zero when a critical layer is open")
    verify.add_argument("--lang", choices=["en", "ar"], default="en")
    verify.set_defaults(func=cmd_verify)

    report = sub.add_parser("report", help="write the markdown report")
    report.add_argument("--lang", choices=["en", "ar"], default="ar")
    report.add_argument("--out")
    report.set_defaults(func=cmd_report)

    apply_parser = sub.add_parser("apply", help="apply the automatable layers")
    apply_parser.add_argument("--macos", action="store_true")
    apply_parser.add_argument("--android", action="store_true")
    apply_parser.add_argument("--ios", action="store_true")
    apply_parser.add_argument("--x-media", action="store_true",
                              help="macOS only: block pbs/video.twimg.com (X keeps text, loses images)")
    apply_parser.add_argument("--full-hosts", action="store_true",
                              help="macOS only: merge all 236,586 domains (473k lines, slower lookups)")
    apply_parser.set_defaults(func=cmd_apply)
    return parser


def main(argv=None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    if args.command == "apply" and not (args.macos or args.android or args.ios):
        parser.error("apply needs at least one of --macos, --android, --ios")
    try:
        return args.func(args)
    except FortifyError as exc:
        print(f"fortify: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
