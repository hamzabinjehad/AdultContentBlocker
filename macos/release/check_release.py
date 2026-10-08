#!/usr/bin/env python3
"""Validate release configuration or a built Hisn app before packaging."""

from __future__ import annotations

import argparse
import plistlib
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
NETWORK = "com.apple.developer.networking.networkextension"
GROUPS = "com.apple.security.application-groups"
RELEASE_PROVIDER = "content-filter-provider-systemextension"


def read_plist(path: Path) -> dict:
    return plistlib.loads(path.read_bytes())


def check_entitlements(app: dict, extension: dict) -> list[str]:
    problems = []
    for name, values in (("app", app), ("filter", extension)):
        if values.get(NETWORK) != [RELEASE_PROVIDER]:
            problems.append(f"{name}: Developer ID requires {RELEASE_PROVIDER}")
        if values.get("com.apple.security.get-task-allow"):
            problems.append(f"{name}: debugging entitlement must not ship")
        if not values.get(GROUPS):
            problems.append(f"{name}: missing shared application group")
    if app.get(GROUPS) != extension.get(GROUPS):
        problems.append("app and filter application groups differ")
    if app.get("com.apple.developer.system-extension.install") is not True:
        problems.append("app: missing system extension installation entitlement")
    if extension.get("com.apple.security.app-sandbox") is not True:
        problems.append("filter: the system extension must be sandboxed")
    if extension.get("com.apple.security.network.client") is not True:
        problems.append("filter: missing network client entitlement")
    return problems


def check_browser_config(info: dict, public: bool = False) -> list[str]:
    ids = info.get("HisnExtensionIDs")
    if (not isinstance(ids, list) or not ids or
            any(not isinstance(value, str) or not re.fullmatch(r"[a-p]{32}", value) for value in ids)):
        return ["HisnExtensionIDs must contain exact, valid browser extension IDs"]
    problems = []
    if len(ids) != len(set(ids)):
        problems.append("HisnExtensionIDs contains duplicate IDs")
    stores = {"HisnChromeStoreURL": "chromewebstore.google.com",
              "HisnEdgeStoreURL": "microsoftedge.microsoft.com"}
    configured = False
    for key, host in stores.items():
        value = info.get(key)
        if value is None:
            continue
        configured = True
        if not isinstance(value, str):
            problems.append(f"{key}: expected a store URL")
            continue
        url = urlparse(value)
        store_id = url.path.rstrip("/").rsplit("/", 1)[-1]
        if url.scheme != "https" or url.netloc != host or store_id not in ids:
            problems.append(f"{key}: use an HTTPS store URL whose extension ID is in HisnExtensionIDs")
    if public and not configured:
        problems.append("Public packaging needs a published browser extension URL in Hisn/Info.plist")
    return problems


def check_source(root: Path = ROOT, public: bool = False) -> list[str]:
    app = read_plist(root / "Hisn/Hisn.DeveloperID.entitlements")
    extension = read_plist(root / "HisnFilter/HisnFilter.DeveloperID.entitlements")
    problems = check_entitlements(app, extension)
    problems += check_browser_config(read_plist(root / "Hisn/Info.plist"), public)
    # Read the generated project structurally, including each target's selected
    # configuration. A correct entitlement file that no target uses is no help.
    project = subprocess.run(
        ["plutil", "-convert", "json", "-o", "-", str(root / "Hisn.xcodeproj/project.pbxproj")],
        check=True, capture_output=True, text=True)
    import json
    objects = json.loads(project.stdout)["objects"]
    for obj in objects.values():
        if obj.get("isa") != "PBXNativeTarget" or obj.get("name") not in ("Hisn", "HisnFilter", "HisnBridge"):
            continue
        configs = objects[obj["buildConfigurationList"]]["buildConfigurations"]
        config = next((objects[c] for c in configs if objects[c]["name"] == "DeveloperID"), None)
        if config is None:
            problems.append(f"{obj['name']}: no DeveloperID configuration")
            continue
        settings = config["buildSettings"]
        if settings.get("ENABLE_HARDENED_RUNTIME") != "YES":
            problems.append(f"{obj['name']}: hardened runtime is required")
        if obj["name"] != "HisnBridge":
            expected = f"{obj['name']}/{obj['name']}.DeveloperID.entitlements"
            if settings.get("CODE_SIGN_ENTITLEMENTS") != expected:
                problems.append(f"{obj['name']}: DeveloperID selects the wrong entitlements")
    return problems


def check_bundle(app: Path, signed: bool = True, public: bool = False) -> list[str]:
    info = read_plist(app / "Contents/Info.plist")
    problems = check_browser_config(info, public)
    extension = app / "Contents/Library/SystemExtensions/HisnFilter.systemextension"
    bridge = app / "Contents/MacOS/HisnBridge"
    if info.get("CFBundleIdentifier") != "app.hisn.Hisn":
        problems.append("Unexpected app bundle identifier")
    for executable in (app / "Contents/MacOS/Hisn", bridge, extension / "Contents/MacOS/HisnFilter"):
        if not executable.is_file() or not executable.stat().st_mode & 0o111:
            problems.append(f"Missing executable: {executable}")
    filter_info = read_plist(extension / "Contents/Info.plist")
    if filter_info.get("CFBundleIdentifier") != "app.hisn.Hisn.HisnFilter":
        problems.append("Unexpected filter bundle identifier")
    for key in ("CFBundleVersion", "CFBundleShortVersionString"):
        if info.get(key) != filter_info.get(key):
            problems.append(f"App/filter {key} differ")
    for language in ("en", "ar"):
        if not (app / f"Contents/Resources/{language}.lproj").is_dir():
            problems.append(f"Missing {language} localization")
    if signed:
        entitlements = []
        teams = []
        for path in (app, extension, bridge):
            subprocess.run(["codesign", "--verify", "--strict", str(path)], check=True, capture_output=True)
            signature = subprocess.run(["codesign", "-d", "--verbose=4", str(path)],
                                       check=True, capture_output=True, text=True).stderr
            if "Authority=Developer ID Application:" not in signature or "runtime" not in signature:
                problems.append(f"{path.name}: expected Developer ID signature and hardened runtime")
            team = re.search(r"^TeamIdentifier=(.+)$", signature, re.MULTILINE)
            teams.append(team.group(1) if team else None)
            result = subprocess.run(["codesign", "-d", "--entitlements", ":-", str(path)],
                                    check=True, capture_output=True)
            values = plistlib.loads(result.stdout)
            if values.get("com.apple.security.get-task-allow"):
                problems.append(f"{path.name}: debugging entitlement must not ship")
            entitlements.append(values)
        for executable in (app / "Contents/MacOS/Hisn", bridge, extension / "Contents/MacOS/HisnFilter"):
            architectures = subprocess.run(["lipo", "-archs", str(executable)],
                                          check=True, capture_output=True, text=True).stdout.split()
            if not {"arm64", "x86_64"}.issubset(architectures):
                problems.append(f"{executable.name}: Intel and Apple silicon builds are required")
        problems += check_entitlements(entitlements[0], entitlements[1])
        if not teams[0] or teams[0] == "not set" or len(set(teams)) != 1:
            problems.append("App, filter and bridge must be signed by the same team")
        if entitlements[2].get(GROUPS) != entitlements[0].get(GROUPS):
            problems.append("Bridge application groups differ from the app")
        prefix = f"{teams[0]}.app.hisn"
        if prefix not in entitlements[0].get(GROUPS, []):
            problems.append("Missing expanded team-prefixed application group")
        service = filter_info.get("NetworkExtension", {}).get("NEMachServiceName")
        if service != f"{teams[0]}.app.hisn.filter":
            problems.append("Filter Mach service does not match the signing team")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", type=Path)
    parser.add_argument("--unsigned", action="store_true", help="Structure checks for local build validation only")
    parser.add_argument("--public", action="store_true", help="Require a configured store extension")
    args = parser.parse_args()
    try:
        problems = (check_bundle(args.app, not args.unsigned, args.public) if args.app
                    else check_source(public=args.public))
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(f"Release validation failed: {error}", file=sys.stderr)
        return 1
    for problem in problems:
        print(f"Release validation: {problem}", file=sys.stderr)
    if problems:
        return 1
    print("Release validation passed" + (" (unsigned structure only)" if args.unsigned else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
