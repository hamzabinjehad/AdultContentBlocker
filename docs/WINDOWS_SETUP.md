# Windows: making the extension hard to remove

Hisn has no Windows app. On Windows the protection is the browser extension,
held in place by browser policy, plus an administrator password the person does
not hold. This guide sets that up with `profile/make_windows_policy.py`.

## What it does

The generator writes the same browser policy as the Mac profile
([make_profile.py](../profile/make_profile.py)) as two registry files:

| File | Who uses it | What it does |
|---|---|---|
| `hisn-windows-policy.reg` | Administrator, once | Force-installs the extension and locks the browser |
| `hisn-windows-policy-remove.reg` | Whoever holds the administrator password | Removes exactly those values, nothing else |

For Chrome, Edge and Brave it sets:

- **Force-install** (`ExtensionInstallForcelist`, `ExtensionSettings`): Remove
  and Disable are greyed out, and the browser reinstalls the extension if its
  files are deleted. Every other extension is blocked.
- **No private, guest or new-profile windows**, where the extension may not run.
  Edge's InPrivate and Brave's Tor windows are closed too, and Brave's VPN is off.
- **No DevTools**, which can change extension state.
- **Browser DNS and proxy locked** to the system's, so a filtering resolver
  set on the PC or router is not bypassed by the browser.
- **SafeSearch and YouTube restricted mode** at the policy level.

Firefox gets DNS, proxy, private-window and DevTools locks only. There is no
Hisn Firefox extension, so block Firefox (see step 5) if it is installed.

## Steps

1. **Get the store ID.** Publish the extension to the Chrome Web Store (unlisted
   is enough). On a PC that is not joined to a domain or enrolled in MDM, Chrome
   and Edge force-install only from a store; a self-hosted update URL is ignored.
2. **Generate the files** on any computer:
   ```bash
   python3 profile/make_windows_policy.py --extension-id <store-id> --out dist/hisn-windows-policy.reg
   ```
   Use `--browser chrome` (repeatable) to limit it to some browsers.
3. **Split the accounts.** The person's Windows account must be a *standard*
   account. A second person, or a time lock, holds the only administrator
   password. Without this, everything below can be undone in a minute.
4. **Apply.** Signed in as the administrator, double-click
   `hisn-windows-policy.reg` and confirm. Restart each browser.
5. **Close the side doors.** Uninstall browsers you are not covering, or block
   them with Microsoft Family Safety or another admin-controlled app block.
   Set a filtering DNS resolver on the router or the PC (see
   [router compatibility](ROUTER_COMPATIBILITY.md)).
6. **Check it worked.** Open `chrome://policy` (`edge://policy`,
   `brave://policy`): every policy must show *Status: OK*. Then, as the standard
   user, confirm that `chrome://extensions` shows Hisn as "installed by your
   administrator" with no Remove button, and that Ctrl+Shift+N does nothing.
7. **Hand over the remove file** to the person who holds the administrator
   password, and delete it from the protected account.

## Limits

- Anyone with the administrator password can remove the policy. This setup is
  as strong as the hands that password is in ([threat model](THREAT_MODEL.md)).
- Resetting or reinstalling Windows removes it.
- A browser the policy does not cover, a portable browser run from a USB stick,
  or another device is outside it. Step 5 narrows this; it does not close it.
- Generating or merging the file is not proof a browser enforces it. Step 6 is.
- Not yet tested on a real Windows PC by this project. Edge's acceptance of a
  Chrome Web Store force-install on an unmanaged PC in particular must be checked.
