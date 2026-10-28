#!/usr/bin/env python3
"""
Tests for the Windows registry policy generator.

    python3 -m pytest test_make_windows_policy.py -q
    python3 test_make_windows_policy.py

Same conventions as test_make_profile.py: stdlib unittest, and every docstring
says WHAT BUG IT PREVENTS. A .reg file fails quietly — regedit merges what it
can parse and the browser ignores what it cannot read — so these tests parse
the output back the way regedit and Chrome would.
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import make_windows_policy as mwp  # noqa: E402

EXTENSION_ID = "a" * 32
CHROME = r"HKEY_LOCAL_MACHINE\SOFTWARE\Policies\Google\Chrome"
EDGE = r"HKEY_LOCAL_MACHINE\SOFTWARE\Policies\Microsoft\Edge"
BRAVE = r"HKEY_LOCAL_MACHINE\SOFTWARE\Policies\BraveSoftware\Brave"
FIREFOX = r"HKEY_LOCAL_MACHINE\SOFTWARE\Policies\Mozilla\Firefox"


def decode(data: bytes) -> str:
    return data.decode("utf-16")


def parse(data: bytes) -> tuple[dict[str, dict[str, str]], list[str]]:
    """Return ({key: {name: raw value}}, [deleted keys]) as regedit reads them."""
    keys: dict[str, dict[str, str]] = {}
    deleted: list[str] = []
    current = None
    for line in decode(data).split("\r\n")[1:]:
        if line.startswith("[-"):
            deleted.append(line[2:-1])
            current = None
        elif line.startswith("["):
            current = keys.setdefault(line[1:-1], {})
        elif line:
            match = re.fullmatch(r'"((?:[^"\\]|\\.)*)"=(.*)', line)
            assert match and current is not None, line
            current[match.group(1)] = match.group(2)
    return keys, deleted


def unquote(raw: str) -> str:
    assert raw.startswith('"') and raw.endswith('"'), raw
    return re.sub(r"\\(.)", r"\1", raw[1:-1])


def build(**overrides):
    return mwp.build(**{"extension_id": EXTENSION_ID, **overrides})


class TestFileFormat(unittest.TestCase):

    def test_utf16_bom_header_and_crlf(self):
        """regedit rejects a version 5.00 file that is not UTF-16 with a BOM,
        and reads LF-only lines as one long line."""
        for data in build():
            self.assertTrue(data.startswith(b"\xff\xfe"))
            text = decode(data)
            self.assertTrue(text.startswith("Windows Registry Editor Version 5.00\r\n"))
            self.assertNotIn("\n", text.replace("\r\n", ""))

    def test_only_policy_keys_are_touched(self):
        """Both files must stay inside HKLM\\SOFTWARE\\Policies. A remove file
        that deleted a browser's own key would wipe its settings."""
        for data in build():
            keys, deleted = parse(data)
            for key in [*keys, *deleted]:
                self.assertTrue(key.startswith(r"HKEY_LOCAL_MACHINE\SOFTWARE\Policies" + "\\"), key)
            for root in (CHROME, EDGE, BRAVE, FIREFOX):
                self.assertNotIn(root, deleted)

    def test_strings_are_escaped(self):
        """An unescaped quote ends the value early; regedit then skips the
        line and the policy is silently absent."""
        self.assertEqual(mwp.reg_string('a"b\\c'), '"a\\"b\\\\c"')


class TestExtensionPolicy(unittest.TestCase):

    def test_force_install_list(self):
        """The forcelist is what greys out Remove and Disable. It is a subkey
        of numbered values, not a single string, on Windows."""
        keys, _ = parse(build()[0])
        for root in (CHROME, EDGE, BRAVE):
            forced = keys[root + r"\ExtensionInstallForcelist"]
            self.assertEqual(unquote(forced["1"]), f"{EXTENSION_ID};{mwp.CHROME_WEB_STORE}")

    def test_force_list_cleared_before_write(self):
        """Without clearing, an old longer list keeps its tail entries."""
        _, deleted = parse(build()[0])
        self.assertIn(CHROME + r"\ExtensionInstallForcelist", deleted)

    def test_extension_settings_is_valid_json(self):
        """Chrome reads a dictionary policy on Windows as a JSON string. Bad
        JSON drops the whole policy, including the block on other extensions."""
        keys, _ = parse(build()[0])
        settings = json.loads(unquote(keys[CHROME]["ExtensionSettings"]))
        self.assertEqual(settings[EXTENSION_ID]["installation_mode"], "force_installed")
        self.assertEqual(settings["*"]["installation_mode"], "blocked")

    def test_no_id_no_extension_policy(self):
        """Blocking every extension without forcing Hisn would leave the
        browser with no extension at all."""
        keys, _ = parse(mwp.build()[0])
        self.assertNotIn("ExtensionSettings", keys[CHROME])
        self.assertNotIn(CHROME + r"\ExtensionInstallForcelist", keys)

    def test_bad_id_rejected(self):
        with self.assertRaises(ValueError):
            mwp.build(extension_id="not-an-id")
        with self.assertRaises(ValueError):
            mwp.build(extension_id=EXTENSION_ID, update_url="http://example.com/crx")


class TestBrowserControls(unittest.TestCase):

    def test_chrome_locks(self):
        keys, _ = parse(build()[0])
        chrome = keys[CHROME]
        self.assertEqual(chrome["IncognitoModeAvailability"], "dword:00000001")
        self.assertEqual(chrome["DeveloperToolsAvailability"], "dword:00000002")
        self.assertEqual(chrome["BrowserGuestModeEnabled"], "dword:00000000")
        self.assertEqual(chrome["NativeMessagingUserLevelHosts"], "dword:00000000")
        self.assertEqual(unquote(chrome["DnsOverHttpsMode"]), "off")
        self.assertEqual(json.loads(unquote(chrome["ProxySettings"])), {"ProxyMode": "system"})

    def test_edge_uses_its_own_names(self):
        """Edge calls private windows InPrivate and profiles profiles; with
        only Chrome's names, Edge keeps both open."""
        edge = parse(build()[0])[0][EDGE]
        self.assertEqual(edge["InPrivateModeAvailability"], "dword:00000001")
        self.assertEqual(edge["BrowserAddProfileEnabled"], "dword:00000000")
        self.assertEqual(edge["ForceBingSafeSearch"], "dword:00000002")

    def test_brave_tor_and_vpn_off(self):
        """A Brave Tor window skips both the system resolver and incognito
        policy wording; its VPN leaves the filtered path too."""
        brave = parse(build()[0])[0][BRAVE]
        self.assertEqual(brave["TorDisabled"], "dword:00000001")
        self.assertEqual(brave["BraveVPNDisabled"], "dword:00000001")

    def test_firefox_dicts_are_subkeys(self):
        """Firefox reads nested policies as subkeys, not JSON strings."""
        keys, _ = parse(build()[0])
        self.assertEqual(keys[FIREFOX + r"\DNSOverHTTPS"],
                         {"Enabled": "dword:00000000", "Locked": "dword:00000001"})
        self.assertEqual(keys[FIREFOX]["DisablePrivateBrowsing"], "dword:00000001")

    def test_allow_flags(self):
        keys, _ = parse(build(lock_devtools=False, block_incognito=False)[0])
        self.assertNotIn("DeveloperToolsAvailability", keys[CHROME])
        self.assertNotIn("IncognitoModeAvailability", keys[CHROME])
        self.assertNotIn("InPrivateModeAvailability", keys[EDGE])
        self.assertNotIn("TorDisabled", keys[BRAVE])
        self.assertEqual(keys[FIREFOX]["DisablePrivateBrowsing"], "dword:00000000")

    def test_browser_selection(self):
        keys, _ = parse(build(browsers=["edge"])[0])
        self.assertIn(EDGE, keys)
        self.assertNotIn(CHROME, keys)
        with self.assertRaises(ValueError):
            build(browsers=["safari"])


class TestRemoveFile(unittest.TestCase):

    def test_remove_undoes_every_value(self):
        """The remove file is the exit the guardian holds. Any value it misses
        stays in force after 'removal'."""
        applied, cleared = parse(build()[0])
        removed, deleted = parse(build()[1])
        for key, values in applied.items():
            if key in cleared:
                self.assertIn(key, deleted)
                continue
            self.assertEqual(set(removed[key]), set(values), key)
            self.assertTrue(all(v == "-" for v in removed[key].values()))


class TestCommandLine(unittest.TestCase):

    def test_writes_both_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "policy.reg"
            result = subprocess.run(
                [sys.executable, str(Path(mwp.__file__)), "--extension-id", EXTENSION_ID,
                 "--out", str(out)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(out.exists())
            self.assertTrue((Path(tmp) / "policy-remove.reg").exists())

    def test_missing_id_warns(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = subprocess.run(
                [sys.executable, str(Path(mwp.__file__)), "--out", str(Path(tmp) / "p.reg")],
                capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("NOT force-installed", result.stderr)


if __name__ == "__main__":
    unittest.main()
