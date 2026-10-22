#!/usr/bin/env python3
"""Harmless capture acceptance in the isolated visualcheck app; never grants permissions."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

OWNER = "app.hisn.android.visualcheck"
FIXTURE = OWNER + ".test"
CONTROL = OWNER + "/app.hisn.android.VisualCaptureTestActivity"
SCENE = FIXTURE + "/app.hisn.android.GuardFixtureActivity"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    parser.add_argument("--configure", action="store_true", help="Configure only the isolated app, then stop")
    parser.add_argument("--confirm-synthetic-intervention", action="store_true")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    def adb(*command):
        result = subprocess.run([args.adb, "-s", args.serial, *command], text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=25)
        if result.returncode:
            raise RuntimeError("ADB operation failed: " + " ".join(command[:3]) + ": " + result.stderr.strip())
        if command[:3] == ("shell", "am", "start") and "Error:" in result.stdout:
            raise RuntimeError("Android could not open the isolated test activity")
        return result.stdout

    def control(action, *extra):
        nonce = uuid.uuid4().hex
        adb("shell", "am", "start", "--user", "0", "--activity-clear-task", "-W", "-n", CONTROL,
            "--es", "action", action, "--es", "probeNonce", nonce, *extra)
        # Read a diagnostics-only preference, never the owner's rules or pixels.
        result = None
        for attempt in range(15):
            xml = adb("shell", "run-as", OWNER, "cat", "shared_prefs/hisn_visual_probe.xml")
            result = ET.fromstring(xml).find("string[@name='result']")
            if result is not None and result.text and (" NONCE " + nonce) in result.text:
                break
            time.sleep(0.2)
        if result is None or not result.text or not result.text.startswith("OK "):
            raise RuntimeError("Isolated test controller failed: " + (result.text if result is not None else "no result"))
        values = dict(re.findall(r"([A-Z_]+) ([A-Za-z0-9_-]+)", result.text))
        if values.get("OK") != action or values.get("NONCE") != nonce:
            raise RuntimeError("Controller did not acknowledge the requested action")
        for key in ("CAPTURES", "REQUESTS", "SAMPLES", "INTERVENTIONS", "DISCARDED", "CAPTURE_FAILURES", "CAPTURE_ERROR"):
            values[key] = int(values[key])
        return values

    def scene(mode, seconds, restriction=False):
        adb("shell", "am", "start", "--user", "0", "--activity-clear-task", "-W", "-n", SCENE, "--es", "mode", mode)
        time.sleep(seconds)
        if restriction:
            activities = adb("shell", "dumpsys", "activity", "activities")
            foreground = [line for line in activities.splitlines() if "ResumedActivity" in line]
            require(any(OWNER + "/app.hisn.android.RestrictionActivity" in line for line in foreground),
                    "The synthetic intervention did not show its restriction screen")
        return control("status")

    def require(condition, message):
        if not condition:
            raise RuntimeError(message)

    # The package is constant, not an argument. This runner cannot target the normal owner app.
    path = adb("shell", "pm", "path", "--user", "0", OWNER)
    require(path.startswith("package:"), "Install the separate -PhisnIsolatedVisualCheck=true APK first")
    if args.configure:
        configured = control("configure")
        print("Configured Hisn Visual Test for the harmless fixture only.", flush=True)
        print("Enable only Hisn Visual Test in Android accessibility settings, then rerun this driver.", flush=True)
        print(json.dumps(configured), flush=True)
        return

    require(args.confirm_synthetic_intervention,
            "Use --confirm-synthetic-intervention: one harmless frame will simulate a block after actual inference")
    initial = control("status")
    require(initial.get("CONNECTED") == "true",
            "Android accessibility permission is required for the separate Hisn Visual Test service; the runner does not enable it")
    require(initial.get("ENABLED") == "true", "Run --configure first")
    checks = []
    def passed(check):
        checks.append(check)
        print("PASS: " + check["check"], flush=True)
    try:
        normal = None
        for attempt in range(4):
            normal = scene("visual-normal", 20 if attempt == 0 else 10)
            if normal["SAMPLES"] > initial["SAMPLES"]:
                break
        require(normal["SAMPLES"] > initial["SAMPLES"], "No actual screenshot/model sample completed: " + json.dumps(normal))
        require(normal["INTERVENTIONS"] == initial["INTERVENTIONS"], "Benign synthetic image was restricted")
        passed({"check": "actual capture and benign model inference", "samples": normal["SAMPLES"] - initial["SAMPLES"]})

        moving = scene("visual-video", 8)
        require(moving["SAMPLES"] > normal["SAMPLES"], "Moving synthetic frames were not sampled")
        require(moving["INTERVENTIONS"] == normal["INTERVENTIONS"], "Benign moving frames were restricted")
        passed({"check": "benign moving frame sampling", "samples": moving["SAMPLES"] - normal["SAMPLES"]})

        editable = scene("editable", 6)
        require(editable["STATE"] == "INPUT" and editable["REQUESTS"] == moving["REQUESTS"],
                "Focused editable field was not paused: " + json.dumps(editable))
        passed({"check": "focused editable field pauses capture"})

        focused = scene("password", 6)
        require(focused["STATE"] == "INPUT" and focused["REQUESTS"] == editable["REQUESTS"],
                "Focused password field was not paused: " + json.dumps(focused))
        passed({"check": "focused password pauses capture"})

        secure = scene("visual-secure", 6)
        require(secure["CAPTURE_FAILURES"] > focused["CAPTURE_FAILURES"] and secure["SAMPLES"] == focused["SAMPLES"],
                "Protected window was not reported unreadable: " + json.dumps(secure))
        passed({"check": "secure window remains unreadable", "android_error": secure["CAPTURE_ERROR"]})

        control("force-once", "--ez", "confirmSynthetic", "true")
        # A real benign screenshot must first reach an actual allow verdict; only then can the debug hook act.
        forced = scene("visual-normal", 8, restriction=True)
        require(forced["INTERVENTIONS"] == secure["INTERVENTIONS"] + 1,
                "Synthetic one-shot intervention did not run: " + json.dumps(forced))
        passed({"check": "synthetic intervention after actual capture and inference", "accuracy_evidence": False})

        resumed = scene("visual-normal", 8)
        require(resumed["SAMPLES"] > forced["SAMPLES"] and resumed["INTERVENTIONS"] == forced["INTERVENTIONS"],
                "Synthetic lease repeated or ordinary capture did not recover")
        passed({"check": "one-shot expires; ordinary capture recovers"})

        adb("shell", "input", "keyevent", "3")
        time.sleep(6)
        left = control("status")
        require(left["REQUESTS"] == resumed["REQUESTS"], "Capture continued outside selected fixture")
        passed({"check": "leaving selected app stops capture"})

        control("stop")
        stopped = scene("visual-normal", 6)
        require(stopped["STATE"] == "OFF" and stopped["REQUESTS"] == left["REQUESTS"], "Disabled layer still captured")
        passed({"check": "opt-out stops capture and model"})
        report = {"passed": True, "package": OWNER, "checks": checks,
                  "limits": "Synthetic fixtures only; no adult-content accuracy, real-app or battery benchmark."}
        if args.report:
            args.report.write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report, indent=2), flush=True)
    finally:
        try:
            control("stop")
        except Exception:
            # If the diagnostics controller fails, stop only the isolated package.
            # Do not leave a test capture session running or alter the owner's app.
            adb("shell", "am", "force-stop", "--user", "0", OWNER)
            raise


if __name__ == "__main__":
    main()
