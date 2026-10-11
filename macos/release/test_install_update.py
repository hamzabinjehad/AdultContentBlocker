"""Fixture-only installer checks: never build, stop apps or run an install."""
import os
import plistlib
import pwd
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "install_update.sh"


class UpdateTests(unittest.TestCase):
    def run_definition(self, body, **values):
        environment = dict(os.environ, INSTALL_HELPER=str(HELPER), **values)
        return subprocess.run(["/bin/bash", "-uc", 'source "$INSTALL_HELPER"; ' + body],
                              env=environment, capture_output=True, text=True)

    def test_build_numbers_only_move_forward(self):
        cases = [("1", "2", True), ("1.2", "1.3", True), ("1.2.9", "1.3", True),
                 ("1", "1", False), ("2", "1", False), ("1.0", "1", False),
                 ("1.2.9", "1.2.8", False), ("invalid", "2", False),
                 ("1", "1.2.3.4", False)]
        for previous, candidate, accepted in cases:
            with self.subTest(previous=previous, candidate=candidate):
                result = self.run_definition('newer_build "$OLD" "$NEW"', OLD=previous, NEW=candidate)
                self.assertEqual(result.returncode == 0, accepted, result.stderr)

    def agent(self, account):
        return dict(Label="app.hisn.agent", ProgramArguments=[
            "/Applications/Hisn.app/Contents/MacOS/Hisn", "--background", "--for-user", account],
            RunAtLoad=True, KeepAlive={"SuccessfulExit": False}, ThrottleInterval=10)

    def test_only_root_owned_non_writable_agents_are_trusted(self):
        for owner, mode, accepted in (("0", "644", True), ("0", "600", True),
                                      ("0", "0644", True), ("501", "644", False),
                                      ("0", "664", False), ("0", "646", False),
                                      ("0", "777", False), ("0", "invalid", False)):
            with self.subTest(owner=owner, mode=mode):
                result = self.run_definition('secure_agent_permissions "$OWNER" "$MODE"', OWNER=owner, MODE=mode)
                self.assertEqual(result.returncode == 0, accepted, result.stderr)

    def test_existing_protected_account_and_agent_are_preserved(self):
        user = pwd.getpwuid(os.getuid()).pw_name
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "agent.plist"
            original = plistlib.dumps(self.agent(user))
            destination.write_bytes(original)
            result = self.run_definition('protected_user "$FIXTURE_AGENT"', FIXTURE_AGENT=str(destination))
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.strip(), user)
            self.assertEqual(destination.read_bytes(), original)

    def test_malformed_existing_agent_is_not_reassigned(self):
        user = pwd.getpwuid(os.getuid()).pw_name
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "agent.plist"
            for alteration in ({"Label": "other"}, {"ProgramArguments": ["/tmp/Hisn"]},
                               {"ProgramArguments": self.agent("root")["ProgramArguments"]}):
                agent = dict(self.agent(user), **alteration)
                destination.write_bytes(plistlib.dumps(agent))
                result = self.run_definition('protected_user "$FIXTURE_AGENT"', FIXTURE_AGENT=str(destination))
                self.assertNotEqual(result.returncode, 0)

    def simulate_cleanup(self, directory, *, committed, old_moved, new_moved, agent_created, stopped):
        root = Path(directory)
        backup = root / "backup"
        backup.mkdir()
        app = root / "Hisn.app"
        if old_moved:
            (backup / "Hisn.app").mkdir()
            (backup / "Hisn.app/old.txt").write_text("previous app")
        if new_moved:
            app.mkdir()
            (app / "new.txt").write_text("new app")
        elif not old_moved:
            app.mkdir()
            (app / "old.txt").write_text("previous app")
        agent = root / "agent.plist"
        agent.write_bytes(plistlib.dumps(self.agent(pwd.getpwuid(os.getuid()).pw_name)))
        lock = root / "lock"
        lock.mkdir()
        source = HELPER.read_text()
        cleanup = source[source.index("cleanup() {"):source.index("trap cleanup EXIT")]
        # Every mutable destination is an explicit fixture. Absolute launchctl
        # calls and app stopping are intercepted, never sent to the real system.
        script = '''
set -u
/bin/launchctl() { printf '%s\\n' "$*" >> "$FIXTURE_LAUNCH_LOG"; }
stop_installed_app() { :; }
''' + cleanup + "\nfalse; cleanup\n"
        environment = dict(os.environ, APP=str(app), AGENT=str(agent), BACKUP=str(backup),
                           LOCK=str(lock), STAGE="", LABEL="fixture", DOMAIN="fixture",
                           COMMITTED=str(int(committed)), OLD_MOVED=str(int(old_moved)),
                           NEW_MOVED=str(int(new_moved)), AGENT_CREATED=str(int(agent_created)),
                           AGENT_STOPPED=str(int(stopped)), FIXTURE_LAUNCH_LOG=str(root / "launch.log"))
        result = subprocess.run(["/bin/bash", "-c", script], env=environment,
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 1)
        self.assertFalse(lock.exists())
        return app, backup, agent, root / "launch.log"

    def test_failed_update_restores_previous_bundle_and_preserves_agent(self):
        with tempfile.TemporaryDirectory() as directory:
            app, backup, agent, log = self.simulate_cleanup(directory, committed=False,
                old_moved=True, new_moved=True, agent_created=False, stopped=True)
            self.assertTrue((app / "old.txt").exists())
            self.assertTrue((backup / "Failed-Hisn.app/new.txt").exists())
            self.assertTrue(agent.exists())
            self.assertIn("bootstrap fixture", log.read_text())

    def test_failed_fresh_install_removes_only_new_agent_and_retains_failed_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            app, backup, agent, _ = self.simulate_cleanup(directory, committed=False,
                old_moved=False, new_moved=True, agent_created=True, stopped=True)
            self.assertFalse(app.exists())
            self.assertTrue((backup / "Failed-Hisn.app/new.txt").exists())
            self.assertFalse(agent.exists())

    def test_failure_before_stopping_never_replaces_or_stops_old_app(self):
        with tempfile.TemporaryDirectory() as directory:
            app, _, agent, log = self.simulate_cleanup(directory, committed=False,
                old_moved=False, new_moved=False, agent_created=False, stopped=False)
            self.assertTrue((app / "old.txt").exists())
            self.assertTrue(agent.exists())
            self.assertFalse(log.exists())

    def test_success_keeps_backup_and_does_not_restore_old_app(self):
        with tempfile.TemporaryDirectory() as directory:
            app, backup, agent, log = self.simulate_cleanup(directory, committed=True,
                old_moved=True, new_moved=True, agent_created=False, stopped=True)
            self.assertTrue((app / "new.txt").exists())
            self.assertTrue((backup / "Hisn.app/old.txt").exists())
            self.assertTrue(agent.exists())
            self.assertFalse(log.exists())

    def test_installer_never_destructively_removes_live_bundle_or_kills_all_hisn_processes(self):
        for script in (HELPER, ROOT / "install.sh"):
            source = script.read_text()
            self.assertNotIn('rm -rf "$APP"', source)
            self.assertNotIn("pkill -x Hisn", source)
        source = HELPER.read_text()
        self.assertLess(source.index('validate_bundle "$STAGE/Hisn.app"'), source.index("AGENT_STOPPED=1"))
        self.assertIn('[[ -z "$OLD_TEAM" || "$OLD_TEAM" = "$NEW_TEAM" ]]', source)
        self.assertIn('--built-app)', (ROOT / "install.sh").read_text())
        self.assertIn('"$(signing_team "$filter")" = "$app_team"', source)
        self.assertIn('"$(signing_team "$bundle/Contents/MacOS/HisnBridge")" = "$app_team"', source)


if __name__ == "__main__":
    unittest.main()
