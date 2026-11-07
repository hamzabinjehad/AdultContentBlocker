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
import contextlib
import io
import os
import plistlib
import re
import subprocess
import tempfile
import sys
import unittest
from unittest import mock
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import make_profile  # noqa: E402

EXTENSION_ID = "a" * 32


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
        browsers=None,
        mdm=False,
        managed=False,
        organization_name="Your organization",
        team_id="",
        filter_requirement="",
        without_managed_filter=False,
        lock_system_extensions=False,
        allow_erase=False,
        no_safari_private_lock=False,
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
        chrome = payload(build(extension_id=EXTENSION_ID), "com.google.Chrome")
        settings = chrome["ExtensionSettings"]
        self.assertEqual(settings["*"]["installation_mode"], "blocked")
        self.assertEqual(settings[EXTENSION_ID]["installation_mode"], "force_installed")

    def test_forced_install_points_at_a_store_url(self):
        """
        The trap, asserted so it cannot be forgotten.

        A force-install entry is only satisfiable from a URL that actually
        serves the extension. Until it is published, this profile and a locally
        loaded extension cannot both be true, and the profile wins.
        """
        chrome = payload(build(extension_id=EXTENSION_ID), "com.google.Chrome")
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


class TestBrowserEscapes(unittest.TestCase):
    """The ways out of the extension that live inside the browser itself."""

    CHROMIUM = ["com.google.Chrome", "com.microsoft.Edge", "net.imput.helium",
                "com.brave.Browser", "org.chromium.Chromium"]

    def test_no_guest_windows_or_new_profiles(self):
        profile = build()
        for bundle_id in self.CHROMIUM:
            p = payload(profile, bundle_id)
            self.assertFalse(p["BrowserGuestModeEnabled"], bundle_id)
            self.assertFalse(p["BrowserAddPersonEnabled"], bundle_id)

    def test_safesearch_is_forced_by_policy(self):
        profile = build()
        for bundle_id in self.CHROMIUM:
            p = payload(profile, bundle_id)
            self.assertTrue(p["ForceGoogleSafeSearch"], bundle_id)
            self.assertEqual(p["ForceYouTubeRestrict"], 2, bundle_id)
        self.assertEqual(payload(profile, "com.google.Chrome")["SafeSitesFilterBehavior"], 1)
        self.assertEqual(payload(profile, "com.microsoft.Edge")["ForceBingSafeSearch"], 2)
        self.assertNotIn("SafeSitesFilterBehavior", payload(profile, "net.imput.helium"),
                         "a Google-service policy has no place in a de-Googled fork")

    def test_only_the_admin_installed_browser_link_is_used(self):
        """A user-level native-messaging manifest can point at any script,
        which then speaks for the app — "no lock, allow everything"."""
        profile = build()
        for bundle_id in self.CHROMIUM:
            self.assertIs(payload(profile, bundle_id)["NativeMessagingUserLevelHosts"], False,
                          bundle_id)

    def test_allow_flags_do_not_reopen_these(self):
        profile = build(allow_devtools=True, allow_incognito=True)
        p = payload(profile, "net.imput.helium")
        self.assertFalse(p["BrowserGuestModeEnabled"])
        self.assertTrue(p["ForceGoogleSafeSearch"])


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
        Candidate forks need the same requested managed installation keys.
        This serialization test does not prove acceptance of those keys or an
        update source in Helium (or any other running browser).
        """
        profile = build(extension_id=EXTENSION_ID)
        for bundle_id, _, name in make_profile.CHROMIUM_FAMILY:
            p = payload(profile, bundle_id)
            self.assertEqual(p["ExtensionSettings"][EXTENSION_ID]["installation_mode"],
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


class TestFirefox(unittest.TestCase):

    def test_policies_are_switched_on(self):
        """Firefox on macOS ignores every other key in its domain unless this
        one is set — the payload was delivered and did nothing."""
        firefox = payload(build(), "org.mozilla.firefox")
        self.assertIs(firefox["EnterprisePoliciesEnabled"], True)
        self.assertEqual(firefox["DNSOverHTTPS"], {"Enabled": False, "Locked": True})


class TestPasswordOnDisk(unittest.TestCase):
    """The .mobileconfig holds the removal password in plain text."""

    def test_written_for_its_owner_only(self):
        with tempfile.TemporaryDirectory() as d:
            out = Path(d) / "sub" / "p.mobileconfig"
            make_profile.write_private(out, b"secret")
            self.assertEqual(out.read_bytes(), b"secret")
            self.assertEqual(out.stat().st_mode & 0o777, 0o600)

    def test_an_existing_readable_file_is_tightened(self):
        with tempfile.TemporaryDirectory() as d:
            out = Path(d) / "p.mobileconfig"
            out.write_bytes(b"old")
            os.chmod(out, 0o644)
            make_profile.write_private(out, b"new")
            self.assertEqual(out.stat().st_mode & 0o777, 0o600)
            self.assertEqual(out.read_bytes(), b"new")


class TestSerialisation(unittest.TestCase):

    def test_profile_is_a_valid_plist(self):
        """macOS rejects a malformed profile with an error naming nothing."""
        data = plistlib.dumps(build())
        self.assertEqual(plistlib.loads(data)["PayloadType"],
                         "Configuration")

    def test_every_payload_is_identified(self):
        """A payload without a UUID and identifier cannot be updated or removed
        individually, and macOS silently drops some of them."""
        profile = build(extension_id=EXTENSION_ID)
        for p in profile["PayloadContent"]:
            self.assertTrue(p.get("PayloadUUID"), p.get("PayloadType"))
            self.assertTrue(p.get("PayloadIdentifier"), p.get("PayloadType"))
            self.assertTrue(p.get("PayloadType"))


class TestIndependentLayers(unittest.TestCase):
    def args(self, **overrides):
        args = argparse.Namespace(doh="https://family.cloudflare-dns.com/dns-query",
                                  dns_addresses=["1.1.1.3", "1.0.0.3"], supervised=False,
                                  extension_id="", update_url="https://clients2.google.com/service/update2/crx",
                                  display_name="Test", lock_settings=False, allow_devtools=False,
                                  allow_incognito=False, removal_password=None, browsers=None,
                                  mdm=False, managed=False, organization_name="Your organization")
        for key, value in overrides.items():
            setattr(args, key, value)
        return args

    def test_network_and_browser_payloads_are_separate_and_app_independent(self):
        layers = make_profile.build_layers(self.args(extension_id="a" * 32))
        self.assertEqual([slug for slug, _, _ in layers], ["network", "browsers"])
        network, browsers = layers[0][1], layers[1][1]
        self.assertIsNotNone(payload(network, "com.apple.dnsSettings.managed"))
        self.assertIsNone(payload(browsers, "com.apple.dnsSettings.managed"))
        self.assertIsNone(payload(network, "com.google.Chrome"))
        self.assertEqual(payload(browsers, "com.google.Chrome")["ExtensionSettings"]["a" * 32]["installation_mode"], "force_installed")
        for _, layer, password in layers:
            self.assertEqual(payload(layer, "com.apple.profileRemovalPassword")["RemovalPassword"], password)
            encoded = plistlib.dumps(layer)
            self.assertNotIn(b"/Applications/Hisn.app", encoded)
            self.assertNotIn(b"HisnBridge", encoded)
            self.assertEqual(plistlib.loads(encoded), layer)

    def test_each_layer_has_a_distinct_identifier_uuid_and_generated_password(self):
        layers = make_profile.build_layers(self.args())
        self.assertNotEqual(layers[0][2], layers[1][2])
        identifiers, uuids = [], []
        for slug, layer, _ in layers:
            self.assertEqual(layer["PayloadIdentifier"], f"app.hisn.profile.{slug}")
            identifiers.append(layer["PayloadIdentifier"])
            uuids.append(layer["PayloadUUID"])
            for p in layer["PayloadContent"]:
                self.assertTrue(p["PayloadIdentifier"].startswith(f"app.hisn.profile.{slug}."))
                identifiers.append(p["PayloadIdentifier"])
                uuids.append(p["PayloadUUID"])
        self.assertEqual(len(identifiers), len(set(identifiers)))
        self.assertEqual(len(uuids), len(set(uuids)))

    def test_settings_restrictions_remain_opt_in_and_do_not_couple_network_layer(self):
        self.assertTrue(all(payload(layer, "com.apple.systempreferences") is None
                            for _, layer, _ in make_profile.build_layers(self.args())))
        network, browsers = [layer for _, layer, _ in make_profile.build_layers(self.args(lock_settings=True))]
        self.assertIsNone(payload(network, "com.apple.systempreferences"))
        self.assertIsNotNone(payload(browsers, "com.apple.systempreferences"))

    def test_explicit_escrow_password_is_honored_without_changing_original_profile(self):
        args = self.args(removal_password="operator-escrowed")
        self.assertTrue(all(password == "operator-escrowed" for _, _, password in make_profile.build_layers(args)))
        combined, password = make_profile.build(args)
        self.assertEqual(combined["PayloadIdentifier"], "app.hisn.profile")
        self.assertEqual(password, "operator-escrowed")

    def test_browser_selection_preserves_network_and_excludes_unselected_browser_policy(self):
        profile = build(browsers=["net.imput.helium", "com.brave.Browser"])
        types = {p["PayloadType"] for p in profile["PayloadContent"]}
        self.assertIn("com.apple.dnsSettings.managed", types)
        self.assertIn("net.imput.helium", types)
        self.assertIn("com.brave.Browser", types)
        self.assertNotIn("com.google.Chrome", types)
        self.assertNotIn("org.mozilla.firefox", types)
        with self.assertRaises(ValueError):
            build(browsers=["invented.browser"])


class TestNativeBrowserCatalog(unittest.TestCase):
    def test_native_linked_policy_domains_match_swift_catalog_not_all_candidates(self):
        source = (Path(__file__).parents[1] / "macos/Hisn/NativeMessagingInstaller.swift").read_text()
        app_ids = set(re.findall(r'bundleID:\s*"([^"\n]+)"', source))
        policy_ids = {"com.microsoft.Edge" if value == "com.microsoft.edgemac" else value for value in app_ids}
        self.assertEqual(policy_ids, make_profile.NATIVE_LINKED_BROWSER_IDS)
        self.assertTrue(policy_ids <= make_profile.BROWSER_POLICY_IDS)
        self.assertNotIn("org.chromium.Thorium", policy_ids,
                         "A policy candidate is not proof of a configured native host")

    def test_native_installer_target_inventory_is_read_only_and_covers_channel_paths(self):
        script = Path(__file__).parents[1] / "macos/install_native_host.sh"
        result = subprocess.run(["bash", str(script), "--user", "--print-targets"],
                                capture_output=True, text=True, check=True)
        paths = result.stdout.splitlines()
        self.assertEqual(len(paths), 11)
        for suffix in ("", " Beta", " Dev", " Canary"):
            self.assertIn(str(Path.home() / f"Library/Application Support/Google/Chrome{suffix}/NativeMessagingHosts"), paths)
        system = subprocess.run(["bash", str(script), "--print-targets"], capture_output=True, text=True, check=True).stdout.splitlines()
        self.assertEqual(system.count("/Library/Google/Chrome/NativeMessagingHosts"), 1,
                         "Google branded channels share a system host; do not invent channel-specific /Library paths")

    def test_read_only_verifier_and_native_installer_inventory_stay_aligned(self):
        root = Path(__file__).parents[1]
        source = (root / "macos/verify_enforcement.sh").read_text()
        catalog = re.search(r'CHROMIUM_BROWSERS="\\\n(.*?)"', source, re.DOTALL)
        self.assertIsNotNone(catalog)
        rows = [line.split("|") for line in catalog.group(1).splitlines()]
        self.assertEqual(len(rows), 11)
        self.assertEqual({domain for _, domain, _ in rows}, make_profile.NATIVE_LINKED_BROWSER_IDS)
        targets = subprocess.run(["bash", str(root / "macos/install_native_host.sh"),
                                  "--user", "--print-targets"], capture_output=True,
                                 text=True, check=True).stdout.splitlines()
        self.assertEqual(set(targets), {str(Path.home() / f"Library/Application Support/{product}/NativeMessagingHosts")
                                        for _, _, product in rows})
        function = re.search(r'nm_system_dir\(\) \{\n.*?\n\}', source, re.DOTALL).group(0)
        resolved = set()
        for name, _, product in rows:
            result = subprocess.run(["bash", "-c", function + '\nnm_system_dir "$1" "$2"',
                                     "inventory", name, product], capture_output=True,
                                    text=True, check=True)
            resolved.add(result.stdout.strip())
        system = subprocess.run(["bash", str(root / "macos/install_native_host.sh"),
                                 "--print-targets"], capture_output=True,
                                text=True, check=True).stdout.splitlines()
        self.assertEqual(resolved, set(system))

    def test_read_only_verifier_missing_profile_is_unknown_not_vacuously_on(self):
        source = (Path(__file__).parents[1] / "macos/verify_enforcement.sh").read_text()
        code = re.search(r"<<'PY'.*?\n(.*?)\nPY\n", source, re.DOTALL).group(1)
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([sys.executable, "-", directory, EXTENSION_ID],
                                    input=code, capture_output=True, text=True, check=True)
            self.assertEqual(result.stdout.strip(), "unknown")

    def test_verifier_reads_published_ids_from_installed_plist_not_swift_comments(self):
        source = (Path(__file__).parents[1] / "macos/verify_enforcement.sh").read_text()
        code = re.search(r"<<'IDS'.*?\n(.*?)\nIDS\n", source, re.DOTALL).group(1)
        with tempfile.TemporaryDirectory() as directory:
            installed, fallback = Path(directory) / "Installed.plist", Path(directory) / "Source.plist"
            installed.write_bytes(plistlib.dumps({"HisnExtensionIDs": ["b" * 32, "not-an-id", 42]}))
            fallback.write_bytes(plistlib.dumps({"HisnExtensionIDs": [EXTENSION_ID]}))
            result = subprocess.run([sys.executable, "-", str(installed), str(fallback)],
                                    input=code, capture_output=True, text=True, check=True)
            self.assertEqual(result.stdout.strip(), "b" * 32)

    def test_verifier_invalid_installed_identity_does_not_fall_back_to_source(self):
        source = (Path(__file__).parents[1] / "macos/verify_enforcement.sh").read_text()
        code = re.search(r"<<'IDS'.*?\n(.*?)\nIDS\n", source, re.DOTALL).group(1)
        with tempfile.TemporaryDirectory() as directory:
            installed, fallback = Path(directory) / "Installed.plist", Path(directory) / "Source.plist"
            installed.write_bytes(b"not-a-plist")
            fallback.write_bytes(plistlib.dumps({"HisnExtensionIDs": [EXTENSION_ID]}))
            result = subprocess.run([sys.executable, "-", str(installed), str(fallback)],
                                    input=code, capture_output=True, text=True, check=True)
            self.assertEqual(result.stdout, "")

    def test_verifier_uses_source_identity_when_no_installed_plist_exists(self):
        source = (Path(__file__).parents[1] / "macos/verify_enforcement.sh").read_text()
        code = re.search(r"<<'IDS'.*?\n(.*?)\nIDS\n", source, re.DOTALL).group(1)
        with tempfile.TemporaryDirectory() as directory:
            missing, fallback = Path(directory) / "Missing.plist", Path(directory) / "Source.plist"
            fallback.write_bytes(plistlib.dumps({"HisnExtensionIDs": [EXTENSION_ID]}))
            result = subprocess.run([sys.executable, "-", str(missing), str(fallback)],
                                    input=code, capture_output=True, text=True, check=True)
            self.assertEqual(result.stdout.strip(), EXTENSION_ID)

    def test_native_registration_and_verifier_shell_syntax(self):
        root = Path(__file__).parents[1]
        for script in ("install_native_host.sh", "verify_enforcement.sh"):
            subprocess.run(["bash", "-n", str(root / "macos" / script)], check=True,
                           capture_output=True, text=True)


class TestExtensionSourceValidation(unittest.TestCase):
    def test_valid_published_format_is_not_confused_with_publication_proof(self):
        make_profile.validate_extension_source("a" * 32, "https://clients2.google.com/service/update2/crx")
        make_profile.validate_extension_source("a" * 32, "https://[::1]:443/update")
        make_profile.validate_extension_source("", "")

    def test_invalid_ids_and_non_https_or_credential_urls_are_rejected(self):
        for value in ("abc", "q" * 32, "A" * 32, "a" * 31, "a" * 33):
            with self.assertRaises(ValueError):
                make_profile.validate_extension_source(value, "https://example.com/update")
        for value in ("file:///tmp/update.xml", "http://example.com/update",
                      "https://user:password@example.com/update", "https://example.com/update#fragment",
                      "https:///update", "https://example.com:invalid/update",
                      "https://example.com:99999/update", "https://exam ple.com/update",
                      "https://example.com/ update", "https://example.com/\nupdate",
                      "https://example.com/\x7fupdate", "https://example.com\\update",
                      "https://%zz/update", "https://-invalid.example/update",
                      "https://example..com/update"):
            with self.assertRaises(ValueError):
                make_profile.validate_extension_source("a" * 32, value)

    def test_imported_builders_reject_invalid_source_not_just_cli(self):
        for value in ("abc", "q" * 32):
            with self.assertRaises(ValueError):
                build(extension_id=value)
            with self.assertRaises(ValueError):
                make_profile.chromium_extension_policy(value, "https://example.com/update")
        with self.assertRaises(ValueError):
            build(extension_id=EXTENSION_ID, update_url="http://example.com/update")


class TestProfileCLI(unittest.TestCase):
    def run_main(self, arguments):
        stdout, stderr = io.StringIO(), io.StringIO()
        with mock.patch.object(sys, "argv", ["make_profile.py", *arguments]), \
             mock.patch.object(make_profile, "resolver_is_reachable", return_value=True), \
             contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            result = make_profile.main()
        return result, stdout.getvalue(), stderr.getvalue()

    def test_cli_writes_two_owner_readable_selected_layers_without_installing(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / "test.mobileconfig"
            result, stdout, stderr = self.run_main([
                "--out", str(out), "--separate-layers", "--browser", "net.imput.helium",
                "--extension-id", EXTENSION_ID,
            ])
            self.assertEqual(result, 0)
            self.assertEqual(stdout, "", "Recovery secrets must not leak to the normal output")
            self.assertFalse(out.exists(), "Split generation must not overwrite the old combined artifact")
            generated = sorted(Path(directory).glob("*.mobileconfig"))
            self.assertEqual([item.name for item in generated], ["test-browsers.mobileconfig", "test-network.mobileconfig"])
            self.assertIn("No profile or network filter was installed", stderr)
            for item in generated:
                self.assertEqual(item.stat().st_mode & 0o777, 0o600)
            browsers = plistlib.loads(generated[0].read_bytes())
            self.assertIsNotNone(payload(browsers, "net.imput.helium"))
            self.assertIsNone(payload(browsers, "com.google.Chrome"))

    def test_generated_passwords_can_be_explicitly_escrowed_by_label(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / "test.mobileconfig"
            result, stdout, _ = self.run_main(["--out", str(out), "--separate-layers", "--print-password"])
            self.assertEqual(result, 0)
            secrets = dict(line.split(": ", 1) for line in stdout.splitlines())
            self.assertEqual(set(secrets), {"network", "browsers"})
            self.assertNotEqual(secrets["network"], secrets["browsers"])
            for slug, secret in secrets.items():
                profile = plistlib.loads(out.with_name(f"test-{slug}.mobileconfig").read_bytes())
                self.assertEqual(payload(profile, "com.apple.profileRemovalPassword")["RemovalPassword"], secret)

    def test_cli_mdm_requires_a_named_organization(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(SystemExit) as failure:
                self.run_main(["--mdm",
                               "--out", str(Path(directory) / "mdm.mobileconfig")])
            self.assertEqual(failure.exception.code, 2)
            self.assertEqual(list(Path(directory).iterdir()), [])

    def test_cli_mdm_rejects_password_flags_it_cannot_honour(self):
        for extra in (["--print-password"], ["--removal-password", "operator-secret"]):
            with self.assertRaises(SystemExit) as failure:
                self.run_main(["--mdm", "--organization-name", "Example Institute",
                               *extra])
            self.assertEqual(failure.exception.code, 2)

    def test_cli_mdm_writes_an_owner_readable_profile_with_no_secret(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / "mdm.mobileconfig"
            result, stdout, stderr = self.run_main([
                "--mdm", "--organization-name", "Example Institute",
                "--out", str(out),
            ])
            self.assertEqual(result, 0)
            self.assertEqual(stdout, "", "MDM builds have no secret to print")
            self.assertIn("no removal password", stderr)
            self.assertEqual(out.stat().st_mode & 0o777, 0o600)
            profile = plistlib.loads(out.read_bytes())
            self.assertIsNone(payload(profile, "com.apple.profileRemovalPassword"))
            self.assertIs(payload(profile, "app.hisn.Hisn")["HisnManaged"], True)
            self.assertEqual(profile["PayloadOrganization"], "Example Institute")

    def test_cli_mdm_with_a_team_id_writes_the_filter_layer(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / "mdm.mobileconfig"
            result, _, stderr = self.run_main([
                "--mdm", "--organization-name", "Example Institute",
                "--team-id", "ABCDE12345", "--out", str(out),
            ])
            self.assertEqual(result, 0)
            self.assertIn("Managed filter:", stderr)
            profile = plistlib.loads(out.read_bytes())
            self.assertEqual(payload(profile, "com.apple.webcontent-filter")
                             ["FilterDataProviderBundleIdentifier"],
                             make_profile.FILTER_PROVIDER_BUNDLE_ID)
            self.assertIsNotNone(payload(profile, "com.apple.system-extension-policy"))

    def test_cli_mdm_can_keep_the_app_managed_filter(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / "mdm.mobileconfig"
            result, _, stderr = self.run_main([
                "--mdm", "--organization-name", "Example Institute",
                "--team-id", "ABCDE12345", "--without-managed-filter",
                "--out", str(out),
            ])
            self.assertEqual(result, 0)
            self.assertIn("no managed content filter", stderr)
            profile = plistlib.loads(out.read_bytes())
            self.assertIsNone(payload(profile, "com.apple.webcontent-filter"))

    def test_cli_maximum_needs_mdm_and_a_team_id(self):
        for arguments in (
            ["--maximum"],
            ["--maximum", "--mdm", "--organization-name", "Example Institute"],
            ["--maximum", "--team-id", "ABCDE12345"],
        ):
            with self.assertRaises(SystemExit) as failure:
                self.run_main(arguments)
            self.assertEqual(failure.exception.code, 2, arguments)

    def test_cli_maximum_sets_every_lock_without_a_weakening_flag(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / "maximum.mobileconfig"
            result, _, stderr = self.run_main([
                "--mdm", "--organization-name", "Example Institute",
                "--team-id", "ABCDE12345", "--maximum", "--out", str(out),
            ])
            self.assertEqual(result, 0)
            profile = plistlib.loads(out.read_bytes())
            self.assertIs(payload(profile, "com.apple.dnsSettings.managed")
                          ["DNSSettings"]["ProhibitDisablement"], True)
            self.assertIs(payload(profile, "com.apple.system-extension-policy")
                          ["AllowUserOverrides"], False)
            self.assertIs(payload(profile, "com.apple.applicationaccess")
                          ["allowEraseContentAndSettings"], False)
            self.assertIs(payload(profile, "com.apple.webcontent-filter")
                          ["SafariHistoryRetentionEnabled"], True)
            chrome = payload(profile, "com.google.Chrome")
            self.assertEqual(chrome["DeveloperToolsAvailability"], 2)
            self.assertEqual(chrome["IncognitoModeAvailability"], 1)
            self.assertIn("Maximum preset", stderr)
            self.assertIn("Layers in this build", stderr)

    def test_cli_maximum_refuses_to_be_weakened(self):
        for flag in ("--allow-devtools", "--allow-incognito", "--allow-erase",
                     "--no-safari-private-lock", "--without-managed-filter"):
            with self.assertRaises(SystemExit) as failure:
                self.run_main([
                    "--mdm", "--organization-name", "Example Institute",
                    "--team-id", "ABCDE12345", "--maximum", flag,
                ])
            self.assertEqual(failure.exception.code, 2, flag)

    def test_cli_rejects_team_id_without_mdm_and_bad_requirements(self):
        for arguments in (
            ["--team-id", "ABCDE12345"],
            ["--mdm", "--organization-name", "Example", "--team-id", "lowercase1"],
            ["--mdm", "--organization-name", "Example",
             "--filter-requirement", 'anchor apple generic'],
            ["--mdm", "--organization-name", "Example", "--team-id", "ABCDE12345",
             "--lock-system-extensions", "--without-managed-filter"],
        ):
            with self.assertRaises(SystemExit) as failure:
                self.run_main(arguments)
            self.assertEqual(failure.exception.code, 2, arguments)

    def test_cli_lock_system_extensions_reaches_the_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / "mdm.mobileconfig"
            result, _, _ = self.run_main([
                "--mdm", "--organization-name", "Example Institute",
                "--team-id", "ABCDE12345", "--lock-system-extensions",
                "--out", str(out),
            ])
            self.assertEqual(result, 0)
            policy = payload(plistlib.loads(out.read_bytes()),
                             "com.apple.system-extension-policy")
            self.assertIs(policy["AllowUserOverrides"], False)

    def test_cli_rejects_bad_force_install_input_before_any_file_is_written(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(SystemExit) as failure:
                self.run_main(["--out", str(Path(directory) / "bad.mobileconfig"),
                               "--extension-id", "abc"])
            self.assertEqual(failure.exception.code, 2)
            self.assertEqual(list(Path(directory).iterdir()), [])

    def test_native_installer_missing_values_and_relative_app_paths_fail_read_only(self):
        script = Path(__file__).parents[1] / "macos/install_native_host.sh"
        for arguments, message in ((["--extension-id"], "requires a value"),
                                   (["--app-path"], "requires a value"),
                                   (["--app-path", "relative.app", "--print-targets"], "absolute path")):
            result = subprocess.run(["bash", str(script), *arguments], capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn(message, result.stderr)
            self.assertEqual(result.stdout, "")




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

class TestMDMDeployment(unittest.TestCase):
    """--mdm is a deployment mode, not a stronger profile by itself.

    The MDM owns removal and can reinstall the profile, so a local removal
    password would be one more secret to lose and no more protection. But
    nothing here proves supervision or effective policy; those need ADE and the
    verification steps in docs/MDM_DEPLOYMENT.md.
    """

    def test_mdm_implies_the_managed_flag(self):
        profile = build(mdm=True, organization_name="Example Institute")
        managed = payload(profile, "app.hisn.Hisn")
        self.assertIsNotNone(managed)
        self.assertIs(managed["HisnManaged"], True)
        self.assertEqual(managed["OrganizationName"], "Example Institute")

    def test_mdm_omits_the_local_password_and_keeps_removal_disallowed(self):
        profile = build(mdm=True, organization_name="Example Institute")
        self.assertIsNone(payload(profile, "com.apple.profileRemovalPassword"))
        self.assertIs(profile["PayloadRemovalDisallowed"], True)
        self.assertEqual(profile["PayloadOrganization"], "Example Institute")

    def test_mdm_does_not_claim_supervision_without_the_flag(self):
        profile = build(mdm=True, organization_name="Example Institute")
        self.assertNotIn("ProhibitDisablement",
                         payload(profile, "com.apple.dnsSettings.managed")["DNSSettings"])
        supervised = build(mdm=True, organization_name="Example Institute",
                           supervised=True)
        self.assertIs(payload(supervised, "com.apple.dnsSettings.managed")
                      ["DNSSettings"]["ProhibitDisablement"], True)

    def test_split_mdm_layers_keep_the_managed_payload_and_embed_no_password(self):
        args = argparse.Namespace(
            doh="https://family.cloudflare-dns.com/dns-query",
            dns_addresses=["1.1.1.3", "1.0.0.3"], supervised=False,
            extension_id="", update_url="https://clients2.google.com/service/update2/crx",
            display_name="Test", lock_settings=False, allow_devtools=False,
            allow_incognito=False, removal_password=None, browsers=None,
            mdm=True, managed=False, organization_name="Example Institute",
            team_id="", filter_requirement="", without_managed_filter=False,
            lock_system_extensions=False, allow_erase=False,
            no_safari_private_lock=False)
        layers = make_profile.build_layers(args)
        network = next(layer for slug, layer, _ in layers if slug == "network")
        browsers = next(layer for slug, layer, _ in layers if slug == "browsers")
        self.assertIsNotNone(payload(network, "app.hisn.Hisn"))
        self.assertIsNone(payload(browsers, "app.hisn.Hisn"))
        for _, layer, password in layers:
            self.assertIsNone(password)
            self.assertIsNone(payload(layer, "com.apple.profileRemovalPassword"))


class TestMDMFilterLayer(unittest.TestCase):
    """The filter has to be owned by the management layer, not the app.

    A profile that only sets DNS and browser policy leaves the content filter
    as app-managed configuration: a person can switch it off where macOS
    offers the toggle, and the extension can be removed unless a system
    extension policy says otherwise. With a signed team ID the profile now
    carries the `com.apple.webcontent-filter` payload and the
    `com.apple.system-extension-policy` payload, so the filter starts without
    the app and (on macOS 15+) is non-removable while SIP is on.

    These tests are serialization tests. They do not prove the extension loads
    or that macOS accepts the profile; a positive MDM test is still required.
    """

    TEAM = "ABCDE12345"

    def mdm(self, **overrides):
        values = {"mdm": True, "organization_name": "Example Institute",
                  "team_id": self.TEAM}
        values.update(overrides)
        return build(**values)

    def test_no_team_id_keeps_the_old_profile_and_says_so(self):
        """Backward compatibility: an existing --mdm invocation still works."""
        profile = build(mdm=True, organization_name="Example Institute")
        self.assertIsNone(payload(profile, "com.apple.webcontent-filter"))
        self.assertIsNone(payload(profile, "com.apple.system-extension-policy"))
        self.assertIs(payload(profile, "app.hisn.Hisn")["HisnFilterManaged"], False)

    def test_team_id_adds_the_plugin_filter(self):
        profile = self.mdm()
        web = payload(profile, "com.apple.webcontent-filter")
        self.assertIsNotNone(web)
        self.assertEqual(web["FilterType"], "Plugin")
        self.assertEqual(web["PluginBundleID"], make_profile.FILTER_APP_BUNDLE_ID)
        self.assertEqual(web["FilterDataProviderBundleIdentifier"],
                         make_profile.FILTER_PROVIDER_BUNDLE_ID)
        self.assertIn(self.TEAM, web["FilterDataProviderDesignatedRequirement"])
        self.assertIn(make_profile.FILTER_PROVIDER_BUNDLE_ID,
                      web["FilterDataProviderDesignatedRequirement"])
        self.assertIs(web["FilterSockets"], True)
        self.assertIs(web["FilterPackets"], False)
        self.assertEqual(web["FilterGrade"], "firewall")
        self.assertNotIn("FilterBrowsers", web,
                         "FilterBrowsers is iOS-only; macOS uses FilterSockets")

    def test_the_filter_locks_safari_private_browsing_by_default(self):
        self.assertIs(payload(self.mdm(), "com.apple.webcontent-filter")
                      ["SafariHistoryRetentionEnabled"], True)
        self.assertIs(payload(self.mdm(no_safari_private_lock=True),
                              "com.apple.webcontent-filter")
                      ["SafariHistoryRetentionEnabled"], False)

    def test_the_extension_is_allowed_and_non_removable(self):
        policy = payload(self.mdm(), "com.apple.system-extension-policy")
        for key in ("AllowedSystemExtensions", "NonRemovableSystemExtensions",
                    "NonRemovableFromUISystemExtensions"):
            self.assertEqual(policy[key], {self.TEAM: [
                make_profile.FILTER_PROVIDER_BUNDLE_ID]}, key)
        self.assertNotIn("AllowUserOverrides", policy,
                         "blocking other extensions is opt-in")

    def test_lock_system_extensions_blocks_user_overrides(self):
        policy = payload(self.mdm(lock_system_extensions=True),
                         "com.apple.system-extension-policy")
        self.assertIs(policy["AllowUserOverrides"], False)

    def test_a_team_id_without_mdm_is_rejected(self):
        with self.assertRaises(ValueError):
            build(team_id=self.TEAM)

    def test_a_custom_requirement_must_name_the_extension_and_team(self):
        profile = self.mdm(filter_requirement=(
            'identifier "app.hisn.Hisn.HisnFilter" and anchor apple generic '
            f'and certificate leaf[subject.OU] = "{self.TEAM}"'))
        self.assertEqual(payload(profile, "com.apple.webcontent-filter")
                         ["FilterDataProviderDesignatedRequirement"],
                         'identifier "app.hisn.Hisn.HisnFilter" and anchor apple generic '
                         f'and certificate leaf[subject.OU] = "{self.TEAM}"')
        for bad in ("anchor apple generic",
                    f'anchor apple generic and certificate leaf[subject.OU] = "{self.TEAM}"',
                    'identifier "app.hisn.Hisn.HisnFilter" and certificate leaf[subject.OU] = "OTHER12345"'):
            with self.assertRaises(ValueError):
                self.mdm(filter_requirement=bad)

    def test_without_managed_filter_removes_both_payloads(self):
        profile = self.mdm(without_managed_filter=True)
        self.assertIsNone(payload(profile, "com.apple.webcontent-filter"))
        self.assertIsNone(payload(profile, "com.apple.system-extension-policy"))
        marker = payload(profile, "app.hisn.Hisn")
        self.assertIs(marker["HisnFilterManaged"], False)
        self.assertNotIn("filter", marker["HisnProtectedLayers"])

    def test_the_managed_marker_reports_the_profile_and_layers(self):
        marker = payload(self.mdm(), "app.hisn.Hisn")
        self.assertEqual(marker["HisnProfileVersion"], make_profile.PROFILE_VERSION)
        self.assertIs(marker["HisnFilterManaged"], True)
        self.assertIn("filter", marker["HisnProtectedLayers"])
        self.assertIn("system-extension-policy", marker["HisnProtectedLayers"])
        self.assertIn("erase-lock", marker["HisnProtectedLayers"])

    def test_erase_is_blocked_only_in_an_mdm_build(self):
        self.assertIs(payload(self.mdm(), "com.apple.applicationaccess")
                      ["allowEraseContentAndSettings"], False)
        self.assertNotIn("allowEraseContentAndSettings",
                         payload(self.mdm(allow_erase=True),
                                 "com.apple.applicationaccess"))
        self.assertNotIn("allowEraseContentAndSettings",
                         payload(build(), "com.apple.applicationaccess"),
                         "a personal profile must not hide the erase button")

    def test_split_layers_keep_filter_extension_and_marker_with_network(self):
        args = argparse.Namespace(
            doh="https://family.cloudflare-dns.com/dns-query",
            dns_addresses=["1.1.1.3"], supervised=False, extension_id="",
            update_url="https://clients2.google.com/service/update2/crx",
            display_name="Test", lock_settings=False, allow_devtools=False,
            allow_incognito=False, removal_password=None, browsers=None,
            mdm=True, managed=False, organization_name="Example Institute",
            team_id=self.TEAM, filter_requirement="",
            without_managed_filter=False, lock_system_extensions=False,
            allow_erase=False, no_safari_private_lock=False)
        layers = make_profile.build_layers(args)
        network = next(layer for slug, layer, _ in layers if slug == "network")
        browsers = next(layer for slug, layer, _ in layers if slug == "browsers")
        for ptype in ("com.apple.webcontent-filter",
                      "com.apple.system-extension-policy", "app.hisn.Hisn"):
            self.assertIsNotNone(payload(network, ptype), ptype)
            self.assertIsNone(payload(browsers, ptype), ptype)


class TestMDMVerifier(unittest.TestCase):
    """The verifier must never turn an unreadable answer into "not managed"."""

    def parser_code(self):
        source = (Path(__file__).parents[1] / "macos/verify_mdm.sh").read_text()
        return re.search(r"<<'STATUS'.*?\n(.*?)\nSTATUS\n", source, re.DOTALL).group(1)

    def parse(self, output):
        result = subprocess.run([sys.executable, "-", output],
                                input=self.parser_code(), capture_output=True,
                                text=True, check=True)
        return result.stdout.strip()

    def test_documented_enrollment_shapes(self):
        self.assertEqual(self.parse(""), "unknown\tunknown")
        self.assertEqual(self.parse("Enrolled via DEP: No\nMDM enrollment: No"),
                         "no\tnone")
        self.assertEqual(self.parse(
            "Enrolled via DEP: Yes\nMDM enrollment: Yes (User Approved)\n"
            "MDM server: https://mdm.example.com/path?x=1"),
            "yes\tapproved")
        self.assertEqual(self.parse(
            "Enrolled via DEP: No\nMDM enrollment: Yes (User Approved)"),
            "no\tapproved")
        self.assertEqual(self.parse("MDM enrollment: Yes"), "unknown\tunapproved")

    def test_unreadable_or_contradictory_answers_are_unknown(self):
        # A recognizable DEP line still reports what it says; the MDM state is
        # unknown, which is what the script's verdict keys off.
        self.assertEqual(self.parse("Enrolled via DEP: Yes\nMDM enrollment: No"),
                         "yes\tunknown")
        self.assertEqual(self.parse("Enrolled via DEP: Yes\nMDM enrollment: Yes"),
                         "yes\tunknown")
        for output in ("MDM enrollment: perhaps",
                       "Enrolled via DEP: Maybe\nMDM enrollment: No",
                       "profiles: could not read enrollment"):
            self.assertEqual(self.parse(output), "unknown\tunknown", output)

    def test_verifier_shell_syntax(self):
        subprocess.run(["bash", "-n", str(Path(__file__).parents[1] /
                                         "macos/verify_mdm.sh")],
                       check=True, capture_output=True, text=True)

    def layer_code(self):
        source = (Path(__file__).parents[1] / "macos/verify_mdm.sh").read_text()
        return re.search(r"<<'LAYERS'.*?\n(.*?)\nLAYERS\n", source,
                         re.DOTALL).group(1)

    def layer_facts(self, directory):
        result = subprocess.run(
            [sys.executable, "-", "testuser", str(directory)],
            input=self.layer_code(), capture_output=True, text=True, check=True)
        return dict(line.split("=", 1) for line in result.stdout.splitlines())

    def test_the_layer_reader_sees_the_managed_filter_and_policy(self):
        """`--strict` is only as honest as this reader. A plist that is there
        must be read, and a missing one must stay unknown rather than become a
        reassuring false."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "app.hisn.Hisn.plist").write_bytes(plistlib.dumps({
                "HisnManaged": True, "OrganizationName": "Example Institute",
                "HisnProfileVersion": 2, "HisnFilterManaged": True,
            }))
            (root / "com.apple.applicationaccess.plist").write_bytes(plistlib.dumps({
                "allowCloudPrivateRelay": False,
                "allowEraseContentAndSettings": False,
            }))
            (root / "com.apple.dnsSettings.managed.plist").write_bytes(plistlib.dumps({
                "DNSSettings": {"DNSProtocol": "HTTPS",
                                "ServerURL": "https://family.cloudflare-dns.com/dns-query",
                                "ProhibitDisablement": True},
            }))
            (root / "com.google.Chrome.plist").write_bytes(plistlib.dumps({
                "DnsOverHttpsMode": "off", "IncognitoModeAvailability": 1,
            }))
            (root / "org.mozilla.firefox.plist").write_bytes(plistlib.dumps({
                "EnterprisePoliciesEnabled": True,
            }))
            facts = self.layer_facts(root)
            self.assertEqual(facts["managed"], "yes")
            self.assertEqual(facts["organization"], "Example Institute")
            self.assertEqual(facts["version"], "2")
            self.assertEqual(facts["filter_declared"], "yes")
            self.assertEqual(facts["dns"], "yes")
            self.assertEqual(facts["dns_prohibit"], "yes")
            self.assertEqual(facts["relay"], "off")
            self.assertEqual(facts["erase"], "off")
            self.assertEqual(facts["chrome"], "yes")
            self.assertEqual(facts["firefox"], "yes")

    def test_the_layer_reader_keeps_missing_files_unknown(self):
        with tempfile.TemporaryDirectory() as directory:
            facts = self.layer_facts(Path(directory))
            for key in ("managed", "filter_declared", "dns_prohibit"):
                self.assertEqual(facts[key], "unknown", key)
            self.assertEqual(facts["dns"], "no",
                             "a missing DNS payload is a definite absence")
            self.assertEqual(facts["chrome"], "no")
            self.assertEqual(facts["firefox"], "no")
            self.assertEqual(facts["relay"], "unknown")
            self.assertEqual(facts["erase"], "unknown")
            self.assertEqual(facts["version"], "")


class TestManagedMode(unittest.TestCase):
    """The organization flag is a forced app-domain payload, not app state."""

    def test_managed_adds_the_forced_app_payload(self):
        profile = build(managed=True, organization_name="Example Institute")
        managed = payload(profile, "app.hisn.Hisn")
        self.assertIsNotNone(managed)
        self.assertIs(managed["HisnManaged"], True)
        self.assertEqual(managed["OrganizationName"], "Example Institute")
        self.assertIs(profile["PayloadRemovalDisallowed"], True)

    def test_an_unmanaged_profile_does_not_claim_to_be_managed(self):
        self.assertIsNone(payload(build(), "app.hisn.Hisn"))

    def test_an_empty_organization_name_gets_a_neutral_label(self):
        profile = build(managed=True, organization_name="   ")
        self.assertEqual(payload(profile, "app.hisn.Hisn")["OrganizationName"],
                         "Your organization")


if __name__ == "__main__":
    unittest.main(verbosity=2)
