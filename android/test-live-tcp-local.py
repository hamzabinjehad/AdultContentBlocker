#!/usr/bin/env python3
"""Two pipelined local-block queries over native Android TCP. Debug app UID; no public DNS."""
import argparse
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", required=True)
args = parser.parse_args()

def adb(*parts):
    return subprocess.run([args.adb, "-s", args.serial, *parts], check=True,
                          capture_output=True, text=True, timeout=20).stdout


adb("shell", "run-as", "app.hisn.android", "rm", "-f", "files/hisn_guard_test_status")
adb("shell", "am", "start", "--user", "0", "-W", "-n", "app.hisn.android/.GuardTestSetupActivity",
    "--es", "mode", "network-local-tcp")
deadline = time.monotonic() + 15
while True:
    state = adb("shell", "run-as", "app.hisn.android", "cat", "files/hisn_guard_test_status").strip()
    if state == "PASS network-local-tcp":
        break
    if state != "PENDING network-local-tcp" or time.monotonic() >= deadline:
        raise RuntimeError("Native local TCP check failed: " + state)
    time.sleep(0.25)
print("PASS: two pipelined synthetic local blocks returned over one native Android TCP connection")
print("No internet or public resolver was used; this does not test allowed-domain resolution")
