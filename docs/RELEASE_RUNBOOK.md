# Hisn Release Runbook

This repository now has a Developer ID build configuration and a package
pipeline. It has passed an unsigned universal build and package inspection.
That does not establish that Apple signing, notarization, or installation on a
clean Mac works. Record those results before distributing a beta.

## Current External Blockers

Checked on 2026-10-01:

- No Developer ID Application signing identity was found on the development Mac.
- HisnChromeStoreURL and HisnEdgeStoreURL are not configured.
- The unauthenticated list manifest request at
  `https://raw.githubusercontent.com/hamzabinjehad/AdultContentBlocker/lists/manifest.json`
  returned HTTP 404. Make the list channel publicly readable and verify signed
  updates from a fresh account.
- List licensing, a signed app updater, privacy/support pages, and signed
  hardware validation remain open in PUBLISHING_PLAN.md.

## Prepare

1. Use a paid Apple team with Developer ID Application and Developer ID
   Installer certificates and the Network Extension/System Extension capabilities.
   Include the team-prefixed application group in the provisioning profiles.
2. Publish the browser extension beta. In `macos/Hisn/Info.plist`, add its exact
   ID to HisnExtensionIDs and its HTTPS store link as HisnChromeStoreURL or
   HisnEdgeStoreURL. The link's last path component must match an admitted ID.
   Use the same store ID when generating the protection profile.
3. Bump MARKETING_VERSION and CURRENT_PROJECT_VERSION for the app and filter
   in `macos/generate_xcodeproj.py`, then regenerate the Xcode project. Every
   update must increase the build number. Keep old signed installers and archives.
4. Run the tests:

```bash
./test.sh python seed extension release macos browser
```

`release` checks the generated project's selected entitlements and runs package
validation tests. Debug and Release remain development configurations.
DeveloperID uses the distribution entitlement
`content-filter-provider-systemextension` and hardened runtime.

## Inspect An Unsigned Package

```bash
bash macos/release/release.sh --smoke /tmp/hisn-release-check
```

This compiles both Intel and Apple silicon, builds a package, expands it, and
checks the expanded app's components and translations. It never installs or
activates anything. Hisn-UNSIGNED-TEST.pkg must never be distributed as a release.

## Build And Notarize

Set these variables to the real team and certificate names in your environment:

```bash
export HISN_TEAM_ID='YOURTEAMID'
export HISN_INSTALLER_IDENTITY='Developer ID Installer: Your Name (YOURTEAMID)'
bash macos/release/release.sh --build
```

The script archives, exports, validates the actual signatures and entitlements,
and builds `dist/release/Hisn.pkg`. It checks all three executables are universal
and the app, bridge, and filter share the same signing team and application groups.
The installer pins the app to Applications and uses replacement rather than
leaving old bundle files behind. Signing failure stops the pipeline.

Store notarization credentials using Apple's `notarytool store-credentials`;
keep them in Keychain rather than the repository. Then:

```bash
export HISN_NOTARY_PROFILE='your-keychain-profile'
bash macos/release/release.sh --notarize
```

The script submits the signed package, requires an Accepted result, staples the
ticket, validates it, and runs Gatekeeper assessment. This is an explicit separate
step because it sends the artifact to Apple. No upload happens during tests or
the local smoke build. Preserve notarization.json and the archive/export logs.

## Verify Installation

Run these checks on a clean Mac or disposable test account, with the signed,
notarized package:

1. Install while signed in to the protected account. Supply administrator
   credentials when asked. Check /Applications/Hisn.app is root-owned.
2. Open the app and complete setup without developer tools. Confirm the filter
   reaches its own authority; an enabled configuration alone is insufficient.
3. Install the store extension and verify its connection in each browser profile.
   The package installs administrator-owned native messaging manifests for the
   supported Chromium browsers. App launch removes older user-scope Hisn copies.
4. Start the five-minute trial. Test an approved harmless blocked test domain,
   an allowed site, lock expiry, app window close, and browser restart.
5. Run the signed hardware cases in TAMPER_MODEL.md and TEST_MATRIX.md, including
   sleep/wake, restart, policy persistence, and partner release.
6. Install the next beta over the first, while signed in as a different
   administrator. Check the existing protected account is preserved and the
   previous lock remains enforced.
7. Test a failed list download and a later successful signed update.
8. Have a person unfamiliar with the code follow USER_GUIDE.md. Record any step
   requiring developer help. Do not call onboarding complete while those remain.

The package creates a root-owned login agent for the protected account. A fresh
install requires someone signed in; an update retains the existing agent's
account. It does not change account roles, install a removal-password profile,
or set a Screen Time passcode automatically.

Record each result in RELEASE_VALIDATION.md. An unsigned build is evidence of
compilation and package structure only. Keep every untested signed/device case
marked pending.

## References

- [Apple Network Extension entitlements](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.networking.networkextension)
- [Apple notarization](https://developer.apple.com/documentation/security/notarizing-macos-software-before-distribution)
