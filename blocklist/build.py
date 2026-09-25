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
import zlib
from pathlib import Path

from terms import compile_terms, lang_counts, serialize_terms

USER_AGENT = "hisn-blocklist-builder/1.0 (+https://github.com/hamzabinjehad/AdultContentBlocker)"
FETCH_TIMEOUT = 120

# A syntactically valid DNS name we are willing to put in a blocklist.
DOMAIN_RE = re.compile(
    r"^(?=.{1,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\.(?!-)[a-z0-9-]{1,63}(?<!-))+$"
)

HOSTS_PREFIXES = ("0.0.0.0", "127.0.0.1", "::1", "::")


# --------------------------------------------------------------------------- #
# Fetching
# --------------------------------------------------------------------------- #

def fetch(url: str, member: str | None = None) -> str:
    """Fetch a source. With `member`, treat the response as a .tar.gz and
    return that one file from inside it.

    The archive case exists for the Université Toulouse Capitole blacklist,
    which is distributed only as a tarball and is by a wide margin the largest
    and most international source available — 4.6M domains against the ~1M the
    five plain-text sources produce between them. Refusing to handle an archive
    would mean leaving that on the table, and with it most of this project's
    coverage outside English.
    """
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=FETCH_TIMEOUT) as resp:
        raw = resp.read()
    if member is None:
        return raw.decode("utf-8", errors="replace")

    import io
    import tarfile
    with tarfile.open(fileobj=io.BytesIO(raw)) as tf:
        extracted = tf.extractfile(member)
        if extracted is None:
            raise ValueError(f"{member} not found in archive")
        return extracted.read().decode("utf-8", errors="replace")


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
        # `*.example.com` — the wildcard form several large lists publish
        # (oisd's `domainswild`, AdGuard exports). Every consumer of these
        # artifacts already blocks a domain AND all its subdomains, so the
        # wildcard is exactly what we mean by a bare entry; stripping it is
        # lossless. Without this the whole source is silently discarded by
        # DOMAIN_RE, which rejects the asterisk — a source that "succeeds" and
        # contributes nothing is the failure mode this pipeline fears most.
        if d.startswith("*."):
            d = d[2:]
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
    rules = dnr_rules(domains, mode, limit)
    path.write_text(serialize_dnr(rules), encoding="utf-8")
    return len(rules)


def serialize_dnr(rules: list[dict]) -> str:
    """The one byte form of a ruleset — seed.py verify compares against it."""
    return json.dumps(rules, separators=(",", ":"))


def dnr_rules(domains: list[str], mode: str, limit: int) -> list[dict]:
    """
    Emit Chrome declarativeNetRequest static rules.

    Chrome caps a static ruleset, so we bucket by rule id and rely on
    `requestDomains` — one rule can carry many domains, which is dramatically
    more efficient than one rule per domain.
    """
    rules = []
    if mode == "block":
        # Refuse, never trim. This used to cut the list to `limit` without a
        # word: a core tier that grew past it lost its tail from every browser
        # while the build, the manifest and the publish guard all looked fine.
        # A core tier that large means a source changed; a person decides
        # whether to raise --dnr-limit or narrow the tier.
        if len(domains) > limit:
            raise SystemExit(
                f"FATAL: {len(domains):,} core domains exceed the browser rule "
                f"budget (--dnr-limit {limit:,}). Nothing is dropped silently: "
                f"raise the limit or narrow the core tier.")
        chunk = 1000
        rid = 1
        for i in range(0, len(domains), chunk):
            rules.append({
                "id": rid,
                "priority": 3,          # the ladder: extension/lib/policy.js
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
                "priority": 3,          # the ladder: extension/lib/policy.js
                "action": {"type": "block"},
                "condition": {
                    "requestDomains": domains[i:i + chunk],
                    "resourceTypes": [
                        "script", "image", "media", "xmlhttprequest",
                        "stylesheet", "font", "object", "websocket", "other",
                    ],
                },
            })
    return rules


# Chrome guarantees 30,000 static rules but only ~1,000 `regexFilter` rules,
# and regex rules are the expensive kind. Staying well under the documented
# ceiling leaves room for the strict-mode and custom-block rules the extension
# adds at runtime.
MAX_REGEX_RULES = 500


def _not_followed_by(suffixes: list[str]) -> str:
    """RE2 has no lookahead. Match the end, or any continuation that does not
    spell out one of `suffixes` — built from a trie of them, so `milf` with the
    guard `ord` becomes milf(?:$|[^o]|o(?:$|[^r]|r(?:$|[^d])))."""
    trie: dict = {}
    for suf in suffixes:
        node = trie
        for ch in suf:
            node = node.setdefault(ch, {})
        node["$end"] = True

    def build(node: dict) -> str:
        chars = sorted(k for k in node if k != "$end")
        parts = ["$", "[^" + "".join(chars) + "]"]
        for ch in chars:
            child = node[ch]
            if child.get("$end"):
                continue            # the whole innocent word: no match here
            parts.append(re.escape(ch) + build(child))
        return "(?:" + "|".join(parts) + ")"

    return build(trie)


def write_keyword_rules(path: Path, terms: list[dict], never: list[str],
                        exempt: list[str], start_id: int) -> int:
    """
    Emit DNR rules that match keywords in the URL, not just the hostname.

    ── WHY THIS IS THE ONLY LAYER THAT CAN DO IT ──────────────────────────
    HTTPS encrypts the path, so the macOS socket filter sees a hostname and
    nothing else. Chrome's declarativeNetRequest sees the whole URL — host,
    path AND query string. That makes this the only place a search for an
    explicit term can be stopped, because the term lives in `?q=…` on a domain
    (google.com, bing.com, a forum) that must obviously stay reachable.
    It also catches adult material on paths of otherwise legitimate hosts,
    which is the gap the threat model's coverage table calls out for X, Reddit
    and image search.

    `substring` terms become `urlFilter`, which is a cheap literal scan.
    `token` terms become `regexFilter` with explicit boundaries, because they
    are the short, ambiguous ones — `sex` as a bare substring matches
    `essex.gov.uk`, and `sks` matches `tasks.office.com`. The boundary class is
    the same idea as the tokenizer on the Swift side: a match only counts when
    the term is delimited by something that is not a letter or digit.

    Every rule carries an `excludedRequestDomains` rail built from the
    exempt-domain list, so a keyword can never take out a reference or
    public-health site through a URL that merely quotes it.
    """
    rules: list[dict] = []
    rid = start_id
    never_set = {n.lower() for n in never}
    regex_used = 0

    for entry in terms:
        term = entry["t"]
        if term in never_set:
            continue
        # ASCII letters and digits only. A regex metacharacter would silently
        # change a term's meaning; and Chrome sees a URL serialised — punycode
        # host, everything else percent-encoded — so an Arabic-script term can
        # never match, and as a urlFilter one non-ASCII rule makes Chrome
        # reject a whole downloaded update.
        if not re.fullmatch(r"[a-z0-9]+", term):
            continue

        if entry.get("kind") == "substring":
            # Long, unambiguous terms: anywhere in the URL, so `?q=porn` is
            # caught on a search engine that must itself stay reachable. Where
            # an innocent word STARTS with the term (never_keyword.txt: milford,
            # pornic) the rule says "not followed by the rest of it" — RE2 has no
            # lookahead, so that is spelled out as a small alternation.
            guards = sorted(w[len(term):] for w in never_set
                            if w.startswith(term) and len(w) > len(term)
                            and re.fullmatch(r"[a-z0-9]+", w))
            if guards and regex_used < MAX_REGEX_RULES:
                regex_used += 1
                condition = {"regexFilter": re.escape(term) + _not_followed_by(guards),
                             "isUrlFilterCaseSensitive": False}
            else:
                condition = {"urlFilter": term}
        else:
            if regex_used >= MAX_REGEX_RULES:
                continue
            regex_used += 1
            # Short, ambiguous terms: in the HOSTNAME only, as a whole label
            # token (optionally followed by digits) — the same rule the Mac
            # filter applies. They used to match anywhere in the URL, which
            # blocked a Google search for "adult adhd", "summa cum laude",
            # /genres/young-adult, /ford/escort and every form with ?sex=female.
            # Searches are the page-text layer's and SafeSearch's job.
            # ASCII classes only: \p{L}/\p{N} compile past DNR's per-regex
            # memory limit and Chrome silently drops the rule
            # (test/browser/dnr.sh).
            condition = {"regexFilter": (r"^[a-z][a-z0-9+.-]*://(?:[^/?#]*[._-])?"
                                         + re.escape(term)
                                         + r"[0-9]*(?:[._:-][^/?#]*)?(?:[/?#]|$)"),
                         "isUrlFilterCaseSensitive": False}

        condition["resourceTypes"] = ["main_frame", "sub_frame"]
        # The rail that keeps reference and public-health sites reachable.
        # `porn` as a URL keyword otherwise blocks Wikipedia's own article on
        # pornography, every dictionary entry, and any news report that puts the
        # word in a slug — pages whose whole purpose is to discuss the subject
        # rather than serve it. Domain rules still apply to these hosts; only
        # the KEYWORD layer steps aside.
        if exempt:
            condition["excludedRequestDomains"] = exempt

        rules.append({
            "id": rid,
            "priority": 4,          # above the domain rules (policy.js ladder)
            "action": {"type": "redirect",
                       "redirect": {"extensionPath": "/blocked.html?reason=terms"}},
            "condition": condition,
        })
        rid += 1

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
        futures = {pool.submit(fetch, s["url"], s.get("member")): s
                   for s in active}
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
            if not parsed:
                # An error page, a login wall or a changed format that parses
                # to nothing is a failure, not a source that happens to be empty.
                print(f"  !! {src['id']}: FAILED (parsed to 0 domains)", file=sys.stderr)
                stats.append({"id": src["id"], "ok": False, "domains": 0,
                              "error": "parsed to 0 domains"})
                continue
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

    # The core tier is what the browser ships and the filter falls back to;
    # every source of it must be in the build, whatever the rest did.
    failed_core = sorted(s["id"] for s in stats if not s["ok"] and tier_of.get(s["id"]) == "core")
    if failed_core:
        print(f"FATAL: core source(s) failed: {', '.join(failed_core)}. "
              "Refusing to publish without them.", file=sys.stderr)
        return 1

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
        # A previous manifest that will not parse is a FAILURE, never "start
        # again at 1": every installed client holds a higher version and would
        # refuse this build, and every build after it, as a rollback.
        try:
            version = int(json.loads(prev_manifest.read_text())["version"]) + 1
        except Exception as e:                              # noqa: BLE001
            raise SystemExit(f"FATAL: previous manifest {prev_manifest} unreadable ({e}); "
                             "refusing to guess a version")
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
    # ...and the form that is actually PUBLISHED: raw DEFLATE (RFC 1951, what
    # Apple's NSData .zlib decompresses). The full list is ~128 MiB and GitHub
    # refuses any pushed file over 100 MiB, so publishing the packed file as is
    # failed on every run and no install ever received an update. ~19 MiB.
    packer = zlib.compressobj(9, zlib.DEFLATED, -15)
    (out / "domains.packed.deflate").write_bytes(
        packer.compress((out / "domains.packed").read_bytes()) + packer.flush())

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

    # The keyword layer. Compiled from blocklist/terms/ and covered by the same
    # single signature as everything else — see terms.py for why it exists and
    # what the `essex.gov.uk` problem is. `compile_terms` raises SystemExit if
    # the list is degraded or has lost a language tier, so a broken term source
    # fails the build rather than shipping a silently empty keyword layer.
    terms_payload = compile_terms(Path(__file__).parent / "terms")
    terms_payload["version"] = version
    (out / "terms.json").write_text(serialize_terms(terms_payload),
                                    encoding="utf-8")
    term_langs = lang_counts(terms_payload)
    print(f"Terms: {len(terms_payload['terms']):,} "
          f"({', '.join(f'{k}:{v}' for k, v in sorted(term_langs.items()))}), "
          f"{len(terms_payload['host_terms'])} host terms", file=sys.stderr)

    # Keyword rules go in their own artifact and their own id range, above the
    # domain rules, so the two can be reasoned about and budgeted separately.
    n_keyword_rules = write_keyword_rules(
        out / "dnr_keyword_rules.json",
        terms_payload["host_terms"],
        terms_payload["never_keyword"],
        sorted(set(terms_payload["exempt_domains"])
               | set(terms_payload.get("keyword_exempt_hosts", []))),
        start_id=n_rules + 1)
    print(f"Keyword rules: {n_keyword_rules} "
          f"(ids {n_rules + 1}–{n_rules + n_keyword_rules})", file=sys.stderr)

    artifacts = {}
    for name in ("domains.txt", "domains_core.txt", "domains.packed",
                 "domains.packed.deflate",
                 "dnr_block_rules.json", "dnr_keyword_rules.json",
                 "domains.index", "terms.json"):
        p = out / name
        artifacts[name] = {"sha256": sha256_file(p), "bytes": p.stat().st_size}

    manifest = {
        "schema": 1,
        "version": version,
        "built_at": built_at,
        "domain_count": len(domains),
        "core_domain_count": len(core_domains),
        "dnr_rule_count": n_rules,
        "dnr_keyword_rule_count": n_keyword_rules,
        "term_count": len(terms_payload["terms"]),
        "host_term_count": len(terms_payload["host_terms"]),
        "term_langs": term_langs,
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
