# Hisn Mac 1.1.5 — root guardian daemon

Development candidate, 30 October 2026. Unsigned and unnotarized; not a
distribution build. No standard-account, reboot or notarized-package test was
run for this change.

## What changed

A root **LaunchDaemon** now backs the login agent. Before 1.1.5 the agent lived
in `/Library/LaunchAgents` (root-owned, but loaded into the user's GUI session);
a standard user could `launchctl bootout` it for that session. The new daemon
runs in the system domain and puts the agent back.

- `/Library/LaunchDaemons/app.hisn.guardian.plist` runs
  `/bin/bash "/Library/Application Support/Hisn/hisn-guardian" --daemon` as
  root, `RunAtLoad`, with `KeepAlive` only for an unsuccessful exit.
- The protected account is named in a root-owned
  `/Library/Application Support/Hisn/guardian.conf` (mode 644, root:wheel). The
  daemon rejects `root`, `loginwindow`, `_mbsetupuser` and anything that is not
  an existing `[A-Za-z0-9._-]+` account.
- Every 30 seconds it checks whether `gui/<uid>/app.hisn.agent` exists. If the
  GUI session exists and the agent is gone, it bootstraps the agent with
  `launchctl asuser <uid> launchctl bootstrap gui/<uid> …`.
- It logs `agent-reloaded`, `agent-reload-failed`, `account-missing` or
  `config-unreadable` with a UTC timestamp to
  `/Library/Application Support/Hisn/guardian.log`. No browsing data is logged.
- It does **not** relaunch an app the person quit normally outside a lock. The
  agent's `KeepAlive` handles a crash or force-quit; Hisn already refuses Quit
  during a lock. Restarting a normal quit would fight a legitimate control.
- It does **not** probe the content filter. The app already asks the filter over
  XPC and reports a missing/stale provider; the daemon's job is the system
  domain, not a second health model.

### List freshness is visible

The Details page now shows **Last list update** and says when the downloaded
generation is more than seven days old or when the last update failed. The
previous signed list is still enforced; the note is freshness, not a missing
layer. `ListUpdateFreshness` is a small tested rule, not a second updater.

### Installer changes

- The package `postinstall` installs the daemon, its script and config after the
  login agent, then bootstraps it. A daemon failure is reported but does not
  undo a valid app install.
- `macos/install_update.sh` does the same for the developer installer.
- `macos/install.sh --remove-agent` now removes the daemon, its plist and its
  script/config as well as the login agent.
- The release package copies `install_guardian.sh`, `hisn-guardian` and
  `app.hisn.guardian.plist` into its scripts payload.

Version 1.1.5, build 9.

## Verification

On 30 October 2026:

- `./test.sh release`: **33 tests**, including the new
  `release/test_guardian.py` (9 tests). It covers the plist shape, a missing
  config, refused accounts (`root`, `loginwindow`, a space and a shell
  injection), a logged-out session being quiet, bootstrapping a missing agent,
  a failed reload, a healthy agent left alone, installer wiring and shell syntax.
- `./test.sh macos`: **496 app tests** plus the Arabic-string check (524 strings).
- `bash macos/release/release.sh --smoke /tmp/…`: **unsigned package structure
  passed**. The expanded package's `Scripts/` contains `postinstall`,
  `install_guardian.sh`, `hisn-guardian` and `app.hisn.guardian.plist`; nothing
  was installed. The package remains unsigned.
- `./test.sh python seed` and the version/coverage checks pass.

**No root install, standard-account `launchctl bootout`, reboot, or
notarized-package test is claimed.** The daemon is exercised with boundary
functions replaced, not against a live system domain.

## Limits

- The daemon is root-owned and survives a standard user, but an administrator
  can `launchctl bootout system/app.hisn.guardian`, remove the plist, or remove
  the app. That is the Mac lock ladder's honest limit; see
  [TAMPER_MODEL.md](TAMPER_MODEL.md).
- A logged-out account has no GUI session; the daemon waits rather than treating
  that as tampering.
- The daemon does not repair a stopped system extension, a removed profile or a
  lost Full Disk Access. The app's status reports those; an administrator must
  restore them.
- The log is local and contains only account/action/time. It does not prove the
  filter was enforcing, and it is not yet surfaced in the app; an administrator
  reads it directly.
- The package is still unsigned and unnotarized; a Developer ID and notarization
  remain owner actions.

## Device checks still open

On a clean Mac with a **standard daily account** and a separate administrator:

1. Install the app and daemon; confirm the agent starts at login.
2. As the standard user, `launchctl bootout gui/$(id -u)/app.hisn.agent`; within
   one interval the daemon must restore it and the app must return.
3. Log out and back in; the agent must start normally and the log must not show a
   logged-out failure.
4. Reboot; confirm the daemon starts before login, waits for the GUI session, and
   restores the agent.
5. As the administrator, `launchctl bootout system/app.hisn.guardian`; confirm
   the app stays installed and the login agent is no longer restored. That is the
   documented admin recovery, not a bug.
6. Run the notarized package on a clean Mac and record the result in
   [RELEASE_VALIDATION.md](RELEASE_VALIDATION.md).
