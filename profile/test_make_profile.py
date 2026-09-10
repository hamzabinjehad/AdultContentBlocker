#!/usr/bin/env python3
"""
Tests for the hardening profile generator.

    python3 -m pytest test_make_profile.py -q
    python3 test_make_profile.py

Same conventions as the other suites here: stdlib unittest, no dependency, and
every docstring says WHAT BUG IT PREVENTS.

This file exists because the generator had no tests and shipped a profile that
would have quietly broken the product it is meant to protect — see
`TestExtensionPolicy`. A profile is the hardest artefact in this repo to debug
after the fact: it is installed once, by hand, into System Settings, and when
it is wrong the symptom is something *else* failing silently somewhere else.
"""

from __future__ import annotations

import argparse
import plistlib
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import make_profile  # noqa: E402


def build(**overrides) -> dict:
    """A profile dict, with the CLI's own defaults unless overridden."""
    args = argparse.Namespace(
        doh="https://family.cloudflare-dns.com/dns-query",
        dns_addresses=["1.1.1.3", "1.0.0.3"],
        supervised=False,
        extension_id="",
        update_url="https://clients2.google.com/service/update2/crx",
        display_name="Test",
        lock_settings=False,
        allow_devtools=False,
        allow_incognito=False,
        removal_password="test-password",
    )
    for key, value in overrides.items():
        setattr(args, key, value)
    profile, _ = make_profile.build(args)
    return profile


def payload(profile: dict, ptype: str) -> dict | None:
    for p in profile.get("PayloadContent", []):
        if p.get("PayloadType") == ptype:
            return p
    return None


class TestExtensionPolicy(unittest.TestCase):
    """
    THE test in this file.

    Passing `--extension-id` emits an ExtensionSettings block with
    `"*": {"installation_mode": "blocked"}` — deny every extension except the
    named one — and pins that one to `force_installed` from the Chrome Web
    Store. Both halves are right for a shipped product and together they are
    catastrophic before one exists: the extension is not published, so the
    force-install fails forever, and the blanket block stops the unpacked copy
    from being loaded at all. Installing that profile makes the browser layer
    permanently absent, and nothing says so — Chrome reports a failed policy
    install somewhere nobody looks.
    """

    def test_no_extension_id_means_no_extension_policy(self):
        chrome = payload(build(), "com.google.Chrome")
        self.assertIsNotNone(chrome)
        self.assertNotIn("ExtensionSettings", chrome,
                         "without an id there must be no extension policy, or "
                         "loading an unpacked build becomes impossible")
        self.assertNotIn("ExtensionInstallForcelist", chrome)

    def test_extension_id_blocks_everything_else(self):
        chrome = payload(build(extension_id="abc"), "com.google.Chrome")
        settings = chrome["ExtensionSettings"]
        self.assertEqual(settings["*"]["installation_mode"], "blocked")
        self.assertEqual(settings["abc"]["installation_mode"], "force_installed")

    def test_forced_install_points_at_a_store_url(self):
        """
        The trap, asserted so it cannot be forgotten.

        A force-install entry is only satisfiable from a URL that actually
        serves the extension. Until it is published, this profile and a locally
        loaded extension cannot both be true, and the profile wins.
        """
        chrome = payload(build(extension_id="abc"), "com.google.Chrome")
        forced = chrome["ExtensionInstallForcelist"][0]
        self.assertTrue(forced.endswith("clients2.google.com/service/update2/crx"),
                        "if this stops being the web store, revisit whether an "
                        "unpublished extension can still be force-installed")


class TestDevTools(unittest.TestCase):
    """
    DevTools are locked by default and unlockable by flag.

    Locked is right for someone under a lock: the console can edit extension
    state and request headers, which is a one-line bypass. Unlockable matters
    because the person building the extension needs the console on their own
    machine, and a profile that makes the project undebuggable is one they do
    not install — which leaves every other control in it unapplied too.
    """

    def test_locked_by_default(self):
        profile = build()
        self.assertEqual(
            payload(profile, "com.google.Chrome")["DeveloperToolsAvailability"], 2)
        self.assertEqual(
            payload(profile, "com.microsoft.Edge")["DeveloperToolsAvailability"], 2)
        self.assertTrue(
            payload(profile, "org.mozilla.firefox")["DisableDeveloperTools"])

    def test_allow_devtools_removes_the_lock_everywhere(self):
        profile = build(allow_devtools=True)
        self.assertNotIn("DeveloperToolsAvailability",
                         payload(profile, "com.google.Chrome"))
        self.assertNotIn("DeveloperToolsAvailability",
                         payload(profile, "com.microsoft.Edge"))
        self.assertFalse(
            payload(profile, "org.mozilla.firefox")["DisableDeveloperTools"])

    def test_allow_devtools_does_not_loosen_anything_else(self):
        """The flag is about the console, not about DNS. A profile that quietly
        stopped closing DoH would defeat the reason it is installed."""
        for profile in (build(), build(allow_devtools=True)):
            chrome = payload(profile, "com.google.Chrome")
            self.assertEqual(chrome["DnsOverHttpsMode"], "off")
            self.assertFalse(chrome["BuiltInDnsClientEnabled"])
            firefox = payload(profile, "org.mozilla.firefox")
            self.assertFalse(firefox["DNSOverHTTPS"]["Enabled"])
            self.assertTrue(firefox["DNSOverHTTPS"]["Locked"])


class TestNetworkHardening(unittest.TestCase):
    """The payloads that close the bypasses a blocklist cannot reach."""

    def test_dns_is_forced_system_wide(self):
        dns = payload(build(), "com.apple.dnsSettings.managed")
        self.assertEqual(dns["DNSSettings"]["DNSProtocol"], "HTTPS")
        self.assertIn("1.1.1.3", dns["DNSSettings"]["ServerAddresses"])

    def test_private_relay_is_disabled(self):
        """iCloud Private Relay tunnels Safari's DNS past everything here."""
        access = payload(build(), "com.apple.applicationaccess")
        self.assertFalse(access["allowCloudPrivateRelay"])

    def test_browser_proxy_is_pinned_to_system(self):
        """A per-browser proxy is a filter bypass that needs no admin rights."""
        chrome = payload(build(), "com.google.Chrome")
        self.assertEqual(chrome["ProxySettings"]["ProxyMode"], "system")
        firefox = payload(build(), "org.mozilla.firefox")
        self.assertTrue(firefox["Proxy"]["Locked"])
        self.assertTrue(firefox["BlockAboutConfig"],
                        "about:config is where a Firefox user re-enables DoH")


class TestChromiumFamily(unittest.TestCase):
    """
    Naming only Chrome and Edge left the obvious escape open.

    Every Chromium fork reads the same policy keys under its own bundle id, and
    every one of them ships a built-in DNS-over-HTTPS client. Install Brave,
    switch that on, and the hosts file and the managed resolver both stop being
    consulted — the browser resolves names itself. Locking Chrome while leaving
    Brave open is not a partial defence, it is a signpost to the way out.
    """

    # Written out rather than read from CHROMIUM_FAMILY on purpose. A test that
    # iterates the list it is checking cannot notice the list getting shorter —
    # deleting Brave from the source would delete it from the expectations too,
    # and the suite would go green while the hole reopened. Verified by doing
    # exactly that: with the list-driven version the mutation passed.
    MUST_COVER = [
        "com.google.Chrome", "com.microsoft.Edge", "org.mozilla.firefox",
        "com.brave.Browser", "com.vivaldi.Vivaldi", "com.operasoftware.Opera",
        "company.thebrowser.Browser", "org.chromium.Chromium",
    ]

    def test_every_known_fork_has_its_dns_locked(self):
        types = {p["PayloadType"] for p in build()["PayloadContent"]}
        for bundle_id in self.MUST_COVER:
            self.assertIn(bundle_id, types, f"{bundle_id} is unprotected")

    def test_forks_lock_dns_and_proxy(self):
        profile = build()
        for bundle_id, _, name in make_profile.CHROMIUM_FAMILY:
            p = payload(profile, bundle_id)
            self.assertEqual(p["DnsOverHttpsMode"], "off", name)
            self.assertFalse(p["BuiltInDnsClientEnabled"], name)
            self.assertEqual(p["ProxySettings"]["ProxyMode"], "system", name)

    def test_forks_force_install_when_an_id_is_given(self):
        """
        The extension must be non-removable on the forks the user actually runs,
        Helium above all — not just on Chrome. Without this it was removable on
        Helium, defeating the profile's purpose. Verified end-to-end that Helium
        keeps Chromium's policy engine (the keys are compiled into its
        framework), so this policy is honoured there once delivered.
        """
        profile = build(extension_id="abc")
        for bundle_id, _, name in make_profile.CHROMIUM_FAMILY:
            p = payload(profile, bundle_id)
            self.assertEqual(p["ExtensionSettings"]["abc"]["installation_mode"],
                             "force_installed", name)
            self.assertEqual(p["ExtensionSettings"]["*"]["installation_mode"],
                             "blocked", name)

    def test_no_extension_policy_on_the_forks_without_an_id(self):
        """No id, nothing to force-install — and `"*": blocked` without it would
        stop the unpacked dev copy loading. Same guard as Chrome's."""
        profile = build()
        for bundle_id, _, name in make_profile.CHROMIUM_FAMILY:
            self.assertNotIn("ExtensionSettings", payload(profile, bundle_id), name)

    def test_devtools_flag_reaches_the_forks_too(self):
        locked, open_ = build(), build(allow_devtools=True)
        for bundle_id, _, name in make_profile.CHROMIUM_FAMILY:
            self.assertEqual(
                payload(locked, bundle_id)["DeveloperToolsAvailability"], 2, name)
            self.assertNotIn("DeveloperToolsAvailability",
                             payload(open_, bundle_id), name)


class TestRemoval(unittest.TestCase):

    def test_removal_needs_the_password(self):
        profile = build()
        self.assertTrue(profile["PayloadRemovalDisallowed"])
        self.assertEqual(
            payload(profile, "com.apple.profileRemovalPassword")["RemovalPassword"],
            "test-password")

    def test_generated_passwords_are_not_guessable(self):
        a, b = make_profile.gen_password(), make_profile.gen_password()
        self.assertNotEqual(a, b)
        self.assertGreaterEqual(len(a), 20)


class TestSerialisation(unittest.TestCase):

    def test_profile_is_a_valid_plist(self):
        """macOS rejects a malformed profile with an error naming nothing."""
        data = plistlib.dumps(build())
        self.assertEqual(plistlib.loads(data)["PayloadType"],
                         "Configuration")

    def test_every_payload_is_identified(self):
        """A payload without a UUID and identifier cannot be updated or removed
        individually, and macOS silently drops some of them."""
        profile = build(extension_id="abc")
        for p in profile["PayloadContent"]:
            self.assertTrue(p.get("PayloadUUID"), p.get("PayloadType"))
            self.assertTrue(p.get("PayloadIdentifier"), p.get("PayloadType"))
            self.assertTrue(p.get("PayloadType"))


if __name__ == "__main__":
    unittest.main(verbosity=2)


class TestIncognito(unittest.TestCase):
    """
    Blocking private/incognito windows is a first-class, flag-controlled feature.

    It is the ONLY thing that closes the incognito hole for the extension: the
    extension is `spanning`, so it runs in a private window only if the user
    enabled it there, which a determined person will not. Removing the window is
    the enforceable answer. Default on; `--allow-incognito` turns it off for the
    person who instead wants incognito to exist and be covered by the extension.
    """

    def test_incognito_blocked_by_default_everywhere(self):
        profile = build()
        for bundle_id in ("com.google.Chrome", "com.microsoft.Edge",
                          "net.imput.helium", "com.brave.Browser"):
            self.assertEqual(payload(profile, bundle_id)["IncognitoModeAvailability"],
                             1, f"{bundle_id} still allows incognito")
        # Firefox's equivalent.
        self.assertTrue(payload(profile, "org.mozilla.firefox")["DisablePrivateBrowsing"])

    def test_allow_incognito_leaves_private_browsing_available(self):
        profile = build(allow_incognito=True)
        for bundle_id in ("com.google.Chrome", "com.microsoft.Edge",
                          "net.imput.helium"):
            self.assertNotIn("IncognitoModeAvailability", payload(profile, bundle_id))
        self.assertFalse(payload(profile, "org.mozilla.firefox")["DisablePrivateBrowsing"])

    def test_allow_incognito_does_not_loosen_dns(self):
        """The flag is about private windows, not the network locks around them."""
        chrome = payload(build(allow_incognito=True), "com.google.Chrome")
        self.assertEqual(chrome["DnsOverHttpsMode"], "off")
        self.assertFalse(chrome["BuiltInDnsClientEnabled"])

    def test_helium_is_covered(self):
        """The user's actual browser. A Chromium fork reads the same policy keys,
        so leaving it out is a signposted way around every DNS lock."""
        ids = [b for b, _, _ in make_profile.CHROMIUM_FAMILY]
        self.assertIn("net.imput.helium", ids)
        helium = payload(build(), "net.imput.helium")
        self.assertEqual(helium["DnsOverHttpsMode"], "off")
        self.assertEqual(helium["IncognitoModeAvailability"], 1)
