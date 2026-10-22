# Android tamper resistance: personal and managed modes

Reviewed 2026-10-21 after the owner's description of BlockerHero showing a
blocking screen over VPN/app-information settings. That description identifies
UI interception/friction, not proof of an Android-enforced uninstall ban. Hisn
does not claim the current competitor version was retested in this update.

## Personal self-control preview

Hisn preserves Android Settings, dialers, launchers and permission controllers
as excluded surfaces. It does not use accessibility to trap their screens,
automatically deny consent dialogs or hide the system's controls. During a real
in-app commitment, saved restrictions cannot be weakened through Hisn's backend.
Version 0.4.4 also adds a dismissible reminder before the existing in-app VPN
settings links when a commitment is active. The owner may cancel or proceed;
the reminder does not intercept settings opened elsewhere. This is friction,
not enforced VPN persistence or uninstall prevention.

Google Play's current Accessibility API policy prohibits using that API to
prevent disabling/uninstalling apps except when authorized through parental
control by a parent/guardian or through enterprise management by authorized
administrators. Ordinary self-control consent is not named as that exception.
Do not relabel an adult's personal mode as parental control to evade the policy.
This is a distribution-policy constraint, distinct from what a particular
sideloaded app may attempt technically. See the official
[Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).

## Stronger managed-device mode: separate scope

Android documents device/profile-owner DPC controls for managed VPN
configuration and uninstall blocking. These are administrative capabilities,
not permissions acquired by showing a pop-up or installing an APK. Managed
profile scope does not automatically protect the owner's whole personal profile.
See [Android Enterprise networking](https://developer.android.com/work/dpc/network-telephony)
and [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager).

A future supported mode would need explicit enrollment consent, verified
management scope, recovery/support planning and separately tested policies.
Hisn's current DNS-only route must not be placed into always-on lockdown: normal
traffic is outside its route and networking could break. A full supported
traffic design and lifecycle tests are prerequisites, not completed features.

No device/profile-owner enrollment, new administrative policy, account removal,
factory reset, debugging restriction or system-settings lock was applied to the
owner's Samsung. This update does not make Hisn undeletable or unstoppable.
