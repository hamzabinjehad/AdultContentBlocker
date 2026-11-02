"""The laptop provisioning helper must refuse every unsafe phone and never wipe.

The helper is deliberately read-only unless `--apply` is given. These tests
pin the preflight facts, the exact commands and the stop-on-failure behaviour,
so a future change cannot quietly add a destructive step to a guardian tool.
"""
import subprocess
import tempfile
import unittest
from pathlib import Path

import guardian_provision as gp


def facts(**overrides):
    values = dict(
        serial="SER",
        sdk=34,
        users=((0, "Owner"),),
        accounts=(),
        owner=None,
        owner_known=True,
        installed=None,
        setup_complete=True,
    )
    values.update(overrides)
    return gp.Facts(**values)


class ParseTests(unittest.TestCase):
    def test_devices(self):
        text = ("List of devices attached\n"
                "R58M1234\tdevice\n"
                "emulator-5554\tunauthorized\n")
        self.assertEqual(gp.parse_devices(text),
                         {"R58M1234": "device", "emulator-5554": "unauthorized"})

    def test_users(self):
        text = ("Users:\n"
                "\tUserInfo{0:Owner:c13} running\n"
                "\tUserInfo{10:Guest:404} running\n")
        self.assertEqual(gp.parse_users(text), ((0, "Owner"), (10, "Guest")))

    def test_users_unknown_when_empty(self):
        self.assertIsNone(gp.parse_users(""))
        self.assertIsNone(gp.parse_users(None))

    def test_accounts(self):
        text = ("Accounts: 2\n"
                "  Account {name=person@example.com, type=com.google}\n"
                "  Account {name=work, type=com.microsoft}\n")
        self.assertEqual(gp.parse_accounts(text),
                         ("person@example.com (com.google)", "work (com.microsoft)"))

    def test_no_accounts(self):
        self.assertEqual(gp.parse_accounts("Accounts: 0\n"), ())

    def test_device_owner(self):
        component = "app.hisn.android/app.hisn.android.CommitmentAdminReceiver"
        owner, known = gp.parse_owner(f"Device Owner: ComponentInfo{{{component}}}\n")
        self.assertTrue(known)
        self.assertIn("app.hisn.android", owner)

    def test_no_device_owner(self):
        self.assertEqual(gp.parse_owner("Device Owner: null\n"), (None, True))
        self.assertEqual(gp.parse_owner("No device owner\n"), (None, True))

    def test_unreadable_owner_is_reported_as_unknown(self):
        self.assertEqual(gp.parse_owner("something else"), (None, False))
        self.assertEqual(gp.parse_owner(None), (None, False))

    def test_installed_version(self):
        self.assertEqual(gp.parse_installed("versionName=0.8.4\n"), "0.8.4")
        self.assertEqual(gp.parse_installed("versionName=null\n"), "installed")
        self.assertIsNone(gp.parse_installed("Unable to find package: app.hisn.android\n"))
        self.assertIsNone(gp.parse_installed(None))


class PlanTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.apk = Path(self.tmp.name) / "hisn.apk"
        self.apk.write_bytes(b"not really an apk; only the path is checked")

    def test_installs_then_sets_owner_then_opens(self):
        plan = gp.build_plan(facts(), self.apk)
        self.assertTrue(plan.ok)
        self.assertEqual(plan.commands, (
            ("install", "-r", "--user", "0", str(self.apk)),
            ("shell", "dpm", "set-device-owner", gp.ADMIN),
            ("shell", "am", "start", "-n", gp.MAIN_ACTIVITY),
        ))

    def test_refuses_any_account(self):
        plan = gp.build_plan(facts(accounts=("person@example.com (com.google)",)), self.apk)
        self.assertFalse(plan.ok)
        self.assertEqual(plan.commands, ())
        self.assertIn("remove every account", plan.problems[0])

    def test_refuses_secondary_user_or_profile(self):
        plan = gp.build_plan(facts(users=((0, "Owner"), (10, "Guest"))), self.apk)
        self.assertFalse(plan.ok)
        self.assertIn("secondary users", plan.problems[0])
        self.assertIn("Guest", plan.problems[0])

    def test_refuses_another_device_owner(self):
        plan = gp.build_plan(facts(owner="com.other/.Admin"), self.apk)
        self.assertFalse(plan.ok)
        self.assertIn("another app is already device owner", plan.problems[0])

    def test_refuses_old_android(self):
        plan = gp.build_plan(facts(sdk=25), self.apk)
        self.assertFalse(plan.ok)
        self.assertIn("minSdk 26", plan.problems[0])

    def test_refuses_missing_apk(self):
        plan = gp.build_plan(facts(), Path(self.tmp.name) / "missing.apk")
        self.assertFalse(plan.ok)
        self.assertIn("APK not found", plan.problems[0])

    def test_refuses_unfinished_setup(self):
        plan = gp.build_plan(facts(setup_complete=False), self.apk)
        self.assertFalse(plan.ok)
        self.assertIn("setup wizard", plan.problems[0])

    def test_unknown_setup_is_a_note_not_a_block(self):
        plan = gp.build_plan(facts(setup_complete=None), self.apk)
        self.assertTrue(plan.ok)
        self.assertTrue(any("setup wizard" in note for note in plan.notes))

    def test_unknown_owner_is_a_note_not_a_block(self):
        plan = gp.build_plan(facts(owner_known=False), self.apk)
        self.assertTrue(plan.ok)
        self.assertTrue(any("existing device owner" in note for note in plan.notes))

    def test_already_owner_only_opens_the_app(self):
        plan = gp.build_plan(facts(owner=gp.PACKAGE, installed="0.8.4"), self.apk)
        self.assertTrue(plan.ok)
        self.assertEqual(plan.commands,
                         (("shell", "am", "start", "-n", gp.MAIN_ACTIVITY),))
        self.assertTrue(any("already device owner" in note for note in plan.notes))

    def test_plan_contains_no_destructive_command(self):
        plan = gp.build_plan(facts(), self.apk)
        text = " ".join(" ".join(command) for command in plan.commands).lower()
        for forbidden in ("uninstall", "clear", "factory", "wipe", "remove-account"):
            self.assertNotIn(forbidden, text)
        self.assertNotIn("pm clear", text)

    def test_render_names_the_commands_and_the_refusal(self):
        adb = gp.Adb("adb", "SER")
        good = gp.render(gp.build_plan(facts(), self.apk), adb)
        self.assertIn("install -r --user 0", good)
        self.assertIn("set-device-owner", good)
        bad = gp.render(gp.build_plan(facts(accounts=("a (b)",)), self.apk), adb)
        self.assertIn("Refused; nothing will be changed", bad)


class RecordingAdb:
    def __init__(self, fail_at=None):
        self.executable = "adb"
        self.serial = "SER"
        self.calls = []
        self.fail_at = fail_at

    def _base(self):
        return (self.executable, "-s", self.serial)

    def run(self, *args, allow_failure=False):
        self.calls.append(args)
        code = 1 if self.fail_at is not None and len(self.calls) == self.fail_at else 0
        return subprocess.CompletedProcess(
            (*self._base(), *args), code,
            "Success\n" if code == 0 else "",
            "denied\n" if code != 0 else "")


class ExecuteTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.apk = Path(self.tmp.name) / "hisn.apk"
        self.apk.write_bytes(b"x")
        self.plan = gp.build_plan(facts(), self.apk)

    def test_runs_commands_in_order(self):
        adb = RecordingAdb()
        self.assertEqual(gp.execute(adb, self.plan, echo=lambda line: None), 0)
        self.assertEqual(tuple(adb.calls), self.plan.commands)

    def test_stops_on_first_failure(self):
        adb = RecordingAdb(fail_at=2)
        self.assertEqual(gp.execute(adb, self.plan, echo=lambda line: None), 1)
        self.assertEqual(len(adb.calls), 2)


class VerifyTests(unittest.TestCase):
    def test_package_owner_is_verified(self):
        ok, message = gp.verify_owner(facts(owner=gp.PACKAGE))
        self.assertTrue(ok)
        self.assertIn("verified", message)

    def test_another_owner_is_a_failure(self):
        ok, message = gp.verify_owner(facts(owner="com.other/.Admin"))
        self.assertFalse(ok)
        self.assertIn("com.other", message)

    def test_no_owner_is_a_failure(self):
        ok, message = gp.verify_owner(facts(owner=None))
        self.assertFalse(ok)
        self.assertIn("none", message)

    def test_unknown_readback_is_not_claimed_as_success(self):
        ok, message = gp.verify_owner(facts(owner_known=False))
        self.assertTrue(ok)
        self.assertIn("could not be read back", message)


class ReadFactsTests(unittest.TestCase):
    def test_rejects_a_device_that_is_not_connected(self):
        def runner(command):
            return subprocess.CompletedProcess(command, 0,
                                               "List of devices attached\nOTHER\tdevice\n", "")
        adb = gp.Adb("adb", "SER", runner=runner)
        with self.assertRaises(gp.ProvisionError):
            gp.read_facts(adb, "SER")

    def test_rejects_an_unauthorized_device(self):
        def runner(command):
            return subprocess.CompletedProcess(command, 0,
                                               "List of devices attached\nSER\tunauthorized\n", "")
        adb = gp.Adb("adb", "SER", runner=runner)
        with self.assertRaises(gp.ProvisionError):
            gp.read_facts(adb, "SER")

    def test_reads_the_fixture(self):
        responses = {
            ("shell", "getprop", "ro.build.version.sdk"): "34\n",
            ("shell", "pm", "list", "users"): "Users:\n\tUserInfo{0:Owner:c13} running\n",
            ("shell", "dumpsys", "account"): "Accounts: 0\n",
            ("shell", "dumpsys", "device_policy"): "Device Owner: null\n",
            ("shell", "dumpsys", "package", gp.PACKAGE):
                "versionName=0.8.4\nversionCode=24\n",
            ("shell", "settings", "get", "secure", "user_setup_complete"): "1\n",
        }

        def runner(command):
            if command[1] == "devices":
                return subprocess.CompletedProcess(command, 0,
                                                   "List of devices attached\nSER\tdevice\n", "")
            return subprocess.CompletedProcess(command, 0,
                                               responses.get(command[3:], ""), "")

        adb = gp.Adb("adb", "SER", runner=runner)
        value = gp.read_facts(adb, "SER")
        self.assertEqual(value.sdk, 34)
        self.assertEqual(value.users, ((0, "Owner"),))
        self.assertEqual(value.accounts, ())
        self.assertEqual(value.owner, None)
        self.assertTrue(value.owner_known)
        self.assertEqual(value.installed, "0.8.4")
        self.assertTrue(value.setup_complete)


if __name__ == "__main__":
    unittest.main()
