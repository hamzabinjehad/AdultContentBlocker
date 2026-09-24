#!/usr/bin/env python3
"""
The seed bundle: every generated artifact this repo SHIPS, cut from one signed
build and kept honest by one check.

    python3 blocklist/seed.py verify                       # what CI and the tests run
    python3 blocklist/seed.py sync --dist dist             # copy a signed build into place
    python3 blocklist/seed.py sync --build --sign-key keys/blocklist_ed25519.pem

WHAT SHIPS, AND WHY IT IS ONE THING
-----------------------------------
Four artifacts leave this repo inside a client rather than over the network:

    seed/domains_core.txt               the filter's starting blocklist
    seed/terms.json                     the filter's keyword layer
    extension/seed/terms.json           the extension's keyword layer (same bytes)
    extension/rules/dnr_block_rules.json    the extension's static domain rules
    extension/rules/dnr_keyword_rules.json  the extension's static keyword rules

plus `seed/manifest.json` and its signature, which is the only reason any of
them can be trusted: the macOS filter verifies the signature, then checks each
artifact against the SHA-256 the manifest records, on exactly the path a
downloaded list takes. Being bundled buys nothing.

They used to be maintained by hand — regenerate `terms.json`, copy it into two
places, and hope the manifest still described it. It did not: the manifest was
signed before `terms.json` existed, so it had no entry for it, so the filter
did what a fail-closed check must do and left the keyword layer OFF. The
bundled protection against domains registered today was silently disabled on
every machine, and every status light stayed green. This file exists so that
cannot recur:

  * `sync` is the only way the shipped files change. It takes one signed build
    and copies every artifact from it, so the set is internally consistent by
    construction.
  * `verify` fails if any shipped file is missing from the signed manifest,
    differs from what the manifest says, or — for `terms.json` — differs from
    what `blocklist/terms/` would compile to today. The test suite and CI run
    it, so a term edit that is not followed by a re-cut seed cannot merge.

THE VERSION COUNTER
-------------------
Clients reject a manifest whose version is lower than the one they hold, and a
freshly installed client holds the SEED's version. So the seed must never carry
a version higher than the next list CI will publish, or every new install
rejects every download until the counter catches up. `sync --build` therefore
keeps the seed's current version unless told otherwise, and the publish
workflow seeds its own counter from this manifest when no list has been
published yet. When cutting from a real published build (`sync --dist` on
CI's output), the version is simply that build's.

SIGNING
-------
A re-cut seed is a re-signed manifest, and the private key is not in this repo.
Either run `sync --build --sign-key` where the key lives, or dispatch the
publish workflow with `refresh_seed` and let CI, which holds the key, commit the
result. There is deliberately no third way: an unsigned seed is a seed the
filter refuses, and `verify` says so.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from terms import compile_terms, serialize_terms  # noqa: E402

REPO = Path(__file__).resolve().parent.parent

# (path relative to the repo root, artifact name in the manifest)
SHIPPED: tuple[tuple[str, str], ...] = (
    ("seed/domains_core.txt", "domains_core.txt"),
    ("seed/terms.json", "terms.json"),
    ("extension/seed/terms.json", "terms.json"),
    ("extension/rules/dnr_block_rules.json", "dnr_block_rules.json"),
    ("extension/rules/dnr_keyword_rules.json", "dnr_keyword_rules.json"),
)

MANIFEST = "seed/manifest.json"
SIGNATURE = "seed/manifest.json.sig"

# Everywhere the list-signing public key is pinned. All four must agree, or a
# list one client accepts is a list another rejects. The two source files are
# searched for a 64-hex literal rather than parsed, which is enough: there is
# exactly one such literal in each.
KEY_FILES = ("blocklist/public_key.hex", "extension/public_key.hex")
KEY_SOURCES = ("extension/lib/verify.js", "macos/Hisn/BlocklistStore.swift")
HEX64 = re.compile(r"\b[0-9a-f]{64}\b")

# What `BlocklistStore.load` refuses, mirrored so a seed the app would reject
# is caught here rather than on first launch.
MIN_DOMAIN_COUNT = 100_000


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def pinned_public_key(repo: Path) -> tuple[str | None, list[str]]:
    """The one key every client pins, or the reasons there is not one."""
    problems: list[str] = []
    found: dict[str, str] = {}
    for rel in KEY_FILES:
        p = repo / rel
        if not p.exists():
            problems.append(f"{rel}: missing")
            continue
        found[rel] = p.read_text(encoding="utf-8").strip()
    for rel in KEY_SOURCES:
        p = repo / rel
        if not p.exists():
            problems.append(f"{rel}: missing")
            continue
        m = HEX64.search(p.read_text(encoding="utf-8"))
        if not m:
            problems.append(f"{rel}: no pinned public key found")
            continue
        found[rel] = m.group(0)
    keys = set(found.values())
    if len(keys) > 1:
        problems.append("clients pin different public keys: "
                        + ", ".join(f"{k}={v[:8]}…" for k, v in sorted(found.items())))
        return None, problems
    if not keys:
        return None, problems
    return keys.pop(), problems


def verify_signature(manifest_bytes: bytes, sig_hex: str, pub_hex: str) -> str | None:
    """None if the signature is good, else the reason it is not."""
    from cryptography.exceptions import InvalidSignature
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
    try:
        pub = Ed25519PublicKey.from_public_bytes(bytes.fromhex(pub_hex))
        pub.verify(bytes.fromhex(sig_hex.strip()), manifest_bytes)
    except (ValueError, InvalidSignature) as exc:
        return f"{SIGNATURE}: signature does not verify against the pinned key ({exc or 'invalid'})"
    return None


def verify(repo: Path = REPO) -> list[str]:
    """Every way the shipped bundle can be wrong, as a list. Empty means shippable."""
    problems: list[str] = []

    pub, key_problems = pinned_public_key(repo)
    problems += key_problems

    manifest_path = repo / MANIFEST
    sig_path = repo / SIGNATURE
    if not manifest_path.exists() or not sig_path.exists():
        problems.append(f"{MANIFEST} and {SIGNATURE} must both exist")
        return problems

    manifest_bytes = manifest_path.read_bytes()
    if pub is not None:
        bad = verify_signature(manifest_bytes, sig_path.read_text(encoding="utf-8"), pub)
        if bad:
            problems.append(bad)

    try:
        manifest = json.loads(manifest_bytes)
        artifacts = manifest["artifacts"]
        version = int(manifest["version"])
        domain_count = int(manifest["domain_count"])
    except (ValueError, KeyError, TypeError) as exc:
        problems.append(f"{MANIFEST}: malformed ({exc})")
        return problems

    if domain_count <= MIN_DOMAIN_COUNT:
        problems.append(f"{MANIFEST}: domain_count {domain_count:,} is at or below "
                        f"the {MIN_DOMAIN_COUNT:,} floor the filter enforces")

    for rel, name in SHIPPED:
        p = repo / rel
        if not p.exists():
            problems.append(f"{rel}: missing")
            continue
        meta = artifacts.get(name)
        if not meta:
            problems.append(f"{rel}: '{name}' is not in the signed manifest — "
                            f"nothing proves which build it came from")
            continue
        actual = sha256_file(p)
        if actual != meta.get("sha256"):
            problems.append(f"{rel}: sha256 {actual[:12]}… does not match the "
                            f"signed manifest's {str(meta.get('sha256'))[:12]}…")

    # The keyword layer must be what the sources say, not what someone last
    # remembered to regenerate. Same serialisation as build.py, so this is a
    # byte comparison, not a semantic one.
    seed_terms = repo / "seed/terms.json"
    if seed_terms.exists():
        payload = compile_terms(repo / "blocklist" / "terms")
        payload["version"] = version
        canonical = serialize_terms(payload).encode("utf-8")
        if canonical != seed_terms.read_bytes():
            problems.append("seed/terms.json differs from what blocklist/terms/ "
                            "compiles to — the sources changed and the seed "
                            "was not re-cut")

    return problems


def current_version(repo: Path) -> int | None:
    try:
        return int(json.loads((repo / MANIFEST).read_text(encoding="utf-8"))["version"])
    except (OSError, ValueError, KeyError, TypeError):
        return None


def sync(repo: Path, dist: Path, log=print) -> None:
    """Copy one signed build's shipped artifacts into place. Verify separately."""
    manifest = dist / "manifest.json"
    sig = dist / "manifest.json.sig"
    if not manifest.exists():
        raise SystemExit(f"{manifest}: no build there — run build.py first, or pass --build")
    if not sig.exists():
        raise SystemExit(f"{sig}: the build is unsigned, and an unsigned seed is one "
                         f"the filter refuses. Build with --sign-key.")

    for rel, name in SHIPPED:
        src = dist / name
        if not src.exists():
            raise SystemExit(f"{src}: artifact missing from the build")
        dst = repo / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_bytes(src.read_bytes())
        log(f"  {rel:44s} <- {name}")
    (repo / MANIFEST).write_bytes(manifest.read_bytes())
    (repo / SIGNATURE).write_bytes(sig.read_bytes())
    log(f"  {MANIFEST:44s} <- manifest.json (+ .sig)")


def cmd_verify(args: argparse.Namespace) -> int:
    problems = verify(Path(args.repo))
    if problems:
        print("seed bundle is NOT shippable:", file=sys.stderr)
        for p in problems:
            print(f"  - {p}", file=sys.stderr)
        print("\nfix: python3 blocklist/seed.py sync --build --sign-key <key>, "
              "or dispatch the publish workflow with refresh_seed", file=sys.stderr)
        return 1
    v = current_version(Path(args.repo))
    print(f"seed bundle verified   version={v}   "
          f"{len(SHIPPED)} shipped artifacts match the signed manifest")
    return 0


def cmd_sync(args: argparse.Namespace) -> int:
    repo = Path(args.repo)
    dist = Path(args.dist)

    if args.build:
        if not args.sign_key:
            raise SystemExit("--build needs --sign-key: an unsigned seed is useless")
        version = args.version if args.version is not None else current_version(repo)
        cmd = [sys.executable, str(Path(__file__).parent / "build.py"),
               "--out", str(dist), "--sign-key", args.sign_key]
        if args.sources:
            cmd += ["--sources", args.sources]
        if version is not None:
            cmd += ["--version", str(version)]
        print("+", " ".join(cmd), file=sys.stderr)
        subprocess.run(cmd, check=True)

    sync(repo, dist)
    problems = verify(repo)
    if problems:
        print("\nsynced, but the result does not verify:", file=sys.stderr)
        for p in problems:
            print(f"  - {p}", file=sys.stderr)
        return 1
    print(f"\nOK  seed version={current_version(repo)}  — commit seed/, "
          f"extension/seed/ and extension/rules/ together")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", default=str(REPO), help=argparse.SUPPRESS)
    sub = ap.add_subparsers(dest="cmd", required=True)

    v = sub.add_parser("verify", help="check every shipped artifact against the signed manifest")
    v.set_defaults(func=cmd_verify)

    s = sub.add_parser("sync", help="copy a signed build's artifacts into place, then verify")
    s.add_argument("--dist", default=str(REPO / "dist"),
                   help="a directory build.py wrote (default: dist/)")
    s.add_argument("--build", action="store_true",
                   help="run build.py into --dist first")
    s.add_argument("--sign-key", default=None,
                   help="PEM Ed25519 private key, passed to build.py with --build")
    s.add_argument("--version", type=int, default=None,
                   help="version for --build (default: the seed's current version)")
    s.add_argument("--sources", default=None, help="passed to build.py with --build")
    s.set_defaults(func=cmd_sync)

    args = ap.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
