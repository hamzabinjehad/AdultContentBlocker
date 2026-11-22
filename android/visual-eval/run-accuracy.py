#!/usr/bin/env python3
"""Run the production image check on rendered screens and score it against the labels.

  python3 android/visual-eval/run-accuracy.py --serial SERIAL --views DIR \
      --mode adult|people-all|people-female|people-male --report REPORT.json [--markdown OUT.md]

DIR is a make-views.py output. Only ids, digests and PNGs go to the phone; the labels in
cases.json stay here and are joined with the verdicts afterwards. The phone deletes each file
once judged, and this script removes the staging and app folders whether or not the run
finishes. The report holds counts, ids and labels, never pixels or paths.

Adult positives run only on an emulator unless --allow-physical-device is passed. Use a
disposable AVD for them and delete it afterwards: freed emulator storage can keep file
contents in the AVD's disk image on this computer.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parent))
import accuracy  # noqa: E402

OWNER = "app.hisn.android.visualcheck"
ACTIVITY = OWNER + "/app.hisn.android.AccuracyRunActivity"
RESULT = "files/visual_accuracy_result.json"


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--views", required=True, type=Path)
    parser.add_argument("--mode", required=True, choices=accuracy.MODES)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--markdown", type=Path)
    parser.add_argument("--allow-physical-device", action="store_true")
    parser.add_argument("--appearance", choices=("oarriaga", "faceres", "native"), default="native",
                        help="women/men estimate in people modes: native FaceRes (the app), FaceRes in the "
                             "WebView, or the 64x64 Oarriaga classifier")
    parser.add_argument("--evaluate-appearance", action="store_true",
                        help="also record every face's raw estimates from both models")
    args = parser.parse_args()

    corpus = json.loads((args.views / "cases.json").read_text(encoding="utf-8"))
    if corpus.get("schema") != 1:
        raise SystemExit("Unknown cases.json schema")
    if (corpus["kind"] == "adult") != (args.mode == "adult"):
        raise SystemExit("Mode %s does not fit a %s label set" % (args.mode, corpus["kind"]))
    cases = corpus["cases"]
    if not 1 <= len(cases) <= 5000:
        raise SystemExit("Between 1 and 5000 screens per run")
    for case in cases:
        if accuracy.sha256_file(args.views / (case["id"] + ".png")) != case["sha256"]:
            raise SystemExit("Screen digest mismatch: " + case["id"])
    positives = corpus["kind"] == "adult" and any(c["expected"] == "block" for c in cases)

    def adb(*command, timeout=60):
        result = subprocess.run([args.adb, "-s", args.serial, *command], text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if result.returncode:
            raise RuntimeError("ADB command failed: " + " ".join(command[:3]) + ": " + result.stderr.strip())
        return result.stdout

    emulator = adb("shell", "getprop", "ro.kernel.qemu").strip() == "1" or args.serial.startswith("emulator-")
    if positives and not emulator and not args.allow_physical_device:
        raise SystemExit("Adult positives run on a disposable emulator; pass --allow-physical-device to override")
    if not adb("shell", "pm", "path", "--user", "0", OWNER).startswith("package:"):
        raise SystemExit("Install the -PhisnIsolatedVisualCheck=true debug APK first")

    staging = "/data/local/tmp/hisn-accuracy-" + uuid.uuid4().hex
    local = args.views / (".device-" + uuid.uuid4().hex)
    completed = False
    try:
        local.mkdir()
        (local / "manifest.json").write_text(json.dumps(
            {"schema": 1, "mode": args.mode, "appearance": args.appearance, "evaluate": args.evaluate_appearance,
             "cases": [{"id": c["id"], "sha256": c["sha256"]} for c in cases]}),
            encoding="utf-8")
        adb("shell", "mkdir", "-p", staging)
        adb("push", str(local / "manifest.json"), staging + "/manifest.json")
        for case in cases:
            adb("push", str(args.views / (case["id"] + ".png")), staging + "/" + case["id"] + ".png")
        adb("shell", "am", "force-stop", "--user", "0", OWNER)
        adb("shell", "run-as", OWNER, "rm", "-rf", "files/visual_accuracy", RESULT)
        adb("shell", "run-as", OWNER, "mkdir", "-p", "files/visual_accuracy")
        # adb joins shell arguments into one remote command line, so quote the inner script.
        adb("shell", "run-as %s sh -c 'cp %s/* files/visual_accuracy/'" % (OWNER, staging), timeout=600)
        adb("shell", "rm", "-rf", staging)
        time.sleep(1)
        started = time.monotonic()
        adb("shell", "am", "start", "--user", "0", "--activity-clear-task", "-W", "-n", ACTIVITY)
        deadline = started + 180 + len(cases) * 5
        seen = -1
        while time.monotonic() < deadline:
            time.sleep(5)
            try:
                result = json.loads(adb("shell", "run-as", OWNER, "cat", RESULT))
            except (RuntimeError, ValueError):
                continue
            count = len(result.get("results", []))
            if count != seen:
                print("Judged", count, "/", len(cases), flush=True)
                seen = count
            if result["state"] in ("failed", "cancelled"):
                raise RuntimeError("Run " + result["state"] + ": " + str(result.get("error")))
            if result["state"] == "complete":
                summary = accuracy.score(args.mode, cases, result["results"])
                report = {"schema": 1, "mode": args.mode, "appearance": result.get("appearance"),
                          "device": result.get("device"),
                          "android_sdk": result.get("android_sdk"), "version": result.get("version"),
                          "backend": result.get("actual_backend"), "frame_edge": result.get("frame_edge"),
                          "model_load_ms": result.get("model_load_ms"),
                          "labels_sha256": corpus["labels_sha256"], "summary": summary,
                          "results": [{"id": r["id"], "actual": r["actual"], "strong": r["strong"],
                                       "person": r["person"], "cropped": r["cropped"],
                                       **({"faces": r["faces"]} if r.get("faces") else {})} for r in result["results"]],
                          "limits": "Still screens rendered from labelled pictures; no live capture, scrolling, "
                                    "video or second capture. Intervals are 95% Wilson; views of one picture "
                                    "are correlated, so per-picture counts are the honest sample size."}
                args.report.write_text(json.dumps(report, indent=1) + "\n", encoding="utf-8")
                if args.markdown:
                    args.markdown.write_text(accuracy.markdown(summary), encoding="utf-8")
                print(accuracy.markdown(summary), flush=True)
                completed = True
                return
        raise TimeoutError("Accuracy run timed out")
    finally:
        if not completed:
            try:
                adb("shell", "am", "force-stop", "--user", "0", OWNER)
            except Exception:
                pass
        for command in (("shell", "rm", "-rf", staging),
                        ("shell", "run-as", OWNER, "rm", "-rf", "files/visual_accuracy", RESULT)):
            try:
                adb(*command)
            except Exception as failure:
                print("Cleanup failed, remove by hand:", " ".join(command), failure, file=sys.stderr)
        for child in local.glob("*") if local.exists() else ():
            child.unlink()
        if local.exists():
            local.rmdir()


if __name__ == "__main__":
    main()
