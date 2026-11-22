#!/usr/bin/env python3
"""Download the reviewed Commons pictures and write labels for make-views.py.

  python3 android/visual-eval/fetch-commons.py --sources android/visual-eval/commons-sources.json \
      --out android/build/visual-commons

Only entries whose "review" is "ok" are fetched; "pending" stops the run, so nothing is used
unreviewed. --accept-suggestions takes a pending entry's "suggested" decision (from a pre-review
of small previews) as its review; spot-check those first. Each file is checked against the SHA-1 Commons published for it. The output folder
(new) gets the pictures, benign-labels.json (kind adult, every case expected allow) and
people-labels.json (kind people), each with the sha256 make-views.py checks, and
ATTRIBUTION.json with title, author, licence and page for every picture.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import time
import urllib.error
import urllib.request

AGENT = "HisnVisualEval/0.1 (offline accuracy tooling)"
HOSTS = ("https://upload.wikimedia.org/",)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("--sources", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--accept-suggestions", action="store_true",
                        help="treat a pending entry's 'suggested' decision (a preview pre-review) as its review")
    args = parser.parse_args()
    sources = json.loads(args.sources.read_text(encoding="utf-8"))
    entries = sources["entries"]
    if args.accept_suggestions:
        for entry in entries:
            if entry.get("review") == "pending" and entry.get("suggested") in ("ok", "rejected"):
                entry["review"] = entry["suggested"]
    pending = [e["id"] for e in entries if e.get("review") not in ("ok", "rejected")]
    if pending:
        raise SystemExit("%d entries are not reviewed yet (first: %s)" % (len(pending), pending[0]))
    chosen = [e for e in entries if e["review"] == "ok"]
    if not chosen:
        raise SystemExit("No entries are marked ok")
    for entry in chosen:
        if not entry["url"].startswith(HOSTS) or not re.fullmatch(r"[0-9a-f]{40}", entry["sha1"]):
            raise SystemExit("Unexpected source for " + entry["id"])
    args.out.mkdir(parents=True, exist_ok=False)

    benign, people, attribution = [], [], []
    for number, entry in enumerate(chosen, 1):
        extension = ".png" if entry["url"].lower().endswith(".png") else ".jpg"
        target = args.out / (entry["id"] + extension)
        for attempt in range(5):
            time.sleep(1.0 + 4 * attempt)
            try:
                request = urllib.request.Request(entry["url"], headers={"User-Agent": AGENT})
                with urllib.request.urlopen(request, timeout=60) as response:
                    data = response.read(entry["bytes"] + 1)
                break
            except (urllib.error.URLError, OSError):
                if attempt == 4:
                    raise
        if len(data) != entry["bytes"] or hashlib.sha1(data).hexdigest() != entry["sha1"]:
            raise SystemExit("Digest mismatch for " + entry["id"])
        target.write_bytes(data)
        case = {"id": entry["id"].replace(".", "-"), "file": target.name, "sha256": hashlib.sha256(data).hexdigest()}
        case.update(entry["label"])
        (benign if entry["set"] == "benign" else people).append(case)
        attribution.append({k: entry[k] for k in ("id", "title", "author", "licence", "licence_url", "page")})
        print("%3d/%d %s" % (number, len(chosen), entry["id"]), flush=True)

    if benign:
        (args.out / "benign-labels.json").write_text(json.dumps(
            {"schema": 1, "kind": "adult", "cases": benign}, indent=1) + "\n", encoding="utf-8")
    if people:
        (args.out / "people-labels.json").write_text(json.dumps(
            {"schema": 1, "kind": "people", "cases": people}, indent=1) + "\n", encoding="utf-8")
    (args.out / "ATTRIBUTION.json").write_text(json.dumps(attribution, indent=1, ensure_ascii=False) + "\n",
                                                encoding="utf-8")
    print("Fetched", len(chosen), "pictures:", len(benign), "benign,", len(people), "people, into", args.out)


if __name__ == "__main__":
    main()
