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
parser.add_argument("--case", choices=("ordinary", "editable", "password", "word", "long", "wide", "app",
                    "rapid", "heartbeat", "adult", "stress", "split-context", "live-update", "text-updates"))
args = parser.parse_args()


def adb(*parts):
    return subprocess.run([args.adb, "-s", args.serial, *parts], check=True,
                          capture_output=True, text=True, timeout=30).stdout


def fixture_report(mode):
    # Remove only the previous diagnostic status, never policy files.
    adb("shell", "run-as", "app.hisn.android", "rm", "-f", "files/hisn_guard_test_status")
    adb("shell", "am", "start", "--user", "0", "-W", "-n",
        "app.hisn.android/.GuardTestSetupActivity", "--es", "mode", mode)
    deadline = time.monotonic() + 5
    prefix = "CONNECTED " if mode == "status" else "PASS " + mode
    while True:
        try:
            status = adb("shell", "run-as", "app.hisn.android", "cat", "files/hisn_guard_test_status").strip()
        except subprocess.CalledProcessError as error:
            if "No such file" not in error.stderr:
                raise
            status = ""
        if status.startswith(prefix) or status.startswith("FAIL "):
            return status
        if time.monotonic() >= deadline:
            raise RuntimeError("Fixture did not report " + mode + (": " + status if status else ""))
        time.sleep(0.15)


def configure(mode):
    status = fixture_report(mode)
    if status != "PASS " + mode:
        raise RuntimeError(status)


def resumed():
    return "\n".join(line for line in adb("shell", "dumpsys", "activity", "activities").splitlines()
                     if "mResumedActivity" in line or "topResumedActivity" in line)


def status():
    return fixture_report("status")


def text_updates():
    fixture("stale-label-event", False)
    initial = int(re.search(r"FAST_TEXT (\d+)", status()).group(1))
    fixture("late-label", True)
    final = int(re.search(r"FAST_TEXT (\d+)", status()).group(1))
    if final <= initial:
        raise AssertionError("A changed visible label did not exercise the event fast path")
    print("PASS live label update restricted through the event fast path", flush=True)


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


def live_update():
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    configure("block-app-later")
    adb("shell", "am", "start", "--user", "0", "-W", "-n",
        "app.hisn.android.test/app.hisn.android.GuardFixtureActivity",
        "-f", "0x10008000", "--es", "mode", "ordinary")
    if "GuardFixtureActivity" not in resumed():
        raise AssertionError("Fixture did not start before the rule changed")
    print("PASS fixture allowed before the rule was saved", flush=True)
    deadline = time.monotonic() + 9
    while time.monotonic() < deadline:
        report = adb("shell", "run-as", "app.hisn.android", "cat", "files/hisn_guard_test_status").strip()
        if report.startswith("FAIL "):
            raise AssertionError(report)
        if report == "APPLIED block-app-later" and "app.hisn.android/.RestrictionActivity" in resumed():
            print("PASS new saved rule restricted the still-open app without another screen event from the fixture", flush=True)
            return
        time.sleep(0.15)
    raise AssertionError("New app rule did not restrict the static fixture: " + status())


def fixture(mode, blocked):
    # Start each independent case from the launcher so Android publishes a new
    # foreground window even when the preceding case used the same activity.
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
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
    if args.case == "adult":
        configure("adult-policy-test")
        print("PASS actual APK compact-spelling/context policy and literal-rule independence", flush=True)
    # No instrumentation: it restarts the app and disconnects Android accessibility.
    adult_case = args.case in ("adult", "split-context", "text-updates")
    setup = "setup-live-app" if args.case == "live-update" else ("setup-adult" if adult_case else "setup")
    configure(setup)
    try:
        if args.case == "live-update":
            live_update()
        if adult_case:
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
        if args.case == "split-context":
            fixture("split-context", False)
            fixture("split-context-block", True)
        if args.case == "text-updates":
            text_updates()
        if args.case == "adult":
            fixture("medical", False)
            fixture("protective", False)
            fixture("recovery", False)
            fixture("adult-en", True)
            fixture("adult-ar", True)
            fixture("adult-compact-en", True)
            fixture("adult-compact-ar", True)
            fixture("adult-label-en", True)
            fixture("adult-label-ar", True)
        for case in ("ordinary", "editable", "password"):
            if args.case is None or args.case == case:
                fixture(case, False)
        if args.case is None or args.case == "heartbeat":
            heartbeat_test()
        for case in ("word", "long", "wide", "stress"):
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
