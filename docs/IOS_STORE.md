# App Store readiness — Hisn iPhone & iPad 1.1.4

Repository-side record for a TestFlight/App Store submission. It is not a
listing and does not claim App Review approval. No Apple team or Family Controls
entitlement exists yet.

## Build and entitlement

- Bundle: `app.hisn.mobile`, version **1.1.4 (5)**.
- The app and `HisnActivityMonitor` need the **Family Controls distribution
  entitlement**. The Safari blockers and `HisnText` are app extensions; the
  activity monitor has its own entitlement.
- The app uses Screen Time / DeviceActivity / ManagedSettings through Apple's
  `FamilyControls` framework. There is no private API, no hidden profile and no
  MDM requirement for the personal modes.
- The supervised profile is generated on a Mac by
  `profile/make_ios_profile.py` and installed with Apple Configurator. It is not
  embedded in the app or used to bypass App Review.

## Listing copy

**Title:** Hisn — Adult Content Blocker

**Subtitle (30 chars):** Screen Time, Safari and focus

**Promotional text:**

> Block adult domains in Safari, limit the apps you choose and keep a date you
> cannot quietly move. On-device matching; no account and no browsing log.

**Description:**

> Hisn adds Arabic-aware adult-content protection beside Apple's Screen Time
> controls. Four Safari blockers load the signed core list, a Safari text
> extension hides detected posts and pages, and optional local visual checks
> blur media without uploading it.
>
> You can choose Screen Time app limits or a daily budget, keep a fixed
> commitment that refuses to shorten, or seal a Screen Time passcode until a
> date you choose. A supervised device can use Hisn's configuration profile for
> the strongest lock. Every mode states its exit and what it cannot stop.
>
> **Privacy:** matching stays on the device. Hisn has no account, no analytics
> and no server. The only routine network request is an HTTPS date check from
> apple.com for the time lock; the DNS layer, if you enable it, uses Cloudflare
> Family DNS under its own policy.
>
> **Limits:** no filter catches all adult content. Unreadable media, other
> browsers, another device, a Screen Time access revocation, the Apple Account
> passcode reset or a device erase can bypass a layer. Hisn shows the level it
> can actually attest and says when it is only confirmed by you.

**Keywords:** adult blocker, porn blocker, screen time, self control, Arabic,
Safari filter, focus, accountability.

**Category:** Productivity (alternative: Health & Fitness). Age rating: 4+;
the app blocks adult content but does not display it.

## Privacy label

- **Data collected: none.**
- **Tracking: no.**
- **Data linked to you: none.**
- **Data not linked to you: none.**
This matches [PRIVACY.md](../PRIVACY.md) and
[PrivacyInfo.xcprivacy](../ios/Hisn/PrivacyInfo.xcprivacy). The privacy manifest
declares only required reasons: app/app-group `UserDefaults` and the monotonic
boot clock.
- The policy and support links in Settings point at the repository pages; host
  them at stable public URLs before submission.

## App Review notes

- Every lock is disclosed in the app: self-only, passcode vault, family
  guardian, supervised profile. The app never claims a stronger level than it can
  show.
- The passcode vault is a Screen Time passcode the person sets themselves. Hisn
  cannot verify the Settings steps and says so. Apple's Apple Account reset and
  device erase remain the recovery paths.
- The Safari blockers and text extension are Safari extensions; the person can
  enable or disable them in Settings. A lock does not prevent that on an
  unsupervised device.
- Optional visual checks run locally and are off by default. No image or frame
  leaves the device. Face blurring is optional and disclosed as imperfect.
- The supervised profile is an external Apple Configurator artifact, not an
  in-app bypass. The app does not install profiles itself.
- No account, no login, no user-generated content, no ads, no purchases in this
  build.
- To review the Screen Time screens without an entitlement/device, the
  simulator tests cover layout and copy; the reviewer may need a provisioned
  TestFlight build for the full flow.

## Screenshots and metadata to capture

- iPhone 6.7" and 6.5" (and iPad 12.9" if supporting iPad separately):
  Overview, Setup/readiness, Screen Time app limits, vault confirmation, Safari
  blocker explanation, privacy/settings.
- English and Arabic screenshots; RTL layout must be shown.
- Do not include real browsing history, adult content, personal names or a real
  passcode in any asset.
- App preview video optional; none in this build.

## TestFlight plan

1. Provision the app and activity monitor with the Family Controls entitlement.
2. Archive/upload; TestFlight internal testers first.
3. On a disposable device: Screen Time authorization, Safari blocker activation,
   text extension, app shields/budget, vault seal/reveal, commitment clock,
   revocation and reboot.
4. Supervised device: install the profile, verify app removal is blocked and the
   profile needs its removal password; test the removal path.
5. Record every result in [RELEASE_VALIDATION.md](RELEASE_VALIDATION.md) and
   [the coverage pass record](COVERAGE_PASS_2026-10-30.md).

## Release checklist

- [ ] Apple Developer Program; Family Controls distribution entitlement.
- [ ] Signing team, archive, TestFlight.
- [ ] Host privacy/support pages; fill the App Store privacy label (no data).
- [ ] Screenshots in English and Arabic for required device sizes.
- [ ] App Review notes above; no hidden locks or recovery paths.
- [ ] Device and supervised-profile acceptance.
- [ ] Submit; record the build number, review result and any rejection text.
