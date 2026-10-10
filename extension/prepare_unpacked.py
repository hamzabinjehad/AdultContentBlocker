#!/usr/bin/env python3
"""Prepare a fresh browser-specific unpacked extension without changing source.

    python3 extension/prepare_unpacked.py --out dist/hisn-unpacked-chrome-1.0.1

The destination must not already exist. This preserves manifest bytes (including
the pinned development key), unlike the Web Store package. A browser may write
its own cache into the prepared folder; prepare a NEW folder for another browser
or when that cache prevents loading. Existing folders are never repaired/deleted.
"""

from __future__ import annotations

import argparse
import fnmatch
from html.parser import HTMLParser
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import sys
from urllib.parse import unquote, urlsplit

SOURCE = Path(__file__).resolve().parent
SKIP_DIRECTORIES = {"test", "eval", "keys", "scripts", "dist", "__MACOSX"}
SKIP_FILES = {"package.sh", "check_package.py", "prepare_unpacked.py", "id_rsa", "id_ed25519"}
SKIP_SUFFIXES = {".py", ".pyc", ".sh", ".pem", ".key", ".p12", ".pfx", ".crx", ".zip",
                 ".mobileprovision", ".provisionprofile"}
REQUIRED_DYNAMIC = ("content/feed.js", "content/scan.js", "seed/terms.json")
MODULE_IMPORT = re.compile(
    r"^\s*(?:import|export)\s+(?:[^;\"']*?\bfrom\s*)?[\"']([^\"']+)[\"']",
    re.MULTILINE,
)
DYNAMIC_IMPORT = re.compile(r"\bimport\(\s*[\"']([^\"']+)[\"']\s*\)")


class PreparationError(ValueError):
    """The folder cannot safely be prepared or is structurally incomplete."""


class PageAssets(HTMLParser):
    def __init__(self):
        super().__init__()
        self.assets = []

    def handle_starttag(self, tag, attrs):
        attribute = {"script": "src", "link": "href", "img": "src"}.get(tag)
        if attribute and (value := dict(attrs).get(attribute)):
            self.assets.append(value)


def _skip(path: Path, directory: bool) -> bool:
    name = path.name
    # _locales is the one legitimate Chromium localization resource directory.
    if name.startswith(".") or (name.startswith("_") and name != "_locales"):
        return True
    if directory:
        return name in SKIP_DIRECTORIES
    return name in SKIP_FILES or path.suffix.lower() in SKIP_SUFFIXES


def _inventory(source: Path, prefix="") -> list[str]:
    files = []
    for entry in sorted(source.iterdir()):
        mode = entry.lstat().st_mode
        # Never follow an unknown link, including a link named like excluded
        # tooling/cache. Excluded ordinary directories are not traversed.
        if stat.S_ISLNK(mode):
            raise PreparationError(f"source contains a symlink: {entry}")
        directory = stat.S_ISDIR(mode)
        if _skip(entry, directory):
            continue
        relative = f"{prefix}/{entry.name}" if prefix else entry.name
        if directory:
            files.extend(_inventory(entry, relative))
        elif stat.S_ISREG(mode):
            files.append(relative)
        else:
            raise PreparationError(f"source contains a non-regular entry: {entry}")
    return files


def _copy_file(source: Path, target: Path) -> None:
    # Reject a file replaced by a symlink after validation where supported.
    # This user-invoked copy assumes the source tree is not changed concurrently.
    if source.is_symlink():
        raise PreparationError(f"source contains a symlink: {source}")
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    with os.fdopen(os.open(source, flags), "rb") as src:
        if not stat.S_ISREG(os.fstat(src.fileno()).st_mode):
            raise PreparationError(f"source is not a regular file: {source}")
        with target.open("xb") as dst:
            shutil.copyfileobj(src, dst)


def _path_without_symlinks(path: Path) -> Path:
    absolute = Path(os.path.abspath(path))
    for component in (absolute, *absolute.parents):
        if component.is_symlink():
            raise PreparationError(f"symlink path is not supported: {component}")
    return absolute


def validate(folder: Path, files=None) -> None:
    """Check bundled dependencies, not browser permissions or protection state.

    The import check handles the static/literal imports used by this extension;
    it is deliberately not a JavaScript parser or a guarantee about future
    computed runtime imports. Hisn's dynamically injected scripts are explicit.
    """
    try:
        manifest = json.loads((folder / "manifest.json").read_bytes())
    except (OSError, ValueError) as exc:
        raise PreparationError(f"invalid or missing manifest.json: {exc}") from exc
    if not isinstance(manifest, dict) or manifest.get("manifest_version") != 3:
        raise PreparationError("manifest must describe a Manifest V3 extension")
    if not isinstance(manifest.get("name"), str) or not manifest["name"]:
        raise PreparationError("manifest name is missing")
    if not isinstance(manifest.get("version"), str) or not manifest["version"]:
        raise PreparationError("manifest version is missing")
    if not isinstance(manifest.get("key"), str) or not manifest["key"]:
        raise PreparationError("local manifest key is required to preserve the extension identity")
    files = set(_inventory(folder) if files is None else files)

    def require(reference, owner="", wildcard=False):
        if not isinstance(reference, str) or not reference:
            raise PreparationError(f"invalid resource reference in {owner or 'manifest'}")
        url = urlsplit(reference)
        if url.scheme or url.netloc or "\\" in reference:
            raise PreparationError(f"resource must be bundled locally: {reference}")
        decoded = unquote(url.path)
        path = PurePosixPath(decoded)
        if path.is_absolute() or ".." in path.parts and not owner:
            raise PreparationError(f"resource escapes extension: {reference}")
        joined = os.path.normpath(str(PurePosixPath(owner).parent / path)).replace(os.sep, "/")
        if joined == ".." or joined.startswith("../"):
            raise PreparationError(f"resource escapes extension: {reference}")
        if wildcard and any(char in joined for char in "*?["):
            if not any(fnmatch.fnmatchcase(name, joined) for name in files):
                raise PreparationError(f"resource pattern has no bundled files: {joined}")
        elif joined not in files:
            raise PreparationError(f"missing bundled resource: {joined}")

    worker = manifest.get("background", {}).get("service_worker")
    require(worker)
    action = manifest.get("action", {})
    for page in (action.get("default_popup"), manifest.get("options_page"),
                 manifest.get("options_ui", {}).get("page")):
        if page:
            require(page)
    for icons in (manifest.get("icons", {}), action.get("default_icon", {})):
        for resource in (icons.values() if isinstance(icons, dict) else [icons]):
            require(resource)
    for script in manifest.get("content_scripts", []):
        for resource in script.get("js", []) + script.get("css", []):
            require(resource)
    for rule in manifest.get("declarative_net_request", {}).get("rule_resources", []):
        require(rule.get("path"))
    for resource_set in manifest.get("web_accessible_resources", []):
        for resource in resource_set.get("resources", []):
            require(resource, wildcard=True)
    for resource in REQUIRED_DYNAMIC:
        require(resource)
    default_locale = manifest.get("default_locale")
    if default_locale:
        if not isinstance(default_locale, str) or "/" in default_locale or "\\" in default_locale:
            raise PreparationError("invalid manifest default_locale")
        require(f"_locales/{default_locale}/messages.json")

    for relative in sorted(files):
        path = folder / relative
        if relative.endswith(".html"):
            parser = PageAssets()
            parser.feed(path.read_text(encoding="utf-8"))
            for resource in parser.assets:
                require(resource, relative)
        elif relative.endswith(".js"):
            code = path.read_text(encoding="utf-8")
            for resource in MODULE_IMPORT.findall(code) + DYNAMIC_IMPORT.findall(code):
                if not resource.startswith(("./", "../")):
                    raise PreparationError(f"non-relative module import in {relative}: {resource}")
                require(resource, relative)


def prepare(output: Path, source: Path = SOURCE) -> Path:
    source = _path_without_symlinks(Path(source))
    output = _path_without_symlinks(Path(output))
    if not source.is_dir():
        raise PreparationError(f"source is not a directory: {source}")
    if output == source or source in output.parents or output in source.parents:
        raise PreparationError("output must not overlap the source directory")
    if os.path.lexists(output):
        raise PreparationError(f"output already exists; choose a fresh folder: {output}")
    # Validate the filtered copy plan before creating any destination. Unknown
    # symlinks fail; browser caches/tooling are neither read nor copied.
    files = _inventory(source)
    validate(source, files)
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        output.mkdir(exist_ok=False)
    except FileExistsError as exc:
        raise PreparationError(f"output already exists; choose a fresh folder: {output}") from exc
    # No deletion/overwrite on failure, and no atomic transaction claim. A
    # failed copy retains its fresh partial folder with a clear error message.
    try:
        for relative in files:
            target = output / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            _copy_file(source / relative, target)
        validate(output)
        return output
    except (OSError, ValueError, TypeError, AttributeError) as exc:
        raise PreparationError(f"preparation failed; partial output retained at {output}: {exc}") from exc


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path, help="fresh, nonexistent output folder")
    args = parser.parse_args(argv)
    try:
        output = prepare(args.out)
    except (PreparationError, OSError, ValueError, TypeError, AttributeError) as exc:
        print(f"cannot prepare unpacked extension: {exc}", file=sys.stderr)
        return 1
    print(f"Prepared clean unpacked extension: {output}")
    print("Load this folder with Load unpacked. Manifest key and source files were preserved.")
    print("For another browser, prepare a different fresh folder; do not reuse browser-written caches.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
