#!/usr/bin/env python3
"""
Hisn browser policy for Windows.

Produces two registry files from the same browser policy as the Mac profile
(make_profile.py):

    hisn-windows-policy.reg          apply: double-click as an administrator
    hisn-windows-policy-remove.reg   undo:  give this to whoever holds the
                                            administrator password, not the user

    python3 make_windows_policy.py --extension-id <store id> \\
                                   --out ../dist/hisn-windows-policy.reg

The policy is what makes the extension hard to remove: a browser that reads
`ExtensionInstallForcelist` greys out Remove and Disable and reinstalls the
extension if its files are deleted. The keys live under
`HKEY_LOCAL_MACHINE\\SOFTWARE\\Policies`, which only an administrator can write.

WHAT THIS DOES AND DOES NOT DO
------------------------------
It is only as strong as the administrator password. Whoever holds it can merge
the remove file, or delete the keys in regedit, in a minute. The person being
protected must use a standard account and must not hold that password; see
docs/WINDOWS_SETUP.md and docs/THREAT_MODEL.md.

On a Windows PC that is not joined to a domain or enrolled in MDM, Chrome and
Edge force-install only from their own stores. The default update URL is the
Chrome Web Store's; a self-hosted update URL is ignored on such a PC.

Generating the file does not install it or prove a browser enforces it. Open
chrome://policy (edge://policy, brave://policy) after merging and check each
policy shows Status OK.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from make_profile import (  # noqa: E402
    CHROMIUM_DEVTOOLS_LOCK,
    CHROMIUM_INCOGNITO_BLOCK,
    CHROMIUM_POLICY,
    chromium_extension_policy,
    validate_extension_source,
)

HIVE = "HKEY_LOCAL_MACHINE"
CHROME_WEB_STORE = "https://clients2.google.com/service/update2/crx"

# Registry policy roots, from each vendor's administrator documentation.
BROWSERS = {
    "chrome": ("Google Chrome", r"SOFTWARE\Policies\Google\Chrome"),
    "edge": ("Microsoft Edge", r"SOFTWARE\Policies\Microsoft\Edge"),
    "brave": ("Brave", r"SOFTWARE\Policies\BraveSoftware\Brave"),
    "firefox": ("Firefox", r"SOFTWARE\Policies\Mozilla\Firefox"),
}


def chromium_policy(browser: str, extension_id: str, update_url: str,
                    lock_devtools: bool, block_incognito: bool) -> dict:
    """The Mac profile's Chromium policy, with each browser's own names added.

    Edge renamed two of Chrome's policies; Brave adds Tor windows and its own
    VPN, both of which leave the filtered path. Unknown names are ignored by a
    browser, so adding the right one never weakens another.
    """
    policy = dict(CHROMIUM_POLICY)
    if lock_devtools:
        policy.update(CHROMIUM_DEVTOOLS_LOCK)
    if block_incognito:
        policy.update(CHROMIUM_INCOGNITO_BLOCK)
    if browser == "chrome":
        policy["SafeSitesFilterBehavior"] = 1
    elif browser == "edge":
        policy["ForceBingSafeSearch"] = 2
        policy["BrowserAddProfileEnabled"] = False
        if block_incognito:
            policy["InPrivateModeAvailability"] = 1
    elif browser == "brave":
        # A Tor window is a private window that also skips the system
        # resolver, so it goes whenever incognito does. The VPN goes always.
        if block_incognito:
            policy["TorDisabled"] = True
        policy["BraveVPNDisabled"] = True
    if extension_id:
        policy.update(chromium_extension_policy(extension_id, update_url))
    return policy


def firefox_policy(lock_devtools: bool, block_incognito: bool) -> dict:
    """The Mac profile's Firefox policy. There is no Hisn Firefox extension,
    so this locks DNS and proxies only; Firefox stays outside the extension."""
    return {
        "DNSOverHTTPS": {"Enabled": False, "Locked": True},
        "DisablePrivateBrowsing": block_incognito,
        "DisableDeveloperTools": lock_devtools,
        "Proxy": {"Mode": "system", "Locked": True},
        "BlockAboutConfig": True,
    }


def reg_string(text: str) -> str:
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def reg_value(value) -> str:
    # bool before int: True is an int in Python.
    if isinstance(value, bool):
        return f"dword:{int(value):08x}"
    if isinstance(value, int):
        if not 0 <= value <= 0xFFFFFFFF:
            raise ValueError(f"{value} does not fit a registry DWORD")
        return f"dword:{value:08x}"
    if isinstance(value, str):
        return reg_string(value)
    raise TypeError(f"no registry form for {type(value).__name__}")


class RegFile:
    """Apply and remove sections built side by side, so the remove file always
    undoes exactly what the apply file wrote and nothing else."""

    def __init__(self) -> None:
        self.apply: list[str] = []
        self.remove: list[str] = []

    def key(self, path: str, values: dict, *, replace: bool = False) -> None:
        full = f"{HIVE}\\{path}"
        if replace:
            # A list is a subkey of numbered values. Clear it first, or a
            # shorter list leaves the old tail entries in force.
            self.apply.append(f"[-{full}]")
            self.remove.extend([f"[-{full}]", ""])
        self.apply.append(f"[{full}]")
        self.apply.extend(f"{reg_string(name)}={reg_value(v)}" for name, v in values.items())
        self.apply.append("")
        if not replace and values:
            self.remove.append(f"[{full}]")
            self.remove.extend(f"{reg_string(name)}=-" for name in values)
            self.remove.append("")

    def policy(self, root: str, policy: dict, *, dicts_as_json: bool) -> None:
        """Chromium reads a dictionary policy as a JSON string; Firefox reads
        it as a subkey. Both read a list as a subkey numbered from 1."""
        scalars = {}
        for name, value in policy.items():
            if isinstance(value, list):
                self.key(f"{root}\\{name}",
                         {str(i): item for i, item in enumerate(value, 1)}, replace=True)
            elif isinstance(value, dict) and dicts_as_json:
                scalars[name] = json.dumps(value, separators=(",", ":"), sort_keys=True)
            elif isinstance(value, dict):
                self.key(f"{root}\\{name}", value)
            else:
                scalars[name] = value
        self.key(root, scalars)

    def render(self, lines: list[str]) -> bytes:
        text = "\r\n".join(["Windows Registry Editor Version 5.00", "", *lines, ""])
        # regedit expects UTF-16 LE with a byte-order mark for version 5.00.
        return "﻿".encode("utf-16-le") + text.encode("utf-16-le")


def build(extension_id: str = "", update_url: str = CHROME_WEB_STORE,
          browsers: list[str] | None = None, lock_devtools: bool = True,
          block_incognito: bool = True) -> tuple[bytes, bytes]:
    """Return (apply file, remove file) as bytes ready to write."""
    validate_extension_source(extension_id, update_url)
    selected = browsers or list(BROWSERS)
    unknown = set(selected) - set(BROWSERS)
    if unknown:
        raise ValueError("unknown browsers: " + ", ".join(sorted(unknown)))
    reg = RegFile()
    for browser in BROWSERS:
        if browser not in selected:
            continue
        root = BROWSERS[browser][1]
        if browser == "firefox":
            reg.policy(root, firefox_policy(lock_devtools, block_incognito),
                       dicts_as_json=False)
        else:
            reg.policy(root, chromium_policy(browser, extension_id, update_url,
                                             lock_devtools, block_incognito),
                       dicts_as_json=True)
    return reg.render(reg.apply), reg.render(reg.remove)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--extension-id", default="",
                    help="Chrome Web Store ID of the Hisn extension. Without it "
                         "the extension is not force-installed and can be removed.")
    ap.add_argument("--update-url", default=CHROME_WEB_STORE)
    ap.add_argument("--browser", dest="browsers", action="append",
                    choices=sorted(BROWSERS),
                    help="Emit policy for this browser only; repeat for several. Default: all.")
    ap.add_argument("--allow-devtools", action="store_true",
                    help="Leave DevTools enabled. DevTools can edit extension state.")
    ap.add_argument("--allow-incognito", action="store_true",
                    help="Leave private windows available. By default they are "
                         "disabled, which is the only way to close that hole.")
    ap.add_argument("--out", default="../dist/hisn-windows-policy.reg")
    args = ap.parse_args()
    try:
        apply_reg, remove_reg = build(args.extension_id, args.update_url, args.browsers,
                                      lock_devtools=not args.allow_devtools,
                                      block_incognito=not args.allow_incognito)
    except ValueError as error:
        ap.error(str(error))

    out = Path(args.out)
    remove_out = out.with_name(f"{out.stem}-remove{out.suffix}")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(apply_reg)
    remove_out.write_bytes(remove_reg)
    print(f"wrote {out}\nwrote {remove_out}", file=sys.stderr)

    if not args.extension_id:
        print("\nNo --extension-id: browser settings are locked, but the "
              "extension is NOT force-installed and can still be removed.",
              file=sys.stderr)
    elif args.update_url != CHROME_WEB_STORE:
        print("\nSelf-hosted update URL: Chrome and Edge ignore it on a PC that "
              "is not domain-joined or MDM-enrolled. Use the Chrome Web Store.",
              file=sys.stderr)
    print("\nMerge the apply file as an administrator, then check chrome://policy. "
          "Keep the remove file with whoever holds the administrator password; "
          "the user's own account must be a standard account.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
