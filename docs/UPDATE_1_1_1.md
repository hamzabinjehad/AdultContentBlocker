# Hisn 1.1.1 — local update

The extension uses Hisn's existing brand, Apple-style controls and adaptive
dark/light surfaces. English and Arabic layouts retain readable text, including
in narrow or short popups. This update preserves the native apps' existing UI
and protection settings; it does not replace their navigation or commitments.

## Install a tested Mac build

Build and test as the normal account. For a separately built, signed bundle:

```sh
macos/install.sh --built-app /absolute/path/to/Hisn.app
```

The installer requests administrator permission before stopping the installed
app. It preserves the existing protected account and login agent, validates the
staged app, and retains the old bundle under
`/Library/Application Support/Hisn Updates/`. It does not reset lock settings,
install a DNS/management profile or change account roles. A failed replacement
attempts to restore the previous app.

The app, filter and bridge must be updated together. A signed installed app
cannot be replaced by a different signing team or an ad-hoc downgrade. An
ad-hoc local build does **not** activate the system network filter; that requires
appropriate Apple signing and entitlement approval.

## Reload each browser extension

An unpacked extension does not automatically reload when its files change.
Use the browser's Extensions page to reload Hisn. Verify **1.1.1** in its details
before testing the popup.

If loading fails because of a browser-created `_metadata` directory, prepare a
fresh folder separately for each browser:

```sh
python3 extension/prepare_unpacked.py --out dist/hisn-unpacked-helium-1.1.1
python3 extension/prepare_unpacked.py --out dist/hisn-unpacked-chatgpt-1.1.1
```

Use **Load unpacked** with the appropriate folder. Keep the original extension
identity to preserve native messaging. Do not uninstall Hisn to refresh it:
uninstalling can remove the extension's own preferences. Each browser retains
its own permissions and settings. Browser support and managed removal
restrictions require verification in that browser.

## GitHub publication prerequisite

Daily signed-list publication requires the original Ed25519 private key as
`BLOCKLIST_SIGNING_KEY` in GitHub's `list-signing` environment. The tracked
public key cannot produce signatures. Do not put private keys in source,
issues, pull requests or logs. Changing the trusted public key requires a
separate coordinated migration; generating a replacement is not an update fix.

## Limits

Visual checks remain opt-in, local and experimental. Some media cannot be read,
sampled video frames are not comprehensive coverage, and appearance preferences
can miss faces. Ordinary extensions remain removable; administrator recovery
remains possible. Simulator compilation is not physical Safari verification.
