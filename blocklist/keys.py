#!/usr/bin/env python3
"""
Ed25519 key management + manifest verification for the Hisn blocklist.

    python3 keys.py generate --out ../keys           # once, offline
    python3 keys.py verify --dist ../dist --pub ../keys/blocklist_ed25519.pub

The PRIVATE key never leaves your machine or your CI secret store. The PUBLIC
key is compiled into the macOS app and the browser extension, so rotating it
requires shipping an update — treat the private key accordingly.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)
from cryptography.exceptions import InvalidSignature


def cmd_generate(args: argparse.Namespace) -> int:
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    priv_path = out / "blocklist_ed25519.pem"
    pub_path = out / "blocklist_ed25519.pub"

    if priv_path.exists() and not args.force:
        print(f"refusing to overwrite {priv_path} (use --force)", file=sys.stderr)
        return 1

    priv = Ed25519PrivateKey.generate()
    priv_path.write_bytes(priv.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    ))
    priv_path.chmod(0o600)

    raw_pub = priv.public_key().public_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PublicFormat.Raw,
    )
    pub_path.write_text(raw_pub.hex() + "\n", encoding="utf-8")

    print(f"private key -> {priv_path}  (KEEP SECRET, chmod 600)")
    print(f"public  key -> {pub_path}")
    print(f"\nEmbed this public key in the clients:\n  {raw_pub.hex()}")
    return 0


def load_pub(path: Path) -> Ed25519PublicKey:
    text = path.read_text(encoding="utf-8").strip()
    return Ed25519PublicKey.from_public_bytes(bytes.fromhex(text))


def cmd_verify(args: argparse.Namespace) -> int:
    """Exactly what a client must do before trusting a downloaded list."""
    dist = Path(args.dist)
    manifest_bytes = (dist / "manifest.json").read_bytes()
    sig = bytes.fromhex((dist / "manifest.json.sig").read_text().strip())

    pub = load_pub(Path(args.pub))
    try:
        pub.verify(sig, manifest_bytes)
    except InvalidSignature:
        print("SIGNATURE INVALID — reject this list", file=sys.stderr)
        return 1
    print("signature OK")

    manifest = json.loads(manifest_bytes)

    if args.min_version is not None and manifest["version"] < args.min_version:
        print(f"ROLLBACK DETECTED: manifest version {manifest['version']} < "
              f"held version {args.min_version} — reject", file=sys.stderr)
        return 1

    failed = False
    for name, meta in manifest["artifacts"].items():
        p = dist / name
        if not p.exists():
            print(f"  MISSING  {name}", file=sys.stderr)
            failed = True
            continue
        h = hashlib.sha256()
        with p.open("rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                h.update(chunk)
        if h.hexdigest() != meta["sha256"]:
            print(f"  HASH MISMATCH  {name}", file=sys.stderr)
            failed = True
        else:
            print(f"  ok  {name}  ({meta['bytes']:,} bytes)")

    if failed:
        return 1
    print(f"\nall artifacts verified   version={manifest['version']}   "
          f"domains={manifest['domain_count']:,}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    g = sub.add_parser("generate", help="create a new signing keypair")
    g.add_argument("--out", default="../keys")
    g.add_argument("--force", action="store_true")
    g.set_defaults(func=cmd_generate)

    v = sub.add_parser("verify", help="verify a built dist/ directory")
    v.add_argument("--dist", default="../dist")
    v.add_argument("--pub", default="../keys/blocklist_ed25519.pub")
    v.add_argument("--min-version", type=int, default=None,
                   help="Simulate a client that already holds this version.")
    v.set_defaults(func=cmd_verify)

    args = ap.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
