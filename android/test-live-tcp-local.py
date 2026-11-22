#!/usr/bin/env python3
"""Two pipelined local-block queries over native Android TCP. Debug app UID; no public DNS."""
import argparse
import re
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", required=True)
parser.add_argument("--family", choices=("ipv4", "ipv6", "both"), default="ipv4")
parser.add_argument("--backlog", action="store_true",
                    help="Also check local UDP and TCP blocks while every resolver slot is occupied")
args = parser.parse_args()

def adb(*parts):
    return subprocess.run([args.adb, "-s", args.serial, *parts], check=True,
                          capture_output=True, text=True, timeout=20).stdout


def run(mode, timed=False):
    adb("shell", "run-as", "app.hisn.android", "rm", "-f", "files/hisn_guard_test_status")
    adb("shell", "am", "start", "--user", "0", "-W", "-n", "app.hisn.android/.GuardTestSetupActivity",
        "--es", "mode", mode)
    deadline = time.monotonic() + 15
    while True:
        state = adb("shell", "run-as", "app.hisn.android", "cat", "files/hisn_guard_test_status").strip()
        if timed:
            match = re.fullmatch(r"PASS " + mode + r" ELAPSED_MS (\d+)", state)
            if match:
                return int(match[1])
        elif state == "PASS " + mode:
            return None
        if state != "PENDING " + mode or time.monotonic() >= deadline:
            raise RuntimeError("Native local TCP check failed: " + state)
        time.sleep(0.25)


def check(family):
    mode = "network-local-tcp6" if family == "ipv6" else "network-local-tcp"
    run(mode)
    print(f"PASS: two synthetic local blocks returned over one native Android TCP connection ({family})")
    if family == "ipv6":
        print("PASS: the tunnel refused the IPv6 Private DNS connection on port 853")


for family in ("ipv4", "ipv6") if args.family == "both" else (args.family,):
    check(family)
if args.backlog:
    duration = run("network-local-backlog", timed=True)
    print(f"PASS: IPv4 and IPv6 UDP/TCP local blocks while the resolver queue was full ({duration} ms total)")
print("No internet or public resolver was used; this does not test allowed-domain resolution")
