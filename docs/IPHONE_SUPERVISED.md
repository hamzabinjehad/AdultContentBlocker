# Removal-resistant Hisn on a supervised iPhone or iPad

On a personal iPhone, the owner can always delete an app, remove a profile,
add a VPN or erase the phone. Apple only lets those routes be closed on a
**supervised** device. Supervision is free, done once from a Mac with Apple
Configurator, and erases the device. Whoever holds the supervising Mac and the
profile's removal password (a guardian or accountability partner, not the
person being protected) becomes the way back.

This is the strongest setup Hisn can offer without a paid MDM service. It is
still not absolute. See **Limits** below.

## What you need

- A Mac with [Apple Configurator](https://apps.apple.com/app/apple-configurator/id1037126344)
  (free), held by the guardian.
- The iPhone or iPad and its USB cable. **Back it up first: supervision erases it.**
- Python 3 on the Mac to build the profile.

## Steps

1. **Build the profile** (the guardian does this, so the password stays with them):

   ```sh
   python3 profile/make_ios_profile.py --out dist/hisn-iphone.mobileconfig \
       --block-site reddit.com --block-site x.com
   ```

   `--block-sites-file sites.txt` takes one site per line. The removal password
   is generated, written inside the file (readable by the guardian's account
   only) and not printed. Use `--print-password` only if the protected person
   cannot see the screen, and store it the way you store other recovery secrets.

2. **Supervise the device.** In Apple Configurator: connect the device, choose
   **Prepare**, select **Manual configuration**, tick **Supervise devices** and
   **untick "Allow devices to pair with other computers"**, create an
   organization when asked, and finish. The device is erased and restarts.

3. **Set the device up** and sign in to the person's Apple Account. Leave
   **Find My** on: Activation Lock is what makes a computer restore need that
   account's password.

4. **Install Hisn** from the App Store, open it and follow its Setup tab
   (Safari parts, Screen Time, My blocked sites). Do this **before** step 5:
   the profile blocks interactive profile installation and app deletion.
   Skip Hisn's own **Prepare family DNS** step: the profile provides DNS, so
   Hisn's DNS row reads as not set up even though the profile's DNS is active.

5. **Install the profile** from Apple Configurator: select the device,
   **Add › Profiles**, choose `hisn-iphone.mobileconfig`.

6. **Set a Screen Time passcode** the guardian keeps (Settings › Screen Time ›
   Lock Screen Time Settings). Hisn's in-app commitment works alongside it.

7. **Check each layer** on the device:
   - Settings › General › VPN & Device Management shows *Hisn Phone Protection*
     with no **Remove** button.
   - Holding an app icon shows no **Remove App**.
   - Settings › General › Transfer or Reset shows no **Erase All Content and Settings**.
   - Settings › General › VPN & Device Management › DNS cannot be switched off.
   - An adult test site and one of your `--block-site` sites are blocked in Safari.

## What each part closes

| Route around protection | Closed by | Only on supervised? |
|---|---|---|
| Delete the Hisn app (or any app) | `allowAppRemoval: false` | Yes |
| Remove the profile | `PayloadRemovalDisallowed` + removal password | Yes |
| Erase from Settings | `allowEraseContentAndSettings: false` | Yes |
| Install a VPN or DNS profile to route around filtering | `allowVPNCreation`, `allowUIConfigurationProfileInstallation: false` | Yes |
| Switch the family DNS off | `ProhibitDisablement` | Yes |
| iCloud Private Relay bypassing DNS | `allowCloudPrivateRelay: false` | Yes |
| Sideloaded browsers from other marketplaces or the web | `allowMarketplaceAppInstallation`, `allowWebDistributionAppInstallation: false` | Yes |
| Safari private browsing | `allowSafariPrivateBrowsing: false` (iOS 26+) | Yes |
| Adult websites in Safari and WebKit browsers | Apple's built-in filter (`AutoFilterEnabled`) + your sites (`DenyListURLs`) | — |

Every key was checked against Apple's
[device-management schema](https://github.com/apple/device-management) on
2026-10-24. Older iOS versions ignore keys they do not know.

## Limits

- **Unsupervised, almost none of this applies**, and the profile can be removed.
- **The 30-day provisional period.** A device supervised by hand in Apple
  Configurator can release supervision during its first 30 days. Plan for it.
- **Computer restore.** Recovery/DFU restore from any computer erases the
  device and the profile. Activation Lock then asks for the Apple Account
  password; whoever knows it can set the phone up again unprotected.
- **App deletion is all or nothing.** iOS has no per-app switch without MDM.
  Use `--allow-app-removal` to leave deletion allowed for all apps.
- **Not a classifier.** DNS and Apple's filter judge sites, not individual
  images, posts or videos. Hisn Text in Safari adds page-text checks.
- **Configuration, not proof.** Test every layer on the device; this file was
  generated and unit-tested, not installed on a phone in this repository.

For a fleet, or for removal that survives erasing, use an MDM with Automated
Device Enrollment instead; see [network-first protection](NETWORK_FIRST_PROTECTION.md#phone-enforcement).
