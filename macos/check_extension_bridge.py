#!/usr/bin/env python3
"""Check the Hisn browser extension <-> Mac app connection, read-only.

    python3 macos/check_extension_bridge.py
    python3 macos/check_extension_bridge.py --require-managed
    python3 macos/check_extension_bridge.py --json

What it checks:

  1. `/Applications/Hisn.app` and its `HisnBridge` executable exist.
  2. The app's `Info.plist` lists the extension IDs, and every installed
     Chromium browser's `app.hisn.bridge` manifest allows each of them.
  3. The bridge answers a framed `getLockState` message with a valid reply.
  4. With `--require-managed`, a Chromium policy file force-installs the
     extension (which is what makes it non-removable for a standard user).

Nothing is installed, changed or sent anywhere. A missing manifest is reported
with the exact `install_native_host.sh` command that fixes it. Managed-policy
detection is evidence that a policy file exists, not proof the browser accepted
it; verify the effective policy page as the docs describe.
"""

from __future__ import annotations

import argparse
import json
import os
import plistlib
import select
import struct
import subprocess
import sys
import time
from pathlib import Path

DEFAULT_APP = Path("/Applications/Hisn.app")
HOST = "app.hisn.bridge"
BROWSERS = [
    # name, app bundle, system native-host directory, user data directory, policy domain
    ("Chrome", "Google Chrome.app", "/Library/Google/Chrome/NativeMessagingHosts",
     "Google/Chrome", "com.google.Chrome"),
    ("Edge", "Microsoft Edge.app", "/Library/Microsoft/Edge/NativeMessagingHosts",
     "Microsoft Edge", "com.microsoft.Edge"),
    ("Brave", "Brave Browser.app", "/Library/Application Support/BraveSoftware/Brave-Browser/NativeMessagingHosts",
     "BraveSoftware/Brave-Browser", "com.brave.Browser"),
    ("Vivaldi", "Vivaldi.app", "/Library/Application Support/Vivaldi/NativeMessagingHosts",
     "Vivaldi", "com.vivaldi.Vivaldi"),
    ("Opera", "Opera.app", "/Library/Application Support/com.operasoftware.Opera/NativeMessagingHosts",
     "com.operasoftware.Opera", "com.operasoftware.Opera"),
    ("Arc", "Arc.app", "/Library/Application Support/Arc/User Data/NativeMessagingHosts",
     "Arc/User Data", "company.thebrowser.Browser"),
    ("Chromium", "Chromium.app", "/Library/Application Support/Chromium/NativeMessagingHosts",
     "Chromium", "org.chromium.Chromium"),
    ("Helium", "Helium.app", "/Library/Application Support/net.imput.helium/NativeMessagingHosts",
     "net.imput.helium", "net.imput.helium"),
]
MANAGED_DIRS = [Path("/Library/Managed Preferences"), Path.home() / "Library/Managed Preferences"]


def read_extension_ids(app: Path) -> list[str]:
    info = app / "Contents/Info.plist"
    try:
        with info.open("rb") as handle:
            plist = plistlib.load(handle)
    except (OSError, plistlib.InvalidFileException) as error:
        raise SystemExit(f"cannot read {info}: {error}")
    ids = plist.get("HisnExtensionIDs")
    if not isinstance(ids, list) or not ids or not all(isinstance(v, str) for v in ids):
        raise SystemExit(f"{info} has no usable HisnExtensionIDs array")
    return ids


def manifest_candidates(system_dir: str, user_dir: str) -> list[Path]:
    return [Path(system_dir) / f"{HOST}.json",
            Path.home() / "Library/Application Support" / user_dir / "NativeMessagingHosts" / f"{HOST}.json"]


def allowed_origins(path: Path) -> list[str]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return []
    origins = data.get("allowed_origins")
    return origins if isinstance(origins, list) else []


def read_exactly(stream, count: int, timeout: float) -> bytes | None:
    data = b""
    deadline = time.monotonic() + timeout
    while len(data) < count:
        left = deadline - time.monotonic()
        if left <= 0:
            return None
        ready, _, _ = select.select([stream], [], [], left)
        if not ready:
            return None
        chunk = os.read(stream.fileno(), count - len(data))
        if not chunk:
            return None
        data += chunk
    return data


def ask_bridge(app: Path, timeout: float = 8.0) -> tuple[dict | None, str]:
    bridge = app / "Contents/MacOS/HisnBridge"
    if not bridge.is_file() or not os.access(bridge, os.X_OK):
        return None, f"missing executable {bridge}"
    process = subprocess.Popen([str(bridge)], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, close_fds=True)
    try:
        message = json.dumps({"type": "getLockState"}).encode("utf-8")
        assert process.stdin is not None
        process.stdin.write(struct.pack("<I", len(message)) + message)
        process.stdin.flush()
        assert process.stdout is not None
        header = read_exactly(process.stdout, 4, timeout)
        if header is None:
            return None, "no reply from HisnBridge"
        length = struct.unpack("<I", header)[0]
        if length == 0 or length > 4 * 1024 * 1024:
            return None, f"invalid reply length {length}"
        body = read_exactly(process.stdout, length, timeout)
        if body is None:
            return None, "reply was cut short"
        try:
            reply = json.loads(body.decode("utf-8"))
        except ValueError as error:
            return None, f"reply is not JSON: {error}"
        if not isinstance(reply, dict) or not isinstance(reply.get("lockUntil"), (int, float)):
            return None, "reply has no numeric lockUntil"
        return reply, ""
    finally:
        try:
            assert process.stdin is not None
            process.stdin.close()
        except OSError:
            pass
        try:
            process.wait(timeout=1)
        except subprocess.TimeoutExpired:
            process.kill()


def managed_evidence(ids: list[str]) -> list[dict]:
    found = []
    for domain in sorted({browser[4] for browser in BROWSERS}):
        for base in MANAGED_DIRS:
            path = base / f"{domain}.plist"
            if not path.is_file():
                continue
            try:
                with path.open("rb") as handle:
                    policy = plistlib.load(handle)
            except (OSError, plistlib.InvalidFileException):
                continue
            settings = policy.get("ExtensionSettings")
            force = policy.get("ExtensionInstallForcelist")
            forced = []
            for extension_id in ids:
                entry = settings.get(extension_id) if isinstance(settings, dict) else None
                if isinstance(entry, dict) and entry.get("installation_mode") == "force_installed":
                    forced.append(extension_id)
                elif isinstance(force, list) and any(
                        isinstance(item, str) and item.split(";", 1)[0] == extension_id for item in force):
                    forced.append(extension_id)
            if forced:
                found.append({"domain": domain, "path": str(path), "ids": forced})
    return found


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--app-path", default=str(DEFAULT_APP))
    parser.add_argument("--extension-id", action="append", default=[],
                        help="Check this ID instead of the app's HisnExtensionIDs (repeatable).")
    parser.add_argument("--require-managed", action="store_true",
                        help="Fail unless a Chromium policy force-installs the extension.")
    parser.add_argument("--json", action="store_true", help="Machine-readable output.")
    args = parser.parse_args(argv)

    app = Path(args.app_path)
    problems: list[str] = []
    if not app.is_dir():
        raise SystemExit(f"Hisn app not found at {app}; install it first.")
    ids = args.extension_id or read_extension_ids(app)
    if not all(len(i) == 32 and all("a" <= c <= "p" for c in i) for i in ids):
        raise SystemExit("extension IDs must be 32 Chrome Web Store letters a-p")

    browser_rows = []
    for name, bundle, system_dir, user_dir, _domain in BROWSERS:
        installed = (Path("/Applications") / bundle).exists()
        if not installed:
            continue
        candidates = manifest_candidates(system_dir, user_dir)
        existing = [path for path in candidates if path.is_file()]
        if not existing:
            problems.append(f"{name}: no {HOST} native-messaging manifest")
            browser_rows.append({"browser": name, "manifest": None, "missing_ids": ids})
            continue
        origins = [origin for path in existing for origin in allowed_origins(path)]
        missing = [i for i in ids if f"chrome-extension://{i}/" not in origins]
        if missing:
            problems.append(f"{name}: manifest does not allow {', '.join(missing)}")
        browser_rows.append({"browser": name, "manifest": str(existing[0]), "missing_ids": missing})

    reply, bridge_error = ask_bridge(app)
    if reply is None:
        problems.append(f"bridge: {bridge_error}")

    managed = managed_evidence(ids)
    if args.require_managed and not managed:
        problems.append("managed policy: no force-installed extension found for " + ", ".join(ids))

    if args.json:
        print(json.dumps({"ids": ids, "browsers": browser_rows, "bridge": reply,
                          "bridge_error": bridge_error, "managed": managed,
                          "problems": problems}, indent=2, ensure_ascii=False))
    else:
        print(f"Hisn app: {app}")
        print(f"Extension IDs: {', '.join(ids)}")
        print("\nBrowser native-messaging manifests:")
        for row in browser_rows:
            if row["manifest"] is None:
                print(f"  {row['browser']:<9} MISSING")
            elif row["missing_ids"]:
                print(f"  {row['browser']:<9} present, does not allow {', '.join(row['missing_ids'])}")
            else:
                print(f"  {row['browser']:<9} allows every ID")
        print("\nBridge reply:")
        if reply is None:
            print(f"  FAILED: {bridge_error}")
        else:
            print(f"  mode={reply.get('mode')!r} lockUntil={reply.get('lockUntil')!r} "
                  f"allowlist={len(reply.get('allowlist') or [])} "
                  f"customBlocks={len(reply.get('customBlocks') or [])}")
        print("\nManaged force-install (what makes removal policy-blocked):")
        if managed:
            for item in managed:
                print(f"  {item['domain']}: {item['path']}")
        else:
            print("  not detected")
            print("  This is expected for an unpacked extension. Force-install needs a published")
            print("  store ID: profile/make_profile.py --extension-id <id> --browser chrome")
        if problems:
            print("\nProblems:")
            for problem in problems:
                print(f"  - {problem}")
            if any("native-messaging manifest" in p for p in problems):
                print("\nFix a missing manifest with:")
                print(f"  sudo macos/install_native_host.sh --extension-id {ids[0]}")
            return 1
        print("\nOK: the app bridge answers and every installed browser is connected.")
        print("Policy presence is not proof the browser accepted it; check chrome://policy,")
        print("edge://policy or the fork's equivalent after installing the profile.")
    return 0 if not problems else 1


if __name__ == "__main__":
    raise SystemExit(main())
