#!/usr/bin/env python3
"""Opt-in real local-time schedule boundary on a harmless static fixture. No clock or permission changes."""
import argparse
import re
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", required=True)
args = parser.parse_args()


def adb(*parts):
    return subprocess.run([args.adb, "-s", args.serial, *parts], check=True,
                          capture_output=True, text=True, timeout=25).stdout


def command(mode):
    adb("shell", "run-as", "app.hisn.android", "rm", "-f", "files/hisn_guard_test_status")
    adb("shell", "am", "start", "--user", "0", "-W", "-n",
        "app.hisn.android/.GuardTestSetupActivity", "--es", "mode", mode)
    result = adb("shell", "run-as", "app.hisn.android", "cat", "files/hisn_guard_test_status").strip()
    if mode != "status" and result != "PASS " + mode:
        raise RuntimeError(result)
    return result


def top():
    return "\n".join(line for line in adb("shell", "dumpsys", "activity", "activities").splitlines()
                     if "mResumedActivity" in line or "topResumedActivity" in line)


def open_fixture():
    adb("shell", "am", "start", "--user", "0", "-W", "-n",
        "app.hisn.android.test/app.hisn.android.GuardFixtureActivity", "-f", "0x10008000",
        "--es", "mode", "ordinary")


def field(state, name):
    match = re.search(r"\b" + name + r" (\d+)\b", state)
    if not match:
        raise RuntimeError("Missing schedule diagnostic " + name)
    return int(match.group(1))


# These checks use generated isolated preferences, never a real owner commitment.
command("schedule-policy-test")
print("PASS isolated schedule persistence and commitment checks", flush=True)
configured = False
try:
    command("setup-schedule")
    configured = True
    before = command("status")
    start = field(before, "SCHEDULE_START")
    interventions = field(before, "SCHEDULE_INTERVENTIONS")
    periodic = field(before, "PERIODIC_INSPECTIONS")
    open_fixture()
    time.sleep(2)
    if "GuardFixtureActivity" not in top():
        raise AssertionError("Harmless fixture was not available before schedule boundary")
    print("PASS scheduled fixture initially allowed; waiting for local minute " + str(start), flush=True)
    print("Leave the static test screen open. No clock/timezone change or new screen event is sent.", flush=True)
    deadline = time.monotonic() + 150
    next_progress = time.monotonic() + 30
    while True:
        resumed = top()
        if "app.hisn.android/.RestrictionActivity" in resumed:
            break
        if "GuardFixtureActivity" not in resumed:
            raise RuntimeError("Schedule test interrupted: the fixture is no longer foreground")
        if time.monotonic() >= deadline:
            raise AssertionError("Schedule did not restrict the static fixture before test deadline")
        if time.monotonic() >= next_progress:
            print("Waiting for actual local-time boundary; fixture remains foreground", flush=True)
            next_progress += 30
        time.sleep(0.4)
    after = command("status")
    if field(after, "SCHEDULE_INTERVENTIONS") <= interventions or field(after, "PERIODIC_INSPECTIONS") <= periodic:
        raise AssertionError("No schedule-specific periodic enforcement was observed")
    print("PASS static fixture restricted at real daily boundary by periodic checking", flush=True)
    command("restore")
    configured = False
    time.sleep(1.8)
    open_fixture()
    time.sleep(2)
    if "GuardFixtureActivity" not in top():
        raise AssertionError("Restored harmless fixture remained restricted")
    print("PASS fixture allowed after restore; owner settings preserved", flush=True)
finally:
    if configured:
        command("restore")
        print("Temporary schedule fixture restored", flush=True)
