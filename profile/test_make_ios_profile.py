#!/usr/bin/env python3
"""
Tests for the supervised iPhone/iPad profile generator.

Every test names the failure it prevents: a profile is installed once, by hand,
and when it is wrong the symptom shows up somewhere else, silently.
"""

from __future__ import annotations

import contextlib
import io
import os
import plistlib
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))

import make_ios_profile as ios  # noqa: E402


def generate(*argv: str) -> tuple[dict, str, str]:
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "p.mobileconfig"
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            code = ios.main(["--allow-unreachable-resolver", "--out", str(out), *argv])
        assert code == 0, stderr.getvalue()
        mode = os.stat(out).st_mode & 0o777
        assert mode == 0o600, oct(mode)
        return plistlib.loads(out.read_bytes()), stdout.getvalue(), stderr.getvalue()


def by_type(profile: dict) -> dict:
    return {p["PayloadType"]: p for p in profile["PayloadContent"]}


class TestRemovalProtection(unittest.TestCase):
    def test_profile_cannot_be_removed_without_its_password(self):
        """A profile the person can simply delete protects nothing."""
        profile, _, _ = generate("--removal-password", "s3cret")
        self.assertTrue(profile["PayloadRemovalDisallowed"])
        self.assertEqual(by_type(profile)["com.apple.profileRemovalPassword"]["RemovalPassword"], "s3cret")

    def test_routes_around_the_filter_are_closed_on_supervised_devices(self):
        """Deleting apps, erasing, a second profile or a VPN each undo the setup."""
        restrictions = by_type(generate()[0])["com.apple.applicationaccess"]
        for key in ["allowAppRemoval", "allowEraseContentAndSettings",
                    "allowUIConfigurationProfileInstallation", "allowVPNCreation",
                    "allowCloudPrivateRelay", "allowMarketplaceAppInstallation",
                    "allowWebDistributionAppInstallation", "allowSafariPrivateBrowsing"]:
            self.assertIs(restrictions[key], False, key)

    def test_optional_hardening_is_absent_by_default(self):
        """Each extra can remove a recovery path; installing must be a choice."""
        restrictions = by_type(generate()[0])["com.apple.applicationaccess"]
        for key in ["allowAccountModification", "allowPasscodeModification",
                    "allowAppInstallation", "allowAppClips",
                    "allowEnablingRestrictions", "forceAutomaticDateAndTime"]:
            self.assertNotIn(key, restrictions, key)

    def test_optional_hardening_is_written_only_when_asked(self):
        restrictions = by_type(generate(
            "--deny-account-modification", "--deny-passcode-modification",
            "--deny-app-installation", "--deny-app-clips",
            "--deny-enabling-restrictions", "--force-automatic-date-time")[0])["com.apple.applicationaccess"]
        for key in ["allowAccountModification", "allowPasscodeModification",
                    "allowAppInstallation", "allowAppClips", "allowEnablingRestrictions"]:
            self.assertIs(restrictions[key], False, key)
        self.assertIs(restrictions["forceAutomaticDateAndTime"], True)
        self.assertIs(restrictions["allowAppRemoval"], False)

    def test_app_removal_can_be_left_allowed_explicitly(self):
        """iOS has no per-app removal switch; someone may need to delete other apps."""
        restrictions = by_type(generate("--allow-app-removal")[0])["com.apple.applicationaccess"]
        self.assertIs(restrictions["allowAppRemoval"], True)
        self.assertIs(restrictions["allowEraseContentAndSettings"], False)
    def test_maximum_turns_on_every_hardening_key(self):
        """One flag for the strongest supervised profile, but only as an explicit choice."""
        restrictions = by_type(generate("--maximum")[0])["com.apple.applicationaccess"]
        for key in ["allowAccountModification", "allowPasscodeModification",
                    "allowAppInstallation", "allowAppClips", "allowEnablingRestrictions"]:
            self.assertIs(restrictions[key], False, key)
        self.assertIs(restrictions["forceAutomaticDateAndTime"], True)
        self.assertIs(restrictions["allowAppRemoval"], False)

    def test_maximum_refuses_to_leave_app_removal_allowed(self):
        """A maximum profile that lets apps be deleted is not maximum."""
        with self.assertRaises(SystemExit), contextlib.redirect_stderr(io.StringIO()):
            ios.main(["--out", os.devnull, "--maximum", "--allow-app-removal"])


    def test_other_browsers_are_blocked_only_when_asked_and_safari_stays(self):
        """Blocking Safari would remove the one browser the filter covers."""
        self.assertNotIn("blockedAppBundleIDs",
                         by_type(generate()[0])["com.apple.applicationaccess"])
        ids = by_type(generate("--block-browsers")[0])["com.apple.applicationaccess"]["blockedAppBundleIDs"]
        self.assertIn("com.google.chrome.ios", ids)
        self.assertIn("org.mozilla.ios.firefox", ids)
        self.assertEqual(len(ids), len(set(ids)))
        self.assertFalse([i for i in ids if "safari" in i.lower() or i.startswith("com.apple.")])

    def test_no_removal_password_leaves_only_the_supervising_mac(self):
        """A password on the phone is a way out on the phone."""
        profile = generate("--no-removal-password")[0]
        self.assertNotIn("com.apple.profileRemovalPassword", by_type(profile))
        self.assertTrue(profile["PayloadRemovalDisallowed"])

    def test_hisn_dns_replaces_the_public_resolver(self):
        """Two DNS layers would leave it to iOS which one answers."""
        types = by_type(generate("--hisn-dns")[0])
        self.assertNotIn("com.apple.dnsSettings.managed", types)
        proxy = types["com.apple.dnsProxy.managed"]
        self.assertEqual(proxy["AppBundleIdentifier"], "app.hisn.mobile")
        self.assertEqual(proxy["ProviderBundleIdentifier"], "app.hisn.mobile.dnsproxy")
        self.assertIn("com.apple.dnsSettings.managed", by_type(generate()[0]))

    def test_safari_is_hidden_only_when_asked(self):
        """Hiding Safari breaks links from other apps; it must be a choice."""
        self.assertNotIn("allowSafari", by_type(generate()[0])["com.apple.applicationaccess"])
        self.assertIs(by_type(generate("--block-safari")[0])["com.apple.applicationaccess"]["allowSafari"], False)

    def test_short_video_addresses_join_the_persons_sites(self):
        """A short-video flag that dropped the person's own sites would unblock them."""
        self.assertNotIn("DenyListURLs", by_type(generate()[0])["com.apple.webcontent-filter"])
        urls = by_type(generate("--block-short-video", "--block-site", "example.org")[0])[
            "com.apple.webcontent-filter"]["DenyListURLs"]
        self.assertIn("https://example.org", urls)
        self.assertIn("https://www.youtube.com/shorts", urls)
        self.assertIn("https://www.tiktok.com", urls)
        self.assertNotIn("https://www.youtube.com", urls)
        self.assertEqual(len(urls), len(set(urls)))

    def test_short_video_addresses_count_toward_apples_limit(self):
        """Past the limit Apple may ignore the list; the build must refuse instead."""
        with tempfile.TemporaryDirectory() as tmp:
            sites = Path(tmp) / "sites.txt"
            sites.write_text("\n".join(f"s{i}.example.org" for i in range(ios.MAX_DENY_URLS)))
            with self.assertRaises(SystemExit), contextlib.redirect_stderr(io.StringIO()):
                ios.main(["--allow-unreachable-resolver", "--out", str(Path(tmp) / "p"),
                          "--block-short-video", "--block-sites-file", str(sites)])

    def test_dns_cannot_be_switched_off_and_uses_a_real_resolver(self):
        """A DNS setting the person can toggle off is a suggestion, not a layer."""
        dns = by_type(generate()[0])["com.apple.dnsSettings.managed"]["DNSSettings"]
        self.assertTrue(dns["ProhibitDisablement"])
        self.assertEqual(dns["ServerURL"], ios.mac.RESOLVERS[ios.mac.DEFAULT_RESOLVER]["doh"])

    def test_password_is_not_printed_unless_asked(self):
        """The person the profile protects may be watching the terminal."""
        _, stdout, _ = generate()
        self.assertEqual(stdout, "")
        _, stdout, _ = generate("--removal-password", "shown", "--print-password")
        self.assertEqual(stdout.strip(), "shown")

    def test_payload_identifiers_are_unique_and_namespaced(self):
        """Duplicate identifiers make iOS replace one payload with another."""
        profile = generate()[0]
        ids = [p["PayloadIdentifier"] for p in profile["PayloadContent"]]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertTrue(all(i.startswith(ios.ID_PREFIX + ".") for i in ids))


class TestBlockedSites(unittest.TestCase):
    def test_sites_are_canonical_unique_and_sorted(self):
        """A typo'd or duplicated entry looks blocked and is not."""
        web = by_type(generate("--block-site", "https://www.Reddit.com/r/x",
                               "--block-site", "x.com", "--block-site", "reddit.com")[0])
        self.assertEqual(web["com.apple.webcontent-filter"]["DenyListURLs"],
                         ["https://reddit.com", "https://x.com"])
        self.assertTrue(web["com.apple.webcontent-filter"]["AutoFilterEnabled"])

    def test_no_sites_means_only_apples_filter(self):
        web = by_type(generate()[0])["com.apple.webcontent-filter"]
        self.assertNotIn("DenyListURLs", web)
        self.assertEqual(web["FilterType"], "BuiltIn")

    def test_invalid_sites_fail_the_build(self):
        """A silently dropped line is a site the person believes is blocked."""
        for bad in ["not a site", "192.168.1.1", "localhost", "مثال.مصر"]:
            with self.assertRaises(SystemExit), contextlib.redirect_stderr(io.StringIO()):
                ios.main(["--allow-unreachable-resolver", "--out", os.devnull, "--block-site", bad])

    def test_sites_file_skips_comments(self):
        with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
            f.write("# my list\nexample.com\n\n  www.example.org  \n")
        try:
            web = by_type(generate("--block-sites-file", f.name)[0])["com.apple.webcontent-filter"]
        finally:
            os.unlink(f.name)
        self.assertEqual(web["DenyListURLs"], ["https://example.com", "https://example.org"])

    def test_apples_deny_list_limit_is_enforced(self):
        """Apple documents at most 500 URLs; more risks the whole filter."""
        with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
            f.write("\n".join(f"s{i}.example" for i in range(ios.MAX_DENY_URLS + 1)))
        try:
            with self.assertRaises(SystemExit), contextlib.redirect_stderr(io.StringIO()):
                ios.main(["--allow-unreachable-resolver", "--out", os.devnull,
                          "--block-sites-file", f.name])
        finally:
            os.unlink(f.name)


class TestResolverCheck(unittest.TestCase):
    def test_unresolvable_resolver_refuses_to_build(self):
        """A profile pointing at a missing resolver takes the phone offline."""
        with mock.patch.object(ios.mac, "resolver_is_reachable", return_value=False), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(ios.main(["--out", os.devnull]), 1)


if __name__ == "__main__":
    unittest.main()
