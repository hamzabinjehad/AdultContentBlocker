#!/usr/bin/env python3
"""Read Hisn's managed-uninstall state from a connected debug build.

    python3 test-live-managed-status.py --serial SERIAL
    python3 test-live-managed-status.py --serial SERIAL --expect-managed
    python3 test-live-managed-status.py --serial SERIAL --expect-lockdown
    python3 test-live-managed-status.py --serial SERIAL --expect-strict

The app runs `ManagedStatusActivity` in its own process; the activity reads the
same controller the UI uses and writes one JSON line to private storage. This
script only starts that read-only debug activity, reads the file through
`run-as`, and checks the result. It does not apply, clear or release
device-owner policy, does not uninstall anything, and does not enable a VPN,
accessibility, admin or managed mode.

On an ordinary install the expected result is: not a device owner, uninstall not
blocked, state UNAVAILABLE or ADMIN_ONLY. `--expect-managed` is for a disposable
device that has deliberately been provisioned as Hisn's device owner; it
requires an active keep switch or commitment and Android's readback saying
uninstall is blocked. `--expect-lockdown` additionally requires the expected
managed-lockdown restrictions (five on Android 8, six on Android 9, seven on
Android 10–13 and eight on Android 14+, plus the strict unknown-source key on
Android 10+ when strict is on) to be held and read back exactly, and a matching
always-on DNS request.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time

PACKAGE = "app.hisn.android"
STATUS_FILE = "files/managed-status.json"

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb", help="adb executable")
parser.add_argument("--serial", required=True, help="device serial from adb devices")
parser.add_argument("--min-version-code", type=int, default=21,
                    help="fail if the installed version code is older (default: 21)")
parser.add_argument("--expect-managed", action="store_true",
                    help="require a provisioned device owner with an active guard")
parser.add_argument("--expect-lockdown", action="store_true",
                    help="also require every managed-lockdown restriction to be held")
parser.add_argument("--expect-strict", action="store_true",
                    help="also require strict managed lockdown and its install/debug keys")
args = parser.parse_args()
if args.expect_lockdown or args.expect_strict:
    args.expect_managed = True
if args.expect_strict:
    args.expect_lockdown = True


def adb(*parts: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run([args.adb, "-s", args.serial, *parts],
                          check=check, capture_output=True, text=True, timeout=30)


def fail(message: str) -> None:
    print("FAIL:", message, file=sys.stderr)
    raise SystemExit(1)


try:
    package_dump = adb("shell", "dumpsys", "package", PACKAGE).stdout
except subprocess.SubprocessError as exc:
    fail(f"cannot read package state: {exc}")
version = re.search(r"versionName=([^\s]+)", package_dump)
code = re.search(r"versionCode=(\d+)", package_dump)
if not version or not code:
    fail(f"{PACKAGE} is not installed or has no version metadata")
version_code = int(code.group(1))
if version_code < args.min_version_code:
    fail(f"installed versionCode {version_code} is older than required {args.min_version_code}")

# Remove any previous result so a failed launch cannot reuse old evidence.
adb("shell", "run-as", PACKAGE, "rm", "-f", STATUS_FILE, check=False)
start = adb("shell", "am", "start", "--user", "0", "-W", "-n",
            f"{PACKAGE}/.ManagedStatusActivity", check=False)
if start.returncode != 0:
    fail(f"could not start the debug status activity: {start.stderr.strip()}")
read = None
# am start -W waits for the activity to resume; the file is written in onCreate,
# but allow a short retry on a loaded device rather than reading a partial file.
for _ in range(15):
    read = adb("shell", "run-as", PACKAGE, "cat", STATUS_FILE, check=False)
    if read.returncode == 0 and read.stdout.strip():
        break
    time.sleep(0.2)
if read is None or read.returncode != 0 or not read.stdout.strip():
    fail("status file missing; is this a debug build with ManagedStatusActivity? "
         f"{(read.stderr if read else '').strip()}")
try:
    data = json.loads(read.stdout.strip())
except json.JSONDecodeError as exc:
    fail(f"status is not JSON: {exc}: {read.stdout[:400]!r}")

for key in ("deviceOwner", "adminActive", "uninstallBlocked", "keepInstalled",
            "managedLockdown", "managedStrict", "suspendBlocked", "remainingMillis",
            "restrictions", "desiredRestrictions", "restrictionsMatch", "managedMatches",
            "alwaysOnVpn", "alwaysOnDesired", "alwaysOnMatch", "suspendedApps",
            "desiredSuspended", "suspensionMatch", "state", "mayRelease"):
    if key not in data:
        fail(f"status is missing {key}: {data}")
if not isinstance(data["restrictions"], list) or not isinstance(data["desiredRestrictions"], list):
    fail(f"restriction sets are not lists: {data}")
sdk_text = adb("shell", "getprop", "ro.build.version.sdk").stdout.strip()
try:
    sdk = int(sdk_text)
except ValueError:
    fail(f"cannot read the device API level: {sdk_text!r}")
# Android 9 (API 28) adds manual date/time control; Android 10 (API 29) adds
# the separate Private DNS restriction. Adding a user is blocked on every
# supported version.
LOCKDOWN = {"no_safe_boot", "no_control_apps", "no_factory_reset", "no_config_vpn", "no_add_user"}
if sdk >= 28:
    LOCKDOWN.add("no_config_date_time")
if sdk >= 29:
    LOCKDOWN.add("disallow_config_private_dns")
# Android 14 (API 34) adds the default-app restriction.
if sdk >= 34:
    LOCKDOWN.add("disallow_config_default_apps")
# Strict adds the unknown-source install block on Android 10+. USB debugging
# is deliberately left available for guardian verification and recovery.
STRICT = set(LOCKDOWN)
if sdk >= 29:
    STRICT.add("no_install_unknown_sources_globally")
EXPECTED = STRICT if data["managedStrict"] else LOCKDOWN
if args.expect_strict and not data["managedStrict"]:
    fail("strict lockdown expected, but the app reports managedStrict=false")

print(f"Hisn {data.get('versionName')} ({data.get('versionCode')}) on {args.serial}")
print(f"device owner: {data['deviceOwner']}  admin: {data['adminActive']}  "
      f"uninstall blocked: {data['uninstallBlocked']}")
print(f"keep installed: {data['keepInstalled']}  remaining: {data['remainingMillis']} ms  "
      f"state: {data['state']}  may release: {data['mayRelease']}")
print(f"managed lockdown: {data['managedLockdown']}  strict: {data['managedStrict']}  "
      f"suspend blocked: {data['suspendBlocked']}  "
      f"held: {sorted(data['restrictions'])}  desired: {sorted(data['desiredRestrictions'])}  "
      f"match: {data['restrictionsMatch']}")
print(f"suspended apps: {sorted(data['suspendedApps'])}  "
      f"wanted: {sorted(data['desiredSuspended'])}  match: {data['suspensionMatch']}")
print(f"always-on DNS VPN: current={data['alwaysOnVpn']} desired={data['alwaysOnDesired']} "
      f"match={data['alwaysOnMatch']}  managed match: {data['managedMatches']}")
if data["managedLockdown"] and (data["keepInstalled"] or data["remainingMillis"] > 0) \
        and data["deviceOwner"]:
    if not data["restrictionsMatch"] or set(data["restrictions"]) != EXPECTED:
        fail(f"managed lockdown is on but Android does not hold the expected restrictions: {sorted(EXPECTED)}")
    if not data["managedMatches"] or not data["alwaysOnMatch"]:
        fail("managed lockdown is on but the always-on DNS request does not match the policy")
    if not data["suspensionMatch"] or set(data["suspendedApps"]) != set(data["desiredSuspended"]):
        fail("managed lockdown is on but the suspended-app set does not match the policy")
if data.get("error"):
    fail(f"app reported a status error: {data['error']}")

if args.expect_managed:
    if not data["deviceOwner"]:
        fail("managed mode expected, but Hisn is not this user's device owner")
    if not (data["keepInstalled"] or data["remainingMillis"] > 0):
        fail("managed mode expected, but neither a keep switch nor a commitment is active")
    if not data["uninstallBlocked"] or data["state"] != "PROTECTED":
        fail("managed mode expected, but Android does not report uninstall blocked")
    if args.expect_lockdown:
        if not data["managedLockdown"] or not data["restrictionsMatch"] \
                or set(data["restrictions"]) != EXPECTED or not data["managedMatches"]:
            fail(f"managed lockdown expected, but the restrictions were not held and read back: "
                 f"expected {sorted(EXPECTED)}")
        if not data["alwaysOnMatch"]:
            fail("managed lockdown expected, but the always-on DNS VPN does not match the policy")
        print("PASS: device owner with uninstall blocked and managed lockdown held")
        raise SystemExit(0)
    print("PASS: device owner with the uninstall guard applied and read back")
    raise SystemExit(0)

if data["deviceOwner"]:
    # A managed test device is not a failure, but the script must not claim an
    # ordinary install. With an active guard the state must be protected.
    if (data["keepInstalled"] or data["remainingMillis"] > 0) \
            and (not data["uninstallBlocked"] or data["state"] != "PROTECTED"):
        fail("device owner with an active guard is not reporting uninstall protection")
    print("PASS: read-only managed-device status (owner present)")
    print("No uninstall, admin, device-owner, VPN or accessibility change was made.")
    raise SystemExit(0)

if data["uninstallBlocked"] or data["state"] == "PROTECTED":
    fail("an unmanaged install claims uninstall protection")
if data["state"] not in ("UNAVAILABLE", "ADMIN_ONLY"):
    fail(f"unmanaged install has an unexpected state: {data['state']}")
print("PASS: ordinary install is honestly reported as not managed; Android can uninstall it")
print("No uninstall, admin, device-owner, VPN or accessibility change was made.")
