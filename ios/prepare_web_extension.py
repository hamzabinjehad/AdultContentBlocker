#!/usr/bin/env python3
"""Stage a Safari scanner after verifying signed terms and bundled visual assets.

Canonical JS is copied only into generated build products, never maintained as
a source fork. No downloads, keys, browser activation or user data are involved.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from network.publish import (Rejected, load_key, read_small, validate_manifest,
                             verify_signature, valid_domain)

RESOURCES = {
    "manifest.json": "ios/HisnWebExtension/manifest.json",
    "background.js": "ios/HisnWebExtension/background.js",
    "background.html": "ios/HisnWebExtension/background.html",
    "options.html": "ios/HisnWebExtension/options.html",
    "options.js": "ios/HisnWebExtension/options.js",
    "options.css": "ios/HisnWebExtension/options.css",
    "blocked.html": "ios/HisnWebExtension/blocked.html",
    "blocked.js": "ios/HisnWebExtension/blocked.js",
    "content/scan.js": "extension/content/scan.js",
    "content/feed.js": "extension/content/feed.js",
    "content/visual.js": "extension/content/visual.js",
    "visual/capture.js": "extension/visual/capture.js",
    "visual/broker.js": "extension/visual/broker.js",
    "visual/decisions.js": "extension/visual/decisions.js",
    "visual/queue.js": "extension/visual/queue.js",
    "lib/score.js": "extension/lib/score.js",
    "lib/normalize.js": "extension/lib/normalize.js",
    "icons/icon-32.png": "extension/icons/icon-32.png",
    "icons/icon-128.png": "extension/icons/icon-128.png",
}

# Fixed runtime allowlist: model-manifest data can never introduce paths, build
# dependencies or external runtime downloads into the Safari extension bundle.
VISUAL_ASSETS = frozenset({
    "engine.bundle.js", "models/blazeface.json", "models/blazeface.bin",
    "models/gender.json", "models/gender.bin", "notices/human-models-MIT.txt",
    "notices/face-classification-MIT.txt", "notices/mediapipe-Apache-2.0.txt",
    "notices/tensorflowjs-Apache-2.0.txt",
    "notices/nsfwjs-LICENSE.txt", "notices/@vladmandic-human-LICENSE.txt",
    "notices/buffer-LICENSE.txt", "engine.bundle.js.LEGAL.txt",
})
VISUAL_MAX_BYTES = 32 * 1024 * 1024


def verified_visual_resources(repo: Path = ROOT) -> dict[str, bytes]:
    """Verify the generated, pinned build assets; not an accuracy certification.

    Unlike signed blocklist terms, visual/assets.json is repository build
    metadata. Hash checks detect staging drift, not malicious source changes.
    """
    visual = repo / "extension/visual"
    metadata_raw = read_small(visual / "assets.json")
    try:
        metadata = json.loads(metadata_raw)
    except (ValueError, UnicodeError) as error:
        raise Rejected("invalid visual build metadata") from error
    if (not isinstance(metadata, dict) or metadata.get("schema") != 1
            or not isinstance(metadata.get("revision"), str)
            or len(metadata["revision"]) != 40
            or any(c not in "0123456789abcdef" for c in metadata["revision"])
            or not isinstance(metadata.get("artifacts"), dict)
            or set(metadata["artifacts"]) != VISUAL_ASSETS):
        raise Rejected("unexpected visual build asset inventory")
    result = {"visual/assets.json": metadata_raw}
    for name, entry in metadata["artifacts"].items():
        raw = read_small(visual / name, VISUAL_MAX_BYTES)
        if (not raw or not isinstance(entry, dict)
                or type(entry.get("bytes")) is not int or len(raw) != entry["bytes"]
                or hashlib.sha256(raw).hexdigest() != entry.get("sha256")):
            raise Rejected(f"visual asset hash or byte count mismatch: {name}")
        result[f"visual/{name}"] = raw
    for model in ("blazeface", "gender"):
        try:
            graph = json.loads(result[f"visual/models/{model}.json"])
            paths = [name for group in graph["weightsManifest"] for name in group["paths"]]
        except (ValueError, UnicodeError, KeyError, TypeError) as error:
            raise Rejected("malformed bundled model manifest") from error
        if paths != [f"{model}.bin"]:
            raise Rejected("unexpected visual model shard path")
    return result


def verified_terms(repo: Path = ROOT) -> tuple[bytes, int]:
    key, _ = load_key(repo / "blocklist/public_key.hex")
    raw = read_small(repo / "seed/manifest.json")
    verify_signature(raw, read_small(repo / "seed/manifest.json.sig", 256), key)
    manifest, version, _, _ = validate_manifest(raw, "domains_core.txt")
    entry = manifest["artifacts"].get("terms.json")
    if entry is None:
        raise Rejected("signed manifest does not include scanner terms")
    content = read_small(repo / "seed/terms.json")
    if len(content) != entry["bytes"] or hashlib.sha256(content).hexdigest() != entry["sha256"]:
        raise Rejected("signed scanner terms hash or byte count mismatch")
    if content != read_small(repo / "extension/seed/terms.json"):
        raise Rejected("canonical extension terms differ from the signed seed")
    try:
        terms = json.loads(content)
    except (ValueError, UnicodeError) as error:
        raise Rejected("invalid scanner terms JSON") from error
    if (not isinstance(terms, dict) or type(terms.get("version")) is not int
            or terms["version"] != version):
        raise Rejected("scanner terms version mismatch")
    positives = terms.get("terms")
    negatives = terms.get("negatives")
    if not isinstance(positives, list) or not positives or not isinstance(negatives, list):
        raise Rejected("empty or malformed scanner term lists")
    for group in [positives, negatives]:
        for term in group:
            if (not isinstance(term, dict) or not isinstance(term.get("t"), str) or not term["t"]
                    or len(term["t"]) > 256 or type(term.get("w")) not in [int, float]
                    or not math.isfinite(term["w"]) or term["w"] == 0
                    or (group is positives and term["w"] < 0)):
                raise Rejected("malformed scanner term")
    exempt = terms.get("exempt_domains")
    if not isinstance(exempt, list) or any(not isinstance(host, str) or not valid_domain(host) for host in exempt):
        raise Rejected("malformed scanner exempt domains")
    return content, version


def stage(output: Path, repo: Path = ROOT) -> dict:
    terms, version = verified_terms(repo)
    # Verify every source before emitting any files. Never recursively include
    # development tests, signing inputs, Chrome policy or native-host machinery.
    contents = {name: read_small(repo / source) for name, source in RESOURCES.items()}
    contents.update(verified_visual_resources(repo))
    contents["seed/terms.json"] = terms
    output.mkdir(parents=True, exist_ok=True)
    for name, raw in contents.items():
        destination = output / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(raw)
    metadata = {"schema": 1, "version": version,
                "resources": {name: {"sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)}
                              for name, raw in sorted(contents.items())}}
    (output / "scanner-metadata.json").write_text(json.dumps(metadata, sort_keys=True))
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    stage(args.output)
    print("Verified and staged Hisn Safari text and optional visual scanner (no activation)")


if __name__ == "__main__":
    main()
