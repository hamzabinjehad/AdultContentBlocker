# Road to 1.0: finish the product, make removal slow, make stopping visible

Written 2026-10-28 after reading the code, the test suites and every plan in
this folder. It coordinates the existing plans rather than replacing them:

- [End-goal plan](END_GOAL_PLAN.md) — the outcome and its measurable gates.
- [Threat model](THREAT_MODEL.md) — the adversary is the user, and coverage is
  the bigger gap.
- [Android 1.0 plan](ANDROID_1.0_PLAN.md) and
  [Android tamper scope](ANDROID_TAMPER_RESISTANCE.md).
- [iPhone/iPad plan](IOS_IMPROVEMENT_PLAN.md).
- [Publishing plan](PUBLISHING_PLAN.md) — release tracks and ship-blockers.
- [Test matrix](TEST_MATRIX.md) — what is demonstrated, inferred or manual.

The plan assumes one full-time engineer plus an owner who can make account,
signing and store decisions. If that is not true, keep the order and multiply
the calendar. Dates are targets, not promises.

## Contents

1. [Where the project actually is](#1-where-the-project-actually-is)
2. [The finish line](#2-the-finish-line)
3. [The lock: what "non-deletable" and "non-stoppable" can mean](#3-the-lock-what-non-deletable-and-non-stoppable-can-mean)
4. [Protecting users from adult content](#4-protecting-users-from-adult-content)
5. [Milestones and backlog](#5-milestones-and-backlog)
6. [Verification and release](#6-verification-and-release)
7. [Risks, decisions and the next ten actions](#7-risks-decisions-and-the-next-ten-actions)

---

## 1. Where the project actually is

The unit-level code is in good shape. The gaps are integration, device proof,
distribution and freshness — not a missing feature list.

### 1.1 Platforms

| Platform | Code state | Release state | The real gap |
| --- | --- | --- | --- |
| Chromium extension | 1.1.12 plus the newly committed short-video/pause work | Not on the Chrome Web Store; unpacked only | Store publication, managed force-install, one real-browser acceptance pass |
| Mac | 1.1.4; filter, browser guard, guardian split, profile, partner key | Unsigned, unnotarized, no app updater | A signed `.pkg`, an update path, and the hardware checks in `TEST_MATRIX.md` |
| iPhone/iPad | 1.1.2 development; Screen Time, Safari blockers, vault, supervised profile | No Apple team, no Family Controls entitlement, no device | Entitlement, a disposable iPhone, device acceptance of every lock level |
| Android | 0.6.0 preview; direct-build uninstall guard and boot shield now committed | No signed production APK; device-owner flow unproven | A disposable device-owner phone, safe-boot/app-control restrictions, signed release |
| Lists | Signed seed v4 built 2026-10-01 | **No `lists` branch on origin** | Publish signed daily generations; update iOS and Android |

A Windows policy generator (`profile/make_windows_policy.py`, with a test)
appeared during this review. It generates Chrome/Edge/Brave/Firefox registry
policies for a standard Windows account, which is a useful hardening start.
There is no Windows app, installer, updater or `WINDOWS_SETUP.md`, and the
README does not list Windows as a platform. Treat Windows as **out of scope for
1.0** unless the owner decides otherwise; see §7.2.

### 1.2 What I verified on 2026-10-28

| Suite | Result | Note |
| --- | --- | --- |
| `./test.sh python seed` | Pass | Blocklist, profile, network, Safari rules, seed verification |
| `./test.sh extension` | Pass | All JavaScript suites, package check, benign-corpus gate |
| `./test.sh release` | Pass | Mac installer and configuration checks |
| `./test.sh macos` | Pass | 490 tests; 516 strings have Arabic |
| `./test.sh ios` | **Fails at the hosted-test step** | 27 Python tests pass; no iPhone simulator device is available (the iOS 26.5 runtime is installed), so `xcodebuild test` never runs |
| `android/test-core.sh` | Pass | With Android Studio's JBR; core suite only, not Gradle/lint/APK |
| `./test.sh browser` | Not run | Needs a Chromium-based browser |

Two environment facts matter for the plan: the machine has **Java 8 on
`PATH`** (Android needs 17+; Android Studio ships a JBR at
`/Applications/Android Studio.app/Contents/jbr/Contents/Home`) and **no
bootable iPhone simulator device** (`xcrun simctl list devices available` is
empty, although the iOS 26.5 runtime is present). A clean run of `./test.sh`
therefore cannot pass today, even though each suite that can run does pass.

These suites ran against the working tree that became `d98e6c1` and
`584a471`, not against the merged release branch. Re-run them after W0.1.

### 1.3 Integration debt

The current branch is `android-0.6.0` at `584a471`:

- **5 commits ahead of `origin/android-0.6.0`**, including the 0.6.0 managed
  mode, the direct-build uninstall guard, the boot shield and the extension
  short-video/pause work.
- **72 commits behind `origin/main`**, which contains the 0.5.4/0.5.5 Android
  work and the macOS grayscale fix.
- The `pause-and-short-video` branch still exists; its extension changes are
  now in `584a471` and its Android changes in `7113846`. Retire it after the
  merge so there is one source of truth.
- The working tree is clean apart from `.claude/launch.json` (IDE configuration,
  not a product file).

This is the first thing to fix. Adding features to a branch that cannot merge
cleanly with `main` makes every later release gate harder.

### 1.4 What is genuinely good

Do not lose these while finishing:

- Signed, pinned lists with rollback protection and a fail-closed client.
- A real test culture: contract tests, a browser harness, device runners,
  localization checks, and a test matrix that separates demonstrated from
  inferred.
- Honest documentation that names bypasses instead of hiding them.
- Layers that are independent by design: DNS, filter, extension, Screen Time,
  managed profile.

The product risk is not that these are weak. It is that the release is split
across branches, stores and unproven devices, while the docs promise a finished
product.

---

## 2. The finish line

> **Hisn 1.0 is finished when a non-technical person can install it on the
> devices they actually use, see which layers are verified and which are not,
> be protected from adult content across those devices' real surfaces, and
> cannot remove or weaken that protection alone before a date they chose —
> with a signed build, a privacy policy, a support route and an honest list of
> every remaining gap.**

That is deliberately not "undeletable" and not "100 % blocking". Section 3
explains what is achievable instead.

### 2.1 Definition of done

| ID | Gate | Measured by |
| --- | --- | --- |
| **D1 Content** | Domain, text, visual, short-video and search coverage meet the per-surface targets in §4 | Per-platform benchmark report with denominators |
| **D2 Removal** | Each platform reaches a declared lock tier and reports the highest evidence it has: *verified*, *attested* or *unavailable* | In-app status plus a device test record |
| **D3 Stop** | Enforcement survives app quit, app deletion, reboot, network change and a normal user's settings; layer loss is visible | Red-team matrix in §6.3 |
| **D4 Freshness** | Signed list generations publish daily and install on Mac, Chromium, iOS and Android; a stale client says so | `lists` manifest is HTTP 200; client version readback |
| **D5 Portability** | Sites, words and the commitment deadline travel between devices through one signed export/import file | Import on a second device changes enforcement |
| **D6 Release** | Signed/notarized/store builds, hashes, privacy policy, support page, onboarding a second person can follow | Release checklist signed off |
| **D7 Safety** | Consent, disclosure, a slow exit, no browsing logs, no hidden install, no blocked emergency access | Design review plus the abuse checklist in §3.8 |

### 2.2 Explicit non-goals for 1.0

- Absolute undeletability or a claim of "unstoppable".
- 100 % adult-content classification, or coverage of every encrypted native
  app on iOS.
- Google Play distribution of the uninstall guard (policy, §3.2).
- A full-tunnel iOS VPN or an iOS content filter outside guardian/supervised
  modes.
- Server-side browsing logs, screenshots or a family dashboard that sees sites.
- Hidden administrator accounts, exploits, or disabling OS recovery.
- Blocking emergency calls, the lock screen or a factory reset from recovery.

---

## 3. The lock: what "non-deletable" and "non-stoppable" can mean

### 3.1 The short answer

No mainstream consumer OS lets an ordinary app make itself permanently
undeletable against a device administrator. What exists is a ladder of
**supported platform controls**:

- **Android:** device owner can refuse uninstall, block safe boot, block app
  control (uninstall, disable, clear data, force stop) and force an always-on
  VPN / private DNS.
- **iPhone/iPad:** only a **supervised** device can make apps undeletable and
  the profile non-removable. On an unsupervised phone, a Screen Time passcode
  plus "Deleting Apps: Don't Allow" is the strongest available lock — and the
  owner can reset it with their Apple Account.
- **Mac:** the lock is **a second person holding administrator rights** plus a
  root-owned app, daemon and profile. Apple has no macOS "allowAppRemoval"
  restriction; a standard user cannot delete a root-owned app, an admin can.
- **Browser:** a **managed, force-installed** extension cannot be disabled by
  an ordinary user; an unpacked or store-installed one can. The OS-level
  filter and DNS still have to cover the browser if the extension disappears.

So the honest promise is:

> **Removal or stopping requires a second person, a chosen date, or a factory
> reset/recovery — not a single decision by the person at 2 a.m.**

"Non-stoppable" is not a property of the app process. It is a property of
where enforcement lives. Put it in the OS, not in the window:

| Enforcement lives in | Survives app quit | Survives app delete | Survives reboot | A normal user can stop it |
| --- | --- | --- | --- | --- |
| Android `VpnService` + device-owner policy | Yes | Blocked by owner | Yes | Only by removing device ownership |
| iOS Screen Time / DNS profile / web filter | Yes | Yes | Yes | Only by revoking authorization or removing the profile |
| macOS Network Extension + root profile + daemon | Yes | Yes, until the filter is removed | Yes | No, for a standard user |
| Chromium static DNR + managed policy | Yes | No | Yes | No, while policy applies |
| The Hisn window / menu-bar app | No | No | No | Yes |

The app is the control panel and the health display. It must never be the lock.

### 3.2 Android ladder

| Tier | How it is provisioned | Blocks uninstall | Blocks stopping | Honest residual |
| --- | --- | --- | --- | --- |
| **A0 Personal (Play)** | Normal install | No | No | Removable by design; Google Play's Accessibility policy forbids the uninstall guard for ordinary self-control |
| **A1 Direct build** | Sideload, owner enables *Keep Hisn installed* | Friction: the guard leaves the uninstall / App info / accessibility / Safe Mode screens | Partly: boot shield covers the first minute after unlock | Settings text it does not recognise, other OEM skins, Safe Mode from hardware keys, ADB/root, force-stop, a killed accessibility service |
| **A2 Managed (device owner)** | Factory-reset phone, QR/NFC provisioning or `adb shell dpm set-device-owner` | **Yes**, Android refuses uninstall | Yes for ordinary flows: `DISALLOW_APPS_CONTROL`, `DISALLOW_SAFE_BOOT`, `DISALLOW_FACTORY_RESET` (Settings), always-on VPN, private DNS | Device-owner removal via ADB/console, a platform vulnerability, recovery-mode reset, accessibility toggle (see below) |
| **A3 Dedicated focus device** | Device owner + lock-task allowlist | Yes | Yes: no Settings, no other apps | A different device; not a daily driver |

#### What to build for Android

1. **Managed-mode restrictions.** While the owner's managed commitment is
   active and the app is device owner, apply and read back:
   - `setUninstallBlocked(admin, packageName, true)` — already implemented.
   - `addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)`.
   - `addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)` —
     blocks uninstall, disable, clear data and force stop for every app. This
     is broad and must be an explicit managed-edition choice.
   - `addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)` — blocks
     the Settings erase, not recovery mode.
   - `addUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN)` and
     `setAlwaysOnVpnPackage(admin, packageName, lockdown)` — see the network
     decision below.
   - A status screen that reads every flag back and shows *verified* only when
     all of them match; release removes them in the reverse order.
2. **Provisioning that a person can follow.** A QR/NFC flow using
   `EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME` on a freshly reset phone,
   plus the existing ADB route for developers. Never provision the owner's
   daily phone; never enroll silently.
3. **A watchdog, not a prayer.** A foreground service plus boot receivers is
   not enough if the OEM kills the app. Add a periodic `WorkManager` health
   check that notices a disabled accessibility service or a dropped VPN and
   posts a local notification. A killed app cannot restart itself; the app
   must say "nothing is guarding" rather than stay green.
4. **The network decision.** Hisn's `VpnService` is DNS-only. Always-on
   *lockdown* with a DNS-only tunnel can black-hole ordinary traffic, so:
   - **Option A (recommended for 1.0):** device-owner `setGlobalPrivateDns`
     to a filtering DoT resolver, plus `DISALLOW_CONFIG_VPN`. The resolver's
     categories enforce, not Hisn's list; state that.
   - **Option B:** keep the DNS VPN always-on without lockdown, block VPN
     configuration changes, and accept that a VPN crash is a leak until it
     restarts.
   - **Option C (2.0):** build a full-tunnel forwarder so lockdown is safe.
     That is a separate project with a real battery and throughput budget.
5. **A dedicated mode.** For people who want the strongest lock, a lock-task
   device with only allowed apps is honest and much stronger than pretending
   a daily phone is a kiosk. Offer it as a separate setup, not a default.

#### The accessibility limitation

Even as device owner, Android has no supported API that prevents the user
from turning off an accessibility service. The guard depends on it, and the
boot shield only covers the first minute. Do not claim otherwise. The managed
restrictions above are what make the install and the settings sticky; the
accessibility service is the *intervention* layer, not the lock.

#### Distribution rule

The Play build must not contain the uninstall-screen guard. The direct and
managed builds must never be uploaded to Play. Keep them as separate Gradle
flavors, separate application IDs if necessary, and a release checklist that
cannot mix them.

### 3.3 iPhone and iPad ladder

| Tier | How it is set up | Blocks deletion | Blocks stopping | Honest residual |
| --- | --- | --- | --- | --- |
| **I0 Personal** | Screen Time web filter, app limits, Safari blockers | No | No | Revoke Screen Time access, delete the app |
| **I1 Passcode vault** | Hisn seals a random Screen Time passcode; *Deleting Apps: Don't Allow*; *Lock Screen Time Settings* | Yes, until the date | Screen Time and the web filter keep working if the app is quit | The owner can reset the passcode with their Apple Account; erase removes everything |
| **I2 Family guardian** | `.child` authorization; the Family Sharing organizer holds the passcode and Apple Account reset | Yes | Yes | The guardian is the exit; requires a real child account |
| **I3 Supervised** | Apple Configurator supervision + Hisn's profile (`allowAppRemoval=false`, locked DNS, web filter, removal password) | Yes | Yes: DNS `ProhibitDisablement`, VPN creation blocked, profile non-removable | The removal-password holder can remove it; DFU restore with Activation Lock |
| **I4 Supervised + time lock / MDM** | I3 with the removal password encrypted to the date (drand `tlock`) or an ABM/MDM-managed device | Yes | Yes | The date, the MDM console, or a DFU restore |

#### What to build for iPhone/iPad

1. **The long-lead items first:** Apple Developer Program, Family Controls
   distribution entitlement for the app and `HisnActivityMonitor`, a
   disposable test iPhone on iOS 26, and a second Apple Account for Family
   Sharing tests. Without the entitlement nothing based on Screen Time can
   ship.
2. **Fix the claims before the code.** The vault copy says apps "cannot be
   deleted" without saying the owner can reset the passcode with their Apple
   Account, and that Hisn cannot verify the passcode was entered. Make the
   wording conditional and show the *attested* level. This is a trust bug,
   not a wording preference.
3. **Add a removal-protection row** to readiness with three states:
   - *Verified*: only where an API reads the state back (supervised profile,
     DNS `ProhibitDisablement`, Screen Time authorization).
   - *Attested*: the person confirmed a self-test, such as trying to delete an
     app.
   - *Unavailable*: the app says plainly what it cannot check.
4. **Device acceptance before any claim:** supervised profile blocks app
   removal, profile removal needs the password, DNS cannot be disabled, VPN
   creation is blocked, erase is blocked; the vault reveals only after the
   date on both device and Apple server time; the 15-minute setup window
   behaves as documented.
5. **Time-lock the removal password** (`tlock`, P1). A solo adult can then use
   supervision without a second person, with the date as the exit. Test the
   reference tool interoperability and the offline-encryption path.
6. **Decide the URL filter.** `NEURLFilterManager` on iOS 26 is the biggest
   coverage gain for an unsupervised phone, but it needs a developer-run
   Private Information Retrieval server, which conflicts with "nothing leaves
   the device". Default: defer to 2.0 and document the gap.

#### What iOS will never give Hisn

- A third-party filter for every app on an unsupervised phone.
- Protection from a factory reset / DFU restore.
- A way to prevent force-quitting the Hisn app. The lock must be Screen Time,
  DNS, the web filter or the supervised profile — not the app process.
- Forced Safari extension enablement. The Safari extension is a coverage
  layer, not the lock.

### 3.4 Mac ladder

| Tier | How it is set up | Blocks deletion | Blocks stopping | Honest residual |
| --- | --- | --- | --- | --- |
| **M0 User app** | Drag to Applications | No | No | Any user can remove it |
| **M1 Guardian split** | Root-owned `/Applications/Hisn.app`, `/Library/LaunchAgents`, profile, filter authority; daily account is standard; a second person holds admin | Standard user cannot delete or disable | LaunchAgent relaunches the app; the filter enforces after the app quits | The admin can remove the app, profile and filter; recovery mode; another device |
| **M2 Root daemon + signed package + updater** | Root-owned LaunchDaemon in the system domain, signed/notarized `.pkg`, signed app updater, MDM-managed profile | Standard user cannot | Daemon restarts the agent, detects tampering and reports filter loss | The admin can still remove everything |
| **M3 MDM / time lock** | ABM/DEP-managed Mac, non-removable profiles, time-locked removal password | Standard user cannot | Daemon + MDM policy | An admin with recovery/erase access |

#### What to build for Mac

1. **A root LaunchDaemon, not only a LaunchAgent.** A LaunchAgent in
   `/Library/LaunchAgents` is root-owned, but it loads into the user's GUI
   session and a standard user may be able to `launchctl bootout` it. Test
   this on a real standard account. Add a root LaunchDaemon in
   `/Library/LaunchDaemons` that:
   - starts the app when the user is logged in,
   - restarts the app/agent if it disappears,
   - records a local-only tamper event that the app can show,
   - reports a filter/provider loss instead of showing green.
2. **A signed, notarized installer and an updater.** The release runbook and
   `macos/release/release.sh` exist; the missing pieces are a Developer ID
   identity, a notarized `.pkg`, and an update path. A filter without an
   updater cannot be supported after the first bug fix.
3. **Verify the guardian split on a clean account.** `setup_guardian.sh
   --check` then `--create-admin` then `--demote-me`; prove the standard user
   cannot delete `/Applications/Hisn.app`, cannot unload the daemon, cannot
   remove the profile, and cannot disable the system extension. Test the
   admin recovery too.
4. **Keep the profile and the filter independent.** Losing one must not
   silently lose the other; `verify_enforcement.sh` should say which layer is
   missing, not print a single green light.
5. **Time-lock the profile removal password** (`tlock`, P1), same as iOS.

#### The macOS truth

Apple's `com.apple.applicationaccess` `allowAppRemoval` key is **iOS and
watchOS only; macOS is n/a**. There is no supported "prevent app deletion"
restriction on macOS. Root ownership plus a non-admin daily user plus a root
daemon is the lock; MDM adds policy enforcement, not magic.

### 3.5 Browser extension ladder

| Tier | How it is set up | Blocks disable/removal | Blocks stopping | Honest residual |
| --- | --- | --- | --- | --- |
| **E0 Unpacked** | Load unpacked | No | No | Developer mode can remove it |
| **E1 Store** | Chrome Web Store | No | No | The user can disable/remove it |
| **E2 Managed policy** | `ExtensionSettings` force-install, `"*": blocked` for others, root-owned native host, incognito/guest/devtools disabled | Yes for a standard user | Yes while policy applies | An admin can change policy; another browser/device |
| **E3 MDM + standard user** | E2 delivered by MDM; only approved browsers installed | Yes | Yes | Admin/recovery; another device |

#### What to build for the extension

1. **Publish an unlisted beta.** Run `extension/package.sh`, upload, read the
   real store ID, then wire it into `NativeMessagingInstaller.swift` and
   `profile/make_profile.py --extension-id`. The packaged extension strips the
   local development key; the development ID will not work in the store build.
2. **Ship the short-video/pause work as 1.1.13** with release notes, Arabic
   and English strings, and a real-browser DNR run. It is committed but not
   released.
3. **Add the managed profile path to the app's setup**, not just to the
   Python generator. Show `chrome://policy` verification and the recovery
   password custody.
4. **Accept that a deleted extension enforces nothing.** The Mac filter and
   DNS are the independent layers; the browser guard is the friction. Do not
   describe the extension as the lock.

### 3.6 Stop-resistance matrix

This is the table to test, not to assert.

| Attack | Android managed | iPhone supervised | Mac guardian | Chromium managed |
| --- | --- | --- | --- | --- |
| Force-quit the app | Filter continues (VPN), guard stops | Screen Time/DNS continue | Filter continues, agent restarts | Extension worker restarts |
| Delete the app | Android refuses | iOS refuses | Standard user cannot | Extension removed: filter/DNS still enforce |
| Disable the extension | n/a | Safari extension off: Screen Time filter still enforces | Browser guard warns/closes; policy blocks disable | Policy prevents disable |
| Turn off the VPN / DNS | `DISALLOW_CONFIG_VPN`; readback alerts | DNS `ProhibitDisablement` | Profile + filter | Profile DNS |
| Remove the profile | n/a | Needs removal password | Needs removal password + admin | Needs admin |
| Reboot | Boot receivers, always-on VPN | Profile persists | LaunchDaemon | Policy persists |
| Safe mode | `DISALLOW_SAFE_BOOT` | n/a | n/a | n/a |
| New user / profile | Device owner is per user; profile owner is not enough | Supervision is device-wide | Profile at device scope | Policy at device scope |
| Clear app data | `DISALLOW_APPS_CONTROL` | n/a | n/a | Storage wipe: re-download list |
| Change the clock | Elapsed realtime + boot count; server time for the vault | Apple server time | Monotonic high-water mark | Device clock; list version floor |
| Factory reset | Recovery still possible | DFU still possible | Recovery still possible | n/a |
| Another device | Open | Open | Open | Open |

The last row is why the product needs the coverage checklist and router DNS;
no code change closes it.

### 3.7 Claim language

| Say this | Only when | Never say |
| --- | --- | --- |
| "Android refuses to uninstall Hisn until the commitment ends or managed mode is released." | Device owner, `isUninstallBlocked` readback true, shown in-app | "Undeletable" |
| "Apps cannot be deleted on this iPhone." | Supervised profile installed, `allowAppRemoval=false`, read back | "Cannot be removed" on a personal phone |
| "The daily user cannot delete or disable Hisn; the guardian can." | The account is standard and the guardian test passed | "Nobody can remove it" |
| "Hisn blocks this adult site." | The URL is in the signed list and the layer was tested | "Blocks all adult content" |
| "This layer is verified / attested / unavailable." | The evidence is in the app | A green light with no evidence |

### 3.8 Abuse and consent guardrails

These features can be repurposed for coercive control. The product must not
make that easy:

- Hardened modes are for a device the user owns and consents to, or a minor's
  device managed by a parent/guardian with the platform's own parental
  controls.
- No covert installation, no hiding the app from the device's user, no remote
  control, no reading messages, no location, no browsing logs.
- Every lock is visible in the app and has a disclosed exit: a date, a
  guardian, or recovery. A lock with no exit is a product people uninstall
  pre-emptively.
- Do not block emergency calls, the lock screen, or a factory reset from
  recovery.
- If someone asks to install this on another adult's phone without their
  consent, refuse and offer the consented family/guardian path instead.

---

## 4. Protecting users from adult content

### 4.1 Coverage is per surface, not per product

The same list is not enforced everywhere, and pretending otherwise is the
fastest way to lose trust. Today:

| Surface | Domain list | Page text | Visual | Short video | Native apps |
| --- | --- | --- | --- | --- | --- |
| Chromium extension | 159,768 core domains (about 320 static rules); a list update refreshes the same core tier, not the long tail | Yes (ar/en/de, evasion) | Opt-in, hide-first on supported sites | Opt-in (YouTube, Instagram, Facebook, Snapchat, TikTok) | No |
| Mac content filter | ~982k signed domains, flow-level for all apps | No native-app text | No | No | All apps at host/flow level |
| Safari on iPhone/iPad | ~160k domains in four blockers; Screen Time web filter | Safari extension | Opt-in local | No | WebKit only |
| Android DNS | 159,768 bundled core domains | Selected apps via accessibility | Selected apps, Android 14+ | Selected apps | Selected apps only |
| Router / DNS | The resolver's categories, not Hisn's list | No | No | No | Any device on that network |

There are three list tiers, and they are not interchangeable:

1. **Core tier (~160k):** what the browser, the four Safari blockers and the
   Android seed can hold. The extension's static rules and its downloaded
   generation both use this tier; a list update refreshes it and the keyword
   layer, and does not add the long tail.
2. **Full list (~982k, per the build docs):** what the Mac socket filter and
   DNS can hold. It is not shipped to the extension or Safari.
3. **Keyword layer (~5.8k terms):** page-text scoring, independent of the
   domain tier.

Until the `lists` branch exists, every client is frozen at the 2026-10-01
seed. That is the single largest freshness gap. It is not a 3× extension gap:
the extension is not supposed to carry the full list.

### 4.2 Quality gates

| Layer | Gate for 1.0 | Where it is reported |
| --- | --- | --- |
| Domains | Daily signed publish; ≥ 200k sanity floor; known adult blocked; critical infrastructure not blocked; rollback rejected; per-surface count shown | CI + in-app list version |
| Text | ≥ 500 labelled cases per language (ar/en/de); ≤ 2 % misses; ≤ 1 % benign blocks; medical, recovery, religious, news and LGBTQ+ negatives reviewed | `extension/eval` and the Android corpus, with denominators |
| Visual | Licensed adult/benign benchmark; ≥ 95 % recall; ≤ 3 % false hides; hide-first with no adult frame visible before a verdict; CPU/battery budget | Per-platform benchmark record |
| Short video | The five surfaces match, in-app navigation is covered, ordinary video is not blocked, and the switch obeys the commitment direction rule | `short-video.test.js`, `ShortVideoSurfacesTest`, device run |
| Search | Google, Bing, DuckDuckGo, Yandex SafeSearch; YouTube Restricted Mode; image search | DNS + policy readback |
| Native apps | Android selected apps; Mac accessibility text guard (P1); iOS documented as WebKit-only | Device matrix |
| Freshness | A client that has not updated says "list may be stale"; a failed update keeps the previous generation | In-app status |

The existing 41-case synthetic corpus (0/18 misses, 0/23 benign blocks) is a
regression set, not a benchmark. The plan's gate is 500 real, labelled cases
per language, with the corpus and its provenance kept out of the repo if the
licences require it.

### 4.3 Work, in order

1. **Publish the list.** Configure the `list-signing` environment, run the
   workflow on `main`, verify `lists/manifest.json` returns 200, and add a
   client test that a published generation installs. This refreshes the core
   tier and keyword layer for the browser, and the full list for the Mac.
2. **Signed updates for iOS and Android.** Today only Mac and Chromium
   download generations. The iOS Safari blockers and the Android DNS seed
   change only with a new app build. Add signature checks, rollback
   rejection and capacity checks (four Safari parts under 40k each).
3. **Text quality.** Expand the labelled corpus, keep the evasion rules
   bounded, and make context scoring local. Never lower thresholds to make
   the numbers look better.
4. **Visual quality.** Hide-first everywhere it can run; sample video faster
   while it is playing; pause and cover on an adult verdict; never auto-reveal.
   Add body-level person hiding only after a licensed benchmark exists.
5. **Short video.** Ship the opt-in surfaces; add in-app navigation coverage
   where the network rules cannot see it; make the block page a pause, not a
   scold.
6. **Native apps.** Mac accessibility text guard for chosen apps (Telegram,
   Discord, Mail), with the same privacy contract as Android: on-device,
   opt-in, password fields skipped, nothing stored.
7. **Search and DNS bypass.** Force SafeSearch at DNS and policy; block
   unfiltered DoH providers; turn off Private Relay; document direct-IP and
   ECH gaps rather than claiming they are closed.
8. **False positives.** Medical, sex education, recovery, religious and
   LGBTQ+ content must not be blocked. Add an in-app "this is wrong" route
   that stores only the host locally, and a reviewed allowlist update path.

### 4.4 Privacy invariants

- Matching stays on the device. No browsing history, matched text, image or
  screenshot leaves it.
- The accountability partner learns that a release was requested and when —
  nothing else.
- The list updater reveals only which repository file was fetched. Do not add
  an account, a unique install ID or a telemetry endpoint.
- The visual models are local and their licences travel with the build.

---

## 5. Milestones and backlog

### 5.1 Timeline

| Milestone | Target | Exit gate |
| --- | --- | --- |
| **M0 One repo, one baseline** | 2026-11-08 | `main` + `android-0.6.0` merged; WIP committed; bare `./test.sh` green on a clean Mac; CI covers Android and iOS; `release/1.0` branch exists |
| **M1 Fresh lists everywhere** | 2026-11-22 | `lists` branch 200; Mac, Chromium, iOS and Android install a generation; stale clients say so |
| **M2 Locked editions** | 2026-12-20 | Android device-owner refusal + restrictions proven on a disposable phone; iPhone supervised profile proven on a disposable phone; Mac guardian split + daemon proven on a standard account; Chromium force-install proven |
| **M3 Coverage** | 2027-01-10 | 500-case text gate; visual benchmark; short-video release; Mac native text guard; iOS URL-filter decision recorded |
| **M4 Device proof** | 2027-01-24 | Red-team matrix run on Mac, iPhone, iPad and Android; every failure fixed or recorded as a limit; release record written |
| **M5 Signed 1.0** | 2027-02-07 | Signed/notarized Mac pkg; store extension; TestFlight iOS; signed Android APK; privacy/support pages; second-person onboarding |

If the team is one part-time person, use the same order and treat the dates as
quarters, not weeks. M1's iOS/Android legs depend on W1.2; if that slips, ship
the Mac and Chromium list updates first and move the phone legs to M1.5.

### 5.2 Backlog

Sizes: S ≤ 2 days, M ≤ 1 week, L ≤ 3 weeks, XL > 3 weeks.

#### P0 — required for 1.0

| ID | Work | Platform | Size | Done when |
| --- | --- | --- | --- | --- |
| W0.1 | Merge `origin/main` into the release branch; resolve 0.5.4/0.5.5 vs 0.6.0/direct conflicts; retire the duplicate short-video branch | All | M | CI green on the merged branch; no lost test or doc |
| W0.2 | Add a JDK 17 Android CI job and an iOS simulator job; make bare `./test.sh` fail loudly on a missing toolchain | CI | M | A clean runner runs every suite or fails with the missing tool named |
| W0.3 | Create `release/1.0`, a version matrix and a changelog; tag the integration point | All | S | Every platform version is pinned in one file |
| W1.1 | Publish the `lists` branch | Lists | S | `curl -I .../lists/manifest.json` is 200; clients install v5+ |
| W1.2 | Signed list updates for iOS Safari blockers and Android DNS | iOS, Android | L | A published generation changes enforcement without a new app build |
| W1.3 | Extension 1.1.13: short-video/pause release notes, package, unlisted store beta | Extension | M | Store build passes the browser suite; real ID wired |
| W1.4 | Per-surface list coverage: document the ~160k core / ~982k full split; make each client state its tier | Extension, iOS, Android | M | A coverage table is generated per release; no client claims the full list |
| W1.5 | Text corpus: 500 labelled cases per language and a CI regression gate | All | L | A vocabulary change reports misses and false blocks per language |
| W1.6 | Visual: licensed benchmark, hide-first audit, video sampling | Chromium, Safari, Android | L | Recall/false-hide numbers with denominators; no pre-verdict adult frame |
| W2.1 | Android managed edition: safe-boot/app-control/factory-reset restrictions, always-on/private DNS, watchdog, QR provisioning | Android | XL | Disposable phone: uninstall refused, safe mode blocked, clear data blocked, VPN persistent, recovery works |
| W2.2 | Android direct build: boot shield + guard, signed APK | Android | M | Signed APK, hash, cert fingerprint; guard tested on a real phone |
| W2.3 | iPhone supervised edition: profile test, vault wording, level UI, removal password | iOS | L | Device test record for every key in `IPHONE_SUPERVISED.md` |
| W2.4 | Mac guardian edition: root daemon, bootout test, signed pkg, updater | Mac | L | Standard user cannot delete/disable; admin recovery documented; pkg notarized |
| W2.5 | Chromium managed edition: force-install, `"*": blocked`, policy verification | Extension/Mac | M | `chrome://policy` shows every key; standard user cannot disable |
| W2.6 | Removal-protection status: verified / attested / unavailable on every platform | All | M | No screen can claim a higher level than it can read back |
| W3.1 | Layer-health model: detect permission/service/VPN/profile loss, alert locally, fail closed | All | L | Each layer has a test for loss and recovery |
| W3.2 | Red-team matrix: force-quit, delete, disable, VPN, DoH, profile removal, safe mode, new user, clock, network change | All | L | Every row in §3.6 is pass, fixed, or recorded as a limit |
| W3.3 | Reboot/update/sleep recovery on all platforms | All | M | A device test record per platform |
| W4.1 | Export/import sites, words and the commitment deadline | All | L | A second device enforces the imported state |
| W4.2 | Partner/guardian and emergency-exit flows tested | All | M | Release, extend and recovery each verified |
| W5.1 | Apple Developer Program, Family Controls entitlement, TestFlight | iOS | XL lead | Entitlement granted; TestFlight build installs |
| W5.2 | Mac signing, notarization, package, updater | Mac | L | Clean Mac installs from the pkg; update path verified |
| W5.3 | Chrome Web Store listing and privacy practices | Extension | M | Listing live (unlisted first); ID documented |
| W5.4 | Android production keystore and signed release | Android | S | Offline key + two backups; APK hash and cert published |
| W5.5 | Privacy policy, support page, licence review | All | M | Pages live; blocklist/model licences cleared |
| W5.6 | Onboarding and docs; a second person completes setup from the docs alone | All | M | Recorded dry run with every confusion fixed |
| W6.1 | Device acceptance matrix | All | L | `TEST_MATRIX.md` updated with dated evidence |
| W6.2 | Focused two-week red team / dogfood | All | L | Bypass list triaged; no P0 left |
| W6.3 | Release candidate record and rollback plan | All | S | Signed validation record, hashes, known gaps |

#### P1 — after 1.0 or in parallel if capacity allows

| ID | Work | Why it matters |
| --- | --- | --- |
| W2.7 | Time-locked removal password (`tlock`) for iOS/Mac | Lets a solo adult use supervision without a second person |
| W1.7 | Mac accessibility text guard | Covers Telegram/Discord/Mail, where the browser cannot |
| W1.8 | Body-level person hiding + faster video sampling | Closes the "face-only" gap; needs a licensed benchmark |
| W1.9 | Android full-tunnel forwarder | Makes always-on lockdown safe; battery/throughput project |
| W1.10 | iOS `NEURLFilterManager` prototype | Biggest coverage gain for an unsupervised phone; PIR-server decision |
| W2.8 | MDM/ABM runbook for families and small organizations | Turns profiles into non-removable policy |
| W4.3 | Cross-device coverage checklist | Makes "another device" visible |
| W4.4 | Router compatibility adapters | Covers TVs, consoles, guests |

#### P2 — later

- More languages and dialect corpora (fa, ur, fr, es, …).
- macOS visual layer (ScreenCaptureKit).
- Firefox/Edge/Safari extension management beyond Chromium.
- Usage budgets and richer schedules.
- Family dashboard that reports only aggregate, non-browsing health.
- A managed "focus device" kiosk edition.
- A Windows edition: the registry policy generator is a start, but a product
  needs an installer, updater, app/agent, support and device tests.

### 5.3 Critical path and parallel tracks

```
Week 1-2   W0.1/W0.2/W0.3  ─┬─ W1.1 lists  ── W1.2 iOS/Android updates
                            ├─ W1.3 extension beta
                            └─ W2.2 Android direct build
Week 3-6   W2.1 Android managed ────────────── W2.5 Chromium managed
           W5.1 Apple entitlement (start day 1, longest lead)
Week 5-8   W2.4 Mac guardian ── W5.2 Mac signed pkg/updater
           W2.3 iOS supervised (after entitlement/device)
Week 7-10  W1.5 text corpus, W1.6 visual, W3.1 health
Week 9-11  W3.2 red team, W6.1 device matrix
Week 11-14 W5.3/W5.4/W5.5 stores/docs, W6.3 release record
```

The Apple entitlement and the disposable devices are the longest-lead items.
Start them on day one; everything else can proceed without them.

### 5.4 Owner actions (long lead, start now)

| Action | Why | Lead time |
| --- | --- | --- |
| Join the Apple Developer Program and request the Family Controls distribution entitlement for the app and monitor | Nothing Screen Time-based can ship without it | Weeks |
| Create the Android production keystore offline with two backups | Losing it means existing installs can never be updated | Hours, but must happen before the first signed release |
| Configure the `list-signing` GitHub environment with `BLOCKLIST_SIGNING_KEY` and a `main`-only policy | The `lists` branch cannot exist without it | Minutes, once the key is at hand |
| Join the Chrome Web Store ($5) and publish an unlisted beta | The extension is currently unpacked-only | Days to review |
| Obtain a Developer ID Application certificate and notarization credentials | The Mac filter needs a signed, notarized build | Days |
| Prepare one disposable Android phone and one disposable iPhone/iPad | Device-owner and supervision tests erase the device | Procurement |
| Decide Play vs direct for Android, and guardian vs supervised vs vault for iPhone | The distribution and default setup follow from it | A decision, not a task |
| Confirm the blocklist and visual-model licences for a paid tier | Some upstream sources are non-commercial/GPL | Legal review |

---

## 6. Verification and release

### 6.1 Automated gates

- `./test.sh python seed extension release macos ios` green on a clean Mac
  (with a bootable iPhone simulator device).
- `cd android && JAVA_HOME=<JDK 17+ or Android Studio JBR> sh test-core.sh` and
  `./gradlew assembleDebug assembleRelease lintDebug` green in CI.
- `./test.sh browser` in a real Chromium, including the new short-video DNR
  rules.
- `extension/eval/run.sh` at 50/80/100: no benign page blocked; coverage
  reported, not gated.
- `blocklist/seed.py verify`, the publish guard and the real-artifact checks on
  the signed build.
- A published-generation test that installs v5 over v4 and rejects v3.

### 6.2 Device gates

A dated record in `docs/` for each platform, with hashes and what failed:

- **Android managed:** factory reset → provision device owner → enable keep →
  try uninstall (refused) → try safe mode (blocked) → try clear data (blocked)
  → reboot → VPN or private DNS persists → release managed mode → uninstall
  works.
- **Android direct:** guard leaves the uninstall/App info/accessibility/Safe
  Mode screens; the boot shield appears after unlock and removes itself; the
  status says "nothing is guarded" when accessibility is off.
- **iPhone supervised:** no Remove App; profile removal needs the password;
  DNS cannot be disabled; VPN creation blocked; erase blocked; vault reveal
  only after the date; Apple Account reset behaves as documented.
- **iPhone personal:** vault seals and reveals; Screen Time filter reads back;
  the app says the level is *attested*, not *verified*.
- **Mac guardian:** standard user cannot delete/disable; admin can; force-quit
  relaunch; reboot; filter survives app deletion; profile removal needs the
  password; `verify_enforcement.sh` reports each layer separately.
- **Chromium managed:** `chrome://policy` shows force-install and the blocking
  policies; disable/remove buttons are absent; incognito/guest closed; the
  extension reconnects after restart.

### 6.3 Red-team scenarios

Run these on a disposable setup, not the owner's daily device:

1. Delete the app from Finder / Settings / the launcher.
2. Force-quit, then reopen; kill the helper process.
3. Disable the extension, remove it, install a rival blocker.
4. Turn on a VPN, a proxy, a DNS-over-HTTPS profile, Private Relay.
5. Remove the configuration profile; try the wrong password.
6. Reboot into Safe Mode; create a new user; reset the clock.
7. Clear app data; revoke accessibility/Screen Time; disable the VPN.
8. Use another browser, a guest profile, an in-app web view, another device.
9. Try a false positive: a medical, recovery or LGBTQ+ page must not block.
10. Try an adult page on a new domain, inside X/Reddit/Telegram, and in a
    short-video feed.

Every result is pass, fixed, or a documented limit. A "known bypass" that is
not in the docs is a bug.

### 6.4 Release checklist

- [ ] `release/1.0` matches the tested commit; every artifact is built from it.
- [ ] Mac: Developer ID signed, notarized, stapled, installed on a clean Mac.
- [ ] Extension: store package, real ID wired, privacy practices "no data
      collected", unlisted beta first.
- [ ] iOS: TestFlight build with the entitlement; review notes disclose every
      lock and its exit.
- [ ] Android: signed APK, hash, certificate fingerprint, direct-build warning.
- [ ] Lists: `lists` branch 200, version floor, rollback test.
- [ ] Privacy policy and support page live; no browsing data collected.
- [ ] `TEST_MATRIX.md` and the release validation record updated.
- [ ] A second person completes setup from the docs alone.
- [ ] Known gaps copied verbatim into the release notes.

---

## 7. Risks, decisions and the next ten actions

### 7.1 Risks

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Overclaiming "undeletable" and losing trust after the first bypass | High | High | Claim table §3.7; verified/attested/unavailable labels |
| False positives on health, education or LGBTQ+ content | Medium | High | Reviewed corpus, appeals, allowlist, never lower thresholds silently |
| Google Play rejects the uninstall guard | High if submitted | High | Keep direct/managed builds out of Play; separate flavors |
| Apple rejects a Screen Time feature behind a paywall or a hidden lock | Medium | High | Guideline 4.10/2.5.19 review; disclose every lock and exit |
| List publication key lost or misconfigured | Medium | High | Environment-scoped key, two backups, publish guard, rollback floor |
| Device-owner provisioning is too hard for consumers | High | Medium | QR provisioning, a guided reset flow, a "managed edition" label |
| The 72-commit merge loses a fix | Medium | High | Merge first, run the full matrix, compare test counts before/after |
| Battery/latency from visual sampling | Medium | Medium | Benchmarks per release, opt-in, WebGL first, CPU fallback |
| Another device defeats everything | Certain | High | Coverage checklist, router DNS, honest limits, family setup |
| Coercive use of the lock features | Medium | High | Consent, disclosure, exits, no concealment (§3.8) |

### 7.2 Decisions needed

1. **Which customer first?**
   - *Recommended:* Android managed + Mac guardian + Chromium managed as the
     demonstrable "locked" beta in six weeks; iPhone 1.0 after the entitlement.
   - *Alternative:* iPhone first if the audience is mostly iPhone users; then
     start the entitlement today and accept a longer critical path.
2. **Play or direct for Android?**
   - *Recommended:* both, as separate builds. Play = personal, removable;
     direct/managed = locked. Never upload the direct build.
3. **iPhone default lock?**
   - *Recommended:* vault for a solo adult, guardian (Family Sharing) when a
     second person exists, supervised profile for the strongest setup. Show
     the level, never assume it.
4. **Always-on network on Android?**
   - *Recommended:* device-owner Private DNS for 1.0; full tunnel in 2.0.
     Do not ship lockdown over a DNS-only VPN.
5. **MDM?**
   - *Recommended:* document it for families/organizations in 1.0, make it a
     supported "managed" edition in 1.1. It is not required for the consumer
     path.
6. **iOS URL filter?**
   - *Recommended:* defer. The PIR server contradicts the no-server promise;
     revisit after 1.0 with a privacy design.
7. **Time-locked removal password?**
   - *Recommended:* P1, before the iPhone supervised release if possible. It
     is the only way a solo adult gets a real lock without a second person.
8. **Pricing?**
   - *Recommended:* free core + paid managed setup/support, subject to the
     blocklist/model licences. Screen Time features cannot be paywalled.
9. **Windows?**
   - *Recommended:* defer past 1.0. The new registry-policy generator is a
     useful piece, but Windows needs its own app, installer, updater, support
     and device tests before it can be called a platform.

### 7.3 The next ten actions, in order

1. Merge `origin/main` into `android-0.6.0`; resolve conflicts; run the full
   suite; tag the integration point.
2. Configure `list-signing`, run the publish workflow, and verify the `lists`
   branch returns 200.
3. Point `JAVA_HOME` at the Android Studio JBR (or install JDK 17) and create
   a bootable iPhone simulator device; make bare `./test.sh` green; add both
   to CI.
4. Release extension 1.1.13 (short-video + pause) as an unlisted beta.
5. Build and sign an Android direct APK; run the guard and boot-shield checks
   on a real phone.
6. Start the Apple Developer Program and the Family Controls entitlement
   request today.
7. Run `setup_guardian.sh` on a clean Mac account and test `launchctl bootout`.
8. Implement the removal-protection status (verified/attested/unavailable) on
   one platform as the reference, then port it.
9. Start the 500-case text corpus; publish the first scorecard with
   denominators.
10. Book the disposable Android and iPhone test devices; write the device-test
    calendar.

### 7.4 Scoreboard

Track these monthly. They are the difference between "more code" and "a
product that is finished":

| Measure | Today | 1.0 target |
| --- | --- | --- |
| Published list version / clients updating | Seed v4, no `lists` branch | Daily v5+, four clients |
| Text corpus | 41 synthetic cases | 500 labelled per language |
| Visual benchmark | Benign baseline only | Licensed recall + false-hide numbers |
| Platforms with a verified removal level | 0 | 4 |
| Device acceptance records | Partial (Samsung) | Mac, iPhone, iPad, Android |
| Signed release artifacts | 0 | Mac pkg, extension, iOS, APK |
| Claims above the evidence | 1 known (vault wording) | 0 |

---

## The one rule

If a protection claim cannot be demonstrated on a device, it belongs in the
"not covered" list — not in the marketing. The product's advantage is not that
it can do the impossible; it is that it tells the truth about what it can do,
and then does it.
