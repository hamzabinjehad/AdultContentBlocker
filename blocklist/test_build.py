#!/usr/bin/env python3
"""
Tests for the blocklist pipeline.

The most valuable test here is `test_collapse_lookup_invariant`. The builder
deletes subdomains whose parent is already listed, and the clients recover them
by walking up the domain at lookup time. Those two halves live in different
languages, in different repos-worth of code, and if they ever disagree the
result is a silent hole: domains that look blocked in the list but resolve fine
on the device. So the invariant is asserted directly.

    python3 -m pytest test_build.py -q      (or just: python3 test_build.py)
"""

import json
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from build import (                                    # noqa: E402
    apply_never_block,
    clean,
    collapse_subdomains,
    parse_adblock,
    parse_hosts,
    parse_plain,
    write_dnr_rules,
)


def client_lookup(host: str, blocked: set[str]) -> bool:
    """
    Faithful port of BlocklistStore.isBlocked (Swift) / the DNR requestDomains
    semantics: a host matches if it, or any of its parent domains, is listed.
    """
    parts = host.lower().split(".")
    for i in range(len(parts) - 1):
        if ".".join(parts[i:]) in blocked:
            return True
    return False


class TestParsers(unittest.TestCase):

    def test_hosts_format(self):
        text = (
            "# comment\n"
            "0.0.0.0 bad.example\n"
            "127.0.0.1 other.example  # trailing\n"
            "0.0.0.0 a.test b.test\n"
            "203.0.113.5 notablocklistentry.example\n"   # not a sink IP
            "\n"
        )
        got = parse_hosts(text)
        self.assertEqual(got, {"bad.example", "other.example", "a.test", "b.test"})
        self.assertNotIn("notablocklistentry.example", got)

    def test_plain_format(self):
        self.assertEqual(
            parse_plain("one.example\n# note\n\nTWO.example\nthree.example.\n"),
            {"one.example", "two.example", "three.example"},
        )

    def test_adblock_format(self):
        text = (
            "! title\n"
            "||blocked.example^\n"
            "||with-options.example^$third-party\n"
            "||path.example/foo\n"
            "@@||allowed.example^\n"        # exception — must be ignored
            "/regex-rule/\n"
        )
        got = parse_adblock(text)
        self.assertEqual(got,
                         {"blocked.example", "with-options.example", "path.example"})
        self.assertNotIn("allowed.example", got)

    def test_clean_rejects_junk(self):
        # Note: .example/.test/.invalid/.local are RFC 2606 reserved names and
        # are deliberately dropped by clean(), so real-looking TLDs are used here.
        got = clean({
            "good.co", "www.stripped.co", "localhost",
            "no-dot", "bad_underscore.co", "-leading.co",
            "thing.local", "UPPER.co",
        })
        self.assertIn("good.co", got)
        self.assertIn("stripped.co", got)               # www. removed
        self.assertIn("upper.co", got)                  # lowercased
        for bad in ("localhost", "no-dot", "bad_underscore.co",
                    "-leading.co", "thing.local"):
            self.assertNotIn(bad, got)

    def test_clean_drops_reserved_tlds(self):
        """RFC 2606 names in an upstream list are noise, never real targets."""
        got = clean({"a.example", "b.test", "c.invalid", "d.local", "real.co"})
        self.assertEqual(got, {"real.co"})


class TestSafetyRail(unittest.TestCase):

    def test_suffix_tier_protects_whole_subtree(self):
        kept, removed = apply_never_block(
            {"apple.com", "ocsp.apple.com", "evil.example"},
            suffixes=["apple.com"], apexes=[])
        self.assertEqual(kept, {"evil.example"})
        self.assertIn("ocsp.apple.com", removed)

    def test_apex_tier_leaves_subdomains_blockable(self):
        """The bug this tier exists to prevent: a shared CDN apex in the suffix
        tier would silently unblock every adult site hosted on that CDN."""
        kept, removed = apply_never_block(
            {"cloudfront.net", "d1abc.cloudfront.net"},
            suffixes=[], apexes=["cloudfront.net"])
        self.assertEqual(removed, ["cloudfront.net"])
        self.assertIn("d1abc.cloudfront.net", kept)

    def test_rail_beats_upstream(self):
        """No upstream source may cause a lockout, whatever it claims."""
        poisoned = {"apple.com", "github.com", "icloud.com"}
        kept, _ = apply_never_block(
            poisoned,
            suffixes=["apple.com", "icloud.com"], apexes=["github.com"])
        self.assertEqual(kept, set())


class TestCollapse(unittest.TestCase):

    def test_children_dropped_when_parent_present(self):
        got = collapse_subdomains(
            {"example.com", "a.example.com", "b.a.example.com", "other.com"})
        self.assertEqual(got, {"example.com", "other.com"})

    def test_orphan_subdomain_survives(self):
        got = collapse_subdomains({"cdn.example.com", "other.com"})
        self.assertIn("cdn.example.com", got)

    def test_collapse_lookup_invariant(self):
        """
        THE load-bearing test.

        Everything the builder throws away must still be caught by the client's
        parent-walk. If this ever fails, the published list silently under-
        blocks and no other test in the suite would notice.
        """
        original = {
            "example.com", "a.example.com", "b.a.example.com",
            "cdn.other.com", "deep.cdn.other.com",
            "standalone.net",
            "d1abc.cloudfront.net",
        }
        collapsed = collapse_subdomains(original)

        for host in original:
            with self.subTest(host=host):
                self.assertTrue(
                    client_lookup(host, collapsed),
                    f"{host} was dropped by collapse and is not recovered by lookup",
                )

    def test_collapse_does_not_overreach(self):
        """Collapsing must not start matching things that were never listed."""
        collapsed = collapse_subdomains({"example.com"})
        self.assertFalse(client_lookup("notexample.com", collapsed))
        self.assertFalse(client_lookup("example.com.evil.net", collapsed))
        self.assertTrue(client_lookup("sub.example.com", collapsed))


class TestDNRRules(unittest.TestCase):

    def test_rules_are_wellformed_and_ids_unique(self):
        import tempfile
        domains = [f"d{i}.example" for i in range(2500)]
        with tempfile.NamedTemporaryFile(suffix=".json") as fh:
            n = write_dnr_rules(Path(fh.name), domains, "block", limit=10_000)
            rules = json.loads(Path(fh.name).read_text())

        self.assertEqual(n, len(rules))
        ids = [r["id"] for r in rules]
        self.assertEqual(len(ids), len(set(ids)), "duplicate rule ids")

        # Chrome guarantees only 30,000 static rules across enabled rulesets.
        self.assertLess(len(rules), 30_000)

        for r in rules:
            self.assertIn(r["action"]["type"], ("block", "redirect"))
            self.assertTrue(r["condition"]["requestDomains"])
            self.assertTrue(r["condition"]["resourceTypes"])

    def test_limit_is_respected(self):
        import tempfile
        domains = [f"d{i}.example" for i in range(5000)]
        with tempfile.NamedTemporaryFile(suffix=".json") as fh:
            write_dnr_rules(Path(fh.name), domains, "block", limit=1500)
            rules = json.loads(Path(fh.name).read_text())
        covered = {d for r in rules for d in r["condition"]["requestDomains"]}
        self.assertLessEqual(len(covered), 1500)


class TestRealArtifacts(unittest.TestCase):
    """Sanity checks against the actual built dist/, when it exists."""

    dist = Path(__file__).parent.parent / "dist"

    def setUp(self):
        if not (self.dist / "manifest.json").exists():
            self.skipTest("no dist/ built")
        self.manifest = json.loads((self.dist / "manifest.json").read_text())

    def test_known_domains_are_blocked(self):
        domains = set(
            line for line in (self.dist / "domains.packed").read_text().splitlines()
            if line
        )
        for host in ("pornhub.com", "www.pornhub.com", "xvideos.com"):
            with self.subTest(host=host):
                self.assertTrue(client_lookup(host, domains), f"{host} not blocked")

    def test_critical_infrastructure_is_not_blocked(self):
        domains = set(
            line for line in (self.dist / "domains.packed").read_text().splitlines()
            if line
        )
        for host in ("apple.com", "ocsp.apple.com", "github.com",
                     "raw.githubusercontent.com", "icloud.com",
                     "cloudflare-dns.com", "letsencrypt.org",
                     "cloudfront.net", "amazonaws.com"):
            with self.subTest(host=host):
                self.assertFalse(client_lookup(host, domains),
                                 f"{host} would be blocked — lockout risk")

    def test_manifest_covers_every_artifact(self):
        for name in self.manifest["artifacts"]:
            self.assertTrue((self.dist / name).exists(), f"{name} missing")


class TestClientConfig(unittest.TestCase):
    """The two clients' update configuration, pinned to each other and to the
    build.

    Both clients download from a base URL that is hard-coded in their own
    language, and the publish workflow pushes to THIS repository's `lists`
    branch. Nothing else compares the three. When they disagree, updates fail
    silently on every install — "keeping current list", forever — which is
    exactly the failure the threat model rates worse than being switched off.
    """

    repo = Path(__file__).parent.parent

    def _js_base(self):
        src = (self.repo / "extension" / "background.js").read_text(encoding="utf-8")
        m = re.search(r'const LIST_BASE = "([^"]+)"', src)
        self.assertIsNotNone(m, "LIST_BASE not found in background.js")
        return m.group(1)

    def _swift_base(self):
        src = (self.repo / "macos" / "Hisn" / "ListUpdater.swift").read_text(encoding="utf-8")
        m = re.search(r'URL\(string:\s*"([^"]+)"\)', src)
        self.assertIsNotNone(m, "base URL not found in ListUpdater.swift")
        return m.group(1)

    def test_both_clients_download_from_the_same_place(self):
        self.assertEqual(self._js_base(), self._swift_base(),
                         "the extension and the app would install different lists")

    def test_update_base_is_a_lists_branch(self):
        base = self._js_base()
        self.assertRegex(base, r"^https://raw\.githubusercontent\.com/[^/]+/[^/]+/lists$",
                         "clients must read the `lists` branch the publish workflow writes")

    def test_generation_artifacts_are_what_the_build_produces(self):
        """Every artifact a client asks for as part of a generation must be one
        build.py writes and lists in the manifest, or updates fail on
        `missing:<name>` at every install."""
        build = (self.repo / "blocklist" / "build.py").read_text(encoding="utf-8")
        produced = set(re.findall(r'"([a-z_]+\.(?:json|packed|txt|index))"', build))
        js = (self.repo / "extension" / "lib" / "generation.js").read_text(encoding="utf-8")
        js_wanted = set(re.findall(r'"([a-z_]+\.json)"', js.split("GENERATION_ARTIFACTS")[1].split("]")[0]))
        swift = (self.repo / "macos" / "Hisn" / "ListUpdater.swift").read_text(encoding="utf-8")
        swift_wanted = set(re.findall(r'"([a-z_.]+)"', swift.split("generationFiles")[1].split("]")[0]))
        for name in js_wanted | swift_wanted - {"manifest.json", "manifest.json.sig"}:
            self.assertIn(name, produced, f"{name} is requested by a client but not built")


if __name__ == "__main__":
    unittest.main(verbosity=2)
