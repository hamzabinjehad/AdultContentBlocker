# Connecting the browser extension to the Hisn app

The extension can run alone, but the Mac app is the authority for the lock
clock, the app-owned site lists and the browser guard. The two talk over
Chromium **native messaging**: the extension calls
`chrome.runtime.sendNativeMessage("app.hisn.bridge", {type: "getLockState"})`,
and the app's `HisnBridge` executable replies. A silent bridge during a lock is
treated as tampering, so the extension fails closed to Strict.

## Why there are two extension IDs

| How it is loaded | Extension ID | Where it comes from |
| --- | --- | --- |
| Unpacked from `extension/` | `hfhaffbmoeepcdolgejeidkgaoapcjig` | the pinned `key` in `manifest.json` |
| Chrome Web Store | assigned by the store | the manifest `key` is stripped by `extension/package.sh` |

The store ID is different, and native messaging requires an exact ID. The app
therefore reads `HisnExtensionIDs` from its `Info.plist`; a shipping build must
list the store ID there as well as the development ID. The native-host installer
accepts both:

```sh
sudo macos/install_native_host.sh \
  --extension-id hfhaffbmoeepcdolgejeidkgaoapcjig \
  --extension-id <store-id>
```

`macos/install_native_host.sh --user` is for development only. A user-level
manifest is a file the protected account can edit or delete; the system
manifest lives under `/Library` and requires an administrator.

## Check the connection

```sh
python3 macos/check_extension_bridge.py
```

The check is read-only. It verifies the app and `HisnBridge`, every installed
Chromium browser's `app.hisn.bridge` manifest, and a live `getLockState` reply.
`--json` is for automation. A failure names the exact installer command.

If the extension still says **App offline** after the check passes:

1. Confirm the browser loaded the extension from this checkout (or the published
   store build), not an older copy with a different ID.
2. In the extension's **Details**, confirm it is enabled and allowed in the
   profile you are using.
3. Restart the browser after installing or changing the native-host manifest;
   Chromium caches host manifests per process.
4. Check `chrome://policy` (or the fork's equivalent) if a managed policy is in
   place. A policy file on disk is not proof the browser accepted it.

## What "undeletable" means for an extension

An unpacked extension loaded with **Load unpacked** is always removable from
`chrome://extensions`. No extension code can change that, and a page cannot
grant itself enterprise management.

The only supported way to stop an ordinary user removing or disabling the
extension is **managed browser policy**:

```sh
python3 profile/make_profile.py \
  --browser chrome \
  --extension-id <published-store-id> \
  --out dist/hisn-browser.mobileconfig
```

The profile sets `ExtensionInstallForcelist` and `ExtensionSettings` with
`installation_mode: force_installed`, pins the toolbar button, blocks every
other extension, and (by default) disables Incognito, guest windows, new
profiles and DevTools. On a Mac where the daily account is a **standard** user
and the second person holds the administrator password, the user cannot remove
or disable it. The browser reinstalls it from the store if its files are
deleted.

Requirements and limits:

- The extension must be **published to the Chrome Web Store** (unlisted is
  enough) for force-install by ID. On a Mac that is not joined to a domain or
  enrolled in MDM, Chrome ignores a self-hosted update URL for this purpose.
- The profile must be installed by an administrator. Apple's profile removal
  password can be held by the second person; the profile itself is not a
  deletion lock.
- Anyone with the administrator password, recovery mode or a factory reset can
  remove the policy. MDM can make removal harder, not impossible.
- Incognito **spanning** is not the same as **allowed in Incognito**. The
  profile disables private browsing by default because an extension that is
  not allowed there cannot protect it; the popup reports the other case.
- The Mac app's browser guard closes a browser whose extension stops checking
  in during a lock. That is detection and friction, not prevention.

So: a **managed** extension can be made non-removable for a standard user; an
unpacked extension cannot. This page uses "managed", not "undeletable".

## Related

- [Chrome enforcement](CHROME_ENFORCEMENT.md) — supported browsers, policy
  verification and the remaining profile/window limits.
- [Supervised iPhone/iPad](IPHONE_SUPERVISED.md) — the equivalent ladder on
  Apple's mobile devices.
- [Tamper model](TAMPER_MODEL.md) — who can undo what, including administrators
  and recovery.
