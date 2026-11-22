#!/usr/bin/env python3
"""Pick freely licensed Commons pictures for the benign and people sets: metadata only.

  python3 android/visual-eval/pick-commons.py --plan android/visual-eval/commons-plan.json \
      --out android/visual-eval/commons-sources.json

Reads each category's direct file members and their image info (URL, SHA-1, size, MIME,
licence, author) from the Commons API, keeps photos with a free licence, and writes a source
list with every entry marked "review": "pending". Nothing is downloaded. A person reviews
the list (and the pictures on Commons), sets "review" to "ok" or "rejected" with a reason, and
only then does fetch-commons.py download the "ok" entries.
"""
import argparse
import collections
import json
from pathlib import Path
import re
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://commons.wikimedia.org/w/api.php"
AGENT = "HisnVisualEval/0.1 (offline accuracy tooling; metadata only)"
FREE = re.compile(r"^(CC0|Public domain|PD[- ].*|CC BY(-SA)? [1-4]\.0( [A-Za-z-]+)?)$", re.I)
MAX_BYTES = 12 * 1024 * 1024


def query(params, attempts=8):
    """One API call, at most one every two seconds, honouring Retry-After on 429/503."""
    url = API + "?" + urllib.parse.urlencode(dict(params, format="json", formatversion="2", maxlag="5"))
    wait = 2.0
    for attempt in range(attempts):
        time.sleep(wait)
        try:
            request = urllib.request.Request(url, headers={"User-Agent": AGENT})
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as failure:
            if failure.code not in (429, 503) or attempt == attempts - 1:
                raise
            retry = failure.headers.get("Retry-After", "")
            wait = float(retry) + 1 if retry.isdigit() else min(120.0, wait * 2 + 5)
            print("  rate limited; waiting %.0f s" % wait, flush=True)
        except (OSError, ValueError):
            if attempt == attempts - 1:
                raise
            wait = min(120.0, wait * 2)
    raise RuntimeError("unreachable")


def members(category, limit):
    titles, more = [], {}
    while len(titles) < limit:
        data = query(dict(action="query", list="categorymembers", cmtitle="Category:" + category,
                          cmtype="file", cmlimit="50", cmsort="sortkey", **more))
        titles += [m["title"] for m in data["query"]["categorymembers"]]
        if "continue" not in data:
            break
        more = {"cmcontinue": data["continue"]["cmcontinue"]}
    return titles


def details(titles):
    found = {}
    for start in range(0, len(titles), 40):
        data = query(dict(action="query", prop="imageinfo", titles="|".join(titles[start:start + 40]),
                          iiprop="url|sha1|size|mime|extmetadata",
                          iiextmetadatafilter="LicenseShortName|LicenseUrl|Artist"))
        for page in data["query"]["pages"]:
            info = (page.get("imageinfo") or [None])[0]
            if info:
                found[page["title"]] = info
    return found


def plain(html):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", html or "")).strip()[:200]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    plan = json.loads(args.plan.read_text(encoding="utf-8"))
    if plan.get("schema") != 1:
        raise SystemExit("Unknown plan schema")
    entries, used, numbers = [], set(), collections.Counter()
    for group in plan["groups"]:
        wanted, skip = group["take"], group.get("skip", 0)
        titles = sorted(members(group["category"], 6 * (wanted + skip) + 50))
        info = details(titles)
        kept, skipped = 0, 0
        for title in titles:
            item = info.get(title)
            if kept == wanted or not item:
                continue
            licence = item.get("extmetadata", {}).get("LicenseShortName", {}).get("value", "")
            if (item.get("mime") not in ("image/jpeg", "image/png") or not FREE.match(licence)
                    or min(item.get("width", 0), item.get("height", 0)) < 400 or item.get("size", 0) > MAX_BYTES):
                continue
            if title in used:
                continue
            if skipped < skip:
                skipped += 1
                continue
            used.add(title)
            kept += 1
            # Numbered per id prefix across groups, so two groups with the same label never share ids.
            slug = re.sub(r"[^a-z0-9]+", "-", group["label"].get("category", "") or "-".join(
                [*group["label"].get("people", []), *group["label"].get("attributes", {}).values()]).lower()).strip("-")
            prefix = "%s-%s" % (group["set"], slug or "none")
            numbers[prefix] += 1
            entries.append({
                "id": "%s-%03d" % (prefix, numbers[prefix]),
                "set": group["set"], "title": title, "commons_category": group["category"],
                "url": item["url"].split("?", 1)[0], "page": item["descriptionurl"].split("?", 1)[0], "sha1": item["sha1"],
                "bytes": item["size"], "width": item["width"], "height": item["height"],
                "licence": licence, "licence_url": item.get("extmetadata", {}).get("LicenseUrl", {}).get("value", ""),
                "author": plain(item.get("extmetadata", {}).get("Artist", {}).get("value", "")),
                "label": group["label"], "review": "pending"})
        print("%-34s %3d of %d picked" % (group["category"], kept, wanted), flush=True)
    args.out.write_text(json.dumps({"schema": 1, "_comment": "Generated by pick-commons.py. Set review to ok or rejected (with review_note) for every entry before fetching.", "entries": entries}, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print("Wrote", len(entries), "entries,", sum(e["bytes"] for e in entries) // (1024 * 1024), "MB if all fetched, to", args.out)


if __name__ == "__main__":
    main()
