#!/usr/bin/env python3
"""
Refuse to publish a list that is worse than the one it replaces.

    python3 blocklist/publish_guard.py --previous prev/manifest.json --new dist/manifest.json \
                                       --sources blocklist/sources.json --dist dist

The absolute floors the workflow had (200,000 domains) were a tenth of a
normal build (5.2M), so a build that lost UT1 — four fifths of the list — or
all four core sources on a bad day for raw.githubusercontent.com still passed
them, and was signed and published to every install. build.py needs only half
the sources to succeed, and counted a source that returned nothing as a
success. This compares the new build with the PREVIOUS PUBLISHED one:

  * the version strictly increases — clients reject anything else, and two
    different signed lists with one number are indistinguishable;
  * every core-tier source succeeded and returned something;
  * no source fell below half of what it returned last time;
  * the domain count, the core tier and the browser's rule count each kept at
    least 80% of what they were;
  * nothing to be published exceeds GitHub's 100 MiB file limit.

Exits non-zero with one line per problem. Unit-tested in test_build.py.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

KEEP_FRACTION = 0.80          # of the previous domain / core / rule counts
SOURCE_KEEP_FRACTION = 0.50   # of each source's previous count
MAX_FILE_BYTES = 95 * 1024 * 1024   # GitHub refuses pushes of files over 100 MiB

# What the clients download, and therefore what the lists branch carries.
PUBLISHED = ("manifest.json", "manifest.json.sig", "domains.packed.deflate",
             "domains_core.txt", "dnr_block_rules.json", "dnr_keyword_rules.json",
             "domains.index", "terms.json")


def problems(previous: dict, new: dict, core_sources: set[str],
             sizes: dict[str, int] | None = None) -> list[str]:
    out: list[str] = []
    if not isinstance(new.get("version"), int) or new["version"] <= int(previous.get("version", 0)):
        out.append(f"version {new.get('version')} does not exceed the published {previous.get('version')}")

    stats_new = {s["id"]: s for s in new.get("sources", [])}
    stats_prev = {s["id"]: s for s in previous.get("sources", [])}
    for sid in sorted(core_sources):
        s = stats_new.get(sid)
        if not s or not s.get("ok") or not s.get("domains"):
            out.append(f"core source {sid} failed or returned nothing")
    for sid, prev in stats_prev.items():
        now = stats_new.get(sid)
        if prev.get("domains") and now and now.get("ok") \
                and now.get("domains", 0) < SOURCE_KEEP_FRACTION * prev["domains"]:
            out.append(f"source {sid} fell from {prev['domains']:,} to {now.get('domains', 0):,}")

    for key in ("domain_count", "core_domain_count", "dnr_rule_count"):
        was, now = previous.get(key), new.get(key)
        if was and (now is None or now < KEEP_FRACTION * was):
            out.append(f"{key} fell from {was:,} to {now if now is not None else 'nothing'}")

    for name, size in (sizes or {}).items():
        if size > MAX_FILE_BYTES:
            out.append(f"{name} is {size / 2**20:.0f} MiB — over GitHub's per-file limit")
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--previous", required=True)
    ap.add_argument("--new", required=True)
    ap.add_argument("--sources", required=True)
    ap.add_argument("--dist", required=True)
    args = ap.parse_args()

    previous = json.loads(Path(args.previous).read_text(encoding="utf-8"))
    new = json.loads(Path(args.new).read_text(encoding="utf-8"))
    cfg = json.loads(Path(args.sources).read_text(encoding="utf-8"))
    core = {s["id"] for s in cfg["sources"]
            if s.get("enabled", True) and s.get("tier") == "core"}
    sizes = {}
    for name in PUBLISHED:
        p = Path(args.dist) / name
        if not p.exists():
            print(f"REFUSED: {name} missing from {args.dist}", file=sys.stderr)
            return 1
        sizes[name] = p.stat().st_size

    found = problems(previous, new, core, sizes)
    for line in found:
        print(f"REFUSED: {line}", file=sys.stderr)
    if found:
        return 1
    print(f"publish guard: v{previous.get('version')} -> v{new['version']}, "
          f"{new.get('domain_count', 0):,} domains, {new.get('core_domain_count', 0):,} core — OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
