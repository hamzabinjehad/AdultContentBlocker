# From personal use to publishing

The order this has to happen in, what each step costs, and the decisions that
are yours rather than the code's. Start only when `docs/DOGFOOD.md`'s "what
stops a release" list is empty.

## 1. Apple — the Mac app and its filter

| Step | Detail |
|---|---|
| Apple Developer Program | $99/year. Needed for the system filter at all, and for distributing outside the App Store. |
| Identifiers | App `app.hisn.Hisn`, system extension `app.hisn.Hisn.HisnFilter`, app groups `group.app.hisn` and `<TEAMID>.app.hisn`, capability *Network Extensions* + *System Extension*. |
| Development | `macos/install.sh --team <TEAMID>` — Xcode's automatic signing uses `content-filter-provider`. Verify the three hardware checks in `docs/TAMPER_MODEL.md` › *Still to verify on hardware* before anything else. |
| Distribution entitlements | A Developer ID build must use **`content-filter-provider-systemextension`** in both `Hisn.entitlements` and `HisnFilter.entitlements` (Apple rejects the plain value outside development), with Developer ID provisioning profiles for the app and the extension. Add a Release-only entitlements pair rather than editing the development ones. |
| Notarization | `xcodebuild archive` → export with Developer ID → `xcrun notarytool submit --wait` → `xcrun stapler staple`. Ship as a signed, notarized **.pkg** whose postinstall runs what `install.sh` does (app to `/Applications`, LaunchAgent, `install_native_host.sh` for the admin-owned browser link). |
| Updates | The app has none yet. Add Sparkle 2 (EdDSA-signed appcast) before the first public build — a filter nobody can update is a filter stuck with its first bug. The list updates itself already. |

## 2. The browser extension

| Step | Detail |
|---|---|
| Chrome Web Store | $5 once. `extension/package.sh` → upload. Listing in Arabic and English. Privacy practices: **no data collected**; justify `<all_urls>` (page-text checking), `nativeMessaging` (the lock state from the Mac app), `scripting`. Start as **Unlisted** for a closed beta. |
| Store id | The store assigns its own id. Add it to `NativeMessagingInstaller.extensionIDs`, rebuild the app, and pass `--extension-id` to the profile (`docs/CHROME_ENFORCEMENT.md`). Until then the unpacked id keeps working beside it. |
| Edge Add-ons | Free, same package. Edge's own policy domain is already in the profile. |
| Localisation | The pages are English-only. Move strings into `_locales/{ar,en}/messages.json` (`chrome.i18n`) and set `dir="rtl"` for Arabic — the audience this product exists for reads Arabic first. |

## 3. The list

* The repository public, `BLOCKLIST_SIGNING_KEY` set in the main-only
  `list-signing` environment, the daily workflow green
  (`docs/SETUP.md` › *Publishing the list*).
* **Licences.** Three of the nine sources — the three UT1 lists — are "free for
  non-commercial use". Selling Hisn with them in the list needs the university's
  permission or their removal (`blocklist/sources.json`, `enabled: false`).
  hagezi's NSFW list is GPL-3.0: redistributing the merged *list* with
  attribution is the normal reading, but take advice before a paid tier.
* Keep the signing key offline apart from the CI secret; write down who holds a
  copy. A lost key means the rotation in `blocklist/keys.py` and a client
  update; a leaked one means the same, urgently.

## 4. The product around it

* **Name.** Search "Hisn" / "حصن" in the trademark databases where you will
  sell, and the App Store and Chrome Web Store, before printing it anywhere.
* **Privacy policy.** Short and true: nothing about browsing leaves the device;
  the partner feature has no server; list downloads are plain HTTPS fetches of
  a public file. `docs/THREAT_MODEL.md` Part 5 is the promise.
* **Onboarding.** The app should walk a new person through `docs/SETUP.md`
  rather than send them to it — especially step 0 (the partner) and step 8
  (the account split), which are where people stop.
* **Support.** An address, and a page answering "the guard closed my browser"
  and "I cannot turn it off" (the honest answer: that is the product; here is
  the partner route and the 48-hour release).
* **Pricing.** Decide what, if anything, is paid. The lists and the Arabic
  vocabulary are the value; the lock is Apple's (`docs/POSITIONING.md`).

## 5. Beyond the Mac

* **iPhone and iPad.** A Safari content blocker (the core list; Safari caps a
  blocker at 150k rules, so split it across several) and a Safari Web
  Extension carrying the page-text scanner and the Arabic vocabulary, under
  Screen Time's lock. The Screen Time API (FamilyControls) needs a separate
  entitlement request to Apple — ask early.
* **Safari on the Mac.** The same Safari Web Extension covers Safari's page
  text, which today only Screen Time's filter watches.

## 6. The first public release, as a checklist

- [ ] DOGFOOD release blockers: none open
- [ ] Hardware checks in TAMPER_MODEL done, results written into that file
- [ ] Developer ID build, notarized, stapled, installed on a clean Mac from the .pkg
- [ ] Extension on the store (unlisted), store id wired into app and profile
- [ ] Arabic UI in the app and the extension
- [ ] Non-commercial list sources resolved
- [ ] Privacy policy and support page live
- [ ] `./test.sh` and `./test.sh browser` green in CI on the release commit
- [ ] A second person, not you, has set it up from the docs alone
