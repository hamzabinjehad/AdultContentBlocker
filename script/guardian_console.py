#!/usr/bin/env python3
"""Laptop-side reference console for Hisn's guardian pairing protocol.

    python3 script/guardian_console.py keygen --out ~/.hisn-guardian
    python3 script/guardian_console.py offer --key ~/.hisn-guardian/guardian.key
    python3 script/guardian_console.py verify-response --key ... --offer ... --response ...
    python3 script/guardian_console.py sign-policy --key ... --device ... --counter 1 \
        --days 30 --rules rules.json --out policy.json
    python3 script/guardian_console.py verify-policy --public-key ... --policy policy.json --device ...

This is a protocol reference, not the final Mac app. It writes no browsing data
and talks to no server. The private key should be encrypted; `--no-password` is
for tests and throwaway prototypes only.
"""
from __future__ import annotations

import argparse
import getpass
import json
import os
import sys
import time
from pathlib import Path

import guardian_protocol as gp


class ConsoleError(Exception):
    pass


def read_private(path: Path, password_env: str | None):
    try:
        data = path.read_bytes()
    except OSError as exc:
        raise ConsoleError(f"cannot read {path}: {exc}") from exc
    password = None
    if password_env:
        value = os.environ.get(password_env)
        if not value:
            raise ConsoleError(f"{password_env} is not set")
        password = value.encode("utf-8")
    return gp.load_private_pem(data, password)


def read_public(path: Path):
    try:
        data = path.read_bytes()
    except OSError as exc:
        raise ConsoleError(f"cannot read {path}: {exc}") from exc
    text = data.decode("ascii", "strict").strip()
    if text.startswith("-----BEGIN"):
        return gp.load_public_pem(data)
    return gp.load_public_b64(text)


def write_private(path: Path, data: bytes, force: bool) -> None:
    if path.exists() and not force:
        raise ConsoleError(f"{path} exists; pass --force to replace it")
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "wb") as stream:
        stream.write(data)
    os.chmod(path, 0o600)


def write_text(path: Path, text: str, force: bool, mode: int = 0o644) -> None:
    if path.exists() and not force:
        raise ConsoleError(f"{path} exists; pass --force to replace it")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    os.chmod(path, mode)


def keygen(args) -> int:
    if args.no_password and args.password_env:
        raise ConsoleError("use either --no-password or --password-env, not both")
    password = None
    if args.password_env:
        value = os.environ.get(args.password_env)
        if not value:
            raise ConsoleError(f"{args.password_env} is not set")
        password = value.encode("utf-8")
    elif not args.no_password:
        if not sys.stdin.isatty():
            raise ConsoleError("pass --password-env or --no-password in a non-interactive shell")
        first = getpass.getpass("Guardian key password: ")
        second = getpass.getpass("Repeat: ")
        if first != second:
            raise ConsoleError("passwords do not match")
        if not first:
            raise ConsoleError("empty password; use --no-password for a throwaway key")
        password = first.encode("utf-8")

    key = gp.generate_key()
    out = Path(args.out)
    private_path = out / "guardian.key"
    public_path = out / "guardian.pub"
    write_private(private_path, gp.private_pem(key, password), args.force)
    write_text(public_path, gp.public_b64(key.public_key()) + "\n", args.force)
    print(f"private key: {private_path} (mode 0600)")
    print(f"public key:  {public_path}")
    print(f"device id of this guardian key: {gp.device_id(key.public_key())}")
    if args.no_password:
        print("WARNING: the private key is not encrypted. Use it for a prototype only.",
              file=sys.stderr)
    return 0


def offer(args) -> int:
    key = read_private(Path(args.key), args.password_env)
    nonce = bytes.fromhex(args.nonce) if args.nonce else None
    if nonce is not None and len(nonce) != gp.NONCE_BYTES:
        raise ConsoleError(f"--nonce must be {gp.NONCE_BYTES} bytes of hex")
    value = gp.build_offer(key.public_key(), nonce=nonce, label=args.label)
    print(value.uri)
    return 0


def verify_response(args) -> int:
    key = read_private(Path(args.key), args.password_env)
    try:
        offer_value = gp.parse_offer(args.offer)
    except gp.ProtocolError as exc:
        raise ConsoleError(str(exc)) from exc
    try:
        response = json.loads(Path(args.response).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ConsoleError(f"cannot read response: {exc}") from exc
    device, sas = gp.verify_response(offer_value, response, key)
    print(json.dumps({"device_id": gp.device_id(device),
                      "device_public_key": gp.public_b64(device),
                      "sas": sas}, indent=2))
    return 0


def sign_policy(args) -> int:
    key = read_private(Path(args.key), args.password_env)
    try:
        rules = json.loads(Path(args.rules).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ConsoleError(f"cannot read rules: {exc}") from exc
    if not isinstance(rules, dict):
        raise ConsoleError("rules file must contain a JSON object")
    issued = args.issued_at if args.issued_at is not None else int(time.time())
    expires = args.not_after if args.not_after is not None else issued + args.days * 86400
    policy = gp.sign_policy(key, device=args.device, counter=args.counter,
                            issued_at=issued, not_after=expires, rules=rules)
    out = Path(args.out)
    write_text(out, json.dumps(policy, indent=2, sort_keys=True) + "\n", args.force)
    print(f"wrote {out} (counter {args.counter}, expires {expires})")
    return 0


def verify_policy(args) -> int:
    public = read_public(Path(args.public_key))
    try:
        policy = json.loads(Path(args.policy).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ConsoleError(f"cannot read policy: {exc}") from exc
    now = args.now if args.now is not None else int(time.time())
    body = gp.verify_policy(public, policy, device=args.device, now=now,
                            last_counter=args.last_counter)
    print(json.dumps(body, indent=2, sort_keys=True))
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0],
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    gen = sub.add_parser("keygen", help="create a guardian keypair")
    gen.add_argument("--out", required=True, help="directory for guardian.key and guardian.pub")
    gen.add_argument("--password-env", help="read the key password from this environment variable")
    gen.add_argument("--no-password", action="store_true",
                     help="write an unencrypted key; prototype use only")
    gen.add_argument("--force", action="store_true", help="replace existing files")
    gen.set_defaults(handler=keygen)

    off = sub.add_parser("offer", help="print the pairing URI for the phone")
    off.add_argument("--key", required=True)
    off.add_argument("--password-env")
    off.add_argument("--label", default="Guardian")
    off.add_argument("--nonce", help="16 bytes of hex; for reproducible tests only")
    off.set_defaults(handler=offer)

    resp = sub.add_parser("verify-response", help="verify the phone's pairing response")
    resp.add_argument("--key", required=True)
    resp.add_argument("--password-env")
    resp.add_argument("--offer", required=True)
    resp.add_argument("--response", required=True)
    resp.set_defaults(handler=verify_response)

    sign = sub.add_parser("sign-policy", help="sign a policy envelope")
    sign.add_argument("--key", required=True)
    sign.add_argument("--password-env")
    sign.add_argument("--device", required=True)
    sign.add_argument("--counter", type=int, required=True)
    sign.add_argument("--days", type=int, default=30)
    sign.add_argument("--issued-at", type=int)
    sign.add_argument("--not-after", type=int)
    sign.add_argument("--rules", required=True)
    sign.add_argument("--out", required=True)
    sign.add_argument("--force", action="store_true")
    sign.set_defaults(handler=sign_policy)

    verify = sub.add_parser("verify-policy", help="verify a policy envelope")
    verify.add_argument("--public-key", required=True)
    verify.add_argument("--policy", required=True)
    verify.add_argument("--device", required=True)
    verify.add_argument("--last-counter", type=int, default=0)
    verify.add_argument("--now", type=int)
    verify.set_defaults(handler=verify_policy)

    args = parser.parse_args(argv)
    try:
        return args.handler(args)
    except (ConsoleError, gp.ProtocolError, OSError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
