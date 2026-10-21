#!/usr/bin/env python3
"""Opt-in USB fixture checks. No permission grants, network traffic or private UI capture."""
import argparse
import subprocess
import time
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", required=True)
parser.add_argument("--restore-only", action="store_true", help="Restore fixture-only additions after an interrupted run")
parser.add_argument("--case", choices=("ordinary", "editable", "password", "word", "long", "wide", "app", "rapid", "heartbeat", "adult"))
args = parser.parse_args()


def adb(*parts):
    return subprocess.run([args.adb, "-s", args.serial, *parts], check=True,
                          capture_output=True, text=True, timeout=30).stdout


def configure(mode):
    # Remove only the previous diagnostic status, never policy files.
    adb("shell", "run-as", "app.hisn.android", "rm", "-f", "files/hisn_guard_test_status")
    adb("shell", "am", "start", "--user", "0", "-W", "-n",
        "app.hisn.android/.GuardTestSetupActivity", "--es", "mode", mode)
    status = adb("shell", "run-as", "app.hisn.android", "cat", "files/hisn_guard_test_status").strip()
    if status != "PASS " + mode:
        raise RuntimeError(status)


def resumed():
    return "\n".join(line for line in adb("shell", "dumpsys", "activity", "activities").splitlines()
                     if "mResumedActivity" in line or "topResumedActivity" in line)


def status():
    adb("shell", "am", "start", "--user", "0", "-W", "-n",
        "app.hisn.android/.GuardTestSetupActivity", "--es", "mode", "status")
    return adb("shell", "run-as", "app.hisn.android", "cat", "files/hisn_guard_test_status").strip()


def rapid_reopen():
    baseline = int(re.search(r"COOLDOWN_ENFORCEMENTS (\d+)", status()).group(1))
    fixture("word", True)
    adb("shell", "am", "start", "--user", "0", "-W", "-n",
        "app.hisn.android.test/app.hisn.android.GuardFixtureActivity",
        "-f", "0x10008000", "--es", "mode", "word")
    time.sleep(0.8)
    if "GuardFixtureActivity" in resumed():
        raise AssertionError("Rapid reopening left the restricted fixture usable")
    latest = int(re.search(r"COOLDOWN_ENFORCEMENTS (\d+)", status()).group(1))
    if latest <= baseline:
        raise AssertionError("Rapid attempt did not exercise the cooldown path; rerun")
    print("PASS rapid reopen blocked during warning cooldown", flush=True)


def heartbeat_test():
    before = int(re.search(r"PERIODIC_INSPECTIONS (\d+)", status()).group(1))
    fixture("ordinary", False)
    after = int(re.search(r"PERIODIC_INSPECTIONS (\d+)", status()).group(1))
    if after <= before:
        raise AssertionError("No periodic selected-screen inspection observed")
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(2)
    stopped = int(re.search(r"PERIODIC_INSPECTIONS (\d+)", status()).group(1))
    time.sleep(2)
    if int(re.search(r"PERIODIC_INSPECTIONS (\d+)", status()).group(1)) != stopped:
        raise AssertionError("Periodic checks did not stop outside the selected fixture")
    print("PASS periodic selected-screen checks; stopped outside selected app", flush=True)


def fixture(mode, blocked):
    time.sleep(1.8)  # Respect intervention debounce between independent cases.
    adb("shell", "am", "start", "--user", "0", "-W", "-n",
        "app.hisn.android.test/app.hisn.android.GuardFixtureActivity",
        "-f", "0x10008000", "--es", "mode", mode)
    end = time.monotonic() + (6 if blocked else 3)
    shield = False
    visible = False
    while time.monotonic() < end:
        top = resumed()
        shield = "app.hisn.android/.RestrictionActivity" in top
        visible = "GuardFixtureActivity" in top
        if shield:
            break
        time.sleep(0.2)
    if shield != blocked or (not blocked and not visible):
        detail = status()
        raise AssertionError("Fixture " + mode + ": expected " + ("restriction screen" if blocked else "allowed visible screen")
                             + "; fixture visible=" + str(visible) + "; " + detail)
    print("PASS", mode, "blocked" if blocked else "allowed", flush=True)


if args.restore_only:
    configure("restore")
    print("PASS fixture additions restored")
else:
    # No instrumentation: it restarts the app and disconnects Android accessibility.
    configure("setup-adult" if args.case == "adult" else "setup")
    try:
        if args.case == "adult":
            deadline = time.monotonic() + 12
            ready = False
            while time.monotonic() < deadline:
                detail = status()
                if "ADULT_ASSETS READY" in detail:
                    ready = True
                    break
                if "ADULT_ASSETS ERROR" in detail:
                    raise AssertionError("Verified adult policy unavailable")
                time.sleep(0.25)
            if not ready:
                raise AssertionError("Verified adult policy load timed out")
            fixture("medical", False)
            fixture("adult-en", True)
            fixture("adult-ar", True)
        for case in ("ordinary", "editable", "password"):
            if args.case is None or args.case == case:
                fixture(case, False)
        if args.case is None or args.case == "heartbeat":
            heartbeat_test()
        for case in ("word", "long", "wide"):
            if args.case is None or args.case == case:
                fixture(case, True)
        if args.case is None or args.case == "app":
            configure("block-app")
            fixture("ordinary", True)
        if args.case is None or args.case == "rapid":
            rapid_reopen()
    finally:
        configure("restore")
        print("PASS fixture additions removed; owner rules preserved", flush=True)
