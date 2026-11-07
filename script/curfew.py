#!/usr/bin/env python3
"""A night curfew for this Mac: inside the window, only allowlisted apps stay usable.

    python3 script/curfew.py install --start 22:00 --end 04:00
    python3 script/curfew.py install --allow com.apple.Music --allow com.apple.iCal
    python3 script/curfew.py status
    python3 script/curfew.py check --now 23:30
    python3 script/curfew.py uninstall

It installs one per-user LaunchAgent. Every ten seconds, inside the window, it
asks which app is in front and turns the display off when that app is not on the
allow list. Finder, the Dock, the login window and the system UI are always
allowed so the Mac stays recoverable; a blocked app can be brought back only by
switching to an allowed one.

WHAT THIS IS
------------
A speed bump you chose, not a lock. It runs as you, so it cannot stop an
administrator from removing it, and it does not survive someone who knows where
the plist is. The real curfew on a Mac is Screen Time ▸ Downtime with a passcode
held by someone else; this exists for the case where that passcode is not
available yet.

HOW TO TURN IT OFF WITHOUT A TERMINAL
-------------------------------------
Finder ▸ Go to Folder ▸ ~/Library/LaunchAgents, delete app.hisn.curfew.plist, and
delete ~/Library/Application Support/app.hisn.curfew — the next tick exits and
the job stays gone after logout.
"""
from __future__ import annotations

import argparse
import json
import os
import plistlib
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

LABEL = "app.hisn.curfew"
SUPPORT = Path.home() / "Library/Application Support/app.hisn.curfew"
CONFIG = SUPPORT / "curfew.json"
OFF = SUPPORT / "curfew.off"
AGENTS = Path.home() / "Library/LaunchAgents"
PLIST = AGENTS / f"{LABEL}.plist"
LOG_DIR = Path.home() / "Library/Logs/Hisn"
LOG = LOG_DIR / "curfew.log"

# Always allowed: without Finder and the login window there is no way back in.
IMPLICIT_ALLOW = (
    "com.apple.finder",
    "com.apple.dock",
    "com.apple.loginwindow",
    "com.apple.controlcenter",
    "com.apple.notificationcenterui",
    "com.apple.systemuiserver",
    "com.apple.SecurityAgent",
    "com.apple.UserNotificationCenter",
    "com.apple.screencaptureui",
    "com.apple.ScreenSaver.Engine",
)

DEFAULT_START = 22 * 60
DEFAULT_END = 4 * 60
TICK_SECONDS = 10


def parse_clock(value: str) -> int:
    text = value.strip()
    if not text:
        raise argparse.ArgumentTypeError("empty time")
    parts = text.split(":")
    if len(parts) > 2:
        raise argparse.ArgumentTypeError(f"not a clock time: {value}")
    try:
        hour = int(parts[0])
        minute = int(parts[1]) if len(parts) == 2 else 0
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"not a clock time: {value}") from exc
    if not (0 <= hour <= 24) or not (0 <= minute < 60) or (hour == 24 and minute):
        raise argparse.ArgumentTypeError(f"not a clock time: {value}")
    return (hour % 24) * 60 + minute


def clock_text(minutes: int) -> str:
    return f"{minutes // 60:02d}:{minutes % 60:02d}"


def in_window(minutes: int, start: int, end: int) -> bool:
    if start == end:
        return False
    if start < end:
        return start <= minutes < end
    return minutes >= start or minutes < end


def minutes_now(now: datetime | None = None) -> int:
    now = now or datetime.now()
    return now.hour * 60 + now.minute


def load_config() -> dict:
    try:
        return json.loads(CONFIG.read_text())
    except (OSError, ValueError):
        return {}


def saved_config() -> dict:
    config = load_config()
    return {
        "enabled": bool(config.get("enabled", False)),
        "start": int(config.get("start", DEFAULT_START)),
        "end": int(config.get("end", DEFAULT_END)),
        "allow": list(config.get("allow", [])),
    }


def frontmost_bundle() -> str | None:
    """The frontmost app's bundle id, read without asking for Accessibility."""
    front = subprocess.run(["lsappinfo", "front"], capture_output=True, text=True, timeout=5)
    token = front.stdout.strip()
    if not token.startswith("ASN:") or front.returncode != 0:
        return None
    info = subprocess.run(
        ["lsappinfo", "info", "-only", "bundleid", token],
        capture_output=True, text=True, timeout=5,
    )
    for line in info.stdout.splitlines():
        line = line.strip()
        if line.startswith("bundleID="):
            return line.split("=", 1)[1].strip().strip('"')
    return None


def is_allowed(bundle: str | None, allow: list) -> bool:
    if not bundle:
        return True  # unreadable is not proof of a blocked app; do not blank
    return bundle in IMPLICIT_ALLOW or bundle in allow


def blank_display() -> bool:
    """Turn the display off. Falls back to the screen saver when pmset refuses."""
    try:
        proc = subprocess.run(["pmset", "displaysleepnow"], capture_output=True, text=True, timeout=10)
        if proc.returncode == 0:
            return True
    except (OSError, subprocess.SubprocessError):
        pass
    try:
        proc = subprocess.run(
            ["open", "-a", "/System/Library/CoreServices/ScreenSaverEngine.app"],
            capture_output=True, text=True, timeout=10,
        )
        return proc.returncode == 0
    except (OSError, subprocess.SubprocessError):
        return False


def log(message: str) -> None:
    try:
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        with LOG.open("a") as handle:
            handle.write(f"{datetime.now().isoformat(timespec='seconds')} {message}\n")
    except OSError:
        pass


def tick() -> int:
    """One evaluation. Called by launchd every TICK_SECONDS."""
    if OFF.exists():
        return 0
    config = saved_config()
    if not config["enabled"]:
        return 0
    if not in_window(minutes_now(), config["start"], config["end"]):
        return 0
    bundle = frontmost_bundle()
    if is_allowed(bundle, config["allow"]):
        return 0
    ok = blank_display()
    log(f"curfew: blanked display for {bundle} ({'ok' if ok else 'failed'})")
    return 0


def make_plist(script: Path) -> dict:
    return {
        "Label": LABEL,
        "ProgramArguments": ["/usr/bin/python3", str(script), "tick"],
        "RunAtLoad": True,
        "StartInterval": TICK_SECONDS,
        "StandardOutPath": str(LOG),
        "StandardErrorPath": str(LOG),
        "ProcessType": "Background",
        "LowPriorityIO": True,
    }


def launchctl(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(["launchctl", *args], capture_output=True, text=True, timeout=20)


def install(args) -> int:
    start = args.start
    end = args.end
    if start == end:
        print("start and end must differ", file=sys.stderr)
        return 2
    allow = list(dict.fromkeys(args.allow or []))
    SUPPORT.mkdir(parents=True, exist_ok=True)
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG.write_text(json.dumps({
        "enabled": True,
        "start": start,
        "end": end,
        "allow": allow,
    }, indent=2) + "\n")
    if OFF.exists():
        OFF.unlink()
    # A launchd job cannot read ~/Desktop (TCC), so run the copy in Application
    # Support; deleting that copy is also the no-terminal way to stop the job.
    script = SUPPORT / "curfew.py"
    shutil.copy2(Path(__file__).resolve(), script)
    script.chmod(0o755)
    AGENTS.mkdir(parents=True, exist_ok=True)
    PLIST.write_bytes(plistlib.dumps(make_plist(script)))
    domain = f"gui/{os.getuid()}"
    launchctl("bootout", domain, str(PLIST))
    loaded = launchctl("bootstrap", domain, str(PLIST))
    if loaded.returncode != 0:
        print(loaded.stderr.strip() or loaded.stdout.strip(), file=sys.stderr)
        return 1
    print(f"Curfew installed: {clock_text(start)}–{clock_text(end)}, every {TICK_SECONDS}s.")
    print(f"  allow list: {', '.join(allow) if allow else 'nothing (system UI and Finder only)'}")
    print(f"  plist:      {PLIST}")
    print(f"  config:     {CONFIG}")
    print(f"  log:        {LOG}")
    print("Screen Time ▸ Downtime with a guardian's passcode is the real lock; this is the speed bump.")
    return 0


def uninstall(_args) -> int:
    domain = f"gui/{os.getuid()}"
    launchctl("bootout", domain, str(PLIST))
    for path in (PLIST, CONFIG, SUPPORT / "curfew.py"):
        try:
            path.unlink()
        except FileNotFoundError:
            pass
    print("Curfew removed.")
    return 0


def change_allow(args, add: bool) -> int:
    config = saved_config()
    if not config["enabled"]:
        print("not installed — run install first")
        return 1
    allow = config["allow"]
    for bundle in args.bundles:
        if add and bundle not in allow:
            allow.append(bundle)
        if not add and bundle in allow:
            allow.remove(bundle)
    CONFIG.write_text(json.dumps({
        "enabled": True,
        "start": config["start"],
        "end": config["end"],
        "allow": allow,
    }, indent=2) + "\n")
    print(f"allowed now: {', '.join(allow) if allow else 'nothing (system UI and Finder only)'}")
    return 0


def status(_args) -> int:
    config = saved_config()
    print(f"config:  {'enabled' if config['enabled'] else 'not installed'}"
          f" ({clock_text(config['start'])}–{clock_text(config['end'])})")
    print(f"allow:   {', '.join(config['allow']) if config['allow'] else 'nothing'}")
    print(f"window:  {'inside' if in_window(minutes_now(), config['start'], config['end']) else 'outside'}"
          f" at {clock_text(minutes_now())}")
    print(f"off file: {'present — paused' if OFF.exists() else 'absent'}")
    print(f"front:   {frontmost_bundle() or 'unreadable'}")
    loaded = launchctl("print", f"gui/{os.getuid()}/{LABEL}")
    print(f"agent:   {'loaded' if loaded.returncode == 0 else 'not loaded'}")
    return 0


def check(args) -> int:
    config = saved_config()
    minutes = args.now if args.now is not None else minutes_now()
    if not config["enabled"]:
        print("not installed — run install first")
        return 1
    inside = in_window(minutes, config["start"], config["end"])
    bundle = frontmost_bundle()
    allowed = is_allowed(bundle, config["allow"])
    decision = "blank" if inside and not allowed else "allow"
    print(f"{clock_text(minutes)}: window {'open' if inside else 'closed'}; "
          f"front {bundle or 'unreadable'}; would {decision}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = parser.add_subparsers(dest="command", required=True)

    inst = sub.add_parser("install", help="install the per-user LaunchAgent")
    inst.add_argument("--start", type=parse_clock, default=DEFAULT_START)
    inst.add_argument("--end", type=parse_clock, default=DEFAULT_END)
    inst.add_argument("--allow", action="append", default=[], metavar="BUNDLE_ID")
    inst.set_defaults(func=install)

    sub.add_parser("uninstall", help="remove the agent and config").set_defaults(func=uninstall)
    sub.add_parser("status", help="what is installed and what is in front").set_defaults(func=status)

    chk = sub.add_parser("check", help="one evaluation, touches nothing")
    chk.add_argument("--now", type=parse_clock, default=None)
    chk.set_defaults(func=check)

    add = sub.add_parser("allow", help="let an app through the curfew")
    add.add_argument("bundles", nargs="+", metavar="BUNDLE_ID")
    add.set_defaults(func=lambda a: change_allow(a, True))

    deny = sub.add_parser("deny", help="block an app again")
    deny.add_argument("bundles", nargs="+", metavar="BUNDLE_ID")
    deny.set_defaults(func=lambda a: change_allow(a, False))

    sub.add_parser("tick", help="internal: one launchd evaluation").set_defaults(func=lambda _a: tick())
    return parser


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
