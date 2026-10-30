# Hisn extension 1.2.1 — store-ready metadata and an honest footer

Development candidate, 30 October 2026. Still an unpacked build; no Chrome Web
Store listing exists. The packaged zip is store-clean, but publication needs the
owner's $5 developer account and a store review.

## What changed

- **Manifest metadata.** Added `short_name` and `homepage_url`, and bumped the
  version to 1.2.1. The local development key is still present in the working
  manifest and is stripped by `package.sh`; the published ID will differ.
- **Options footer.** Settings now states that matching stays on the device, shows
  the installed extension version, and links to the repository privacy policy and
  support page. It also says plainly that a browser policy can make the extension
  non-removable for a standard user, and that Hisn cannot detect that from the
  page. The footer is not shown in the compact popup.
- **Store-readiness record.** [EXTENSION_STORE.md](EXTENSION_STORE.md) contains
  the listing copy, category, permission justifications, privacy practices,
  managed-install policy, reviewer test steps and the post-publication ID wiring
  checklist.

## Verification

On 30 October 2026:

- `extension/test/run.sh`: all JavaScript suites pass, including the
  English/Arabic key and placeholder parity test.
- `./test.sh extension`: unit suites, packaged-zip check and the evaluation
  corpus pass.
- `./test.sh browser` (settings page harness) in Helium 0.20.2.1: the settings
  page, popup, block page, DNR/SafeSearch and scanner harnesses pass. The
  settings harness now asserts the footer version, privacy/support links and
  managed-removal wording.
- `package.sh`: the staged zip contains no `key`, test files, PEM or `_comment`
  keys.

## Package artifact

`extension/package.sh` produced `dist/hisn-extension-1.2.1.zip`:

| Field | Value |
| --- | --- |
| Version | 1.2.1 |
| Size | 5.3 MB |
| Files | 90 |
| SHA-256 | `0033f0df70d6930584c31a3e6e946a50b5be4e35205771380dee4e376cdb82bb` |

The zip contains no `key`, PEM, test file or `_comment_*` key. It is not signed
by the store; the store assigns its own ID and signature.

## Limits

- No store listing, real store ID, review result or managed force-install test is
  claimed. `EXTENSION_STORE.md` is the input for those owner actions.
- The extension cannot tell whether policy installed it without a broad
  `management` permission; the footer says policy controls removal but does not
  claim to verify it.
- A deleted or disabled unpacked extension enforces nothing. The Mac filter and
  DNS remain the independent layers; the managed-policy path is the only way to
  stop a standard user removing it.
- The policy/support links point at the public repository, not a hosted page.
- The visual engine remains opt-in and experimental, and a listed domain is not a
  claim to block every path or every site.

## Publication checklist still open

- [ ] Chrome Web Store developer account and one-time fee.
- [ ] Upload the `dist/hisn-extension-1.2.1.zip` from `extension/package.sh`.
- [ ] Fill the listing from `EXTENSION_STORE.md`, including the privacy tab.
- [ ] Read the assigned store ID and wire it into the native-messaging installer,
      Mac profile generator and Windows policy generator.
- [ ] Publish unlisted first; test install, update, disable/remove, incognito and
      the native-messaging link in a clean Chromium profile.
- [ ] Add the managed `ExtensionSettings` policy and verify a standard user cannot
      disable or remove it.
