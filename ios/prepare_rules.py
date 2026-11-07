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

# Six parts of the proven 40,000-rule size, not four. Adding parts keeps each
# blocker at the size Hisn already ships and tests; raising the per-blocker
# limit would be the unmeasured change. The bundled core is capped to
# PARTS * RULE_LIMIT by build.py and script/coverage.py.
PARTS = 6
RULE_LIMIT = 40_000

# Sponsored adult TLDs. A domain registered this morning is on no list yet, but
# every name under one of these registries belongs to the category the list
# exists to block. `.cam` is deliberately excluded: it is generic and has
# ordinary camera/webcam uses. The extension carries the same set in
# `extension/lib/policy.js` and `rules/adult_tlds.json`; the Mac carries it in
# AdultTldGuard.swift; Android in AdultTldPolicy.java. They are policy
# constants, not list content, so they work when a list cannot be read.
ADULT_TLDS = ("adult", "porn", "sex", "xxx")


def rule(domain: str) -> dict:
    if not valid_domain(domain):
        raise Rejected("noncanonical Safari domain")
    # Match the destination host, not the top-page host (if-domain). Label and
    # authority boundaries prevent blocking innocent domain suffix collisions.
    # Safari's regex subset has no alternation. Serialized HTTP(S) request URLs
    # have an authority delimiter even when the user enters a bare hostname.
    pattern = r"^https?://([^/?#]*@)?([a-z0-9-]+\.)*" + re.escape(domain) + r"\.?[:/?#]"
    return {"trigger": {"url-filter": pattern}, "action": {"type": "block"}}


def adult_tld_rule(tld: str) -> dict:
    """One rule per sponsored adult TLD; Safari's subset has no alternation.

    `([a-z0-9-]+\.)*` matches subdomains and the escaped TLD is the FINAL
    label. The trailing `\.?` accepts an absolute FQDN, and `[:/?#]` is the
    authority delimiter every serialized HTTP(S) URL has. `notxxx.com`,
    `porn.example.com` and `xxx.evil.com` therefore do not match: the guarded
    label must be the host's last one.
    """
    if tld not in ADULT_TLDS:
        raise Rejected("unknown adult TLD")
    pattern = r"^https?://([^/?#]*@)?([a-z0-9-]+\.)*" + re.escape(tld) + r"\.?[:/?#]"
    return {"trigger": {"url-filter": pattern}, "action": {"type": "block"}}


def guard_rules() -> list[dict]:
    return [adult_tld_rule(tld) for tld in ADULT_TLDS]


def signed_bundle(bundle: Path, public_key: Path) -> tuple[dict, int, list[str], str | None]:
    """The signature-verified manifest and the core domain tier it describes.

    Everything downstream reads the returned values, never the files again, so
    one verification covers the Safari shards and the DNS filter's data.
    """
    key, _ = load_key(public_key)
    raw = read_small(bundle / "manifest.json")
    verify_signature(raw, read_small(bundle / "manifest.json.sig", 256), key)
    manifest, version, entry, count = validate_manifest(raw, "domains_core.txt")
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
    if count + len(guard_rules()) > PARTS * RULE_LIMIT:
        raise Rejected("core list plus guard rules exceeds Safari shard capacity; "
                       "add explicit targets, never truncate")
    built = manifest.get("built_at")
    if not isinstance(built, str) or not built:
        built = None
    return manifest, version, domains, built


def verified_domains(bundle: Path, public_key: Path) -> tuple[list[str], int, str | None]:
    _, version, domains, built = signed_bundle(bundle, public_key)
    return domains, version, built


# The signed seed carries one terms list for every client; the DNS filter needs
# only its hostname subset, not the text terms, so it neither ships nor parses
# 5,831 page-classification rules.
MAX_HOST_TERMS = 1_000
MAX_NEVER_KEYWORD = 5_000
MAX_EXEMPT_HOSTS = 200


def host_term_payload(bundle: Path, manifest: dict) -> dict:
    """Verify terms.json against the signed manifest and return its host subset.

    The manifest is already signature-verified by `signed_bundle`; pinning the
    file's bytes and digest to that manifest gives the terms exactly the same
    evidence as the domain list. Invalid data is rejected, never partially used.
    """
    entry = manifest["artifacts"].get("terms.json")
    if not isinstance(entry, dict):
        raise Rejected("signed manifest does not include terms.json")
    raw = read_small(bundle / "terms.json", 2 << 20)
    if len(raw) != entry["bytes"] or hashlib.sha256(raw).hexdigest() != entry["sha256"]:
        raise Rejected("signed terms artifact mismatch")
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeError, ValueError) as error:
        raise Rejected("terms.json is not valid UTF-8 JSON") from error
    if data.get("schema") != 1:
        raise Rejected("unsupported terms schema")
    version = data.get("version")
    if type(version) is not int or version < 1:
        raise Rejected("invalid terms version")
    host_terms = data.get("host_terms")
    if not isinstance(host_terms, list) or not 1 <= len(host_terms) <= MAX_HOST_TERMS:
        raise Rejected("invalid host term count")
    terms = []
    for item in host_terms:
        if not isinstance(item, dict):
            raise Rejected("invalid host term")
        term, kind = item.get("t"), item.get("kind")
        if not isinstance(term, str) or not bounded_term(term) or kind not in ("token", "substring"):
            raise Rejected("invalid host term")
        terms.append({"t": term, "kind": kind})
    never = string_list(data.get("never_keyword"), MAX_NEVER_KEYWORD, "never_keyword")
    exempt = string_list(data.get("keyword_exempt_hosts", []), MAX_EXEMPT_HOSTS,
                         "keyword_exempt_hosts")
    return {"schema": 1, "version": version, "host_terms": terms,
            "never_keyword": never, "keyword_exempt_hosts": exempt}


def bounded_term(value: str) -> bool:
    return 1 <= len(value) <= 253 and "\n" not in value and "\t" not in value


def string_list(value: object, limit: int, label: str) -> list[str]:
    if not isinstance(value, list) or len(value) > limit:
        raise Rejected(f"invalid {label}")
    for item in value:
        if not isinstance(item, str) or not bounded_term(item):
            raise Rejected(f"invalid {label}")
    return sorted(set(value))


def compile_part(domains: list[str], part: int) -> bytes:
    if not 1 <= part <= PARTS:
        raise Rejected("invalid Safari part")
    selected = domains[(part - 1) * RULE_LIMIT:part * RULE_LIMIT]
    if not selected:
        raise Rejected("empty Safari part")
    rules = [rule(domain) for domain in selected]
    # The guard lives in the final part because that is the one with spare
    # slots. Losing that part loses the guard as well as its domain slice, and
    # the app's per-part verification reports it.
    if part == PARTS:
        rules.extend(guard_rules())
    if len(rules) > RULE_LIMIT:
        raise Rejected("Safari part exceeds the 40,000-rule limit")
    return json.dumps(rules, separators=(",", ":")).encode()


def dns_assets(bundle: Path, public_key: Path,
               output: Path) -> dict:
    """Write the DNS filter's build-verified domain and hostname-term files.

    The domain file is the same signature-verified core tier the Safari shards
    carry, one domain per line in sorted order. `dns-metadata.json` pins both
    files' bytes and SHA-256; the extension verifies them again at runtime and
    refuses to start on a mismatch. Nothing here downloads or changes the seed.
    """
    manifest, version, domains, built = signed_bundle(bundle, public_key)
    terms = host_term_payload(bundle, manifest)
    domains_raw = ("\n".join(domains) + "\n").encode("ascii")
    terms_raw = json.dumps(terms, sort_keys=True, separators=(",", ":")).encode("utf-8")
    metadata = {
        "schema": 1,
        "version": version,
        "count": len(domains),
        "built": built,
        "domains_sha256": hashlib.sha256(domains_raw).hexdigest(),
        "domains_bytes": len(domains_raw),
        "terms_sha256": hashlib.sha256(terms_raw).hexdigest(),
        "terms_bytes": len(terms_raw),
        "terms_version": terms["version"],
        "adult_tlds": list(ADULT_TLDS),
    }
    if metadata["count"] > 1_000_000:
        raise Rejected("DNS domain list exceeds the extension's index limit")
    output.mkdir(parents=True, exist_ok=True)
    (output / "dns-domains.txt").write_bytes(domains_raw)
    (output / "dns-host-terms.json").write_bytes(terms_raw)
    (output / "dns-metadata.json").write_text(json.dumps(metadata, sort_keys=True))
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--part", type=int, default=0)
    parser.add_argument("--dns", action="store_true",
                        help="Write the DNS filter assets instead of Safari rules")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.dns:
        if args.part:
            raise SystemExit("--dns writes the DNS filter assets; it does not take --part")
        dns_assets(ROOT / "seed", ROOT / "blocklist/public_key.hex", args.output)
        return
    domains, version, built = verified_domains(ROOT / "seed", ROOT / "blocklist/public_key.hex")
    args.output.mkdir(parents=True, exist_ok=True)
    if args.part:
        raw = compile_part(domains, args.part)
        (args.output / "rules.json").write_bytes(raw)
        count = min(RULE_LIMIT, len(domains) - (args.part - 1) * RULE_LIMIT)
        if args.part == PARTS:
            count += len(guard_rules())
        metadata = {"version": version, "count": count,
                    "sha256": hashlib.sha256(raw).hexdigest(), "built": built}
    else:
        metadata = {"version": version, "count": len(domains) + len(guard_rules()),
                    "parts": PARTS, "built": built}
    (args.output / "rules-metadata.json").write_text(json.dumps(metadata, sort_keys=True))


if __name__ == "__main__":
    main()
