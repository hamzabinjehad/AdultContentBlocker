# Android tamper resistance: personal, admin and managed modes

Reviewed 2026-10-21, updated 2026-10-26 for the 0.6.0 managed-uninstall preview,
2026-10-29 for the 0.7.0 managed lockdown, 2026-10-30 for 0.8.x, 2026-11-01
for the add-user/date-time/default-app restrictions, strict install mode and the DNS-only always-on request, and 2026-11-02 for the 0.9.0 protection lock.
This describes what the Android
app can actually enforce. It is not a claim that an installed app can survive a
determined device administrator, a factory reset or OS-level access.

## Personal self-control preview

This section describes the Play build (the default). The direct sideload build
adds the protection-screen guard described in the next sections.

The Play build preserves Android Settings, dialers, launchers and permission
controllers as excluded surfaces. It does not use accessibility to trap their
screens, automatically deny consent dialogs or hide the system's controls.
During a protection lock, saved restrictions cannot be changed through Hisn's
backend at all: the backend refuses additions, removals and edits, not only
weakening. A dismissible reminder appears before the existing in-app VPN
settings links when a lock is active; the owner may cancel or proceed. This is
friction, not enforced VPN persistence or uninstall prevention.

Google Play's Accessibility API policy prohibits using that API to prevent
disabling/uninstalling apps except when authorized through parental control by
a parent/guardian or through enterprise management by authorized
administrators. Ordinary self-control consent is not named as that exception.
Do not relabel an adult's personal mode as parental control to evade the policy.
This is a distribution-policy constraint, distinct from what a sideloaded app
may attempt technically. See the official
[Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).

## The protection lock (0.9.0)

The in-app commitment is called the **protection lock**. While one runs:

- `PolicyStore.save` refuses every rule change, in both directions, for every
  in-app caller. The Rules page disables its switches, rows and schedule button
  and shows the remaining time. This replaced the earlier "tighten only" rule;
  a person can no longer add a restriction mid-lock either. Starting a longer
  lock is allowed.
- The only tightening path that still writes rules is a guardian-signed policy
  applied through `GuardianPolicyApplier` → `PolicyStore.updateFromGuardian`.
  It is separate because the guardian is the documented second authority; it
  cannot remove anything.
- The direct build arms its accessibility removal guard from the lock, even with
  both guard switches off. The boot-shield copy and the layer watchdog follow
  the same state.
- A device-owner install applies `setUninstallBlocked` for the lock. The Play
  build cannot block uninstall and now says so in its attention list.
- A lock does not stop Android Settings, data clearing, permission revocation,
  clock changes or another device. Those remain documented limits.

## Direct (sideload) build: protection-screen guard

Added 2026-10-28, extended 2026-11-01 and 2026-11-02. Building with
`-PhisnDistribution=direct` sets `BuildConfig.SELF_PROTECTION` and gives a
BlockerHero-style guard. Play builds compile it out, because the policy above
forbids it there. Never upload a direct build to Google Play.

- **Opt-in switches, automatic during a lock.** Two owner switches, both off by
  default, live in Guide → Manage uninstall protection: **Close Hisn's removal
  and disable screens** (the keep switch) and **Also stop new app installs
  (downloads)**. A running protection lock turns the removal guard on by itself,
  even when both switches are off; the switches extend the guard beyond a lock.
  A switch that is on cannot be turned off while a lock runs. The install-only
  switch is never armed by a lock on its own.
- **What it closes.** `TextGuardService` watches Settings, the package
  installers, the Play Store and the power menu. When a window there is Hisn's
  removal or disable screen, the Safe Mode prompt, or — with the install switch
  on — a new app's install screen, it presses BACK then HOME and opens the
  protection page. These screens are covered:
  - the uninstall confirmation
  - App info (uninstall, force stop)
  - the storage page (clear data)
  - device-admin deactivation
  - Hisn's own accessibility toggle ("Use Hisn")
  - the power menu's Safe Mode screen or dialog (System UI, or `android` on
    older AOSP). Power off and restart stay available.
  - a Settings VPN screen that names Hisn and offers disconnect, forget,
    always-on, edit or settings, so the DNS layer cannot be switched off there
    without meeting the shield page.

  English, Arabic and German Settings labels are matched. The Safe Mode label
  must be a whole line ("Sicherer Modus", "Safe mode"), so a notification that
  only mentions safe mode is not acted on. Pages that only list Hisn
  stay usable. The matching rules are in
  `app.hisn.core.SelfProtectionScreens` and tested by
  `SelfProtectionScreensTest`.
- **The protection page.** `ShieldActivity` is a full-screen, local page that
  names one of five reasons: uninstall, disable, Safe Mode, VPN or install. It holds
  *Back to Hisn* and *Go to home screen* for eight seconds, enables both, and
  returns Home after 60 seconds if it is still in front and untouched. The
  service starts this page after 100 ms (down from 250 ms) and repeat attempts
  inside the old cooldown no longer skip it. On Android 11+ it first draws a
  touch-blocking `TYPE_ACCESSIBILITY_OVERLAY` over the watched screen, so the
  transition cannot be tapped through; the overlay is removed as the page
  starts and leaves the system bars alone. It reads
  no screen text and stores nothing. The system Home button, the dialler and
  emergency screens are never blocked. Back never returns to Settings.
- **Privacy.** While either switch is on, the visible text of those system
  windows is read on the device to recognise the screens. System UI includes the
  notification shade, so its text is read too. It is not stored or sent
  anywhere. The direct build shows a separate accessibility disclosure that says
  so.
- **Device admin.** In the direct build the protection dialog also offers the
  legacy admin. It puts a deactivation screen in front of uninstall, and the
  guard leaves that screen too.

It does **not** survive any of these:
- Safe Mode entered with the hardware keys during boot, which no app can see
  (Safe Mode turns third-party accessibility off)
- tapping the Safe Mode button again within the guard's reaction time
  (about 120 ms plus a screen read; not measured)
- `adb uninstall`, or root
- a factory reset
- Hisn's accessibility service being off or killed by the OEM (the status line
  says "nothing is guarded")
- Settings text that the guard does not recognise, such as other OEM skins,
  other languages, or a renamed action

The VPN guard recognises Settings text; an OEM that draws its VPN controls in a
non-Settings package or a language not covered can still avoid it. Battery
optimization is not blocked; instead Guide → **Keep protection running** asks
for the unrestricted-battery exemption and reports whether it was granted.
Only device-owner mode makes Android itself refuse an uninstall.

Hardware check, 2026-10-28, on a Samsung SM-G990B2 (Android 16) with Settings
in German and Hisn's app language set to Arabic. The test used the isolated
`-PhisnIsolatedVisualCheck=true` copy, because the main install had a
commitment running.
- With the switch on, these screens went straight to the restriction notice
  (the protection page replaced that notice in the 2026-11-01 work):
  - App info for both Hisn installs
  - the package-installer uninstall dialog
  - the Samsung accessibility pages for both Hisn services ("حصن-Shortcut")
- These screens stayed usable:
  - App info for another app
  - the accessibility list and the "Installed apps" list
  - the Settings home and the apps list
- Safe Mode: long-pressing "Ausschalten" opened Samsung's "Sicherer Modus"
  screen, and the guard closed it back to the ordinary power menu. The
  ordinary power menu (Ausschalten, Neustart, Notruf) stayed open. The fast
  double-tap race was not tried, because losing it would reboot the phone.
- With the switch off, App info opened normally again.
- Not yet checked on hardware: the storage page and device-admin deactivation
  (they are covered by the captured-text unit tests).

## Direct build: new-app installs (unreleased)

Added 2026-11-01 in the working tree; not yet part of a versioned release.

- **A second, separate opt-in.** **Also stop new app installs (downloads)** in
  the same protection dialog closes the package installer's Install
  confirmation for a new app and any Play Store page that offers Install (the
  home, a search result or an app page). It is separate from the
  keep-installed switch, so a person can lock removal without blocking every
  install.
- **Why.** A new browser or VPN is the obvious way around the DNS and app
  layers. The guard cannot classify a download, but it can refuse the screen
  that starts an install. App updates, Hisn's own update page and the store's
  update list stay usable.
- **Saved state.** The install switch is sealed with the other settings (the
  same main file and copy) and cannot be turned off while a commitment runs,
  matching the keep switch. The layer watchdog treats either switch as an armed
  accessibility layer.
- **What it is not.** It is not Android's device-owner install block. An
  install started by an already-open installer, an install already queued
  without any page that offers Install, a browser's own APK download, ADB, root
  and a factory reset are not covered. It closes screens; it does not remove
  the installer or the store.
- **Evidence, 2026-11-01.** API 36 emulator (`hisn_ui`), direct debug build:
  Hisn's App info page was closed and the uninstall reason page appeared; a
  Play Store app page for Chrome was closed and the install reason page
  appeared; the Arabic locale rendered the RTL page; *Back to Hisn* opened
  MainActivity; the eight-second hold kept both buttons disabled. Captures are
  in [design/android-shield-2026-11-01](../design/android-shield-2026-11-01/README.md).
  The real package-installer confirmation, the switch dialog, TalkBack, an OEM
  skin and a physical device were not checked.

## Legacy device admin: a confirmation step only

The optional `CommitmentAdminReceiver` adds an Android confirmation step before
uninstall. It deliberately requests no wipe, password, force-lock or other
administrative policies. A legacy admin **cannot block uninstall**: Android
allows the owner to deactivate it and remove Hisn. The app labels this mode
"Device admin only", not protected.

## Optional device-owner mode: 0.6.0 uninstall guard and 0.7.0 lockdown

Android exposes uninstall blocking and user restrictions only to a device
owner (or, inside its own profile, a profile owner). Hisn implements the
device-owner path as two explicit, opt-in layers:

- `UninstallProtectionController` reads `DevicePolicyManager.isDeviceOwnerApp`
  and Android's own `isUninstallBlocked` readback. It applies
  `setUninstallBlocked(admin, packageName, true)` only when the app is device
  owner and either a commitment is running or the owner enabled **Keep Hisn
  installed**.
- The in-app status has five states: unavailable, admin-only, ready, protected,
  and out-of-sync. The last one is surfaced rather than hidden, so a UI that
  says "protected" cannot drift from the platform flag.
- An active protection lock always blocks; the keep-installed switch can be
  enabled during a lock but cannot be disabled until it ends. The lock freezes
  content rules in both directions; managed switches can still be tightened.
- When no commitment and no keep switch remain, the app offers **Remove managed
  mode**, which clears the uninstall block and releases the device-owner
  appointment. This is the recovery path; without it, voluntary enrollment
  would be one-way.
- Boot and foreground entry reconcile the platform flag with the current
  commitment. If a commitment expires while the app is not running, the block
  may remain until Hisn next runs; that is stricter than the promise and is
  stated in the UI/disclosure.
- **Managed lockdown (0.7.0 onward)** is a second switch, off by default and
  shown only to a device owner. While it is on and a commitment or the keep
  switch holds, Hisn applies and reads back `no_safe_boot`, `no_control_apps`,
  `no_factory_reset`, `no_add_user`, `no_config_vpn` and, on Android 9+
  (API 28), `no_config_date_time`, on Android 10+ (API 29)
  `disallow_config_private_dns` and, on Android 14+ (API 34),
  `disallow_config_default_apps`. The add-user, date/time and default-app keys
  were added on 2026-11-01. `no_control_apps` stops uninstalling, disabling, force-stopping
  or clearing the data of **every** app, so it is disclosed as broad before it
  is saved. The managed choices are saved in one write, and release clears the
  restrictions in reverse apply order before the uninstall block and device
  ownership.
- **Strict mode** is a separate, off-by-default switch under managed lockdown.
  While it is on and the lockdown holds, it adds
  `no_install_unknown_sources_globally` on Android 10+ (API 29), for nine keys
  in total on Android 14+. It is separate because it also blocks a manual
  sideloaded Hisn update; the guardian turns lockdown off when no commitment
  runs to update the phone. USB debugging is deliberately left available, so
  the guardian can still verify and recover over ADB. A device-wide install
  block (`no_install_apps`) is still **not** used, because it would also freeze
  Play updates.
- **Blocked-app suspension (2026-11-01)** is a separate, off-by-default switch.
  While managed lockdown holds and the owner has chosen it, Android suspends
  every app that is both installed and in Hisn's block list, using
  `setPackagesSuspended`. A suspended app disappears from the launcher and cannot
  be opened, while its data is kept. The status reads `FLAG_SUSPENDED` back;
  unblocking the app, turning the switch off or releasing managed mode resumes
  it. The cleanup depends on Android listing installed (including suspended)
  packages; a data clear that removes the block list is covered only while that
  listing still shows the app. This is code and pure-test evidence until a
  disposable device run.
- **Always-on DNS (2026-11-01)** is part of the same managed lockdown, and only
  while DNS filtering is already on. The device owner calls
  `setAlwaysOnVpnPackage(Hisn, false)`, reads the package back, and clears it
  when DNS is turned off, the lockdown is released or the VPN is revoked. It is
  **not** a full tunnel and does not enable VPN lockdown: the tunnel carries DNS
  only, so non-DNS traffic is never routed through Hisn. While it is active,
  another VPN cannot be used; the managed-lockdown disclosure says so. A
  revoked VPN approval, an app-data clear or a factory reset still ends it.
- The status combines both layers: a missing or extra restriction or an
  always-on request that does not match is **out of sync**, never a green
  light. The restriction readback repair runs
  only while the app is in the foreground. Separately, the 0.8.0 layer watchdog
  checks the accessibility guard and DNS every 15 minutes and posts a local
  notice; it does not repair restrictions or restart a service.

### What this does and does not survive

While device owner and the guard is on, Android's ordinary uninstall flow
refuses to remove Hisn in the user where it is owner. With managed lockdown on,
the restrictions above (eight on Android 14+, seven on Android 10–13, six on
Android 9 and five on Android 8; nine, eight, six and five with strict) also
hold. This does **not** survive:

- a factory reset from recovery (the supported recovery from a lost
  device-owner app; the Settings erase is what `no_factory_reset` blocks);
- an administrator removing device ownership through Android management,
  ADB, root or a platform vulnerability;
- removal of the device-owner app by the OS, a managed-device console or a
  user with those privileges;
- a different Android user/profile that is not managed. Profile-owner scope
  does not automatically protect the personal profile;
- a force-stopped or uninstalled Hisn: the restrictions are held by Android
  while they are set, but the app cannot repair them if it never runs again;
- a different always-on VPN or a revoked VPN approval: the DNS tunnel stops and
  Android may use unfiltered DNS until Hisn is opened again.

Without managed lockdown, the uninstall guard also does not prevent
force-stop, clearing app data, disabling services or changing other Android
settings. Clearing app data removes the saved keep switch and rules but does
not release device ownership by itself; open Hisn and use **Remove managed
mode** when no commitment remains, or factory-reset the device. With lockdown
on, `no_control_apps` blocks those app-control actions and `no_config_vpn`
blocks VPN configuration changes, but it is not a general Settings
lock and does not cover an OEM screen it does not recognise. It protects the
uninstall and restriction decisions, not every setting. No claim of
"undeletable" is absolute. Hisn does not hide the app. Only the direct build,
with the owner's switch on, closes the uninstall screen and shows the
protection page.

### Provisioning and current-device status

Device ownership is not a permission an installed app can grant itself. The
supported route is a factory-reset device with no accounts, the app installed,
then the owner runs:

```sh
adb shell dpm set-device-owner app.hisn.android/.CommitmentAdminReceiver
```

A daily-use phone with accounts, an existing device owner, or Samsung Secure
Folder profile management cannot safely be converted in place. A managed
profile would protect only that profile. Google Play distribution of a device
policy controller needs its own declarations and review; this preview is for
sideloaded, owner-controlled devices.

#### Laptop provisioning helper

`script/guardian_provision.py` wraps the same install and appointment without
hiding the commands:

```sh
python3 script/guardian_provision.py --serial SERIAL \
    --apk android/app/build/outputs/apk/debug/app-debug.apk
```

The default is a dry run: it reads the phone, prints what it found and the
exact commands, and changes nothing. `--apply` runs those commands after a
typed `PROVISION` confirmation (`--yes` skips the prompt). It refuses a phone
with any account, a secondary user or profile, another device owner, an
unfinished setup wizard, or Android below Hisn's minSdk 26. It does not wipe
the phone, remove an account, uninstall an app or change an ordinary setting;
the only administrative change is the device-owner appointment. It reads the
appointment back afterwards and returns a failure for a missing or different
owner. After it finishes, turn on **Keep Hisn installed** and **Managed
lockdown** in the app, then check the result from the laptop with
`android/test-live-managed-status.py --expect-managed --expect-lockdown`.

An API 36 emulator run on 2026-11-01 provisioned Hisn as device owner and
passed the nine-key readback, strict mode, blocked-app suspension readback and
the app's own uninstall refusal. It also caught a real bug: the app used the
non-existent `no_apps_control` key instead of Android's `no_control_apps`, so
the all-app control restriction was never held. The key is corrected. Settings
UI refusals, release, the tunnel and a physical phone remain unproven; the
record is [ANDROID_LIVE_TEST_2026-11-01.md](ANDROID_LIVE_TEST_2026-11-01.md).

The connected Samsung used for testing on 2026-10-28 is **not** Hisn's device
owner (Samsung Secure Folder is a profile owner in user 150). The installed
`0.6.0-direct` build reports managed protection as unavailable; the read-only
device check returned `deviceOwner=false`, `uninstallBlocked=false`, state
`UNAVAILABLE`, and Android can still uninstall it. No device-owner appointment,
factory reset, account removal or administrative policy was applied to it. Testing the actual blocked state
requires a disposable, freshly provisioned device or emulator.

## Distribution and product scope

- The Play app remains removable. That is an honest platform limit, not a
  missing toggle. The direct build adds friction, not enforcement.
- Managed mode is for a phone the owner controls, not for concealment from the
  person using it. Enrollment requires owner-level action.
- Full-traffic VPN lockdown, disable prevention, data-clearing prevention and
  resistance to a device administrator's recovery controls remain out of scope.
- The battery, boot and service-lifecycle claims are separate from this
  administrative control and are documented in the Android release records.

## Direct build: faster screen guard and boot shield (2026-10-28)

- The uninstall-screen guard now reacts to the window event's own text, retries while
  Android swaps the active window, and throttles (rather than postpones) Settings' content
  changes; the accessibility `notificationTimeout` dropped from 150 to 40 ms. On the
  Samsung SM-G990B2 (Android 16) the uninstall dialog is left about 130 ms after it
  resumes (was about 410 ms). App info is left about 60 ms after Settings draws it.
- **Boot shield.** After a reboot Android bound Hisn's accessibility service about 3.5 s
  after unlock, and BOOT_COMPLETED reached Hisn about 14 s after unlock. While "Keep Hisn
  installed" is on and Hisn may display over other apps, `BootShieldService` starts at
  LOCKED_BOOT_COMPLETED (from a device-protected copy of the switch), waits behind the lock
  screen, and covers the whole screen from `USER_PRESENT` for at least 60 s and until the
  guard reports ready (cap 3 min). Measured on the Samsung: shield shown at unlock, removed
  after 60.1 s.
- Limit: Settings and the package installer hide non-system overlays, so the shield
  cannot cover them. It covers the launcher; the notification shade's Settings shortcut
  remains a route during that minute, where the accessibility guard acts once it is bound.
