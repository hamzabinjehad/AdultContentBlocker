# Android tamper resistance: personal, admin and managed modes

Reviewed 2026-10-21, updated 2026-10-26 for the 0.6.0 managed-uninstall
preview. This describes what the Android app can actually enforce. It is not a
claim that an installed app can survive a determined device administrator, a
factory reset or OS-level access.

## Personal self-control preview

Hisn preserves Android Settings, dialers, launchers and permission controllers
as excluded surfaces. It does not use accessibility to trap their screens,
automatically deny consent dialogs or hide the system's controls. During a real
in-app commitment, saved restrictions cannot be weakened through Hisn's backend.
A dismissible reminder appears before the existing in-app VPN settings links
when a commitment is active; the owner may cancel or proceed. This is friction,
not enforced VPN persistence or uninstall prevention.

Google Play's Accessibility API policy prohibits using that API to prevent
disabling/uninstalling apps except when authorized through parental control by
a parent/guardian or through enterprise management by authorized
administrators. Ordinary self-control consent is not named as that exception.
Do not relabel an adult's personal mode as parental control to evade the policy.
This is a distribution-policy constraint, distinct from what a sideloaded app
may attempt technically. See the official
[Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).

## Legacy device admin: a confirmation step only

The optional `CommitmentAdminReceiver` adds an Android confirmation step before
uninstall. It deliberately requests no wipe, password, force-lock or other
administrative policies. A legacy admin **cannot block uninstall**: Android
allows the owner to deactivate it and remove Hisn. The app labels this mode
"Device admin only", not protected.

## Optional device-owner mode: 0.6.0 preview

Android exposes uninstall blocking only to a device owner (or, inside its own
profile, a profile owner). Hisn implements the device-owner path as an explicit,
opt-in managed-device mode:

- `UninstallProtectionController` reads `DevicePolicyManager.isDeviceOwnerApp`
  and Android's own `isUninstallBlocked` readback. It applies
  `setUninstallBlocked(admin, packageName, true)` only when the app is device
  owner and either a commitment is running or the owner enabled **Keep Hisn
  installed**.
- The in-app status has five states: unavailable, admin-only, ready, protected,
  and out-of-sync. The last one is surfaced rather than hidden, so a UI that
  says "protected" cannot drift from the platform flag.
- An active commitment always blocks; the keep-installed switch can be enabled
  during a commitment but cannot be disabled until it ends. This uses the same
  direction rule as saved rules.
- When no commitment and no keep switch remain, the app offers **Remove managed
  mode**, which clears the uninstall block and releases the device-owner
  appointment. This is the recovery path; without it, voluntary enrollment
  would be one-way.
- Boot and foreground entry reconcile the platform flag with the current
  commitment. If a commitment expires while the app is not running, the block
  may remain until Hisn next runs; that is stricter than the promise and is
  stated in the UI/disclosure.

### What this does and does not survive

While device owner and the guard is on, Android's ordinary uninstall flow
refuses to remove Hisn in the user where it is owner. It does **not** survive:

- a factory reset (the supported recovery from a lost device-owner app);
- an administrator removing device ownership through Android management,
  ADB, root or a platform vulnerability;
- removal of the device-owner app by the OS, a managed-device console or a
  user with those privileges;
- a different Android user/profile that is not managed. Profile-owner scope
  does not automatically protect the personal profile.

It also does not prevent force-stop, clearing app data, disabling services or
changing other Android settings. Clearing app data removes the saved keep
switch and rules but does not release device ownership by itself; open Hisn and
use **Remove managed mode** when no commitment remains, or factory-reset the
device. It protects the uninstall decision only. No claim of "undeletable" is
absolute, and Hisn does not hide the app or trap the uninstall screen.

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

The connected Samsung used for testing on 2026-10-26 is **not** Hisn's device
owner (Samsung Secure Folder is a profile owner in user 150). No device-owner
appointment, factory reset, account removal or administrative policy was applied
to it. The 0.6.0 build therefore reports managed protection as unavailable on
that phone, and Android can still uninstall it. Testing the actual blocked state
requires a disposable, freshly provisioned device or emulator.

## Distribution and product scope

- The ordinary app remains removable. That is an honest platform limit, not a
  missing toggle.
- Managed mode is for a phone the owner controls, not for concealment from the
  person using it. Enrollment requires owner-level action.
- Full-traffic VPN lockdown, disable prevention, data-clearing prevention and
  resistance to a device administrator's recovery controls remain out of scope.
- The battery, boot and service-lifecycle claims are separate from this
  administrative control and are documented in the Android release records.
