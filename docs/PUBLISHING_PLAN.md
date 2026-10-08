# Hisn publishing plan

Last reviewed: 2026-10-01.

This plan turns the current repo into a public release candidate. It assumes the
first public step is a closed or unlisted beta, not a fully marketed launch.

## Current state

The project is already well structured for release:

- `blocklist/` builds signed list artifacts, applies safety rails, and has
  publish guards against silent list collapse.
- `extension/` is a Chrome/Edge Manifest V3 extension with packaged store
  output, native app integration, signed list verification, real-browser tests,
  Arabic UI, and fail-closed behavior.
- `macos/` contains the app, system content filter, native messaging bridge,
  installer scripts, setup verifier, root-owned filter authority, and a large
  Swift test suite.
- `profile/` builds the hardening profile for browser policy, DNS-over-HTTPS,
  Private Relay, SafeSearch, private browsing, and native messaging constraints.
- `.github/workflows/` gates pull requests and `main`, then builds and publishes
  signed list updates from a protected `list-signing` environment.

Local checks passed on 2026-10-01:

- `./test.sh python seed extension`
- `./test.sh browser`
- `./test.sh macos`

## Do not publish yet

Implementation progress on 2026-10-01:

- Added the DeveloperID configuration and distribution entitlements, while
  keeping development signing in Debug and Release.
- Added archive/export, installer packaging, notarization commands, and checks
  of the actual bundle. An unsigned universal package has built and passed
  inspection; signed installation and notarization are still untested.
- Added direct filter enable/approval/restart feedback and setup actions,
  a five-minute initial lock choice, and browser store-link configuration.
- Fixed pending-restart activation, concurrent activation requests, legacy
  user-scope browser links, and daylight-saving schedule endpoints.
- Added release tests, regression coverage, USER_GUIDE.md, RELEASE_RUNBOOK.md,
  and RELEASE_VALIDATION.md.
- Confirmed the current public list manifest returns HTTP 404 and this Mac
  lacks a Developer ID Application identity.

Use RELEASE_RUNBOOK.md for the implemented workflow. The blockers below remain
release gates until their acceptance criteria have real evidence.

The code is not ready for a public release until these ship-blockers are closed:

1. Run the paid-team hardware checks in `docs/TAMPER_MODEL.md`.
   The most important check is that the system filter's root-owned authority is
   actually reachable, writable, and persistent on a real signed install.

2. Build a real Developer ID distribution path.
   The debug/development entitlement uses `content-filter-provider`; Developer
   ID distribution needs a release entitlement value of
   `content-filter-provider-systemextension` for the app/filter pair.

3. Produce a signed, notarized installer package.
   The public artifact should install the app into `/Applications`, install the
   LaunchAgent, install the admin-owned native messaging host, and optionally
   start the existing profile/setup flow.

4. Add an app updater before public launch.
   The docs already call this out: a content filter without an app update path
   is difficult to support after the first bug fix.

5. Resolve list-source licensing.
   `blocklist/sources.json` includes UT1 sources marked non-commercial and
   hagezi NSFW as GPL-3.0. Decide whether the first public release is free,
   get permission, or remove/replace sources before any paid offering.

6. Publish the browser extension as an unlisted beta and wire the store ID.
   The packaged extension strips the local development key, so the store ID must
   be added to the native messaging installer and the force-install profile.

7. Publish a privacy policy and support page.
   The product promise is local-only enforcement. The public pages should say
   exactly what data stays on device, what list-update requests reveal, and how
   partner release/support works.

8. Have a second person set it up from the docs alone.
   This is the only test that proves the product is understandable outside the
   author/developer loop.

## Release tracks

### Track 1: macOS signing, packaging, and install

- Enroll or use an Apple Developer Program team.
- Create identifiers for:
  - `app.hisn.Hisn`
  - `app.hisn.Hisn.HisnFilter`
  - `group.app.hisn`
  - `<TEAMID>.app.hisn`
- Request/confirm Network Extension and System Extension capabilities.
- Add release-only entitlements for Developer ID distribution.
- Add an archive/export script that produces a Developer ID app bundle.
- Add a package build script that creates a `.pkg` whose install steps match
  `macos/install.sh`.
- Notarize and staple the package.
- Test the package on a clean macOS account or VM.

Acceptance criteria:

- A clean machine can install from the `.pkg`.
- `macos/verify_enforcement.sh` shows the expected enforced layers.
- The app bundle and LaunchAgent are root-owned after install.
- The system extension can be enabled from `/Applications/Hisn.app`.

### Track 2: system filter hardware validation

Run and record the manual checks in `docs/TAMPER_MODEL.md` and
`docs/TEST_MATRIX.md`:

- `FilterSync.status` returns non-nil.
- The filter row shows the filter's own domain count.
- The filter's container is writable under root's container path.
- A lock started in the app reaches the filter within about one second.
- Deleting the app's user-owned mirrors does not end a filter-backed lock.
- Restart, sleep/wake, VPN, alternate-browser, and clock-change checks behave as
  documented.

Acceptance criteria:

- Results are written back into `docs/TAMPER_MODEL.md` or a dated release note.
- Any bypass that needs no administrator rights is fixed before beta.

### Track 3: browser extension store beta

- Run `extension/package.sh`.
- Upload the package to Chrome Web Store as unlisted first.
- Fill privacy practices as no data collected, if that remains true.
- Explain why `<all_urls>`, `nativeMessaging`, `scripting`, and DNR permissions
  are required.
- Add the assigned Chrome Web Store ID to `NativeMessagingInstaller.swift`.
- Rebuild the hardening profile with the store ID.
- Repeat for Edge Add-ons when Chrome is stable.

Acceptance criteria:

- Store-installed extension connects to the app.
- Native messaging works with the store ID, not only the local unpacked ID.
- The profile force-installs the store extension and keeps required browser
  policies locked.

### Track 4: list publishing

- Make the repository public, or move list artifacts to a public release host.
- Create the `list-signing` environment and store `BLOCKLIST_SIGNING_KEY` there.
- Confirm the environment only admits `main`.
- Run the publish workflow once on `main`.
- Confirm `https://raw.githubusercontent.com/<owner>/<repo>/lists/manifest.json`
  returns `200`.
- Keep the private key offline outside CI.

Acceptance criteria:

- Clients can download, verify, and install a newer signed list.
- The publish guard compares against the previous published manifest.
- A failed source, small list, or missing Arabic term layer blocks publication.

### Track 5: onboarding and support

- Turn `docs/SETUP.md` into an in-app guided setup checklist.
- Keep showing status from real evidence, not only intended settings.
- Add public pages for:
  - privacy policy
  - support
  - setup help
  - "why a locked app cannot simply turn off"
- Add a release-mode first-run flow that points the user to partner/account
  setup before long locks.

Acceptance criteria:

- A non-developer can install, connect the browser extension, run verification,
  and understand what remains open.
- Support copy never implies the software can solve second-device or
  administrator/recovery access by itself.

### Track 6: updater

- Add Sparkle 2 or another signed macOS updater suitable for Developer ID apps.
- Host an EdDSA-signed appcast.
- Decide whether the updater replaces only the app or also runs privileged
  helper/package steps when the system extension or native host changes.
- Add a rollback/test plan for bad updates.

Acceptance criteria:

- A signed release can update to a newer signed release.
- A failed update does not remove the current working protection.
- The update path is documented for support.

## Suggested code work, in order

- [x] Add release entitlements and archive/package scripts.
- [x] Add RELEASE_RUNBOOK.md with commands and manual checks.
- [x] Add the RELEASE_VALIDATION.md hardware-validation record template.
- [ ] Add signed app-update plumbing and test a real beta update.
- [ ] Finish profile setup and validate onboarding with a non-developer.
- [x] Add a store-ID and store-link configuration path shared by the app and installer.
- [x] Add CI/package checks for distribution entitlements and bundle components.

## Public beta gate

Start an unlisted beta only when all of these are true:

- Automated tests are green in CI on the release commit.
- The macOS package installs cleanly on a clean machine.
- The store extension connects to the installed app.
- The system filter hardware checks are recorded.
- The list update channel is public and signed.
- Licensing decisions are recorded.
- Privacy and support pages are live.
- One person other than the developer has completed setup from instructions.

## Public launch gate

Move from beta to public only after:

- At least one dogfooding cycle has no release-stopping `BYPASS`, silent
  `BREAK`, repeated false browser-guard closures, major false-positive pain, or
  multi-day list-update failure.
- The updater has successfully delivered at least one beta update.
- The support flow has handled at least one install/debug case without requiring
  developer-only knowledge.
