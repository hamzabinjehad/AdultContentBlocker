# Tamper model — who can change what, from the code as it is

`THREAT_MODEL.md` Part 2 gives the attacks by likelihood. This document is the
audit behind it: every store the enforcement reads, who owns it, who can write
it, and therefore what each kind of person can undo. It distinguishes
**redundant** storage (survives accidents and casual deletion) from
**tamper-resistant** storage (survives a motivated standard user), because the
two were being described with one word.

Verified from source on 2026-09-13. Rows marked *inferred* describe behaviour
read from the code; rows marked *demonstrated* have a test or a script that
shows it. Nothing here was exercised against a live filter on a real Mac in
this pass — see "Assumption to verify first".

## The principals

| Principal | Means |
|---|---|
| **Standard user** (the daily account after `setup_guardian.sh --demote-me`) | Their own files, their login keychain, their browser profile, `defaults write` on their own domains, quitting or force-quitting processes they own |
| **Administrator** (the guardian, or the daily user before demotion) | `sudo`, System Settings (profiles, extensions, network filter), `systemextensionsctl`, editing anything under `/Library` |
| **Recovery-capable user** (anyone with a FileVault token who can reboot into Recovery) | Disable SIP, boot another OS, erase the disk |

## Stores and who can write them

| Store | Path / location | Owner today | Standard user can… | Enforcement effect |
|---|---|---|---|---|
| Lock — app-group defaults | `~/Library/Group Containers/group.app.hisn/…/group.app.hisn.plist` | user | edit or delete | one of three mirrors; `read()` takes the MAX deadline across mirrors |
| Lock — keychain item | login keychain, service `app.hisn.lock` | user | delete (`security delete-generic-password -s app.hisn.lock`) | mirror |
| Lock — "system" file | `/Library/Application Support/Hisn/lock.plist` | **whoever first ran the app** | delete/edit **if they were admin when it was created** (ownership survives demotion); otherwise cannot create it at all | mirror. The comment in `LockStore.swift` calls it root-owned; **it is not**, because the app writes it as the user and no privileged writer exists |
| Self-release first-seen records | app-group defaults | user | delete → the 48 h wait **restarts** (fail-closed) | cannot shorten a release |
| Time high-water mark | app-group defaults + mtime of the "system" file | user | delete | clock rollback protection weakens to the file's mtime |
| Hand lists (`allowlist`, `customBlocks`), inspection settings, user words | app-group defaults | user | edit freely **while unlocked**; the app refuses loosening writes while locked, but `defaults write` bypasses the app | the filter re-reads them every 30 s |
| Downloaded generation | `~/Library/Group Containers/group.app.hisn/list/` | user | delete → filter falls back to the bundled seed; replace with an older *signed* generation → refused while the persisted floor (`filterListVersion`) is higher, but that floor is itself in user defaults | rollback resistance is against the **network**, not the local user |
| Filter health keys | app-group defaults | user | forge | the status header can be lied to by the person it reports to |
| Extension state (`chrome.storage.local`) | browser profile directory | user | edit via devtools on the worker; `guardedUpdate` schema blocks the *message* path only | `appPresent`, `failClosed`, `lastHeartbeat` are reachable through the storage API from a devtools console |
| Native-host manifest | user scope `~/Library/Application Support/<browser>/NativeMessagingHosts/app.hisn.bridge.json`; system scope `/Library/…` (needs `sudo`) | user / root | delete the user-scope one → extension loses the app → **fails closed** to strict after 5 min | tamper here makes things stricter |
| Browser policy (force-install, incognito off, DoH off) | configuration profile, `PayloadScope: System` → `/Library/Managed Preferences` | root | nothing | only an admin can remove the profile, and it carries a removal password |
| Network filter preference | `NEFilterManager` (system) | root | nothing; disabling is in System Settings and prompts for admin | the app re-asserts on launch |
| System extension activation | `sysextd`, SIP-protected | root | nothing | `systemextensionsctl` needs admin, and full removal needs SIP off |
| Bundled seed | inside the app bundle, code-signed | root (in `/Applications`) | nothing as standard user | modifying it breaks the app's signature; the filter also verifies the manifest signature |

## What follows

### Standard user
Can **end a lock** by deleting the three mirrors — two of them trivially and
the third if they created it while admin. Can **loosen the hand lists** with
`defaults write`. Can **blind the status header**. Cannot remove the profile,
the filter, the extension, or the app, and cannot stop the filter from
enforcing whatever the mirrors say. So today the lock's *duration* is
redundant, not tamper-resistant; the *layers* are tamper-resistant.

This is the gap between what `THREAT_MODEL.md` row 7 promises ("Delete the
app's data — Yes") and what the code delivers. It is honest to say: row 7
holds against accidents and against `defaults delete` of one store; it does
not hold against a standard user who has read this document.

### Administrator
Everything above, plus: remove the profile (with the removal password), turn
the filter off in System Settings, deactivate or delete the system extension,
delete the app. `setup_guardian.sh` exists so that the daily user is not one.

### Recovery-capable user
Everything, including turning SIP off and erasing the disk. Closed by nothing
in software; only by the daily user not holding the recovery path alone.

## Assumption to verify first

Every "shared" store above assumes the filter (a **system** extension, which
runs as root) and the app (the user) resolve `group.app.hisn` to the **same**
container and the same defaults domain. On macOS, a root process's app-group
container can resolve under `/private/var/root/Library/Group Containers/`
rather than the user's. If that is what happens here, then in production:

* the filter never sees a lock, a hand list, or a downloaded generation — it
  enforces the bundled seed in blocklist mode, forever;
* `filterDomainCount` and the new health keys are written where the app never
  reads them, so the status header shows "no list" while the filter runs.

None of this is visible to unit tests. **Check it on a Mac with the filter
activated, before anything else in this document is acted on:**

```bash
# after starting a lock in the app, as the daily user:
defaults read group.app.hisn filterHeartbeatAt          # written by the filter, every 30 s
sudo defaults read /private/var/root/Library/Group\ Containers/group.app.hisn/Library/Preferences/group.app.hisn filterHeartbeatAt
```

Whichever of the two answers, that is where the filter lives. If it is the
second, the fix is architectural and is the next section.

## The smallest privileged policy service

If the assumption above fails — and even if it holds, to make the lock
*duration* tamper-resistant — the filter is already the privileged process,
and it should own the authoritative copy of policy:

* **Store**: a root-owned directory `/Library/Application Support/Hisn/`
  written **only by the filter** (uid 0), mode `755/644`. Contents: lock
  state, the hand lists, the installed generation's version floor.
* **Transport**: `NEFilterManager` gives the app an XPC channel to its own
  system extension (`NEMachServiceName` in the extension's `Info.plist`, an
  `NSXPCListener` in the provider). The app sends *requests*; the filter
  *validates* them against the same rules `SiteLists.write` and `LockStore.write`
  apply today (extend-only deadline, add-only blocks, remove-only allowances
  while locked) and writes the result. The bridge reads the same file.
* **Authentication**: the listener accepts connections only from a process
  whose code signature is the app's (`SecCodeCheckValidity` against a
  designated requirement with the team ID), so a `defaults write` or a rogue
  binary cannot ask.
* **No general execution**: the command set is exactly `setLock`,
  `requestRelease`, `cancelRelease`, `setLists`, `getStatus`. No paths, no
  shell, no file names cross the boundary.
* **Migration**: the filter keeps reading the three mirrors and takes the MAX,
  so an existing lock is honoured; new writes go to the root-owned store; the
  mirrors become a cache.

This is the one change that would turn "standard user can end a lock by
deleting three things" into "standard user cannot end a lock". It is not in
this pass because it needs the assumption above verified on real hardware
first, and it changes the process boundary the whole app is built around.

## Interim hardening that needs no service

* `setup_guardian.sh --demote-me` should `chown -R root:wheel
  "/Library/Application Support/Hisn"` if it exists, so the daily user does
  not keep ownership of the file they created while admin. The app's later
  writes will fail (as they already do for a user who was never admin) and
  the file becomes a frozen mirror of the deadline at demotion — which still
  cannot be shortened by the standard user. Not done in this pass because
  it changes an account-setup script this machine is not running.
* The extension's own storage is the user's, and no validation on read
  changes that: a well-formed `appPresent: false` written through the storage
  API is indistinguishable from the real thing. What limits the damage is
  that the browser layer is not load-bearing — the profile force-installs
  the extension and the Mac filter enforces the same policy without it.
  Stated plainly in `POLICY.md` and `SETUP.md` rather than papered over.
