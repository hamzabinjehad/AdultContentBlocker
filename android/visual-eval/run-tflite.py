#!/usr/bin/env python3
"""Benchmark the debug-only native TFLite prototype on supplied, labelled files."""
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
ACTIVITY = OWNER + "/app.hisn.android.TfliteBenchmarkActivity"
MODEL_SHA256 = "01215075f6e759bac8931310ed95665295bd662eff547f27a33982bcf2cb4d46"


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
            "limits": "Native debug prototype on supplied benign files; no adult-positive corpus, capture, battery or population accuracy."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--corpus", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--backend", choices=("cpu",), default="cpu")
    args = parser.parse_args()
    raw_manifest = (args.corpus / "manifest.json").read_bytes()
    manifest = json.loads(raw_manifest)
    if manifest["schema"] != 1 or not 1 <= len(manifest["cases"]) <= 64:
        raise ValueError("Invalid manifest")
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
    staging = "/data/local/tmp/hisn-tflite-" + uuid.uuid4().hex

    def adb(*command):
        result = subprocess.run([args.adb, "-s", args.serial, *command], text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
        if result.returncode:
            raise RuntimeError("ADB command failed: " + " ".join(command[:3]) + ": " + result.stderr.strip())
        return result.stdout

    if not adb("shell", "pm", "path", "--user", "0", OWNER).startswith("package:"):
        raise RuntimeError("Install the -PhisnIsolatedVisualCheck=true debug APK first")
    completed = False
    try:
        adb("shell", "mkdir", "-p", staging)
        adb("shell", "run-as", OWNER, "mkdir", "-p", "files/visual_benchmark")
        for name in ["manifest.json"] + [case["id"] + ".png" for case in cases]:
            adb("push", str(args.corpus / name), staging + "/" + name)
            adb("shell", "run-as", OWNER, "cp", staging + "/" + name, "files/visual_benchmark/" + name)
        adb("shell", "run-as", OWNER, "rm", "-f", "files/tflite_benchmark_result.json")
        adb("shell", "am", "force-stop", "--user", "0", OWNER)
        time.sleep(1)
        adb("shell", "am", "start", "--user", "0", "--activity-clear-task", "-W", "-n", ACTIVITY,
            "--es", "backend", args.backend)
        deadline = time.monotonic() + 90 + len(cases) * manifest["repeats"] * (manifest["interval_ms"] / 1000 + 15)
        seen = -1
        while time.monotonic() < deadline:
            time.sleep(5)
            result = json.loads(adb("shell", "run-as", OWNER, "cat", "files/tflite_benchmark_result.json"))
            if result.get("model_sha256") != MODEL_SHA256:
                raise RuntimeError("Device report model digest mismatch")
            if result.get("requested_backend") != args.backend:
                raise RuntimeError("Device report backend mismatch")
            if result.get("actual_backend") not in (args.backend, "cpu", "tflite-cpu"):
                raise RuntimeError("Device report backend unavailable")
            count = len(result.get("results", []))
            if count != seen:
                print("Completed", count, "/", len(cases) * manifest["repeats"], "native calls", flush=True)
                seen = count
            if result["state"] in ("failed", "cancelled"):
                raise RuntimeError("Benchmark " + result["state"] + ": " + str(result.get("error")))
            if result["state"] == "complete":
                if count != len(cases) * manifest["repeats"]:
                    raise RuntimeError("Incomplete result count")
                expected = {case["id"]: case["expected"] for case in cases}
                for row in result["results"]:
                    if row["id"] not in expected or row["expected"] != expected[row["id"]]:
                        raise RuntimeError("Unexpected device result")
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
