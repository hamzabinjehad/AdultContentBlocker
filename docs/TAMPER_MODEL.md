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

## The root-owned authority (built 2026-09-25)

The filter is a system extension, and system extensions run as root, so its
`group.app.hisn` defaults and container resolve under `/private/var/root` —
not the user's. Everything above that says "app-group defaults" was therefore
invisible to the filter on a real install: it enforced the bundled seed in
blocklist mode, forever, and its health keys landed where the app never read
them. That is how system extensions work, so the design no longer depends on
the shared container at all:

* **Store.** `PolicyService` (`macos/Hisn/PolicyAuthority.swift`) keeps the
  lock, the hand lists, the user's words and apps, the inspection settings,
  the clock high-water mark and the list rollback floor in the filter's own
  sandbox container under root's home, as two alternately-written copies so
  one torn write cannot end a lock. A standard user can neither read nor
  write it.
* **Transport.** The filter listens on the Mach service named in its
  Info.plist (`NEMachServiceName`, prefixed with the team app group); the app
  and the bridge connect with `.privileged` (`FilterXPC.swift`). Every value
  crosses as JSON.
* **Authentication.** Both ends call `setCodeSigningRequirement` with
  "Apple-issued, same team", taken from their own signature at run time. An
  unsigned build trusts nobody.
* **No general execution.** The command set is `status`, `submit` (one of
  `proposeLock`, `setLists`, `setUserBlocks`, `setInspection`) and
  `installGeneration` (re-verified in full by the filter before a byte is
  written).
* **Rules.** Exactly the mirrors' rules, from the same functions:
  `LockStore.refusal`, `SiteLists.loosening`, `UserBlocks.loosening`,
  `Inspection.loosening`. Extend-only deadline, strict stays strict, release
  cannot be accelerated and waits from when the *authority* first saw it,
  add-only blocks, remove-only allowances.
* **Two copies, one answer.** `FilterSync` (app, every 20 s and after every
  change) and the bridge (every heartbeat) act on `PolicyMerge.stricter` of the
  app's mirrors and the authority's record. Deleting the mirrors mid-lock gets
  them written back; forging an allowance into them is dropped by the merge;
  a lock recorded before the authority existed is handed over on first sync.

* **The app never loosens on its own word.** Its mirrors are the user's
  files and their clock a key in the user's defaults, so switching the
  filter off, letting the app quit and standing the browser guard down all
  ask `EffectiveLock` (mirrors OR authority), and switching the filter off
  also needs the filter's own answer that no lock runs. The merge judges
  each copy by its own clock (`PolicyView.locked`), so a forged app clock
  cannot make the authority's lock look over. (All from the 2026-09-25
  audit; `AuditRegressionTests` has one test per finding.)
* **Guard evidence.** The browser guard's allowed apps live in the record
  too (add only while unlocked), and each extension check-in reaches the
  filter from the bridge over the code-signed channel; while the filter
  answers, a `defaults write` of either in the app's copy changes nothing.

What this changes in the table above: **Lock (all three mirrors), hand lists,
words, apps and inspection settings** become tamper-resistant against a
standard user *whenever the filter is installed* — the mirrors are now a cache.
The browser extension's own storage and the filter health keys in defaults are
unchanged, and still not trusted.

### Still to verify on hardware (needs the paid team)

* The listener registers and the app connects (`FilterSync.status` non-nil;
  the Overview's filter row shows the filter's own domain count).
* `startFilter` finds its container writable and the record survives a
  reboot (`sudo ls /private/var/root/Library/Containers/app.hisn.Hisn.HisnFilter/Data/Library/Application\ Support/Hisn/`).
* A lock started in the app is enforced by the filter within a second
  (`onChange`), and deleting the app's three mirrors does not end it.

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
