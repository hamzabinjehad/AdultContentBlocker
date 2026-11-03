# Guardian mode: managed removal resistance, pairing and coverage

Written 2026-11-01. **Status: plan, not implemented.** This extends
[Road to 1.0](ROAD_TO_1.0.md) section 3 and [End-goal plan](END_GOAL_PLAN.md)
workstreams D and E. It does not mark any gate complete. "MES" in the request
is read as **MDM** (managed-device mode); the device-management sections apply
whichever name is used.

## The question

Can Hisn be made undeletable, owned by a "main account", paired with a laptop,
and able to block every possible route to adult content?

## The short answer

- **No app can make itself undeletable on a device its user administers.** Only
  the platform's device-management layer can refuse the uninstall: Android
  device owner, Apple supervision or MDM, a macOS administrator split plus MDM,
  or browser policy. The app can ask the platform; it cannot be the platform.
- **"Main account" should be the platform family account, not a new Hisn
  account.** Apple Family Sharing and Google Family Link already own the
  authority a guardian needs. A second identity provider adds breach risk,
  account-recovery support and store review, and buys nothing a guardian key
  cannot do locally.
- **The laptop is the provisioning and recovery console.** Pairing is for
  policy and health, not for browsing history. Nothing about which sites a
  person visited should cross the cable.
- **"Every possible way" is a layered matrix with residuals, not a switch.**
  The honest product claim is: on a managed device, the ordinary removal and
  bypass routes are closed; recovery, a restore, a different device or an
  administrator with the keys remain.

The rest of this document turns that into a build plan.

## Four levels of removal resistance

| Level | Setup | Deletion blocked | Stopping blocked | Residual | Who it fits |
| --- | --- | --- | --- | --- | --- |
| **L0 App only** | Install the app | No | No | Uninstall, disable, force-stop | Anyone trying it |
| **L1 Family account** | Apple Family Sharing + Screen Time passcode; Google Family Link | Hisn deletion can be blocked by *Deleting Apps: Don't Allow* (iOS); Family Link blocks chosen apps and installs | Screen Time / Family Link survive quitting Hisn | Account recovery, passcode reset, a new device | Families, no reset, no laptop |
| **L2 Managed device** | One factory reset; Apple Configurator supervision or Android device owner via ADB/QR | Yes: the OS refuses uninstall/disable; profile removal needs a password | Yes, including VPN, Private DNS, app installs, Safe Mode and Settings erase | Recovery/DFU restore, ADB/root, the guardian or admin, Android factory reset from recovery | A household willing to reset once and keep a laptop |
| **L3 MDM / fleet** | ABM/ADE plus an MDM, or a managed-Play DPC | Yes, non-removable profiles and remote policy | Yes, plus remote lock/wipe and compliance | The MDM administrator, ABM, recovery | Schools, clinics, organizations |

L2 is the sweet spot for a family: it is free, it does not require an
organization, and it is the first level where the operating system itself says
"this app cannot be removed". L3 is stronger operationally but requires an
organization with a D-U-N-S number for Apple Business Manager and a paid MDM.
L1 is what the current public apps can do without a reset.

## Why "main account" is not a Hisn account

The repository already forbids server-side browsing logs for good reason
([privacy policy](../PRIVACY.md), [README](../README.md)). A Hisn account would
tempt exactly the data model that policy forbids: a device list, a policy
history and a support console that slowly accumulates identities and blocked
domains. It would also create a single point of account takeover over every
protected device.

Use three identities instead:

1. **Platform family account.** The child Apple Account under Family Sharing, or
   the child Google account under Family Link. This is the legal and recovery
   authority; Hisn does not duplicate it.
2. **Guardian key.** A P-256 keypair generated on the guardian's laptop,
   stored in the Secure Enclave or Keychain, with one printed recovery code.
   This signs policy packs and release approvals. It has no password to phish
   and no server to breach.
3. **Device key.** A P-256 keypair generated on the phone, stored in the
   Android Keystore or iOS Keychain. It identifies the device to the guardian
   key and signs health receipts.

A Hisn cloud account is only justified if remote management across networks is
a real requirement. Then it must be an **encrypted relay**, not a data store:
the server holds signed policy envelopes and health receipts it cannot read,
with no domain names and no browsing events. That is Phase 5, optional, and
gated on a privacy review.

## The Guardian model

Roles:

- **Guardian**: holds the guardian key, the recovery code and the removal
  password. May be a parent, a partner or the same person at a later date.
- **Protected person**: uses the device. Consents to enrollment and can always
  see that Hisn is installed and what it enforces.
- **Device**: holds the device key, the current policy pack and the local
  enforcement layers.

Objects:

| Object | Contents | Lives |
| --- | --- | --- |
| `GuardianKey` | P-256 public key, label, created date | Laptop; public half on every enrolled device |
| `DeviceBinding` | Device public key, platform, model class, enrolled date, policy counter | Laptop; device keeps its own private key |
| `PolicyPack` | Version, device binding, rules hash, schedule, lock date, platform controls, expiry, signature | Laptop signs; device stores the last accepted pack |
| `HealthReceipt` | Device ID, timestamp, layer booleans, tamper events, signature | Device signs; laptop stores only the latest |
| `RecoveryEnvelope` | Guardian key backup, removal password, time-lock ciphertext | Guardian's password manager and a printed copy |

A `HealthReceipt` never contains a hostname, a URL, a page title or an image.
Its layers are coarse: `owner`, `uninstall_blocked`, `profile`, `dns`, `vpn`,
`filter`, `accessibility`, `browser`, `screen_time`. That is enough to answer
"is this device still protected?" without answering "what did they look at?".

## Pairing the phone and the laptop

Pairing has one job: move a signed policy from the guardian's laptop to the
phone, and a coarse health receipt back. It must work offline and must not
depend on a Hisn server.

### Flow

1. **Guardian init.** The laptop generates a guardian keypair and a recovery
   code. The private key never leaves the Secure Enclave or Keychain.
2. **Show an invite.** The laptop shows a QR code:
   `hisn://pair/1?gk=<base64url-public-key>&nonce=<128-bit>&label=<name>`.
3. **Scan and answer.** The phone scans the QR, generates a device keypair and
   replies with its public key, the nonce and a proof of possession.
4. **Short authentication string.** Both screens show the same six digits,
   derived from the ECDH shared secret. The guardian confirms them on both
   devices. This is what stops a relay from substituting keys.
5. **Bind.** The laptop stores the device public key; the phone stores the
   guardian public key and a device ID. Both sides pin the keys for later
   policy and receipt signatures.
6. **Policy.** The laptop signs a `PolicyPack`; the phone verifies signature,
   device binding, counter and expiry before applying it. Replay of an older
   pack is rejected by the counter; an expired pack keeps the last known rules
   but raises a visible "policy stale" state.
7. **Health.** The phone signs a receipt after each significant state change
   and on a schedule. The laptop shows it as verified, stale or missing.

### Crypto and transport

- **P-256 ECDH** for the shared secret and **P-256 ECDSA** for signatures,
  with HKDF-SHA256 for key separation and AES-256-GCM for the envelope. P-256
  is the curve both Apple's Secure Enclave and Android's Keystore/StrongBox can
  hold. Ed25519 and X25519 work in software, but they are not backed by those
  secure elements on both platforms; do not mix the two without a migration
  rule.
- The QR carries public keys only. The six-digit SAS is compared out of band
  by the two people in the room.
- **LAN**: mDNS/Bonjour discovery, TLS 1.3 with a self-signed certificate
  pinned to the pairing key, or a Noise handshake over TCP. No router
  configuration.
- **USB**: Android ADB for provisioning and for a wired pairing fallback; iOS
  uses Apple Configurator for supervision and the LAN path for app-level
  pairing. Do not use private iOS USB APIs.
- **Relay (optional)**: store-and-forward encrypted envelopes, push to wake,
  delete on delivery, no plaintext metadata beyond a random device ID and a
  timestamp.

The pairing protocol is worth its own specification and test vectors before
any UI is built. A pairing bug is a remote-policy-injection bug. The reference
implementation, 33 protocol tests, an 11-test laptop console and a Java
phone-side pairing class are in
[script/guardian_protocol.py](../script/guardian_protocol.py),
[GuardianPairing.java](../android/core/src/main/java/app/hisn/core/GuardianPairing.java)
and [PAIRING_PROTOCOL.md](PAIRING_PROTOCOL.md); Keystore storage and the QR UI
are not wired yet.

### The laptop as a filtered gateway (optional)

Pairing is not the only way to use the laptop. The Mac can also act as a
gateway: Hisn's content filter runs on the Mac, Internet Sharing gives the
phone a network, and the phone's traffic crosses the Mac. That would cover
apps a phone-side DNS filter cannot see, with no phone management at all.

It is a home or office layer, not a replacement for device owner or
supervision: the phone must stay near the Mac, the Mac must stay awake and
online, cellular data bypasses it, and latency and battery use rise. It also
needs a real test before it is claimed, because a content filter sees flows on
the Mac and may not see every NATed client flow. Add it only after the
forwarded-traffic test passes.

## What each platform can actually enforce

### Android, device owner

| Control | API | Effect | Residual |
| --- | --- | --- | --- |
| Block Hisn uninstall | `setUninstallBlocked` | Android refuses the uninstall | Factory reset from recovery; ADB ownership removal only on a test-only build |
| Block all app controls | `DISALLOW_APPS_CONTROL` | No uninstall, disable, force-stop or clear-data for any app | Very broad; user cannot repair other apps |
| Block uninstall of any app | `DISALLOW_UNINSTALL_APPS` | No app can be uninstalled | Does not block disable or clear-data |
| Block new installs | `DISALLOW_INSTALL_APPS` | No app can be installed; updates can be blocked too | Can freeze Hisn's own updates; needs a maintenance window |
| Block sideloading | `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY` | No unknown-source installs | Play-installed browsers remain |
| Block debugging | `DISALLOW_DEBUGGING_FEATURES` | Developer options and ADB debugging off | An authorized computer can still use recovery/fastboot |
| Block Safe Mode | `DISALLOW_SAFE_BOOT` | No Safe Mode from power menu or keys | Recovery/fastboot |
| Block Settings erase | `DISALLOW_FACTORY_RESET` | No erase from Settings | Recovery-mode factory reset |
| Block VPN changes | `DISALLOW_CONFIG_VPN` | No adding or changing VPNs | Existing VPN, another user profile |
| Block Private DNS changes | `DISALLOW_CONFIG_PRIVATE_DNS` (API 29+) | DNS cannot be moved to a DoH resolver | Hardcoded IP traffic |
| Block clock changes | `DISALLOW_CONFIG_DATE_TIME` | Date/time cannot be moved manually | Network time, timezone quirks |
| Block default-app changes | `DISALLOW_CONFIG_DEFAULT_APPS` (API 34+) | The default browser and other defaults cannot be changed | An already-installed browser can still be opened directly |
| Block sideloaded installs (strict) | `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY` (API 29+) | New unknown-source apps and browsers cannot be installed | Play-store installs and updates still work; manual Hisn updates blocked |
| Block new users | `DISALLOW_ADD_USER`, `DISALLOW_ADD_MANAGED_PROFILE` | No second user or work profile | A separate device |
| Block mobile-network changes | `DISALLOW_CONFIG_MOBILE_NETWORKS`, `DISALLOW_CONFIG_TETHERING`, `DISALLOW_AIRPLANE_MODE` | Harder to move to another network | Another Wi-Fi or device |
| Suspend blocked apps | `setPackagesSuspended` | Every installed app in the block list disappears from the launcher | Running app until suspension; cleanup depends on installed-app listing |
| Force an allowed browser | `addPersistentPreferredActivity`, `DISALLOW_CONFIG_DEFAULT_APPS` | Links open in the covered browser | In-app WebViews |
| Always-on DNS | `setAlwaysOnVpnPackage(admin, pkg, false)` | The DNS-only tunnel stays connected | Another VPN, revoked approval; no lockdown |
| Always-on full tunnel | `setAlwaysOnVpnPackage(admin, pkg, true)` | Traffic is locked to the VPN | Needs a full-tunnel service, not built |
| Dedicated kiosk | `setLockTaskPackages`, `setLockTaskFeatures` | Only allowed apps run | Not suitable for a daily phone |

Two Android facts are easy to get wrong:

- **The current DNS VPN is DNS-only and deliberately does not advertise
  always-on support** (`DnsVpnService.java` line 36). Asking Android to make it
  always-on with lockdown would cut every non-DNS connection. A full-tunnel
  local VPN that forwards non-DNS traffic is a prerequisite for the strongest
  network lock.
- **`DISALLOW_INSTALL_APPS` also blocks updates**, including Hisn's own. For a
  long-lived install, prefer `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY` plus
  suspending known browsers, and open a short, guardian-approved maintenance
  window for updates instead of freezing the app on an old list.

### iPhone and iPad, supervised

| Control | Profile key | Effect | Residual |
| --- | --- | --- | --- |
| Block app deletion | `allowAppRemoval=false` | No Remove App for any app | Supervised only; all-or-nothing |
| Non-removable profile | `PayloadRemovalDisallowed=true` plus `com.apple.profileRemovalPassword` | No Remove button without the password | Password holder; restore |
| Block erase | `allowEraseContentAndSettings=false` | No Erase All Content and Settings | DFU/recovery restore |
| Block VPN/profile installs | `allowVPNCreation=false`, `allowUIConfigurationProfileInstallation=false` | No new VPN or profile | Supervised only |
| Block alternative marketplaces | `allowMarketplaceAppInstallation=false`, `allowWebDistributionAppInstallation=false` | No marketplace or web-distributed apps | Supervised only |
| Block private browsing | `allowSafariPrivateBrowsing=false` (iOS 26+) | No Safari private mode | Other WebKit browsers, if installed |
| Block Private Relay | `allowCloudPrivateRelay=false` | DNS cannot be bypassed by Private Relay | Cellular carrier DNS, another network |
| Block account and passcode changes | `allowAccountModification=false`, `allowPasscodeModification=false` | The account/passcode cannot be changed on-device | Account recovery; lockout risk |
| Block new apps | `allowAppInstallation=false` | No App Store installs | Needed apps; all-or-nothing |
| Force time | `forceAutomaticDateAndTime=true` | Manual clock changes blocked | Network time |
| Adult web filter | `com.apple.webcontent-filter` with `FilterType=BuiltIn`, `AutoFilterEnabled=true` | Apple's adult filter in Safari and WebKit | Not every app; no image classification |
| Encrypted DNS | `com.apple.dnsSettings.managed`, `ProhibitDisablement=true` | DNS cannot be switched off in Settings | A VPN already installed, another network |

The supervised profile is installed by Apple Configurator from the guardian's
Mac, not by the app. That is what keeps the public app store build compliant:
the app never tries to make itself undeletable; the device's management layer
does. Plan for the 30-day provisional supervision window, during which a
freshly supervised device can still be released.

Apple's MDM route needs Apple Business Manager, which needs an organization and
a D-U-N-S number. For a family, **supervision plus a non-removable profile is
the ceiling**; a third-party MDM may manage a supervised device, but the
supported fleet route is ABM/ADE. Do not promise family MDM without an
organization.

### macOS

macOS has no per-app "cannot delete" restriction. The lock is ownership and
policy:

- Hisn.app and its helpers are root-owned in `/Applications`; the daily account
  is a standard user; a root LaunchDaemon restores the login agent.
- A configuration profile with `PayloadRemovalDisallowed` and a removal
  password closes the browser/network policy path.
- MDM can deliver non-removable profiles, a system-extension policy and PPPC
  grants; macOS 15+ can mark the filter extension non-removable from the UI.
- The administrator can still remove everything, and recovery mode can erase
  the disk. Time-lock the removal password for a solo user.

### Windows and the network

Windows is not a Hisn platform today. If it is added, the honest lock is a
standard user account plus machine-wide Chrome/Edge policy from an
administrator account the protected person does not hold. The browser policy
in `profile/make_windows_policy.py` is the start.

The router is the only layer that covers televisions, consoles, guests and
devices Hisn cannot run on:

- Filtering DNS for the whole LAN, with the router admin password held by the
  guardian.
- Firewall rules that block DoH/DoT endpoints and known public resolvers, so a
  device cannot move its DNS.
- IPv6 DNS handled as well as IPv4; a filter that only covers IPv4 is a
  one-setting bypass.
- A guest network that is filtered, or not broadcast at all.

## Coverage matrix

| Layer | Catches | Defeated by | Hisn today |
| --- | --- | --- | --- |
| Router DNS and firewall | Listed hosts on every LAN device | Mobile data, another network, DoH, hardcoded IPs, VPN | Tools and guides only |
| On-device DNS profile / Private DNS | Listed hosts in most apps | DoH inside an app, direct IPs, VPN | iOS profile, Android VPN, Mac filter |
| Full-tunnel local VPN | DNS plus SNI/IP policy for all apps | Apps that pin certificates or use ECH, another VPN, root | Not built (DNS-only today) |
| Browser extension and managed policy | Known hosts, page text, images, SafeSearch, incognito | Another browser, extension removed, unreadable media | Extension 1.4.0 |
| OS content filter | Host and flow decisions | Provider loss, established flows, encrypted posts | macOS system extension |
| Apple web content filter | Adult sites in Safari/WebKit | Other apps, images, posts | Supervised profile |
| Screen Time / Family Link | App limits, chosen apps, downtime | Account recovery, another device | iOS and Android |
| App blocking and browser allowlist | Adult apps and unmanaged browsers | A browser already installed, WebViews | Partial |
| Text classifier | Explicit words and phrases on the page | Images, video, other languages, context | Extension, Safari, Android |
| Visual classifier | Nudity in images and video frames | False negatives, performance, unreadable video | Opt-in, browser and Android |
| Search and YouTube restrictions | SafeSearch and Restricted Mode | Another search engine, direct URLs, country domains | Extension policy; Android opt-in DNS CNAME (unverified) |
| Per-app accessibility guard | Visible text in selected apps | Accessibility off, OEM differences | Android direct build |
| Accountability partner | Ordinary loosening | Guardian authority, another device | Mac partner approvals |
| Device management | Uninstall, disable, profile removal | Recovery, admin, another device | Android device owner preview; iOS supervised profile |
| Coverage checklist | "I forgot the tablet" | A device the person did not disclose | Planned (M-C) |

The point of the table is not that Hisn does all of it. It is that every row
has a different failure mode, so a single "protected" badge is dishonest. The
app should show each layer separately, as it already does on Mac and Android.

## What exists today

| Surface | Already built | Missing for this plan |
| --- | --- | --- |
| Android managed mode | Device-owner uninstall block, eight API-gated lockdown restrictions plus one strict install key, an opt-in suspension of blocked apps, a DNS-only always-on VPN while DNS filtering is on, readback, release path, pure tests, a read-only live status check, and a laptop provisioning helper for install plus `set-device-owner` | QR/UI provisioning wizard, full-tunnel VPN with lockdown, install and browser controls, physical device proof |
| iPhone and iPad | Supervised profile with six opt-in hardening keys, built-in adult filter, encrypted DNS, removal password, Screen Time vault, a tested `--maximum` preset, and a guardian pack with profile, manifest, checklist and hash | MDM/ABM path, time-locked password, phone-side pairing, device acceptance |
| Mac | Guardian account split, root guardian daemon, profile generator, browser policy, filter authority | Signed package and updater, MDM delivery, standard-account bootout proof |
| Pairing | Reference P-256 offer/response/SAS and signed policy envelope with 33 tests, an 11-test laptop console, an Android Java pairing plus policy verifier checked against Python fixtures, an API 36 emulator runtime exchange of offer/response/SAS and a signed policy with an EC key in AndroidKeyStore on API 31+ (AES-wrapped fallback), a tighten-only policy application (keep, lockdown, strict, suspension, blocked apps and blocked domains) read back as protected, and a one-tap "keep only this browser" selection; verified manual product pairing screen (paste offer, SAS, copy response), with camera QR and policy import still missing; signed domain-list hash (implemented) and a default-browser allowlist still missing | Android/iOS key storage, QR scanning UI, transport, health receipts |
| Account | None, by design | Optional encrypted relay only if remote management is a proven need |

## The gaps that matter most today

1. **Encrypted DNS and DoH.** A phone or browser that uses DoH bypasses a DNS
   filter. Android has `DISALLOW_CONFIG_PRIVATE_DNS`; iOS has the DNS profile;
   the router needs firewall rules. The DNS policy should also block the
   well-known DoH/DoT endpoints where the platform allows it.
2. **A new browser or VPN.** The strongest install block is expensive because
   it freezes updates. The practical order is: block unknown sources, suspend
   known browsers, force an allowed browser as the default, and make installs
   require the guardian.
3. **VPN and Private DNS changes.** `DISALLOW_CONFIG_VPN` and
   `DISALLOW_CONFIG_PRIVATE_DNS` are already in the Android lockdown preview;
   the missing piece is an always-on full-tunnel service so the filter cannot
   simply be turned off.
4. **Clock rollback.** A commitment that trusts the device clock can be ended
   by moving the date. `DISALLOW_CONFIG_DATE_TIME` and
   `forceAutomaticDateAndTime` close the manual path; network time and timezone
   changes still need trusted-time checks.
5. **The second device and the hotspot.** No on-device product wins this. The
   answer is network filtering, a coverage checklist and a conversation, not a
   stronger app.
6. **Hardcoded IPs and ECH.** DNS and SNI see less every year. A local proxy
   with IP blocklists and on-device classification helps; a promise of complete
   coverage does not.

## What "unlimited" should mean

"Unlimited" is ambiguous. It can mean no end date, or every possible route.

- **No end date** is a policy choice, not a technical one. An open-ended lock
  should still have a guardian release, a 90-day review and a recovery path.
  A lock with no exit is the lock people rip out, and the one a court or a
  support team cannot undo.
- **Every possible route** is not achievable. The product should publish a
  coverage matrix like the one above and a measured false-negative rate, not a
  percentage in a marketing headline.

The recommended shape is an **open-ended commitment with a guardian-held
release and a scheduled review**. For a solo user with no second person, the
time-locked removal password is the substitute for a guardian: the lock ends
on a date nobody can move, including Hisn.

## Roadmap

| Phase | Deliverable | Platform | Done when | Effort |
| --- | --- | --- | --- | --- |
| **0** | Claims and product split: `Hisn` (consumer, store-compliant) and `Hisn Guardian` (managed, direct/enterprise). Update README, store copy, in-app text and this plan. | Docs | No surface says "undeletable" without naming the management level; store review notes agree | S |
| **1** | Guardian Console: `guardian init`, `pair`, `policy`, `audit android`, `audit ios`, `recover`; QR + SAS pairing; signed policy pack; health receipts; no server. | macOS CLI/UI, Android/iOS clients | Two devices pair over LAN; a replayed or wrong-device pack is rejected; an audit reads device owner, uninstall block, DNS, VPN and profile state; no hostname crosses the link | M |
| **2** | Android managed edition: provisioning wizard (ADB and QR), restriction tiers, full-tunnel always-on VPN, browser allowlist, install controls, device tests. | Android | A disposable device passes uninstall, disable, clear-data, Safe Mode, Settings erase, VPN, Private DNS, install, clock and new-user tests; release restores everything | L |
| **3** | iOS supervised pack: `--maximum` preset, guardian pack generator, removal-password envelope, time-locked password, MDM/ABM guide, device acceptance. | iOS/iPadOS, Python | A supervised device shows no Remove App, no profile Remove, no erase, no VPN creation, no DNS off switch; the time-lock round-trip passes | M |
| **4** | Coverage: DoH/DoT blocking, SafeSearch and YouTube restricted mode, IP lists, browser allowlist, Mac word guard, Mac visual layer, optional iOS URL filter prototype. | All | A measured corpus meets the false-negative and false-positive gates in the end-goal plan | L |
| **5** | Optional encrypted relay and multi-device dashboard: store-and-forward policy and health only; no browsing data; external privacy review. | Server + clients | A relay compromise exposes no hostnames, no page text and no images; deletion and retention are documented | L |
| **6** | Adversarial device matrix: every route in this document attempted on real hardware, results recorded in the test matrix. | All | Every claim has a test or is marked unproven | M |

The order is deliberate. Phase 1 is cheap and unlocks every later phase. Phase
2 is the strongest thing Hisn can ship without an organization. Phase 3 is
already most of the way there in `profile/make_ios_profile.py`. Phase 4 is
where most of the actual blocking gain is, but it needs the enforcement base
first. Phase 5 is optional and should not delay 1.0.

## Store, legal and consent

- **Two builds, two claims.** The App Store and Play builds may not make
  themselves undeletable. The managed build is distributed directly, through
  managed Google Play, or as a custom Apple app for organizations. Keep
  separate application IDs, flavors and release checklists; never upload the
  managed build to a public store.
- **Play policy.** A device policy controller needs its own declarations and
  review. Accessibility use that prevents disabling or uninstalling an app is
  restricted to parental control or enterprise management; ordinary
  self-control consent is not that exception. Do not relabel an adult's
  personal mode as parental control.
- **Apple policy.** A public app cannot remove the system's uninstall control.
  Supervision and MDM are the sanctioned paths; the profile is installed by the
  guardian, not by the app.
- **Consent.** Enrollment requires the protected person's informed consent and
  a visible, named app. No hidden icon, no fake system app, no covert
  monitoring. The guardian's authority has a documented recovery path.
- **Privacy.** No browsing logs, no uploaded images, no page titles in health
  receipts. The relay, if built, is encrypted and stores only policy and coarse
  health.
- **Abuse.** Undeletable software is a stalkerware pattern. The defense is
  transparency, consent, a recovery path and a support route, not a secret
  kill switch that the guardian can also abuse.

## Adversarial acceptance tests

| Attempt | Expected on L2 | Residual |
| --- | --- | --- |
| Uninstall Hisn from Settings | Refused by the OS | Factory reset from recovery |
| Disable or force-stop Hisn | Refused | None while device owner holds |
| Clear Hisn's data | Refused | None while device owner holds |
| Enter Safe Mode | Refused on Android; not applicable on iOS | Recovery/fastboot |
| Erase from Settings | Refused | DFU/recovery restore |
| Remove the profile | Password required | Password holder, restore |
| Add a VPN or DNS profile | Refused | An existing VPN, another network |
| Change Private DNS | Refused | Hardcoded IPs |
| Install another browser | Refused or blocked | An already-installed browser, WebViews |
| Move the clock | Refused | Network time, timezone |
| Add a user or profile | Refused | Another device |
| Use ADB | Debugging off | Recovery/fastboot; a test-only build can remove ownership |
| Restore the phone | Not prevented | Activation Lock, account recovery |
| Use another device or hotspot | Not prevented | Router DNS, coverage checklist |

Every row needs a dated record in [TEST_MATRIX.md](TEST_MATRIX.md) before a
release note claims it.

## Open decisions

1. **Who is the customer?** Families with a guardian, solo self-control, or
   both. The managed edition is a family/institution product; the store app is
   a self-control product.
2. **Is a factory reset acceptable?** Android device owner and iOS supervision
   both require one. If not, the ceiling is L1.
3. **Is an organization available?** Without ABM/D-U-N-S, iOS stops at
   supervised profile, not MDM.
4. **Is a relay acceptable?** If not, pairing is LAN/USB only and remote
   management is out of scope.
5. **Build or integrate?** For iOS, integrating an existing MDM is cheaper than
   writing one. For Android, a focused DPC is tractable and gives Hisn control
   over the policy language.
6. **Which platform first?** Android, because device owner is the only route
   that blocks uninstall without an organization, and it is testable on a
   disposable phone.

## What I would build first

1. **Phase 0**, one pass over every claim and store listing. The current docs
   are honest; the marketing and the app copy must match them before the
   managed build exists.
2. **Phase 1**, the Guardian Console and pairing protocol. It is the piece that
   makes "connect the phone with the laptop" real, and it is testable without
   devices for the protocol and with one Android phone for the audit.
3. **Phase 2**, the Android managed edition, starting with the restriction
   tiers and provisioning wizard, then the full-tunnel VPN. This is the first
   configuration where "you cannot delete it" is true in the ordinary sense.
4. **Do not build a Hisn account or a cloud relay yet.** Use the platform
   family account and a local guardian key. Revisit only when remote management
   is a proven requirement.

## References

- Android `DevicePolicyManager`: <https://developer.android.com/reference/android/app/admin/DevicePolicyManager>
- Android `UserManager` restrictions: <https://developer.android.com/reference/android/os/UserManager>
- Apple device-management profile schema: <https://github.com/apple/device-management>
- Apple Platform Deployment: <https://support.apple.com/guide/deployment/welcome/web>
- Google Play sensitive-permissions policy: <https://support.google.com/googleplay/android-developer/answer/16558241>
- Apple Configurator: <https://apps.apple.com/app/apple-configurator/id1037126344>

## What this plan does not claim

- That any phone becomes undeletable against a person with recovery, ADB, root
  or the guardian's keys.
- That a public App Store or Play build can block its own uninstall.
- That every adult image, video, post or encrypted message is classified.
- That a family can use Apple MDM without an organization.
- That a lock with no exit is a good product.
