"""Pin every shipping platform version in one file and check it against the code.

`versions.json` is the release's version set. The platform manifests stay the
source of truth for what a build actually carries; this check fails when the
two disagree, so a version bump cannot land in one place and be forgotten in
another. It also generates `docs/VERSIONS.md`, which is the human-readable
release matrix.

    python3 script/versions.py           # rewrite docs/VERSIONS.md
    python3 script/versions.py --check   # fail if the matrix or a manifest drifted
"""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSIONS = ROOT / "versions.json"
DOC = ROOT / "docs/VERSIONS.md"


class Drift(ValueError):
    """A platform manifest does not match the pinned release set."""


def extension_version():
    data = json.loads((ROOT / "extension/manifest.json").read_text())
    return str(data["version"]), None


def gradle_version():
    text = (ROOT / "android/app/build.gradle").read_text()
    code = re.search(r"^\s*versionCode\s+(\d+)\s*$", text, re.MULTILINE)
    name = re.search(r"^\s*versionName\s+'([^']+)'\s*$", text, re.MULTILINE)
    if not code or not name:
        raise Drift("android/app/build.gradle: versionCode or versionName not found")
    return name.group(1), int(code.group(1))


def xcode_version(relative):
    text = (ROOT / relative).read_text()
    names = {value.strip().strip('"') for value in
             re.findall(r"MARKETING_VERSION\s*=\s*([^;]+);", text)}
    builds = {value.strip().strip('"') for value in
              re.findall(r"CURRENT_PROJECT_VERSION\s*=\s*([^;]+);", text)}
    if not names or not builds:
        raise Drift(f"{relative}: MARKETING_VERSION or CURRENT_PROJECT_VERSION not found")
    if len(names) != 1 or len(builds) != 1:
        raise Drift(f"{relative}: targets disagree on their version: {sorted(names)} / {sorted(builds)}")
    build = builds.pop()
    if not build.isdigit():
        raise Drift(f"{relative}: CURRENT_PROJECT_VERSION is not a number: {build!r}")
    return names.pop(), int(build)


def seed_facts():
    manifest = json.loads((ROOT / "seed/manifest.json").read_text())
    signature = ROOT / "seed/manifest.json.sig"
    if not signature.exists() or signature.stat().st_size == 0:
        raise Drift("seed/manifest.json.sig is missing or empty")
    for key in ("version", "built_at", "schema", "core_domain_count", "domain_count",
                "term_count", "artifacts"):
        if key not in manifest:
            raise Drift(f"seed/manifest.json: missing {key}")
    if manifest["schema"] != 1:
        raise Drift(f"seed/manifest.json: unsupported schema {manifest['schema']}")
    return manifest


PARSERS = {
    "extension": extension_version,
    "macos": lambda: xcode_version("macos/Hisn.xcodeproj/project.pbxproj"),
    "ios": lambda: xcode_version("ios/HisnMobile.xcodeproj/project.pbxproj"),
    "android": gradle_version,
}


def load():
    release = json.loads(VERSIONS.read_text())
    platforms = release.get("platforms")
    if not isinstance(platforms, list) or not platforms:
        raise Drift("versions.json: platforms must be a nonempty list")
    seen = set()
    for platform in platforms:
        for key in ("id", "name", "version", "build", "state", "source", "notes"):
            if key not in platform:
                raise Drift(f"versions.json: platform {platform.get('id', '?')} lacks {key}")
        if platform["id"] in seen:
            raise Drift(f"versions.json: duplicate platform {platform['id']}")
        seen.add(platform["id"])
    if seen != set(PARSERS):
        raise Drift("versions.json: platforms must be exactly " + ", ".join(sorted(PARSERS)))
    for key in ("branch", "updated"):
        if not release.get(key):
            raise Drift(f"versions.json: missing {key}")
    return release


def problems():
    """Every disagreement between the pinned set and the platform manifests."""
    release = load()
    found = []
    for platform in release["platforms"]:
        version, build = PARSERS[platform["id"]]()
        if version != platform["version"]:
            found.append(f"{platform['id']}: {platform['source']} says {version}, "
                         f"versions.json pins {platform['version']}")
        if build != platform["build"]:
            found.append(f"{platform['id']}: {platform['source']} says build {build}, "
                         f"versions.json pins {platform['build']}")
    seed = seed_facts()
    if not isinstance(seed["version"], int) or seed["version"] < 1:
        found.append("seed: version must be a positive integer")
    if seed["core_domain_count"] < 100_000:
        found.append(f"seed: only {seed['core_domain_count']:,} core domains; the 1.0 floor is 100,000")
    if seed["core_domain_count"] > seed["domain_count"]:
        found.append("seed: the core tier is larger than the full list")
    return found


def document(release=None, seed=None):
    release = release or load()
    seed = seed or seed_facts()
    rows = "\n".join(
        f"| {p['name']} | {p['version']} | {p['build'] if p['build'] is not None else '—'} | "
        f"{p['state']} | `{p['source']}` |"
        for p in release["platforms"]
    )
    notes = "\n".join(
        f"- **{p['name']}** (`{p['id']}`): {p['notes']}" for p in release["platforms"]
    )
    return f"""# Versions and release state

> Generated by `script/versions.py` from `versions.json` and the platform
> manifests. Do not edit by hand: change `versions.json` and the platform
> manifest together, then run `python3 script/versions.py`.

Pinned on `{release['branch']}`, updated {release['updated']}. A platform
version means what that platform's own manifest says; it is not a claim that a
signed artifact has shipped. The state column is the honest release state.

| Platform | Version | Build | State | Source of truth |
| --- | --- | --- | --- | --- |
{rows}

## Seed bundle

| Field | Value |
| --- | --- |
| Version | {seed['version']} |
| Built | {seed['built_at']} |
| Schema | {seed['schema']} |
| Core tier (clients) | {seed['core_domain_count']:,} domains |
| Full list (Mac/DNS) | {seed['domain_count']:,} domains |
| Keyword layer | {seed['term_count']:,} terms |

The seed is signed (`seed/manifest.json.sig`) and verified by
`python3 blocklist/seed.py verify`. Its version is the floor a published
generation must beat; clients refuse a lower one.

## Version notes

{notes}

## Bumping a version

1. Bump the platform's own manifest: `extension/manifest.json`,
   `android/app/build.gradle`, or the Xcode target's `MARKETING_VERSION` and
   `CURRENT_PROJECT_VERSION`.
2. Change the same value in `versions.json` and update its `updated` date.
3. Run `python3 script/versions.py` to regenerate this page, then
   `python3 script/versions.py --check` (also part of `./test.sh python`).
4. Add the release notes to `CHANGELOG.md`. A version bump without a changelog
   entry is an unfinished release.

Android `versionCode` only ever increases. iOS and macOS build numbers only
ever increase for a shipped build. The extension version must match the
packaged zip name; the store assigns its own extension ID, not a version.
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true",
                        help="fail if the page or a platform manifest is stale")
    args = parser.parse_args()
    drift = problems()
    if drift:
        parser.exit(1, "Version drift:\n  " + "\n  ".join(drift) + "\n")
    content = document()
    if args.check:
        if not DOC.exists() or DOC.read_text() != content:
            parser.exit(1, "docs/VERSIONS.md is stale; run python3 script/versions.py\n")
    else:
        DOC.write_text(content)
        print(f"wrote {DOC.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
