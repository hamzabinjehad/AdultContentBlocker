#!/usr/bin/env python3
"""Explicit DNS-route probe on an owner-approved Hisn VPN; no permission grants or adult sites."""
import argparse
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", required=True)
parser.add_argument("--start-approved-vpn", action="store_true",
                    help="Start Hisn only if Android VPN approval already exists")
parser.add_argument("--confirm-example-com", action="store_true",
                    help="Acknowledge the fixed example.com query sent after the synthetic local block passes")
parser.add_argument("--simulate-network-change", action="store_true",
                    help="After a pass, simulate Hisn's network callback, require stale status, then repeat the check; does not toggle Wi-Fi/data")
args = parser.parse_args()
if not args.confirm_example_com:
    parser.error("Review the probe and supply --confirm-example-com to send its public test query")


def adb(*parts):
    return subprocess.run([args.adb, "-s", args.serial, *parts], check=True,
                          capture_output=True, text=True, timeout=30).stdout


def command(mode):
    adb("shell", "run-as", "app.hisn.android", "rm", "-f", "files/hisn_guard_test_status")
    adb("shell", "am", "start", "--user", "0", "-W", "-n",
        "app.hisn.android/.GuardTestSetupActivity", "--es", "mode", mode)
    result = adb("shell", "run-as", "app.hisn.android", "cat", "files/hisn_guard_test_status").strip()
    if mode != "status" and result != "PASS " + mode:
        raise RuntimeError(result)
    return result


if args.start_approved_vpn:
    command("network-start")
deadline = time.monotonic() + 20
while True:
    state = command("status")
    if " VPN RUNNING " in state or " VPN DEGRADED " in state:
        break
    if time.monotonic() >= deadline:
        raise RuntimeError("Hisn VPN not ready; review its consent and status on the phone")
    time.sleep(0.25)
def check_route():
    command("network-check")
    deadline = time.monotonic() + 30
    while True:
        state = command("status")
        if " DNS_CHECK PASSED " in state:
            if " DNS_LOCAL_BLOCK true " not in state:
                raise AssertionError("Probe did not verify its local-block step")
            if " DNS_TCP_CHECK true " not in state:
                raise AssertionError("TCP route check did not pass: " + state)
            print("PASS: UDP and reused TCP each blocked the synthetic name and resolved example.com through Hisn")
            return
        if " DNS_CHECK FAILED " in state or " DNS_CHECK STALE " in state:
            raise RuntimeError("DNS route probe failed or was invalidated: " + state)
        if time.monotonic() >= deadline:
            raise RuntimeError("DNS route probe did not complete before the test deadline")
        time.sleep(0.25)


check_route()
if args.simulate_network_change:
    command("network-change")
    state = command("status")
    if " DNS_CHECK STALE " not in state or " DNS_LOCAL_BLOCK false " not in state:
        raise AssertionError("Network event did not invalidate the earlier pass: " + state)
    if " NETWORK_MONITOR true" not in state:
        raise AssertionError("Underlying-network monitor is not registered: " + state)
    print("PASS: simulated network callback discarded the old pass without disconnecting the VPN")
    check_route()
    command("network-old-failure")
    state = command("status")
    if " DNS_CHECK PASSED " not in state:
        raise AssertionError("An obsolete request invalidated the fresh check: " + state)
    print("PASS: simulated old-network failure did not overwrite the new passed check")
print("Scope: local TUN DNS route and resolver only, not all apps or all traffic")
if args.simulate_network_change:
    print("Transition was simulated inside Hisn; actual Wi-Fi/mobile handover remains a separate test")
