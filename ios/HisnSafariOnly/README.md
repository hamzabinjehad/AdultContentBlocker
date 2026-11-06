# Hisn Safari — limited Personal Team edition

Separate app identity `app.hisn.safari`, not the full `app.hisn.mobile` app.
No Family Controls, DeviceActivity monitor, App Group or Network Extension
entitlements are requested. The full project and protection logic are unchanged.

Generate the standard Safari-only candidate (six domain blockers + optional
text/visual scanner):

```
python3 ios/generate_xcodeproj.py --safari-only
```

The free account hit Apple's ten-new-App-IDs-per-seven-days quota during device
setup on 2026-10-13. A separately labeled domain-only candidate reuses the
limited app's child IDs, including its unused text-scanner ID for domain part
one. It does not include the scanner or shorten the six-part domain list:

```
python3 ios/generate_xcodeproj.py --safari-only --domain-only --reuse-text-id-for-part-one
```

This reuse is only for this limited app's own IDs; it never borrows another app's
identity or installs over the full edition. The Info.plist flags inform the UI
and the exact six blocker identifiers. All six parts retain the normal signed
seed verification during build and resource-integrity checks in their handlers.

Open `ios/HisnSafari.xcodeproj`, select HisnSafari, and sign with the user's own
available development team. Account quotas, certificates, profiles and device
trust still apply. Do not remove full-edition entitlements to conceal failures.

## Device evidence — 2026-11-06

- Fixed before this run: the limited app still assumed the pre-1.1.4
  **four-part** bundle — it read a six-part `rules-metadata.json` as
  unverifiable, hid parts 5 and 6 and disabled reload. It now uses
  `MobileProtectionPolicy.blockerIdentifiers.count` everywhere; a
  `test_safari_only.py` assertion pins it. The Python suite passes 40 tests.
- Built and installed the **domain-only + reused-ID candidate, version 1.6.0,
  build 10** (six blockers, `app.hisn.safari` + `blocker2–6` + the reused
  `HisnText` ID for part one; no new App IDs, no quota hit), development-signed
  with the Personal Team `J58UU95FT3` on the connected iPhone 11 (iOS 27.2,
  Developer Mode enabled).
- Install succeeded (`devicectl`); **launch was again denied** with the OS
  security error until the developer profile is trusted in Settings →
  General → VPN & Device Management. That trust step remains the owner's.
- Free profiles expire after seven days; this build's profiles were issued
  2026-11-06 and expire around 2026-11-13 UTC. Reinstall to renew; this is not
  distribution.

## Device evidence — 2026-10-13

- Built and verified the development-signed domain-only bundle, version 1.1.2,
  build 6. That 1.1.2 bundle contained exactly Hisn1–4, no text or DeviceActivity
  extension; the current 1.2.0 project has six parts.
- Installed on the connected iPhone 11; device app listing confirms identity
  and version. Launch was denied with the OS security/profile-trust error.
  User developer trust and successful launch remain to be confirmed.
- The current containing-app provisioning profile expires 2026-10-20 UTC.
  Renew/reinstall before expiration; this is not permanent distribution.
- 27 Python mobile tests pass, including limited-project scope, ID/target counts,
  localization and unchanged full-project generation checks.

## Activation and limits

Trust the user's own developer profile if iOS requests it. Then enable Hisn 1–6
in Settings → Apps → Safari → Extensions and reload Safari pages.
Installation does not enable these switches. Enabled-state readback is not a
traffic/classification acceptance test. Do not label this build fully protected.

The installed domain-only build has no text/image/video classification, no
application shields, no device-wide DNS, no enforced commitment and no deletion
protection. Other browsers and native apps remain uncovered. Physical blocking
and app UI acceptance are pending the user's trust/activation steps. No router,
Screen Time, account permissions or existing full-app settings were changed.
