#!/usr/bin/env python3
"""Control a 120-second additive debug deny for www.example.org. No permission grants."""
import argparse
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", required=True)
parser.add_argument("--action", choices=("start", "status", "clear"), default="status")
parser.add_argument("--confirm-harmless-example-domain", action="store_true",
                    help="Acknowledge a temporary extra deny for exactly www.example.org, not saved rules")
args = parser.parse_args()
if args.action == "start" and not args.confirm_harmless_example_domain:
    parser.error("Review the temporary deny and provide --confirm-harmless-example-domain")


def adb(*parts):
    return subprocess.run([args.adb, "-s", args.serial, *parts], check=True,
                          capture_output=True, text=True, timeout=20).stdout


def command(mode):
    # A failed/interrupted activity launch must not reuse an earlier PASS/status.
    adb("shell", "run-as", "app.hisn.android", "rm", "-f", "files/hisn_guard_test_status")
    adb("shell", "am", "start", "--user", "0", "-W", "-n",
        "app.hisn.android/.GuardTestSetupActivity", "--es", "mode", mode)
    state = adb("shell", "run-as", "app.hisn.android", "cat", "files/hisn_guard_test_status").strip()
    if mode != "status" and state != "PASS " + mode:
        raise RuntimeError(state)
    return state


if args.action != "status":
    command("browser-canary-" + args.action)
state = command("status")
marker = " BROWSER_CANARY_"
start = state.find(marker)
if start < 0:
    raise RuntimeError("Installed app lacks browser-canary diagnostics")
result = state[start + 1:]
print(result)
if args.action == "start" and "BROWSER_CANARY_ACTIVE true " not in result:
    raise RuntimeError("No active canary lease")
if args.action == "clear" and "BROWSER_CANARY_ACTIVE false " not in result:
    raise RuntimeError("Canary was not cleared")
print("Policy evaluations are not unique requests or delivered replies.")
print("Browser PASS also needs an observed DNS error for https://www.example.org/.")
print("No matching evaluations means inconclusive: caches or independent DNS may bypass this route.")
