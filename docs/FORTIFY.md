# Fortifying the Mac, the iPhone and the Android phone

`script/fortify.py` is the operator tool that puts every attached device at the
strongest protection this Mac can apply, then reports what is still open. It is
not a new protection layer: it drives `macos/block_dns.sh`,
`profile/make_ios_profile.py`, `macos/verify_enforcement.sh`, adb and devicectl,
and it never calls a layer enforced without a readback.

```bash
python3 script/fortify.py audit                 # read-only; Mac, Android and iPhone
python3 script/fortify.py apply --android       # Private DNS + always-on Hisn VPN
sudo python3 script/fortify.py apply --macos    # hosts extras + filtering DNS
python3 script/fortify.py apply --ios           # build the iPhone profile
python3 script/fortify.py verify                # non-zero when a critical layer is open
python3 script/fortify.py report --lang ar      # markdown report, Arabic
```

## What each platform gets

**Mac** — `apply --macos` needs root and does two things:

1. It writes the *extras* from `block_dns.sh --hosts` into `/etc/hosts`: the
   SafeSearch addresses (196 names, every browser and app, not only the
   extension), the iCloud Private Relay and public DoH names that route DNS
   around the file, and the country/mobile subdomains of the 1,000 most-visited
   sites. That is about 32,000 lines, not the 473,172 lines the full list
   becomes; the filtering resolver already covers the domain list with wildcard
   semantics, and a 600k-line hosts file slows every lookup.
2. It points the active network service (and `iPhone USB`, if present) at
   AdGuard Family `94.140.14.15` / `94.140.15.16`, then flushes the cache.

`--full-hosts` merges all 236,586 domains instead (the 473k-line option, with
the lookup-cost warning). `--x-media` additionally blocks `pbs.twimg.com`,
`video.twimg.com` and `abs.twimg.com`: X keeps its text and loses every image,
including avatars.

**Android** — `apply --android` sets Private DNS to `dns-family.adguard.com`,
sets Hisn as the always-on VPN (without lockdown), and leaves the app's own DNS
filter in charge. The mode is chosen from the measured state: see below.

**iPhone** — `apply --ios` writes `dist/fortify/Hisn-iPhone.mobileconfig`
(encrypted family DNS, Apple's adult web filter, the supervised-only
restrictions) and prints the install path. It cannot install it: without MDM or
Apple Configurator there is no remote profile install. The file is also copied
to iCloud Drive by the operator when that folder exists.

## What it will not do

* **Telegram.** Media is fetched over MTProto from Telegram's data centres, not
  over DNS. No host list separates porn from a normal photo inside Telegram.
  Keeping the app keeps the hole; only blocking the app closes it. The same is
  true of any end-to-end messenger.
* **X.** Adult images live on X's own CDN, not on adult domains. `--x-media` is
  the only network-level cut, and it removes *all* images. Without it, X's
  in-app **Settings ▸ Privacy and safety ▸ Content you see ▸ Hide sensitive
  content** is the only content control.
* **iPhone without supervision.** A manually installed profile is removable,
  Screen Time is changed with the device passcode, and the full Hisn app needs
  the Family Controls entitlement this team does not have. The real lock is a
  Screen Time passcode the guardian types.
* **A device owner.** Anyone with the administrator password, the Apple Account,
  a recovery mode restore or another network defeats every layer above. The lock
  is a person, not a file.

## The measured Android finding

Strict Private DNS (`private_dns_mode=hostname`) and the Hisn VPN do not
coexist. Android runs DoT against the active network's resolver; with the VPN up
that is `10.111.0.2`, which does not answer DoT, so every lookup fails — a
benign `ping example.com` returned `unknown host`. With the VPN as the resolver,
`opportunistic` plus the AdGuard Family hostname is the setting that works and
still filters. Without the VPN, `hostname` is correct and `apply` chooses it.
The audit reports `opportunistic` as **partial**, not enforced, because it can
fall back to plain DNS.

## The handover step

`apply` cannot split accounts or type a passcode. When the guardian is present:

* Mac: `macos/setup_guardian.sh --create-admin` creates the second administrator
  and `--demote-me` makes the daily account standard. The guardian owns that
  password; a password you set yourself is a pause button.
* iPhone: the guardian types the Screen Time passcode and turns on
  **Content & Privacy ▸ Content Restrictions ▸ Web Content ▸ Limit Adult
  Websites**. Then enable every Hisn blocker in Safari ▸ Extensions.
* Android: the guardian holds the device-owner or Hisn lock credentials; a
  device owner is the only way to stop uninstall and Private DNS changes.

## Reversal

```bash
sudo macos/block_dns.sh --undo                  # removes the hosts blocks
sudo networksetup -setdnsservers Wi-Fi Empty    # back to DHCP DNS
python3 script/fortify.py apply --android       # re-run after setting DNS by hand
```

The Android settings are ordinary secure settings:
`adb shell settings put global private_dns_mode opportunistic` and
`adb shell settings put secure always_on_vpn_app null`.
