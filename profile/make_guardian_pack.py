#!/usr/bin/env python3
"""Build a supervised iPhone/iPad guardian pack, not just a profile.

    python3 profile/make_guardian_pack.py --out dist/iphone-pack \
        --block-site reddit.com --maximum

The pack contains:

  hisn-iphone.mobileconfig   the profile, mode 0600 (it holds the removal password)
  manifest.json              hashes and facts, never the password
  GUARDIAN.md                supervision, install, verification and recovery steps
  SHA256SUMS                 the profile hash

It does not supervise or install anything. Apple Configurator is still the
supervising tool; this file only removes the chance of installing the right
profile with the wrong settings or losing the recovery password.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import plistlib
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import make_ios_profile as ios  # noqa: E402
import make_profile as mac  # noqa: E402

PROFILE_NAME = "hisn-iphone.mobileconfig"
MANIFEST_NAME = "manifest.json"
README_NAME = "GUARDIAN.md"
SUMS_NAME = "SHA256SUMS"


def parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0],
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default="../dist/hisn-iphone-pack")
    ap.add_argument("--resolver", choices=sorted(mac.RESOLVERS), default=mac.DEFAULT_RESOLVER)
    ap.add_argument("--block-site", dest="block_sites", action="append", default=[])
    ap.add_argument("--block-sites-file")
    ap.add_argument("--display-name", default="Hisn Phone Protection")
    ap.add_argument("--removal-password", default=None)
    ap.add_argument("--print-password", action="store_true")
    ap.add_argument("--allow-app-removal", action="store_true")
    ap.add_argument("--allow-unreachable-resolver", action="store_true")
    ap.add_argument("--maximum", action="store_true",
                    help="turn on every supervised hardening switch; refuses --allow-app-removal")
    ap.add_argument("--deny-account-modification", action="store_true")
    ap.add_argument("--deny-passcode-modification", action="store_true")
    ap.add_argument("--deny-app-installation", action="store_true")
    ap.add_argument("--deny-app-clips", action="store_true")
    ap.add_argument("--deny-enabling-restrictions", action="store_true")
    ap.add_argument("--force-automatic-date-time", action="store_true")
    ap.add_argument("--force", action="store_true", help="replace files in a non-empty --out")
    return ap


def read_sites(args) -> list[str]:
    raw = list(args.block_sites or [])
    if args.block_sites_file:
        try:
            raw += [line for line in Path(args.block_sites_file).read_text().splitlines()
                    if line.strip() and not line.lstrip().startswith("#")]
        except OSError as exc:
            raise SystemExit(f"cannot read {args.block_sites_file}: {exc}") from exc
    hosts, rejected = [], []
    for entry in raw:
        host = ios.canonical_host(entry)
        if host is None:
            rejected.append(entry.strip())
        elif host not in hosts:
            hosts.append(host)
    if rejected:
        raise SystemExit("not a site: " + ", ".join(rejected))
    if len(hosts) > ios.MAX_DENY_URLS:
        raise SystemExit(f"{len(hosts)} sites; Apple's filter takes at most {ios.MAX_DENY_URLS}")
    return sorted(hosts)


def profile_args(args, doh: str, addresses: list[str], hosts: list[str], password: str):
    return argparse.Namespace(
        removal_password=password,
        doh=doh,
        dns_addresses=addresses,
        blocked_hosts=hosts,
        allow_app_removal=args.allow_app_removal,
        display_name=args.display_name,
        deny_account_modification=args.maximum or args.deny_account_modification,
        deny_passcode_modification=args.maximum or args.deny_passcode_modification,
        deny_app_installation=args.maximum or args.deny_app_installation,
        deny_app_clips=args.maximum or args.deny_app_clips,
        deny_enabling_restrictions=args.maximum or args.deny_enabling_restrictions,
        force_automatic_date_time=args.maximum or args.force_automatic_date_time,
    )


def guardian_readme(*, created: str, sites: int, maximum: bool) -> str:
    hardening = ("all supervised hardening switches" if maximum
                 else "the selected supervised hardening switches")
    return f"""# Hisn supervised iPhone/iPad guardian pack

Created {created}. This pack holds a configuration profile for a **supervised**
iPhone or iPad. It does not supervise the device and does not install anything.

## What is inside

- `{PROFILE_NAME}` — family DNS, Apple's adult website filter, the removal
  password and {hardening}. Keep this file with the guardian; the removal
  password is inside it.
- `{MANIFEST_NAME}` — profile hash, resolver, site count and maximum flag. It
  never contains the password.
- `{SUMS_NAME}` — `sha256sum -c` line for the profile.

## Install, in order

1. Back up the device. Supervision erases it.
2. In Apple Configurator: connect the device, choose **Prepare → Manual
   configuration**, tick **Supervise devices**, untick **Allow devices to pair
   with other computers**, then finish.
3. Set the device up and sign in to the person's Apple Account. Leave **Find My**
   on.
4. Install Hisn from the App Store and finish its own Setup tab. Do this before
   adding the profile.
5. In Apple Configurator, select the device, **Add → Profiles**, and choose
   `{PROFILE_NAME}`.
6. Set a Screen Time passcode the guardian keeps.

## Verify on the device

- Settings → General → VPN & Device Management shows this profile with **no
  Remove button**.
- Holding an app icon shows **no Remove App**.
- Settings → General → Transfer or Reset shows **no Erase All Content and
  Settings**.
- The DNS entry cannot be switched off.
- A test adult site is blocked in Safari, and one of the extra sites is blocked.

## Recovery and limits

- Removal needs the password inside the profile or a supervised reset from the
  guardian's Mac. A DFU/recovery restore erases the profile; Activation Lock
  then asks for the Apple Account password.
- Supervision added by hand has a 30-day provisional release window.
- "No app deletion" applies to every app; iOS has no per-app switch without MDM.
- The profile is not a classifier: it filters websites, not every image or post.
- A deleted profile or app enforces nothing; the guardian's removal password and
  the device's Activation Lock are the recovery path.
"""


def write_pack(out: Path, profile: dict, password: str, args, hosts: list[str],
               resolver: str, maximum: bool) -> None:
    if out.exists() and any(out.iterdir()) and not args.force:
        raise SystemExit(f"{out} is not empty; pass --force to replace the pack")
    out.mkdir(parents=True, exist_ok=True)
    profile_bytes = plistlib.dumps(profile, fmt=plistlib.FMT_XML)
    profile_path = out / PROFILE_NAME
    mac.write_private(profile_path, profile_bytes)
    digest = hashlib.sha256(profile_bytes).hexdigest()
    created = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    manifest = {
        "format": 1,
        "created": created,
        "profile": PROFILE_NAME,
        "profile_sha256": digest,
        "profile_bytes": len(profile_bytes),
        "blocked_sites": len(hosts),
        "maximum": maximum,
        "resolver": resolver,
        "display_name": args.display_name,
        "note": ("The removal password is inside the profile "
                 "(com.apple.profileRemovalPassword). Give the profile to the guardian."),
    }
    (out / MANIFEST_NAME).write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n",
                                     encoding="utf-8")
    (out / README_NAME).write_text(
        guardian_readme(created=created, sites=len(hosts), maximum=maximum), encoding="utf-8")
    (out / SUMS_NAME).write_text(f"{digest}  {PROFILE_NAME}\n", encoding="ascii")
    print(f"wrote {out} ({len(hosts)} extra sites, profile {PROFILE_NAME}, sha256 {digest})")
    if args.print_password:
        print(password)
    else:
        print("The removal password is written only inside the profile. "
              "Give the profile to the guardian before installing it.", file=sys.stderr)


def main(argv: list[str] | None = None) -> int:
    ap = parser()
    args = ap.parse_args(argv)
    if args.maximum and args.allow_app_removal:
        ap.error("--maximum cannot be combined with --allow-app-removal")
    if not args.allow_unreachable_resolver:
        preset = mac.RESOLVERS[args.resolver]
        if not mac.resolver_is_reachable(preset["doh"]):
            ap.error(f"{preset['doh']} does not resolve; a profile pointing at it would take "
                     "the phone's DNS down. Pass --allow-unreachable-resolver if building offline.")
    preset = mac.RESOLVERS[args.resolver]
    hosts = read_sites(args)
    password = args.removal_password or mac.gen_password()
    profile, actual_password = ios.build(profile_args(
        args, preset["doh"], preset["addresses"], hosts, password))
    if actual_password != password:
        raise SystemExit("internal error: the profile password changed")
    write_pack(Path(args.out), profile, password, args, hosts, args.resolver, args.maximum)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
