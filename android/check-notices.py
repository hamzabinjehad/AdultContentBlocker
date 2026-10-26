#!/usr/bin/env python3
"""The APK's "Licenses and sources" screen must cover every list source it bundles.

The APK ships seed/domains_core.txt, built from the enabled core-tier sources in
blocklist/sources.json. A source added there without a notice here fails this
check, as does a bundled license file the screen does not list, or a summary
that names a source in one language only. Run by test-core.sh.
"""
import json
import pathlib
import re
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
# Upstream repository named in the summary, and the license text bundled for it.
# chadmayfield: blocklist/sources.json says MIT, but the upstream LICENSE file is GPL 3.0.
SOURCES = {
    "stevenblack-porn": ("StevenBlack/hosts", "licenses/StevenBlack-hosts-MIT.txt"),
    "stevenblack-laptop": ("StevenBlack/hosts", "licenses/StevenBlack-hosts-MIT.txt"),
    "sinfonietta-porn": ("Sinfonietta/hostfiles", "licenses/Sinfonietta-hostfiles-MIT.txt"),
    "hagezi-nsfw": ("hagezi/dns-blocklists", "licenses/GPL-3.0.txt"),
    "chadmayfield-top1m": ("chadmayfield/my-pihole-blocklists", "licenses/GPL-3.0.txt"),
}
# Imported German terms in seed/terms.json (blocklist/terms/terms.de-import.tsv).
TERMS = ("LDNOOBW", "licenses/LDNOOBW-CC-BY-4.0.txt")


def summary(language_dir):
    root = ET.parse(ROOT / f"app/src/main/res/{language_dir}/strings.xml").getroot()
    return next("".join(e.itertext()) for e in root if e.get("name") == "notice_sources_body")


def main():
    problems = []
    config = json.loads((ROOT / "../blocklist/sources.json").read_text(encoding="utf-8"))
    core = [s["id"] for s in config["sources"] if s.get("enabled", True) and s.get("tier") == "core"]
    if not core:
        problems.append("blocklist/sources.json lists no enabled core-tier source")
    summaries = {"en": summary("values"), "ar": summary("values-ar")}
    required = [TERMS]
    for source in core:
        if source not in SOURCES:
            problems.append(f"core source {source} is bundled in the APK but has no notice")
        else:
            required.append(SOURCES[source])
    for name, path in required:
        if not (ASSETS / path).is_file():
            problems.append(f"{path}: license text missing")
        for language, text in summaries.items():
            if name not in text:
                problems.append(f"notice_sources_body ({language}) does not name {name}")
    listed = set(re.findall(r'"(licenses/[^"]+)"', (ROOT / "app/src/main/java/app/hisn/android/Notices.java")
                            .read_text(encoding="utf-8")))
    for path in sorted(p.relative_to(ASSETS).as_posix() for p in (ASSETS / "licenses").glob("*")):
        if path not in listed:
            problems.append(f"{path}: bundled but not shown in Notices.java")
    for path in sorted(listed):
        if not (ASSETS / path).is_file():
            problems.append(f"Notices.java lists {path}, which is not bundled")
    # copyAndroidVisual packages these beside the engine; the screen lists them from assets.
    visual = ROOT / "../extension/visual"
    if not (visual / "engine.bundle.js.LEGAL.txt").is_file() or not any((visual / "notices").glob("*")):
        problems.append("extension/visual: engine license file or notices missing")
    if problems:
        raise SystemExit("Notice check failed:\n" + "\n".join(problems))
    print(f"Notices passed ({len(core)} core list sources, the imported word list and "
          f"{len(listed)} list license files, English and Arabic summaries)")


if __name__ == "__main__":
    main()
