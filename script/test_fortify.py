"""The fortify operator tool parses real readbacks and refuses to overclaim."""
import io
import json
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

import fortify

VERIFY = """Protection evidence on this Mac — 2026-11-06 13:14
================================================
Daily user: x
Scope: configured native-link browsers; preference presence is not effective-policy proof.

  ○ OPEN      account split          'x' is an admin — can undo everything below
  ● observed  app files              owned by root — the browser link's program cannot be swapped
  ● observed  domain blocklist       148038 entries in /etc/hosts
  ◐ partial   SafeSearch (DNS)       not forced for: Google outside the extension
  ◐ partial   DNS bypasses           not blocked: dns.google
  ○ OPEN      system filter          not reported active — check signing
"""


class FakeRunner:
    def __init__(self, answers=None):
        self.answers = answers or {}
        self.commands = []

    def run(self, args, timeout=60, check=False, input_text=None):
        key = tuple(str(a) for a in args)
        self.commands.append(key)
        out = self.answers.get(key, "")
        if callable(out):
            out = out()
        return subprocess.CompletedProcess([str(a) for a in args], 0, out, "")

    def text(self, args, timeout=60, default=""):
        return self.run(args, timeout=timeout).stdout


class ParseTests(unittest.TestCase):
    def test_verify_rows_become_checks(self):
        checks = fortify.parse_verify_output(VERIFY)
        by_id = {c.id: c for c in checks}
        self.assertEqual(by_id["account_split"].status, "open")
        self.assertEqual(by_id["app_files"].status, "enforced")
        self.assertEqual(by_id["domain_blocklist"].status, "enforced")
        self.assertEqual(by_id["safesearch_dns"].status, "partial")
        self.assertEqual(by_id["system_filter"].status, "open")
        self.assertIn("148038", by_id["domain_blocklist"].detail)

    def test_header_lines_are_ignored(self):
        self.assertEqual(fortify.parse_verify_output("Daily user: x\n"), [])

    def test_adb_devices_skip_unauthorised(self):
        text = ("List of devices attached\n"
                "RZCTA0XN60K            device usb:1 product:r9q2xeea model:SM_G990B2 device:r9q\n"
                "ABC123                 unauthorized usb:2\n")
        self.assertEqual(fortify.parse_adb_devices(text), [("RZCTA0XN60K", "SM_G990B2")])

    def test_devicectl_keeps_pairing_state(self):
        payload = {"result": {"devices": [
            {"identifier": "A", "hardwareProperties": {"udid": "A", "reality": "physical"},
             "connectionProperties": {"pairingState": "paired"},
             "deviceProperties": {"name": "iPhone"}},
            {"identifier": "B", "hardwareProperties": {"udid": "B", "reality": "simulated"},
             "connectionProperties": {"pairingState": "connected"}},
        ]}}
        devices = fortify.parse_devicectl_devices(payload)
        self.assertEqual(len(devices), 1)
        self.assertEqual(devices[0]["udid"], "A")
        self.assertTrue(devices[0]["paired"])


class MacAuditTests(unittest.TestCase):
    def runner(self, dns="  nameserver[0] : 94.140.14.15\n  nameserver[1] : 94.140.15.16\n"):
        return FakeRunner({
            (str(fortify.REPO / "macos/verify_enforcement.sh"),): VERIFY,
            ("scutil", "--dns"): dns,
            ("wc", "-l", "/etc/hosts"): "  154908 /etc/hosts\n",
        })

    def test_family_resolver_reads_enforced(self):
        device = fortify.audit_macos(self.runner())
        dns = next(c for c in device.checks if c.id == "filtering_dns")
        self.assertEqual(dns.status, "enforced")
        self.assertIn("AdGuard Family", dns.detail)

    def test_router_resolver_reads_open(self):
        device = fortify.audit_macos(self.runner("  nameserver[0] : 172.20.10.1\n"))
        dns = next(c for c in device.checks if c.id == "filtering_dns")
        self.assertEqual(dns.status, "open")

    def test_mixed_resolvers_read_partial(self):
        device = fortify.audit_macos(self.runner(
            "  nameserver[0] : 94.140.14.15\n  nameserver[1] : 172.20.10.1\n"))
        dns = next(c for c in device.checks if c.id == "filtering_dns")
        self.assertEqual(dns.status, "partial")

    def test_hosts_count_replaces_the_script_detail(self):
        device = fortify.audit_macos(self.runner())
        hosts = next(c for c in device.checks if c.id == "domain_blocklist")
        self.assertIn("154908", hosts.detail)


class PlanTests(unittest.TestCase):
    def runner(self):
        return FakeRunner({
            ("route", "-n", "get", "default"): "   interface: en0\n",
            ("networksetup", "-listnetworkserviceorder"):
                "(1) Wi-Fi\n(Hardware Port: Wi-Fi, Device: en0)\n\n"
                "(2) iPhone USB\n(Hardware Port: iPhone USB, Device: en5)\n",
            ("networksetup", "-listallnetworkservices"): "Wi-Fi\niPhone USB\n",
        })

    def test_default_plan_is_extras_then_dns(self):
        steps = fortify.macos_plan(self.runner())
        self.assertEqual(steps[0], ["extras"])
        dns_steps = [s for s in steps if s[0] == "networksetup"]
        self.assertEqual([s[2] for s in dns_steps], ["Wi-Fi", "iPhone USB"])
        for step in dns_steps:
            self.assertEqual(step[-2:], list(fortify.FAMILY_DNS))

    def test_full_hosts_plan_merges_the_list(self):
        steps = fortify.macos_plan(self.runner(), full_hosts=True)
        self.assertEqual(steps[0][-1], "--merge")

    def test_extras_replaced_not_duplicated(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "hosts"
            path.write_text("127.0.0.1 localhost\n")
            fortify.apply_extras_hosts("# >>> hisn safesearch begin >>>\n0.0.0.0 google.com\n", path)
            fortify.apply_extras_hosts("# >>> hisn safesearch begin >>>\n0.0.0.0 google.com\n", path)
            text = path.read_text()
            self.assertEqual(text.count(fortify.EXTRAS_START), 1)
            self.assertEqual(text.count("0.0.0.0 google.com"), 1)
            self.assertTrue(text.startswith("127.0.0.1 localhost"))

    def test_x_media_hosts_are_written_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "hosts"
            path.write_text("127.0.0.1 localhost\n")
            fortify.apply_x_media_hosts(path)
            fortify.apply_x_media_hosts(path)
            text = path.read_text()
            self.assertEqual(text.count(fortify.X_MEDIA_START), 1)
            self.assertEqual(text.count("0.0.0.0 pbs.twimg.com"), 1)
            self.assertTrue(text.startswith("127.0.0.1 localhost"))

    def test_non_root_apply_prints_the_sudo_line(self):
        with mock.patch.object(fortify.os, "geteuid", return_value=501):
            out = io.StringIO()
            with redirect_stdout(out):
                fortify.apply_macos(FakeRunner())
        self.assertIn("sudo", out.getvalue())
        self.assertIn("apply --macos", out.getvalue())


class DeviceTests(unittest.TestCase):
    def test_worst_layer_wins(self):
        device = fortify.Device("android", "phone", "1")
        device.add(fortify.Check("a", "A", "enforced"))
        device.add(fortify.Check("b", "B", "partial"))
        self.assertEqual(device.status(), "partial")
        device.add(fortify.Check("c", "C", "open"))
        self.assertEqual(device.status(), "open")

    def test_critical_open_only_counts_listed_ids(self):
        device = fortify.Device("android", "phone", "1")
        device.add(fortify.Check("hisn_vpn", "VPN", "open"))
        device.add(fortify.Check("cosmetic", "Other", "open"))
        self.assertEqual([c.id for c in device.critical_open()], ["hisn_vpn"])

    def test_render_arabic_marks_every_state(self):
        device = fortify.Device("ios", "iPhone", "1")
        device.add(fortify.Check("screen_time", "Screen Time", "unknown", "read on the phone"))
        device.add(fortify.Check("hisn_safari", "Safari", "enforced", "installed"))
        text = fortify.render_markdown([device], lang="ar")
        self.assertIn("الآيفون", text)
        self.assertIn("غير معروف", text)
        self.assertIn("مطبّق", text)


class IosTests(unittest.TestCase):
    def test_unreadable_phone_is_unknown_not_missing(self):
        entry = {"name": "iPhone", "udid": "U", "paired": True}
        with mock.patch.object(fortify, "installed_ios_apps", return_value=None):
            device = fortify.audit_ios(FakeRunner(), entry)
        self.assertEqual(next(c for c in device.checks if c.id == "hisn_safari").status, "unknown")
        self.assertEqual(device.critical_open(), [])

    def test_installed_safari_build_reads_enforced(self):
        entry = {"name": "iPhone", "udid": "U", "paired": True}
        with mock.patch.object(fortify, "installed_ios_apps", return_value={"app.hisn.safari"}):
            device = fortify.audit_ios(FakeRunner(), entry)
        self.assertEqual(next(c for c in device.checks if c.id == "hisn_safari").status, "enforced")


class AndroidAuditTests(unittest.TestCase):
    def test_strict_dns_is_never_chosen_while_the_vpn_resolves(self):
        self.assertEqual(fortify.private_dns_mode_for(True), "opportunistic")
        self.assertEqual(fortify.private_dns_mode_for(False), "hostname")
    def test_opportunistic_dns_is_partial_and_strict_is_enforced(self):
        answers = {
            ("adb", "-s", "S", "shell", "pm", "list", "packages"): f"package:{fortify.APP_ANDROID}\n",
            ("adb", "-s", "S", "shell", "dumpsys", "package", fortify.APP_ANDROID):
                "versionName=1.1.0-direct\n",
            ("adb", "-s", "S", "shell", "dumpsys", "connectivity"):
                "ni{VPN CONNECTED extra: VPN:app.hisn.android}",
            ("adb", "-s", "S", "shell", "settings", "get", "secure", "enabled_accessibility_services"):
                "app.hisn.android/app.hisn.android.TextGuardService",
            ("adb", "-s", "S", "shell", "appops", "get", fortify.APP_ANDROID, "GET_USAGE_STATS"):
                "GET_USAGE_STATS: allow",
            ("adb", "-s", "S", "shell", "settings", "get", "global", "private_dns_mode"): "opportunistic\n",
            ("adb", "-s", "S", "shell", "settings", "get", "global", "private_dns_specifier"):
                "dns-family.adguard.com\n",
            ("adb", "-s", "S", "shell", "settings", "get", "secure", "always_on_vpn_app"):
                fortify.APP_ANDROID + "\n",
        }
        runner = FakeRunner(answers)
        device = fortify.audit_android(runner, "adb", "S", "SM_G990B2")
        self.assertEqual(next(c for c in device.checks if c.id == "private_dns").status, "partial")
        self.assertEqual(next(c for c in device.checks if c.id == "hisn_vpn").status, "enforced")
        self.assertEqual(next(c for c in device.checks if c.id == "always_on_vpn").status, "enforced")

        answers[("adb", "-s", "S", "shell", "settings", "get", "global", "private_dns_mode")] = "hostname\n"
        device = fortify.audit_android(FakeRunner(answers), "adb", "S", "SM_G990B2")
        self.assertEqual(next(c for c in device.checks if c.id == "private_dns").status, "enforced")


if __name__ == "__main__":
    unittest.main()
