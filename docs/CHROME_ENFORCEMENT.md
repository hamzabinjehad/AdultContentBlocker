# Forcing the extension on Chrome/Edge

The browser extension is the only layer that reads **page text** — the socket
filter sees hosts and flows, never content. So catching adult material inside a
general site (X, Reddit) or on a domain registered today, un-removably, means
forcing the extension. On macOS that is possible for Chrome/Edge and **not** for
Safari without supervised MDM (see `THREAT_MODEL.md`). This is the Chrome path,
in order.

Every step depends on the one before it. Do them in this sequence or the result
is an extension that looks installed and enforces nothing.

## 0. Prerequisite: the account split

None of this is a lock until the person under it is a **standard user** and a
second person holds admin — `macos/setup_guardian.sh`. A managed profile keeps
the extension un-removable only for as long as the profile itself cannot be
removed, and removing a profile needs admin. Skip this and everything below is a
setting the user can undo in a minute.

## 1. Package the extension

```bash
extension/package.sh          # -> dist/hisn-extension-<version>.zip
```

Strips the private key, the `key` field, `_comment_*` keys, and `test/`. The zip
is what you upload; it is verified to contain no `.pem` and a clean manifest.

## 2. Publish to the Chrome Web Store

Upload the zip at <https://chrome.google.com/webstore/devconsole> (one-time $5
developer registration, separate from Apple's $99). It goes through review.

**THE gotcha, and it is a silent one.** The Web Store assigns the extension its
own id and ignores the manifest `key`. So the published extension's id is **not**
`hfhaffbmoeepcdolgejeidkgaoapcjig` — that id belongs to the local unpacked key.
Read the real id from the dashboard after upload. Everything downstream that
names an id must use the store id, or the browser and the app stop talking with
no error anywhere visible.

## 3. Wire the store id into the two places that hardcode one

* **Native messaging** — add the store id to `extensionIDs` in
  `macos/Hisn/NativeMessagingInstaller.swift`, then rebuild the app. The list
  already carries the local id; adding the store id lets one app build serve
  both the unpacked and the published extension. Miss this and `pollNative()`
  fails forever: the extension never hears the lock state and fails closed to
  strict.

* **The force-install profile** — pass the store id when generating it:

  ```bash
  python3 profile/make_profile.py --resolver cloudflare \
      --extension-id <STORE_ID> \
      --out dist/hisn-hardening.mobileconfig --print-password
  ```

  This emits `ExtensionInstallForcelist` (force-install by id from the store)
  plus `ExtensionSettings` with `"*": blocked`, for Chrome, Edge **and every
  Chromium fork in the family — Helium included**. The extension cannot be
  disabled or removed, and no other extension can be installed to proxy around
  it. Save the printed removal password; give it to the second person, never the
  user.

  **Helium (`net.imput.helium`), verified and still to verify.** Its framework
  is Chromium 152 with the full enterprise-policy engine — `ExtensionSettings`,
  `ExtensionInstallForcelist`, `IncognitoModeAvailability`, `DnsOverHttpsMode`
  are all compiled in — so the profile's DNS lock and incognito block will hold
  there, and the force-install policy is now written into its payload. Two
  things need a real install to confirm and cannot be checked from the binary:
  (1) that Helium reads managed policy from
  `/Library/Managed Preferences/net.imput.helium.plist` (standard Chromium
  behaviour, but confirm via `helium://policy` after installing the profile),
  and (2) that Helium's force-install accepts the Chrome Web Store update URL —
  if it uses a different extension source, the `update_url` needs changing for
  its payload.

  Do **not** install this profile until the extension is actually live on the
  store. Before then, the force-install fails and `"*": blocked` stops even the
  unpacked copy from loading — you get no extension at all. `make_profile.py`
  with no `--extension-id` deliberately omits this whole block for exactly that
  reason, and a test pins the behaviour.

## 4. Install the profile, as the second person

System Settings → General → Device Management → install
`dist/hisn-hardening.mobileconfig`. It also locks DNS-over-HTTPS across eleven
Chromium browsers and disables iCloud Private Relay — the network bypasses a
blocklist cannot reach.

## 5. Verify it actually took

```bash
macos/verify_enforcement.sh
```

Read-only. It reports which layers are genuinely live — account split, hosts,
Chrome incognito, Chrome DoH, Private Relay, the system filter — and exits
non-zero if any critical one is open. Run it as the second person after setup:
the whole point of this product is that "looks protected" and "is protected"
can differ, and this is where you catch the difference.

**Incognito is the row people miss, and there are two ways to close it.**
The extension is `incognito: spanning`: it *can* run in a private window, but
the browser keeps it off there until the user turns on "Allow in Incognito" —
so the popup detects that and asks. That path depends on the user opting in,
which a determined person will not.

The enforceable path is to remove private browsing altogether:
`make_profile.py` sets `IncognitoModeAvailability = 1` by default (and
`DisablePrivateBrowsing` for Firefox), so there is no private window to slip
through. Pass `--allow-incognito` only if you deliberately want incognito to
exist and to rely on the opt-in extension coverage instead. Until the profile
is installed, `verify_enforcement.sh` reports this row OPEN, and it is.

## What this gets you, and what it still does not

Enforced: the extension cannot be disabled or removed, no rival extension can be
installed, incognito is gone (so it cannot be used to escape the extension), DoH
is off, page text is scanned against the ~5,800-term list including the 5,547
Arabic terms.

Still open, honestly: an admin removing the profile (closed only by the account
split), Recovery mode (`THREAT_MODEL.md` row 13), and another device (row 15).
The profile narrows the browser gap to a list of known Chromium forks; a fork
published tomorrow needs adding by hand. The socket filter — which never asks
which browser opened a flow — is the layer that closes that class for good, and
it needs Apple's $99 and the same account split to be un-disableable.
