#!/usr/bin/env python3
"""Create or check an AltStore/SideStore source for a Hisn IPA.

The source is the "download the app online" part of direct distribution: a
static JSON file on any HTTPS host that lists the current IPA, its version,
size and SHA-256. AltStore, SideStore and compatible tools read it and sign the
IPA with the person's own Apple team on install.

Reading an IPA needs no network and no Apple account. `--check` validates an
existing source file without touching an IPA, so CI can guard the published
file.
"""
from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import plistlib
import sys
import zipfile
from pathlib import Path

DEFAULT_BUNDLE = "app.hisn.mobile"
DEFAULT_IDENTIFIER = "app.hisn.mobile.source"
DEFAULT_WEBSITE = "https://github.com/hamzabinjehad/AdultContentBlocker"
DEFAULT_TINT = "#5C5CFF"
MAX_PLIST = 256 << 10
MAX_IPA = 1 << 30
REQUIRED_ENTITLEMENTS = [
    "com.apple.developer.networking.networkextension",
    "com.apple.developer.family-controls",
    "com.apple.security.application-groups",
]


class Invalid(ValueError):
    """The IPA or source cannot be described honestly."""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(1 << 20):
            digest.update(chunk)
    return digest.hexdigest()


def read_app_info(ipa: Path) -> dict:
    """The built app's Info.plist, read without extracting the whole IPA."""
    if not ipa.is_file():
        raise Invalid(f"{ipa}: not a file")
    if ipa.stat().st_size > MAX_IPA:
        raise Invalid(f"{ipa}: larger than {MAX_IPA} bytes")
    try:
        with zipfile.ZipFile(ipa) as archive:
            names = [name for name in archive.namelist()
                     if name.startswith("Payload/") and name.count("/") == 2
                     and name.endswith(".app/Info.plist")]
            if len(names) != 1:
                raise Invalid("IPA must contain exactly one Payload/*.app/Info.plist")
            info = archive.getinfo(names[0])
            if info.file_size > MAX_PLIST:
                raise Invalid("Info.plist is implausibly large")
            with archive.open(info) as handle:
                data = plistlib.load(handle)
    except (zipfile.BadZipFile, KeyError, plistlib.InvalidFileException) as error:
        raise Invalid(f"{ipa}: unreadable IPA") from error
    if not isinstance(data, dict):
        raise Invalid("Info.plist is not a dictionary")
    for key in ("CFBundleIdentifier", "CFBundleShortVersionString", "CFBundleVersion"):
        value = data.get(key)
        if not isinstance(value, str) or not value.strip():
            raise Invalid(f"Info.plist has no {key}")
    return data


def validate_https_url(url: str, what: str = "URL") -> str:
    if not isinstance(url, str) or not url.startswith("https://"):
        raise Invalid(f"{what} must be HTTPS")
    if len(url) > 2048 or any(character.isspace() for character in url):
        raise Invalid(f"{what} is not a single URL")
    return url


def validate_url(url: str) -> str:
    validate_https_url(url, "download URL")
    if not url.lower().endswith(".ipa"):
        raise Invalid("download URL must name the .ipa file")
    return url


def build_source(ipa: Path, url: str, *, name: str, identifier: str,
                 subtitle: str, description: str, icon_url: str,
                 developer: str = "Hisn", website: str = DEFAULT_WEBSITE,
                 tint: str = DEFAULT_TINT, min_ios: str = "16.0",
                 date: str | None = None) -> dict:
    info = read_app_info(ipa)
    bundle = info["CFBundleIdentifier"]
    if bundle != DEFAULT_BUNDLE:
        raise Invalid(f"unexpected bundle identifier {bundle!r}")
    if not icon_url:
        raise Invalid("an icon URL is required: installers show it before download")
    validate_https_url(icon_url, "icon URL")
    validate_url(url)
    if not tint.startswith("#") or len(tint) != 7:
        raise Invalid("tintColor must be #RRGGBB")
    if not subtitle.strip() or not description.strip():
        raise Invalid("subtitle and description are required")
    size = ipa.stat().st_size
    version = info["CFBundleShortVersionString"]
    build = info["CFBundleVersion"]
    today = date or datetime.date.today().isoformat()
    return {
        "name": name,
        "identifier": identifier,
        "subtitle": subtitle,
        "description": description,
        "iconURL": icon_url,
        "headerURL": icon_url,
        "website": website,
        "tintColor": tint,
        "featuredApps": [bundle],
        "apps": [{
            "name": name,
            "bundleIdentifier": bundle,
            "developerName": developer,
            "subtitle": subtitle,
            "localizedDescription": description,
            "iconURL": icon_url,
            "tintColor": tint,
            "category": "utilities",
            "screenshots": [],
            "versions": [{
                "version": version,
                "buildVersion": build,
                "date": today,
                "downloadURL": url,
                "size": size,
                "sha256": sha256(ipa),
                "minOSVersion": min_ios,
            }],
            "appPermissions": {
                "entitlements": REQUIRED_ENTITLEMENTS,
                "privacy": {"Tracking": False, "TrackingDomains": [], "CollectedData": []},
            },
        }],
    }


def validate_source(source: object) -> list[str]:
    """Names the first thing wrong, or returns no errors."""
    errors: list[str] = []
    if not isinstance(source, dict):
        return ["source is not an object"]
    for key in ("name", "identifier", "subtitle", "iconURL", "website", "tintColor"):
        if not isinstance(source.get(key), str) or not source[key].strip():
            errors.append(f"missing {key}")
    apps = source.get("apps")
    if not isinstance(apps, list) or len(apps) != 1:
        errors.append("source must list exactly one app")
        return errors
    app = apps[0]
    if not isinstance(app, dict):
        return ["the app entry is not an object"]
    if app.get("bundleIdentifier") != DEFAULT_BUNDLE:
        errors.append(f"bundleIdentifier must be {DEFAULT_BUNDLE}")
    versions = app.get("versions")
    if not isinstance(versions, list) or not versions:
        errors.append("no versions")
        return errors
    seen = set()
    for version in versions:
        if not isinstance(version, dict):
            errors.append("a version entry is not an object")
            continue
        for key in ("version", "buildVersion", "date", "downloadURL"):
            if not isinstance(version.get(key), str) or not version[key].strip():
                errors.append(f"version entry is missing {key}")
        sha = version.get("sha256")
        if not isinstance(sha, str) or len(sha) != 64 or any(
                character not in "0123456789abcdef" for character in sha):
            errors.append("version entry needs a lowercase SHA-256")
        size = version.get("size")
        if not isinstance(size, int) or not 0 < size <= MAX_IPA:
            errors.append("version entry needs a plausible byte size")
        key = (version.get("version"), version.get("buildVersion"))
        if key in seen:
            errors.append("duplicate version/build")
        seen.add(key)
        try:
            validate_url(version.get("downloadURL", ""))
        except Invalid as error:
            errors.append(str(error))
        try:
            validate_https_url(app.get("iconURL", ""), "icon URL")
        except Invalid as error:
            errors.append(str(error))
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ipa", type=Path, help="the built IPA")
    parser.add_argument("--url", help="HTTPS URL the IPA will be served from")
    parser.add_argument("--out", type=Path, help="where to write the source JSON")
    parser.add_argument("--check", type=Path,
                        help="validate an existing source JSON and exit")
    parser.add_argument("--name", default="Hisn")
    parser.add_argument("--identifier", default=DEFAULT_IDENTIFIER)
    parser.add_argument("--subtitle", default="Adult-content blocking on iPhone and iPad")
    parser.add_argument("--description", default=(
        "Hisn blocks adult content on the device: six Safari blockers from a signed "
        "domain list, a device-wide DNS filter over every app's system DNS, Screen "
        "Time app rules, and a commitment that freezes them. This direct build is "
        "distributed outside the App Store; the installer signs it with your own "
        "Apple team and the entitlements it needs."))
    parser.add_argument("--icon-url", default="https://raw.githubusercontent.com/"
                        "hamzabinjehad/AdultContentBlocker/main/design/app-icon/Hisn.png")
    parser.add_argument("--developer", default="Hisn")
    parser.add_argument("--website", default=DEFAULT_WEBSITE)
    parser.add_argument("--tint", default=DEFAULT_TINT)
    parser.add_argument("--min-ios", default="16.0")
    parser.add_argument("--date", default=None, help="YYYY-MM-DD, defaults to today")
    args = parser.parse_args(argv)

    if args.check:
        try:
            source = json.loads(args.check.read_text())
        except (OSError, ValueError) as error:
            print(f"{args.check}: {error}", file=sys.stderr)
            return 1
        errors = validate_source(source)
        if errors:
            for error in errors:
                print(f"{args.check}: {error}", file=sys.stderr)
            return 1
        print(f"{args.check}: valid source for "
              f"{source['apps'][0]['versions'][0]['version']}")
        return 0

    if not args.ipa or not args.url or not args.out:
        parser.error("--ipa, --url and --out are required (or use --check)")
    try:
        source = build_source(args.ipa, args.url, name=args.name,
                              identifier=args.identifier, subtitle=args.subtitle,
                              description=args.description, icon_url=args.icon_url,
                              developer=args.developer, website=args.website,
                              tint=args.tint, min_ios=args.min_ios, date=args.date)
    except Invalid as error:
        print(f"source not written: {error}", file=sys.stderr)
        return 1
    errors = validate_source(source)
    if errors:
        print("source not written: " + "; ".join(errors), file=sys.stderr)
        return 1
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(source, indent=2, sort_keys=True) + "\n")
    print(f"wrote {args.out} ({source['apps'][0]['versions'][0]['version']} "
          f"build {source['apps'][0]['versions'][0]['buildVersion']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
