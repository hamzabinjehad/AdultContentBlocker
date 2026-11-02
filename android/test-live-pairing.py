#!/usr/bin/env python3
"""Run the Java pairing and policy verifier on a connected debug build.

    python3 android/test-live-pairing.py --serial SERIAL [--apk APK]

The debug-only GuardianPairDebugActivity uses a software P-256 key wrapped by
an AES-256-GCM key in AndroidKeyStore. It proves the wire format, JCA pairing,
identity persistence and version-2 policy verification on the Android runtime;
it is not an EC key inside the Keystore and not the product QR UI.

The script creates a guardian key and offer with script/guardian_protocol.py,
starts the activity, reads its response through run-as, verifies it with the
guardian key, signs a policy bound to the same device key, and has the activity
verify that policy. With --apply-policy it also requires the activity to apply
the tighten-only managed rules and read them back. It exits non-zero on any
mismatch.
"""
from __future__ import annotations

import argparse
import base64
import json
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "script"))
import guardian_protocol as gp  # noqa: E402

PACKAGE = "app.hisn.android"
ACTIVITY = f"{PACKAGE}/.GuardianPairDebugActivity"
NOW = int(time.time())


def fail(message: str) -> None:
    print("FAIL:", message, file=sys.stderr)
    raise SystemExit(1)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", help="install this APK before testing")
    parser.add_argument("--apply-policy", action="store_true",
                        help="sign managed rules and require the activity to apply them; needs device owner")
    parser.add_argument("--block-app", default="com.android.chrome",
                        help="package to add through --apply-policy (default: com.android.chrome)")
    args = parser.parse_args()

    def adb(*parts: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        command = [args.adb, "-s", args.serial, *parts]
        return subprocess.run(command, check=check, capture_output=True, text=True, timeout=60)

    if args.apk:
        install = adb("install", "-r", args.apk, check=False)
        if install.returncode != 0:
            fail(f"install failed: {(install.stderr or install.stdout).strip()}")
    package = adb("shell", "dumpsys", "package", PACKAGE, check=False)
    if package.returncode != 0 or "versionName" not in package.stdout:
        fail(f"{PACKAGE} is not installed")

    guardian = gp.generate_key()
    offer = gp.build_offer(guardian.public_key(), nonce=b"pair-live-000001",
                           label="Live pairing")
    offer_b64 = base64.b64encode(offer.uri.encode()).decode()

    adb("shell", "run-as", PACKAGE, "rm", "-f", "files/pairing-response.json",
        "files/pairing-sas.txt", "files/pairing-result.txt", "files/pairing-policy.json",
        check=False)
    start = adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--es", "offer_b64", offer_b64,
                check=False)
    if start.returncode != 0:
        fail(f"could not start pairing activity: {start.stderr.strip()}")
    response_text = adb("shell", "run-as", PACKAGE, "cat", "files/pairing-response.json",
                        check=False)
    if response_text.returncode != 0 or not response_text.stdout.strip():
        fail("pairing response file missing")
    try:
        response = json.loads(response_text.stdout.strip())
    except json.JSONDecodeError as exc:
        fail(f"pairing response is not JSON: {exc}")
    try:
        device, sas = gp.verify_response(offer, response, guardian)
    except gp.ProtocolError as exc:
        fail(f"guardian rejected the Java response: {exc}")
    device_id = gp.device_id(device)
    sas_file = adb("shell", "run-as", PACKAGE, "cat", "files/pairing-sas.txt", check=False)
    if sas_file.returncode != 0 or sas_file.stdout.strip() != sas:
        fail(f"device SAS file mismatch: {sas_file.stdout.strip()!r} vs {sas!r}")
    print(f"pairing PASS device={device_id} sas={sas}")

    rules = {"strict": True, "blocked_hosts_sha256": "00"}
    if args.apply_policy:
        rules.update({"keep_installed": True, "lockdown": True, "suspend_blocked": True,
                      "blocked_apps": [args.block_app],
                      "blocked_domains": ["example.com"]})
        rules["blocked_domains_sha256"] = gp.domain_list_sha256(rules["blocked_domains"])
    policy = gp.sign_policy(guardian, device=device_id, counter=1, issued_at=NOW,
                            not_after=NOW + 3600, rules=rules)
    policy_b64 = base64.b64encode(json.dumps(policy, separators=(",", ":")).encode()).decode()
    start = adb("shell", "am", "start", "-W", "-n", ACTIVITY,
                "--es", "offer_b64", offer_b64,
                "--es", "policy_b64", policy_b64,
                "--el", "now", str(NOW), check=False)
    if start.returncode != 0:
        fail(f"could not start policy activity: {start.stderr.strip()}")
    result = adb("shell", "run-as", PACKAGE, "cat", "files/pairing-result.txt", check=False)
    if result.returncode != 0 or "PASS" not in result.stdout:
        fail(f"activity did not pass: {(result.stderr or result.stdout).strip()}")
    policy_text = adb("shell", "run-as", PACKAGE, "cat", "files/pairing-policy.json",
                      check=False)
    if policy_text.returncode != 0 or not policy_text.stdout.strip():
        fail("verified policy file missing")
    try:
        rules = json.loads(policy_text.stdout)
    except json.JSONDecodeError as exc:
        fail(f"verified rules are not JSON: {exc}")
    if rules.get("strict") is not True:
        fail(f"verified rules changed: {rules}")
    print("policy PASS rules=" + json.dumps(rules, sort_keys=True))
    if args.apply_policy:
        applied_text = adb("shell", "run-as", PACKAGE, "cat", "files/pairing-applied.json",
                           check=False)
        if applied_text.returncode != 0 or not applied_text.stdout.strip():
            fail("applied-policy file missing")
        try:
            applied = json.loads(applied_text.stdout)
        except json.JSONDecodeError as exc:
            fail(f"applied policy is not JSON: {exc}")
        if not applied.get("owner") or not applied.get("sync"):
            fail(f"policy did not apply to a device owner: {applied}")
        if not (applied.get("lockdown") and applied.get("strict") and applied.get("suspend")):
            fail(f"managed choices were not tightened: {applied}")
        if args.block_app not in applied.get("blockedApps", []):
            fail(f"blocked app missing from applied policy: {applied}")
        if "example.com" not in applied.get("blockedDomains", []):
            fail(f"blocked domain missing from applied policy: {applied}")
        print("apply PASS " + json.dumps(applied, sort_keys=True))
    identity = "unknown"
    for token in result.stdout.split():
        if token.startswith("identity="):
            identity = token.split("=", 1)[1]
    print(f"identity: {identity} (EC in AndroidKeyStore on API 31+; AES-wrapped fallback below)")
    print("The camera QR flow and laptop-side response/policy transport are not covered by this harness.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
