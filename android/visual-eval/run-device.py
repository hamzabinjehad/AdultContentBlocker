#!/usr/bin/env python3
"""Evaluate supplied, labelled images in the isolated Android app, with no capture permission."""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
import math
from pathlib import Path
import re
import statistics
import subprocess
import time
import uuid

OWNER = "app.hisn.android.visualcheck"
ACTIVITY = OWNER + "/app.hisn.android.VisualBenchmarkActivity"


def summary(report, manifest):
    groups = defaultdict(list)
    for item in report["results"]:
        groups[item["id"]].append(item)
    confusion = Counter()
    for rows in groups.values():
        row = rows[0]
        if row["actual"] not in ("allow", "block"):
            confusion["unknown"] += 1
        elif row["expected"] == "block":
            confusion["true_positive" if row["actual"] == "block" else "false_negative"] += 1
        else:
            confusion["false_positive" if row["actual"] == "block" else "true_negative"] += 1
    times = sorted(row["roundtrip_ms"] for row in report["results"] if row["actual"] in ("allow", "block"))
    positive = sum(row["expected"] == "block" for row in manifest["cases"])
    return {"unique_source_images": manifest.get("unique_sources"), "unique_views": len(groups),
            "inference_calls": len(report["results"]), "positive_views": positive,
            "first_verdict_confusion_by_view": {key: confusion[key] for key in
                ("true_positive", "false_negative", "true_negative", "false_positive", "unknown")},
            "inconsistent_views": sum(len({row["actual"] for row in rows}) > 1 for rows in groups.values()),
            "unknown_calls": sum(row["actual"] not in ("allow", "block") for row in report["results"]),
            "recall": None if not positive else confusion["true_positive"] / positive,
            "roundtrip_median_ms": statistics.median(times) if times else None,
            "roundtrip_p95_ms": times[math.ceil(len(times) * 0.95)-1] if times else None,
            "roundtrip_max_ms": max(times) if times else None,
            "limits": "Paired layouts are correlated. First verdict per view is counted once; repeats measure stability/latency. No population accuracy or battery-life estimate."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--corpus", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--confirm-reviewed-corpus", action="store_true")
    parser.add_argument("--backend", choices=("auto", "cpu", "webgl"), default="auto")
    args = parser.parse_args()
    raw_manifest = (args.corpus / "manifest.json").read_bytes()
    if len(raw_manifest) > 131072:
        raise ValueError("Manifest exceeds bounds")
    manifest = json.loads(raw_manifest)
    if manifest["schema"] != 1 or not 1 <= len(manifest["cases"]) <= 64:
        raise ValueError("Invalid manifest")
    if manifest.get("kind") != "benign-controls" and not args.confirm_reviewed_corpus:
        raise ValueError("Custom corpus requires --confirm-reviewed-corpus and reviewed labels/provenance")
    cases = manifest["cases"]
    if not 1 <= manifest["repeats"] <= 10 or len(cases) * manifest["repeats"] > 240 or not 1000 <= manifest["interval_ms"] <= 5000:
        raise ValueError("Benchmark exceeds bounds")
    ids = set()
    for case in cases:
        key = case["id"]
        if not re.fullmatch(r"[a-zA-Z0-9_-]{1,64}", key) or key in ids or case["expected"] not in ("allow", "block"):
            raise ValueError("Invalid case")
        ids.add(key)
        image = (args.corpus / (key + ".png")).read_bytes()
        if len(image) > 2097152 or hashlib.sha256(image).hexdigest() != case["sha256"]:
            raise ValueError("Image integrity failed: " + key)
    expected_digest = hashlib.sha256(raw_manifest).hexdigest()
    staging = "/data/local/tmp/hisn-vbench-" + uuid.uuid4().hex

    def adb(*command):
        result = subprocess.run([args.adb, "-s", args.serial, *command], text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
        if result.returncode:
            raise RuntimeError("ADB command failed: " + " ".join(command[:3]))
        if command[:3] == ("shell", "am", "start") and "Error:" in result.stdout:
            raise RuntimeError("Benchmark activity unavailable")
        return result.stdout

    if not adb("shell", "pm", "path", "--user", "0", OWNER).startswith("package:"):
        raise RuntimeError("Install only the -PhisnIsolatedVisualCheck=true APK first")
    completed = False
    try:
        adb("shell", "mkdir", "-p", staging)
        adb("shell", "run-as", OWNER, "mkdir", "-p", "files/visual_benchmark")
        for name in ["manifest.json"] + [case["id"] + ".png" for case in cases]:
            adb("push", str(args.corpus / name), staging + "/" + name)
            adb("shell", "run-as", OWNER, "cp", staging + "/" + name, "files/visual_benchmark/" + name)
        # Delete only this test's old report so an identical manifest cannot retrieve an old run.
        adb("shell", "run-as", OWNER, "rm", "-f", "files/visual_benchmark_result.json")
        adb("shell", "am", "start", "--user", "0", "--activity-clear-task", "-W", "-n", ACTIVITY,
            "--es", "backend", args.backend)
        deadline = time.monotonic() + 90 + len(cases) * manifest["repeats"] * (manifest["interval_ms"] / 1000 + 15)
        seen = -1
        while time.monotonic() < deadline:
            time.sleep(5)
            result = json.loads(adb("shell", "run-as", OWNER, "cat", "files/visual_benchmark_result.json"))
            if result.get("manifest_sha256") != expected_digest:
                raise RuntimeError("Device report does not match supplied corpus")
            if result.get("requested_backend") != args.backend:
                raise RuntimeError("Device report does not match requested backend")
            count = len(result.get("results", []))
            if count != seen:
                print("Completed", count, "/", len(cases) * manifest["repeats"], "actual model calls", flush=True)
                seen = count
            if result["state"] in ("failed", "cancelled"):
                raise RuntimeError("Benchmark " + result["state"] + ": " + str(result.get("error")))
            if result["state"] == "complete":
                if count != len(cases) * manifest["repeats"]:
                    raise RuntimeError("Incomplete result count")
                expected = {case["id"]: case["expected"] for case in cases}
                repetitions = Counter()
                for row in result["results"]:
                    if row["id"] not in expected or row["expected"] != expected[row["id"]]:
                        raise RuntimeError("Unexpected device result")
                    repetitions[(row["id"], row["repeat"])] += 1
                if any(repetitions[(key, repeat)] != 1 for key in expected for repeat in range(manifest["repeats"])):
                    raise RuntimeError("Duplicate or missing case repetitions")
                result["summary"] = summary(result, manifest)
                result["corpus"] = manifest
                args.report.write_text(json.dumps(result, indent=2) + "\n")
                print(json.dumps(result["summary"], indent=2), flush=True)
                completed = True
                return
        raise TimeoutError("Device benchmark timed out")
    finally:
        if not completed:
            adb("shell", "am", "force-stop", "--user", "0", OWNER)
        adb("shell", "run-as", OWNER, "rm", "-rf", "files/visual_benchmark")
        adb("shell", "rm", "-rf", staging)


if __name__ == "__main__":
    main()
