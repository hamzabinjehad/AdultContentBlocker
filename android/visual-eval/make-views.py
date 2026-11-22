#!/usr/bin/env python3
"""Turn labelled source pictures into phone-sized screens for an accuracy run.

  python3 android/visual-eval/make-views.py --labels LABELS.json --source DIR --out NEW_DIR

Each source picture becomes 1080 x 2400 screens: full, tall, feed and tile by default, and
optionally partial for adult sets (see accuracy.render). The output folder gets one PNG per screen and cases.json,
the computer-side labels for every screen. Labels never go to the phone.

A label set with adult positives must live, and be rendered, outside this repository:
the script refuses a --source or --out inside it. Source files are checked against the
sha256 in their labels before anything is rendered.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import accuracy  # noqa: E402


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("--labels", required=True, type=Path)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--views", default=",".join(accuracy.DEFAULT_VIEWS),
                        help="comma-separated subset of " + ",".join(accuracy.VIEWS)
                        + " (default " + ",".join(accuracy.DEFAULT_VIEWS) + ")")
    args = parser.parse_args()
    from PIL import Image

    labels = accuracy.load_labels(args.labels)
    views = [v for v in args.views.split(",") if v]
    if not views or any(v not in accuracy.VIEWS for v in views):
        raise SystemExit("Unknown view in --views")
    if "partial" in views and labels["kind"] != "adult":
        raise SystemExit("partial can cut the face out of the picture; people labels would no longer hold")
    if accuracy.has_adult_positives(labels):
        for name, path in (("--labels", args.labels), ("--source", args.source), ("--out", args.out)):
            if accuracy.inside_repo(path):
                raise SystemExit(name + " is inside the repository; adult material must stay outside it")
    args.out.mkdir(parents=True, exist_ok=False)

    cases = []
    for case in labels["cases"]:
        path = args.source / case["file"]
        if accuracy.sha256_file(path) != case["sha256"]:
            raise SystemExit("Source digest mismatch for case " + case["id"])
        with Image.open(path) as opened:
            opened.load()
            source = opened.convert("RGB")
        for view in views:
            view_id = "%s__%s" % (case["id"], view)
            target = args.out / (view_id + ".png")
            accuracy.render(source, view).save(target, format="PNG", optimize=False)
            entry = {"id": view_id, "source": case["id"], "view": view, "sha256": accuracy.sha256_file(target)}
            for key in ("expected", "category", "people", "attributes"):
                if key in case:
                    entry[key] = case[key]
            cases.append(entry)
        source.close()

    labels_digest = hashlib.sha256(args.labels.read_bytes()).hexdigest()
    (args.out / "cases.json").write_text(json.dumps(
        {"schema": 1, "kind": labels["kind"], "labels_sha256": labels_digest, "views": views,
         "cases": cases}, indent=1) + "\n", encoding="utf-8")
    print("Rendered", len(cases), "screens from", len(labels["cases"]), "pictures into", args.out)


if __name__ == "__main__":
    main()
