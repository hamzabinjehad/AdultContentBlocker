#!/usr/bin/env python3
"""Compile Safari resources only after signed-seed and infrastructure checks.

No source downloads, signing keys, router changes or device configuration.
Outputs are generated build artifacts; changing the seed requires rebuilding.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from network.publish import (Rejected, SafetyPolicy, load_key, read_small,
                             validate_manifest, verify_signature, valid_domain)

PARTS = 4
RULE_LIMIT = 40_000


def rule(domain: str) -> dict:
    if not valid_domain(domain):
        raise Rejected("noncanonical Safari domain")
    # Match the destination host, not the top-page host (if-domain). Label and
    # authority boundaries prevent blocking innocent domain suffix collisions.
    # Safari's regex subset has no alternation. Serialized HTTP(S) request URLs
    # have an authority delimiter even when the user enters a bare hostname.
    pattern = r"^https?://([^/?#]*@)?([a-z0-9-]+\.)*" + re.escape(domain) + r"\.?[:/?#]"
    return {"trigger": {"url-filter": pattern}, "action": {"type": "block"}}


def verified_domains(bundle: Path, public_key: Path) -> tuple[list[str], int]:
    key, _ = load_key(public_key)
    raw = read_small(bundle / "manifest.json")
    verify_signature(raw, read_small(bundle / "manifest.json.sig", 256), key)
    _, version, entry, count = validate_manifest(raw, "domains_core.txt")
    content = read_small(bundle / "domains_core.txt", 16 << 20)
    if len(content) != entry["bytes"] or hashlib.sha256(content).hexdigest() != entry["sha256"]:
        raise Rejected("signed core artifact mismatch")
    domains = [line for line in content.decode("ascii").split("\n") if line and not line.startswith("#")]
    if len(domains) != count or len(set(domains)) != count or not domains:
        raise Rejected("core domain count mismatch or duplicate")
    if domains != sorted(domains):
        raise Rejected("core domains must be sorted")
    safety = SafetyPolicy(ROOT / "blocklist/sources.json")
    for domain in domains:
        if not valid_domain(domain):
            raise Rejected("noncanonical core domain")
        safety.check(domain)
    if count > PARTS * RULE_LIMIT:
        raise Rejected("core list exceeds Safari shard capacity; add explicit targets, never truncate")
    return domains, version


def compile_part(domains: list[str], part: int) -> bytes:
    if not 1 <= part <= PARTS:
        raise Rejected("invalid Safari part")
    selected = domains[(part - 1) * RULE_LIMIT:part * RULE_LIMIT]
    if not selected:
        raise Rejected("empty Safari part")
    return json.dumps([rule(domain) for domain in selected], separators=(",", ":")).encode()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--part", type=int, default=0)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    domains, version = verified_domains(ROOT / "seed", ROOT / "blocklist/public_key.hex")
    args.output.mkdir(parents=True, exist_ok=True)
    if args.part:
        raw = compile_part(domains, args.part)
        (args.output / "rules.json").write_bytes(raw)
        metadata = {"version": version, "count": min(RULE_LIMIT, len(domains) - (args.part - 1) * RULE_LIMIT),
                    "sha256": hashlib.sha256(raw).hexdigest()}
    else:
        metadata = {"version": version, "count": len(domains), "parts": PARTS}
    (args.output / "rules-metadata.json").write_text(json.dumps(metadata, sort_keys=True))


if __name__ == "__main__":
    main()
