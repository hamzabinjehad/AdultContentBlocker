#!/usr/bin/env python3
"""
Hisn blocklist builder.

Fetches upstream blocklists, normalises them into one canonical domain set,
collapses redundant subdomains, applies the never-block safety rail, and emits
signed artifacts for every consumer (macOS network extension, browser extension,
DNS resolver).

Usage:
    python3 build.py --out ../dist
    python3 build.py --out ../dist --sign-key ../keys/blocklist_ed25519.pem

Design notes
------------
* Every artifact is hashed and listed in `manifest.json`, and only the manifest
  is signed. One signature covers everything, and a client can verify a single
  artifact without downloading the rest.
* `version` is a monotonically increasing integer. Clients MUST refuse a
  manifest whose version is lower than the one they already hold; otherwise an
  attacker who can intercept traffic can "unblock" by replaying an old list.
* The never-block rail is applied last so that no upstream source can ever
  cause a lockout from Apple, certificate, or help/support infrastructure.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import datetime as dt
import hashlib
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

USER_AGENT = "hisn-blocklist-builder/1.0 (+https://github.com/hisn-app)"
FETCH_TIMEOUT = 120

# A syntactically valid DNS name we are willing to put in a blocklist.
DOMAIN_RE = re.compile(
    r"^(?=.{1,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\.(?!-)[a-z0-9-]{1,63}(?<!-))+$"
)

HOSTS_PREFIXES = ("0.0.0.0", "127.0.0.1", "::1", "::")


# --------------------------------------------------------------------------- #
# Fetching
# --------------------------------------------------------------------------- #

def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=FETCH_TIMEOUT) as resp:
        return resp.read().decode("utf-8", errors="replace")


# --------------------------------------------------------------------------- #
# Parsing
# --------------------------------------------------------------------------- #

def parse_hosts(text: str) -> set[str]:
    """0.0.0.0 example.com  /  127.0.0.1 example.com"""
    out: set[str] = set()
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) < 2 or parts[0] not in HOSTS_PREFIXES:
            continue
        for host in parts[1:]:
            out.add(host.strip().lower().rstrip("."))
    return out


def parse_plain(text: str) -> set[str]:
    """One bare domain per line."""
    out: set[str] = set()
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip().lower().rstrip(".")
        if line:
            out.add(line)
    return out


def parse_adblock(text: str) -> set[str]:
    """||example.com^  — we only take pure domain-anchored rules."""
    out: set[str] = set()
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith(("!", "#", "[")):
            continue
        if line.startswith("@@"):          # exception rule, not a block
            continue
        if not line.startswith("||"):
            continue
        rule = line[2:]
        # Drop everything after the separator / options marker.
        for sep in ("^", "$", "/"):
            idx = rule.find(sep)
            if idx != -1:
                rule = rule[:idx]
        rule = rule.strip().lower().rstrip(".")
        if rule:
            out.add(rule)
    return out


PARSERS = {"hosts": parse_hosts, "plain": parse_plain, "adblock": parse_adblock}


# --------------------------------------------------------------------------- #
# Normalising
# --------------------------------------------------------------------------- #

def clean(domains: set[str]) -> set[str]:
    """Keep only syntactically valid, non-local DNS names."""
    out: set[str] = set()
    for d in domains:
        # Normalise before validating. DOMAIN_RE only accepts lowercase, so an
        # entry like "Example.com" — which is exactly how a human adds one to
        # extra_block — would otherwise be dropped silently rather than fixed.
        d = d.strip().lower().rstrip(".")
        if d.startswith("www."):
            d = d[4:]
        if not DOMAIN_RE.match(d):
            continue
        if d.endswith((".local", ".localdomain", ".invalid", ".test", ".example")):
            continue
        if d in ("localhost", "local", "broadcasthost"):
            continue
        out.add(d)
    return out


def collapse_subdomains(domains: set[str]) -> set[str]:
    """
    Drop `a.b.example.com` when `example.com` is already blocked.

    Every consumer of these artifacts blocks a domain *and all of its
    subdomains*, so keeping the children is pure bloat — and bloat matters:
    the browser extension has a hard rule-count ceiling.
    """
    kept: set[str] = set()
    # Shortest first: a parent is always shorter than its children.
    for d in sorted(domains, key=lambda x: (x.count("."), len(x))):
        parts = d.split(".")
        covered = False
        for i in range(1, len(parts) - 1):
            if ".".join(parts[i:]) in kept:
                covered = True
                break
        if not covered:
            kept.add(d)
    return kept


def apply_never_block(
    domains: set[str],
    suffixes: list[str],
    apexes: list[str],
) -> tuple[set[str], list[str]]:
    """
    Apply the two-tier safety rail. Returns (kept, removed).

    `suffixes`  protect the domain and its whole subtree (OS infrastructure).
    `apexes`    protect only the apex itself — subdomains stay blockable, which
                is what shared CDNs need: cloudfront.net must resolve, but
                d1a7u....cloudfront.net serving an adult CDN must not.
    """
    suffix_set = {n.lower().lstrip(".") for n in suffixes}
    apex_set = {n.lower().lstrip(".") for n in apexes}
    kept, removed = set(), []
    for d in domains:
        parts = d.split(".")
        under_suffix = any(
            ".".join(parts[i:]) in suffix_set for i in range(len(parts))
        )
        is_apex = d in apex_set
        (removed.append(d) if (under_suffix or is_apex) else kept.add(d))
    return kept, sorted(removed)


# --------------------------------------------------------------------------- #
# Artifacts
# --------------------------------------------------------------------------- #

def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def write_domains(path: Path, domains: list[str], header: str) -> None:
    with path.open("w", encoding="utf-8") as fh:
        fh.write(header)
        fh.write("\n".join(domains))
        fh.write("\n")


def write_dnr_rules(path: Path, domains: list[str], mode: str, limit: int) -> int:
    """
    Emit Chrome declarativeNetRequest static rules.

    Chrome caps a static ruleset, so we bucket by rule id and rely on
    `requestDomains` — one rule can carry many domains, which is dramatically
    more efficient than one rule per domain.
    """
    rules = []
    if mode == "block":
        # Chunk domains across rules; each rule holds up to `chunk` domains.
        # Truncate to `limit` FIRST: slicing `domains[i:i+chunk]` off the full
        # list lets the final chunk overshoot the limit by up to chunk-1.
        domains = domains[:limit]
        chunk = 1000
        rid = 1
        for i in range(0, len(domains), chunk):
            rules.append({
                "id": rid,
                "priority": 1,
                "action": {
                    "type": "redirect",
                    "redirect": {"extensionPath": "/blocked.html"},
                },
                "condition": {
                    "requestDomains": domains[i:i + chunk],
                    "resourceTypes": ["main_frame", "sub_frame"],
                },
            })
            rid += 1
        # Block non-document subresources outright (no redirect, just drop).
        rid_base = rid
        for i in range(0, len(domains), chunk):
            rules.append({
                "id": rid_base + (i // chunk),
                "priority": 1,
                "action": {"type": "block"},
                "condition": {
                    "requestDomains": domains[i:i + chunk],
                    "resourceTypes": [
                        "script", "image", "media", "xmlhttprequest",
                        "stylesheet", "font", "object", "websocket", "other",
                    ],
                },
            })
    path.write_text(json.dumps(rules, separators=(",", ":")), encoding="utf-8")
    return len(rules)


# --------------------------------------------------------------------------- #
# Signing
# --------------------------------------------------------------------------- #

def sign_manifest(manifest_bytes: bytes, key_path: Path) -> str:
    from cryptography.hazmat.primitives import serialization

    key = serialization.load_pem_private_key(
        key_path.read_bytes(), password=None
    )
    return key.sign(manifest_bytes).hex()


# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #

def main() -> int:
    ap = argparse.ArgumentParser(description="Build the Hisn blocklist artifacts.")
    ap.add_argument("--sources", default=str(Path(__file__).parent / "sources.json"))
    ap.add_argument("--out", default=str(Path(__file__).parent.parent / "dist"))
    ap.add_argument("--sign-key", default=None,
                    help="PEM Ed25519 private key. Unsigned build if omitted.")
    ap.add_argument("--version", type=int, default=None,
                    help="Monotonic version. Defaults to previous+1, else 1.")
    ap.add_argument("--dnr-limit", type=int, default=250_000,
                    help="Max domains pushed into the browser static ruleset.")
    args = ap.parse_args()

    cfg = json.loads(Path(args.sources).read_text(encoding="utf-8"))
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    active = [s for s in cfg["sources"] if s.get("enabled", True)]
    print(f"Fetching {len(active)} sources...", file=sys.stderr)

    raw: dict[str, set[str]] = {}
    stats: list[dict] = []

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(fetch, s["url"]): s for s in active}
        for fut in concurrent.futures.as_completed(futures):
            src = futures[fut]
            try:
                text = fut.result()
            except Exception as exc:                       # noqa: BLE001
                print(f"  !! {src['id']}: FAILED ({exc})", file=sys.stderr)
                stats.append({"id": src["id"], "ok": False, "domains": 0,
                              "error": str(exc)})
                continue
            parsed = clean(PARSERS[src["format"]](text))
            raw[src["id"]] = parsed
            stats.append({"id": src["id"], "ok": True, "domains": len(parsed)})
            print(f"  ok {src['id']}: {len(parsed):,}", file=sys.stderr)

    ok_sources = [s for s in stats if s["ok"]]
    if not ok_sources:
        print("FATAL: every source failed. Refusing to publish an empty list.",
              file=sys.stderr)
        return 1

    # Refuse to ship a build that silently lost most of its coverage — an empty
    # or near-empty list is worse than a stale one, because it looks like it works.
    if len(ok_sources) < max(1, len(active) // 2):
        print(f"FATAL: only {len(ok_sources)}/{len(active)} sources succeeded. "
              f"Refusing to publish a degraded list.", file=sys.stderr)
        return 1

    tier_of = {s["id"]: s.get("tier", "extended") for s in active}

    merged: set[str] = set()
    core: set[str] = set()
    for sid, s in raw.items():
        merged |= s
        if tier_of.get(sid) == "core":
            core |= s
    extra = clean(set(cfg.get("extra_block", [])))
    merged |= extra
    core |= extra
    print(f"Merged: {len(merged):,}  (core tier: {len(core):,})", file=sys.stderr)

    merged, removed = apply_never_block(
        merged,
        cfg.get("never_block_suffix", []),
        cfg.get("never_block_apex", []),
    )
    if removed:
        print(f"Safety rail removed {len(removed)}: {removed[:10]}", file=sys.stderr)

    core, _ = apply_never_block(
        core,
        cfg.get("never_block_suffix", []),
        cfg.get("never_block_apex", []),
    )

    collapsed = collapse_subdomains(merged)
    print(f"After subdomain collapse: {len(collapsed):,} "
          f"(-{len(merged) - len(collapsed):,})", file=sys.stderr)

    domains = sorted(collapsed)
    core_domains = sorted(collapse_subdomains(core))
    print(f"Core tier after collapse: {len(core_domains):,}", file=sys.stderr)

    # Version: monotonic, never reused.
    prev_manifest = out / "manifest.json"
    if args.version is not None:
        version = args.version
    elif prev_manifest.exists():
        try:
            version = int(json.loads(prev_manifest.read_text())["version"]) + 1
        except Exception:                                   # noqa: BLE001
            version = 1
    else:
        version = 1

    built_at = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()

    # --- artifacts ---------------------------------------------------------
    header = (f"# Hisn blocklist  version={version}  built={built_at}  "
              f"count={len(domains)}\n"
              f"# Do not edit by hand. Generated by blocklist/build.py\n")

    write_domains(out / "domains.txt", domains, header)
    write_domains(out / "domains_core.txt", core_domains, header)

    # Newline-free packed form for the network extension: fast to mmap + split.
    (out / "domains.packed").write_text("\n".join(domains), encoding="utf-8")

    # The browser extension gets the CORE tier only. Chrome guarantees just
    # 30,000 static rules across all enabled rulesets, and a 19MB ruleset is
    # a real startup cost on every browser launch. The extended long tail is
    # mostly parked/dead domains that the socket-level filter covers anyway,
    # so spending the browser's rule budget on it buys almost nothing.
    n_rules = write_dnr_rules(out / "dnr_block_rules.json", core_domains,
                              "block", args.dnr_limit)

    # Bloom-ish prefix index so the client can reject the common case cheaply.
    # (16-bit buckets of the first 2 bytes of sha256 — tiny, and cuts the
    # per-request hash-set probe for domains that obviously are not listed.)
    index = bytearray(8192)
    for d in domains:
        h = hashlib.sha256(d.encode()).digest()
        bit = ((h[0] << 8) | h[1]) & 0xFFFF
        index[bit >> 3] |= 1 << (bit & 7)
    (out / "domains.index").write_bytes(bytes(index))

    artifacts = {}
    for name in ("domains.txt", "domains_core.txt", "domains.packed",
                 "dnr_block_rules.json", "domains.index"):
        p = out / name
        artifacts[name] = {"sha256": sha256_file(p), "bytes": p.stat().st_size}

    manifest = {
        "schema": 1,
        "version": version,
        "built_at": built_at,
        "domain_count": len(domains),
        "core_domain_count": len(core_domains),
        "dnr_rule_count": n_rules,
        "sources": sorted(stats, key=lambda s: s["id"]),
        "never_block_removed": len(removed),
        "artifacts": artifacts,
    }
    manifest_bytes = json.dumps(manifest, indent=2, sort_keys=True).encode()
    (out / "manifest.json").write_bytes(manifest_bytes)

    if args.sign_key:
        sig = sign_manifest(manifest_bytes, Path(args.sign_key))
        (out / "manifest.json.sig").write_text(sig + "\n", encoding="utf-8")
        print(f"Signed manifest ({len(sig)//2} bytes)", file=sys.stderr)
    else:
        print("WARNING: unsigned build (no --sign-key).", file=sys.stderr)

    print(f"\nOK  version={version}  domains={len(domains):,}  "
          f"dnr_rules={n_rules:,}  out={out}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
