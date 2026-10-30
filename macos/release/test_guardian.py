"""The root guardian daemon: decisions, plist and installer wiring.

No launchctl, package or account operation runs. The shell script exposes its
three boundary functions so a test can replace them and still exercise the real
configuration parsing and logging.
"""
import os
import plistlib
import pwd
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INSTALLER = ROOT / "release/installer"
GUARDIAN = INSTALLER / "hisn-guardian"
PLIST = INSTALLER / "app.hisn.guardian.plist"


class GuardianTests(unittest.TestCase):
    def tree(self):
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        home = root / "Library/Application Support/Hisn"
        home.mkdir(parents=True)
        (root / "Library/LaunchAgents").mkdir(parents=True)
        return temporary, root

    def run_guardian(self, root, body, config=None):
        if config is not None:
            (root / "Library/Application Support/Hisn/guardian.conf").write_text(config)
        actions = root / "actions"
        environment = dict(os.environ, HISN_GUARDIAN_ROOT=str(root),
                           HISN_GUARDIAN_SOURCE_ONLY="1", ACTIONS=str(actions),
                           GUARDIAN=str(GUARDIAN))
        result = subprocess.run(["/bin/bash", "-c", 'source "$GUARDIAN"; ' + body],
                                env=environment, capture_output=True, text=True, timeout=20)
        return result, actions

    def log(self, root):
        path = root / "Library/Application Support/Hisn/guardian.log"
        return path.read_text() if path.exists() else ""

    def test_plist_is_a_system_daemon_that_runs_the_guardian(self):
        with PLIST.open("rb") as source:
            data = plistlib.load(source)
        self.assertEqual(data["Label"], "app.hisn.guardian")
        self.assertEqual(data["ProgramArguments"],
                         ["/bin/bash", "/Library/Application Support/Hisn/hisn-guardian", "--daemon"])
        self.assertIs(data["RunAtLoad"], True)
        self.assertEqual(data["KeepAlive"], {"SuccessfulExit": False})
        self.assertGreaterEqual(data["ThrottleInterval"], 30)
        self.assertNotIn("LimitLoadToSessionType", data, "a system daemon must not be a GUI agent")

    def test_missing_config_is_reported_without_touching_launchd(self):
        temporary, root = self.tree()
        with temporary:
            result, actions = self.run_guardian(
                root, 'gui_domain_loaded(){ touch "$ACTIONS.gui"; return 0; }; '
                      'agent_loaded(){ touch "$ACTIONS.agent"; return 0; }; '
                      'check_once; echo "RC=$?"')
            self.assertIn("RC=1", result.stdout)
            self.assertFalse(actions.exists(), "a missing config reached launchctl")
            self.assertIn("config-unreadable", self.log(root))

    def test_root_and_malformed_accounts_are_refused(self):
        for account in ("root", "loginwindow", "a b", "user;open /Applications/Calculator.app"):
            with self.subTest(account=account):
                temporary, root = self.tree()
                with temporary:
                    result, actions = self.run_guardian(
                        root, 'gui_domain_loaded(){ touch "$ACTIONS.gui"; return 0; }; '
                              'agent_loaded(){ touch "$ACTIONS.agent"; return 0; }; '
                              'check_once; echo "RC=$?"',
                        config=f"USER_NAME={account}\n")
                    self.assertIn("RC=1", result.stdout)
                    self.assertFalse(actions.exists(), f"{account!r} reached launchctl")

    def test_logged_out_account_is_normal_and_quiet(self):
        temporary, root = self.tree()
        with temporary:
            user = pwd.getpwuid(os.getuid()).pw_name
            result, actions = self.run_guardian(
                root, 'gui_domain_loaded(){ return 1; }; '
                      'agent_loaded(){ touch "$ACTIONS.agent"; return 1; }; '
                      'reload_agent(){ touch "$ACTIONS.reload"; return 0; }; '
                      'check_once; echo "RC=$?"',
                config=f"USER_NAME={user}\n")
            self.assertIn("RC=0", result.stdout)
            self.assertFalse(actions.exists(), "a logged-out account tried to load the agent")
            self.assertEqual(self.log(root), "")

    def test_missing_agent_is_bootstrapped_and_logged(self):
        temporary, root = self.tree()
        with temporary:
            user = pwd.getpwuid(os.getuid()).pw_name
            result, actions = self.run_guardian(
                root, 'gui_domain_loaded(){ return 0; }; '
                      'agent_loaded(){ return 1; }; '
                      'reload_agent(){ echo "reload uid=$1" >> "$ACTIONS"; return 0; }; '
                      'check_once; echo "RC=$?"',
                config=f"USER_NAME={user}\n")
            self.assertIn("RC=0", result.stdout)
            self.assertIn("reload uid=", actions.read_text())
            self.assertIn(f"agent-reloaded user={user}", self.log(root))

    def test_failed_reload_is_reported(self):
        temporary, root = self.tree()
        with temporary:
            user = pwd.getpwuid(os.getuid()).pw_name
            result, actions = self.run_guardian(
                root, 'gui_domain_loaded(){ return 0; }; '
                      'agent_loaded(){ return 1; }; '
                      'reload_agent(){ return 1; }; '
                      'check_once; echo "RC=$?"',
                config=f"USER_NAME={user}\n")
            self.assertIn("RC=1", result.stdout)
            self.assertIn(f"agent-reload-failed user={user}", self.log(root))

    def test_healthy_agent_is_left_alone(self):
        temporary, root = self.tree()
        with temporary:
            user = pwd.getpwuid(os.getuid()).pw_name
            result, actions = self.run_guardian(
                root, 'gui_domain_loaded(){ return 0; }; '
                      'agent_loaded(){ return 0; }; '
                      'reload_agent(){ touch "$ACTIONS.reload"; return 0; }; '
                      'check_once; echo "RC=$?"',
                config=f"USER_NAME={user}\n")
            self.assertIn("RC=0", result.stdout)
            self.assertFalse(actions.exists(), "a healthy agent was reloaded")
            self.assertEqual(self.log(root), "")

    def test_installers_ship_and_remove_the_daemon(self):
        postinstall = (INSTALLER / "postinstall").read_text()
        self.assertIn('install_guardian "$SCRIPTS" "$USER_NAME"', postinstall)
        update = (ROOT / "install_update.sh").read_text()
        self.assertIn('install_guardian "$SCRIPT_DIR/release/installer" "$USER_NAME"', update)
        release = (ROOT / "release/release.sh").read_text()
        for name in ("install_guardian.sh", "hisn-guardian", "app.hisn.guardian.plist"):
            self.assertIn(name, release)
        remove = (ROOT / "install.sh").read_text()
        self.assertIn("app.hisn.guardian.plist", remove)
        self.assertIn("guardian.conf", remove)

    def test_shell_syntax_and_permissions(self):
        for name in ("hisn-guardian", "postinstall", "preinstall", "install_guardian.sh"):
            path = INSTALLER / name
            subprocess.run(["/bin/bash", "-n", str(path)], check=True)
        self.assertTrue(os.access(GUARDIAN, os.X_OK), "the daemon script must be executable")


if __name__ == "__main__":
    unittest.main()
