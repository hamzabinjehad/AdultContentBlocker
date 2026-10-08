#!/usr/bin/env python3
"""Verify a signed Hisn bundle and atomically publish AdGuard Home DNS rules.

No downloads, DNS changes, resolver reloads, or router credentials are involved.
The state directory and pinned key must be controlled by the administrator.
"""

from __future__ import annotations

import argparse
import contextlib
import datetime as dt
import fcntl
import hashlib
import json
import os
import re
import stat
import sys
import time
import uuid
from pathlib import Path

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey


DEFAULT_SAFETY = Path(__file__).resolve().parent.parent / "blocklist/sources.json"
ARTIFACTS = {"domains.txt": "domain_count", "domains_core.txt": "core_domain_count"}
FORMAT = "adguard-domain-subtree-v1"
OUTPUT_FORMATS = {
    "adguard": (FORMAT, "adguard.txt"),
    "hosts": ("hosts-exact-domain-v1", "hosts.txt"),
}
MAX_JSON = 1 << 20
MAX_ARTIFACT = 512 << 20
MAX_LINE = 4096
HEX64 = re.compile(r"[0-9a-f]{64}")
NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
GENERATION = re.compile(r"v[1-9][0-9]*-[0-9a-f]{16}-[0-9a-f]{32}")
# Same canonical DNS-label grammar as blocklist/build.py. The builder currently
# permits dotted numeric labels too; do not silently change signed-list counts.
DOMAIN = re.compile(
    r"(?=.{1,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\.(?!-)[a-z0-9-]{1,63}(?<!-))+"
)
DIR_FLAGS = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC
READ_FLAGS = os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK | os.O_CLOEXEC


class Rejected(ValueError):
    """Untrusted input or inconsistent persisted state; nothing is accepted."""


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def integer(value: object, label: str, minimum: int = 0) -> int:
    if type(value) is not int or not minimum <= value <= (1 << 63) - 1:
        raise Rejected(f"{label} must be an integer >= {minimum}")
    return value


def hex_digest(value: object, label: str) -> str:
    if not isinstance(value, str) or HEX64.fullmatch(value) is None:
        raise Rejected(f"{label} must be a lowercase SHA-256 digest")
    return value


def parse_json(raw: bytes, label: str) -> dict:
    def unique(pairs: list[tuple[str, object]]) -> dict:
        obj: dict = {}
        for key, value in pairs:
            if key in obj:
                raise Rejected(f"{label} has duplicate JSON key {key!r}")
            obj[key] = value
        return obj

    try:
        obj = json.loads(raw, object_pairs_hook=unique,
                         parse_constant=lambda value: (_ for _ in ()).throw(
                             Rejected(f"{label} contains {value}")))
    except (ValueError, UnicodeError) as exc:
        raise Rejected(f"invalid {label}: {exc}") from exc
    if not isinstance(obj, dict):
        raise Rejected(f"{label} must be a JSON object")
    return obj


@contextlib.contextmanager
def regular_file(name: str | Path, *, directory: int | None = None):
    fd = os.open(name, READ_FLAGS, dir_fd=directory)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise Rejected(f"{name}: expected a regular file")
        with os.fdopen(fd, "rb", closefd=False) as handle:
            yield handle
    finally:
        os.close(fd)


def read_small(name: str | Path, limit: int = MAX_JSON,
               *, directory: int | None = None) -> bytes:
    with regular_file(name, directory=directory) as handle:
        raw = handle.read(limit + 1)
    if len(raw) > limit:
        raise Rejected(f"{name}: exceeds {limit} bytes")
    return raw


def load_key(path: Path) -> tuple[Ed25519PublicKey, str]:
    try:
        text = read_small(path, 256).decode("ascii").strip()
        if HEX64.fullmatch(text) is None:
            raise ValueError("expected exactly 64 lowercase hex characters")
        raw = bytes.fromhex(text)
        return Ed25519PublicKey.from_public_bytes(raw), digest(raw)
    except (ValueError, UnicodeError) as exc:
        raise Rejected(f"invalid pinned public key: {exc}") from exc


def verify_signature(raw: bytes, signature: bytes, key: Ed25519PublicKey) -> None:
    try:
        text = signature.decode("ascii").strip()
        if re.fullmatch(r"[0-9a-f]{128}", text) is None:
            raise ValueError("expected a 64-byte hex signature")
        key.verify(bytes.fromhex(text), raw)
    except (ValueError, UnicodeError, InvalidSignature) as exc:
        raise Rejected("manifest signature does not verify against the pinned key") from exc


def validate_manifest(raw: bytes, artifact: str) -> tuple[dict, int, dict, int]:
    manifest = parse_json(raw, "manifest")
    if type(manifest.get("schema")) is not int or manifest["schema"] != 1:
        raise Rejected("unsupported manifest schema (expected 1)")
    version = integer(manifest.get("version"), "manifest version", 1)
    entries = manifest.get("artifacts")
    if not isinstance(entries, dict):
        raise Rejected("manifest artifacts must be an object")
    # Even unused manifest paths are rejected. Never interpret a signed name
    # as an unrestricted filesystem path; selected names are fixed choices.
    for name, entry in entries.items():
        if NAME.fullmatch(name) is None:
            raise Rejected(f"unsafe artifact name {name!r}")
        if not isinstance(entry, dict):
            raise Rejected(f"invalid artifact metadata for {name}")
        hex_digest(entry.get("sha256"), f"{name} sha256")
        integer(entry.get("bytes"), f"{name} bytes")
    if artifact not in entries:
        raise Rejected(f"signed manifest does not include {artifact}")
    count = integer(manifest.get(ARTIFACTS[artifact]), ARTIFACTS[artifact], 1)
    return manifest, version, entries[artifact], count


def valid_domain(domain: str) -> bool:
    return (DOMAIN.fullmatch(domain) is not None and not domain.endswith(
        (".local", ".localdomain", ".invalid", ".test", ".example")))


def suffix_chain(domain: str) -> list[str]:
    labels = domain.split(".")
    return [".".join(labels[index:]) for index in range(len(labels) - 1)]


class SafetyPolicy:
    def __init__(self, path: Path):
        policy = parse_json(read_small(path), "local safety policy")
        lists: dict[str, list[str]] = {}
        for field in ("never_block_suffix", "never_block_apex"):
            values = policy.get(field)
            if (not isinstance(values, list) or not values or
                    any(not isinstance(value, str) or not valid_domain(value)
                        for value in values)):
                raise Rejected(f"local safety policy needs canonical {field} domains")
            lists[field] = sorted(set(values))
        self.suffixes = set(lists["never_block_suffix"])
        self.apexes = set(lists["never_block_apex"])
        # Protect www.apex exactly without granting a whole CDN subtree an
        # exception. A listed tenant of that CDN must remain blockable.
        self.exact = self.apexes | {"www." + value for value in self.apexes}
        self.ancestors = {parent for value in self.suffixes | self.apexes
                          for parent in suffix_chain(value)[1:]}
        self.sha256 = digest(json.dumps(lists, sort_keys=True,
                                        separators=(",", ":")).encode())

    def check(self, domain: str) -> None:
        if (domain in self.exact or domain in self.ancestors or
                any(parent in self.suffixes for parent in suffix_chain(domain))):
            raise Rejected(f"{domain}: would block protected infrastructure or a shared apex")


def trusted_directory(fd: int, label: str) -> None:
    info = os.fstat(fd)
    if info.st_uid != os.geteuid() or info.st_mode & 0o022:
        raise Rejected(f"{label} must belong to this administrator and deny group/world writes")


@contextlib.contextmanager
def state_lock(path: Path, timeout: float):
    # Only the selected directory is created; its parent must already exist.
    try:
        path.mkdir(mode=0o755)
    except FileExistsError:
        pass
    state = os.open(path, DIR_FLAGS)
    lock = generations = None
    try:
        trusted_directory(state, "state directory")
        lock = os.open(".publish.lock", os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW |
                       os.O_CLOEXEC | os.O_NONBLOCK, 0o600, dir_fd=state)
        info = os.fstat(lock)
        if (not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or
                info.st_uid != os.geteuid() or info.st_mode & 0o077):
            raise Rejected("publisher lock must be a private regular file")
        deadline = time.monotonic() + timeout
        while True:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise Rejected("another publisher holds the update lock")
                time.sleep(0.05)
        try:
            os.mkdir("generations", mode=0o755, dir_fd=state)
        except FileExistsError:
            pass
        generations = os.open("generations", DIR_FLAGS, dir_fd=state)
        trusted_directory(generations, "generations directory")
        yield state, generations
    finally:
        if generations is not None:
            os.close(generations)
        if lock is not None:
            os.close(lock)
        os.close(state)


def file_digest(name: str, directory: int) -> tuple[str, int]:
    hasher = hashlib.sha256()
    count = 0
    with regular_file(name, directory=directory) as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            hasher.update(chunk)
            count += len(chunk)
    return hasher.hexdigest(), count


def load_current(state: int, generations: int, key: Ed25519PublicKey,
                 key_hash: str) -> tuple[str | None, dict | None]:
    try:
        target = os.readlink("current", dir_fd=state)
    except FileNotFoundError:
        return None, None
    prefix = "generations/"
    if not target.startswith(prefix) or GENERATION.fullmatch(target[len(prefix):]) is None:
        raise Rejected("current pointer has an unsafe generation target")
    directory = os.open(target[len(prefix):], DIR_FLAGS, dir_fd=generations)
    try:
        trusted_directory(directory, "current generation")
        meta = parse_json(read_small("metadata.json", directory=directory), "saved metadata")
        output = next((entry for entry in OUTPUT_FORMATS.values()
                       if entry[0] == meta.get("format")), None)
        if type(meta.get("schema")) is not int or meta["schema"] != 1 or output is None:
            raise Rejected("unsupported saved metadata schema or rule format")
        if meta.get("public_key_sha256") != key_hash:
            raise Rejected("pinned key differs from the key that established this state")
        artifact = meta.get("artifact")
        if not isinstance(artifact, str) or artifact not in ARTIFACTS:
            raise Rejected("saved metadata has an unsupported artifact")
        raw = read_small("manifest.json", directory=directory)
        verify_signature(raw, read_small("manifest.json.sig", 1024, directory=directory), key)
        _, version, entry, count = validate_manifest(raw, artifact)
        expected = {"version": version, "manifest_sha256": digest(raw),
                    "artifact_sha256": entry["sha256"], "artifact_bytes": entry["bytes"],
                    "domain_count": count, "generation": target[len(prefix):]}
        for field in ("version", "artifact_bytes", "domain_count", "output_bytes"):
            integer(meta.get(field), f"saved {field}")
        if any(meta.get(field) != value for field, value in expected.items()):
            raise Rejected("saved metadata does not match its signed manifest/generation")
        hex_digest(meta.get("safety_sha256"), "saved safety digest")
        output_hash, output_size = file_digest(output[1], directory)
        if (meta.get("output_sha256") != output_hash or
                meta.get("output_bytes") != output_size):
            raise Rejected("current DNS rules are damaged; refusing to reset rollback state")
        return target, meta
    finally:
        os.close(directory)


def write_file(directory: int, name: str, raw: bytes) -> None:
    fd = os.open(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW |
                 os.O_CLOEXEC, 0o644, dir_fd=directory)
    with os.fdopen(fd, "wb") as handle:
        handle.write(raw)
        handle.flush()
        os.fsync(handle.fileno())


def render_artifact(bundle: int, stage: int, artifact: str, entry: dict,
                    count: int, version: int, safety: SafetyPolicy,
                    max_bytes: int, output_format: str = "adguard") -> tuple[str, int]:
    if entry["bytes"] > max_bytes:
        raise Rejected(f"{artifact}: signed size exceeds the configured byte limit")
    fd = os.open(OUTPUT_FORMATS[output_format][1], os.O_WRONLY | os.O_CREAT | os.O_EXCL |
                 os.O_NOFOLLOW | os.O_CLOEXEC, 0o644, dir_fd=stage)
    source_hash = hashlib.sha256()
    output_hash = hashlib.sha256()
    source_size = output_size = domains = 0
    previous = ""
    with os.fdopen(fd, "wb") as output, regular_file(artifact, directory=bundle) as source:
        def emit(raw: bytes) -> None:
            nonlocal output_size
            output.write(raw)
            output_hash.update(raw)
            output_size += len(raw)

        if output_format == "adguard":
            emit(f"! Hisn verified DNS rules; version={version}; artifact={artifact}\n"
                 "! Each rule blocks the domain and its subdomains.\n".encode("ascii"))
        else:
            # Hosts files contain exact names, with no invented descendants.
            # Consumers must separately test their subdomain/AAAA behavior.
            emit(f"# Hisn verified hosts; version={version}; artifact={artifact}\n"
                 "# Exact listed domains only; wildcard coverage is not guaranteed.\n".encode("ascii"))
        while True:
            raw = source.readline(MAX_LINE + 1)
            if not raw:
                break
            source_size += len(raw)
            if source_size > max_bytes or len(raw) > MAX_LINE:
                raise Rejected(f"{artifact}: oversized content/line")
            source_hash.update(raw)
            line = raw.removesuffix(b"\n")
            if not line or line.startswith(b"#"):
                continue
            try:
                domain = line.decode("ascii")
            except UnicodeError as exc:
                raise Rejected(f"{artifact}: non-ASCII domain; use signed canonical punycode") from exc
            if not valid_domain(domain):
                raise Rejected(f"{artifact}: noncanonical DNS domain {domain[:80]!r}")
            if domain <= previous:
                raise Rejected(f"{artifact}: domains must be sorted and unique")
            previous = domain
            safety.check(domain)
            domains += 1
            emit((f"||{domain}^\n" if output_format == "adguard"
                  else f"0.0.0.0 {domain}\n").encode("ascii"))
        if source_size != entry["bytes"] or source_hash.hexdigest() != entry["sha256"]:
            raise Rejected(f"{artifact}: bytes/SHA-256 differ from the signed manifest")
        if domains != count:
            raise Rejected(f"{artifact}: counted {domains} domains; signed count is {count}")
        output.flush()
        os.fsync(output.fileno())
    return output_hash.hexdigest(), output_size


def remove_stage(generations: int, name: str) -> None:
    """Only remove our fixed filenames from our private staging directory."""
    directory = os.open(name, DIR_FLAGS, dir_fd=generations)
    try:
        for file in ("adguard.txt", "hosts.txt", "metadata.json", "manifest.json", "manifest.json.sig"):
            try:
                os.unlink(file, dir_fd=directory)
            except FileNotFoundError:
                pass
    finally:
        os.close(directory)
    os.rmdir(name, dir_fd=generations)


def switch_current(state: int, target: str, previous: str | None) -> None:
    temporary = ".current-" + uuid.uuid4().hex
    swapped = False
    try:
        os.symlink(target, temporary, dir_fd=state)
        os.replace(temporary, "current", src_dir_fd=state, dst_dir_fd=state)
        swapped = True
        os.fsync(state)
    except OSError as exc:
        if swapped:
            # A directory fsync failure is not durable acceptance. Restore the
            # previous complete generation if the filesystem still permits it.
            try:
                if previous is None:
                    os.unlink("current", dir_fd=state)
                else:
                    os.symlink(previous, temporary, dir_fd=state)
                    os.replace(temporary, "current", src_dir_fd=state, dst_dir_fd=state)
                os.fsync(state)
            except OSError as restore:
                raise OSError("commit durability failed and pointer restoration failed; "
                              "inspect current before retrying") from restore
        raise exc
    finally:
        try:
            os.unlink(temporary, dir_fd=state)
        except FileNotFoundError:
            pass


def publish(bundle: Path, state: Path, public_key: Path, *,
            artifact: str = "domains.txt", safety_policy: Path = DEFAULT_SAFETY,
            min_version: int = 1, min_count: int | None = None,
            max_bytes: int = MAX_ARTIFACT, lock_timeout: float = 30.0,
            output_format: str = "adguard") -> dict:
    if output_format not in OUTPUT_FORMATS:
        raise Rejected("unsupported output format (choose adguard or hosts)")
    if artifact not in ARTIFACTS:
        raise Rejected("unsupported artifact (choose domains.txt or domains_core.txt)")
    integer(min_version, "minimum version", 1)
    integer(max_bytes, "maximum artifact bytes", 1)
    if min_count is None:
        min_count = 100_000 if artifact == "domains.txt" else 50_000
    integer(min_count, "minimum domain count", 1)
    if not 0 <= lock_timeout <= 60:
        raise Rejected("lock timeout must be between 0 and 60 seconds")
    key, key_hash = load_key(public_key)
    safety = SafetyPolicy(safety_policy)
    bundle_fd = os.open(bundle, DIR_FLAGS)
    try:
        raw = read_small("manifest.json", directory=bundle_fd)
        signature = read_small("manifest.json.sig", 1024, directory=bundle_fd)
        verify_signature(raw, signature, key)
        _, version, entry, count = validate_manifest(raw, artifact)
        if version < min_version:
            raise Rejected(f"manifest version {version} is below the configured minimum {min_version}")
        if count < min_count:
            raise Rejected(f"signed domain count {count} is below the configured minimum {min_count}")
        with state_lock(state, lock_timeout) as (state_fd, generations):
            previous_target, previous = load_current(state_fd, generations, key, key_hash)
            if previous is not None:
                if previous["format"] != OUTPUT_FORMATS[output_format][0]:
                    raise Rejected("output format differs from this state; use a separate state directory")
                if previous["artifact"] != artifact:
                    raise Rejected("artifact tier differs from this state; use a separate state directory")
                if version < previous["version"]:
                    raise Rejected(f"rollback rejected: {version} < held version {previous['version']}")
                if version == previous["version"] and (
                        previous["manifest_sha256"] != digest(raw) or
                        previous["safety_sha256"] != safety.sha256):
                    raise Rejected("same-version content/configuration differs from the accepted generation")
            generation = f"v{version}-{digest(raw)[:16]}-{uuid.uuid4().hex}"
            stage_name = ".stage-" + uuid.uuid4().hex
            os.mkdir(stage_name, mode=0o700, dir_fd=generations)
            stage = os.open(stage_name, DIR_FLAGS, dir_fd=generations)
            renamed = False
            try:
                output_hash, output_size = render_artifact(
                    bundle_fd, stage, artifact, entry, count, version, safety, max_bytes, output_format)
                if previous is not None and version == previous["version"]:
                    if (previous["output_sha256"] != output_hash or
                            previous["output_bytes"] != output_size):
                        raise Rejected("same-version rendered rules differ from the accepted generation")
                    return {"status": "unchanged", **previous}
                meta = {"schema": 1, "format": OUTPUT_FORMATS[output_format][0], "generation": generation,
                        "version": version, "artifact": artifact,
                        "domain_count": count, "artifact_bytes": entry["bytes"],
                        "artifact_sha256": entry["sha256"], "manifest_sha256": digest(raw),
                        "public_key_sha256": key_hash, "safety_sha256": safety.sha256,
                        "output_bytes": output_size, "output_sha256": output_hash,
                        "published_at": dt.datetime.now(dt.timezone.utc).isoformat()}
                write_file(stage, "manifest.json", raw)
                write_file(stage, "manifest.json.sig", signature)
                write_file(stage, "metadata.json", (json.dumps(meta, indent=2, sort_keys=True) + "\n").encode())
                os.fchmod(stage, 0o755)
                os.fsync(stage)
                os.rename(stage_name, generation, src_dir_fd=generations, dst_dir_fd=generations)
                renamed = True
                os.fsync(generations)
                switch_current(state_fd, "generations/" + generation, previous_target)
                return {"status": "published", **meta}
            finally:
                os.close(stage)
                if not renamed:
                    remove_stage(generations, stage_name)
    finally:
        os.close(bundle_fd)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True, help="local signed build/seed directory")
    parser.add_argument("--public-key", type=Path, required=True, help="administrator-pinned 64-hex Ed25519 key file")
    parser.add_argument("--state", type=Path, required=True, help="administrator-owned publication directory")
    parser.add_argument("--artifact", choices=tuple(ARTIFACTS), default="domains.txt")
    parser.add_argument("--output-format", choices=tuple(OUTPUT_FORMATS), default="adguard",
                        help="adguard subtree rules or exact-name hosts export for compatible routers")
    parser.add_argument("--safety-policy", type=Path, default=DEFAULT_SAFETY,
                        help="trusted local sources.json containing the existing never-block rails")
    parser.add_argument("--min-version", type=int, default=1, help="trusted initial version floor; persisted versions also apply")
    parser.add_argument("--min-count", type=int, help="domain floor (default: full 100000; core 50000)")
    parser.add_argument("--max-bytes", type=int, default=MAX_ARTIFACT)
    parser.add_argument("--lock-timeout", type=float, default=30.0)
    args = vars(parser.parse_args(argv))
    try:
        result = publish(**args)
    except (OSError, Rejected) as exc:
        print(f"DNS generation rejected: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
