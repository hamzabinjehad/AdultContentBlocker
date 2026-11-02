#!/usr/bin/env python3
"""Provision a factory-reset Android phone as Hisn's device owner.

    python3 script/guardian_provision.py --serial SERIAL --apk app.apk
    python3 script/guardian_provision.py --serial SERIAL --apk app.apk --apply --yes

The default is a dry run. It reads the phone, explains what it would do and
prints the exact adb commands; nothing is changed. `--apply` runs only those
commands, after a typed confirmation unless `--yes` is given.

The script is deliberately narrow:

  * It never wipes the phone, removes an account, uninstalls an app, clears
    app data or changes an ordinary Android setting. Removing accounts and
    secondary users is the owner's job, on the phone, before provisioning. The
    one administrative change it makes is the device-owner appointment.
  * It refuses a phone that already has another device owner, more than one
    user, an unfinished setup wizard, or an Android version below Hisn's
    minSdk.
  * It does not hide Hisn, grant it extra permissions or make it a system app.
    Device owner is the platform's own management role; the app asks Android to
    block its uninstall only while the owner's keep switch or a commitment
    holds.

Device ownership is not reversible from this script. Hisn's own **Remove
managed mode** clears it when no commitment and no keep switch remain; a
factory reset from recovery also removes it. Read
docs/ANDROID_TAMPER_RESISTANCE.md before running it.
"""
from __future__ import annotations

import argparse
import re
import shlex
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

PACKAGE = "app.hisn.android"
ADMIN = "app.hisn.android/.CommitmentAdminReceiver"
MAIN_ACTIVITY = "app.hisn.android/.MainActivity"
MIN_SDK = 26


class ProvisionError(Exception):
    """The phone or the arguments cannot be provisioned safely."""


class AdbError(ProvisionError):
    def __init__(self, command: tuple[str, ...], result: subprocess.CompletedProcess[str]):
        detail = (result.stderr or result.stdout or "").strip().splitlines()
        message = f"{shlex.join(command)} failed"
        if detail:
            message += ": " + detail[-1]
        super().__init__(message)
        self.result = result


def default_runner(command: tuple[str, ...]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, capture_output=True, text=True, timeout=60)


class Adb:
    """The small surface of adb this script needs, injectable for tests."""

    def __init__(self, executable: str = "adb", serial: str = "",
                 runner=default_runner):
        self.executable = executable
        self.serial = serial
        self.runner = runner

    def _base(self) -> tuple[str, ...]:
        return (self.executable, "-s", self.serial)

    def devices(self) -> str:
        command = (self.executable, "devices")
        result = self.runner(command)
        if result.returncode != 0:
            raise AdbError(command, result)
        return result.stdout

    def run(self, *args: str, allow_failure: bool = False) -> subprocess.CompletedProcess[str]:
        command = (*self._base(), *args)
        result = self.runner(command)
        if result.returncode != 0 and not allow_failure:
            raise AdbError(command, result)
        return result

    def shell(self, *args: str) -> subprocess.CompletedProcess[str]:
        return self.run("shell", *args)

    def shell_optional(self, *args: str) -> str | None:
        """A read whose failure means "unknown", not "stop the whole check"."""
        try:
            return self.shell(*args).stdout
        except AdbError:
            return None


@dataclass(frozen=True)
class Facts:
    """What the phone actually reports before provisioning."""

    serial: str
    sdk: int | None
    users: tuple[tuple[int, str], ...] | None
    accounts: tuple[str, ...] | None
    owner: str | None
    owner_known: bool
    installed: str | None
    setup_complete: bool | None


@dataclass(frozen=True)
class Plan:
    facts: Facts
    commands: tuple[tuple[str, ...], ...]
    problems: tuple[str, ...]
    notes: tuple[str, ...]

    @property
    def ok(self) -> bool:
        return not self.problems


def parse_devices(text: str) -> dict[str, str]:
    devices: dict[str, str] = {}
    for line in text.splitlines():
        parts = line.split()
        if len(parts) >= 2 and parts[0] != "List":
            devices[parts[0]] = parts[1]
    return devices


def parse_users(text: str | None) -> tuple[tuple[int, str], ...] | None:
    if text is None:
        return None
    users: list[tuple[int, str]] = []
    for match in re.finditer(r"UserInfo\{(\d+):([^:}]*):", text):
        users.append((int(match.group(1)), match.group(2).strip()))
    # `pm list users` always prints at least user 0 on a working device.
    return tuple(users) if users else None


def parse_accounts(text: str | None) -> tuple[str, ...] | None:
    if text is None:
        return None
    accounts = []
    for match in re.finditer(r"Account \{name=([^,}]+), type=([^}]+)\}", text):
        accounts.append(f"{match.group(1).strip()} ({match.group(2).strip()})")
    return tuple(accounts)


def parse_owner(text: str | None) -> tuple[str | None, bool]:
    """Return (owner component, whether the answer is known)."""
    if text is None:
        return None, False
    if re.search(r"\bno device owner\b", text, re.IGNORECASE):
        return None, True
    match = re.search(r"Device Owner:\s*(?:ComponentInfo\{)?([^\s}]+)", text, re.IGNORECASE)
    if match:
        value = match.group(1).strip()
        if value.lower() in ("null", "none"):
            return None, True
        return value, True
    return None, False


def parse_installed(text: str | None) -> str | None:
    if not text or "Unable to find package" in text:
        return None
    match = re.search(r"versionName=([^\s]+)", text)
    if not match or match.group(1) == "null":
        return "installed"
    return match.group(1)


def _int_or_none(text: str | None) -> int | None:
    if text is None:
        return None
    try:
        return int(text.strip())
    except ValueError:
        return None


def _bool_or_none(text: str | None) -> bool | None:
    if text is None:
        return None
    value = text.strip()
    if value == "1":
        return True
    if value == "0":
        return False
    return None


def read_facts(adb: Adb, serial: str) -> Facts:
    devices = parse_devices(adb.devices())
    state = devices.get(serial)
    if state is None:
        listed = ", ".join(sorted(devices)) or "none"
        raise ProvisionError(f"{serial} is not connected; adb devices lists: {listed}")
    if state != "device":
        raise ProvisionError(f"{serial} is {state}; authorize USB debugging or reconnect it")

    sdk = _int_or_none(adb.shell_optional("getprop", "ro.build.version.sdk"))
    users = parse_users(adb.shell_optional("pm", "list", "users"))
    accounts = parse_accounts(adb.shell_optional("dumpsys", "account"))
    owner, owner_known = parse_owner(adb.shell_optional("dumpsys", "device_policy"))
    installed = parse_installed(adb.shell_optional("dumpsys", "package", PACKAGE))
    setup_complete = _bool_or_none(
        adb.shell_optional("settings", "get", "secure", "user_setup_complete"))
    return Facts(serial, sdk, users, accounts, owner, owner_known, installed, setup_complete)


def build_plan(facts: Facts, apk: Path) -> Plan:
    """Decide whether to provision and which commands to run. Never executes."""
    problems: list[str] = []
    notes: list[str] = []

    if not apk.is_file():
        problems.append(f"APK not found: {apk}")
    if facts.sdk is None:
        problems.append("could not read the Android API level")
    elif facts.sdk < MIN_SDK:
        problems.append(f"Android API {facts.sdk} is below Hisn's minSdk {MIN_SDK}")
    if facts.users is None:
        problems.append("could not read the device's users")
    else:
        extra = [user for user in facts.users if user[0] != 0]
        if extra:
            labels = ", ".join(f"{name} (user {uid})" for uid, name in extra)
            problems.append(f"remove secondary users or profiles first: {labels}")
    if facts.accounts is None:
        notes.append("could not read accounts; Android will refuse if any exist")
    elif facts.accounts:
        shown = ", ".join(facts.accounts[:4])
        more = f" and {len(facts.accounts) - 4} more" if len(facts.accounts) > 4 else ""
        problems.append(
            "remove every account on the phone first (Settings > Passwords & accounts), "
            f"including {shown}{more}; this script will not remove them")
    if facts.setup_complete is False:
        problems.append("finish the Android setup wizard before provisioning")
    elif facts.setup_complete is None:
        notes.append("could not confirm the setup wizard finished")

    if facts.owner_known:
        if facts.owner and PACKAGE not in facts.owner:
            problems.append(f"another app is already device owner: {facts.owner}")
        elif facts.owner and PACKAGE in facts.owner:
            notes.append("Hisn is already device owner; only the app is opened")
    else:
        notes.append("could not confirm an existing device owner; Android will refuse if one exists")

    if problems:
        return Plan(facts, (), tuple(problems), tuple(notes))

    if facts.owner_known and facts.owner and PACKAGE in facts.owner:
        commands = (("shell", "am", "start", "-n", MAIN_ACTIVITY),)
    else:
        commands = (
            ("install", "-r", "--user", "0", str(apk)),
            ("shell", "dpm", "set-device-owner", ADMIN),
            ("shell", "am", "start", "-n", MAIN_ACTIVITY),
        )
    return Plan(facts, commands, (), tuple(notes))


def render(plan: Plan, adb: Adb) -> str:
    facts = plan.facts
    users = "unknown" if facts.users is None else ", ".join(
        f"{name} (user {uid})" for uid, name in facts.users)
    accounts = "unknown" if facts.accounts is None else (
        "none" if not facts.accounts else f"{len(facts.accounts)} present")
    owner = facts.owner or ("unknown" if not facts.owner_known else "none")
    setup = {True: "yes", False: "no", None: "unknown"}[facts.setup_complete]
    lines = [
        f"Device {facts.serial}: Android API {facts.sdk if facts.sdk is not None else 'unknown'}",
        f"Users: {users}",
        f"Accounts: {accounts}",
        f"Device owner: {owner}",
        f"Hisn installed: {facts.installed or 'no'}",
        f"Setup wizard complete: {setup}",
        "",
    ]
    if plan.problems:
        lines.append("Refused; nothing will be changed:")
        lines.extend(f"  - {problem}" for problem in plan.problems)
    else:
        lines.append("Planned commands:")
        for command in plan.commands:
            lines.append("  " + shlex.join((*adb._base(), *command)))
    if plan.notes:
        lines.append("")
        lines.append("Notes:")
        lines.extend(f"  - {note}" for note in plan.notes)
    return "\n".join(lines)


def execute(adb: Adb, plan: Plan, echo=print) -> int:
    """Run the planned commands in order. Returns a shell exit status."""
    for command in plan.commands:
        echo("$ " + shlex.join((*adb._base(), *command)))
        result = adb.run(*command, allow_failure=True)
        output = (result.stdout or "").strip()
        if output:
            echo(output)
        if result.returncode != 0:
            error = (result.stderr or "").strip()
            echo("FAILED" + (": " + error.splitlines()[-1] if error else ""))
            return 1
    return 0


def verify_owner(facts: Facts) -> tuple[bool, str]:
    """Read the appointment back; an unreadable answer is not a success claim."""
    if not facts.owner_known:
        return True, "device owner could not be read back; check it on the phone"
    if facts.owner and PACKAGE in facts.owner:
        return True, "device owner verified by Android readback"
    return False, f"device owner readback is {facts.owner or 'none'}"


def _confirm() -> bool:
    print("This makes Hisn the phone's device owner. The script does not remove accounts,")
    print("wipe the phone or uninstall anything. Device ownership is released from Hisn's")
    print("Remove managed mode when no commitment or keep switch remains, or by a factory reset.")
    return input("Type PROVISION to continue: ").strip() == "PROVISION"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__.splitlines()[0],
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True, help="device serial from adb devices")
    parser.add_argument("--apk", required=True, help="Hisn APK to install")
    parser.add_argument("--adb", default="adb", help="adb executable (default: adb)")
    parser.add_argument("--apply", action="store_true",
                        help="run the planned commands; without it this is a dry run")
    parser.add_argument("--yes", action="store_true",
                        help="skip the typed confirmation; only with --apply")
    args = parser.parse_args(argv)

    adb = Adb(args.adb, args.serial)
    try:
        facts = read_facts(adb, args.serial)
    except ProvisionError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    plan = build_plan(facts, Path(args.apk))
    print(render(plan, adb))
    if not plan.ok:
        return 1
    if not args.apply:
        print("\nDry run only. Re-run with --apply to execute these commands.")
        return 0
    if not args.yes and not _confirm():
        print("Cancelled.")
        return 1
    status = execute(adb, plan)
    if status != 0:
        return status
    try:
        after = read_facts(adb, args.serial)
    except ProvisionError as exc:
        print(f"FAIL: commands completed but verification could not read the phone: {exc}",
              file=sys.stderr)
        return 1
    verified, message = verify_owner(after)
    print(message)
    if not verified:
        return 1

    print("\nProvisioned. Next, on the phone:")
    print("  1. Open Hisn > Guide and turn on Keep Hisn installed.")
    print("  2. Turn on Managed lockdown and read its disclosure before saving.")
    print("  3. Check the state from this laptop:")
    print(f"     python3 android/test-live-managed-status.py --serial {args.serial} "
          "--expect-managed --expect-lockdown")
    print("\nRecovery: Hisn's Remove managed mode after the commitment and keep switch are off,")
    print("or a factory reset from recovery.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
